package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemHierarchyModels.MAX_QUERY_DEPTH;
import static com.colla.platform.modules.project.domain.WorkItemHierarchyModels.MAX_QUERY_NODES;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyIssue;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyReport;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyMutation;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyNavigation;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyNode;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyPage;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyRebuildBatch;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemRelationModels;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import com.colla.platform.modules.project.infrastructure.WorkItemHierarchyRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemHierarchyRepository.HierarchyRebuildRecord;
import com.colla.platform.modules.project.infrastructure.WorkItemHierarchyRepository.RebuildBatchStart;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemHierarchyService {
    private static final int MAX_INHERITED_FIELDS = 32;
    private static final Set<String> STRUCTURAL_FAILURES = Set.of(
        "CANONICAL_EDGE_CYCLE",
        "MAX_DEPTH_EXCEEDED"
    );

    private final WorkItemHierarchyRepository hierarchyRepository;
    private final WorkItemRelationRepository relationRepository;
    private final WorkItemHierarchyProjectionService projectionService;
    private final WorkItemRelationAccessDecisionService accessDecision;
    private final WorkItemRelationService relationService;
    private final WorkItemService workItemService;
    private final AuditLog auditLog;
    private final ObjectMapper objectMapper;

    public WorkItemHierarchyService(
        WorkItemHierarchyRepository hierarchyRepository,
        WorkItemRelationRepository relationRepository,
        WorkItemHierarchyProjectionService projectionService,
        WorkItemRelationAccessDecisionService accessDecision,
        WorkItemRelationService relationService,
        WorkItemService workItemService,
        AuditLog auditLog,
        ObjectMapper objectMapper
    ) {
        this.hierarchyRepository = hierarchyRepository;
        this.relationRepository = relationRepository;
        this.projectionService = projectionService;
        this.accessDecision = accessDecision;
        this.relationService = relationService;
        this.workItemService = workItemService;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    public HierarchyPage query(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String relationKey,
        String direction,
        String cursor,
        int maxDepth,
        int limit
    ) {
        accessDecision.requireVisible(user, spaceId);
        requireNode(user, spaceId, workItemId);
        String key = relationKey(relationKey);
        String normalizedDirection = direction(direction);
        Cursor parsed = cursor(cursor);
        int safeDepth = Math.max(1, Math.min(maxDepth, MAX_QUERY_DEPTH));
        int safeLimit = Math.max(1, Math.min(limit, MAX_QUERY_NODES));
        List<HierarchyNode> rows = hierarchyRepository.listNodes(
            user.workspaceId(),
            spaceId,
            key,
            workItemId,
            normalizedDirection,
            parsed == null ? null : parsed.depth(),
            parsed == null ? null : parsed.nodeId(),
            safeDepth,
            safeLimit + 1
        );
        boolean truncated = rows.size() > safeLimit;
        List<HierarchyNode> visible = truncated ? rows.subList(0, safeLimit) : rows;
        HierarchyNode last = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        return new HierarchyPage(
            List.copyOf(visible),
            truncated && last != null ? last.depth() + ":" + last.id() : null,
            truncated
        );
    }

    public HierarchyNavigation navigation(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String relationKey,
        int maxDepth,
        int limit
    ) {
        accessDecision.requireVisible(user, spaceId);
        String key = relationKey(relationKey);
        HierarchyNode focus = requireNode(user, spaceId, workItemId);
        int safeDepth = Math.max(1, Math.min(maxDepth, MAX_QUERY_DEPTH));
        int safeLimit = Math.max(1, Math.min(limit, MAX_QUERY_NODES));
        List<HierarchyNode> ancestors = hierarchyRepository.listNodes(
            user.workspaceId(), spaceId, key, workItemId, "ancestors",
            null, null, safeDepth, safeLimit + 1
        );
        List<HierarchyNode> children = hierarchyRepository.listNodes(
            user.workspaceId(), spaceId, key, workItemId, "descendants",
            null, null, 1, safeLimit + 1
        );
        HierarchyNode parent = ancestors.stream()
            .filter(node -> node.depth() == 1)
            .findFirst()
            .orElse(null);
        List<HierarchyNode> siblings = parent == null
            ? List.of()
            : hierarchyRepository.listNodes(
                user.workspaceId(), spaceId, key, parent.id(), "descendants",
                null, null, 1, safeLimit + 1
            ).stream().filter(node -> !node.id().equals(workItemId)).toList();
        List<HierarchyNode> localTree = hierarchyRepository.listNodes(
            user.workspaceId(), spaceId, key, workItemId, "descendants",
            null, null, safeDepth, safeLimit + 1
        );
        boolean truncated = ancestors.size() > safeLimit
            || children.size() > safeLimit
            || siblings.size() > safeLimit
            || localTree.size() > safeLimit;
        List<HierarchyNode> breadcrumbs = ancestors.stream()
            .limit(safeLimit)
            .sorted(Comparator.comparingInt(HierarchyNode::depth).reversed())
            .toList();
        return new HierarchyNavigation(
            focus,
            breadcrumbs,
            parent,
            List.copyOf(children.stream().limit(safeLimit).toList()),
            List.copyOf(siblings.stream().limit(safeLimit).toList()),
            List.copyOf(localTree.stream().limit(safeLimit).toList()),
            truncated,
            truncated ? "node_or_depth_budget_reached" : null
        );
    }

    @Transactional
    public HierarchyMutation attach(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        UUID parentWorkItemId,
        UUID childWorkItemId,
        long expectedParentVersion,
        long expectedChildVersion,
        String requestId
    ) {
        RelationView relation = relationService.create(
            user,
            spaceId,
            relationKey(relationKey),
            parentWorkItemId,
            childWorkItemId,
            expectedParentVersion,
            expectedChildVersion,
            requestId(requestId)
        );
        requireParentRelation(relation, childWorkItemId);
        return new HierarchyMutation(relation, null);
    }

    @Transactional
    public HierarchyMutation detach(
        CurrentUser user,
        UUID spaceId,
        UUID relationId,
        long expectedRelationVersion,
        long expectedParentVersion,
        long expectedChildVersion,
        String reason,
        String requestId
    ) {
        RelationView current = relationService.get(user, spaceId, relationId, null);
        requireParentRelation(current, current.target().id());
        RelationView relation = relationService.withdraw(
            user,
            spaceId,
            relationId,
            expectedRelationVersion,
            expectedParentVersion,
            expectedChildVersion,
            reason,
            requestId(requestId)
        );
        return new HierarchyMutation(relation, null);
    }

    @Transactional
    public HierarchyMutation reparent(
        CurrentUser user,
        UUID spaceId,
        UUID currentRelationId,
        UUID newParentWorkItemId,
        long expectedRelationVersion,
        long expectedCurrentParentVersion,
        long expectedNewParentVersion,
        long expectedChildVersion,
        String reason,
        String confirmation,
        String requestId
    ) {
        requireConfirmation(confirmation, "REPARENT");
        String normalizedRequestId = requestId(requestId);
        RelationView current = relationService.get(user, spaceId, currentRelationId, null);
        UUID childWorkItemId = current.target().id();
        requireParentRelation(current, childWorkItemId);
        relationService.withdraw(
            user,
            spaceId,
            currentRelationId,
            expectedRelationVersion,
            expectedCurrentParentVersion,
            expectedChildVersion,
            reason,
            subRequestId(normalizedRequestId, "detach")
        );
        RelationView attached = relationService.create(
            user,
            spaceId,
            current.relationKey(),
            newParentWorkItemId,
            childWorkItemId,
            expectedNewParentVersion,
            expectedChildVersion,
            subRequestId(normalizedRequestId, "attach")
        );
        auditLog.log(
            user,
            "work_item_hierarchy.reparented",
            "work_item",
            childWorkItemId,
            Map.of(
                "spaceId", spaceId.toString(),
                "relationKey", current.relationKey(),
                "previousParentWorkItemId", current.source().id().toString(),
                "currentParentWorkItemId", newParentWorkItemId.toString()
            )
        );
        return new HierarchyMutation(attached, null);
    }

    @Transactional
    public HierarchyMutation splitChild(
        CurrentUser user,
        UUID spaceId,
        UUID parentWorkItemId,
        String relationKey,
        UUID childTypeId,
        String childTitle,
        JsonNode childFieldValues,
        List<String> inheritFieldKeys,
        long expectedParentVersion,
        String requestId
    ) {
        String normalizedRequestId = requestId(requestId);
        WorkItemView parent = workItemService.get(user, spaceId, parentWorkItemId);
        if (parent.item().version() != expectedParentVersion) {
            throw failure(
                "RELATION_ENDPOINT_VERSION_CONFLICT",
                "The parent work item changed concurrently"
            );
        }
        ObjectNode values = inheritedValues(
            parent.fieldValues(),
            childFieldValues,
            inheritFieldKeys
        );
        WorkItemView child = workItemService.create(
            user,
            spaceId,
            childTypeId,
            childTitle,
            values,
            subRequestId(normalizedRequestId, "child")
        );
        RelationView relation = relationService.create(
            user,
            spaceId,
            relationKey(relationKey),
            parentWorkItemId,
            child.item().id(),
            expectedParentVersion,
            child.item().version(),
            subRequestId(normalizedRequestId, "relation")
        );
        requireParentRelation(relation, child.item().id());
        auditLog.log(
            user,
            "work_item_hierarchy.child_split",
            "work_item",
            child.item().id(),
            Map.of(
                "spaceId", spaceId.toString(),
                "parentWorkItemId", parentWorkItemId.toString(),
                "relationKey", relation.relationKey(),
                "inheritedFieldCount", inheritFieldKeys == null ? 0 : inheritFieldKeys.size()
            )
        );
        return new HierarchyMutation(relation, child);
    }

    @Transactional
    public ConsistencyReport scan(
        CurrentUser user,
        UUID spaceId,
        String relationKey
    ) {
        accessDecision.requireManager(user, spaceId);
        String key = relationKey(relationKey);
        relationRepository.acquireGraphLock(user.workspaceId(), spaceId, key);
        return projectionService.scan(user.workspaceId(), spaceId, key);
    }

    @Transactional
    public HierarchyRebuildBatch rebuild(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        boolean dryRun,
        String confirmation,
        String requestId
    ) {
        accessDecision.requireManager(user, spaceId);
        if (!dryRun) {
            requireConfirmation(confirmation, "REBUILD_HIERARCHY");
        }
        String key = relationKey(relationKey);
        String normalizedRequestId = requestId(requestId);
        String requestHash = hash(
            "rebuild|" + user.id() + "|" + spaceId + "|" + key + "|" + dryRun
        );
        hierarchyRepository.tryCreateRebuildBatch(new RebuildBatchStart(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            key,
            normalizedRequestId,
            requestHash,
            dryRun,
            user.id()
        ));
        HierarchyRebuildRecord record = hierarchyRepository.findRebuildBatchByRequest(
            user.workspaceId(), spaceId, normalizedRequestId
        ).orElseThrow(() -> failure(
            "IDEMPOTENCY_CONFLICT",
            "Hierarchy rebuild receipt is unavailable"
        ));
        requireSameRequest(user, requestHash, record);
        if ("completed".equals(record.batch().status())
            || "failed".equals(record.batch().status())) {
            return record.batch();
        }
        return executeRebuild(user, record);
    }

    @Transactional
    public HierarchyRebuildBatch resume(
        CurrentUser user,
        UUID spaceId,
        UUID batchId,
        String confirmation
    ) {
        accessDecision.requireManager(user, spaceId);
        requireConfirmation(confirmation, "REBUILD_HIERARCHY");
        HierarchyRebuildRecord record = hierarchyRepository.findRebuildBatch(
            user.workspaceId(), spaceId, batchId
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN",
            "Hierarchy rebuild batch is not available"
        ));
        if (!"failed".equals(record.batch().status())) {
            throw failure(
                "HIERARCHY_REBUILD_NOT_RESUMABLE",
                "Only a failed hierarchy rebuild can be resumed"
            );
        }
        return executeRebuild(user, record);
    }

    private HierarchyRebuildBatch executeRebuild(
        CurrentUser user,
        HierarchyRebuildRecord record
    ) {
        HierarchyRebuildBatch batch = record.batch();
        relationRepository.acquireGraphLock(
            user.workspaceId(), batch.spaceId(), batch.relationKey()
        );
        ConsistencyReport before = projectionService.scan(
            user.workspaceId(), batch.spaceId(), batch.relationKey()
        );
        List<ConsistencyIssue> structural = before.issues().stream()
            .filter(issue -> STRUCTURAL_FAILURES.contains(issue.code()))
            .limit(MAX_QUERY_NODES)
            .toList();
        String status;
        List<ConsistencyIssue> failures;
        if (!structural.isEmpty()) {
            status = "failed";
            failures = structural;
        } else {
            if (!batch.dryRun()) {
                projectionService.rebuild(
                    user.workspaceId(), batch.spaceId(), batch.relationKey()
                );
            }
            status = "completed";
            failures = batch.dryRun()
                ? before.issues().stream().limit(MAX_QUERY_NODES).toList()
                : List.of();
        }
        if (hierarchyRepository.completeRebuildBatch(
            user.workspaceId(),
            batch.spaceId(),
            batch.id(),
            batch.attempt(),
            status,
            before.edgeCount(),
            before.expectedPathCount(),
            failures
        ) != 1) {
            throw failure(
                "HIERARCHY_REBUILD_VERSION_CONFLICT",
                "Hierarchy rebuild batch changed concurrently"
            );
        }
        HierarchyRebuildBatch completed = hierarchyRepository.findRebuildBatch(
            user.workspaceId(), batch.spaceId(), batch.id()
        ).orElseThrow().batch();
        auditLog.log(
            user,
            "work_item_hierarchy.rebuild_" + completed.status(),
            "project_space",
            batch.spaceId(),
            Map.of(
                "relationKey", batch.relationKey(),
                "dryRun", batch.dryRun(),
                "attempt", completed.attempt(),
                "issueCount", completed.issueCount()
            )
        );
        return completed;
    }

    private ObjectNode inheritedValues(
        JsonNode visibleParentValues,
        JsonNode requestedChildValues,
        List<String> inheritFieldKeys
    ) {
        List<String> keys = inheritFieldKeys == null
            ? List.of()
            : inheritFieldKeys.stream().distinct().toList();
        if (keys.size() > MAX_INHERITED_FIELDS) {
            throw failure(
                "HIERARCHY_INHERITANCE_BUDGET_EXCEEDED",
                "Too many inherited fields were requested"
            );
        }
        ObjectNode result = objectMapper.createObjectNode();
        for (String key : keys) {
            if (!WorkItemRelationModels.SEMANTIC_KEY.matcher(key).matches()) {
                throw failure(
                    "INVALID_HIERARCHY_INHERITANCE_FIELD",
                    "Inherited field keys must be stable semantic keys"
                );
            }
            JsonNode visible = visibleParentValues == null ? null : visibleParentValues.get(key);
            if (visible != null) {
                result.set(key, visible.deepCopy());
            }
        }
        if (requestedChildValues != null && !requestedChildValues.isNull()) {
            if (!requestedChildValues.isObject()) {
                throw failure(
                    "INVALID_FIELD_VALUES",
                    "Child field values must be a JSON object"
                );
            }
            requestedChildValues.fields().forEachRemaining(entry ->
                result.set(entry.getKey(), entry.getValue().deepCopy())
            );
        }
        return result;
    }

    private HierarchyNode requireNode(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId
    ) {
        return hierarchyRepository.findNode(user.workspaceId(), spaceId, workItemId)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN",
                "Work item hierarchy endpoint is not available"
            ));
    }

    private void requireParentRelation(RelationView relation, UUID childWorkItemId) {
        if (!"parent_child".equals(relation.kind())
            || !"directed".equals(relation.direction())
            || !relation.target().id().equals(childWorkItemId)) {
            throw failure(
                "HIERARCHY_RELATION_REQUIRED",
                "The operation requires a directed parent-child relation"
            );
        }
    }

    private void requireSameRequest(
        CurrentUser user,
        String requestHash,
        HierarchyRebuildRecord record
    ) {
        if (!requestHash.equals(record.requestHash())
            || !user.id().equals(record.batch().requestedBy())) {
            throw failure(
                "IDEMPOTENCY_KEY_REUSED",
                "Request id was already used with different hierarchy input"
            );
        }
    }

    private String relationKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!WorkItemRelationModels.SEMANTIC_KEY.matcher(normalized).matches()) {
            throw failure("INVALID_RELATION_KEY", "Invalid relation key");
        }
        return normalized;
    }

    private String direction(String value) {
        return switch (value == null ? "descendants" : value.trim()) {
            case "ancestors" -> "ancestors";
            case "descendants" -> "descendants";
            default -> throw failure(
                "INVALID_HIERARCHY_DIRECTION",
                "Hierarchy direction must be ancestors or descendants"
            );
        };
    }

    private Cursor cursor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String[] parts = value.split(":", 2);
            int depth = Integer.parseInt(parts[0]);
            UUID nodeId = UUID.fromString(parts[1]);
            if (depth < 1 || depth > MAX_QUERY_DEPTH) {
                throw new IllegalArgumentException();
            }
            return new Cursor(depth, nodeId);
        } catch (RuntimeException exception) {
            throw failure("INVALID_HIERARCHY_CURSOR", "Invalid hierarchy cursor");
        }
    }

    private String requestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id is required and limited to 120 characters");
        }
        return normalized;
    }

    private void requireConfirmation(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw failure(
                "HIERARCHY_CONFIRMATION_REQUIRED",
                "The exact hierarchy confirmation token is required"
            );
        }
    }

    private String subRequestId(String requestId, String operation) {
        return "hierarchy-" + operation + "-" + hash(requestId + "|" + operation).substring(0, 32);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Cursor(int depth, UUID nodeId) {
    }
}
