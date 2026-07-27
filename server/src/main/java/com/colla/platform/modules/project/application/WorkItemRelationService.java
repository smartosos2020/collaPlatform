package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.contract.WorkItemRelationChangedEvent;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Cardinality;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.DeletionPolicy;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Direction;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationCapabilities;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationDefinitionBinding;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationPage;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationProjection;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.WorkItemRelation;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository.CommandStart;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository.HistoryAppend;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository.NewRelation;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.WorkItemRelationRuntimeAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemRelationService {
    private static final int MAX_PAGE_SIZE = 200;

    private final WorkItemRelationRepository relationRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemRelationRuntimeAdapter runtimeAdapter;
    private final WorkItemRelationAccessDecisionService accessDecision;
    private final WorkItemHierarchyProjectionService hierarchyProjection;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemRelationService(
        WorkItemRelationRepository relationRepository,
        WorkItemRepository workItemRepository,
        WorkItemRelationRuntimeAdapter runtimeAdapter,
        WorkItemRelationAccessDecisionService accessDecision,
        WorkItemHierarchyProjectionService hierarchyProjection,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.relationRepository = relationRepository;
        this.workItemRepository = workItemRepository;
        this.runtimeAdapter = runtimeAdapter;
        this.accessDecision = accessDecision;
        this.hierarchyProjection = hierarchyProjection;
        this.canonicalizer = canonicalizer;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RelationView create(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        long expectedSourceVersion,
        long expectedTargetVersion,
        String requestId
    ) {
        accessDecision.requireWritable(user, spaceId);
        String normalizedKey = relationKey(relationKey);
        String normalizedRequestId = requestId(requestId);
        String requestHash = requestHash(user, "create", Map.of(
            "spaceId", spaceId.toString(),
            "relationKey", normalizedKey,
            "sourceWorkItemId", sourceWorkItemId.toString(),
            "targetWorkItemId", targetWorkItemId.toString(),
            "expectedSourceVersion", expectedSourceVersion,
            "expectedTargetVersion", expectedTargetVersion
        ));
        CommandReceipt receipt = begin(
            user, spaceId, null, "create", normalizedRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }

        LockedEndpoints locked = lockEndpoints(
            user, spaceId, sourceWorkItemId, targetWorkItemId,
            expectedSourceVersion, expectedTargetVersion
        );
        RelationDefinitionBinding binding = runtimeAdapter.requireForCreate(
            locked.source(), locked.target(), normalizedKey
        );
        CanonicalEndpoints endpoints = canonical(binding, locked.source(), locked.target());
        validateSelf(binding, endpoints);
        relationRepository.acquireGraphLock(user.workspaceId(), spaceId, normalizedKey);
        validateAvailable(user, spaceId, binding, endpoints);

        UUID relationId = UUID.randomUUID();
        try {
            relationRepository.insert(new NewRelation(
                relationId,
                user.workspaceId(),
                spaceId,
                binding.relationKey(),
                binding.kind(),
                binding.direction(),
                binding.definitionTypeId(),
                binding.definitionVersionId(),
                binding.definitionConfigHash(),
                endpoints.source().id(),
                endpoints.target().id(),
                user.id()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw failure(
                "RELATION_EDGE_CONFLICT",
                "An equivalent active relation already exists",
                exception
            );
        }
        WorkItemRelation relation = requireRelation(user, spaceId, relationId, false);
        hierarchyProjection.refreshAfterMutation(relation);
        appendHistory(receipt, relation, "created", user.id(), metadata(
            expectedSourceVersion, expectedTargetVersion, "create"
        ));
        RelationView result = view(
            user,
            spaceId,
            requireProjection(user, spaceId, relationId),
            sourceWorkItemId
        );
        complete(receipt, result);
        appendSideEffects(user, result, relation, "created", normalizedRequestId);
        return result;
    }

    public RelationView get(
        CurrentUser user,
        UUID spaceId,
        UUID relationId,
        UUID perspectiveWorkItemId
    ) {
        accessDecision.requireVisible(user, spaceId);
        return view(
            user,
            spaceId,
            requireProjection(user, spaceId, relationId),
            perspectiveWorkItemId
        );
    }

    public RelationPage list(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String relationKey,
        UUID cursor,
        int limit
    ) {
        accessDecision.requireVisible(user, spaceId);
        requireItem(user, spaceId, workItemId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        List<RelationProjection> rows = relationRepository.list(
            user.workspaceId(),
            spaceId,
            workItemId,
            relationKey == null || relationKey.isBlank() ? null : relationKey(relationKey),
            cursor,
            safeLimit + 1
        );
        boolean hasMore = rows.size() > safeLimit;
        List<RelationProjection> visible = hasMore ? rows.subList(0, safeLimit) : rows;
        List<RelationView> views = visible.stream()
            .map(row -> view(user, spaceId, row, workItemId))
            .toList();
        return new RelationPage(
            views,
            hasMore && !visible.isEmpty()
                ? visible.get(visible.size() - 1).relation().id()
                : null
        );
    }

    public RelationCapabilities capabilities(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId
    ) {
        String normalizedKey = relationKey(relationKey);
        WorkItem source = requireItem(user, spaceId, sourceWorkItemId);
        WorkItem target = requireItem(user, spaceId, targetWorkItemId);
        runtimeAdapter.requireForCreate(source, target, normalizedKey);
        return accessDecision.capabilities(
            user, spaceId, normalizedKey, source, target, "new"
        );
    }

    @Transactional
    public RelationView withdraw(
        CurrentUser user,
        UUID spaceId,
        UUID relationId,
        long expectedRelationVersion,
        long expectedSourceVersion,
        long expectedTargetVersion,
        String reason,
        String requestId
    ) {
        accessDecision.requireWritable(user, spaceId);
        String normalizedReason = reason(reason);
        String normalizedRequestId = requestId(requestId);
        String requestHash = requestHash(user, "withdraw", Map.of(
            "relationId", relationId.toString(),
            "expectedRelationVersion", expectedRelationVersion,
            "expectedSourceVersion", expectedSourceVersion,
            "expectedTargetVersion", expectedTargetVersion,
            "reason", normalizedReason
        ));
        CommandReceipt receipt = begin(
            user, spaceId, relationId, "withdraw", normalizedRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }

        WorkItemRelation current = requireRelation(user, spaceId, relationId, true);
        if (!"active".equals(current.status()) || current.version() != expectedRelationVersion) {
            throw failure("RELATION_VERSION_CONFLICT", "Relation changed or is not active");
        }
        lockEndpoints(
            user, spaceId, current.sourceWorkItemId(), current.targetWorkItemId(),
            expectedSourceVersion, expectedTargetVersion
        );
        relationRepository.acquireGraphLock(
            user.workspaceId(), spaceId, current.relationKey()
        );
        if (relationRepository.withdraw(
            user.workspaceId(),
            spaceId,
            relationId,
            expectedRelationVersion,
            user.id(),
            hashText(normalizedReason)
        ) != 1) {
            throw failure("RELATION_VERSION_CONFLICT", "Relation changed or is not active");
        }
        WorkItemRelation withdrawn = requireRelation(user, spaceId, relationId, false);
        hierarchyProjection.refreshAfterMutation(withdrawn);
        appendHistory(receipt, withdrawn, "withdrawn", user.id(), metadata(
            expectedSourceVersion, expectedTargetVersion, "withdraw"
        ));
        RelationView result = view(
            user,
            spaceId,
            requireProjection(user, spaceId, relationId),
            current.sourceWorkItemId()
        );
        complete(receipt, result);
        appendSideEffects(user, result, withdrawn, "withdrawn", normalizedRequestId);
        return result;
    }

    @Transactional
    public RelationView restore(
        CurrentUser user,
        UUID spaceId,
        UUID relationId,
        long expectedRelationVersion,
        long expectedSourceVersion,
        long expectedTargetVersion,
        String requestId
    ) {
        accessDecision.requireWritable(user, spaceId);
        String normalizedRequestId = requestId(requestId);
        String requestHash = requestHash(user, "restore", Map.of(
            "relationId", relationId.toString(),
            "expectedRelationVersion", expectedRelationVersion,
            "expectedSourceVersion", expectedSourceVersion,
            "expectedTargetVersion", expectedTargetVersion
        ));
        CommandReceipt receipt = begin(
            user, spaceId, relationId, "restore", normalizedRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }

        WorkItemRelation current = requireRelation(user, spaceId, relationId, true);
        if (!"withdrawn".equals(current.status()) || current.version() != expectedRelationVersion) {
            throw failure("RELATION_VERSION_CONFLICT", "Relation changed or is not withdrawn");
        }
        LockedEndpoints locked = lockEndpoints(
            user, spaceId, current.sourceWorkItemId(), current.targetWorkItemId(),
            expectedSourceVersion, expectedTargetVersion
        );
        RelationDefinitionBinding binding = runtimeAdapter.requireStored(current);
        CanonicalEndpoints endpoints = new CanonicalEndpoints(locked.source(), locked.target());
        relationRepository.acquireGraphLock(
            user.workspaceId(), spaceId, current.relationKey()
        );
        validateAvailable(user, spaceId, binding, endpoints);
        try {
            if (relationRepository.restore(
                user.workspaceId(),
                spaceId,
                relationId,
                expectedRelationVersion,
                user.id()
            ) != 1) {
                throw failure("RELATION_VERSION_CONFLICT", "Relation changed or is not withdrawn");
            }
        } catch (DataIntegrityViolationException exception) {
            throw failure(
                "RELATION_EDGE_CONFLICT",
                "An equivalent active relation already exists",
                exception
            );
        }
        WorkItemRelation restored = requireRelation(user, spaceId, relationId, false);
        hierarchyProjection.refreshAfterMutation(restored);
        appendHistory(receipt, restored, "restored", user.id(), metadata(
            expectedSourceVersion, expectedTargetVersion, "restore"
        ));
        RelationView result = view(
            user,
            spaceId,
            requireProjection(user, spaceId, relationId),
            current.sourceWorkItemId()
        );
        complete(receipt, result);
        appendSideEffects(user, result, restored, "restored", normalizedRequestId);
        return result;
    }

    /**
     * Joins the caller's work-item lifecycle transaction. Restrict fails closed, detach creates
     * ordinary immutable withdrawal facts, and retain-history leaves the edge bound to the
     * archived endpoint. Restore never reactivates a withdrawn edge.
     */
    @Transactional
    public void beforeEndpointArchive(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String parentRequestId
    ) {
        accessDecision.requireWritable(user, spaceId);
        List<WorkItemRelation> relations = relationRepository.listActiveTouching(
            user.workspaceId(), spaceId, workItemId
        );
        for (WorkItemRelation relation : relations) {
            RelationDefinitionBinding binding = runtimeAdapter.requireStored(relation);
            if (binding.deletionPolicy() == DeletionPolicy.restrict) {
                throw failure(
                    "RELATION_LIFECYCLE_RESTRICTED",
                    "The work item has a relation whose definition restricts archiving"
                );
            }
            if (binding.deletionPolicy() == DeletionPolicy.detach) {
                WorkItem source = requireItem(user, spaceId, relation.sourceWorkItemId());
                WorkItem target = requireItem(user, spaceId, relation.targetWorkItemId());
                String requestId = lifecycleRequestId(
                    parentRequestId, relation.id(), "archive-detach"
                );
                withdraw(
                    user,
                    spaceId,
                    relation.id(),
                    relation.version(),
                    source.version(),
                    target.version(),
                    "endpoint_archived",
                    requestId
                );
            }
        }
    }

    public void requireEndpointDeleteAllowed(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId
    ) {
        accessDecision.requireWritable(user, spaceId);
        List<WorkItemRelation> relations = relationRepository.listActiveTouching(
            user.workspaceId(), spaceId, workItemId
        );
        if (relations.stream().anyMatch(relation ->
            runtimeAdapter.requireStored(relation).deletionPolicy() != DeletionPolicy.detach
        )) {
            throw failure(
                "RELATION_LIFECYCLE_RESTRICTED",
                "Retained or restricted relation history prevents hard deletion"
            );
        }
    }

    private void validateAvailable(
        CurrentUser user,
        UUID spaceId,
        RelationDefinitionBinding binding,
        CanonicalEndpoints endpoints
    ) {
        if (!"active".equals(endpoints.source().status())
            || !"active".equals(endpoints.target().status())) {
            throw failure(
                "RELATION_ENDPOINT_NOT_ACTIVE",
                "Both relation endpoints must be active"
            );
        }
        if (relationRepository.findActiveEdge(
            user.workspaceId(),
            spaceId,
            binding.relationKey(),
            endpoints.source().id(),
            endpoints.target().id()
        ).isPresent()) {
            throw failure(
                "RELATION_EDGE_CONFLICT",
                "An equivalent active relation already exists"
            );
        }
        if (binding.sourceCardinality() == Cardinality.one
            && relationRepository.countActiveOutgoing(
                user.workspaceId(), spaceId, binding.relationKey(), endpoints.source().id()
            ) > 0) {
            throw failure(
                "RELATION_SOURCE_CARDINALITY_EXCEEDED",
                "The source endpoint already has the maximum number of active relations"
            );
        }
        if (binding.targetCardinality() == Cardinality.one
            && relationRepository.countActiveIncoming(
                user.workspaceId(), spaceId, binding.relationKey(), endpoints.target().id()
            ) > 0) {
            throw failure(
                "RELATION_TARGET_CARDINALITY_EXCEEDED",
                "The target endpoint already has the maximum number of active relations"
            );
        }
        if (isAcyclic(binding.kind())
            && relationRepository.pathExists(
                user.workspaceId(),
                spaceId,
                binding.relationKey(),
                endpoints.target().id(),
                endpoints.source().id(),
                binding.maxDepth()
            )) {
            throw failure(
                "RELATION_CYCLE_DETECTED",
                "The relation would create a cycle"
            );
        }
    }

    private LockedEndpoints lockEndpoints(
        CurrentUser user,
        UUID spaceId,
        UUID sourceId,
        UUID targetId,
        long expectedSourceVersion,
        long expectedTargetVersion
    ) {
        WorkItem source;
        WorkItem target;
        if (sourceId.equals(targetId)) {
            source = workItemRepository.lock(user.workspaceId(), spaceId, sourceId)
                .orElseThrow(() -> failure(
                    "NOT_FOUND_OR_HIDDEN", "Relation endpoint is not available"
                ));
            target = source;
        } else if (sourceId.toString().compareTo(targetId.toString()) < 0) {
            source = lockItem(user, spaceId, sourceId);
            target = lockItem(user, spaceId, targetId);
        } else {
            target = lockItem(user, spaceId, targetId);
            source = lockItem(user, spaceId, sourceId);
        }
        if (source.version() != expectedSourceVersion
            || target.version() != expectedTargetVersion) {
            throw failure(
                "RELATION_ENDPOINT_VERSION_CONFLICT",
                "A relation endpoint changed concurrently"
            );
        }
        return new LockedEndpoints(source, target);
    }

    private WorkItem lockItem(CurrentUser user, UUID spaceId, UUID workItemId) {
        return workItemRepository.lock(user.workspaceId(), spaceId, workItemId)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Relation endpoint is not available"
            ));
    }

    private WorkItem requireItem(CurrentUser user, UUID spaceId, UUID workItemId) {
        accessDecision.requireVisible(user, spaceId);
        return workItemRepository.find(user.workspaceId(), spaceId, workItemId)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Relation endpoint is not available"
            ));
    }

    private WorkItemRelation requireRelation(
        CurrentUser user,
        UUID spaceId,
        UUID relationId,
        boolean lock
    ) {
        return relationRepository.find(user.workspaceId(), spaceId, relationId, lock)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Work item relation is not available"
            ));
    }

    private RelationProjection requireProjection(
        CurrentUser user,
        UUID spaceId,
        UUID relationId
    ) {
        return relationRepository.findProjection(user.workspaceId(), spaceId, relationId)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Work item relation is not available"
            ));
    }

    private CanonicalEndpoints canonical(
        RelationDefinitionBinding binding,
        WorkItem source,
        WorkItem target
    ) {
        if (binding.direction() == Direction.undirected
            && source.id().toString().compareTo(target.id().toString()) > 0) {
            return new CanonicalEndpoints(target, source);
        }
        return new CanonicalEndpoints(source, target);
    }

    private void validateSelf(
        RelationDefinitionBinding binding,
        CanonicalEndpoints endpoints
    ) {
        if (endpoints.source().id().equals(endpoints.target().id())
            && (!binding.allowSelf() || binding.kind() != RelationKind.normal)) {
            throw failure(
                "RELATION_SELF_EDGE_REJECTED",
                "The relation definition does not allow a self edge"
            );
        }
    }

    private boolean isAcyclic(RelationKind kind) {
        return kind == RelationKind.parent_child
            || kind == RelationKind.dependency
            || kind == RelationKind.blocking;
    }

    private RelationView view(
        CurrentUser user,
        UUID spaceId,
        RelationProjection projection,
        UUID perspectiveWorkItemId
    ) {
        WorkItemRelation relation = projection.relation();
        boolean targetPerspective = perspectiveWorkItemId != null
            && perspectiveWorkItemId.equals(relation.targetWorkItemId())
            && !perspectiveWorkItemId.equals(relation.sourceWorkItemId());
        boolean reverse = relation.direction() == Direction.directed && targetPerspective;
        String perspective = perspectiveWorkItemId == null
            ? "source"
            : perspectiveWorkItemId.equals(relation.sourceWorkItemId())
                && perspectiveWorkItemId.equals(relation.targetWorkItemId())
                ? "self"
                : targetPerspective ? "target" : "source";
        RelationCapabilities capabilities = accessDecision.capabilities(
            user,
            spaceId,
            relation.relationKey(),
            endpointItem(projection.source(), relation),
            endpointItem(projection.target(), relation),
            relation.status()
        );
        List<String> actions = new ArrayList<>();
        if (capabilities.canWithdraw()) {
            actions.add("withdraw");
        }
        if (capabilities.canRestore()) {
            actions.add("restore");
        }
        return new RelationView(
            relation.id(),
            relation.relationKey(),
            relation.kind().name(),
            relation.direction().name(),
            relation.status(),
            relation.version(),
            relation.definitionVersionId(),
            relation.definitionConfigHash(),
            projection.source(),
            projection.target(),
            perspective,
            reverse ? projection.reverseName() : projection.forwardName(),
            reverse,
            List.copyOf(actions),
            relation.createdAt(),
            relation.updatedAt()
        );
    }

    private WorkItem endpointItem(
        com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationEndpoint endpoint,
        WorkItemRelation relation
    ) {
        return new WorkItem(
            endpoint.id(),
            relation.workspaceId(),
            relation.spaceId(),
            endpoint.typeDefinitionId(),
            endpoint.typeVersionId(),
            endpoint.typeKey(),
            "",
            relation.definitionConfigHash(),
            1,
            endpoint.displayKey(),
            endpoint.title(),
            objectMapper.createObjectNode(),
            endpoint.status(),
            endpoint.version(),
            relation.createdBy(),
            relation.createdAt(),
            relation.updatedBy(),
            relation.updatedAt(),
            null
        );
    }

    private CommandReceipt begin(
        CurrentUser user,
        UUID spaceId,
        UUID relationId,
        String operation,
        String requestId,
        String requestHash
    ) {
        relationRepository.tryStartCommand(new CommandStart(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            relationId,
            operation,
            requestId,
            requestHash,
            user.id()
        ));
        CommandReceipt receipt = relationRepository.findCommand(
            user.workspaceId(), spaceId, operation, requestId
        ).orElseThrow(() -> failure(
            "IDEMPOTENCY_CONFLICT", "Relation command receipt is unavailable"
        ));
        if (!receipt.requestHash().equals(requestHash)
            || !receipt.createdBy().equals(user.id())
            || (relationId != null && !relationId.equals(receipt.relationId()))) {
            throw failure(
                "IDEMPOTENCY_KEY_REUSED",
                "Request id was already used with different relation input"
            );
        }
        if (!"pending".equals(receipt.status()) && !"completed".equals(receipt.status())) {
            throw failure(
                "IDEMPOTENCY_CONFLICT",
                "Relation command receipt has an invalid state"
            );
        }
        return receipt;
    }

    private RelationView replay(CommandReceipt receipt) {
        if (receipt.response() == null) {
            throw failure(
                "IDEMPOTENCY_IN_PROGRESS",
                "The original relation command is still in progress"
            );
        }
        try {
            return objectMapper.treeToValue(receipt.response(), RelationView.class);
        } catch (JsonProcessingException exception) {
            throw failure(
                "IDEMPOTENCY_CONFLICT",
                "Stored relation response is invalid",
                exception
            );
        }
    }

    private void complete(CommandReceipt receipt, RelationView result) {
        relationRepository.completeCommand(
            receipt.id(),
            result.id(),
            result.version(),
            objectMapper.valueToTree(result)
        );
    }

    private void appendHistory(
        CommandReceipt receipt,
        WorkItemRelation relation,
        String eventKind,
        UUID actorId,
        JsonNode safeMetadata
    ) {
        relationRepository.appendHistory(new HistoryAppend(
            UUID.randomUUID(),
            relation.workspaceId(),
            relation.spaceId(),
            relation.id(),
            relation.version(),
            eventKind,
            relation.relationKey(),
            relation.sourceWorkItemId(),
            relation.targetWorkItemId(),
            relation.definitionTypeId(),
            relation.definitionVersionId(),
            relation.definitionConfigHash(),
            receipt.id(),
            safeMetadata,
            actorId
        ));
    }

    private void appendSideEffects(
        CurrentUser user,
        RelationView result,
        WorkItemRelation relation,
        String mutation,
        String requestId
    ) {
        ObjectNode activity = objectMapper.createObjectNode();
        activity.put("relationId", relation.id().toString());
        activity.put("relationKey", relation.relationKey());
        activity.put("mutation", mutation);
        activity.put("relationVersion", relation.version());
        activity.put("sourceWorkItemId", relation.sourceWorkItemId().toString());
        activity.put("targetWorkItemId", relation.targetWorkItemId().toString());
        workItemRepository.appendActivity(
            UUID.randomUUID(),
            user.workspaceId(),
            relation.spaceId(),
            relation.sourceWorkItemId(),
            "relation_" + mutation,
            user.id(),
            activity
        );
        if (!relation.sourceWorkItemId().equals(relation.targetWorkItemId())) {
            workItemRepository.appendActivity(
                UUID.randomUUID(),
                user.workspaceId(),
                relation.spaceId(),
                relation.targetWorkItemId(),
                "relation_" + mutation,
                user.id(),
                activity
            );
        }
        auditLog.log(
            user,
            "work_item_relation." + mutation,
            WorkItemRelationChangedEvent.AGGREGATE_TYPE,
            relation.id(),
            Map.of(
                "spaceId", relation.spaceId().toString(),
                "relationKey", relation.relationKey(),
                "sourceWorkItemId", relation.sourceWorkItemId().toString(),
                "targetWorkItemId", relation.targetWorkItemId().toString(),
                "definitionVersionId", relation.definitionVersionId().toString(),
                "relationVersion", relation.version()
            )
        );
        WorkItemRelationChangedEvent event = new WorkItemRelationChangedEvent(
            relation.spaceId(),
            relation.id(),
            relation.relationKey(),
            relation.sourceWorkItemId(),
            relation.targetWorkItemId(),
            relation.version(),
            mutation
        );
        outbox.append(
            user.workspaceId(),
            WorkItemRelationChangedEvent.EVENT_TYPE,
            WorkItemRelationChangedEvent.AGGREGATE_TYPE,
            relation.id(),
            user.id(),
            event.payload(),
            "work_item_relation:" + relation.id() + ":" + mutation + ":" + requestId
        );
    }

    private JsonNode metadata(
        long sourceVersion,
        long targetVersion,
        String operation
    ) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("operation", operation);
        result.put("sourceWorkItemVersion", sourceVersion);
        result.put("targetWorkItemVersion", targetVersion);
        return result;
    }

    private String requestHash(
        CurrentUser user,
        String operation,
        Map<String, Object> payload
    ) {
        return canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", user.id().toString(),
            "operation", operation,
            "payload", payload
        )));
    }

    private String hashText(String value) {
        return canonicalizer.hash(objectMapper.valueToTree(Map.of("value", value)));
    }

    private String relationKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!com.colla.platform.modules.project.domain.WorkItemRelationModels.SEMANTIC_KEY
            .matcher(normalized).matches()) {
            throw failure(
                "INVALID_RELATION_KEY",
                "Relation key must match [a-z][a-z0-9_]* and contain at most 64 characters"
            );
        }
        return normalized;
    }

    private String requestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw failure(
                "INVALID_REQUEST_ID",
                "Request id must contain 1 to 120 characters"
            );
        }
        return normalized;
    }

    private String reason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw failure(
                "INVALID_RELATION_REASON",
                "Relation withdrawal reason must contain 1 to 500 characters"
            );
        }
        return normalized;
    }

    private String lifecycleRequestId(
        String parentRequestId,
        UUID relationId,
        String operation
    ) {
        return "lifecycle-" + UUID.nameUUIDFromBytes((
            parentRequestId + ":" + relationId + ":" + operation
        ).getBytes(StandardCharsets.UTF_8));
    }

    private record LockedEndpoints(WorkItem source, WorkItem target) {
    }

    private record CanonicalEndpoints(WorkItem source, WorkItem target) {
    }
}
