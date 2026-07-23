package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeDescription;
import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeFieldKey;
import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeName;
import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeSortOrder;

import com.colla.platform.modules.project.application.WorkItemFieldConfigCanonicalizer.CanonicalFieldConfig;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.CreateFieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldStatus;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository.NewFieldDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemFieldDefinitionService {
    private final WorkItemFieldRepository repository;
    private final WorkItemTypeDefinitionService typeService;
    private final WorkItemFieldConfigCanonicalizer canonicalizer;
    private final List<WorkItemFieldReferenceGuard> referenceGuards;

    public WorkItemFieldDefinitionService(
        WorkItemFieldRepository repository,
        WorkItemTypeDefinitionService typeService,
        WorkItemFieldConfigCanonicalizer canonicalizer,
        List<WorkItemFieldReferenceGuard> referenceGuards
    ) {
        this.repository = repository;
        this.typeService = typeService;
        this.canonicalizer = canonicalizer;
        this.referenceGuards = List.copyOf(referenceGuards);
    }

    public List<FieldDefinition> list(UUID workspaceId, UUID spaceId, UUID typeId, String status) {
        requireType(workspaceId, spaceId, typeId);
        String normalized = status == null || status.isBlank() ? "" : FieldStatus.parse(status).name();
        return repository.listByType(workspaceId, spaceId, typeId, normalized);
    }

    public FieldDefinition get(UUID workspaceId, UUID spaceId, UUID typeId, UUID fieldId) {
        requireType(workspaceId, spaceId, typeId);
        return repository.findById(workspaceId, spaceId, typeId, fieldId)
            .orElseThrow(() -> failure("FIELD_NOT_FOUND", "Work item field not found"));
    }

    @Transactional
    public FieldDefinition create(CreateFieldDefinition command) {
        requireIds(command);
        WorkItemTypeDefinition type = requireConfigurableType(command.workspaceId(), command.spaceId(), command.typeDefinitionId());
        String fieldKey = normalizeFieldKey(command.fieldKey());
        String name = normalizeName(command.name());
        String description = normalizeDescription(command.description());
        String fieldType = canonicalType(command.fieldType());
        CanonicalFieldConfig canonical = canonicalizer.canonicalize(fieldType, command.config());
        UUID fieldId = UUID.randomUUID();
        try {
            repository.insert(new NewFieldDefinition(
                fieldId,
                command.workspaceId(),
                command.spaceId(),
                type.id(),
                fieldKey,
                name,
                description,
                fieldType,
                canonical.config(),
                canonical.hash(),
                normalizeSortOrder(command.sortOrder()),
                FieldStatus.active.name(),
                command.system(),
                command.actorId()
            ));
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uk_project_work_item_fields_type_key")) {
                throw new WorkItemFieldException("FIELD_KEY_CONFLICT", "Field key already exists for this work item type", exception);
            }
            throw new WorkItemFieldException("FIELD_PERSISTENCE_CONFLICT", "Work item field conflicts with current data", exception);
        }
        return get(command.workspaceId(), command.spaceId(), type.id(), fieldId);
    }

    @Transactional
    public FieldDefinition update(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String name,
        String description,
        JsonNode config,
        List<ConfigureFieldOption> options,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        requireConfigurableType(workspaceId, spaceId, typeId);
        FieldDefinition before = get(workspaceId, spaceId, typeId, fieldId);
        requireEditable(before);
        CanonicalFieldConfig canonical = canonicalizer.canonicalize(before.fieldType(), config, options);
        int updated = repository.update(
            workspaceId,
            spaceId,
            typeId,
            fieldId,
            normalizeName(name),
            normalizeDescription(description),
            canonical.config(),
            canonical.hash(),
            actorId,
            expectedAggregateVersion
        );
        requireUpdated(updated);
        return get(workspaceId, spaceId, typeId, fieldId);
    }

    @Transactional
    public FieldDefinition transition(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String targetStatus,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        requireConfigurableType(workspaceId, spaceId, typeId);
        FieldDefinition before = get(workspaceId, spaceId, typeId, fieldId);
        FieldStatus target = FieldStatus.parse(targetStatus);
        FieldStatus current = before.parsedStatus();
        if (before.system() && target == FieldStatus.retired) {
            throw failure("SYSTEM_FIELD_PROTECTED", "System fields cannot be retired");
        }
        if (!current.canTransitionTo(target)) {
            throw failure("INVALID_FIELD_LIFECYCLE_TRANSITION", "Field cannot transition from " + current + " to " + target);
        }
        if (current == target) {
            return before;
        }
        referenceGuards.forEach(guard -> guard.assertTransitionAllowed(workspaceId, spaceId, typeId, fieldId, target.name()));
        requireUpdated(repository.transitionStatus(
            workspaceId, spaceId, typeId, fieldId, target.name(), actorId, expectedAggregateVersion
        ));
        return get(workspaceId, spaceId, typeId, fieldId);
    }

    @Transactional
    public FieldDefinition reorder(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        int sortOrder,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        requireConfigurableType(workspaceId, spaceId, typeId);
        FieldDefinition before = get(workspaceId, spaceId, typeId, fieldId);
        if (before.parsedStatus() == FieldStatus.retired) {
            throw failure("RETIRED_FIELD", "Retired fields cannot be reordered");
        }
        requireUpdated(repository.updateSortOrder(
            workspaceId,
            spaceId,
            typeId,
            fieldId,
            normalizeSortOrder(sortOrder),
            actorId,
            expectedAggregateVersion
        ));
        return get(workspaceId, spaceId, typeId, fieldId);
    }

    private WorkItemTypeDefinition requireType(UUID workspaceId, UUID spaceId, UUID typeId) {
        try {
            return typeService.get(workspaceId, spaceId, typeId);
        } catch (WorkItemTypeException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space, type, or field is not available");
        }
    }

    private WorkItemTypeDefinition requireConfigurableType(UUID workspaceId, UUID spaceId, UUID typeId) {
        WorkItemTypeDefinition type = requireType(workspaceId, spaceId, typeId);
        if ("retired".equals(type.status())) {
            throw failure("RETIRED_TYPE", "Retired work item types cannot configure fields");
        }
        return type;
    }

    private void requireEditable(FieldDefinition field) {
        if (field.system()) {
            throw failure("SYSTEM_FIELD_PROTECTED", "System field configuration is protected");
        }
        if (field.parsedStatus() == FieldStatus.retired) {
            throw failure("RETIRED_FIELD", "Retired fields cannot be edited");
        }
    }

    private void requireIds(CreateFieldDefinition command) {
        if (command == null || command.workspaceId() == null || command.spaceId() == null
            || command.typeDefinitionId() == null || command.actorId() == null) {
            throw failure("INVALID_FIELD_SCOPE", "Workspace, project space, type, and actor are required");
        }
    }

    private String canonicalType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        canonicalizer.canonicalize(normalized, null);
        return normalized;
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw failure("FIELD_VERSION_CONFLICT", "Work item field was changed by another request");
        }
    }

    private boolean containsConstraint(Throwable failure, String constraintName) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private WorkItemFieldException failure(String code, String message) {
        return new WorkItemFieldException(code, message);
    }
}
