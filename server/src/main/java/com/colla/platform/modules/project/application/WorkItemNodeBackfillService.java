package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillFailure;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillVerification;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowInstance;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeBackfillRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeBackfillRepository.BatchCreate;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeBackfillRepository.BatchRecord;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeBackfillRepository.UnitCreate;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeBackfillRepository.UnitRecord;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemNodeRuntimeAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public final class WorkItemNodeBackfillService {
    private static final int MAX_BATCH_SIZE = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkItemNodeBackfillService.class);

    private final WorkItemNodeBackfillRepository backfillRepository;
    private final WorkItemNodeWorkflowRepository nodeRepository;
    private final WorkItemRepository workItemRepository;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemNodeRuntimeAdapter runtimeAdapter;
    private final WorkItemNodeWorkflowService nodeWorkflowService;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final AuditLog auditLog;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public WorkItemNodeBackfillService(
        WorkItemNodeBackfillRepository backfillRepository,
        WorkItemNodeWorkflowRepository nodeRepository,
        WorkItemRepository workItemRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemNodeRuntimeAdapter runtimeAdapter,
        WorkItemNodeWorkflowService nodeWorkflowService,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        AuditLog auditLog,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.backfillRepository = backfillRepository;
        this.nodeRepository = nodeRepository;
        this.workItemRepository = workItemRepository;
        this.snapshotAdapter = snapshotAdapter;
        this.runtimeAdapter = runtimeAdapter;
        this.nodeWorkflowService = nodeWorkflowService;
        this.canonicalizer = canonicalizer;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public NodeBackfillBatch createAndExecute(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetEntryNodeKey,
        List<UUID> workItemIds,
        String reason,
        String confirmation,
        String requestId
    ) {
        requireManager(space);
        requireConfirmation(confirmation, "INITIALIZE_EXISTING_NODE_WORKFLOWS");
        String safeReason = reason(reason);
        String safeRequestId = requestId(requestId);
        List<UUID> manifest = manifest(workItemIds);
        RuntimeConfiguration target = snapshotAdapter.requireComplete(
            actor.workspaceId(), space.id(), typeDefinitionId, targetTypeVersionId
        );
        var targetFlow = runtimeAdapter.adapt(target);
        if (!targetFlow.configured()
            || !targetFlow.nodes().containsKey(targetEntryNodeKey)) {
            throw failure(
                "NODE_BACKFILL_ENTRY_INVALID",
                "Backfill entry must exist in the target snapshot"
            );
        }
        List<WorkItem> sourceItems = manifest.stream().map(workItemId -> {
            WorkItem item = workItemRepository.find(
                actor.workspaceId(), space.id(), workItemId
            ).orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Backfill work item is not available"
            ));
            if (!item.typeDefinitionId().equals(typeDefinitionId)) {
                throw failure(
                    "NODE_WORKFLOW_BINDING_CONFLICT",
                    "Backfill manifest contains a work item of another type"
                );
            }
            return item;
        }).toList();
        String reasonHash = canonicalizer.hash(objectMapper.valueToTree(safeReason));
        String manifestHash = canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "workItemIds", manifest.stream().map(UUID::toString).toList(),
            "targetTypeVersionId", targetTypeVersionId.toString(),
            "targetConfigHash", target.configHash(),
            "targetEntryNodeKey", targetEntryNodeKey
        )));
        String requestHash = canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", actor.id().toString(),
            "manifestHash", manifestHash,
            "reasonHash", reasonHash,
            "confirmation", confirmation
        )));
        UUID batchId = UUID.randomUUID();
        boolean created = backfillRepository.tryCreate(new BatchCreate(
            batchId, actor.workspaceId(), space.id(), typeDefinitionId,
            targetTypeVersionId, target.configHash(), targetEntryNodeKey,
            manifest.size(), manifestHash, safeRequestId, requestHash,
            reasonHash, actor.id()
        ));
        BatchRecord batch = backfillRepository.findByRequest(
            actor.workspaceId(), space.id(), safeRequestId
        ).orElseThrow(() -> failure(
            "IDEMPOTENCY_CONFLICT", "Node backfill manifest is unavailable"
        ));
        if (!batch.requestHash().equals(requestHash)
            || !batch.createdBy().equals(actor.id())) {
            throw failure(
                "IDEMPOTENCY_KEY_REUSED",
                "Backfill request id was already used with different input"
            );
        }
        if (!created) {
            return batch.batch();
        }
        for (WorkItem item : sourceItems) {
            backfillRepository.insertUnit(new UnitCreate(
                actor.workspaceId(), space.id(), batch.batch().id(), item.id(),
                item.typeVersionId(), item.configHash(), item.version()
            ));
        }
        auditLog.log(actor, "node_workflow.backfill_planned", "project_space", space.id(), Map.of(
            "batchId", batch.batch().id().toString(),
            "manifestHash", manifestHash,
            "requestedCount", manifest.size(),
            "targetTypeVersionId", targetTypeVersionId.toString(),
            "targetEntryNodeKey", targetEntryNodeKey,
            "reasonHash", reasonHash
        ));
        return execute(actor, space, batch.batch().id());
    }

    public NodeBackfillBatch resume(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID batchId,
        String confirmation
    ) {
        requireManager(space);
        requireConfirmation(confirmation, "RESUME_NODE_WORKFLOW_BACKFILL");
        return execute(actor, space, batchId);
    }

    public NodeBackfillVerification verify(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID batchId
    ) {
        requireManager(space);
        BatchRecord batch = requireBatch(actor, space, batchId);
        ArrayList<NodeBackfillFailure> failures = new ArrayList<>(
            backfillRepository.failures(actor.workspaceId(), space.id(), batchId)
        );
        int verified = 0;
        for (UnitRecord unit : backfillRepository.allUnits(
            actor.workspaceId(), space.id(), batchId
        )) {
            if (!"completed".equals(unit.status())) {
                continue;
            }
            WorkItem item = workItemRepository.find(
                actor.workspaceId(), space.id(), unit.workItemId()
            ).orElse(null);
            NodeWorkflowInstance instance = nodeRepository.findInstance(
                actor.workspaceId(), space.id(), unit.workItemId()
            ).orElse(null);
            if (item == null || instance == null
                || !item.typeVersionId().equals(batch.batch().targetTypeVersionId())
                || !item.configHash().equals(batch.batch().targetConfigHash())
                || !instance.typeVersionId().equals(batch.batch().targetTypeVersionId())
                || !instance.configHash().equals(batch.batch().targetConfigHash())
                || instance.workItemVersion() != item.version()) {
                failures.add(new NodeBackfillFailure(
                    unit.workItemId(), "NODE_BACKFILL_VERIFICATION_DRIFT",
                    "Backfilled work item binding or node instance drifted"
                ));
            } else {
                verified++;
            }
        }
        String status = failures.isEmpty()
            && verified == batch.batch().requestedCount() ? "verified" : "failed";
        auditLog.log(actor, "node_workflow.backfill_verified", "project_space", space.id(), Map.of(
            "batchId", batchId.toString(),
            "status", status,
            "verifiedCount", verified,
            "failureCount", failures.size()
        ));
        return new NodeBackfillVerification(
            batchId, status, verified, List.copyOf(failures)
        );
    }

    private NodeBackfillBatch execute(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID batchId
    ) {
        BatchRecord batch = requireBatch(actor, space, batchId);
        backfillRepository.markRunning(actor.workspaceId(), space.id(), batchId);
        for (UnitRecord unit : backfillRepository.retryableUnits(
            actor.workspaceId(), space.id(), batchId
        )) {
            try {
                requiresNew.executeWithoutResult(ignored -> {
                    WorkItem source = workItemRepository.find(
                        actor.workspaceId(), space.id(), unit.workItemId()
                    ).orElseThrow(() -> failure(
                        "NOT_FOUND_OR_HIDDEN", "Backfill work item is unavailable"
                    ));
                    if (!source.typeVersionId().equals(unit.sourceTypeVersionId())
                        || !source.configHash().equals(unit.sourceConfigHash())
                        || source.version() != unit.sourceWorkItemVersion()) {
                        throw failure(
                            "NODE_BACKFILL_SOURCE_CHANGED",
                            "Work item changed after manifest freeze"
                        );
                    }
                    var result = nodeWorkflowService.backfillInitialize(
                        actor, source, batch.batch().targetTypeVersionId(),
                        batch.batch().targetEntryNodeKey(),
                        unit.sourceWorkItemVersion(), batchId
                    );
                    backfillRepository.markCompleted(
                        actor.workspaceId(), space.id(), batchId,
                        unit.workItemId(), result.workItemVersion()
                    );
                });
            } catch (RuntimeException exception) {
                String code = exception instanceof WorkItemRuntimeException runtime
                    ? runtime.code() : "NODE_BACKFILL_UNIT_FAILED";
                LOGGER.warn(
                    "node_workflow_backfill_unit_failed batchId={} workItemId={} code={}",
                    batchId, unit.workItemId(), code, exception
                );
                requiresNew.executeWithoutResult(ignored -> backfillRepository.markFailed(
                    actor.workspaceId(), space.id(), batchId, unit.workItemId(),
                    code, "Node backfill unit failed with " + code
                ));
            }
        }
        NodeBackfillBatch result = backfillRepository.refreshSummary(
            actor.workspaceId(), space.id(), batchId
        );
        auditLog.log(actor, "node_workflow.backfill_executed", "project_space", space.id(), Map.of(
            "batchId", batchId.toString(),
            "status", result.status(),
            "completedCount", result.completedCount(),
            "failedCount", result.failedCount()
        ));
        return result;
    }

    private BatchRecord requireBatch(
        CurrentUser actor, ProjectSpaceSummary space, UUID batchId
    ) {
        return backfillRepository.find(
            actor.workspaceId(), space.id(), batchId
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Node backfill batch is not available"
        ));
    }

    private List<UUID> manifest(List<UUID> values) {
        if (values == null || values.isEmpty() || values.size() > MAX_BATCH_SIZE) {
            throw failure(
                "INVALID_BACKFILL_MANIFEST",
                "Backfill manifest must contain 1 to 500 work item ids"
            );
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(values);
        if (unique.contains(null) || unique.size() != values.size()) {
            throw failure(
                "INVALID_BACKFILL_MANIFEST",
                "Backfill manifest must contain unique non-null work item ids"
            );
        }
        return unique.stream().sorted().toList();
    }

    private void requireManager(ProjectSpaceSummary space) {
        if (!space.canManage()) {
            throw failure(
                "FORBIDDEN",
                "Only project space owners and admins may run node workflow backfill"
            );
        }
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
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw failure(
                "INVALID_RECOVERY_REASON",
                "Backfill reason must contain 10 to 500 characters"
            );
        }
        return normalized;
    }

    private void requireConfirmation(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw failure(
                "DANGEROUS_CONFIRMATION_REQUIRED",
                "The exact dangerous-operation confirmation is required"
            );
        }
    }
}
