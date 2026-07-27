package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.AggregateSpec;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.FilterNode;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.GroupSpec;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemQueryCanonicalizer {
    private static final Pattern DYNAMIC_FIELD = Pattern.compile("field\\.[a-z][a-z0-9_-]{0,63}");
    private static final Set<String> SYSTEM_FIELDS = Set.of(
        "id", "typeId", "displayKey", "title", "status", "version",
        "createdBy", "createdAt", "updatedAt"
    );
    private static final Set<String> CONTROLLED_FIELDS = Set.of(
        "participantRole", "state", "nodeState", "relation", "ancestor", "descendant"
    );
    private static final Set<String> OPERATORS = Set.of(
        "eq", "ne", "contains", "startsWith", "in", "gt", "gte", "lt", "lte",
        "isNull", "isNotNull"
    );
    private static final Set<String> KINDS = Set.of("and", "or", "not", "predicate");
    private static final Set<String> AGGREGATES = Set.of("count", "min", "max");
    private static final int MAX_DEPTH = 5;
    private static final int MAX_NODES = 24;
    private static final int MAX_SORTS = 4;
    private static final int MAX_SELECT = 32;

    private final ObjectMapper canonicalMapper = JsonMapper.builder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build();

    public CanonicalQuery canonicalize(QueryDefinition requested) {
        if (requested == null || requested.schemaVersion() != 1) {
            throw failure("INVALID_QUERY_SCHEMA", "WorkItem query schema version must be 1");
        }
        Counter counter = new Counter();
        FilterNode filter = normalize(requested.filter(), 1, counter);
        List<SortSpec> sorts = normalizeSorts(requested.sorts());
        GroupSpec group = normalizeGroup(requested.group());
        List<String> select = normalizeSelect(requested.select());
        int limit = Math.max(1, Math.min(requested.limit() <= 0 ? 50 : requested.limit(), 100));
        QueryDefinition normalized = new QueryDefinition(
            1,
            requested.typeId(),
            filter,
            sorts,
            group,
            select,
            limit,
            requested.cursor() == null || requested.cursor().isBlank()
                ? null
                : requested.cursor().trim()
        );
        QueryDefinition hashable = new QueryDefinition(
            normalized.schemaVersion(),
            normalized.typeId(),
            normalized.filter(),
            normalized.sorts(),
            normalized.group(),
            normalized.select(),
            normalized.limit(),
            null
        );
        return new CanonicalQuery(normalized, hash(hashable), counter.value);
    }

    public boolean isDynamicField(String field) {
        return field != null && DYNAMIC_FIELD.matcher(field).matches();
    }

    public Set<String> referencedFields(QueryDefinition definition) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        collect(definition.filter(), fields);
        definition.sorts().forEach(sort -> fields.add(sort.field()));
        if (definition.group() != null) {
            fields.add(definition.group().field());
            definition.group().aggregates().stream()
                .filter(aggregate -> aggregate.field() != null)
                .forEach(aggregate -> fields.add(aggregate.field()));
        }
        fields.addAll(definition.select());
        return Set.copyOf(fields);
    }

    private FilterNode normalize(FilterNode node, int depth, Counter counter) {
        if (node == null) {
            return null;
        }
        if (depth > MAX_DEPTH || ++counter.value > MAX_NODES) {
            throw failure("QUERY_TOO_COMPLEX", "Query filter exceeds the registered complexity budget");
        }
        String kind = lower(node.kind());
        if (!KINDS.contains(kind)) {
            throw failure("INVALID_QUERY_FILTER", "Filter kind is not registered");
        }
        if ("predicate".equals(kind)) {
            String field = field(node.field());
            String operator = operator(node.operator());
            if (("isNull".equals(operator) || "isNotNull".equals(operator)) && node.value() != null) {
                throw failure("INVALID_QUERY_FILTER", "Null checks must not carry a value");
            }
            if (!"isNull".equals(operator) && !"isNotNull".equals(operator) && node.value() == null) {
                throw failure("INVALID_QUERY_FILTER", "Predicate value is required");
            }
            return new FilterNode(kind, field, operator, node.value(), List.of());
        }
        List<FilterNode> children = node.children() == null
            ? List.of()
            : node.children().stream().map(child -> normalize(child, depth + 1, counter)).toList();
        int required = "not".equals(kind) ? 1 : 2;
        if (children.size() < required || "not".equals(kind) && children.size() != 1) {
            throw failure("INVALID_QUERY_FILTER", "Logical filter has an invalid child count");
        }
        List<FilterNode> canonicalChildren = new ArrayList<>(children);
        if (!"not".equals(kind)) {
            canonicalChildren.sort(Comparator.comparing(this::json));
        }
        return new FilterNode(kind, null, null, null, List.copyOf(canonicalChildren));
    }

    private List<SortSpec> normalizeSorts(List<SortSpec> requested) {
        List<SortSpec> source = requested == null || requested.isEmpty()
            ? List.of(new SortSpec("updatedAt", "desc", "last"))
            : requested;
        if (source.size() > MAX_SORTS) {
            throw failure("QUERY_TOO_COMPLEX", "Query has too many sort fields");
        }
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        List<SortSpec> result = new ArrayList<>();
        for (SortSpec sort : source) {
            String field = field(sort.field());
            if (!fields.add(field)) {
                throw failure("INVALID_QUERY_SORT", "Sort fields must be unique");
            }
            String direction = lower(sort.direction());
            String nulls = lower(sort.nulls());
            if (!Set.of("asc", "desc").contains(direction)
                || !Set.of("first", "last").contains(nulls)) {
                throw failure("INVALID_QUERY_SORT", "Sort direction or null ordering is invalid");
            }
            result.add(new SortSpec(field, direction, nulls));
        }
        if (!fields.contains("id")) {
            result.add(new SortSpec("id", result.getFirst().direction(), "last"));
        }
        return List.copyOf(result);
    }

    private GroupSpec normalizeGroup(GroupSpec requested) {
        if (requested == null) return null;
        String groupField = field(requested.field());
        List<AggregateSpec> source = requested.aggregates() == null
            ? List.of()
            : requested.aggregates();
        if (source.size() > 4) {
            throw failure("QUERY_TOO_COMPLEX", "Query has too many aggregates");
        }
        List<AggregateSpec> aggregates = new ArrayList<>();
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (AggregateSpec aggregate : source) {
            String function = lower(aggregate.function());
            if (!AGGREGATES.contains(function)) {
                throw failure("INVALID_QUERY_AGGREGATE", "Aggregate function is not registered");
            }
            String aggregateField = "count".equals(function) && aggregate.field() == null
                ? null
                : field(aggregate.field());
            String alias = aggregate.alias() == null || aggregate.alias().isBlank()
                ? function + (aggregateField == null ? "" : "_" + aggregateField.replace('.', '_'))
                : aggregate.alias().trim();
            if (!aliases.add(alias)) {
                throw failure("INVALID_QUERY_AGGREGATE", "Aggregate aliases must be unique");
            }
            aggregates.add(new AggregateSpec(function, aggregateField, alias));
        }
        return new GroupSpec(groupField, List.copyOf(aggregates));
    }

    private List<String> normalizeSelect(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of("id", "displayKey", "title", "status", "updatedAt");
        }
        if (requested.size() > MAX_SELECT) {
            throw failure("QUERY_TOO_COMPLEX", "Query selects too many fields");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        requested.forEach(value -> result.add(field(value)));
        return List.copyOf(result);
    }

    private String field(String value) {
        String field = value == null ? "" : value.trim();
        if (!SYSTEM_FIELDS.contains(field)
            && !CONTROLLED_FIELDS.contains(field)
            && !DYNAMIC_FIELD.matcher(field).matches()) {
            throw failure("INVALID_QUERY_FIELD", "Query field is not registered");
        }
        return field;
    }

    private String operator(String value) {
        String operator = value == null ? "" : value.trim();
        if (!OPERATORS.contains(operator)) {
            throw failure("INVALID_QUERY_OPERATOR", "Query operator is not registered");
        }
        return operator;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void collect(FilterNode node, Set<String> fields) {
        if (node == null) return;
        if ("predicate".equals(node.kind())) fields.add(node.field());
        node.children().forEach(child -> collect(child, fields));
    }

    private String hash(QueryDefinition definition) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(json(definition).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_QUERY_DEFINITION", "Query cannot be canonicalized", exception);
        }
    }

    private static final class Counter {
        private int value;
    }

    public record CanonicalQuery(QueryDefinition definition, String hash, int filterNodes) {
    }
}
