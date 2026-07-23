package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.infrastructure.AuditRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.project.application.WorkItemFieldConfigurationModels.Configuration;
import com.colla.platform.modules.project.application.WorkItemFieldConfigurationModels.ConfiguredField;
import com.colla.platform.modules.project.application.WorkItemFieldConfigurationModels.ReorderField;
import com.colla.platform.modules.project.application.WorkItemFieldTypeRegistry.FieldTypeDescriptor;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.CreateFieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldCommandRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldCommandRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldCommandRepository.CommandStart;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldOptionRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
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
public class WorkItemFieldConfigurationService {
    private final WorkItemFieldDefinitionService definitionService;
    private final WorkItemTypeDefinitionService typeService;
    private final ProjectSpaceRepository spaceRepository;
    private final WorkItemFieldCommandRepository commandRepository;
    private final WorkItemFieldActionPolicy actionPolicy;
    private final WorkItemFieldTypeRegistry typeRegistry;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final WorkItemFieldConfigCanonicalizer fieldConfigCanonicalizer;
    private final WorkItemFieldComplexReferenceValidator complexReferenceValidator;
    private final WorkItemFieldOptionRepository optionRepository;
    private final DomainEventRepository eventRepository;
    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public WorkItemFieldConfigurationService(
        WorkItemFieldDefinitionService definitionService,
        WorkItemTypeDefinitionService typeService,
        ProjectSpaceRepository spaceRepository,
        WorkItemFieldCommandRepository commandRepository,
        WorkItemFieldActionPolicy actionPolicy,
        WorkItemFieldTypeRegistry typeRegistry,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        WorkItemFieldConfigCanonicalizer fieldConfigCanonicalizer,
        WorkItemFieldComplexReferenceValidator complexReferenceValidator,
        WorkItemFieldOptionRepository optionRepository,
        DomainEventRepository eventRepository,
        AuditRepository auditRepository,
        ObjectMapper objectMapper
    ) {
        this.definitionService = definitionService;
        this.typeService = typeService;
        this.spaceRepository = spaceRepository;
        this.commandRepository = commandRepository;
        this.actionPolicy = actionPolicy;
        this.typeRegistry = typeRegistry;
        this.canonicalizer = canonicalizer;
        this.fieldConfigCanonicalizer = fieldConfigCanonicalizer;
        this.complexReferenceValidator = complexReferenceValidator;
        this.optionRepository = optionRepository;
        this.eventRepository = eventRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    public List<FieldTypeDescriptor> fieldTypes(CurrentUser user, UUID spaceId) {
        requireManager(user, spaceId);
        return typeRegistry.list();
    }

    public Configuration configuration(CurrentUser user, UUID spaceId, UUID typeId, String status) {
        Context context = requireContext(user, spaceId, typeId, false);
        return configuration(context, definitionService.list(user.workspaceId(), spaceId, typeId, status));
    }

    public ConfiguredField detail(CurrentUser user, UUID spaceId, UUID typeId, UUID fieldId) {
        Context context = requireContext(user, spaceId, typeId, false);
        return configured(context, definitionService.get(user.workspaceId(), spaceId, typeId, fieldId));
    }

    @Transactional
    public ConfiguredField create(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String fieldKey,
        String name,
        String description,
        String fieldType,
        JsonNode config,
        int sortOrder,
        String requestId
    ) {
        Context context = requireContext(user, spaceId, typeId, true);
        String canonicalFieldType = typeRegistry.require(fieldType).key();
        var canonicalConfig = fieldConfigCanonicalizer.canonicalize(canonicalFieldType, config);
        complexReferenceValidator.validate(user, spaceId, typeId, canonicalFieldType, canonicalConfig.config());
        Command command = begin(user, spaceId, typeId, "create", payload(
            "fieldKey", fieldKey,
            "name", name,
            "description", description,
            "fieldType", canonicalFieldType,
            "config", canonicalConfig.config(),
            "sortOrder", sortOrder
        ), requestId);
        if (command.replay()) {
            return configured(context, replayField(user, spaceId, typeId, command.receipt()));
        }
        FieldDefinition created = definitionService.create(new CreateFieldDefinition(
            user.workspaceId(), spaceId, typeId, user.id(), fieldKey, name, description,
            canonicalFieldType, canonicalConfig.config(), sortOrder, false
        ));
        recordChange(user, "created", null, created, command.requestId());
        commandRepository.complete(command.receipt().id(), created.id());
        return configured(context, created);
    }

    @Transactional
    public ConfiguredField update(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String name,
        String description,
        JsonNode config,
        long expectedAggregateVersion,
        String requestId
    ) {
        Context context = requireContext(user, spaceId, typeId, true);
        FieldDefinition before = definitionService.get(user.workspaceId(), spaceId, typeId, fieldId);
        List<ConfigureFieldOption> options = configurableOptions(user.workspaceId(), spaceId, typeId, fieldId);
        JsonNode nextConfig = config == null || config.isNull() ? before.config() : config;
        var canonicalConfig = fieldConfigCanonicalizer.canonicalize(before.fieldType(), nextConfig, options);
        complexReferenceValidator.validate(
            user, spaceId, typeId, before.fieldType(), canonicalConfig.config()
        );
        Command command = begin(user, spaceId, typeId, "update", payload(
            "fieldId", fieldId,
            "name", name,
            "description", description,
            "config", canonicalConfig.config(),
            "aggregateVersion", expectedAggregateVersion
        ), requestId);
        if (command.replay()) {
            return configured(context, replayField(user, spaceId, typeId, command.receipt()));
        }
        FieldDefinition after = definitionService.update(
            user.workspaceId(), spaceId, typeId, fieldId, name, description, canonicalConfig.config(),
            options, user.id(), expectedAggregateVersion
        );
        recordChange(user, "updated", before, after, command.requestId());
        commandRepository.complete(command.receipt().id(), after.id());
        return configured(context, after);
    }

    @Transactional
    public ConfiguredField configure(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        JsonNode config,
        List<ConfigureFieldOption> options,
        long expectedAggregateVersion,
        String requestId
    ) {
        Context context = requireContext(user, spaceId, typeId, true);
        FieldDefinition before = definitionService.get(user.workspaceId(), spaceId, typeId, fieldId);
        if (before.system()) {
            throw failure("SYSTEM_FIELD_PROTECTED", "System field configuration is protected");
        }
        if ("retired".equals(before.status())) {
            throw failure("RETIRED_FIELD", "Retired fields cannot be configured");
        }
        List<FieldOption> existing = optionRepository.listByField(user.workspaceId(), spaceId, typeId, fieldId);
        List<ConfigureFieldOption> normalizedOptions = normalizeOptions(before.fieldType(), options, existing);
        var canonical = fieldConfigCanonicalizer.canonicalize(before.fieldType(), config, normalizedOptions);
        complexReferenceValidator.validate(user, spaceId, typeId, before.fieldType(), canonical.config());
        Command command = begin(user, spaceId, typeId, "configure", payload(
            "fieldId", fieldId,
            "config", canonical.config(),
            "options", normalizedOptions,
            "aggregateVersion", expectedAggregateVersion
        ), requestId);
        if (command.replay()) {
            return configured(context, replayField(user, spaceId, typeId, command.receipt()));
        }

        FieldDefinition after = definitionService.update(
            user.workspaceId(), spaceId, typeId, fieldId, before.name(), before.description(),
            canonical.config(), normalizedOptions, user.id(), expectedAggregateVersion
        );
        Map<String, FieldOption> existingByKey = new LinkedHashMap<>();
        existing.forEach(option -> existingByKey.put(option.optionKey(), option));
        for (ConfigureFieldOption option : normalizedOptions) {
            if (existingByKey.containsKey(option.optionKey())) {
                optionRepository.update(
                    user.workspaceId(), spaceId, typeId, fieldId, option.optionKey(), option, user.id()
                );
            } else {
                optionRepository.insert(
                    UUID.randomUUID(), user.workspaceId(), spaceId, typeId, fieldId, option, user.id()
                );
            }
        }
        Map<String, Object> optionDiff = optionDiff(existing, normalizedOptions);
        recordChange(user, "configured", before, after, command.requestId(), Map.of("optionDiff", optionDiff));
        commandRepository.complete(command.receipt().id(), after.id());
        return configured(context, after);
    }

    @Transactional
    public Configuration reorder(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        List<ReorderField> items,
        String requestId
    ) {
        Context context = requireContext(user, spaceId, typeId, true);
        validateReorder(items);
        Command command = begin(user, spaceId, typeId, "reorder", Map.of("items", items), requestId);
        if (command.replay()) {
            return configuration(context, definitionService.list(user.workspaceId(), spaceId, typeId, null));
        }
        List<FieldDefinition> before = new ArrayList<>();
        for (ReorderField item : items) {
            before.add(definitionService.get(user.workspaceId(), spaceId, typeId, item.fieldId()));
        }
        if (before.stream().map(FieldDefinition::status).distinct().count() > 1
            || before.stream().anyMatch(field -> "retired".equals(field.status()))) {
            throw failure("INVALID_FIELD_REORDER", "Fields must be non-retired and share one status to be reordered together");
        }
        List<FieldDefinition> after = new ArrayList<>();
        for (ReorderField item : items) {
            after.add(definitionService.reorder(
                user.workspaceId(), spaceId, typeId, item.fieldId(), item.sortOrder(),
                user.id(), item.aggregateVersion()
            ));
        }
        for (int index = 0; index < after.size(); index++) {
            recordChange(user, "reordered", before.get(index), after.get(index), command.requestId());
        }
        commandRepository.complete(command.receipt().id(), null);
        return configuration(context, definitionService.list(user.workspaceId(), spaceId, typeId, null));
    }

    @Transactional
    public ConfiguredField transition(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String targetStatus,
        long expectedAggregateVersion,
        String requestId
    ) {
        Context context = requireContext(user, spaceId, typeId, true);
        FieldDefinition before = definitionService.get(user.workspaceId(), spaceId, typeId, fieldId);
        String operation = switch (targetStatus) {
            case "active" -> "restore";
            case "disabled" -> "disable";
            case "retired" -> "retire";
            default -> throw failure("INVALID_FIELD_STATUS", "Invalid work item field status");
        };
        Command command = begin(user, spaceId, typeId, operation, payload(
            "fieldId", fieldId,
            "targetStatus", targetStatus,
            "aggregateVersion", expectedAggregateVersion
        ), requestId);
        if (command.replay()) {
            return configured(context, replayField(user, spaceId, typeId, command.receipt()));
        }
        FieldDefinition after = definitionService.transition(
            user.workspaceId(), spaceId, typeId, fieldId, targetStatus, user.id(), expectedAggregateVersion
        );
        if (after.aggregateVersion() != before.aggregateVersion()) {
            recordChange(user, operation + "d", before, after, command.requestId());
        }
        commandRepository.complete(command.receipt().id(), after.id());
        return configured(context, after);
    }

    private Configuration configuration(Context context, List<FieldDefinition> fields) {
        return new Configuration(
            context.space().id(),
            context.type().id(),
            context.space().status(),
            actionPolicy.collectionActions(
                context.space().currentUserRole(), context.space().status(), context.type().status()
            ),
            fields.stream().map(field -> configured(context, field)).toList()
        );
    }

    private ConfiguredField configured(Context context, FieldDefinition field) {
        return new ConfiguredField(field, optionRepository.listByField(
            field.workspaceId(), field.spaceId(), field.typeDefinitionId(), field.id()
        ), actionPolicy.fieldActions(
            context.space().currentUserRole(), context.space().status(), context.type().status(), field
        ));
    }

    private List<ConfigureFieldOption> configurableOptions(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId
    ) {
        return optionRepository.listByField(workspaceId, spaceId, typeId, fieldId).stream()
            .map(option -> new ConfigureFieldOption(
                option.optionKey(), option.name(), option.color(), option.sortOrder(), option.status()
            ))
            .toList();
    }

    private List<ConfigureFieldOption> normalizeOptions(
        String fieldType,
        List<ConfigureFieldOption> requested,
        List<FieldOption> existing
    ) {
        List<ConfigureFieldOption> values = requested == null ? List.of() : requested;
        if (!typeRegistry.require(fieldType).supportsOptions() && !values.isEmpty()) {
            throw failure("INVALID_FIELD_OPTION", "Only select fields can define options");
        }
        if (values.size() > 200) {
            throw failure("INVALID_FIELD_OPTION", "A field can contain at most 200 options");
        }
        List<ConfigureFieldOption> normalized = values.stream()
            .map(com.colla.platform.modules.project.domain.WorkItemFieldOptionModels::normalize)
            .sorted(java.util.Comparator.comparingInt(ConfigureFieldOption::sortOrder)
                .thenComparing(ConfigureFieldOption::optionKey))
            .toList();
        Set<String> keys = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (ConfigureFieldOption option : normalized) {
            if (!keys.add(option.optionKey()) || !orders.add(option.sortOrder())) {
                throw failure("INVALID_FIELD_OPTION", "Option keys and sort orders must be unique within a field");
            }
        }
        for (FieldOption option : existing) {
            if (!keys.contains(option.optionKey())) {
                throw failure("INVALID_FIELD_OPTION", "Existing option keys cannot be removed; disable the option instead");
            }
        }
        return normalized;
    }

    private Map<String, Object> optionDiff(
        List<FieldOption> before,
        List<ConfigureFieldOption> after
    ) {
        Map<String, FieldOption> previous = new LinkedHashMap<>();
        before.forEach(option -> previous.put(option.optionKey(), option));
        int added = 0;
        int changed = 0;
        int disabled = 0;
        for (ConfigureFieldOption option : after) {
            FieldOption old = previous.get(option.optionKey());
            if (old == null) {
                added++;
            } else if (!old.name().equals(option.name()) || !old.color().equals(option.color())
                || old.sortOrder() != option.sortOrder() || !old.status().equals(option.status())) {
                changed++;
            }
            if ("disabled".equals(option.status()) && (old == null || !"disabled".equals(old.status()))) {
                disabled++;
            }
        }
        return Map.of("added", added, "changed", changed, "disabled", disabled, "total", after.size());
    }

    private ProjectSpaceSummary requireManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project space, type, or field is not available"));
        if (!space.isMember()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space, type, or field is not available");
        }
        if (!actionPolicy.isManager(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Project space owner or admin role required");
        }
        return space;
    }

    private Context requireContext(CurrentUser user, UUID spaceId, UUID typeId, boolean writable) {
        ProjectSpaceSummary space = requireManager(user, spaceId);
        WorkItemTypeDefinition type;
        try {
            type = typeService.get(user.workspaceId(), spaceId, typeId);
        } catch (WorkItemTypeException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space, type, or field is not available");
        }
        if (writable && !"active".equals(space.status())) {
            throw failure("SPACE_UNAVAILABLE", "Project space must be active for work item field configuration");
        }
        if (writable && "retired".equals(type.status())) {
            throw failure("RETIRED_TYPE", "Retired work item types cannot configure fields");
        }
        return new Context(space, type);
    }

    private Command begin(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
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
            "typeId", typeId.toString(),
            "request", request
        )));
        boolean started = commandRepository.tryStart(new CommandStart(
            UUID.randomUUID(), user.workspaceId(), spaceId, typeId, normalizedRequestId,
            operation, requestHash, user.id()
        ));
        CommandReceipt receipt = commandRepository.find(user.workspaceId(), normalizedRequestId)
            .orElseThrow(() -> failure("FIELD_IDEMPOTENCY_CONFLICT", "Field command receipt is unavailable"));
        if (!receipt.spaceId().equals(spaceId) || !receipt.typeDefinitionId().equals(typeId)
            || !receipt.operation().equals(operation) || !receipt.requestHash().equals(requestHash)
            || !receipt.createdBy().equals(user.id())) {
            throw failure("FIELD_IDEMPOTENCY_CONFLICT", "Request id was already used with a different field command");
        }
        if (!started && !"completed".equals(receipt.status())) {
            throw failure("FIELD_IDEMPOTENCY_IN_PROGRESS", "The original field command is still in progress");
        }
        return new Command(!started, normalizedRequestId, receipt);
    }

    private FieldDefinition replayField(CurrentUser user, UUID spaceId, UUID typeId, CommandReceipt receipt) {
        if (receipt.responseFieldId() == null) {
            throw failure("FIELD_IDEMPOTENCY_CONFLICT", "Completed field command has no replayable response");
        }
        return definitionService.get(user.workspaceId(), spaceId, typeId, receipt.responseFieldId());
    }

    private void validateReorder(List<ReorderField> items) {
        if (items == null || items.isEmpty()) {
            throw failure("INVALID_FIELD_REORDER", "At least one field is required for reordering");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (ReorderField item : items) {
            if (item == null || item.fieldId() == null || item.sortOrder() < 0 || item.aggregateVersion() < 0) {
                throw failure("INVALID_FIELD_REORDER", "Reorder entries require a field, order, and aggregate version");
            }
            if (!ids.add(item.fieldId()) || !orders.add(item.sortOrder())) {
                throw failure("INVALID_FIELD_REORDER", "Reorder entries cannot contain duplicate fields or sort orders");
            }
        }
    }

    private void recordChange(
        CurrentUser user,
        String action,
        FieldDefinition before,
        FieldDefinition after,
        String requestId
    ) {
        recordChange(user, action, before, after, requestId, Map.of());
    }

    private void recordChange(
        CurrentUser user,
        String action,
        FieldDefinition before,
        FieldDefinition after,
        String requestId,
        Map<String, Object> extraMetadata
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "space_configuration");
        metadata.put("requestId", requestId);
        metadata.put("spaceId", after.spaceId().toString());
        metadata.put("typeDefinitionId", after.typeDefinitionId().toString());
        metadata.put("fieldKey", after.fieldKey());
        metadata.put("before", snapshot(before));
        metadata.put("after", snapshot(after));
        metadata.putAll(extraMetadata);
        var boundary = RequestBoundaryContext.current();
        metadata.put("sourceUi", boundary.sourceUi());
        metadata.put("apiSurface", boundary.apiSurface());
        metadata.put("client", boundary.client());
        if (boundary.requestPath() != null && !boundary.requestPath().isBlank()) {
            metadata.put("requestPath", boundary.requestPath());
        }
        auditRepository.append(
            user.workspaceId(), user.id(), "work_item_field." + action, "work_item_field", after.id(),
            null, null, metadata
        );
        eventRepository.append(
            user.workspaceId(),
            "work_item_field." + action,
            "work_item_field",
            after.id(),
            user.id(),
            metadata,
            eventKey(user.workspaceId(), requestId, action, after.id())
        );
    }

    private Map<String, Object> snapshot(FieldDefinition field) {
        if (field == null) {
            return Map.of();
        }
        return Map.of(
            "name", field.name(),
            "description", field.description(),
            "fieldType", field.fieldType(),
            "configHash", field.configHash(),
            "sortOrder", field.sortOrder(),
            "status", field.status(),
            "aggregateVersion", field.aggregateVersion()
        );
    }

    private String eventKey(UUID workspaceId, String requestId, String action, UUID fieldId) {
        UUID key = UUID.nameUUIDFromBytes(
            (workspaceId + ":" + requestId + ":" + action + ":" + fieldId).getBytes(StandardCharsets.UTF_8)
        );
        return "wif:" + key;
    }

    private Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1] == null ? "" : values[index + 1]);
        }
        return result;
    }

    private WorkItemFieldException failure(String code, String message) {
        return new WorkItemFieldException(code, message);
    }

    private record Context(ProjectSpaceSummary space, WorkItemTypeDefinition type) {
    }

    private record Command(boolean replay, String requestId, CommandReceipt receipt) {
    }
}
