package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.platform.contract.PlatformSearchProjectionProvider;
import com.colla.platform.modules.project.application.WorkItemQueryCanonicalizer.CanonicalQuery;
import com.colla.platform.modules.project.contract.WorkItemQueryContextProvider;
import com.colla.platform.modules.project.contract.WorkItemQueryContextProvider.QueryContext;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.AggregateSpec;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.FilterNode;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.GroupBucket;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryPlan;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public final class WorkItemQueryService {
    private static final int CANDIDATE_BUDGET = 200;
    private final WorkItemRepository repository;
    private final WorkItemService workItems;
    private final PlatformSearchProjectionProvider workItemSearch;
    private final WorkItemQueryContextProvider contexts;
    private final WorkItemQueryCanonicalizer canonicalizer;
    private final WorkItemQueryCursorCodec cursors;
    private final Counter executions;
    private final Counter candidateBound;
    private final Timer latency;

    public WorkItemQueryService(
        WorkItemRepository repository,
        WorkItemService workItems,
        List<PlatformSearchProjectionProvider> searchProviders,
        WorkItemQueryContextProvider contexts,
        WorkItemQueryCanonicalizer canonicalizer,
        WorkItemQueryCursorCodec cursors,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.workItems = workItems;
        this.workItemSearch = searchProviders.stream()
            .filter(provider -> "work_item".equals(provider.objectType()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("WorkItem search projection is unavailable"));
        this.contexts = contexts;
        this.canonicalizer = canonicalizer;
        this.cursors = cursors;
        this.executions = meterRegistry.counter("colla.project.work_item_query.executions");
        this.candidateBound = meterRegistry.counter("colla.project.work_item_query.candidate_bound");
        this.latency = meterRegistry.timer("colla.project.work_item_query.latency");
    }

    public QueryPlan explain(CurrentUser user, UUID spaceId, QueryDefinition request) {
        CanonicalQuery canonical = prepare(user, spaceId, request);
        return new QueryPlan(
            canonical.hash(),
            canonical.definition(),
            List.of(
                "registered-filter-ast",
                "typed-dynamic-fields",
                "controlled-relation-hierarchy-workflow",
                "permission-before-output",
                "stable-multi-sort",
                "signed-keyset-cursor",
                "group-aggregate"
            ),
            CANDIDATE_BUDGET,
            true
        );
    }

    public QueryPlan dryRun(CurrentUser user, UUID spaceId, QueryDefinition request) {
        return explain(user, spaceId, request);
    }

    public QueryResult execute(CurrentUser user, UUID spaceId, QueryDefinition request) {
        return latency.record(() -> executeTimed(user, spaceId, request));
    }

    private QueryResult executeTimed(CurrentUser user, UUID spaceId, QueryDefinition request) {
        CanonicalQuery canonical = prepare(user, spaceId, request);
        QueryDefinition query = canonical.definition();
        executions.increment();
        List<WorkItem> candidates = repository.list(
            user.workspaceId(),
            spaceId,
            query.typeId(),
            null,
            CANDIDATE_BUDGET + 1
        );
        boolean boundReached = candidates.size() > CANDIDATE_BUDGET;
        if (boundReached) {
            candidateBound.increment();
            candidates = candidates.subList(0, CANDIDATE_BUDGET);
        }
        Set<UUID> allowed = workItemSearch.allowed(
            user,
            candidates.stream().map(WorkItem::id).toList(),
            Set.of()
        );
        List<WorkItemView> visible = candidates.stream()
            .filter(item -> allowed.contains(item.id()))
            .map(item -> workItems.get(user, spaceId, item.id()))
            .toList();
        Map<UUID, QueryContext> queryContexts = contexts.load(
            user.workspaceId(),
            spaceId,
            user.id(),
            visible.stream().map(view -> view.item().id()).toList()
        );
        Comparator<WorkItemView> comparator = comparator(query.sorts(), queryContexts);
        List<WorkItemView> matched = visible.stream()
            .filter(view -> matches(query.filter(), view, queryContexts.getOrDefault(
                view.item().id(), QueryContext.empty()
            )))
            .sorted(comparator)
            .toList();
        int start = cursorStart(user, spaceId, canonical, matched);
        int end = Math.min(start + query.limit(), matched.size());
        List<WorkItemView> page = matched.subList(start, end);
        String nextCursor = end < matched.size() && !page.isEmpty()
            ? cursors.encode(
                user.workspaceId(),
                user.id(),
                spaceId,
                canonical.hash(),
                page.getLast().item().id()
            )
            : null;
        List<QueryItem> items = page.stream().map(view -> item(
            view,
            query.select(),
            queryContexts.getOrDefault(view.item().id(), QueryContext.empty())
        )).toList();
        return new QueryResult(
            canonical.hash(),
            items,
            groups(matched, query, queryContexts),
            nextCursor,
            candidates.size(),
            boundReached
        );
    }

    private CanonicalQuery prepare(CurrentUser user, UUID spaceId, QueryDefinition request) {
        workItems.requireQueryScope(user, spaceId);
        CanonicalQuery canonical = canonicalizer.canonicalize(request);
        QueryDefinition query = canonical.definition();
        Set<String> dynamic = canonicalizer.referencedFields(query).stream()
            .filter(canonicalizer::isDynamicField)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!dynamic.isEmpty() && query.typeId() == null) {
            throw failure(
                "QUERY_TYPE_REQUIRED",
                "A WorkItem type is required when dynamic fields are referenced"
            );
        }
        validateDynamicPredicates(user, spaceId, query.typeId(), query.filter());
        for (SortSpec sort : query.sorts()) {
            if (canonicalizer.isDynamicField(sort.field())) {
                workItems.requireQueryCapability(
                    user,
                    spaceId,
                    query.typeId(),
                    sort.field().substring("field.".length()),
                    "eq",
                    sort.direction()
                );
            }
        }
        return canonical;
    }

    private void validateDynamicPredicates(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        FilterNode node
    ) {
        if (node == null) return;
        if ("predicate".equals(node.kind()) && canonicalizer.isDynamicField(node.field())) {
            workItems.requireQueryCapability(
                user,
                spaceId,
                typeId,
                node.field().substring("field.".length()),
                node.operator(),
                "none"
            );
        }
        node.children().forEach(child -> validateDynamicPredicates(user, spaceId, typeId, child));
    }

    private int cursorStart(
        CurrentUser user,
        UUID spaceId,
        CanonicalQuery canonical,
        List<WorkItemView> matched
    ) {
        String encoded = canonical.definition().cursor();
        if (encoded == null) return 0;
        UUID anchorId = cursors.decode(
            encoded,
            user.workspaceId(),
            user.id(),
            spaceId,
            canonical.hash()
        ).anchorId();
        for (int index = 0; index < matched.size(); index++) {
            if (matched.get(index).item().id().equals(anchorId)) return index + 1;
        }
        throw failure("QUERY_CURSOR_EXPIRED", "Query cursor anchor is no longer in the bounded result");
    }

    private boolean matches(FilterNode node, WorkItemView view, QueryContext context) {
        if (node == null) return true;
        return switch (node.kind()) {
            case "and" -> node.children().stream().allMatch(child -> matches(child, view, context));
            case "or" -> node.children().stream().anyMatch(child -> matches(child, view, context));
            case "not" -> !matches(node.children().getFirst(), view, context);
            case "predicate" -> compare(value(view, context, node.field()), node.operator(), node.value());
            default -> false;
        };
    }

    private boolean compare(Object actual, String operator, JsonNode requested) {
        if ("isNull".equals(operator)) return actual == null;
        if ("isNotNull".equals(operator)) return actual != null;
        if (actual instanceof Collection<?> collection) {
            return switch (operator) {
                case "contains", "eq" -> collection.stream().anyMatch(value -> scalar(value).equals(text(requested)));
                case "in" -> requested.isArray() && collection.stream().anyMatch(value ->
                    iterable(requested).stream().anyMatch(node -> scalar(value).equals(text(node))));
                case "ne" -> collection.stream().noneMatch(value -> scalar(value).equals(text(requested)));
                default -> false;
            };
        }
        if (actual == null) return "ne".equals(operator);
        String left = scalar(actual);
        if ("in".equals(operator)) {
            return requested.isArray() && iterable(requested).stream().anyMatch(node -> left.equals(text(node)));
        }
        String right = text(requested);
        int order = order(actual, requested);
        return switch (operator) {
            case "eq" -> left.equals(right);
            case "ne" -> !left.equals(right);
            case "contains" -> left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
            case "startsWith" -> left.toLowerCase(Locale.ROOT).startsWith(right.toLowerCase(Locale.ROOT));
            case "gt" -> order > 0;
            case "gte" -> order >= 0;
            case "lt" -> order < 0;
            case "lte" -> order <= 0;
            default -> false;
        };
    }

    private int order(Object actual, JsonNode requested) {
        if (actual instanceof Number number && requested.isNumber()) {
            return Double.compare(number.doubleValue(), requested.doubleValue());
        }
        if (actual instanceof Instant instant) {
            return instant.compareTo(Instant.parse(requested.asText()));
        }
        return scalar(actual).compareTo(text(requested));
    }

    private Comparator<WorkItemView> comparator(
        List<SortSpec> sorts,
        Map<UUID, QueryContext> queryContexts
    ) {
        Comparator<WorkItemView> result = (left, right) -> 0;
        for (SortSpec sort : sorts) {
            Comparator<WorkItemView> next = (left, right) -> compareValues(
                value(left, queryContexts.getOrDefault(left.item().id(), QueryContext.empty()), sort.field()),
                value(right, queryContexts.getOrDefault(right.item().id(), QueryContext.empty()), sort.field()),
                sort.nulls()
            );
            if ("desc".equals(sort.direction())) next = next.reversed();
            result = result.thenComparing(next);
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareValues(Object left, Object right, String nulls) {
        if (left == null && right == null) return 0;
        if (left == null) return "first".equals(nulls) ? -1 : 1;
        if (right == null) return "first".equals(nulls) ? 1 : -1;
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        return scalar(left).compareTo(scalar(right));
    }

    private Object value(WorkItemView view, QueryContext context, String field) {
        WorkItem item = view.item();
        return switch (field) {
            case "id" -> item.id();
            case "typeId" -> item.typeDefinitionId();
            case "displayKey" -> item.displayKey();
            case "title" -> item.title();
            case "status" -> item.status();
            case "version" -> item.version();
            case "createdBy" -> item.createdBy();
            case "createdAt" -> item.createdAt();
            case "updatedAt" -> item.updatedAt();
            case "participantRole" -> context.participantRoles();
            case "state" -> context.state();
            case "nodeState" -> context.nodeStates();
            case "relation" -> context.relations();
            case "ancestor" -> context.ancestors();
            case "descendant" -> context.descendants();
            default -> field.startsWith("field.")
                ? jsonValue(view.fieldValues().get(field.substring("field.".length())))
                : null;
        };
    }

    private QueryItem item(
        WorkItemView view,
        List<String> selectedFields,
        QueryContext context
    ) {
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String field : selectedFields) {
            if ("id".equals(field)) selected.put(field, view.item().id());
            else if (field.startsWith("field.")) {
                selected.put(field, jsonValue(view.fieldValues().get(field.substring("field.".length()))));
            } else selected.put(field, value(view, context, field));
        }
        WorkItem item = view.item();
        return new QueryItem(
            item.id(),
            item.spaceId(),
            item.typeDefinitionId(),
            item.displayKey(),
            item.title(),
            item.status(),
            item.version(),
            item.createdBy(),
            item.createdAt(),
            item.updatedAt(),
            view.fieldValues(),
            Map.copyOf(selected),
            view.availableActions()
        );
    }

    private List<GroupBucket> groups(
        List<WorkItemView> matched,
        QueryDefinition query,
        Map<UUID, QueryContext> queryContexts
    ) {
        if (query.group() == null) return List.of();
        Map<String, List<WorkItemView>> buckets = new java.util.TreeMap<>();
        for (WorkItemView view : matched) {
            Object raw = value(
                view,
                queryContexts.getOrDefault(view.item().id(), QueryContext.empty()),
                query.group().field()
            );
            buckets.computeIfAbsent(raw == null ? "(null)" : scalar(raw), ignored -> new ArrayList<>())
                .add(view);
        }
        return buckets.entrySet().stream().map(entry -> {
            Map<String, Object> aggregateValues = new LinkedHashMap<>();
            for (AggregateSpec aggregate : query.group().aggregates()) {
                aggregateValues.put(
                    aggregate.alias(),
                    aggregate(aggregate, entry.getValue(), queryContexts)
                );
            }
            return new GroupBucket(entry.getKey(), entry.getValue().size(), Map.copyOf(aggregateValues));
        }).toList();
    }

    private Object aggregate(
        AggregateSpec aggregate,
        List<WorkItemView> views,
        Map<UUID, QueryContext> queryContexts
    ) {
        if ("count".equals(aggregate.function())) return (long) views.size();
        Function<WorkItemView, String> values = view -> scalar(value(
            view,
            queryContexts.getOrDefault(view.item().id(), QueryContext.empty()),
            aggregate.field()
        ));
        return "min".equals(aggregate.function())
            ? views.stream().map(values).min(String::compareTo).orElse(null)
            : views.stream().map(values).max(String::compareTo).orElse(null);
    }

    private static Object jsonValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.booleanValue();
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isFloatingPointNumber()) return value.doubleValue();
        if (value.isTextual()) return value.textValue();
        if (value.isArray()) {
            List<Object> values = new ArrayList<>();
            value.forEach(node -> values.add(jsonValue(node)));
            return List.copyOf(values);
        }
        return value;
    }

    private static String scalar(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? "" : value.isTextual() ? value.textValue() : value.asText();
    }

    private static List<JsonNode> iterable(JsonNode array) {
        List<JsonNode> values = new ArrayList<>();
        array.forEach(values::add);
        return values;
    }
}
