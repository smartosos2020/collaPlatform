package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.CanonicalValues;
import com.colla.platform.modules.project.contract.WorkItemWorkflowEvent;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionKind;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.ActionDecision;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.CurrentState;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.DecisionContext;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.RuntimeFlow;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowBindingCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowPresentation;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository.CommandStart;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository.CurrentStateInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemStateFlowRepository.HistoryAppend;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemStateRuntimeAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemStateFlowService {
    private static final int MAX_HISTORY_PAGE = 200;

    private final WorkItemStateFlowRepository stateRepository;
    private final WorkItemRepository workItemRepository;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemStateRuntimeAdapter runtimeAdapter;
    private final WorkItemStateFlowDecisionService decisionService;
    private final WorkItemRuntimeProjection runtimeProjection;
    private final WorkItemFieldValueCodec valueCodec;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemStateFlowService(
        WorkItemStateFlowRepository stateRepository,
        WorkItemRepository workItemRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemStateRuntimeAdapter runtimeAdapter,
        WorkItemStateFlowDecisionService decisionService,
        WorkItemRuntimeProjection runtimeProjection,
        WorkItemFieldValueCodec valueCodec,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.stateRepository = stateRepository;
        this.workItemRepository = workItemRepository;
        this.snapshotAdapter = snapshotAdapter;
        this.runtimeAdapter = runtimeAdapter;
        this.decisionService = decisionService;
        this.runtimeProjection = runtimeProjection;
        this.valueCodec = valueCodec;
        this.canonicalizer = canonicalizer;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void initializeForNewItem(CurrentUser actor, WorkItem item) {
        RuntimeConfiguration configuration = configuration(item);
        RuntimeFlow flow = runtimeAdapter.adapt(configuration);
        if (!flow.configured()) {
            return;
        }
        boolean inserted = stateRepository.tryInitialize(new CurrentStateInsert(
            item.workspaceId(), item.spaceId(), item.id(), item.typeDefinitionId(),
            item.typeVersionId(), item.configHash(), flow.initialState().stateKey(),
            item.version(), actor.id()
        ));
        if (!inserted) {
            CurrentState existing = stateRepository.findCurrent(
                item.workspaceId(), item.spaceId(), item.id()
            ).orElseThrow(() -> failure(
                "WORKFLOW_INITIALIZATION_CONFLICT",
                "Workflow current state could not be initialized"
            ));
            verifyBinding(item, existing);
            return;
        }
        stateRepository.appendHistory(new HistoryAppend(
            UUID.randomUUID(), item.workspaceId(), item.spaceId(), item.id(), 1,
            item.typeDefinitionId(), item.typeVersionId(), item.configHash(), null,
            flow.initialState().stateKey(), null, "initialize", actor.id(),
            "user", flow.policyVersion() + ":initialize", "initialize:" + item.id(), null,
            objectMapper.createObjectNode()
        ));
    }

    public void alignWorkItemVersion(
        CurrentUser actor,
        WorkItem item,
        long expectedVersion,
        long targetVersion
    ) {
        RuntimeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            return;
        }
        if (stateRepository.alignWorkItemVersion(
            actor.workspaceId(), item.spaceId(), item.id(), expectedVersion, targetVersion, actor.id()
        ) != 1) {
            throw failure(
                "WORKFLOW_VERSION_CONFLICT",
                "Workflow and work item versions could not be aligned"
            );
        }
    }

    public WorkflowPresentation presentation(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem item
    ) {
        RuntimeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            return missing("not_configured", flow.policyVersion());
        }
        CurrentState current = stateRepository.findCurrent(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElse(null);
        if (current == null) {
            return missing("uninitialized", flow.policyVersion());
        }
        verifyBinding(item, current);
        var state = flow.states().get(current.stateKey());
        if (state == null) {
            throw failure("WORKFLOW_STATE_INVALID", "Stored workflow state is not in the bound snapshot");
        }
        Set<String> participantRoles = stateRepository.participantRoles(
            actor.workspaceId(), item.spaceId(), item.id(), actor.id()
        );
        return new WorkflowPresentation(
            "available", flow.policyVersion(), state.stateKey(), state.label(),
            state.category().name(), current.aggregateVersion(),
            !"active".equals(item.status()) ? List.of() : decisionService.available(
                flow,
                current.stateKey(),
                new DecisionContext(space.currentUserRole(), participantRoles, item.fieldValues())
            )
        );
    }

    public Map<UUID, WorkflowPresentation> presentations(
        CurrentUser actor,
        ProjectSpaceSummary space,
        List<WorkItem> items
    ) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = items.stream().map(WorkItem::id).toList();
        Map<UUID, CurrentState> currentStates = stateRepository.findCurrentBatch(
            actor.workspaceId(), space.id(), ids
        );
        Map<UUID, Set<String>> roles = stateRepository.participantRolesBatch(
            actor.workspaceId(), space.id(), ids, actor.id()
        );
        HashMap<String, RuntimeFlow> flowCache = new HashMap<>();
        LinkedHashMap<UUID, WorkflowPresentation> result = new LinkedHashMap<>();
        for (WorkItem item : items) {
            String binding = item.typeVersionId() + ":" + item.configHash();
            RuntimeFlow flow = flowCache.computeIfAbsent(
                binding, ignored -> runtimeAdapter.adapt(configuration(item))
            );
            if (!flow.configured()) {
                result.put(item.id(), missing("not_configured", flow.policyVersion()));
                continue;
            }
            CurrentState current = currentStates.get(item.id());
            if (current == null) {
                result.put(item.id(), missing("uninitialized", flow.policyVersion()));
                continue;
            }
            verifyBinding(item, current);
            var state = flow.states().get(current.stateKey());
            if (state == null) {
                throw failure("WORKFLOW_STATE_INVALID", "Stored workflow state is not in the bound snapshot");
            }
            result.put(item.id(), new WorkflowPresentation(
                "available", flow.policyVersion(), state.stateKey(), state.label(),
                state.category().name(), current.aggregateVersion(),
                !"active".equals(item.status()) ? List.of() : decisionService.available(
                    flow,
                    current.stateKey(),
                    new DecisionContext(
                        space.currentUserRole(),
                        roles.getOrDefault(item.id(), Set.of()),
                        item.fieldValues()
                    )
                )
            ));
        }
        return Map.copyOf(result);
    }

    public List<WorkflowHistoryEntry> history(
        CurrentUser actor,
        WorkItem item,
        Long beforeSequence,
        int limit
    ) {
        configuration(item);
        return stateRepository.pageHistory(
            actor.workspaceId(),
            item.spaceId(),
            item.id(),
            beforeSequence,
            Math.max(1, Math.min(limit, MAX_HISTORY_PAGE))
        );
    }

    @Transactional
    public WorkflowCommandResult execute(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        String actionKey,
        String fromStateKey,
        long expectedWorkItemVersion,
        JsonNode fieldPatch,
        String requestId
    ) {
        String safeAction = semanticKey(actionKey, "INVALID_WORKFLOW_ACTION");
        String safeFrom = semanticKey(fromStateKey, "INVALID_WORKFLOW_STATE");
        String safeRequestId = requestId(requestId);
        JsonNode safePatch = fieldPatch == null ? objectMapper.createObjectNode() : fieldPatch;
        String requestHash = canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", actor.id().toString(),
            "workItemId", suppliedItem.id().toString(),
            "actionKey", safeAction,
            "fromStateKey", safeFrom,
            "expectedWorkItemVersion", expectedWorkItemVersion,
            "fieldPatch", safePatch
        )));
        WorkItem item = workItemRepository.lock(
            actor.workspaceId(), suppliedItem.spaceId(), suppliedItem.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
        RuntimeConfiguration configuration = configuration(item);
        RuntimeFlow flow = runtimeAdapter.adapt(configuration);
        var definedAction = flow.actions().get(safeAction);
        String operation = definedAction == null
            ? "execute"
            : operation(definedAction.kind());
        CommandReceipt receipt = begin(
            actor, item, operation, safeAction, safeFrom, expectedWorkItemVersion,
            safeRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        if (!"active".equals(item.status())) {
            throw failure("WORKFLOW_NOT_WRITABLE", "Workflow actions require an active work item");
        }
        if (item.version() != expectedWorkItemVersion) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Work item version is stale");
        }
        if (!flow.configured()) {
            throw failure("WORKFLOW_CAPABILITY_MISSING", "The bound snapshot has no state flow");
        }
        CurrentState current = stateRepository.lockCurrent(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure(
            "WORKFLOW_NOT_INITIALIZED",
            "Workflow state has not been explicitly initialized"
        ));
        verifyBinding(item, current);
        if (!current.stateKey().equals(safeFrom)
            || current.workItemVersion() != expectedWorkItemVersion) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Workflow state or version is stale");
        }

        JsonNode userPrepared = runtimeProjection.prepareUpdate(
            configuration, space.currentUserRole(), space.status(), item.fieldValues(), safePatch
        );
        ObjectNode candidate = object(userPrepared).deepCopy();
        var action = flow.actions().get(safeAction);
        if (action != null && action.fieldPatch() != null && action.fieldPatch().isObject()) {
            action.fieldPatch().fields().forEachRemaining(entry -> {
                if (entry.getValue() == null || entry.getValue().isNull()) {
                    candidate.remove(entry.getKey());
                } else {
                    candidate.set(entry.getKey(), entry.getValue().deepCopy());
                }
            });
        }
        CanonicalValues values = valueCodec.canonicalize(configuration, candidate);
        Set<String> participantRoles = stateRepository.participantRoles(
            actor.workspaceId(), item.spaceId(), item.id(), actor.id()
        );
        ActionDecision decision = decisionService.decide(
            flow,
            current.stateKey(),
            safeAction,
            new DecisionContext(space.currentUserRole(), participantRoles, values.values())
        );
        requireAllowed(decision);

        long targetWorkItemVersion = expectedWorkItemVersion + 1;
        long targetAggregateVersion = current.aggregateVersion() + 1;
        if (workItemRepository.workflowUpdate(
            actor.workspaceId(), item.spaceId(), item.id(), values.values(),
            actor.id(), expectedWorkItemVersion
        ) != 1) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Work item version is stale");
        }
        workItemRepository.replaceFieldProjections(
            actor.workspaceId(), item.spaceId(), item.id(), values.projections()
        );
        if (stateRepository.compareAndSetState(
            actor.workspaceId(), item.spaceId(), item.id(), current.stateKey(),
            decision.transition().toStateKey(), expectedWorkItemVersion,
            targetWorkItemVersion, current.aggregateVersion(), actor.id()
        ) != 1) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Workflow state was changed concurrently");
        }

        String decisionReference = flow.policyVersion() + ":" + decision.transition().transitionKey();
        String correlationId = "workflow:" + item.id() + ":" + safeRequestId;
        stateRepository.appendHistory(new HistoryAppend(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(),
            targetAggregateVersion + 1, item.typeDefinitionId(), item.typeVersionId(),
            item.configHash(), current.stateKey(), decision.transition().toStateKey(),
            safeAction, historyKind(decision.action().kind()), actor.id(),
            "user", decisionReference, correlationId, safeRequestId,
            objectMapper.createObjectNode()
        ));
        workItemRepository.appendActivity(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(),
            "workflow.action_executed", actor.id(), objectMapper.valueToTree(Map.of(
                "actionKey", safeAction,
                "fromStateKey", current.stateKey(),
                "toStateKey", decision.transition().toStateKey(),
                "workItemVersion", targetWorkItemVersion,
                "aggregateVersion", targetAggregateVersion
            ))
        );
        WorkflowCommandResult result = new WorkflowCommandResult(
            item.id(), safeAction, current.stateKey(), decision.transition().toStateKey(),
            targetWorkItemVersion, targetAggregateVersion, false
        );
        stateRepository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        emit(actor, item, decision, targetWorkItemVersion, targetAggregateVersion,
            decisionReference, safeRequestId);
        auditLog.log(actor, "workflow.action_executed", "work_item", item.id(), Map.of(
            "spaceId", item.spaceId().toString(),
            "actionKey", safeAction,
            "fromStateKey", current.stateKey(),
            "toStateKey", decision.transition().toStateKey(),
            "workItemVersion", targetWorkItemVersion,
            "aggregateVersion", targetAggregateVersion,
            "decisionReference", decisionReference
        ));
        return result;
    }

    @Transactional
    public WorkflowCommandResult correct(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        String targetStateKey,
        long expectedWorkItemVersion,
        String reason,
        String confirmation,
        String requestId
    ) {
        requireConfirmation(confirmation, "CORRECT_WORKFLOW_STATE");
        String safeTarget = semanticKey(targetStateKey, "INVALID_WORKFLOW_STATE");
        String safeReason = reason(reason);
        String safeRequestId = requestId(requestId);
        String reasonHash = canonicalizer.hash(objectMapper.valueToTree(safeReason));
        String requestHash = canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", actor.id().toString(),
            "workItemId", suppliedItem.id().toString(),
            "targetStateKey", safeTarget,
            "expectedWorkItemVersion", expectedWorkItemVersion,
            "reasonHash", reasonHash,
            "confirmation", confirmation
        )));
        WorkItem item = lock(actor, suppliedItem);
        RuntimeConfiguration configuration = configuration(item);
        RuntimeFlow flow = runtimeAdapter.adapt(configuration);
        CommandReceipt receipt = begin(
            actor, item, "correct", "admin_correction", currentStateKey(actor, item),
            expectedWorkItemVersion, safeRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        requireActiveAndVersion(item, expectedWorkItemVersion);
        CurrentState current = lockedCurrent(actor, item);
        verifyCurrentVersion(current, expectedWorkItemVersion);
        if (!flow.configured() || !flow.states().containsKey(safeTarget)) {
            throw failure("WORKFLOW_STATE_INVALID", "Correction target is not in the bound snapshot");
        }
        CanonicalValues values = valueCodec.canonicalize(configuration, item.fieldValues());
        long targetWorkItemVersion = expectedWorkItemVersion + 1;
        long targetAggregateVersion = current.aggregateVersion() + 1;
        if (workItemRepository.workflowUpdate(
            actor.workspaceId(), item.spaceId(), item.id(), values.values(),
            actor.id(), expectedWorkItemVersion
        ) != 1) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Work item version is stale");
        }
        workItemRepository.replaceFieldProjections(
            actor.workspaceId(), item.spaceId(), item.id(), values.projections()
        );
        if (stateRepository.compareAndSetState(
            actor.workspaceId(), item.spaceId(), item.id(), current.stateKey(), safeTarget,
            expectedWorkItemVersion, targetWorkItemVersion, current.aggregateVersion(), actor.id()
        ) != 1) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Workflow state was changed concurrently");
        }
        String decisionReference = flow.policyVersion() + ":admin_correction";
        String correlationId = "workflow-correction:" + item.id() + ":" + safeRequestId;
        JsonNode publicPayload = objectMapper.valueToTree(Map.of("reasonHash", reasonHash));
        stateRepository.appendHistory(new HistoryAppend(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(),
            targetAggregateVersion + 1, item.typeDefinitionId(), item.typeVersionId(),
            item.configHash(), current.stateKey(), safeTarget, "admin_correction",
            "correction", actor.id(), "user", decisionReference, correlationId,
            safeRequestId, publicPayload
        ));
        appendLifecycleActivity(
            actor, item, "workflow.corrected", "admin_correction", current.stateKey(),
            safeTarget, targetWorkItemVersion, targetAggregateVersion, reasonHash
        );
        WorkflowCommandResult result = new WorkflowCommandResult(
            item.id(), "admin_correction", current.stateKey(), safeTarget,
            targetWorkItemVersion, targetAggregateVersion, false
        );
        stateRepository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        emitDirect(
            actor, item.spaceId(), item.id(), item.typeDefinitionId(), item.typeVersionId(),
            item.configHash(), "admin_correction", "correction", current.stateKey(),
            safeTarget, targetWorkItemVersion, targetAggregateVersion, decisionReference,
            safeRequestId, WorkItemWorkflowEvent.ACTION_EXECUTED
        );
        auditLog.log(actor, "workflow.corrected", "work_item", item.id(), Map.of(
            "spaceId", item.spaceId().toString(),
            "fromStateKey", current.stateKey(),
            "toStateKey", safeTarget,
            "reasonHash", reasonHash,
            "workItemVersion", targetWorkItemVersion,
            "aggregateVersion", targetAggregateVersion
        ));
        return result;
    }

    @Transactional
    public WorkflowBindingCommandResult upgradeBinding(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        UUID targetTypeVersionId,
        String targetStateKey,
        long expectedWorkItemVersion,
        String reason,
        String confirmation,
        String requestId
    ) {
        requireConfirmation(confirmation, "UPGRADE_WORKFLOW_BINDING");
        String safeTarget = semanticKey(targetStateKey, "INVALID_WORKFLOW_STATE");
        String safeReason = reason(reason);
        String safeRequestId = requestId(requestId);
        String reasonHash = canonicalizer.hash(objectMapper.valueToTree(safeReason));
        String requestHash = canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", actor.id().toString(),
            "workItemId", suppliedItem.id().toString(),
            "targetTypeVersionId", targetTypeVersionId.toString(),
            "targetStateKey", safeTarget,
            "expectedWorkItemVersion", expectedWorkItemVersion,
            "reasonHash", reasonHash,
            "confirmation", confirmation
        )));
        WorkItem item = lock(actor, suppliedItem);
        CurrentState current = lockedCurrent(actor, item);
        CommandReceipt receipt = begin(
            actor, item, "correct", "binding_upgrade", current.stateKey(),
            expectedWorkItemVersion, safeRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replayBinding(receipt);
        }
        requireActiveAndVersion(item, expectedWorkItemVersion);
        verifyCurrentVersion(current, expectedWorkItemVersion);
        RuntimeConfiguration targetConfiguration = snapshotAdapter.requireComplete(
            actor.workspaceId(), item.spaceId(), item.typeDefinitionId(), targetTypeVersionId
        );
        if (!targetConfiguration.typeDefinitionId().equals(item.typeDefinitionId())) {
            throw failure("WORKFLOW_BINDING_CONFLICT", "Target snapshot belongs to another type");
        }
        RuntimeFlow targetFlow = runtimeAdapter.adapt(targetConfiguration);
        if (!targetFlow.configured() || !targetFlow.states().containsKey(safeTarget)) {
            throw failure("WORKFLOW_STATE_MAPPING_REQUIRED", "Target state mapping is missing or invalid");
        }
        CanonicalValues values = valueCodec.canonicalize(targetConfiguration, item.fieldValues());
        long targetWorkItemVersion = expectedWorkItemVersion + 1;
        long targetAggregateVersion = current.aggregateVersion() + 1;
        if (workItemRepository.workflowBindingUpdate(
            actor.workspaceId(), item.spaceId(), item.id(), item.typeVersionId(),
            item.configHash(), targetConfiguration.versionId(), targetConfiguration.configHash(),
            values.values(), actor.id(), expectedWorkItemVersion
        ) != 1) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Work item binding was changed concurrently");
        }
        workItemRepository.replaceFieldProjections(
            actor.workspaceId(), item.spaceId(), item.id(), values.projections()
        );
        if (stateRepository.upgradeBinding(
            actor.workspaceId(), item.spaceId(), item.id(), item.typeVersionId(),
            item.configHash(), current.stateKey(), targetConfiguration.versionId(),
            targetConfiguration.configHash(), safeTarget, expectedWorkItemVersion,
            targetWorkItemVersion, current.aggregateVersion(), actor.id()
        ) != 1) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Workflow binding was changed concurrently");
        }
        String decisionReference = targetFlow.policyVersion() + ":binding_upgrade";
        String correlationId = "workflow-upgrade:" + item.id() + ":" + safeRequestId;
        JsonNode publicPayload = objectMapper.valueToTree(Map.of(
            "fromTypeVersionId", item.typeVersionId().toString(),
            "toTypeVersionId", targetConfiguration.versionId().toString(),
            "reasonHash", reasonHash
        ));
        stateRepository.appendHistory(new HistoryAppend(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(),
            targetAggregateVersion + 1, item.typeDefinitionId(), targetConfiguration.versionId(),
            targetConfiguration.configHash(), current.stateKey(), safeTarget, "binding_upgrade",
            "correction", actor.id(), "user", decisionReference, correlationId,
            safeRequestId, publicPayload
        ));
        appendLifecycleActivity(
            actor, item, "workflow.binding_changed", "binding_upgrade", current.stateKey(),
            safeTarget, targetWorkItemVersion, targetAggregateVersion, reasonHash
        );
        WorkflowBindingCommandResult result = new WorkflowBindingCommandResult(
            item.id(), item.typeVersionId(), targetConfiguration.versionId(),
            current.stateKey(), safeTarget, targetWorkItemVersion,
            targetAggregateVersion, false
        );
        stateRepository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        emitDirect(
            actor, item.spaceId(), item.id(), item.typeDefinitionId(),
            targetConfiguration.versionId(), targetConfiguration.configHash(),
            "binding_upgrade", "correction", current.stateKey(), safeTarget,
            targetWorkItemVersion, targetAggregateVersion, decisionReference,
            safeRequestId, WorkItemWorkflowEvent.BINDING_CHANGED
        );
        auditLog.log(actor, "workflow.binding_upgraded", "work_item", item.id(), Map.of(
            "spaceId", item.spaceId().toString(),
            "fromTypeVersionId", item.typeVersionId().toString(),
            "toTypeVersionId", targetConfiguration.versionId().toString(),
            "fromStateKey", current.stateKey(),
            "toStateKey", safeTarget,
            "reasonHash", reasonHash,
            "workItemVersion", targetWorkItemVersion
        ));
        return result;
    }

    private CommandReceipt begin(
        CurrentUser actor, WorkItem item, String operation, String actionKey, String fromStateKey,
        long expectedVersion, String requestId, String requestHash
    ) {
        stateRepository.tryStartCommand(new CommandStart(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(), operation,
            actionKey, fromStateKey, expectedVersion, requestId, requestHash, actor.id()
        ));
        CommandReceipt receipt = stateRepository.findCommand(
            actor.workspaceId(), item.id(), operation, requestId
        ).orElseThrow(() -> failure(
            "IDEMPOTENCY_CONFLICT", "Workflow command receipt is unavailable"
        ));
        if (!receipt.requestHash().equals(requestHash) || !receipt.createdBy().equals(actor.id())) {
            throw failure("IDEMPOTENCY_KEY_REUSED", "Request id was already used with different input");
        }
        if (!"pending".equals(receipt.status()) && !"completed".equals(receipt.status())) {
            throw failure("IDEMPOTENCY_CONFLICT", "Workflow command receipt has an invalid state");
        }
        return receipt;
    }

    private WorkflowCommandResult replay(CommandReceipt receipt) {
        if (receipt.response() == null) {
            throw failure("IDEMPOTENCY_CONFLICT", "Workflow command is still in progress");
        }
        try {
            return objectMapper.treeToValue(receipt.response(), WorkflowCommandResult.class);
        } catch (JsonProcessingException exception) {
            throw failure("IDEMPOTENCY_CONFLICT", "Stored workflow response is invalid", exception);
        }
    }

    private WorkflowBindingCommandResult replayBinding(CommandReceipt receipt) {
        if (receipt.response() == null) {
            throw failure("IDEMPOTENCY_CONFLICT", "Workflow command is still in progress");
        }
        try {
            return objectMapper.treeToValue(
                receipt.response(), WorkflowBindingCommandResult.class
            );
        } catch (JsonProcessingException exception) {
            throw failure("IDEMPOTENCY_CONFLICT", "Stored workflow response is invalid", exception);
        }
    }

    private WorkItem lock(CurrentUser actor, WorkItem suppliedItem) {
        return workItemRepository.lock(
            actor.workspaceId(), suppliedItem.spaceId(), suppliedItem.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
    }

    private String currentStateKey(CurrentUser actor, WorkItem item) {
        return stateRepository.findCurrent(
            actor.workspaceId(), item.spaceId(), item.id()
        ).map(CurrentState::stateKey).orElseThrow(() -> failure(
            "WORKFLOW_NOT_INITIALIZED",
            "Workflow state has not been explicitly initialized"
        ));
    }

    private CurrentState lockedCurrent(CurrentUser actor, WorkItem item) {
        CurrentState current = stateRepository.lockCurrent(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure(
            "WORKFLOW_NOT_INITIALIZED",
            "Workflow state has not been explicitly initialized"
        ));
        verifyBinding(item, current);
        return current;
    }

    private void requireActiveAndVersion(WorkItem item, long expectedWorkItemVersion) {
        if (!"active".equals(item.status())) {
            throw failure(
                "WORKFLOW_NOT_WRITABLE",
                "Archived work items retain workflow state but cannot execute workflow commands"
            );
        }
        if (item.version() != expectedWorkItemVersion) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Work item version is stale");
        }
    }

    private void verifyCurrentVersion(CurrentState current, long expectedWorkItemVersion) {
        if (current.workItemVersion() != expectedWorkItemVersion) {
            throw failure("WORKFLOW_VERSION_CONFLICT", "Workflow state or version is stale");
        }
    }

    private RuntimeConfiguration configuration(WorkItem item) {
        RuntimeConfiguration configuration = snapshotAdapter.requireComplete(
            item.workspaceId(), item.spaceId(), item.typeDefinitionId(), item.typeVersionId()
        );
        if (!configuration.configHash().equals(item.configHash())
            || !configuration.typeDefinitionId().equals(item.typeDefinitionId())
            || !configuration.versionId().equals(item.typeVersionId())) {
            throw failure("SNAPSHOT_INTEGRITY_FAILURE", "Work item binding does not match its snapshot");
        }
        return configuration;
    }

    private void verifyBinding(WorkItem item, CurrentState current) {
        if (!current.typeDefinitionId().equals(item.typeDefinitionId())
            || !current.typeVersionId().equals(item.typeVersionId())
            || !current.configHash().equals(item.configHash())) {
            throw failure("WORKFLOW_BINDING_CONFLICT", "Workflow state binding does not match the work item");
        }
    }

    private void requireAllowed(ActionDecision decision) {
        if (decision.allowed()) {
            return;
        }
        switch (decision.reasonCode()) {
            case "not_authorized" ->
                throw failure("FORBIDDEN", "Workflow action is not allowed");
            case "guard_not_satisfied" ->
                throw failure("WORKFLOW_GUARD_REJECTED", "Workflow action requirements are not satisfied");
            case "required_fields_missing" ->
                throw failure("WORKFLOW_REQUIRED_FIELDS_MISSING", "Workflow action requirements are not satisfied");
            case "capability_missing" ->
                throw failure("WORKFLOW_CAPABILITY_MISSING", "The bound snapshot has no state flow");
            default ->
                throw failure("WORKFLOW_ACTION_UNAVAILABLE", "Workflow action is not available");
        }
    }

    private WorkflowPresentation missing(String capability, String policyVersion) {
        return new WorkflowPresentation(
            capability, policyVersion, null, null, null, 0, List.of()
        );
    }

    private ObjectNode object(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw failure("INVALID_FIELD_VALUES", "Work item field values must be an object");
        }
        return (ObjectNode) value;
    }

    private String semanticKey(String value, String code) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!com.colla.platform.modules.project.domain.WorkItemStateFlowModels.SEMANTIC_KEY
            .matcher(normalized).matches()) {
            throw failure(code, "Workflow semantic key is invalid");
        }
        return normalized;
    }

    private String requestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id must contain 1 to 120 characters");
        }
        return normalized;
    }

    private String reason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw failure(
                "INVALID_RECOVERY_REASON",
                "Recovery reason must contain 10 to 500 characters"
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

    private String operation(ActionKind kind) {
        return switch (kind) {
            case forward -> "execute";
            case return_action -> "return";
            case reopen -> "reopen";
            case terminate -> "terminate";
            case restore -> "restore";
            case correction -> "correct";
        };
    }

    private String historyKind(ActionKind kind) {
        return kind == ActionKind.return_action ? "return" : kind.name();
    }

    private void emit(
        CurrentUser actor, WorkItem item, ActionDecision decision, long workItemVersion,
        long aggregateVersion, String decisionReference, String requestId
    ) {
        WorkItemWorkflowEvent event = new WorkItemWorkflowEvent(
            item.spaceId(), item.typeDefinitionId(), item.typeVersionId(), item.configHash(),
            decision.actionKey(), historyKind(decision.action().kind()),
            decision.transition().fromStateKey(),
            decision.transition().toStateKey(), workItemVersion, aggregateVersion, decisionReference
        );
        outbox.append(
            actor.workspaceId(), WorkItemWorkflowEvent.ACTION_EXECUTED,
            WorkItemWorkflowEvent.AGGREGATE_TYPE, item.id(), actor.id(), event.payload(),
            "workflow:" + item.id() + ":action:" + requestId
        );
        outbox.append(
            actor.workspaceId(), WorkItemWorkflowEvent.STATE_CHANGED,
            WorkItemWorkflowEvent.AGGREGATE_TYPE, item.id(), actor.id(), event.payload(),
            "workflow:" + item.id() + ":state:" + requestId
        );
    }

    private void appendLifecycleActivity(
        CurrentUser actor,
        WorkItem item,
        String activityType,
        String actionKey,
        String fromStateKey,
        String toStateKey,
        long workItemVersion,
        long aggregateVersion,
        String reasonHash
    ) {
        workItemRepository.appendActivity(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(),
            activityType, actor.id(), objectMapper.valueToTree(Map.of(
                "actionKey", actionKey,
                "fromStateKey", fromStateKey,
                "toStateKey", toStateKey,
                "workItemVersion", workItemVersion,
                "aggregateVersion", aggregateVersion,
                "reasonHash", reasonHash
            ))
        );
    }

    private void emitDirect(
        CurrentUser actor,
        UUID spaceId,
        UUID workItemId,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        String actionKey,
        String actionKind,
        String fromStateKey,
        String toStateKey,
        long workItemVersion,
        long aggregateVersion,
        String decisionReference,
        String requestId,
        String primaryEventType
    ) {
        WorkItemWorkflowEvent event = new WorkItemWorkflowEvent(
            spaceId, typeDefinitionId, typeVersionId, configHash, actionKey,
            actionKind, fromStateKey, toStateKey, workItemVersion,
            aggregateVersion, decisionReference
        );
        outbox.append(
            actor.workspaceId(), primaryEventType, WorkItemWorkflowEvent.AGGREGATE_TYPE,
            workItemId, actor.id(), event.payload(),
            "workflow:" + workItemId + ":" + primaryEventType + ":" + requestId
        );
        outbox.append(
            actor.workspaceId(), WorkItemWorkflowEvent.STATE_CHANGED,
            WorkItemWorkflowEvent.AGGREGATE_TYPE, workItemId, actor.id(), event.payload(),
            "workflow:" + workItemId + ":state:" + requestId
        );
    }
}
