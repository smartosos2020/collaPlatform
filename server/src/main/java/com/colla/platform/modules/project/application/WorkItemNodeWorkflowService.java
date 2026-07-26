package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.file.contract.FileAccess;
import com.colla.platform.modules.file.contract.FileAccess.Availability;
import com.colla.platform.modules.file.contract.FileAccess.FileState;
import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectRegistry;
import com.colla.platform.modules.project.contract.WorkItemNodeWorkflowEvent;
import com.colla.platform.modules.project.contract.NodeTaskLifecycleEvent;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityImpact;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.BranchMode;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.EdgeDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.JoinDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.JoinPolicy;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.NodeDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.NodeKind;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.ProcessingStrategy;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.RecoveryCommandDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.CompensationDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeAvailableAction;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeArtifactInput;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCommandResult;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.DueNodeTask;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeJoin;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTask;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskArtifact;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskContext;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskInboxItem;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskInboxPage;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskView;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeToken;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTokenView;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeVote;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowInstance;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowPresentation;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeRecoveryResult;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCompensationRun;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.RuntimeNodeFlow;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.CommandStart;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.CompensationRunInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.CompensationStepInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.HistoryAppend;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.InstanceStart;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.JoinArrival;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.JoinInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.TaskInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.TaskArtifactInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.TokenInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemNodeWorkflowRepository.VoteInsert;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemNodeRuntimeAdapter;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.CanonicalValues;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemNodeWorkflowService {
    private static final int MAX_INTERNAL_STEPS = 128;
    private static final int MAX_HISTORY_PAGE = 200;
    private static final Set<String> VOTE_DECISIONS = Set.of("approve", "reject", "abstain");

    private final WorkItemNodeWorkflowRepository repository;
    private final WorkItemRepository workItemRepository;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemNodeRuntimeAdapter runtimeAdapter;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final WorkItemConfigurationCompatibilityAnalyzer compatibilityAnalyzer;
    private final WorkItemRuntimeProjection projection;
    private final WorkItemFieldValueCodec valueCodec;
    private final FileAccess fileAccess;
    private final PlatformObjectRegistry objectRegistry;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemNodeWorkflowService(
        WorkItemNodeWorkflowRepository repository,
        WorkItemRepository workItemRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemNodeRuntimeAdapter runtimeAdapter,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        WorkItemConfigurationCompatibilityAnalyzer compatibilityAnalyzer,
        WorkItemRuntimeProjection projection,
        WorkItemFieldValueCodec valueCodec,
        FileAccess fileAccess,
        PlatformObjectRegistry objectRegistry,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.workItemRepository = workItemRepository;
        this.snapshotAdapter = snapshotAdapter;
        this.runtimeAdapter = runtimeAdapter;
        this.canonicalizer = canonicalizer;
        this.compatibilityAnalyzer = compatibilityAnalyzer;
        this.projection = projection;
        this.valueCodec = valueCodec;
        this.fileAccess = fileAccess;
        this.objectRegistry = objectRegistry;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void initializeForNewItem(CurrentUser actor, WorkItem item) {
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            return;
        }
        NodeWorkflowInstance instance = initialize(actor, item, flow);
        verifyBinding(item, instance);
    }

    public void alignWorkItemVersion(
        CurrentUser actor,
        WorkItem item,
        long expectedVersion,
        long targetVersion
    ) {
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            return;
        }
        NodeWorkflowInstance instance = repository.findInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElse(null);
        if (instance == null) {
            return;
        }
        verifyBinding(item, instance);
        if (repository.alignWorkItemVersion(
            instance.id(), expectedVersion, targetVersion, actor.id()
        ) != 1) {
            throw failure(
                "NODE_WORKFLOW_VERSION_CONFLICT",
                "Node workflow and work item versions could not be aligned"
            );
        }
    }

    public NodeWorkflowPresentation presentation(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem item
    ) {
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            return missing("not_configured", flow.policyVersion(), item.version());
        }
        NodeWorkflowInstance instance = repository.findInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElse(null);
        if (instance == null) {
            List<NodeAvailableAction> actions = "active".equals(item.status())
                ? List.of(new NodeAvailableAction(
                    "start", null, flow.startNode().nodeKey(), "allowed",
                    item.version(), 0, flow.policyVersion()
                ))
                : List.of();
            return new NodeWorkflowPresentation(
                "uninitialized", flow.policyVersion(), null, null, item.version(), 0,
                List.of(), List.of(), actions
            );
        }
        verifyBinding(item, instance);
        List<NodeToken> tokens = repository.activeTokens(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        List<NodeTask> allTasks = repository.openTasks(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        Set<String> roles = actorRoles(actor, space, item);
        Map<UUID, NodeVote> activeVotes = new HashMap<>();
        List<NodeTask> visibleTasks = allTasks.stream()
            .filter(task -> visible(task, actor.id(), roles))
            .toList();
        ArrayList<NodeAvailableAction> actions = new ArrayList<>();
        for (NodeTask task : visibleTasks) {
            latestVotes(actor, item, instance, task).forEach(activeVotes::put);
            actions.addAll(actions(task, actor.id(), roles, activeVotes.get(actor.id()),
                item.version(), instance.aggregateVersion(), flow.policyVersion()));
        }
        actions.sort(Comparator.comparing(NodeAvailableAction::nodeKey)
            .thenComparing(NodeAvailableAction::actionKey)
            .thenComparing(value -> value.taskId() == null ? "" : value.taskId().toString()));
        return new NodeWorkflowPresentation(
            "available", flow.policyVersion(), instance.id(), instance.status(),
            instance.workItemVersion(), instance.aggregateVersion(),
            tokens.stream().map(this::view).toList(),
            visibleTasks.stream().map(this::view).toList(), List.copyOf(actions)
        );
    }

    public List<NodeHistoryEntry> history(
        CurrentUser actor,
        WorkItem item,
        Long beforeSequence,
        int limit
    ) {
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            return List.of();
        }
        NodeWorkflowInstance instance = repository.findInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElse(null);
        if (instance == null) {
            return List.of();
        }
        verifyBinding(item, instance);
        return repository.pageHistory(
            actor.workspaceId(), item.spaceId(), instance.id(), beforeSequence,
            Math.max(1, Math.min(limit, MAX_HISTORY_PAGE))
        );
    }

    public NodeTaskContext taskContext(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem item,
        UUID taskId
    ) {
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        NodeWorkflowInstance instance = repository.findInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Node task is not available"));
        verifyBinding(item, instance);
        NodeTask task = repository.findTask(
            actor.workspaceId(), item.spaceId(), instance.id(), taskId
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Node task is not available"));
        Set<String> roles = actorRoles(actor, space, item);
        if (!visible(task, actor.id(), roles)) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Node task is not available");
        }
        JsonNode projected = projection.projectDetail(
            configuration(item), space.currentUserRole(), space.status(), item.fieldValues()
        );
        ObjectNode visibleForm = objectMapper.createObjectNode();
        var visibleFields = visibleForm.putArray("fields");
        ObjectNode values = objectMapper.createObjectNode();
        task.formSnapshot().path("fields").forEach(field -> {
            if (!"hidden".equals(field.path("mode").asText())) {
                visibleFields.add(field.deepCopy());
                String key = field.path("fieldKey").asText();
                if (projected.has(key)) {
                    values.set(key, projected.get(key).deepCopy());
                }
            }
        });
        Map<UUID, NodeVote> votes = latestVotes(actor, item, instance, task);
        List<NodeAvailableAction> available = actions(
            task, actor.id(), roles, votes.get(actor.id()), item.version(),
            instance.aggregateVersion(), flow.policyVersion()
        );
        List<NodeTaskArtifact> artifacts = repository.taskArtifacts(
            actor.workspaceId(), item.spaceId(), instance.id(), task.id()
        );
        return new NodeTaskContext(
            view(task), visibleForm, values, task.artifactPolicySnapshot().deepCopy(),
            artifacts, task.candidateUserIds().size(), available
        );
    }

    public NodeTaskInboxPage taskInbox(
        CurrentUser actor,
        ProjectSpaceSummary space,
        UUID cursor,
        int limit
    ) {
        int bounded = Math.max(1, Math.min(limit, 200));
        boolean includeAll = oversight(Set.of(space.currentUserRole()));
        List<NodeTaskInboxItem> items = repository.taskInbox(
            actor.workspaceId(), space.id(), actor.id(), includeAll, cursor, bounded + 1
        );
        UUID next = items.size() > bounded ? items.get(bounded - 1).taskId() : null;
        return new NodeTaskInboxPage(
            items.size() > bounded ? List.copyOf(items.subList(0, bounded)) : List.copyOf(items),
            next
        );
    }

    @Transactional
    public int processDueTasks(CurrentUser actor, ProjectSpaceSummary space, int limit) {
        if (!oversight(Set.of(space.currentUserRole()))) {
            throw failure("FORBIDDEN", "Only space oversight can process due node tasks");
        }
        Instant now = Instant.now();
        List<DueNodeTask> dueTasks = repository.markDueTasksTimedOut(
            actor.workspaceId(), space.id(), now, Math.max(1, Math.min(limit, 200))
        );
        for (DueNodeTask task : dueTasks) {
            NodeTaskLifecycleEvent event = new NodeTaskLifecycleEvent(
                space.id(), task.taskId(), task.workItemId(), "timed_out",
                task.nodeKey(), task.dueAt()
            );
            outbox.append(
                actor.workspaceId(), NodeTaskLifecycleEvent.EVENT_TYPE,
                NodeTaskLifecycleEvent.AGGREGATE_TYPE, task.workItemId(), actor.id(),
                event.payload(), "node-task:timed-out:" + task.taskId()
            );
        }
        auditLog.log(actor, "node_task.process_due", "project_space", space.id(), Map.of(
            "processedCount", dueTasks.size(),
            "processedAt", now.toString()
        ));
        return dueTasks.size();
    }

    @Transactional
    public NodeCommandResult start(
        CurrentUser actor,
        WorkItem suppliedItem,
        long expectedWorkItemVersion,
        String requestId
    ) {
        WorkItem item = lock(actor, suppliedItem);
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            throw failure("NODE_WORKFLOW_CAPABILITY_MISSING", "The bound snapshot has no node flow");
        }
        String safeRequestId = requestId(requestId);
        String requestHash = requestHash(actor, item, null, "start", expectedWorkItemVersion, 0, null);
        CommandReceipt receipt = begin(
            actor, item, null, "start", flow.startNode().nodeKey(), expectedWorkItemVersion,
            null, safeRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        requireActiveAndVersion(item, expectedWorkItemVersion);
        NodeWorkflowInstance instance = initialize(actor, item, flow);
        NodeCommandResult result = new NodeCommandResult(
            item.id(), instance.id(), null, "start", flow.startNode().nodeKey(),
            instance.status(), instance.workItemVersion(), instance.aggregateVersion(), false
        );
        repository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        return result;
    }

    @Transactional
    public NodeRecoveryResult recover(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        String commandKey,
        String reason,
        String confirmation,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String requestId
    ) {
        requireOversight(space);
        String safeReason = recoveryReason(reason);
        WorkItem item = lock(actor, suppliedItem);
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        RecoveryCommandDefinition command = flow.recoveryCommands().get(commandKey);
        if (command == null) {
            throw failure("NODE_RECOVERY_UNAVAILABLE", "Recovery command is not declared by the bound snapshot");
        }
        if (!command.authorizedRoles().contains(space.currentUserRole())) {
            throw failure("FORBIDDEN", "The recovery command is not available to the caller");
        }
        if (!command.confirmation().equals(confirmation)) {
            throw failure("DANGEROUS_CONFIRMATION_REQUIRED", "The exact recovery confirmation is required");
        }
        NodeWorkflowInstance instance = repository.lockInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure(
            "NODE_WORKFLOW_NOT_INITIALIZED", "Node workflow has not been explicitly initialized"
        ));
        verifyBinding(item, instance);
        String operation = switch (command.kind()) {
            case return_to -> "return";
            case jump -> "jump";
            case terminate -> "terminate";
            case correct -> "correct";
        };
        String safeRequestId = requestId(requestId);
        ObjectNode arguments = objectMapper.createObjectNode()
            .put("commandKey", command.commandKey())
            .put("reasonHash", canonicalizer.hash(objectMapper.valueToTree(safeReason)))
            .put("confirmation", confirmation);
        String requestHash = requestHash(
            actor, item, null, operation, expectedWorkItemVersion,
            expectedInstanceVersion, arguments
        );
        CommandReceipt receipt = begin(
            actor, item, instance.id(), operation, command.targetNodeKey(),
            expectedWorkItemVersion, expectedInstanceVersion, safeRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return new NodeRecoveryResult(replay(receipt), null, 0);
        }
        requireActiveAndVersion(item, expectedWorkItemVersion);
        verifyVersions(instance, expectedWorkItemVersion, expectedInstanceVersion);
        if (!"active".equals(instance.status())) {
            throw failure("NODE_RECOVERY_UNAVAILABLE", "Only an active node workflow can be recovered");
        }
        List<NodeToken> activeTokens = repository.activeTokens(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        if (activeTokens.isEmpty() || activeTokens.stream().anyMatch(
            token -> !command.fromNodeKeys().contains(token.nodeKey())
        )) {
            throw failure(
                "NODE_RECOVERY_SOURCE_MISMATCH",
                "Every active token must match an explicit recovery source"
            );
        }

        int canceledTasks = repository.cancelOpenTasks(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        int canceledTokens = repository.cancelOpenTokens(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        int canceledJoins = repository.cancelWaitingJoins(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        boolean completed = false;
        UUID targetTokenId = null;
        if (command.targetNodeKey() != null) {
            NodeDefinition target = flow.nodes().get(command.targetNodeKey());
            if (target == null) {
                throw failure("NODE_RECOVERY_TARGET_MISSING", "Recovery target is not in the bound snapshot");
            }
            NodeToken targetToken = insertToken(
                actor, item, instance, target, null, null, null,
                "recovery:" + receipt.id(), safeRequestId
            );
            targetTokenId = targetToken.id();
            completed = processAutomaticQueue(
                actor, item, instance, flow, new ArrayDeque<>(List.of(targetToken)), safeRequestId
            );
        }
        long targetWorkItemVersion = expectedWorkItemVersion + 1;
        if (workItemRepository.workflowUpdate(
            actor.workspaceId(), item.spaceId(), item.id(), item.fieldValues(),
            actor.id(), expectedWorkItemVersion
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Work item changed during recovery");
        }
        String targetStatus = command.kind().name().equals("terminate")
            ? "terminated" : completed ? "completed" : "active";
        if (repository.recoverInstance(
            instance.id(), "active", targetStatus, expectedWorkItemVersion,
            targetWorkItemVersion, expectedInstanceVersion, actor.id()
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Node workflow changed during recovery");
        }
        String eventKind = switch (operation) {
            case "return" -> "returned";
            case "jump" -> "jumped";
            case "terminate" -> "terminated";
            default -> "corrected";
        };
        appendHistory(
            actor, item, instance, eventKind, command.targetNodeKey(), targetTokenId, null,
            "user", flow.policyVersion() + ":" + command.commandKey(), safeRequestId,
            objectMapper.createObjectNode()
                .put("commandKey", command.commandKey())
                .put("reasonHash", arguments.path("reasonHash").asText())
                .put("canceledTaskCount", canceledTasks)
                .put("canceledTokenCount", canceledTokens)
                .put("canceledJoinCount", canceledJoins)
        );
        List<CompensationDefinition> compensations = flow.compensationsByCommand()
            .getOrDefault(command.commandKey(), List.of());
        UUID compensationRunId = executeCompensations(
            actor, item, instance, receipt.id(), command.commandKey(), compensations
        );
        NodeCommandResult result = new NodeCommandResult(
            item.id(), instance.id(), null, operation, command.targetNodeKey(), targetStatus,
            targetWorkItemVersion, expectedInstanceVersion + 1, false
        );
        repository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        appendActivity(actor, item, result, null);
        emit(actor, item, instance, result, flow.policyVersion() + ":" + command.commandKey(), safeRequestId);
        auditLog.log(actor, "node_workflow.recovered", "work_item", item.id(), Map.of(
            "spaceId", item.spaceId().toString(),
            "instanceId", instance.id().toString(),
            "commandKey", command.commandKey(),
            "operation", operation,
            "reasonHash", arguments.path("reasonHash").asText(),
            "canceledTaskCount", canceledTasks,
            "canceledTokenCount", canceledTokens,
            "canceledJoinCount", canceledJoins
        ));
        return new NodeRecoveryResult(result, compensationRunId, compensations.size());
    }

    private UUID executeCompensations(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        UUID commandId,
        String commandKey,
        List<CompensationDefinition> definitions
    ) {
        if (definitions.isEmpty()) {
            return null;
        }
        UUID runId = UUID.randomUUID();
        repository.insertCompensationRun(new CompensationRunInsert(
            runId, actor.workspaceId(), item.spaceId(), instance.id(),
            commandId, commandKey, definitions.size(), actor.id()
        ));
        for (CompensationDefinition definition : definitions) {
            UUID stepId = UUID.randomUUID();
            repository.insertCompensationStep(new CompensationStepInsert(
                stepId, actor.workspaceId(), item.spaceId(), instance.id(), runId,
                definition.compensationKey(), definition.actionKey(), definition.sortOrder()
            ));
            executeCompensationAction(
                actor, item, instance, commandKey,
                definition.compensationKey(), definition.actionKey()
            );
            if (repository.completeCompensationStep(stepId) != 1) {
                throw failure("NODE_COMPENSATION_CONFLICT", "Compensation step changed concurrently");
            }
        }
        if (repository.completeCompensationRun(runId, definitions.size()) != 1) {
            throw failure("NODE_COMPENSATION_CONFLICT", "Compensation run changed concurrently");
        }
        return runId;
    }

    @Transactional
    public NodeCompensationRun resumeCompensation(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        UUID runId,
        String reason,
        String confirmation
    ) {
        requireOversight(space);
        String safeReason = recoveryReason(reason);
        if (!"RESUME_NODE_COMPENSATION".equals(confirmation)) {
            throw failure("DANGEROUS_CONFIRMATION_REQUIRED", "The exact compensation confirmation is required");
        }
        WorkItem item = lock(actor, suppliedItem);
        NodeWorkflowInstance instance = repository.lockInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure(
            "NODE_WORKFLOW_NOT_INITIALIZED", "Node workflow has not been explicitly initialized"
        ));
        NodeCompensationRun run = repository.lockCompensationRun(
            actor.workspaceId(), item.spaceId(), instance.id(), runId
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Compensation run is not available"));
        if ("completed".equals(run.status())) {
            return run;
        }
        if (repository.markCompensationRunRunning(run.id()) != 1) {
            throw failure("NODE_COMPENSATION_CONFLICT", "Compensation run changed concurrently");
        }
        var steps = repository.compensationSteps(
            actor.workspaceId(), item.spaceId(), instance.id(), run.id()
        );
        for (var step : steps) {
            if ("completed".equals(step.status())) {
                continue;
            }
            executeCompensationAction(
                actor, item, instance, run.commandKey(),
                step.compensationKey(), step.actionKey()
            );
            if (repository.completeCompensationStep(step.id()) != 1) {
                throw failure("NODE_COMPENSATION_CONFLICT", "Compensation step changed concurrently");
            }
        }
        if (repository.completeCompensationRun(run.id(), run.totalSteps()) != 1) {
            throw failure("NODE_COMPENSATION_CONFLICT", "Compensation run changed concurrently");
        }
        auditLog.log(actor, "node_workflow.compensation_resumed", "work_item", item.id(), Map.of(
            "instanceId", instance.id().toString(),
            "runId", run.id().toString(),
            "reasonHash", canonicalizer.hash(objectMapper.valueToTree(safeReason)),
            "completedSteps", run.totalSteps()
        ));
        return repository.lockCompensationRun(
            actor.workspaceId(), item.spaceId(), instance.id(), run.id()
        ).orElseThrow();
    }

    private void executeCompensationAction(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        String commandKey,
        String compensationKey,
        String actionKey
    ) {
        switch (actionKey) {
            case "record_audit_marker" -> auditLog.log(
                actor, "node_workflow.compensation_marker", "work_item", item.id(), Map.of(
                    "instanceId", instance.id().toString(),
                    "commandKey", commandKey,
                    "compensationKey", compensationKey
                )
            );
            case "close_open_work" -> {
                repository.cancelOpenTasks(actor.workspaceId(), item.spaceId(), instance.id());
                repository.cancelOpenTokens(actor.workspaceId(), item.spaceId(), instance.id());
                repository.cancelWaitingJoins(actor.workspaceId(), item.spaceId(), instance.id());
            }
            default -> throw failure(
                "NODE_COMPENSATION_ACTION_UNREGISTERED",
                "Compensation action is not registered"
            );
        }
    }

    @Transactional
    public NodeCommandResult upgrade(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        UUID targetTypeVersionId,
        JsonNode nodeMap,
        String reason,
        String confirmation,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String requestId
    ) {
        requireOversight(space);
        if (!"UPGRADE_NODE_WORKFLOW_BINDING".equals(confirmation)) {
            throw failure("DANGEROUS_CONFIRMATION_REQUIRED", "The exact node upgrade confirmation is required");
        }
        String safeReason = recoveryReason(reason);
        WorkItem item = lock(actor, suppliedItem);
        NodeWorkflowInstance instance = repository.lockInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure(
            "NODE_WORKFLOW_NOT_INITIALIZED", "Node workflow has not been explicitly initialized"
        ));
        verifyBinding(item, instance);
        RuntimeConfiguration targetConfiguration = snapshotAdapter.requireComplete(
            actor.workspaceId(), item.spaceId(), item.typeDefinitionId(), targetTypeVersionId
        );
        RuntimeNodeFlow targetFlow = runtimeAdapter.adapt(targetConfiguration);
        if (!targetFlow.configured()) {
            throw failure("NODE_WORKFLOW_CAPABILITY_MISSING", "Target snapshot has no node flow");
        }
        ObjectNode arguments = objectMapper.createObjectNode()
            .put("targetTypeVersionId", targetTypeVersionId.toString())
            .put("targetConfigHash", targetConfiguration.configHash())
            .put("reasonHash", canonicalizer.hash(objectMapper.valueToTree(safeReason)))
            .put("confirmation", confirmation);
        arguments.set("nodeMap", nodeMap);
        String safeRequestId = requestId(requestId);
        String requestHash = requestHash(
            actor, item, null, "upgrade", expectedWorkItemVersion,
            expectedInstanceVersion, arguments
        );
        CommandReceipt receipt = begin(
            actor, item, instance.id(), "upgrade", null, expectedWorkItemVersion,
            expectedInstanceVersion, safeRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        requireActiveAndVersion(item, expectedWorkItemVersion);
        verifyVersions(instance, expectedWorkItemVersion, expectedInstanceVersion);
        if (!"active".equals(instance.status())) {
            throw failure("NODE_UPGRADE_UNAVAILABLE", "Only an active node workflow can be upgraded");
        }
        RuntimeConfiguration sourceConfiguration = configuration(item);
        var compatibility = compatibilityAnalyzer.analyze(
            sourceConfiguration.configHash(), sourceConfiguration.snapshot(),
            targetConfiguration.configHash(), targetConfiguration.snapshot()
        );
        if (compatibility.overallImpact() == CompatibilityImpact.blocked) {
            throw failure("NODE_UPGRADE_BLOCKED", "Target snapshot has blocked compatibility changes");
        }
        List<NodeToken> activeTokens = repository.activeTokens(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        List<String> mappedTargets = mappedTargets(activeTokens, nodeMap, targetFlow);
        CanonicalValues values = valueCodec.canonicalize(
            targetConfiguration, item.fieldValues()
        );
        long targetWorkItemVersion = expectedWorkItemVersion + 1;
        if (workItemRepository.workflowBindingUpdate(
            actor.workspaceId(), item.spaceId(), item.id(), item.typeVersionId(),
            item.configHash(), targetConfiguration.versionId(),
            targetConfiguration.configHash(), values.values(), actor.id(),
            expectedWorkItemVersion
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Work item changed during node upgrade");
        }
        workItemRepository.replaceFieldProjections(
            actor.workspaceId(), item.spaceId(), item.id(), values.projections()
        );
        WorkItem targetItem = workItemRepository.find(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Upgraded work item is unavailable"));
        int canceledTasks = repository.cancelOpenTasks(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        int canceledTokens = repository.cancelOpenTokens(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        int canceledJoins = repository.cancelWaitingJoins(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        ArrayDeque<NodeToken> queue = new ArrayDeque<>();
        for (String targetNodeKey : mappedTargets) {
            NodeDefinition targetNode = targetFlow.nodes().get(targetNodeKey);
            queue.add(insertToken(
                actor, targetItem, instance, targetNode, null, null, null,
                "upgrade:" + receipt.id() + ":" + targetNodeKey, safeRequestId
            ));
        }
        boolean completed = processAutomaticQueue(
            actor, targetItem, instance, targetFlow, queue, safeRequestId
        );
        String targetStatus = completed ? "completed" : "active";
        if (repository.upgradeInstanceBinding(
            instance.id(), item.typeVersionId(), item.configHash(),
            targetConfiguration.versionId(), targetConfiguration.configHash(), targetStatus,
            expectedWorkItemVersion, targetWorkItemVersion, expectedInstanceVersion, actor.id()
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Node instance changed during upgrade");
        }
        appendHistory(
            actor, targetItem, instance, "upgraded", null, null, null, "user",
            targetFlow.policyVersion() + ":upgrade", safeRequestId,
            objectMapper.createObjectNode()
                .put("sourceTypeVersionId", item.typeVersionId().toString())
                .put("targetTypeVersionId", targetConfiguration.versionId().toString())
                .put("reasonHash", arguments.path("reasonHash").asText())
                .put("mappedTokenCount", mappedTargets.size())
                .put("canceledTaskCount", canceledTasks)
                .put("canceledTokenCount", canceledTokens)
                .put("canceledJoinCount", canceledJoins)
        );
        NodeCommandResult result = new NodeCommandResult(
            item.id(), instance.id(), null, "upgrade", null, targetStatus,
            targetWorkItemVersion, expectedInstanceVersion + 1, false
        );
        repository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        appendActivity(actor, targetItem, result, null);
        emit(actor, targetItem, instance, result, targetFlow.policyVersion() + ":upgrade", safeRequestId);
        auditLog.log(actor, "node_workflow.upgraded", "work_item", item.id(), Map.of(
            "spaceId", item.spaceId().toString(),
            "instanceId", instance.id().toString(),
            "sourceTypeVersionId", item.typeVersionId().toString(),
            "targetTypeVersionId", targetConfiguration.versionId().toString(),
            "mappedTokenCount", mappedTargets.size(),
            "reasonHash", arguments.path("reasonHash").asText()
        ));
        return result;
    }

    @Transactional
    public NodeCommandResult backfillInitialize(
        CurrentUser actor,
        WorkItem suppliedItem,
        UUID targetTypeVersionId,
        String targetEntryNodeKey,
        long expectedSourceVersion,
        UUID batchId
    ) {
        WorkItem item = lock(actor, suppliedItem);
        if (item.version() != expectedSourceVersion || !"active".equals(item.status())) {
            throw failure("NODE_BACKFILL_SOURCE_CHANGED", "Work item changed after manifest freeze");
        }
        if (repository.findInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).isPresent()) {
            throw failure("NODE_WORKFLOW_ALREADY_INITIALIZED", "Node workflow already has an instance");
        }
        RuntimeConfiguration targetConfiguration = snapshotAdapter.requireComplete(
            actor.workspaceId(), item.spaceId(), item.typeDefinitionId(), targetTypeVersionId
        );
        RuntimeNodeFlow targetFlow = runtimeAdapter.adapt(targetConfiguration);
        NodeDefinition entry = targetFlow.nodes().get(targetEntryNodeKey);
        if (!targetFlow.configured() || entry == null
            || Set.of(NodeKind.branch, NodeKind.join, NodeKind.end).contains(entry.kind())) {
            throw failure("NODE_BACKFILL_ENTRY_INVALID", "Backfill entry must be an executable target node");
        }
        String unitRequestId = "node-backfill:" + batchId + ":" + item.id();
        ObjectNode arguments = objectMapper.createObjectNode()
            .put("batchId", batchId.toString())
            .put("targetTypeVersionId", targetTypeVersionId.toString())
            .put("targetEntryNodeKey", targetEntryNodeKey);
        String requestHash = requestHash(
            actor, item, null, "backfill", expectedSourceVersion, 0, arguments
        );
        CommandReceipt receipt = begin(
            actor, item, null, "backfill", targetEntryNodeKey, expectedSourceVersion,
            null, unitRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        CanonicalValues values = valueCodec.canonicalize(
            targetConfiguration, item.fieldValues()
        );
        long targetWorkItemVersion = expectedSourceVersion + 1;
        if (workItemRepository.workflowBindingUpdate(
            actor.workspaceId(), item.spaceId(), item.id(), item.typeVersionId(),
            item.configHash(), targetConfiguration.versionId(),
            targetConfiguration.configHash(), values.values(), actor.id(), expectedSourceVersion
        ) != 1) {
            throw failure("NODE_BACKFILL_SOURCE_CHANGED", "Work item changed during node backfill");
        }
        workItemRepository.replaceFieldProjections(
            actor.workspaceId(), item.spaceId(), item.id(), values.projections()
        );
        WorkItem targetItem = workItemRepository.find(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Backfilled work item is unavailable"));
        UUID instanceId = UUID.randomUUID();
        if (!repository.tryStartInstance(new InstanceStart(
            instanceId, actor.workspaceId(), item.spaceId(), item.id(),
            item.typeDefinitionId(), targetConfiguration.versionId(),
            targetConfiguration.configHash(), targetWorkItemVersion, actor.id()
        ))) {
            throw failure("NODE_WORKFLOW_INITIALIZATION_CONFLICT", "Node backfill instance already exists");
        }
        NodeWorkflowInstance instance = repository.lockInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow();
        appendHistory(
            actor, targetItem, instance, "backfilled", targetEntryNodeKey, null, null,
            "system", targetFlow.policyVersion() + ":backfill", unitRequestId,
            objectMapper.createObjectNode()
                .put("batchId", batchId.toString())
                .put("sourceTypeVersionId", item.typeVersionId().toString())
                .put("targetTypeVersionId", targetConfiguration.versionId().toString())
        );
        NodeToken token = insertToken(
            actor, targetItem, instance, entry, null, null, null,
            "backfill:" + batchId + ":" + item.id(), unitRequestId
        );
        boolean completed = processAutomaticQueue(
            actor, targetItem, instance, targetFlow,
            new ArrayDeque<>(List.of(token)), unitRequestId
        );
        String targetStatus = completed ? "completed" : "active";
        if (repository.updateInstance(
            instance.id(), "active", targetStatus, targetWorkItemVersion,
            targetWorkItemVersion, 0, actor.id()
        ) != 1) {
            throw failure("NODE_WORKFLOW_INITIALIZATION_CONFLICT", "Backfill instance version changed");
        }
        NodeCommandResult result = new NodeCommandResult(
            item.id(), instance.id(), null, "backfill", targetEntryNodeKey, targetStatus,
            targetWorkItemVersion, 1, false
        );
        repository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        appendActivity(actor, targetItem, result, null);
        emit(actor, targetItem, instance, result, targetFlow.policyVersion() + ":backfill", unitRequestId);
        auditLog.log(actor, "node_workflow.backfilled", "work_item", item.id(), Map.of(
            "spaceId", item.spaceId().toString(),
            "instanceId", instance.id().toString(),
            "batchId", batchId.toString(),
            "targetTypeVersionId", targetConfiguration.versionId().toString(),
            "targetEntryNodeKey", targetEntryNodeKey
        ));
        return result;
    }

    private List<String> mappedTargets(
        List<NodeToken> activeTokens,
        JsonNode nodeMap,
        RuntimeNodeFlow targetFlow
    ) {
        if (nodeMap == null || !nodeMap.isObject() || !nodeMap.path("mappings").isArray()
            || nodeMap.path("mappings").isEmpty() || nodeMap.path("mappings").size() > 256) {
            throw failure("NODE_UPGRADE_MAPPING_REQUIRED", "Upgrade requires a bounded explicit node map");
        }
        Map<String, JsonNode> mappings = new LinkedHashMap<>();
        for (JsonNode mapping : nodeMap.path("mappings")) {
            String source = mapping.path("fromNodeKey").asText("");
            if (source.isBlank() || mappings.putIfAbsent(source, mapping) != null) {
                throw failure("NODE_UPGRADE_MAPPING_INVALID", "Upgrade source mappings must be unique");
            }
        }
        Set<String> activeSources = activeTokens.stream()
            .map(NodeToken::nodeKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!mappings.keySet().equals(activeSources)) {
            throw failure("NODE_UPGRADE_MAPPING_REQUIRED", "Every and only active node must be explicitly mapped");
        }
        ArrayList<String> result = new ArrayList<>();
        Set<String> mergedTargets = new HashSet<>();
        for (NodeToken token : activeTokens) {
            JsonNode mapping = mappings.get(token.nodeKey());
            String mode = mapping.path("mode").asText("");
            JsonNode targets = mapping.path("toNodeKeys");
            if (!Set.of("one_to_one", "split", "merge").contains(mode)
                || !targets.isArray() || targets.isEmpty() || targets.size() > 16
                || "one_to_one".equals(mode) && targets.size() != 1
                || "merge".equals(mode) && targets.size() != 1
                || "split".equals(mode) && targets.size() < 2) {
                throw failure("NODE_UPGRADE_MAPPING_INVALID", "Upgrade mapping mode and targets are inconsistent");
            }
            Set<String> unique = new LinkedHashSet<>();
            for (JsonNode targetValue : targets) {
                String target = targetValue.asText("");
                NodeDefinition targetNode = targetFlow.nodes().get(target);
                if (targetNode == null
                    || !Set.of(NodeKind.manual, NodeKind.automatic).contains(targetNode.kind())
                    || !unique.add(target)) {
                    throw failure("NODE_UPGRADE_MAPPING_INVALID", "Mapped target must be a unique executable node");
                }
                if (!"merge".equals(mode) || mergedTargets.add(target)) {
                    result.add(target);
                }
            }
        }
        if (result.isEmpty()) {
            throw failure("NODE_UPGRADE_MAPPING_INVALID", "Upgrade mapping produced no target token");
        }
        return List.copyOf(result);
    }

    @Transactional
    public NodeCommandResult taskCommand(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        UUID taskId,
        String operation,
        String decision,
        UUID targetAssigneeId,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String requestId
    ) {
        return taskCommand(
            actor, space, suppliedItem, taskId, operation, decision, targetAssigneeId,
            null, List.of(), expectedWorkItemVersion, expectedInstanceVersion, requestId
        );
    }

    @Transactional
    public NodeCommandResult taskCommand(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem suppliedItem,
        UUID taskId,
        String operation,
        String decision,
        UUID targetAssigneeId,
        JsonNode fieldPatch,
        List<NodeArtifactInput> artifacts,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String requestId
    ) {
        String safeOperation = operation(operation);
        String safeDecision = decision == null ? null : decision.trim().toLowerCase(Locale.ROOT);
        WorkItem item = lock(actor, suppliedItem);
        RuntimeNodeFlow flow = runtimeAdapter.adapt(configuration(item));
        if (!flow.configured()) {
            throw failure("NODE_WORKFLOW_CAPABILITY_MISSING", "The bound snapshot has no node flow");
        }
        NodeWorkflowInstance instance = repository.lockInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow(() -> failure(
            "NODE_WORKFLOW_NOT_INITIALIZED", "Node workflow has not been explicitly initialized"
        ));
        verifyBinding(item, instance);
        NodeTask task = repository.lockTask(
            actor.workspaceId(), item.spaceId(), instance.id(), taskId
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Node task is not available"));
        NodeDefinition node = flow.nodes().get(task.nodeKey());
        if (node == null || node.kind() != NodeKind.manual) {
            throw failure("NODE_WORKFLOW_DEFINITION_INVALID", "Task node is not in the bound snapshot");
        }
        Set<String> roles = actorRoles(actor, space, item);
        if (!eligible(task, actor.id(), roles)) {
            throw failure("FORBIDDEN", "Node task is not available to the caller");
        }
        String safeRequestId = requestId(requestId);
        ObjectNode commandPayload = objectMapper.createObjectNode()
            .put("decision", safeDecision == null ? "" : safeDecision)
            .put("targetAssigneeId", targetAssigneeId == null ? "" : targetAssigneeId.toString());
        commandPayload.set("fieldPatch", fieldPatch == null ? objectMapper.createObjectNode() : fieldPatch);
        commandPayload.set("artifacts", objectMapper.valueToTree(artifacts == null ? List.of() : artifacts));
        String requestHash = requestHash(
            actor, item, taskId, safeOperation, expectedWorkItemVersion,
            expectedInstanceVersion, commandPayload
        );
        CommandReceipt receipt = begin(
            actor, item, instance.id(), safeOperation, task.nodeKey(),
            expectedWorkItemVersion, expectedInstanceVersion, safeRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        requireActiveAndVersion(item, expectedWorkItemVersion);
        verifyVersions(instance, expectedWorkItemVersion, expectedInstanceVersion);

        CanonicalValues submittedValues = null;
        if ("submit".equals(safeOperation)) {
            submittedValues = prepareSubmission(
                actor, space, item, instance, task, fieldPatch,
                artifacts == null ? List.of() : artifacts
            );
        } else if ((fieldPatch != null && fieldPatch.size() > 0)
            || artifacts != null && !artifacts.isEmpty()) {
            throw failure("NODE_SUBMISSION_UNEXPECTED", "Fields and artifacts require the submit action");
        }
        CommandEffect effect = executeTaskOperation(
            actor, item, instance, task, node, safeOperation, safeDecision,
            targetAssigneeId, roles, flow, safeRequestId
        );
        long targetWorkItemVersion = expectedWorkItemVersion + 1;
        if (workItemRepository.workflowUpdate(
            actor.workspaceId(), item.spaceId(), item.id(),
            submittedValues == null ? item.fieldValues() : submittedValues.values(),
            actor.id(), expectedWorkItemVersion
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Work item version is stale");
        }
        if (submittedValues != null) {
            workItemRepository.replaceFieldProjections(
                actor.workspaceId(), item.spaceId(), item.id(), submittedValues.projections()
            );
        }
        String targetStatus = effect.instanceCompleted() ? "completed" : instance.status();
        if (repository.updateInstance(
            instance.id(), instance.status(), targetStatus, expectedWorkItemVersion,
            targetWorkItemVersion, expectedInstanceVersion, actor.id()
        ) != 1) {
            throw failure(
                "NODE_WORKFLOW_VERSION_CONFLICT",
                "Node workflow was changed concurrently"
            );
        }
        long targetAggregateVersion = expectedInstanceVersion + 1;
        String decisionReference = flow.policyVersion() + ":" + task.nodeKey() + ":" + safeOperation;
        NodeCommandResult result = new NodeCommandResult(
            item.id(), instance.id(), task.id(), safeOperation, task.nodeKey(), targetStatus,
            targetWorkItemVersion, targetAggregateVersion, false
        );
        repository.completeCommand(receipt.id(), objectMapper.valueToTree(result));
        appendActivity(actor, item, result, safeDecision);
        emit(actor, item, instance, result, decisionReference, safeRequestId);
        auditLog.log(actor, "node_workflow." + safeOperation, "work_item", item.id(), Map.of(
            "spaceId", item.spaceId().toString(),
            "instanceId", instance.id().toString(),
            "taskId", task.id().toString(),
            "nodeKey", task.nodeKey(),
            "workItemVersion", targetWorkItemVersion,
            "aggregateVersion", targetAggregateVersion,
            "decisionReference", decisionReference
        ));
        return result;
    }

    private CommandEffect executeTaskOperation(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeTask task,
        NodeDefinition node,
        String operation,
        String decision,
        UUID targetAssigneeId,
        Set<String> roles,
        RuntimeNodeFlow flow,
        String requestId
    ) {
        return switch (operation) {
            case "claim" -> {
                requireClaimStrategy(task);
                if (!task.candidateUserIds().contains(actor.id())) {
                    throw failure("FORBIDDEN", "Only a frozen task candidate can claim the task");
                }
                if (repository.claimTask(
                    task.id(), "pending", actor.id(), task.aggregateVersion()
                ) != 1) {
                    throw staleTask();
                }
                appendHistory(actor, item, instance, "claimed", task.nodeKey(), task.tokenId(),
                    task.id(), "user", flow.policyVersion() + ":claim", requestId,
                    objectMapper.createObjectNode());
                yield new CommandEffect(false);
            }
            case "delegate" -> {
                requireClaimStrategy(task);
                if (targetAssigneeId == null || !actor.id().equals(task.assigneeId())) {
                    throw failure("FORBIDDEN", "Only the current assignee can delegate the task");
                }
                if (!task.candidateUserIds().contains(targetAssigneeId)) {
                    throw failure("NODE_TASK_ASSIGNEE_INVALID", "Delegate target is not an eligible candidate");
                }
                if (repository.delegateTask(
                    task.id(), actor.id(), targetAssigneeId, task.aggregateVersion()
                ) != 1) {
                    throw staleTask();
                }
                appendHistory(actor, item, instance, "delegated", task.nodeKey(), task.tokenId(),
                    task.id(), "user", flow.policyVersion() + ":delegate", requestId,
                    objectMapper.createObjectNode().put("targetAssigneeId", targetAssigneeId.toString()));
                yield new CommandEffect(false);
            }
            case "transfer" -> {
                requireClaimStrategy(task);
                if (!oversight(roles) || targetAssigneeId == null
                    || !repository.activeMemberUserIds(
                        actor.workspaceId(), item.spaceId(), Set.of(targetAssigneeId)
                    ).contains(targetAssigneeId)) {
                    throw failure("FORBIDDEN", "Only space oversight may transfer to an active member");
                }
                if (repository.transferTask(
                    task.id(), targetAssigneeId, task.aggregateVersion()
                ) != 1) {
                    throw staleTask();
                }
                appendHistory(actor, item, instance, "transferred", task.nodeKey(), task.tokenId(),
                    task.id(), "user", flow.policyVersion() + ":transfer", requestId,
                    objectMapper.createObjectNode().put("targetAssigneeId", targetAssigneeId.toString()));
                yield new CommandEffect(false);
            }
            case "complete" -> {
                requireClaimStrategy(task);
                if (task.formSnapshot().path("fields").size() > 0
                    || task.artifactPolicySnapshot().size() > 0) {
                    throw failure("NODE_SUBMISSION_REQUIRED", "Task requires atomic form and artifact submission");
                }
                if (!actor.id().equals(task.assigneeId())) {
                    throw failure("FORBIDDEN", "Only the current assignee can complete the task");
                }
                if (repository.completeTask(task.id(), task.aggregateVersion()) != 1) {
                    throw staleTask();
                }
                yield completeManualToken(
                    actor, item, instance, task, flow, "completed", requestId
                );
            }
            case "submit" -> {
                requireClaimStrategy(task);
                if (!actor.id().equals(task.assigneeId())) {
                    throw failure("FORBIDDEN", "Only the current assignee can submit the task");
                }
                if (repository.completeTask(task.id(), task.aggregateVersion()) != 1) {
                    throw staleTask();
                }
                yield completeManualToken(
                    actor, item, instance, task, flow, "submitted", requestId
                );
            }
            case "vote" -> {
                requireVoteStrategy(task);
                if (!task.candidateUserIds().contains(actor.id())) {
                    throw failure("FORBIDDEN", "Only a frozen task candidate can vote");
                }
                if (!VOTE_DECISIONS.contains(decision)) {
                    throw failure("INVALID_NODE_VOTE", "Vote must be approve, reject, or abstain");
                }
                Map<UUID, NodeVote> latest = latestVotes(actor, item, instance, task);
                NodeVote previous = latest.get(actor.id());
                if (previous != null && !"withdraw".equals(previous.decision())) {
                    throw failure("NODE_VOTE_ALREADY_CAST", "The caller already has an active vote");
                }
                appendVote(actor, item, instance, task, decision,
                    previous == null ? null : previous.id(), flow, requestId);
                Map<UUID, NodeVote> after = latestVotes(actor, item, instance, task);
                if (thresholdReached(actor, item, task, after)) {
                    if (repository.completeTask(task.id(), task.aggregateVersion()) != 1) {
                        throw staleTask();
                    }
                    yield completeManualToken(
                        actor, item, instance, task, flow, "completed", requestId
                    );
                }
                yield new CommandEffect(false);
            }
            case "withdraw" -> {
                requireVoteStrategy(task);
                if (!task.candidateUserIds().contains(actor.id())) {
                    throw failure("FORBIDDEN", "Only a frozen task candidate can withdraw a vote");
                }
                Map<UUID, NodeVote> latest = latestVotes(actor, item, instance, task);
                NodeVote previous = latest.get(actor.id());
                if (previous == null || "withdraw".equals(previous.decision())) {
                    throw failure("NODE_VOTE_NOT_ACTIVE", "The caller has no active vote to withdraw");
                }
                appendVote(actor, item, instance, task, "withdraw", previous.id(), flow, requestId);
                yield new CommandEffect(false);
            }
            default -> throw failure("NODE_ACTION_UNAVAILABLE", "Node task action is unavailable");
        };
    }

    private CommandEffect completeManualToken(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeTask task,
        RuntimeNodeFlow flow,
        String eventKind,
        String requestId
    ) {
        NodeToken token = repository.lockToken(
            actor.workspaceId(), item.spaceId(), instance.id(), task.tokenId()
        ).orElseThrow(() -> failure("NODE_TOKEN_NOT_FOUND", "Node token is not available"));
        if (repository.updateTokenStatus(
            actor.workspaceId(), item.spaceId(), instance.id(), token.id(),
            "waiting", "completed"
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Node token was changed concurrently");
        }
        appendHistory(actor, item, instance, eventKind, task.nodeKey(), token.id(), task.id(),
            "user", flow.policyVersion() + ":" + eventKind, requestId,
            objectMapper.createObjectNode());
        boolean completed = advance(
            actor, item, instance, flow, token, requestId
        );
        return new CommandEffect(completed);
    }

    private CanonicalValues prepareSubmission(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeTask task,
        JsonNode fieldPatch,
        List<NodeArtifactInput> artifacts
    ) {
        JsonNode patch = fieldPatch == null ? objectMapper.createObjectNode() : fieldPatch;
        if (!patch.isObject()) {
            throw failure("INVALID_NODE_FORM_PATCH", "Node form patch must be an object");
        }
        Map<String, JsonNode> formFields = new LinkedHashMap<>();
        task.formSnapshot().path("fields").forEach(
            field -> formFields.put(field.path("fieldKey").asText(), field)
        );
        patch.fieldNames().forEachRemaining(key -> {
            JsonNode policy = formFields.get(key);
            if (policy == null || "hidden".equals(policy.path("mode").asText())) {
                throw failure("NOT_FOUND_OR_HIDDEN", "Node form field is not available");
            }
            if (!"write".equals(policy.path("mode").asText())) {
                throw failure("FORBIDDEN", "Node form field is read-only");
            }
        });
        RuntimeConfiguration configuration = configuration(item);
        JsonNode prepared = projection.prepareUpdate(
            configuration, space.currentUserRole(), space.status(), item.fieldValues(), patch
        );
        for (JsonNode policy : formFields.values()) {
            String key = policy.path("fieldKey").asText();
            if (policy.path("required").asBoolean(false)
                && (!prepared.has(key) || blank(prepared.get(key)))) {
                throw failure("NODE_REQUIRED_FIELD_MISSING", "Required node form field is missing");
            }
        }
        CanonicalValues values = valueCodec.canonicalize(configuration, prepared);
        validateAndStoreArtifacts(actor, item, instance, task, artifacts);
        return values;
    }

    private void validateAndStoreArtifacts(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeTask task,
        List<NodeArtifactInput> requested
    ) {
        Map<String, JsonNode> policies = new LinkedHashMap<>();
        task.artifactPolicySnapshot().forEach(
            policy -> policies.put(policy.path("artifactKey").asText(), policy)
        );
        Map<String, Integer> counts = new HashMap<>();
        repository.taskArtifacts(
            actor.workspaceId(), item.spaceId(), instance.id(), task.id()
        ).forEach(artifact -> counts.merge(artifact.artifactKey(), 1, Integer::sum));
        Set<String> references = new HashSet<>();
        LinkedHashSet<UUID> fileIds = new LinkedHashSet<>();
        for (NodeArtifactInput artifact : requested) {
            if (artifact == null || artifact.artifactKey() == null || artifact.kind() == null) {
                throw failure("INVALID_NODE_ARTIFACT", "Artifact reference is incomplete");
            }
            JsonNode policy = policies.get(artifact.artifactKey());
            if (policy == null || !artifact.kind().equals(policy.path("kind").asText())) {
                throw failure("INVALID_NODE_ARTIFACT", "Artifact is not allowed by the frozen task policy");
            }
            String reference;
            if ("file".equals(artifact.kind())) {
                if (artifact.fileId() == null || artifact.objectType() != null || artifact.objectId() != null) {
                    throw failure("INVALID_NODE_ARTIFACT", "File artifact reference is invalid");
                }
                fileIds.add(artifact.fileId());
                reference = artifact.artifactKey() + ":file:" + artifact.fileId();
            } else {
                if (artifact.fileId() != null || artifact.objectType() == null || artifact.objectId() == null
                    || !containsText(policy.path("objectTypes"), artifact.objectType())) {
                    throw failure("INVALID_NODE_ARTIFACT", "Object artifact reference is invalid");
                }
                if (objectRegistry.accessState(
                    actor.workspaceId(), actor.id(), artifact.objectType(), artifact.objectId()
                ) != ObjectAccessState.available) {
                    throw failure("NOT_FOUND_OR_HIDDEN", "Referenced platform object is unavailable");
                }
                reference = artifact.artifactKey() + ":object:" + artifact.objectType() + ":" + artifact.objectId();
            }
            if (!references.add(reference)) {
                throw failure("DUPLICATE_NODE_ARTIFACT", "Artifact references must be unique");
            }
            int count = counts.merge(artifact.artifactKey(), 1, Integer::sum);
            if (count > policy.path("maxCount").asInt()) {
                throw failure("NODE_ARTIFACT_LIMIT_EXCEEDED", "Artifact count exceeds the frozen task policy");
            }
        }
        Map<UUID, FileAccess.FileResult> files = fileAccess.resolve(
            actor.workspaceId(), actor.id(), fileIds
        );
        for (UUID fileId : fileIds) {
            FileAccess.FileResult result = files.get(fileId);
            if (result == null || result.availability() != Availability.AVAILABLE
                || result.metadata() == null || result.metadata().state() != FileState.ACTIVE) {
                throw failure("NOT_FOUND_OR_HIDDEN", "Referenced file is unavailable");
            }
        }
        for (JsonNode policy : policies.values()) {
            if (policy.path("required").asBoolean(false)
                && counts.getOrDefault(policy.path("artifactKey").asText(), 0) == 0) {
                throw failure("NODE_REQUIRED_ARTIFACT_MISSING", "Required node artifact is missing");
            }
        }
        for (NodeArtifactInput artifact : requested) {
            repository.insertTaskArtifact(new TaskArtifactInsert(
                UUID.randomUUID(), actor.workspaceId(), item.spaceId(), instance.id(), task.id(),
                artifact.artifactKey(), artifact.kind(), artifact.fileId(), artifact.objectType(),
                artifact.objectId(), actor.id()
            ));
            if ("file".equals(artifact.kind())) {
                fileAccess.linkUsage(
                    actor.workspaceId(), actor.id(), artifact.fileId(), "work_item", item.id()
                );
            }
        }
    }

    private boolean containsText(JsonNode values, String candidate) {
        for (JsonNode value : values) {
            if (candidate.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean blank(JsonNode value) {
        return value == null || value.isNull()
            || value.isTextual() && value.asText().isBlank()
            || value.isArray() && value.isEmpty();
    }

    private NodeWorkflowInstance initialize(
        CurrentUser actor,
        WorkItem item,
        RuntimeNodeFlow flow
    ) {
        UUID instanceId = UUID.randomUUID();
        boolean inserted = repository.tryStartInstance(new InstanceStart(
            instanceId, actor.workspaceId(), item.spaceId(), item.id(),
            item.typeDefinitionId(), item.typeVersionId(), item.configHash(),
            item.version(), actor.id()
        ));
        if (!inserted) {
            NodeWorkflowInstance existing = repository.findInstance(
                actor.workspaceId(), item.spaceId(), item.id()
            ).orElseThrow(() -> failure(
                "NODE_WORKFLOW_INITIALIZATION_CONFLICT",
                "Node workflow instance could not be initialized"
            ));
            verifyBinding(item, existing);
            return existing;
        }
        NodeWorkflowInstance instance = repository.lockInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow();
        appendHistory(actor, item, instance, "started", flow.startNode().nodeKey(),
            null, null, "system", flow.policyVersion() + ":start",
            "start:" + item.id(), objectMapper.createObjectNode());
        NodeToken start = insertToken(
            actor, item, instance, flow.startNode(), null, null, null,
            "root:" + instance.id(), "start:" + item.id()
        );
        boolean completed = processAutomaticQueue(
            actor, item, instance, flow, new ArrayDeque<>(List.of(start)),
            "start:" + item.id()
        );
        if (repository.updateInstance(
            instance.id(), "active", completed ? "completed" : "active",
            item.version(), item.version(), 0, actor.id()
        ) != 1) {
            throw failure(
                "NODE_WORKFLOW_INITIALIZATION_CONFLICT",
                "Node workflow initialization lost its version"
            );
        }
        NodeWorkflowInstance started = repository.findInstance(
            actor.workspaceId(), item.spaceId(), item.id()
        ).orElseThrow();
        emit(
            actor, item, instance,
            new NodeCommandResult(
                item.id(), instance.id(), null, "start", flow.startNode().nodeKey(),
                started.status(), started.workItemVersion(), started.aggregateVersion(), false
            ),
            flow.policyVersion() + ":start", "start:" + item.id()
        );
        return started;
    }

    private boolean advance(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken completedToken,
        String causationId
    ) {
        ArrayDeque<NodeToken> queue = new ArrayDeque<>();
        enqueueOutgoing(actor, item, instance, flow, completedToken, queue, causationId);
        return processAutomaticQueue(actor, item, instance, flow, queue, causationId);
    }

    private boolean processAutomaticQueue(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        ArrayDeque<NodeToken> queue,
        String causationId
    ) {
        int steps = 0;
        while (!queue.isEmpty()) {
            if (++steps > MAX_INTERNAL_STEPS) {
                throw failure(
                    "NODE_WORKFLOW_STEP_LIMIT",
                    "Node workflow exceeded its bounded internal step limit"
                );
            }
            NodeToken token = queue.removeFirst();
            NodeDefinition node = flow.nodes().get(token.nodeKey());
            if (node == null) {
                throw failure(
                    "NODE_WORKFLOW_DEFINITION_INVALID",
                    "Active token references a node outside the bound snapshot"
                );
            }
            switch (node.kind()) {
                case manual -> createTask(actor, item, instance, flow, token, node, causationId);
                case join -> processJoin(
                    actor, item, instance, flow, token, node, queue, causationId
                );
                case end -> completeAutomaticToken(
                    actor, item, instance, flow, token, "completed", causationId
                );
                case start, automatic -> {
                    completeAutomaticToken(
                        actor, item, instance, flow, token, "completed", causationId
                    );
                    enqueueOutgoing(
                        actor, item, instance, flow, token, queue, causationId
                    );
                }
                case branch -> {
                    completeAutomaticToken(
                        actor, item, instance, flow, token, "split", causationId
                    );
                    enqueueBranch(actor, item, instance, flow, token, queue, causationId);
                }
            }
        }
        return repository.activeTokens(
            actor.workspaceId(), item.spaceId(), instance.id()
        ).isEmpty();
    }

    private NodeToken insertToken(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeDefinition node,
        UUID parentTokenId,
        String splitKey,
        String joinKey,
        String correlationKey,
        String causationId
    ) {
        UUID id = UUID.randomUUID();
        repository.insertToken(new TokenInsert(
            id, actor.workspaceId(), item.spaceId(), instance.id(), node.nodeKey(),
            node.stageKey(), "active", parentTokenId, splitKey, joinKey, correlationKey
        ));
        appendHistory(actor, item, instance, "entered", node.nodeKey(), id, null,
            "system", policyReference(instance, node.nodeKey()), causationId,
            objectMapper.createObjectNode());
        return new NodeToken(
            id, instance.id(), node.nodeKey(), node.stageKey(), "active",
            parentTokenId, splitKey, joinKey, correlationKey, 0, null, null
        );
    }

    private void createTask(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken token,
        NodeDefinition node,
        String causationId
    ) {
        if (repository.updateTokenStatus(
            actor.workspaceId(), item.spaceId(), instance.id(), token.id(),
            "active", "waiting"
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Node token was changed concurrently");
        }
        UUID taskId = UUID.randomUUID();
        JsonNode configuration = node.configuration();
        JsonNode assignment = configuration.path("assignment");
        LinkedHashSet<String> candidateRoles = new LinkedHashSet<>(node.candidateRoles());
        assignment.path("participantRoles").forEach(value -> candidateRoles.add(value.asText()));
        assignment.path("spaceRoles").forEach(value -> candidateRoles.add(value.asText()));
        LinkedHashSet<UUID> requestedCandidates = new LinkedHashSet<>(
            repository.candidateUserIds(
                actor.workspaceId(), item.spaceId(), item.id(), List.copyOf(candidateRoles)
            )
        );
        assignment.path("explicitUserIds").forEach(
            value -> requestedCandidates.add(UUID.fromString(value.asText()))
        );
        assignment.path("fieldParticipantKeys").forEach(value ->
            collectCandidateIds(item.fieldValues().path(value.asText()), requestedCandidates)
        );
        Set<UUID> frozenCandidates = repository.activeMemberUserIds(
            actor.workspaceId(), item.spaceId(), requestedCandidates
        );
        JsonNode formSnapshot = configuration.path("form").isObject()
            ? configuration.path("form").deepCopy()
            : objectMapper.createObjectNode().set("fields", objectMapper.createArrayNode());
        JsonNode artifactSnapshot = configuration.path("artifacts").isArray()
            ? configuration.path("artifacts").deepCopy()
            : objectMapper.createArrayNode();
        Instant now = Instant.now();
        JsonNode schedule = configuration.path("schedule");
        Instant plannedStartAt = now.plus(
            schedule.path("plannedDelayMinutes").asLong(0), ChronoUnit.MINUTES
        );
        Instant dueAt = schedule.has("dueAfterMinutes")
            ? plannedStartAt.plus(schedule.path("dueAfterMinutes").asLong(), ChronoUnit.MINUTES)
            : null;
        repository.insertTask(new TaskInsert(
            taskId, actor.workspaceId(), item.spaceId(), instance.id(), token.id(),
            node.nodeKey(), node.processingStrategy().name(), List.copyOf(candidateRoles),
            frozenCandidates.stream().sorted().toList(), node.quorumCount(),
            formSnapshot, artifactSnapshot, plannedStartAt, dueAt
        ));
        ObjectNode taskPayload = objectMapper.createObjectNode()
            .put("candidateCount", frozenCandidates.size());
        if (dueAt != null) {
            taskPayload.put("dueAt", dueAt.toString());
        }
        appendHistory(actor, item, instance, "task_created", node.nodeKey(), token.id(),
            taskId, "system", flow.policyVersion() + ":" + node.nodeKey(),
            causationId, taskPayload);
        if (frozenCandidates.isEmpty()) {
            appendHistory(actor, item, instance, "assignment_empty", node.nodeKey(), token.id(),
                taskId, "system", flow.policyVersion() + ":" + node.nodeKey(),
                causationId, objectMapper.createObjectNode().put("recoverableBy", "space_oversight"));
        }
    }

    private void collectCandidateIds(JsonNode value, Set<UUID> result) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(item -> collectCandidateIds(item, result));
            return;
        }
        if (value.isTextual()) {
            try {
                result.add(UUID.fromString(value.asText()));
            } catch (IllegalArgumentException ignored) {
                // Published field values may be stale; invalid dynamic candidates fail closed.
            }
        }
    }

    private void completeAutomaticToken(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken token,
        String eventKind,
        String causationId
    ) {
        if (repository.updateTokenStatus(
            actor.workspaceId(), item.spaceId(), instance.id(), token.id(),
            "active", "completed"
        ) != 1) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Automatic token was changed concurrently");
        }
        appendHistory(actor, item, instance, eventKind, token.nodeKey(), token.id(), null,
            "system", flow.policyVersion() + ":" + token.nodeKey(), causationId,
            objectMapper.createObjectNode());
    }

    private void enqueueOutgoing(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken source,
        ArrayDeque<NodeToken> queue,
        String causationId
    ) {
        List<EdgeDefinition> eligible = eligibleEdges(
            flow.outgoing().getOrDefault(source.nodeKey(), List.of()), item.fieldValues()
        );
        if (eligible.isEmpty()) {
            if (flow.nodes().get(source.nodeKey()).kind() != NodeKind.end) {
                throw failure(
                    "NODE_WORKFLOW_NO_ROUTE",
                    "No outgoing edge matched the bound node definition"
                );
            }
            return;
        }
        if (eligible.size() != 1) {
            throw failure(
                "NODE_WORKFLOW_ROUTE_AMBIGUOUS",
                "A non-branch node resolved more than one outgoing edge"
            );
        }
        queue.add(insertForEdge(
            actor, item, instance, flow, source, eligible.get(0), null, causationId
        ));
    }

    private void enqueueBranch(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken source,
        ArrayDeque<NodeToken> queue,
        String causationId
    ) {
        var branch = flow.branchesByNode().get(source.nodeKey());
        if (branch == null) {
            throw failure("NODE_WORKFLOW_DEFINITION_INVALID", "Branch definition is missing");
        }
        List<EdgeDefinition> eligible = branch.edgeKeys().stream()
            .map(flow.edges()::get)
            .filter(java.util.Objects::nonNull)
            .filter(edge -> condition(edge.condition(), item.fieldValues()))
            .sorted(Comparator.comparingInt(EdgeDefinition::priority)
                .thenComparing(EdgeDefinition::edgeKey))
            .toList();
        if (eligible.isEmpty()) {
            throw failure("NODE_WORKFLOW_NO_ROUTE", "Branch has no matching edge");
        }
        List<EdgeDefinition> selected = branch.mode() == BranchMode.exclusive
            ? List.of(eligible.get(0))
            : eligible;
        String splitCorrelation = source.correlationKey()
            + "|split:" + source.id() + ":" + branch.branchKey();
        for (EdgeDefinition edge : selected) {
            queue.add(insertForEdge(
                actor, item, instance, flow, source, edge, branch.branchKey(),
                splitCorrelation, causationId
            ));
        }
    }

    private NodeToken insertForEdge(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken source,
        EdgeDefinition edge,
        String splitKey,
        String causationId
    ) {
        return insertForEdge(
            actor, item, instance, flow, source, edge, splitKey,
            source.correlationKey(), causationId
        );
    }

    private NodeToken insertForEdge(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken source,
        EdgeDefinition edge,
        String splitKey,
        String correlationKey,
        String causationId
    ) {
        NodeDefinition target = flow.nodes().get(edge.toNodeKey());
        if (target == null) {
            throw failure("NODE_WORKFLOW_DEFINITION_INVALID", "Edge target is missing");
        }
        JoinDefinition join = flow.joinsByNode().get(target.nodeKey());
        return insertToken(
            actor, item, instance, target, source.id(), splitKey,
            join == null ? null : join.joinKey(), correlationKey, causationId
        );
    }

    private void processJoin(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        RuntimeNodeFlow flow,
        NodeToken token,
        NodeDefinition node,
        ArrayDeque<NodeToken> queue,
        String causationId
    ) {
        JoinDefinition definition = flow.joinsByNode().get(node.nodeKey());
        if (definition == null) {
            throw failure("NODE_WORKFLOW_DEFINITION_INVALID", "Join definition is missing");
        }
        repository.tryCreateJoin(new JoinInsert(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), instance.id(),
            definition.joinKey(), node.nodeKey(), token.correlationKey(),
            definition.policy().name(), definition.inboundEdgeKeys().size(),
            definition.quorumCount()
        ));
        NodeJoin join = repository.lockJoin(
            actor.workspaceId(), item.spaceId(), instance.id(), definition.joinKey(),
            token.correlationKey()
        ).orElseThrow(() -> failure("NODE_WORKFLOW_JOIN_CONFLICT", "Join could not be loaded"));
        if ("released".equals(join.status())) {
            repository.updateTokenStatus(
                actor.workspaceId(), item.spaceId(), instance.id(), token.id(),
                "active", "canceled"
            );
            return;
        }
        if (!repository.tryRecordJoinArrival(new JoinArrival(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), instance.id(),
            join.id(), token.id()
        ))) {
            throw failure("NODE_WORKFLOW_JOIN_DUPLICATE", "Token already arrived at this join");
        }
        int arrived = join.arrivedCount() + 1;
        if (repository.updateJoinArrival(join.id(), join.arrivedCount(), arrived) != 1) {
            throw failure("NODE_WORKFLOW_JOIN_CONFLICT", "Join arrival was changed concurrently");
        }
        if (!joinReached(definition, arrived)) {
            if (repository.updateTokenStatus(
                actor.workspaceId(), item.spaceId(), instance.id(), token.id(),
                "active", "waiting"
            ) != 1) {
                throw failure("NODE_WORKFLOW_JOIN_CONFLICT", "Join token could not wait");
            }
            return;
        }
        long expectedJoinVersion = join.aggregateVersion() + 1;
        if (repository.releaseJoin(join.id(), arrived, expectedJoinVersion) != 1) {
            throw failure("NODE_WORKFLOW_JOIN_CONFLICT", "Join was released concurrently");
        }
        int canceledTasks = 0;
        int closedTokens;
        if (definition.policy() == JoinPolicy.all) {
            closedTokens = repository.closeJoinTokens(
                actor.workspaceId(), item.spaceId(), instance.id(), node.nodeKey(),
                token.correlationKey(), "completed"
            );
        } else {
            canceledTasks = repository.cancelOpenTasksForCorrelation(
                actor.workspaceId(), item.spaceId(), instance.id(), token.correlationKey()
            );
            closedTokens = repository.closeOpenTokensForCorrelation(
                actor.workspaceId(), item.spaceId(), instance.id(),
                token.correlationKey(), "canceled"
            );
        }
        appendHistory(actor, item, instance, "joined", node.nodeKey(), token.id(), null,
            "system", flow.policyVersion() + ":" + definition.joinKey(), causationId,
            objectMapper.createObjectNode()
                .put("joinKey", definition.joinKey())
                .put("arrivedCount", arrived)
                .put("closedTokenCount", closedTokens)
                .put("canceledTaskCount", canceledTasks));
        List<EdgeDefinition> outgoing = eligibleEdges(
            flow.outgoing().getOrDefault(node.nodeKey(), List.of()), item.fieldValues()
        );
        if (outgoing.size() != 1) {
            throw failure("NODE_WORKFLOW_ROUTE_AMBIGUOUS", "Released join must have one route");
        }
        NodeToken merged = insertForEdge(
            actor, item, instance, flow, token, outgoing.get(0), null,
            parentCorrelation(token.correlationKey()), causationId
        );
        queue.add(merged);
    }

    private String parentCorrelation(String correlationKey) {
        int split = correlationKey.lastIndexOf("|split:");
        return split < 0 ? correlationKey : correlationKey.substring(0, split);
    }

    private void appendVote(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeTask task,
        String decision,
        UUID supersedesVoteId,
        RuntimeNodeFlow flow,
        String causationId
    ) {
        long sequence = repository.nextHistorySequence(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        repository.appendVote(new VoteInsert(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), instance.id(),
            task.id(), task.tokenId(), task.nodeKey(), actor.id(), decision,
            supersedesVoteId, sequence, objectMapper.createObjectNode()
        ));
        appendHistory(actor, item, instance, "voted", task.nodeKey(), task.tokenId(),
            task.id(), "user", flow.policyVersion() + ":vote", causationId,
            objectMapper.createObjectNode().put("decision", decision));
    }

    private Map<UUID, NodeVote> latestVotes(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeTask task
    ) {
        LinkedHashMap<UUID, NodeVote> latest = new LinkedHashMap<>();
        repository.votes(
            actor.workspaceId(), item.spaceId(), instance.id(), task.id()
        ).forEach(vote -> latest.put(vote.voterId(), vote));
        return latest;
    }

    private boolean thresholdReached(
        CurrentUser actor,
        WorkItem item,
        NodeTask task,
        Map<UUID, NodeVote> latest
    ) {
        long approvals = latest.values().stream()
            .filter(vote -> "approve".equals(vote.decision()))
            .count();
        return switch (ProcessingStrategy.valueOf(task.assignmentStrategy())) {
            case all -> {
                int candidates = task.candidateUserIds().size();
                yield candidates > 0 && approvals >= candidates;
            }
            case quorum -> approvals >= task.quorumCount();
            default -> false;
        };
    }

    private boolean joinReached(JoinDefinition definition, int arrived) {
        return switch (definition.policy()) {
            case all -> arrived >= definition.inboundEdgeKeys().size();
            case any -> arrived >= 1;
            case quorum -> arrived >= definition.quorumCount();
        };
    }

    private List<EdgeDefinition> eligibleEdges(List<EdgeDefinition> edges, JsonNode fields) {
        return edges.stream().filter(edge -> condition(edge.condition(), fields)).toList();
    }

    private boolean condition(JsonNode condition, JsonNode fields) {
        if (condition == null || condition.isNull()) {
            return true;
        }
        String operator = condition.path("operator").asText();
        if ("all".equals(operator)) {
            for (JsonNode operand : condition.path("operands")) {
                if (!condition(operand, fields)) {
                    return false;
                }
            }
            return true;
        }
        if ("any".equals(operator)) {
            for (JsonNode operand : condition.path("operands")) {
                if (condition(operand, fields)) {
                    return true;
                }
            }
            return false;
        }
        if ("not".equals(operator)) {
            return !condition(condition.get("operand"), fields);
        }
        JsonNode actual = fields == null ? null : fields.get(condition.path("fieldKey").asText());
        JsonNode expected = condition.get("value");
        return switch (operator) {
            case "present" -> !missing(actual);
            case "absent" -> missing(actual);
            case "eq" -> actual != null && actual.equals(expected);
            case "ne" -> actual == null || !actual.equals(expected);
            case "in" -> contains(expected, actual);
            case "not_in" -> !contains(expected, actual);
            case "gt" -> numeric(actual, expected, value -> value > 0);
            case "gte" -> numeric(actual, expected, value -> value >= 0);
            case "lt" -> numeric(actual, expected, value -> value < 0);
            case "lte" -> numeric(actual, expected, value -> value <= 0);
            default -> throw failure(
                "NODE_WORKFLOW_CONDITION_UNSUPPORTED",
                "Bound node flow contains an unsupported condition operator"
            );
        };
    }

    private boolean numeric(
        JsonNode actual,
        JsonNode expected,
        java.util.function.IntPredicate predicate
    ) {
        if (actual == null || expected == null || !actual.isNumber() || !expected.isNumber()) {
            return false;
        }
        return predicate.test(
            new BigDecimal(actual.asText()).compareTo(new BigDecimal(expected.asText()))
        );
    }

    private boolean contains(JsonNode container, JsonNode candidate) {
        if (container == null || candidate == null || !container.isArray()) {
            return false;
        }
        for (JsonNode value : container) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean missing(JsonNode value) {
        return value == null || value.isNull()
            || value.isTextual() && value.asText().isBlank()
            || value.isArray() && value.isEmpty();
    }

    private List<NodeAvailableAction> actions(
        NodeTask task,
        UUID actorId,
        Set<String> roles,
        NodeVote activeVote,
        long workItemVersion,
        long instanceVersion,
        String policyVersion
    ) {
        if (!eligible(task, actorId, roles)) {
            return List.of();
        }
        ArrayList<NodeAvailableAction> result = new ArrayList<>();
        ProcessingStrategy strategy = ProcessingStrategy.valueOf(task.assignmentStrategy());
        if (strategy == ProcessingStrategy.single || strategy == ProcessingStrategy.any) {
            if ("pending".equals(task.status())) {
                if (task.candidateUserIds().contains(actorId)) {
                    result.add(action("claim", task, workItemVersion, instanceVersion, policyVersion));
                }
            } else if ("claimed".equals(task.status()) && actorId.equals(task.assigneeId())) {
                String completionAction = task.formSnapshot().path("fields").size() > 0
                    || task.artifactPolicySnapshot().size() > 0 ? "submit" : "complete";
                result.add(action(completionAction, task, workItemVersion, instanceVersion, policyVersion));
                result.add(action("delegate", task, workItemVersion, instanceVersion, policyVersion));
            }
            if (oversight(roles) && task.candidateUserIds().isEmpty()) {
                result.add(action("transfer", task, workItemVersion, instanceVersion, policyVersion));
            }
        } else if ((strategy == ProcessingStrategy.all || strategy == ProcessingStrategy.quorum)
            && task.candidateUserIds().contains(actorId)) {
            if (activeVote == null || "withdraw".equals(activeVote.decision())) {
                result.add(action("vote", task, workItemVersion, instanceVersion, policyVersion));
            } else {
                result.add(action("withdraw", task, workItemVersion, instanceVersion, policyVersion));
            }
        }
        return List.copyOf(result);
    }

    private NodeAvailableAction action(
        String actionKey,
        NodeTask task,
        long workItemVersion,
        long instanceVersion,
        String policyVersion
    ) {
        return new NodeAvailableAction(
            actionKey, task.id(), task.nodeKey(), "allowed",
            workItemVersion, instanceVersion, policyVersion
        );
    }

    private boolean visible(NodeTask task, UUID actorId, Set<String> roles) {
        return eligible(task, actorId, roles);
    }

    private boolean eligible(NodeTask task, UUID actorId, Set<String> roles) {
        return actorId.equals(task.assigneeId())
            || task.candidateUserIds().contains(actorId)
            || oversight(roles);
    }

    private boolean oversight(Set<String> roles) {
        return roles.contains("owner") || roles.contains("admin");
    }

    private Set<String> actorRoles(
        CurrentUser actor,
        ProjectSpaceSummary space,
        WorkItem item
    ) {
        java.util.HashSet<String> result = new java.util.HashSet<>();
        result.add(space.currentUserRole());
        result.addAll(repository.participantRoles(
            actor.workspaceId(), item.spaceId(), item.id(), actor.id()
        ));
        return Set.copyOf(result);
    }

    private NodeTaskView view(NodeTask task) {
        return new NodeTaskView(
            task.id(), task.tokenId(), task.nodeKey(), task.assignmentStrategy(),
            task.status(), task.assigneeId(), task.aggregateVersion(), task.createdAt(),
            task.plannedStartAt(), task.dueAt(), task.timedOutAt()
        );
    }

    private NodeTokenView view(NodeToken token) {
        return new NodeTokenView(
            token.id(), token.nodeKey(), token.stageKey(), token.status(), token.enteredAt()
        );
    }

    private void appendHistory(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        String eventKind,
        String nodeKey,
        UUID tokenId,
        UUID taskId,
        String actorClass,
        String decisionReference,
        String causationId,
        JsonNode payload
    ) {
        long sequence = repository.nextHistorySequence(
            actor.workspaceId(), item.spaceId(), instance.id()
        );
        repository.appendHistory(new HistoryAppend(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), instance.id(), item.id(),
            sequence, item.typeDefinitionId(), item.typeVersionId(), item.configHash(),
            eventKind, nodeKey, tokenId, taskId, actor.id(), actorClass, decisionReference,
            "node-workflow:" + instance.id() + ":" + sequence, causationId, payload
        ));
    }

    private void appendActivity(
        CurrentUser actor,
        WorkItem item,
        NodeCommandResult result,
        String decision
    ) {
        ObjectNode payload = objectMapper.createObjectNode()
            .put("instanceId", result.instanceId().toString())
            .put("operation", result.operation())
            .put("nodeKey", result.nodeKey())
            .put("workItemVersion", result.workItemVersion())
            .put("aggregateVersion", result.aggregateVersion());
        if (result.taskId() != null) {
            payload.put("taskId", result.taskId().toString());
        }
        if (decision != null) {
            payload.put("decision", decision);
        }
        workItemRepository.appendActivity(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(),
            "node_workflow." + result.operation(), actor.id(), payload
        );
    }

    private void emit(
        CurrentUser actor,
        WorkItem item,
        NodeWorkflowInstance instance,
        NodeCommandResult result,
        String decisionReference,
        String requestId
    ) {
        WorkItemNodeWorkflowEvent event = new WorkItemNodeWorkflowEvent(
            item.spaceId(), instance.id(), item.typeDefinitionId(), item.typeVersionId(),
            item.configHash(), result.operation(), result.nodeKey(), result.taskId(),
            result.workItemVersion(), result.aggregateVersion(), decisionReference
        );
        String dedupeSource = item.id() + ":" + result.operation() + ":" + requestId;
        outbox.append(
            actor.workspaceId(), WorkItemNodeWorkflowEvent.EVENT_TYPE,
            WorkItemNodeWorkflowEvent.AGGREGATE_TYPE, item.id(), actor.id(), event.payload(),
            "node-workflow:" + UUID.nameUUIDFromBytes(dedupeSource.getBytes(StandardCharsets.UTF_8))
        );
    }

    private CommandReceipt begin(
        CurrentUser actor,
        WorkItem item,
        UUID instanceId,
        String operation,
        String nodeKey,
        long expectedWorkItemVersion,
        Long expectedInstanceVersion,
        String requestId,
        String requestHash
    ) {
        CommandStart start = new CommandStart(
            UUID.randomUUID(), actor.workspaceId(), item.spaceId(), item.id(), instanceId,
            operation, nodeKey, expectedWorkItemVersion, expectedInstanceVersion,
            requestId, requestHash, actor.id()
        );
        if (repository.tryStartCommand(start)) {
            return new CommandReceipt(start.id(), requestHash, "pending", null, actor.id());
        }
        CommandReceipt existing = repository.findCommand(
            actor.workspaceId(), item.id(), operation, requestId
        ).orElseThrow(() -> failure(
            "NODE_WORKFLOW_COMMAND_CONFLICT",
            "Node workflow command receipt could not be acquired"
        ));
        if (!existing.requestHash().equals(requestHash) || !existing.createdBy().equals(actor.id())) {
            throw failure(
                "IDEMPOTENCY_KEY_REUSED",
                "Request id was already used with a different node workflow command"
            );
        }
        if (!"completed".equals(existing.status())) {
            throw failure(
                "NODE_WORKFLOW_COMMAND_IN_PROGRESS",
                "The same node workflow command is still in progress"
            );
        }
        return existing;
    }

    private NodeCommandResult replay(CommandReceipt receipt) {
        try {
            NodeCommandResult result = objectMapper.treeToValue(
                receipt.response(), NodeCommandResult.class
            );
            return new NodeCommandResult(
                result.workItemId(), result.instanceId(), result.taskId(), result.operation(),
                result.nodeKey(), result.instanceStatus(), result.workItemVersion(),
                result.aggregateVersion(), true
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored node workflow command response is invalid", exception);
        }
    }

    private String requestHash(
        CurrentUser actor,
        WorkItem item,
        UUID taskId,
        String operation,
        long workItemVersion,
        long instanceVersion,
        JsonNode arguments
    ) {
        ObjectNode value = objectMapper.createObjectNode()
            .put("actorId", actor.id().toString())
            .put("workItemId", item.id().toString())
            .put("operation", operation)
            .put("expectedWorkItemVersion", workItemVersion)
            .put("expectedInstanceVersion", instanceVersion);
        if (taskId != null) {
            value.put("taskId", taskId.toString());
        }
        value.set("arguments", arguments == null ? objectMapper.createObjectNode() : arguments);
        return canonicalizer.hash(value);
    }

    private WorkItem lock(CurrentUser actor, WorkItem suppliedItem) {
        return workItemRepository.lock(
            actor.workspaceId(), suppliedItem.spaceId(), suppliedItem.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
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

    private void verifyBinding(WorkItem item, NodeWorkflowInstance instance) {
        if (!instance.typeDefinitionId().equals(item.typeDefinitionId())
            || !instance.typeVersionId().equals(item.typeVersionId())
            || !instance.configHash().equals(item.configHash())) {
            throw failure(
                "NODE_WORKFLOW_BINDING_CONFLICT",
                "Node workflow binding does not match the work item"
            );
        }
    }

    private void verifyVersions(
        NodeWorkflowInstance instance,
        long expectedWorkItemVersion,
        long expectedInstanceVersion
    ) {
        if (instance.workItemVersion() != expectedWorkItemVersion
            || instance.aggregateVersion() != expectedInstanceVersion) {
            throw failure(
                "NODE_WORKFLOW_VERSION_CONFLICT",
                "Node workflow or work item version is stale"
            );
        }
    }

    private void requireActiveAndVersion(WorkItem item, long expectedWorkItemVersion) {
        if (!"active".equals(item.status())) {
            throw failure(
                "NODE_WORKFLOW_NOT_WRITABLE",
                "Node workflow commands require an active work item"
            );
        }
        if (item.version() != expectedWorkItemVersion) {
            throw failure("NODE_WORKFLOW_VERSION_CONFLICT", "Work item version is stale");
        }
    }

    private void requireOversight(ProjectSpaceSummary space) {
        if (!space.canManage()) {
            throw failure(
                "FORBIDDEN",
                "Only project space owners and admins may recover node workflows"
            );
        }
    }

    private String recoveryReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw failure(
                "INVALID_RECOVERY_REASON",
                "Recovery reason must contain 10 to 500 characters"
            );
        }
        return normalized;
    }

    private void requireClaimStrategy(NodeTask task) {
        if (!Set.of("single", "any").contains(task.assignmentStrategy())) {
            throw failure("NODE_ACTION_UNAVAILABLE", "Task does not use claim completion");
        }
    }

    private void requireVoteStrategy(NodeTask task) {
        if (!Set.of("all", "quorum").contains(task.assignmentStrategy())) {
            throw failure("NODE_ACTION_UNAVAILABLE", "Task does not use voting completion");
        }
    }

    private RuntimeException staleTask() {
        return failure("NODE_WORKFLOW_VERSION_CONFLICT", "Node task was changed concurrently");
    }

    private String operation(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("claim", "delegate", "transfer", "vote", "withdraw", "complete", "submit").contains(normalized)) {
            throw failure("NODE_ACTION_UNAVAILABLE", "Node task action is unavailable");
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

    private String policyReference(NodeWorkflowInstance instance, String nodeKey) {
        return instance.typeVersionId() + ":" + instance.configHash() + ":" + nodeKey;
    }

    private NodeWorkflowPresentation missing(
        String capability,
        String policyVersion,
        long workItemVersion
    ) {
        return new NodeWorkflowPresentation(
            capability, policyVersion, null, null, workItemVersion, 0,
            List.of(), List.of(), List.of()
        );
    }

    private record CommandEffect(boolean instanceCompleted) {
    }
}
