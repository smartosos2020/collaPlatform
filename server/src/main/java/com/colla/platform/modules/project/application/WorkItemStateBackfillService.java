package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.CanonicalValues;
import com.colla.platform.modules.project.contract.WorkItemWorkflowEvent;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.CurrentState;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.RuntimeFlow;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillFailure;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillVerification;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemStateBackfillRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemStateBackfillRepository.BatchCreate;
import com.colla.platform.modules.project.infrastructure.WorkItemStateBackfillRepository.BatchRecord;
import com.colla.platform.modules.project.infrastructure.WorkItemStateBackfillRepository.UnitCreate;
import com.colla.platform.modules.project.infrastructure.WorkItemStateBackfillRepository.UnitRecord;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository.CurrentStateInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository.HistoryAppend;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemStateRuntimeAdapter;
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

@Service
public final class WorkItemStateBackfillService {
    private static final int MAX_BATCH_SIZE = 500;

    private final WorkItemStateBackfillRepository backfillRepository;
    private final WorkItemStateFlowRepository stateRepository;
    private final WorkItemRepository workItemRepository;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemStateRuntimeAdapter runtimeAdapter;
    private final WorkItemFieldValueCodec valueCodec;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public WorkItemStateBackfillService(
        WorkItemStateBackfillRepository backfillRepository,
        WorkItemStateFlowRepository stateRepository,
        WorkItemRepository workItemRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemStateRuntimeAdapter runtimeAdapter,
        WorkItemFieldValueCodec valueCodec,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.backfillRepository = backfillRepository;
        this.stateRepository = stateRepository;
        this.workItemRepository = workItemRepository;
        this.snapshotAdapter = snapshotAdapter;
        this.runtimeAdapter = runtimeAdapter;
        this.valueCodec = valueCodec;
        this.canonicalizer = canonicalizer;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public StateBackfillBatch createAndExecute(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetStateKey,
        List<UUID> workItemIds,
        String reason,
        String confirmation,
        String requestId
    ) {
        requireManager(space);
        requireConfirmation(confirmation, "INITIALIZE_EXISTING_WORKFLOW_STATES");
        String safeReason = reason(reason);
        String safeRequestId = requestId(requestId);
        List<UUID> manifest = manifest(workItemIds);
        RuntimeConfiguration target = snapshotAdapter.requireComplete(
            actor.workspaceId(), space.id(), typeDefinitionId, targetTypeVersionId
        );
        RuntimeFlow targetFlow = runtimeAdapter.adapt(target);
        if (!targetFlow.configured()
            || !targetFlow.initialState().stateKey().equals(targetStateKey)) {
            throw failure(
                "WORKFLOW_STATE_MAPPING_REQUIRED",
                "Backfill target must be the target snapshot initial state"
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
                    "WORKFLOW_BINDING_CONFLICT",
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
            "targetStateKey", targetStateKey
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
            targetTypeVersionId, target.configHash(), targetStateKey, manifest.size(),
            manifestHash, safeRequestId, requestHash, reasonHash, actor.id()
        ));
        BatchRecord batch = backfillRepository.findByRequest(
            actor.workspaceId(), space.id(), safeRequestId
        ).orElseThrow(() -> failure(
            "IDEMPOTENCY_CONFLICT", "State backfill manifest is unavailable"
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
                item.typeVersionId(), item.configHash(), item.version(), targetStateKey
            ));
        }
        auditLog.log(actor, "workflow.backfill_planned", "project_space", space.id(), Map.of(
            "batchId", batch.batch().id().toString(),
            "manifestHash", manifestHash,
            "requestedCount", manifest.size(),
            "targetTypeVersionId", targetTypeVersionId.toString(),
            "targetStateKey", targetStateKey,
            "reasonHash", reasonHash
        ));
        return execute(actor, space, batch.batch().id());
    }

    public StateBackfillBatch resume(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID batchId,
        String confirmation
    ) {
        requireManager(space);
        requireConfirmation(confirmation, "RESUME_WORKFLOW_STATE_BACKFILL");
        return execute(actor, space, batchId);
    }

