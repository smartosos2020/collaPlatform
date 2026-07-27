package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.file.contract.FileAccess;
import com.colla.platform.modules.identity.contract.SubjectDirectory;
import com.colla.platform.modules.identity.contract.SubjectDirectory.SubjectRef;
import com.colla.platform.modules.identity.contract.SubjectDirectory.SubjectSnapshot;
import com.colla.platform.modules.identity.contract.SubjectDirectory.SubjectState;
import com.colla.platform.modules.identity.contract.SubjectDirectory.SubjectType;
import com.colla.platform.modules.platform.contract.PlatformObjectCommands;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivityPage;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemAttachment;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemAttachmentState;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemComment;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemCommentState;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemCreateForm;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemPage;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemParticipant;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemParticipantState;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowBindingCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowPresentation;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCommandResult;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeArtifactInput;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskContext;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskInboxPage;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowPresentation;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeRecoveryResult;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillVerification;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCompensationRun;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillVerification;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.CanonicalValues;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.FieldProjection;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository.AttachmentLink;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository.CommandStart;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository.LockedType;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository.NewWorkItem;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.contract.WorkItemChangedEvent;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionExplanation;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter.EvaluationContext;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemService {
    private static final int MAX_PAGE_SIZE = 200;

    private final WorkItemRepository repository;
    private final ProjectSpaceRepository spaceRepository;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemRuntimeProjection projection;
    private final WorkItemFieldValueCodec valueCodec;
    private final WorkItemFieldTypeRegistry fieldTypeRegistry;
    private final SubjectDirectory subjectDirectory;
    private final FileAccess fileAccess;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final WorkItemStateFlowService stateFlowService;
    private final WorkItemNodeWorkflowService nodeWorkflowService;
    private final WorkItemStateBackfillService stateBackfillService;
    private final WorkItemNodeBackfillService nodeBackfillService;
    private final WorkItemRelationService relationService;
    private final WorkItemPermissionDecisionService permissionDecisionService;
    private final WorkItemPermissionGovernanceService permissionGovernanceService;
    private final PlatformObjectCommands objectCommands;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemService(
        WorkItemRepository repository,
        ProjectSpaceRepository spaceRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemRuntimeProjection projection,
        WorkItemFieldValueCodec valueCodec,
        WorkItemFieldTypeRegistry fieldTypeRegistry,
        SubjectDirectory subjectDirectory,
        FileAccess fileAccess,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        WorkItemStateFlowService stateFlowService,
        WorkItemNodeWorkflowService nodeWorkflowService,
        WorkItemStateBackfillService stateBackfillService,
        WorkItemNodeBackfillService nodeBackfillService,
        WorkItemRelationService relationService,
        WorkItemPermissionDecisionService permissionDecisionService,
        WorkItemPermissionGovernanceService permissionGovernanceService,
        PlatformObjectCommands objectCommands,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.spaceRepository = spaceRepository;
        this.snapshotAdapter = snapshotAdapter;
        this.projection = projection;
        this.valueCodec = valueCodec;
        this.fieldTypeRegistry = fieldTypeRegistry;
        this.subjectDirectory = subjectDirectory;
        this.fileAccess = fileAccess;
        this.canonicalizer = canonicalizer;
        this.stateFlowService = stateFlowService;
        this.nodeWorkflowService = nodeWorkflowService;
        this.stateBackfillService = stateBackfillService;
        this.nodeBackfillService = nodeBackfillService;
        this.relationService = relationService;
        this.permissionDecisionService = permissionDecisionService;
        this.permissionGovernanceService = permissionGovernanceService;
        this.objectCommands = objectCommands;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public PermissionExplanation explainPermission(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String action
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        RuntimeConfiguration configuration = configuration(user, item);
        // Explanation is available only after the object itself is visible. This preserves the
        // same 404 shape for data-scope and hidden-object denials.
        requirePermission(user, space, item, configuration, "view");
        var decision = permissionDecisionService.decide(
            configuration,
            subjectContext(user, space, item),
            spaceId,
            workItemId,
            action
        );
        return permissionGovernanceService.explainForUser(
            decision,
            !"permission_request".equals(action)
        );
    }

    @Transactional
    public WorkItemView create(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String title,
        JsonNode fieldValues,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        String normalizedTitle = title(title);
        String normalizedRequestId = requestId(requestId);
        String operation = "create";
        String requestHash = requestHash(user, operation, Map.of(
            "spaceId", spaceId.toString(),
            "typeId", typeId.toString(),
            "title", normalizedTitle,
            "fieldValues", fieldValues == null ? objectMapper.createObjectNode() : fieldValues
        ));
        CommandReceipt receipt = begin(
            user, spaceId, null, operation, normalizedRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }

        LockedType type = repository.lockCurrentType(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item type is not available"));
        if (!"active".equals(type.spaceStatus()) || !"active".equals(type.typeStatus())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space and work item type must be active");
        }
        RuntimeConfiguration configuration = snapshotAdapter.requireComplete(
            user.workspaceId(), spaceId, typeId, type.versionId()
        );
        if (!configuration.configHash().equals(type.configHash())) {
            throw failure("SNAPSHOT_INTEGRITY_FAILURE", "Current work item type hash does not match its snapshot");
        }
        requirePermission(user, space, null, configuration, "create");
        JsonNode preparedValues = projection.prepareCreate(
            configuration, space.currentUserRole(), type.spaceStatus(), fieldValues
        );
        CanonicalValues values = valueCodec.canonicalize(configuration, preparedValues);
        long number = repository.nextNumber(user.workspaceId(), spaceId, typeId);
        UUID workItemId = UUID.randomUUID();
        String displayKey = displayKey(type.typeKey(), number);
        repository.insert(new NewWorkItem(
            workItemId,
            user.workspaceId(),
            spaceId,
            typeId,
            type.versionId(),
            configuration.configHash(),
            number,
            displayKey,
            normalizedTitle,
            values.values(),
            user.id()
        ));
        repository.replaceFieldProjections(
            user.workspaceId(), spaceId, workItemId, values.projections()
        );
        repository.upsertParticipant(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            user.id(),
            "owner",
            user.id()
        );
        repository.appendActivity(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            "created",
            user.id(),
            activityPayload(0, "active")
        );
        stateFlowService.initializeForNewItem(
            user, requireItem(user, spaceId, workItemId)
        );
        nodeWorkflowService.initializeForNewItem(
            user, requireItem(user, spaceId, workItemId)
        );
        WorkItemView result = view(user, space, requireItem(user, spaceId, workItemId));
        complete(receipt, result);
        register(result.item(), user.id());
        auditLog.log(user, "work_item.created", "work_item", workItemId, Map.of(
            "spaceId", spaceId.toString(),
            "typeDefinitionId", typeId.toString(),
            "typeVersionId", type.versionId().toString(),
            "configHash", configuration.configHash(),
            "displayKey", displayKey
        ));
        appendEvent(user, result.item(), "created", normalizedRequestId);
        return result;
    }

    public WorkItemView get(CurrentUser user, UUID spaceId, UUID workItemId) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        return view(user, space, requireItem(user, spaceId, workItemId));
    }

    public WorkItemCreateForm createForm(CurrentUser user, UUID spaceId, UUID typeId) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        LockedType type = repository.findCurrentType(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item type is not available"));
        if (!"active".equals(type.typeStatus())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Work item type is not available");
        }
        RuntimeConfiguration configuration = snapshotAdapter.requireComplete(
            user.workspaceId(), spaceId, typeId, type.versionId()
        );
        requirePermission(user, space, null, configuration, "create");
        return new WorkItemCreateForm(
            type.typeId(),
            type.versionId(),
            type.typeKey(),
            type.typeName(),
            projection.runtimePresentation(
                configuration,
                space.currentUserRole(),
                space.status(),
                "create",
                objectMapper.createObjectNode()
            )
        );
    }

    public WorkItemPage list(CurrentUser user, UUID spaceId, UUID typeId, UUID cursor, int limit) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        List<WorkItem> rows = repository.list(
            user.workspaceId(), spaceId, typeId, cursor, safeLimit + 1
        );
        boolean hasMore = rows.size() > safeLimit;
        List<WorkItem> visible = hasMore ? rows.subList(0, safeLimit) : rows;
        List<WorkItemView> items = views(user, space, visible);
        return new WorkItemPage(
            items,
            hasMore && !visible.isEmpty() ? visible.get(visible.size() - 1).id() : null
        );
    }

    @Transactional
    public WorkItemView update(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String title,
        JsonNode fieldValues,
        long expectedVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem current = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, current, configuration(user, current), "edit");
        if (!"active".equals(current.status())) {
            throw failure("INVALID_WORK_ITEM_TRANSITION", "Archived work items must be restored before editing");
        }
        String nextTitle = title == null ? current.title() : title(title);
        JsonNode requested = fieldValues == null ? objectMapper.createObjectNode() : fieldValues;
        String normalizedRequestId = requestId(requestId);
        String operation = "update";
        String requestHash = requestHash(user, operation, Map.of(
            "workItemId", workItemId.toString(),
            "title", nextTitle,
            "fieldValues", requested,
            "expectedVersion", expectedVersion
        ));
        CommandReceipt receipt = begin(
            user, spaceId, workItemId, operation, normalizedRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        RuntimeConfiguration configuration = configuration(user, current);
        JsonNode preparedValues = projection.prepareUpdate(
            configuration, space.currentUserRole(), "active", current.fieldValues(), requested
        );
        requireWritableFields(user, space, current, configuration, requested);
        CanonicalValues values = valueCodec.canonicalize(configuration, preparedValues);
        if (repository.update(
            user.workspaceId(), spaceId, workItemId, nextTitle, values.values(), user.id(), expectedVersion
        ) != 1) {
            throw failure("WORK_ITEM_VERSION_CONFLICT", "Work item changed or is no longer editable");
        }
        stateFlowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        nodeWorkflowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        repository.replaceFieldProjections(
            user.workspaceId(), spaceId, workItemId, values.projections()
        );
        repository.appendActivity(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            "updated",
            user.id(),
            activityPayload(expectedVersion + 1, "active")
        );
        WorkItemView result = view(user, space, requireItem(user, spaceId, workItemId));
        complete(receipt, result);
        register(result.item(), user.id());
        auditLog.log(user, "work_item.updated", "work_item", workItemId, Map.of(
            "spaceId", spaceId.toString(),
            "previousVersion", current.version(),
            "currentVersion", result.item().version(),
            "configHash", current.configHash()
        ));
        appendEvent(user, result.item(), "updated", normalizedRequestId);
        return result;
    }

    @Transactional
    public WorkItemView transition(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String targetStatus,
        long expectedVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem current = requireItem(user, spaceId, workItemId);
        String target = switch (targetStatus) {
            case "archived", "active" -> targetStatus;
            default -> throw failure("INVALID_WORK_ITEM_STATUS", "Invalid work item status");
        };
        String expectedStatus = "archived".equals(target) ? "active" : "archived";
        if (!expectedStatus.equals(current.status())) {
            if (target.equals(current.status())) {
                return view(user, space, current);
            }
            throw failure("INVALID_WORK_ITEM_TRANSITION", "Work item cannot transition to the requested status");
        }
        String operation = "archived".equals(target) ? "archive" : "restore";
        requirePermission(user, space, current, configuration(user, current), operation);
        String normalizedRequestId = requestId(requestId);
        String requestHash = requestHash(user, operation, Map.of(
            "workItemId", workItemId.toString(),
            "expectedVersion", expectedVersion
        ));
        CommandReceipt receipt = begin(
            user, spaceId, workItemId, operation, normalizedRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        if ("archived".equals(target)) {
            relationService.beforeEndpointArchive(
                user, spaceId, workItemId, normalizedRequestId
            );
        }
        if (repository.transition(
            user.workspaceId(), spaceId, workItemId, expectedStatus, target, user.id(), expectedVersion
        ) != 1) {
            throw failure("WORK_ITEM_VERSION_CONFLICT", "Work item changed or cannot transition");
        }
        stateFlowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        nodeWorkflowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        repository.appendActivity(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            operation,
            user.id(),
            activityPayload(expectedVersion + 1, target)
        );
        WorkItemView result = view(user, space, requireItem(user, spaceId, workItemId));
        complete(receipt, result);
        register(result.item(), user.id());
        auditLog.log(user, "work_item." + operation, "work_item", workItemId, Map.of(
            "spaceId", spaceId.toString(),
            "previousStatus", expectedStatus,
            "currentStatus", target,
            "previousVersion", current.version(),
            "currentVersion", result.item().version()
        ));
        appendEvent(user, result.item(), operation, normalizedRequestId);
        return result;
    }

    public List<WorkItemParticipant> listParticipants(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return hydrateParticipants(user, repository.listParticipants(
            user.workspaceId(), spaceId, workItemId
        ));
    }

    @Transactional
    public WorkItemParticipantState changeParticipant(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID participantUserId,
        String participantRole,
        boolean remove,
        long expectedVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem current = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, current, configuration(user, current), "participant_manage");
        if (!"active".equals(current.status())) {
            throw failure("INVALID_WORK_ITEM_TRANSITION", "Archived work items cannot change participants");
        }
        String role = participantRole(participantRole);
        String operation = remove ? "participant_remove" : "participant_upsert";
        String normalizedRequestId = requestId(requestId);
        String requestHash = requestHash(user, operation, Map.of(
            "workItemId", workItemId.toString(),
            "participantUserId", participantUserId.toString(),
            "participantRole", role,
            "expectedVersion", expectedVersion
        ));
        CommandReceipt receipt = begin(
            user, spaceId, workItemId, operation, normalizedRequestId, requestHash
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt, WorkItemParticipantState.class);
        }
        SubjectSnapshot participant = subjectDirectory.resolve(
            user.workspaceId(),
            user.id(),
            List.of(new SubjectRef(SubjectType.MEMBER, participantUserId))
        ).get(new SubjectRef(SubjectType.MEMBER, participantUserId));
        if (participant == null || participant.state() != SubjectState.ACTIVE) {
            throw failure("PARTICIPANT_NOT_AVAILABLE", "Participant is not an active workspace user");
        }
        var existing = repository.findParticipant(
            user.workspaceId(), spaceId, workItemId, participantUserId
        );
        if (remove && existing.isPresent()
            && responsible(existing.get().role())
            && repository.countResponsibleParticipants(user.workspaceId(), spaceId, workItemId) <= 1) {
            throw failure("LAST_RESPONSIBLE_PARTICIPANT", "The last owner or assignee cannot be removed");
        }
        if (repository.touch(
            user.workspaceId(), spaceId, workItemId, user.id(), expectedVersion
        ) != 1) {
            throw failure("WORK_ITEM_VERSION_CONFLICT", "Work item changed or is no longer editable");
        }
        stateFlowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        nodeWorkflowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        if (remove) {
            repository.removeParticipant(
                user.workspaceId(), spaceId, workItemId, participantUserId
            );
        } else {
            repository.upsertParticipant(
                existing.map(WorkItemParticipant::id).orElseGet(UUID::randomUUID),
                user.workspaceId(),
                spaceId,
                workItemId,
                participantUserId,
                role,
                user.id()
            );
        }
        repository.appendActivity(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            remove ? "participant_removed" : "participant_changed",
            user.id(),
            objectMapper.valueToTree(Map.of(
                "version", expectedVersion + 1,
                "participantUserId", participantUserId.toString(),
                "participantRole", role
            ))
        );
        WorkItemParticipantState result = new WorkItemParticipantState(
            expectedVersion + 1,
            hydrateParticipants(user, repository.listParticipants(
                user.workspaceId(), spaceId, workItemId
            ))
        );
        complete(receipt, workItemId, result);
        auditLog.log(user, "work_item." + operation, "work_item", workItemId, Map.of(
            "spaceId", spaceId.toString(),
            "participantUserId", participantUserId.toString(),
            "participantRole", role,
            "version", expectedVersion + 1
        ));
        appendEvent(user, requireItem(user, spaceId, workItemId), operation, normalizedRequestId);
        return result;
    }

    public WorkItemActivityPage listActivities(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        Long beforeSequence,
        int limit
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        var rows = hydrateActivities(user, repository.listActivities(
            user.workspaceId(), spaceId, workItemId, beforeSequence, safeLimit + 1
        ));
        boolean hasMore = rows.size() > safeLimit;
        var visible = hasMore ? rows.subList(0, safeLimit) : rows;
        return new WorkItemActivityPage(
            List.copyOf(visible),
            hasMore && !visible.isEmpty() ? visible.get(visible.size() - 1).sequence() : null
        );
    }

    public List<WorkItemComment> listComments(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return hydrateComments(user, repository.listComments(user.workspaceId(), spaceId, workItemId));
    }

    @Transactional
    public WorkItemCommentState addComment(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String content,
        long expectedVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem current = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, current, configuration(user, current), "comment");
        if (!"active".equals(current.status())) {
            throw failure("INVALID_WORK_ITEM_TRANSITION", "Archived work items cannot be commented on");
        }
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty() || normalizedContent.length() > 20_000) {
            throw failure("INVALID_COMMENT", "Comment content must contain between 1 and 20000 characters");
        }
        String normalizedRequestId = requestId(requestId);
        String operation = "comment_add";
        CommandReceipt receipt = begin(
            user,
            spaceId,
            workItemId,
            operation,
            normalizedRequestId,
            requestHash(user, operation, Map.of(
                "workItemId", workItemId.toString(),
                "content", normalizedContent,
                "expectedVersion", expectedVersion
            ))
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt, WorkItemCommentState.class);
        }
        if (repository.touch(user.workspaceId(), spaceId, workItemId, user.id(), expectedVersion) != 1) {
            throw failure("WORK_ITEM_VERSION_CONFLICT", "Work item changed or is no longer editable");
        }
        stateFlowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        nodeWorkflowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        UUID commentId = UUID.randomUUID();
        repository.insertComment(
            commentId, user.workspaceId(), spaceId, workItemId, user.id(), normalizedContent
        );
        repository.appendActivity(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            "commented",
            user.id(),
            objectMapper.valueToTree(Map.of(
                "version", expectedVersion + 1,
                "commentId", commentId.toString()
            ))
        );
        WorkItemCommentState result = new WorkItemCommentState(
            expectedVersion + 1,
            hydrateComments(user, repository.listComments(user.workspaceId(), spaceId, workItemId))
        );
        complete(receipt, workItemId, result);
        auditLog.log(user, "work_item.comment_added", "work_item", workItemId, Map.of(
            "spaceId", spaceId.toString(),
            "commentId", commentId.toString(),
            "version", expectedVersion + 1
        ));
        appendEvent(user, requireItem(user, spaceId, workItemId), operation, normalizedRequestId);
        return result;
    }

    public List<WorkItemAttachment> listAttachments(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return hydrateAttachments(
            user,
            repository.listAttachments(user.workspaceId(), spaceId, workItemId)
        );
    }

    @Transactional
    public WorkItemAttachmentState addAttachment(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID fileId,
        long expectedVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem current = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, current, configuration(user, current), "attach");
        if (!"active".equals(current.status())) {
            throw failure("INVALID_WORK_ITEM_TRANSITION", "Archived work items cannot add attachments");
        }
        var file = fileAccess.resolve(
            user.workspaceId(), user.id(), Set.of(fileId)
        ).get(fileId);
        if (file == null || file.availability() != FileAccess.Availability.AVAILABLE) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Attachment file is not available");
        }
        String normalizedRequestId = requestId(requestId);
        String operation = "attachment_add";
        CommandReceipt receipt = begin(
            user,
            spaceId,
            workItemId,
            operation,
            normalizedRequestId,
            requestHash(user, operation, Map.of(
                "workItemId", workItemId.toString(),
                "fileId", fileId.toString(),
                "expectedVersion", expectedVersion
            ))
        );
        if ("completed".equals(receipt.status())) {
            return replay(receipt, WorkItemAttachmentState.class);
        }
        if (repository.touch(user.workspaceId(), spaceId, workItemId, user.id(), expectedVersion) != 1) {
            throw failure("WORK_ITEM_VERSION_CONFLICT", "Work item changed or is no longer editable");
        }
        stateFlowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        nodeWorkflowService.alignWorkItemVersion(user, current, expectedVersion, expectedVersion + 1);
        if (repository.insertAttachment(
            UUID.randomUUID(), user.workspaceId(), spaceId, workItemId, fileId, user.id()
        ) != 1) {
            throw failure("ATTACHMENT_ALREADY_LINKED", "File is already attached to this work item");
        }
        fileAccess.linkUsage(user.workspaceId(), user.id(), fileId, "work_item", workItemId);
        repository.appendActivity(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            "attachment_added",
            user.id(),
            objectMapper.valueToTree(Map.of(
                "version", expectedVersion + 1,
                "fileId", fileId.toString()
            ))
        );
        WorkItemAttachmentState result = new WorkItemAttachmentState(
            expectedVersion + 1,
            hydrateAttachments(
                user,
                repository.listAttachments(user.workspaceId(), spaceId, workItemId)
            )
        );
        complete(receipt, workItemId, result);
        auditLog.log(user, "work_item.attachment_added", "work_item", workItemId, Map.of(
            "spaceId", spaceId.toString(),
            "fileId", fileId.toString(),
            "version", expectedVersion + 1
        ));
        appendEvent(user, requireItem(user, spaceId, workItemId), operation, normalizedRequestId);
        return result;
    }

    public WorkItemPage query(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String fieldKey,
        String operator,
        JsonNode value,
        String sortDirection,
        int limit
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        String normalizedSort = sortDirection == null
            ? "none"
            : sortDirection.trim().toLowerCase(Locale.ROOT);
        RuntimeConfiguration configuration = requireQueryCapability(
            user, spaceId, typeId, fieldKey, operator, normalizedSort
        );
        ObjectNode requested = objectMapper.createObjectNode();
        requested.set(fieldKey, value);
        FieldProjection queryValue = valueCodec.canonicalize(configuration, requested)
            .projections().getFirst();
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        List<WorkItem> matched = repository.queryByProjection(
            user.workspaceId(),
            spaceId,
            typeId,
            fieldKey,
            operator,
            queryValue,
            normalizedSort,
            safeLimit
        );
        return new WorkItemPage(views(user, space, matched), null);
    }

    /**
     * Public capability boundary used by registered query consumers. Dynamic fields
     * can only enter a query after the bound published snapshot approves the operation.
     */
    public RuntimeConfiguration requireQueryCapability(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String fieldKey,
        String operator,
        String sortDirection
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        LockedType type = repository.findCurrentType(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item type is not available"));
        RuntimeConfiguration configuration = snapshotAdapter.requireComplete(
            user.workspaceId(), spaceId, typeId, type.versionId()
        );
        JsonNode field = projection.requireQueryableField(
            configuration, space.currentUserRole(), space.status(), fieldKey
        );
        var descriptor = fieldTypeRegistry.require(field.path("fieldType").asText());
        if (!descriptor.filterable() || !descriptor.operators().contains(operator)) {
            throw failure(
                "QUERY_CAPABILITY_UNAVAILABLE",
                "Field does not publish the requested query capability"
            );
        }
        String normalizedSort = sortDirection == null
            ? "none"
            : sortDirection.trim().toLowerCase(Locale.ROOT);
        if (!List.of("none", "asc", "desc").contains(normalizedSort)
            || !"none".equals(normalizedSort) && !descriptor.sortable()) {
            throw failure(
                "QUERY_CAPABILITY_UNAVAILABLE",
                "Field does not publish the requested sort capability"
            );
        }
        return configuration;
    }

    public void requireQueryScope(CurrentUser user, UUID spaceId) {
        requireMember(user, spaceId);
    }

    @Transactional
    public int rebuildFieldProjections(CurrentUser user, UUID spaceId, UUID workItemId) {
        requireWritableMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        CanonicalValues canonical = valueCodec.canonicalize(configuration(user, item), item.fieldValues());
        if (!canonical.values().equals(item.fieldValues())) {
            throw failure("PROJECTION_DRIFT", "Authoritative work item values are not canonical");
        }
        return repository.rebuildFieldProjections(
            user.workspaceId(), spaceId, workItemId, canonical.projections()
        );
    }

    private WorkItemView view(CurrentUser user, ProjectSpaceSummary space, WorkItem item) {
        return view(user, space, item, stateFlowService.presentation(user, space, item));
    }

    private WorkItemView view(
        CurrentUser user,
        ProjectSpaceSummary space,
        WorkItem item,
        WorkflowPresentation workflow
    ) {
        return view(user, space, item, workflow, configuration(user, item));
    }

    private WorkItemView view(
        CurrentUser user,
        ProjectSpaceSummary space,
        WorkItem item,
        WorkflowPresentation workflow,
        RuntimeConfiguration configuration
    ) {
        requirePermission(user, space, item, configuration, "view");
        JsonNode values = filterReadableFields(user, space, item, configuration, projection.projectDetail(
            configuration, space.currentUserRole(), space.status(), item.fieldValues()
        ));
        ObjectNode runtime = (ObjectNode) projection.runtimePresentation(
            configuration, space.currentUserRole(), space.status(), "detail", item.fieldValues()
        );
        runtime.set("workflow", objectMapper.valueToTree(workflow));
        return new WorkItemView(item, values, runtime, actions(user, space, item, configuration));
    }

    private List<WorkItemView> views(
        CurrentUser user,
        ProjectSpaceSummary space,
        List<WorkItem> items
    ) {
        Map<UUID, WorkflowPresentation> workflows = stateFlowService.presentations(user, space, items);
        Map<String, RuntimeConfiguration> configurations = new java.util.HashMap<>();
        return items.stream()
            .map(item -> view(
                user,
                space,
                item,
                workflows.get(item.id()),
                configurations.computeIfAbsent(
                    item.typeVersionId() + ":" + item.configHash(),
                    ignored -> configuration(user, item)
                )
            ))
            .toList();
    }

    public WorkflowPresentation workflow(CurrentUser user, UUID spaceId, UUID workItemId) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return stateFlowService.presentation(user, space, item);
    }

    public NodeWorkflowPresentation nodeWorkflow(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return nodeWorkflowService.presentation(
            user, space, item
        );
    }

    public List<NodeHistoryEntry> nodeWorkflowHistory(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        Long beforeSequence,
        int limit
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return nodeWorkflowService.history(
            user, item, beforeSequence, limit
        );
    }

    public NodeTaskContext nodeTaskContext(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID taskId
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return nodeWorkflowService.taskContext(
            user, space, item, taskId
        );
    }

    public NodeTaskInboxPage nodeTaskInbox(
        CurrentUser user,
        UUID spaceId,
        UUID cursor,
        int limit
    ) {
        return nodeWorkflowService.taskInbox(user, requireMember(user, spaceId), cursor, limit);
    }

    public int processDueNodeTasks(CurrentUser user, UUID spaceId, int limit) {
        return nodeWorkflowService.processDueTasks(
            user, requireWritableMember(user, spaceId), limit
        );
    }

    public NodeCommandResult startNodeWorkflow(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        long expectedWorkItemVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "transition");
        return nodeWorkflowService.start(
            user, item, expectedWorkItemVersion, requestId
        );
    }

    public NodeCommandResult executeNodeTask(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID taskId,
        String operation,
        String decision,
        UUID targetAssigneeId,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String requestId
    ) {
        return executeNodeTask(
            user, spaceId, workItemId, taskId, operation, decision, targetAssigneeId,
            null, List.of(), expectedWorkItemVersion, expectedInstanceVersion, requestId
        );
    }

    public NodeRecoveryResult recoverNodeWorkflow(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String commandKey,
        String reason,
        String confirmation,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "workflow_manage");
        return nodeWorkflowService.recover(
            user, space, item, commandKey,
            reason, confirmation, expectedWorkItemVersion, expectedInstanceVersion, requestId
        );
    }

    public NodeCommandResult upgradeNodeWorkflow(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID targetTypeVersionId,
        JsonNode nodeMap,
        String reason,
        String confirmation,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "workflow_manage");
        return nodeWorkflowService.upgrade(
            user, space, item, targetTypeVersionId,
            nodeMap, reason, confirmation, expectedWorkItemVersion,
            expectedInstanceVersion, requestId
        );
    }

    public NodeCompensationRun resumeNodeCompensation(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID runId,
        String reason,
        String confirmation
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "workflow_manage");
        return nodeWorkflowService.resumeCompensation(
            user, space, item,
            runId, reason, confirmation
        );
    }

    public NodeCommandResult executeNodeTask(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
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
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "transition");
        return nodeWorkflowService.taskCommand(
            user, space, item, taskId, operation,
            decision, targetAssigneeId, fieldPatch, artifacts,
            expectedWorkItemVersion, expectedInstanceVersion,
            requestId
        );
    }

    public List<WorkflowHistoryEntry> workflowHistory(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        Long beforeSequence,
        int limit
    ) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "view");
        return stateFlowService.history(
            user, item, beforeSequence, limit
        );
    }

    public WorkflowCommandResult executeWorkflowAction(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String actionKey,
        String fromStateKey,
        long expectedVersion,
        JsonNode fieldPatch,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "transition");
        return stateFlowService.execute(
            user, space, item, actionKey,
            fromStateKey, expectedVersion, fieldPatch, requestId
        );
    }

    public WorkflowCommandResult correctWorkflowState(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String targetStateKey,
        long expectedVersion,
        String reason,
        String confirmation,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "workflow_manage");
        return stateFlowService.correct(
            user, space, item, targetStateKey,
            expectedVersion, reason, confirmation, requestId
        );
    }

    public WorkflowBindingCommandResult upgradeWorkflowBinding(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID targetTypeVersionId,
        String targetStateKey,
        long expectedVersion,
        String reason,
        String confirmation,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        WorkItem item = requireItem(user, spaceId, workItemId);
        requirePermission(user, space, item, configuration(user, item), "workflow_manage");
        return stateFlowService.upgradeBinding(
            user, space, item, targetTypeVersionId,
            targetStateKey, expectedVersion, reason, confirmation, requestId
        );
    }

    public StateBackfillBatch createWorkflowBackfill(
        CurrentUser user,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetStateKey,
        List<UUID> workItemIds,
        String reason,
        String confirmation,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        return stateBackfillService.createAndExecute(
            user, space, typeDefinitionId, targetTypeVersionId, targetStateKey,
            workItemIds, reason, confirmation, requestId
        );
    }

    public StateBackfillBatch resumeWorkflowBackfill(
        CurrentUser user,
        UUID spaceId,
        UUID batchId,
        String confirmation
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        return stateBackfillService.resume(user, space, batchId, confirmation);
    }

    public StateBackfillVerification verifyWorkflowBackfill(
        CurrentUser user,
        UUID spaceId,
        UUID batchId
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        return stateBackfillService.verify(user, space, batchId);
    }

    public NodeBackfillBatch createNodeWorkflowBackfill(
        CurrentUser user,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetEntryNodeKey,
        List<UUID> workItemIds,
        String reason,
        String confirmation,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        return nodeBackfillService.createAndExecute(
            user, space, typeDefinitionId, targetTypeVersionId, targetEntryNodeKey,
            workItemIds, reason, confirmation, requestId
        );
    }

    public NodeBackfillBatch resumeNodeWorkflowBackfill(
        CurrentUser user,
        UUID spaceId,
        UUID batchId,
        String confirmation
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        return nodeBackfillService.resume(user, space, batchId, confirmation);
    }

    public NodeBackfillVerification verifyNodeWorkflowBackfill(
        CurrentUser user,
        UUID spaceId,
        UUID batchId
    ) {
        ProjectSpaceSummary space = requireWorkflowManager(user, spaceId);
        return nodeBackfillService.verify(user, space, batchId);
    }

    private RuntimeConfiguration configuration(CurrentUser user, WorkItem item) {
        RuntimeConfiguration configuration = snapshotAdapter.requireComplete(
            user.workspaceId(),
            item.spaceId(),
            item.typeDefinitionId(),
            item.typeVersionId()
        );
        if (!configuration.configHash().equals(item.configHash())) {
            throw failure("SNAPSHOT_INTEGRITY_FAILURE", "Work item configuration binding failed integrity validation");
        }
        return configuration;
    }

    private List<String> actions(
        CurrentUser user,
        ProjectSpaceSummary space,
        WorkItem item,
        RuntimeConfiguration configuration
    ) {
        List<String> actions = new ArrayList<>();
        for (String action : List.of("view", "edit", "archive", "restore")) {
            boolean lifecycleApplicable = switch (action) {
                case "edit", "archive" -> "active".equals(item.status());
                case "restore" -> "archived".equals(item.status());
                default -> true;
            };
            if (lifecycleApplicable && permissionDecisionService.decide(
                configuration,
                subjectContext(user, space, item),
                item.spaceId(),
                item.id(),
                action,
                evaluationContext(user, item, null, null, null)
            ).allowed()) {
                actions.add(action);
            }
        }
        return List.copyOf(actions);
    }

    private void requirePermission(
        CurrentUser user,
        ProjectSpaceSummary space,
        WorkItem item,
        RuntimeConfiguration configuration,
        String action
    ) {
        permissionDecisionService.require(permissionDecisionService.decide(
            configuration,
            subjectContext(user, space, item),
            space.id(),
            item == null ? null : item.id(),
            action,
            evaluationContext(user, item, null, null, null)
        ));
    }

    private EvaluationContext evaluationContext(
        CurrentUser user,
        WorkItem item,
        String fieldKey,
        String nodeKey,
        String relationKey
    ) {
        if (item == null) {
            return EvaluationContext.empty();
        }
        Set<String> roles = item.createdBy().equals(user.id()) ? Set.of("creator") : Set.of();
        Set<UUID> participants = item.createdBy().equals(user.id()) ? Set.of(user.id()) : Set.of();
        Map<String, String> values = new java.util.HashMap<>();
        item.fieldValues().fields().forEachRemaining(entry -> {
            if (entry.getValue().isValueNode()) {
                values.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return new EvaluationContext(
            item.id(),
            item.createdBy(),
            participants,
            roles,
            Map.copyOf(values),
            fieldKey,
            nodeKey,
            relationKey
        );
    }

    private JsonNode filterReadableFields(
        CurrentUser user,
        ProjectSpaceSummary space,
        WorkItem item,
        RuntimeConfiguration configuration,
        JsonNode projected
    ) {
        if (!projected.isObject()) {
            return projected;
        }
        ObjectNode safe = ((ObjectNode) projected).deepCopy();
        List<String> keys = new ArrayList<>();
        safe.fieldNames().forEachRemaining(keys::add);
        for (String fieldKey : keys) {
            if (!permissionDecisionService.decide(
                configuration,
                subjectContext(user, space, item),
                item.spaceId(),
                item.id(),
                "field_read",
                evaluationContext(user, item, fieldKey, null, null)
            ).allowed()) {
                safe.remove(fieldKey);
            }
        }
        return safe;
    }

    private void requireWritableFields(
        CurrentUser user,
        ProjectSpaceSummary space,
        WorkItem item,
        RuntimeConfiguration configuration,
        JsonNode requested
    ) {
        if (!requested.isObject()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        requested.fieldNames().forEachRemaining(keys::add);
        for (String fieldKey : keys) {
            if (!permissionDecisionService.decide(
                configuration,
                subjectContext(user, space, item),
                item.spaceId(),
                item.id(),
                "field_write",
                evaluationContext(user, item, fieldKey, null, null)
            ).allowed()) {
                throw failure("FORBIDDEN", "One or more requested fields are not writable");
            }
        }
    }

    private SubjectContext subjectContext(
        CurrentUser user,
        ProjectSpaceSummary space,
        WorkItem item
    ) {
        Set<String> enterpriseRoles = user.roles().stream()
            .map(role -> role.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> workItemRoles = new LinkedHashSet<>();
        if (item != null && item.createdBy().equals(user.id())) {
            workItemRoles.add("creator");
        }
        return new SubjectContext(
            user.workspaceId(),
            user.id(),
            item == null ? 0 : item.version(),
            enterpriseRoles,
            Set.of(space.currentUserRole()),
            Set.copyOf(workItemRoles),
            Set.of()
        );
    }

    private ProjectSpaceSummary requireMember(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project space is not available"));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    private ProjectSpaceSummary requireWritableMember(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        if ("guest".equals(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Guest project space members have read-only access");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
        return space;
    }

    private ProjectSpaceSummary requireWorkflowManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireWritableMember(user, spaceId);
        if (!space.canManage()) {
            throw failure("FORBIDDEN", "Only project space owners and admins may recover workflow state");
        }
        return space;
    }

    private WorkItem requireItem(CurrentUser user, UUID spaceId, UUID workItemId) {
        return repository.find(user.workspaceId(), spaceId, workItemId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
    }

    private CommandReceipt begin(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String operation,
        String requestId,
        String requestHash
    ) {
        repository.tryStartCommand(new CommandStart(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            workItemId,
            operation,
            requestId,
            requestHash,
            user.id()
        ));
        CommandReceipt receipt = repository.findCommand(user.workspaceId(), operation, requestId)
            .orElseThrow(() -> failure("IDEMPOTENCY_CONFLICT", "Work item command receipt is unavailable"));
        if (!receipt.spaceId().equals(spaceId)
            || (workItemId != null && receipt.workItemId() != null && !receipt.workItemId().equals(workItemId))
            || !receipt.requestHash().equals(requestHash)
            || !receipt.createdBy().equals(user.id())) {
            throw failure("IDEMPOTENCY_KEY_REUSED", "Request id was already used with different input");
        }
        if (!"pending".equals(receipt.status()) && !"completed".equals(receipt.status())) {
            throw failure("IDEMPOTENCY_CONFLICT", "Work item command receipt has an invalid state");
        }
        return receipt;
    }

    private void complete(CommandReceipt receipt, WorkItemView result) {
        complete(receipt, result.item().id(), result);
    }

    private WorkItemView replay(CommandReceipt receipt) {
        return replay(receipt, WorkItemView.class);
    }

    private void complete(CommandReceipt receipt, UUID workItemId, Object result) {
        repository.completeCommand(receipt.id(), workItemId, objectMapper.valueToTree(result));
    }

    private <T> T replay(CommandReceipt receipt, Class<T> resultType) {
        if (receipt.response() == null) {
            throw failure("IDEMPOTENCY_CONFLICT", "Work item command is still in progress");
        }
        try {
            return objectMapper.treeToValue(receipt.response(), resultType);
        } catch (JsonProcessingException exception) {
            throw failure("IDEMPOTENCY_CONFLICT", "Stored work item response is invalid", exception);
        }
    }

    private String requestHash(CurrentUser user, String operation, Map<String, Object> payload) {
        return canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", user.id().toString(),
            "operation", operation,
            "payload", payload
        )));
    }

    private String requestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id must contain 1 to 120 characters");
        }
        return normalized;
    }

    private String title(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw failure("INVALID_WORK_ITEM_TITLE", "Work item title must contain 1 to 500 characters");
        }
        return normalized;
    }

    private String displayKey(String typeKey, long number) {
        String prefix = typeKey.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        if (prefix.length() > 64) {
            prefix = prefix.substring(0, 64);
        }
        return prefix + "-" + number;
    }

    private String participantRole(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
            case "owner", "assignee", "collaborator", "watcher" ->
                value.trim().toLowerCase(Locale.ROOT);
            default -> throw failure("INVALID_PARTICIPANT_ROLE", "Invalid work item participant role");
        };
    }

    private boolean responsible(String role) {
        return "owner".equals(role) || "assignee".equals(role);
    }

    private JsonNode activityPayload(long version, String status) {
        return objectMapper.valueToTree(Map.of("version", version, "status", status));
    }

    private List<WorkItemParticipant> hydrateParticipants(
        CurrentUser user,
        List<WorkItemParticipant> participants
    ) {
        LinkedHashSet<SubjectRef> refs = new LinkedHashSet<>();
        participants.forEach(value -> refs.add(new SubjectRef(SubjectType.MEMBER, value.userId())));
        Map<SubjectRef, SubjectSnapshot> snapshots = subjectDirectory.resolve(
            user.workspaceId(), user.id(), refs
        );
        return participants.stream().map(value -> {
            SubjectSnapshot snapshot = snapshots.get(new SubjectRef(SubjectType.MEMBER, value.userId()));
            return new WorkItemParticipant(
                value.id(),
                value.userId(),
                snapshot == null ? null : snapshot.displayName(),
                value.role(),
                value.createdBy(),
                value.createdAt(),
                value.updatedBy(),
                value.updatedAt()
            );
        }).toList();
    }

    private List<com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivity> hydrateActivities(
        CurrentUser user,
        List<com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivity> activities
    ) {
        LinkedHashSet<SubjectRef> refs = new LinkedHashSet<>();
        activities.forEach(value -> refs.add(new SubjectRef(SubjectType.MEMBER, value.actorId())));
        Map<SubjectRef, SubjectSnapshot> snapshots = subjectDirectory.resolve(
            user.workspaceId(), user.id(), refs
        );
        return activities.stream().map(value -> {
            SubjectSnapshot snapshot = snapshots.get(new SubjectRef(SubjectType.MEMBER, value.actorId()));
            return new com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivity(
                value.id(),
                value.sequence(),
                value.type(),
                value.actorId(),
                snapshot == null ? null : snapshot.displayName(),
                value.payload(),
                value.occurredAt()
            );
        }).toList();
    }

    private List<WorkItemComment> hydrateComments(
        CurrentUser user,
        List<WorkItemComment> comments
    ) {
        LinkedHashSet<SubjectRef> refs = new LinkedHashSet<>();
        comments.forEach(value -> refs.add(new SubjectRef(SubjectType.MEMBER, value.authorId())));
        Map<SubjectRef, SubjectSnapshot> snapshots = subjectDirectory.resolve(
            user.workspaceId(), user.id(), refs
        );
        return comments.stream().map(value -> {
            SubjectSnapshot snapshot = snapshots.get(new SubjectRef(SubjectType.MEMBER, value.authorId()));
            return new WorkItemComment(
                value.id(),
                value.authorId(),
                snapshot == null ? null : snapshot.displayName(),
                value.content(),
                value.version(),
                value.createdAt(),
                value.updatedAt()
            );
        }).toList();
    }

    private List<WorkItemAttachment> hydrateAttachments(
        CurrentUser user,
        List<AttachmentLink> attachments
    ) {
        LinkedHashSet<SubjectRef> refs = new LinkedHashSet<>();
        attachments.forEach(value -> refs.add(new SubjectRef(SubjectType.MEMBER, value.createdBy())));
        Map<SubjectRef, SubjectSnapshot> snapshots = subjectDirectory.resolve(
            user.workspaceId(), user.id(), refs
        );
        Map<UUID, FileAccess.FileResult> files = fileAccess.resolve(
            user.workspaceId(),
            user.id(),
            attachments.stream().map(AttachmentLink::fileId).toList()
        );
        return attachments.stream().filter(value -> {
            FileAccess.FileResult file = files.get(value.fileId());
            return file != null && file.availability() == FileAccess.Availability.AVAILABLE;
        }).map(value -> {
            SubjectSnapshot snapshot = snapshots.get(new SubjectRef(SubjectType.MEMBER, value.createdBy()));
            FileAccess.FileMetadata file = files.get(value.fileId()).metadata();
            return new WorkItemAttachment(
                value.id(),
                value.fileId(),
                file.originalName(),
                file.mimeType(),
                file.size(),
                value.createdBy(),
                snapshot == null ? null : snapshot.displayName(),
                value.createdAt()
            );
        }).toList();
    }

    private void register(WorkItem item, UUID actorId) {
        objectCommands.upsertLink(
            item.workspaceId(),
            "work_item",
            item.id(),
            "/project-spaces/" + item.spaceId() + "/work-items/" + item.id(),
            "colla://work-item/" + item.id(),
            item.title(),
            actorId
        );
    }

    private void appendEvent(CurrentUser user, WorkItem item, String mutation, String requestId) {
        WorkItemChangedEvent event = new WorkItemChangedEvent(
            item.spaceId(),
            item.typeDefinitionId(),
            item.typeVersionId(),
            item.configHash(),
            item.version(),
            item.status(),
            mutation
        );
        outbox.append(
            user.workspaceId(),
            WorkItemChangedEvent.EVENT_TYPE,
            WorkItemChangedEvent.AGGREGATE_TYPE,
            item.id(),
            user.id(),
            event.payload(),
            "work_item:" + item.id() + ":" + mutation + ":" + requestId
        );
    }
}
