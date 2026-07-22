package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.Configuration;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.ConfiguredType;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.GovernanceTypeCounts;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.ReorderType;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.UserTypeSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.CreateWorkItemType;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeCommandRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeCommandRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeCommandRepository.CommandStart;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemTypeConfigurationService {
    private final WorkItemTypeDefinitionService definitionService;
    private final ProjectSpaceRepository spaceRepository;
    private final WorkItemTypeCommandRepository commandRepository;
    private final WorkItemTypeActionPolicy actionPolicy;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final DomainEventRepository eventRepository;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public WorkItemTypeConfigurationService(
        WorkItemTypeDefinitionService definitionService,
        ProjectSpaceRepository spaceRepository,
        WorkItemTypeCommandRepository commandRepository,
        WorkItemTypeActionPolicy actionPolicy,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        DomainEventRepository eventRepository,
        AuditService auditService,
        PermissionService permissionService,
        ObjectMapper objectMapper
    ) {
        this.definitionService = definitionService;
        this.spaceRepository = spaceRepository;
        this.commandRepository = commandRepository;
        this.actionPolicy = actionPolicy;
        this.canonicalizer = canonicalizer;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public Configuration configuration(CurrentUser user, UUID spaceId, String status) {
        ProjectSpaceSummary space = requireManager(user, spaceId);
        return configuration(space, definitionService.list(user.workspaceId(), spaceId, status));
    }

    public ConfiguredType detail(CurrentUser user, UUID spaceId, UUID typeId) {
        ProjectSpaceSummary space = requireManager(user, spaceId);
        return configured(space, definitionService.get(user.workspaceId(), spaceId, typeId));
    }

    public List<UserTypeSummary> userSummaries(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        if (!"active".equals(space.status())) {
            return List.of();
        }
        return definitionService.list(user.workspaceId(), spaceId, "active").stream()
            .map(type -> new UserTypeSummary(type.typeKey(), type.name(), type.icon(), type.sortOrder()))
            .toList();
    }

    public GovernanceTypeCounts governanceCounts(CurrentUser user, UUID spaceId) {
        permissionService.requireManageProjects(user);
        requireExistingSpace(user, spaceId);
        return GovernanceTypeCounts.from(definitionService.counts(user.workspaceId(), spaceId));
    }

    public Map<UUID, GovernanceTypeCounts> governanceCounts(CurrentUser user, List<UUID> spaceIds) {
        permissionService.requireManageProjects(user);
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, GovernanceTypeCounts> result = new LinkedHashMap<>();
        definitionService.counts(user.workspaceId(), spaceIds).forEach(
            (spaceId, counts) -> result.put(spaceId, GovernanceTypeCounts.from(counts))
        );
        return Map.copyOf(result);
    }

    @Transactional
    public ConfiguredType create(
        CurrentUser user,
        UUID spaceId,
        String typeKey,
        String name,
        String icon,
        String description,
        int sortOrder,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableManager(user, spaceId);
        Map<String, Object> request = payload(
            "typeKey", typeKey,
            "name", name,
            "icon", icon,
            "description", description,
            "sortOrder", sortOrder
        );
        Command command = begin(user, spaceId, "create", request, requestId);
        if (command.replay()) {
            return configured(space, replayType(user, spaceId, command.receipt()));
        }
        WorkItemTypeDefinition created = definitionService.create(new CreateWorkItemType(
            user.workspaceId(), spaceId, user.id(), typeKey, name, icon, description, sortOrder, false
        ));
        recordChange(user, "created", null, created, command.requestId());
        commandRepository.complete(command.receipt().id(), created.id());
        return configured(space, created);
    }

    @Transactional
    public ConfiguredType update(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String name,
        String icon,
        String description,
        long expectedAggregateVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableManager(user, spaceId);
        WorkItemTypeDefinition before = definitionService.get(user.workspaceId(), spaceId, typeId);
        requireEditable(before);
        Command command = begin(user, spaceId, "update", payload(
            "typeId", typeId,
            "name", name,
            "icon", icon,
            "description", description,
            "aggregateVersion", expectedAggregateVersion
        ), requestId);
        if (command.replay()) {
            return configured(space, replayType(user, spaceId, command.receipt()));
        }
        WorkItemTypeDefinition after = definitionService.updateDisplay(
            user.workspaceId(), spaceId, typeId, name, icon, description, user.id(), expectedAggregateVersion
        );
        recordChange(user, "updated", before, after, command.requestId());
        commandRepository.complete(command.receipt().id(), after.id());
        return configured(space, after);
    }

    @Transactional
    public ConfiguredType copy(
        CurrentUser user,
        UUID spaceId,
        UUID sourceTypeId,
        String typeKey,
        String name,
        String icon,
        String description,
        Integer sortOrder,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableManager(user, spaceId);
        WorkItemTypeDefinition source = definitionService.get(user.workspaceId(), spaceId, sourceTypeId);
        String nextName = blank(name) ? source.name() + " Copy" : name;
        String nextIcon = icon == null ? source.icon() : icon;
        String nextDescription = description == null ? source.description() : description;
        int nextSortOrder = sortOrder == null ? source.sortOrder() + 10 : sortOrder;
        Command command = begin(user, spaceId, "copy", payload(
            "sourceTypeId", sourceTypeId,
            "typeKey", typeKey,
            "name", nextName,
            "icon", nextIcon,
            "description", nextDescription,
            "sortOrder", nextSortOrder
        ), requestId);
        if (command.replay()) {
            return configured(space, replayType(user, spaceId, command.receipt()));
        }
        WorkItemTypeDefinition copied = definitionService.create(new CreateWorkItemType(
            user.workspaceId(), spaceId, user.id(), typeKey, nextName, nextIcon, nextDescription, nextSortOrder, false
        ));
        recordChange(user, "copied", source, copied, command.requestId());
        commandRepository.complete(command.receipt().id(), copied.id());
        return configured(space, copied);
    }

    @Transactional
    public Configuration reorder(CurrentUser user, UUID spaceId, List<ReorderType> items, String requestId) {
        ProjectSpaceSummary space = requireWritableManager(user, spaceId);
        validateReorder(items);
        Command command = begin(user, spaceId, "reorder", Map.of("items", items), requestId);
        if (command.replay()) {
            return configuration(space, definitionService.list(user.workspaceId(), spaceId, null));
        }
        List<WorkItemTypeDefinition> before = new ArrayList<>();
        for (ReorderType item : items) {
            before.add(definitionService.get(user.workspaceId(), spaceId, item.typeId()));
        }
        if (before.stream().map(WorkItemTypeDefinition::status).distinct().count() > 1
            || before.stream().anyMatch(type -> "retired".equals(type.status()))) {
            throw failure("INVALID_REORDER", "Work item types must be non-retired and share one status to be reordered together");
        }
        List<WorkItemTypeDefinition> after = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ReorderType item = items.get(index);
            WorkItemTypeDefinition reordered = definitionService.reorder(
                user.workspaceId(), spaceId, item.typeId(), item.sortOrder(), user.id(), item.aggregateVersion()
            );
            after.add(reordered);
        }
        for (int index = 0; index < after.size(); index++) {
            WorkItemTypeDefinition reordered = after.get(index);
            recordChange(user, "reordered", before.get(index), reordered, command.requestId());
        }
        commandRepository.complete(command.receipt().id(), null);
        return configuration(space, definitionService.list(user.workspaceId(), spaceId, null));
    }

    @Transactional
    public ConfiguredType transition(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String targetStatus,
        long expectedAggregateVersion,
        String requestId
    ) {
        ProjectSpaceSummary space = requireWritableManager(user, spaceId);
        WorkItemTypeDefinition before = definitionService.get(user.workspaceId(), spaceId, typeId);
        if (before.system() && "retired".equals(targetStatus)) {
            throw failure("SYSTEM_TYPE_PROTECTED", "System work item types cannot be retired");
        }
        if ("retired".equals(before.status()) && !"retired".equals(targetStatus)) {
            throw failure("RETIRED_TYPE", "Retired work item types cannot be restored or changed");
        }
        String operation = switch (targetStatus) {
            case "active" -> "restore";
            case "disabled" -> "disable";
            case "retired" -> "retire";
            default -> throw failure("INVALID_STATUS", "Invalid work item type status");
        };
        Command command = begin(user, spaceId, operation, payload(
            "typeId", typeId,
            "targetStatus", targetStatus,
            "aggregateVersion", expectedAggregateVersion
        ), requestId);
        if (command.replay()) {
            return configured(space, replayType(user, spaceId, command.receipt()));
        }
        WorkItemTypeDefinition after = definitionService.transition(
            user.workspaceId(), spaceId, typeId, targetStatus, user.id(), expectedAggregateVersion
        );
        if (after.aggregateVersion() != before.aggregateVersion()) {
            recordChange(user, operation + "d", before, after, command.requestId());
        }
        commandRepository.complete(command.receipt().id(), after.id());
        return configured(space, after);
    }

    private Configuration configuration(ProjectSpaceSummary space, List<WorkItemTypeDefinition> types) {
        return new Configuration(
            space.id(),
            space.status(),
            actionPolicy.collectionActions(space.currentUserRole(), space.status()),
            types.stream().map(type -> configured(space, type)).toList()
        );
    }

    private ConfiguredType configured(ProjectSpaceSummary space, WorkItemTypeDefinition type) {
        return new ConfiguredType(type, actionPolicy.typeActions(space.currentUserRole(), space.status(), type));
    }

    private ProjectSpaceSummary requireManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireMember(user, spaceId);
        if (!actionPolicy.isManager(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Project space owner or admin role required");
        }
        return space;
    }

    private ProjectSpaceSummary requireWritableManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireManager(user, spaceId);
        if (!"active".equals(space.status())) {
            throw failure("SPACE_UNAVAILABLE", "Project space must be active for work item type configuration");
        }
        return space;
    }

    private ProjectSpaceSummary requireMember(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireExistingSpace(user, spaceId);
        if (!space.isMember()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space or work item type is not available");
        }
        return space;
    }

    private ProjectSpaceSummary requireExistingSpace(CurrentUser user, UUID spaceId) {
        return spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project space or work item type is not available"));
    }

    private void requireEditable(WorkItemTypeDefinition type) {
        if (type.system()) {
            throw failure("SYSTEM_TYPE_PROTECTED", "System work item type display is protected");
        }
        if ("retired".equals(type.status())) {
            throw failure("RETIRED_TYPE", "Retired work item types cannot be edited");
        }
    }

    private void validateReorder(List<ReorderType> items) {
        if (items == null || items.isEmpty()) {
            throw failure("INVALID_REORDER", "At least one work item type is required for reordering");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (ReorderType item : items) {
            if (item == null || item.typeId() == null || item.sortOrder() < 0 || item.aggregateVersion() < 0) {
                throw failure("INVALID_REORDER", "Reorder entries require a type, non-negative order, and aggregate version");
            }
            if (!ids.add(item.typeId()) || !orders.add(item.sortOrder())) {
                throw failure("INVALID_REORDER", "Reorder entries cannot contain duplicate types or sort orders");
            }
        }
    }

    private Command begin(
        CurrentUser user,
        UUID spaceId,
        String operation,
        Object request,
        String requestId
    ) {
        String normalizedRequestId = requestId == null ? "" : requestId.trim();
        if (normalizedRequestId.isEmpty() || normalizedRequestId.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id must contain 1 to 120 characters");
        }
        String requestHash = canonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", user.id().toString(),
            "operation", operation,
            "spaceId", spaceId.toString(),
            "request", request
        )));
        UUID commandId = UUID.randomUUID();
        boolean started = commandRepository.tryStart(new CommandStart(
            commandId, user.workspaceId(), spaceId, normalizedRequestId, operation, requestHash, user.id()
        ));
        CommandReceipt receipt = started
            ? commandRepository.find(user.workspaceId(), normalizedRequestId).orElseThrow()
            : commandRepository.find(user.workspaceId(), normalizedRequestId)
                .orElseThrow(() -> failure("IDEMPOTENCY_CONFLICT", "Idempotency receipt is unavailable"));
        if (!receipt.spaceId().equals(spaceId) || !receipt.operation().equals(operation)
            || !receipt.requestHash().equals(requestHash) || !receipt.createdBy().equals(user.id())) {
            throw failure("IDEMPOTENCY_CONFLICT", "Request id was already used with a different command");
        }
        if (!started && !"completed".equals(receipt.status())) {
            throw failure("IDEMPOTENCY_IN_PROGRESS", "The original command is still in progress");
        }
        return new Command(!started, normalizedRequestId, receipt);
    }

    private WorkItemTypeDefinition replayType(CurrentUser user, UUID spaceId, CommandReceipt receipt) {
        if (receipt.responseTypeId() == null) {
            throw failure("IDEMPOTENCY_CONFLICT", "The completed command has no replayable response");
        }
        return definitionService.get(user.workspaceId(), spaceId, receipt.responseTypeId());
    }

    private void recordChange(
        CurrentUser user,
        String action,
        WorkItemTypeDefinition before,
        WorkItemTypeDefinition after,
        String requestId
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "space_configuration");
        metadata.put("requestId", requestId);
        metadata.put("spaceId", after.spaceId().toString());
        metadata.put("typeKey", after.typeKey());
        metadata.put("before", snapshot(before));
        metadata.put("after", snapshot(after));
        auditService.log(user, "work_item_type." + action, "work_item_type", after.id(), metadata);
        eventRepository.append(
            user.workspaceId(),
            "work_item_type." + action,
            "work_item_type",
            after.id(),
            user.id(),
            metadata,
            eventKey(user.workspaceId(), requestId, action, after.id())
        );
    }

    private Map<String, Object> snapshot(WorkItemTypeDefinition type) {
        if (type == null) {
            return Map.of();
        }
        return Map.of(
            "name", type.name(),
            "icon", type.icon(),
            "description", type.description(),
            "sortOrder", type.sortOrder(),
            "status", type.status(),
            "aggregateVersion", type.aggregateVersion()
        );
    }

    private String eventKey(UUID workspaceId, String requestId, String action, UUID typeId) {
        UUID key = UUID.nameUUIDFromBytes(
            (workspaceId + ":" + requestId + ":" + action + ":" + typeId).getBytes(StandardCharsets.UTF_8)
        );
        return "wit:" + key;
    }

    private Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1] == null ? "" : values[index + 1]);
        }
        return result;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private WorkItemTypeException failure(String code, String message) {
        return new WorkItemTypeException(code, message);
    }

    private record Command(boolean replay, String requestId, CommandReceipt receipt) {
    }
}