    public StateBackfillVerification verify(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID batchId
    ) {
        requireManager(space);
        BatchRecord batch = requireBatch(actor, space, batchId);
        ArrayList<StateBackfillFailure> failures = new ArrayList<>(
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
            CurrentState current = stateRepository.findCurrent(
                actor.workspaceId(), space.id(), unit.workItemId()
            ).orElse(null);
            if (item == null || current == null
                || !item.typeVersionId().equals(batch.batch().targetTypeVersionId())
                || !item.configHash().equals(batch.batch().targetConfigHash())
                || !current.typeVersionId().equals(batch.batch().targetTypeVersionId())
                || !current.configHash().equals(batch.batch().targetConfigHash())
                || !current.stateKey().equals(batch.batch().targetStateKey())
                || current.workItemVersion() != item.version()) {
                failures.add(new StateBackfillFailure(
                    unit.workItemId(), "BACKFILL_VERIFICATION_DRIFT",
                    "Backfilled work item binding or current state does not match the manifest"
                ));
            } else {
                verified++;
            }
        }
        String status = failures.isEmpty()
            && verified == batch.batch().requestedCount() ? "verified" : "failed";
        auditLog.log(actor, "workflow.backfill_verified", "project_space", space.id(), Map.of(
            "batchId", batchId.toString(),
            "status", status,
            "verifiedCount", verified,
            "failureCount", failures.size()
        ));
        return new StateBackfillVerification(
            batchId, status, verified, List.copyOf(failures)
        );
    }

