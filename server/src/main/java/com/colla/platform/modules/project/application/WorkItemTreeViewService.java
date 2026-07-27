package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider;
import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider.AncestorRef;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemRelationModels;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.AncestorPath;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeAggregate;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeNode;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreference;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeRequest;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeResult;
import com.colla.platform.modules.project.infrastructure.WorkItemTreePreferenceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public final class WorkItemTreeViewService {
    private static final int MAX_NODES = 200;
    private static final int MAX_PAGE = 50;
    private static final int MAX_DEPTH = 32;
    private static final Pattern VIEW_KEY = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private final WorkItemQueryService queries;
    private final WorkItemService workItems;
    private final WorkItemHierarchyProjectionProvider hierarchy;
    private final WorkItemQueryCursorCodec cursors;
    private final WorkItemTreePreferenceRepository preferences;

    public WorkItemTreeViewService(
        WorkItemQueryService queries,
        WorkItemService workItems,
        WorkItemHierarchyProjectionProvider hierarchy,
        WorkItemQueryCursorCodec cursors,
        WorkItemTreePreferenceRepository preferences
    ) {
        this.queries = queries;
        this.workItems = workItems;
        this.hierarchy = hierarchy;
        this.cursors = cursors;
        this.preferences = preferences;
    }

    public TreeResult render(CurrentUser user, UUID spaceId, TreeRequest request) {
        Forest forest = forest(user, spaceId, request);
        List<UUID> candidates = forest.children().getOrDefault(request.parentId(), List.of());
        int start = cursorStart(user, spaceId, request.cursor(), forest.treeHash(), candidates);
        int end = Math.min(start + forest.pageLimit(), candidates.size());
        List<TreeNode> page = candidates.subList(start, end).stream()
            .map(forest.nodes()::get)
            .toList();
        String next = end < candidates.size() && !page.isEmpty()
            ? cursors.encode(
                user.workspaceId(), user.id(), spaceId, forest.treeHash(), page.getLast().id()
            )
            : null;
        return new TreeResult(
            1,
            forest.relationKey(),
            forest.treeHash(),
            request.parentId(),
            page,
            next,
            next != null || forest.candidateBoundReached(),
            forest.aggregate()
        );
    }

    public AncestorPath path(
        CurrentUser user,
        UUID spaceId,
        UUID focusId,
        TreeRequest request
    ) {
        Forest forest = forest(user, spaceId, request);
        if (!forest.nodes().containsKey(focusId)) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Tree node is not available");
        }
        List<TreeNode> path = new ArrayList<>();
        UUID current = focusId;
        Set<UUID> seen = new LinkedHashSet<>();
        while (current != null) {
            if (!seen.add(current)) {
                throw failure("HIERARCHY_PROJECTION_INVALID", "Tree projection contains a cycle");
            }
            TreeNode node = forest.nodes().get(current);
            path.add(node);
            current = node.parentId();
        }
        java.util.Collections.reverse(path);
        return new AncestorPath(focusId, List.copyOf(path));
    }

    public TreePreference preference(
        CurrentUser user,
        UUID spaceId,
        String viewKey
    ) {
        workItems.requireQueryScope(user, spaceId);
        String key = viewKey(viewKey);
        return preferences.find(user.workspaceId(), spaceId, user.id(), key)
            .orElse(new TreePreference(key, "parent_child", List.of(), 0, Instant.EPOCH));
    }

    public TreePreference savePreference(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        TreePreferenceCommand command
    ) {
        workItems.requireQueryScope(user, spaceId);
        if (command == null
            || command.expectedVersion() < 0
            || command.requestId() == null
            || command.requestId().isBlank()
            || command.requestId().length() > 120) {
            throw failure("INVALID_TREE_PREFERENCE", "Tree preference command is invalid");
        }
        String relationKey = relationKey(command.relationKey());
        List<UUID> expanded = command.expandedNodeIds() == null
            ? List.of() : command.expandedNodeIds();
        if (expanded.size() > 64 || new LinkedHashSet<>(expanded).size() != expanded.size()) {
            throw failure("TREE_EXPANSION_LIMIT", "Expanded tree state must contain at most 64 unique identities");
        }
        return preferences.save(
            user.workspaceId(),
            spaceId,
            user.id(),
            viewKey(viewKey),
            new TreePreferenceCommand(
                command.requestId().trim(),
                command.expectedVersion(),
                relationKey,
                List.copyOf(expanded)
            )
        );
    }

    private Forest forest(CurrentUser user, UUID spaceId, TreeRequest request) {
        validate(request);
        workItems.requireQueryScope(user, spaceId);
        String relationKey = relationKey(request.relationKey());
        QueryDefinition structural = definition(request.query(), null, null);
        PageBundle all = load(user, spaceId, structural);
        PageBundle matched = request.query().filter() == null
            ? all : load(user, spaceId, definition(
                request.query(), request.query().filter(), null
            ));
        Map<UUID, QueryItem> visible = new LinkedHashMap<>();
        all.items().forEach(item -> visible.put(item.id(), item));
        Map<UUID, List<AncestorRef>> ancestry = hierarchy.ancestors(
            user.workspaceId(),
            spaceId,
            relationKey,
            List.copyOf(visible.keySet())
        );
        rejectCycles(ancestry);
        Set<UUID> matchedIds = matched.items().stream()
            .map(QueryItem::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> included = new LinkedHashSet<>(matchedIds);
        if (request.query().filter() == null) {
            included.addAll(visible.keySet());
        } else {
            for (UUID id : matchedIds) {
                ancestry.getOrDefault(id, List.of()).stream()
                    .map(AncestorRef::workItemId)
                    .filter(visible::containsKey)
                    .forEach(included::add);
            }
        }
        Map<UUID, UUID> parents = new HashMap<>();
        for (UUID id : included) {
            UUID parent = ancestry.getOrDefault(id, List.of()).stream()
                .filter(value -> included.contains(value.workItemId()))
                .min(Comparator.comparingInt(AncestorRef::depth))
                .map(AncestorRef::workItemId)
                .orElse(null);
            parents.put(id, parent);
        }
        Map<UUID, Integer> depths = new HashMap<>();
        for (UUID id : included) {
            depth(id, parents, depths, new LinkedHashSet<>());
        }
        int maxDepth = Math.max(1, Math.min(request.maxDepth(), MAX_DEPTH));
        included.removeIf(id -> depths.getOrDefault(id, 0) > maxDepth);
        parents.entrySet().removeIf(entry -> !included.contains(entry.getKey()));
        parents.replaceAll((id, parent) -> parent != null && included.contains(parent) ? parent : null);

        Map<UUID, List<UUID>> children = new HashMap<>();
        children.put(null, new ArrayList<>());
        Map<UUID, Integer> order = new HashMap<>();
        int position = 0;
        for (UUID id : visible.keySet()) {
            order.put(id, position++);
        }
        for (UUID id : included) {
            children.computeIfAbsent(parents.get(id), ignored -> new ArrayList<>()).add(id);
        }
        children.values().forEach(values -> values.sort(Comparator.comparingInt(order::get)));
        Map<UUID, TreeNode> nodes = new LinkedHashMap<>();
        for (UUID id : included) {
            QueryItem item = visible.get(id);
            int visibleChildren = children.getOrDefault(id, List.of()).size();
            nodes.put(id, new TreeNode(
                id,
                parents.get(id),
                item.displayKey(),
                item.title(),
                item.status(),
                item.version(),
                depths.getOrDefault(id, 0),
                visibleChildren,
                visibleChildren > 0,
                matchedIds.contains(id) ? "matched" : "context",
                item.availableActions()
            ));
        }
        int maximum = nodes.values().stream().mapToInt(TreeNode::depth).max().orElse(0);
        TreeAggregate aggregate = new TreeAggregate(
            nodes.size(),
            children.getOrDefault(null, List.of()).size(),
            matchedIds.size(),
            maximum,
            all.boundReached() || matched.boundReached()
        );
        String treeHash = sha256(
            all.queryHash() + "|" + matched.queryHash() + "|" + relationKey + "|" + maxDepth
        );
        return new Forest(
            relationKey,
            treeHash,
            Map.copyOf(nodes),
            immutable(children),
            Math.max(1, Math.min(request.limit(), MAX_PAGE)),
            aggregate,
            aggregate.candidateBoundReached()
        );
    }

    private PageBundle load(CurrentUser user, UUID spaceId, QueryDefinition definition) {
        List<QueryItem> items = new ArrayList<>();
        String cursor = null;
        String hash = "";
        boolean bound = false;
        for (int page = 0; page < 2; page++) {
            QueryDefinition current = new QueryDefinition(
                definition.schemaVersion(), definition.typeId(), definition.filter(),
                definition.sorts(), definition.group(), definition.select(), 100, cursor
            );
            QueryResult result = queries.execute(user, spaceId, current);
            hash = result.queryHash();
            bound |= result.candidateBoundReached();
            items.addAll(result.items());
            cursor = result.nextCursor();
            if (cursor == null || items.size() >= MAX_NODES) break;
        }
        return new PageBundle(List.copyOf(items), hash, bound || cursor != null);
    }

    private static QueryDefinition definition(
        QueryDefinition source,
        com.colla.platform.modules.project.domain.WorkItemQueryModels.FilterNode filter,
        String cursor
    ) {
        List<String> select = new ArrayList<>(source.select() == null ? List.of() : source.select());
        for (String field : List.of("displayKey", "title", "status")) {
            if (!select.contains(field)) select.add(field);
        }
        return new QueryDefinition(
            source.schemaVersion(),
            source.typeId(),
            filter,
            source.sorts(),
            null,
            List.copyOf(select),
            100,
            cursor
        );
    }

    private static int depth(
        UUID id,
        Map<UUID, UUID> parents,
        Map<UUID, Integer> cache,
        Set<UUID> path
    ) {
        if (cache.containsKey(id)) return cache.get(id);
        if (!path.add(id)) {
            throw failure("HIERARCHY_PROJECTION_INVALID", "Tree projection contains a cycle");
        }
        UUID parent = parents.get(id);
        int value = parent == null ? 0 : depth(parent, parents, cache, path) + 1;
        path.remove(id);
        cache.put(id, value);
        return value;
    }

    private static void rejectCycles(Map<UUID, List<AncestorRef>> ancestry) {
        if (ancestry.entrySet().stream().anyMatch(entry -> entry.getValue().stream()
            .anyMatch(value -> value.workItemId().equals(entry.getKey())))) {
            throw failure("HIERARCHY_PROJECTION_INVALID", "Canonical hierarchy projection is invalid");
        }
    }

    private int cursorStart(
        CurrentUser user,
        UUID spaceId,
        String encoded,
        String treeHash,
        List<UUID> candidates
    ) {
        if (encoded == null || encoded.isBlank()) return 0;
        UUID anchor = cursors.decode(
            encoded, user.workspaceId(), user.id(), spaceId, treeHash
        ).anchorId();
        int index = candidates.indexOf(anchor);
        if (index < 0) {
            throw failure("INVALID_QUERY_CURSOR", "Tree cursor anchor is unavailable");
        }
        return index + 1;
    }

    private static void validate(TreeRequest request) {
        if (request == null || request.schemaVersion() != 1 || request.query() == null) {
            throw failure("INVALID_TREE_SCHEMA", "WorkItem tree schema version must be 1");
        }
        if (request.limit() < 1 || request.maxDepth() < 1) {
            throw failure("INVALID_TREE_BUDGET", "Tree limit and max depth must be positive");
        }
    }

    private static String relationKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!WorkItemRelationModels.SEMANTIC_KEY.matcher(key).matches()) {
            throw failure("INVALID_RELATION_KEY", "Tree relation key is invalid");
        }
        return key;
    }

    private static String viewKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!VIEW_KEY.matcher(key).matches()) {
            throw failure("INVALID_VIEW_KEY", "Tree view key is invalid");
        }
        return key;
    }

    private static Map<UUID, List<UUID>> immutable(Map<UUID, List<UUID>> values) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record PageBundle(List<QueryItem> items, String queryHash, boolean boundReached) {
    }

    private record Forest(
        String relationKey,
        String treeHash,
        Map<UUID, TreeNode> nodes,
        Map<UUID, List<UUID>> children,
        int pageLimit,
        TreeAggregate aggregate,
        boolean candidateBoundReached
    ) {
    }
}