    private StateBackfillBatch execute(
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
                requiresNew.executeWithoutResult(ignored ->
                    executeUnit(actor, space, batch, unit)
                );
            } catch (RuntimeException exception) {
                String code = exception instanceof WorkItemRuntimeException runtime
                    ? runtime.code() : "BACKFILL_UNIT_FAILED";
                requiresNew.executeWithoutResult(ignored -> backfillRepository.markFailed(
                    actor.workspaceId(), space.id(), batchId, unit.workItemId(),
                    code, "Backfill unit failed with " + code
                ));
            }
        }
        StateBackfillBatch result = backfillRepository.refreshSummary(
            actor.workspaceId(), space.id(), batchId
        );
        auditLog.log(actor, "workflow.backfill_executed", "project_space", space.id(), Map.of(
            "batchId", batchId.toString(),
            "status", result.status(),
            "completedCount", result.completedCount(),
            "failedCount", result.failedCount()
        ));
        return result;
    }

    private void executeUnit(
        CurrentUser actor,
        ProjectSpaceSummary space,
        BatchRecord batch,
        UnitRecord unit
    ) {
        WorkItem item = workItemRepository.lock(
            actor.workspaceId(), space.id(), unit.workItemId()
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Backfill work item is not available"
        ));
        if (!item.typeDefinitionId().equals(batch.batch().typeDefinitionId())
            || !item.typeVersionId().equals(unit.sourceTypeVersionId())
            || !item.configHash().equals(unit.sourceConfigHash())
            || item.version() != unit.sourceWorkItemVersion()) {
            throw failure(
                "BACKFILL_SOURCE_CHANGED",
                "Work item changed after the backfill manifest was frozen"
            );
        }
        if (stateRepository.findCurrent(
            actor.workspaceId(), space.id(), item.id()
        ).isPresent()) {
            throw failure(
                "WORKFLOW_ALREADY_INITIALIZED",
                "Work item already has workflow state"
            );
        }
        RuntimeConfiguration target = snapshotAdapter.requireComplete(
            actor.workspaceId(), space.id(), item.typeDefinitionId(),
            batch.batch().targetTypeVersionId()
        );
        RuntimeFlow flow = runtimeAdapter.adapt(target);
        if (!flow.configured()
            || !flow.initialState().stateKey().equals(batch.batch().targetStateKey())) {
            throw failure(
                "WORKFLOW_STATE_MAPPING_REQUIRED",
                "Backfill target snapshot or initial state changed"
            );
        }
        CanonicalValues values = valueCodec.canonicalize(target, item.fieldValues());
        long targetVersion = item.version() + 1;
        if (workItemRepository.workflowBindingUpdate(
            actor.workspaceId(), space.id(), item.id(), item.typeVersionId(),
            item.configHash(), target.versionId(), target.configHash(), values.values(),
            actor.id(), item.version()
        ) != 1) {
            throw failure(
                "WORKFLOW_VERSION_CONFLICT",
                "Work item changed while applying state backfill"
            );
        }
        workItemRepository.replaceFieldProjections(
            actor.workspaceId(), space.id(), item.id(), values.projections()
        );
        if (!stateRepository.tryInitialize(new CurrentStateInsert(
            actor.workspaceId(), space.id(), item.id(), item.typeDefinitionId(),
            target.versionId(), target.configHash(), batch.batch().targetStateKey(),
            targetVersion, actor.id()
        ))) {
            throw failure(
                "WORKFLOW_INITIALIZATION_CONFLICT",
                "Workflow current state could not be initialized"
            );
        }
        String correlationId = "workflow-backfill:" + batch.batch().id() + ":" + item.id();
        stateRepository.appendHistory(new HistoryAppend(
            UUID.randomUUID(), actor.workspaceId(), space.id(), item.id(), 1,
            item.typeDefinitionId(), target.versionId(), target.configHash(), null,
            batch.batch().targetStateKey(), null, "initialize", actor.id(), "system",
            flow.policyVersion() + ":backfill", correlationId,
            batch.batch().id().toString(), objectMapper.valueToTree(Map.of(
                "provenance", "explicit_backfill",
                "batchId", batch.batch().id().toString(),
                "sourceTypeVersionId", item.typeVersionId().toString()
            ))
        ));
        workItemRepository.appendActivity(
            UUID.randomUUID(), actor.workspaceId(), space.id(), item.id(),
            "workflow.initialized", actor.id(), objectMapper.valueToTree(Map.of(
                "provenance", "explicit_backfill",
                "batchId", batch.batch().id().toString(),
                "targetTypeVersionId", target.versionId().toString(),
                "targetStateKey", batch.batch().targetStateKey(),
                "workItemVersion", targetVersion
            ))
        );
        WorkItemWorkflowEvent event = new WorkItemWorkflowEvent(
            space.id(), item.typeDefinitionId(), target.versionId(), target.configHash(),
            "backfill_initialize", "initialize", null, batch.batch().targetStateKey(),
            targetVersion, 0, flow.policyVersion() + ":backfill"
        );
        outbox.append(
            actor.workspaceId(), WorkItemWorkflowEvent.INITIALIZED,
            WorkItemWorkflowEvent.AGGREGATE_TYPE, item.id(), actor.id(), event.payload(),
            correlationId + ":initialized"
        );
        outbox.append(
            actor.workspaceId(), WorkItemWorkflowEvent.STATE_CHANGED,
            WorkItemWorkflowEvent.AGGREGATE_TYPE, item.id(), actor.id(), event.payload(),
            correlationId + ":state"
        );
        auditLog.log(actor, "workflow.backfill_unit_completed", "work_item", item.id(), Map.of(
            "spaceId", space.id().toString(),
            "batchId", batch.batch().id().toString(),
            "sourceTypeVersionId", item.typeVersionId().toString(),
            "targetTypeVersionId", target.versionId().toString(),
            "targetStateKey", batch.batch().targetStateKey(),
            "workItemVersion", targetVersion
        ));
        backfillRepository.markCompleted(
            actor.workspaceId(), space.id(), batch.batch().id(), item.id(), targetVersion
        );
    }

    private BatchRecord requireBatch(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID batchId
    ) {
        return backfillRepository.find(
            actor.workspaceId(), space.id(), batchId
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "State backfill batch is not available"
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
                "Only project space owners and admins may run workflow backfill"
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
