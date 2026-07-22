package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemTypeModels.normalizeDescription;
import static com.colla.platform.modules.project.domain.WorkItemTypeModels.normalizeIcon;
import static com.colla.platform.modules.project.domain.WorkItemTypeModels.normalizeName;
import static com.colla.platform.modules.project.domain.WorkItemTypeModels.normalizeSortOrder;
import static com.colla.platform.modules.project.domain.WorkItemTypeModels.normalizeTypeKey;

import com.colla.platform.modules.project.application.WorkItemTypeConfigCanonicalizer.CanonicalConfig;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.CreateWorkItemType;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeStatus;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.NewDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.NewVersion;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemTypeDefinitionService {
    private final WorkItemTypeRepository repository;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final List<WorkItemTypeReferenceGuard> referenceGuards;

    public WorkItemTypeDefinitionService(
        WorkItemTypeRepository repository,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        List<WorkItemTypeReferenceGuard> referenceGuards
    ) {
        this.repository = repository;
        this.canonicalizer = canonicalizer;
        this.referenceGuards = List.copyOf(referenceGuards);
    }

    public List<WorkItemTypeDefinition> list(UUID workspaceId, UUID spaceId, String status) {
        String normalizedStatus = status == null || status.isBlank()
            ? ""
            : WorkItemTypeStatus.parse(status).name();
        return repository.listBySpace(workspaceId, spaceId, normalizedStatus);
    }

    public WorkItemTypeDefinition get(UUID workspaceId, UUID spaceId, UUID typeId) {
        return repository.findById(workspaceId, spaceId, typeId)
            .orElseThrow(() -> failure("TYPE_NOT_FOUND", "Work item type not found"));
    }

    public WorkItemTypeRepository.WorkItemTypeCounts counts(UUID workspaceId, UUID spaceId) {
        return repository.countByStatus(workspaceId, spaceId);
    }

    public Map<UUID, WorkItemTypeRepository.WorkItemTypeCounts> counts(UUID workspaceId, List<UUID> spaceIds) {
        return repository.countBySpaces(workspaceId, spaceIds);
    }

    @Transactional
    public WorkItemTypeDefinition create(CreateWorkItemType command) {
        requireIds(command);
        String typeKey = normalizeTypeKey(command.typeKey());
        String name = normalizeName(command.name());
        String icon = normalizeIcon(command.icon());
        String description = normalizeDescription(command.description());
        int sortOrder = normalizeSortOrder(command.sortOrder());
        String spaceStatus = repository.findSpaceStatus(command.workspaceId(), command.spaceId())
            .orElseThrow(() -> failure("SPACE_NOT_FOUND", "Project space not found"));
        if (!"active".equals(spaceStatus)) {
            throw failure("SPACE_UNAVAILABLE", "Work item types can only be created in an active project space");
        }
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        CanonicalConfig canonical = canonicalizer.initialPublishedSkeleton(typeKey, name, icon, description);
        try {
            repository.insertDefinition(new NewDefinition(
                typeId,
                command.workspaceId(),
                command.spaceId(),
                typeKey,
                name,
                icon,
                description,
                sortOrder,
                WorkItemTypeStatus.active.name(),
                command.system(),
                versionId,
                command.actorId()
            ));
            repository.insertVersion(new NewVersion(
                versionId,
                command.workspaceId(),
                command.spaceId(),
                typeId,
                1,
                canonical.hash(),
                "published",
                canonical.config(),
                command.actorId()
            ));
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uk_project_work_item_types_space_key")) {
                throw new WorkItemTypeException("TYPE_KEY_CONFLICT", "Work item type key already exists in this space", exception);
            }
            throw new WorkItemTypeException("PERSISTENCE_CONFLICT", "Work item type creation conflicts with current data", exception);
        }
        return get(command.workspaceId(), command.spaceId(), typeId);
    }

    @Transactional
    public WorkItemTypeDefinition transition(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String targetStatus,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        WorkItemTypeDefinition before = get(workspaceId, spaceId, typeId);
        requireActiveSpace(workspaceId, spaceId);
        WorkItemTypeStatus target = WorkItemTypeStatus.parse(targetStatus);
        WorkItemTypeStatus current = before.parsedStatus();
        if (before.system() && target == WorkItemTypeStatus.retired) {
            throw failure("SYSTEM_TYPE_PROTECTED", "System work item types cannot be retired");
        }
        if (!current.canTransitionTo(target)) {
            throw failure("INVALID_LIFECYCLE_TRANSITION", "Work item type cannot transition from " + current + " to " + target);
        }
        if (current == target) {
            return before;
        }
        referenceGuards.forEach(guard -> guard.assertTransitionAllowed(workspaceId, spaceId, typeId, target.name()));
        int updated = repository.transitionStatus(
            workspaceId,
            spaceId,
            typeId,
            target.name(),
            actorId,
            expectedAggregateVersion
        );
        if (updated != 1) {
            throw failure("VERSION_CONFLICT", "Work item type was changed by another request");
        }
        return get(workspaceId, spaceId, typeId);
    }

    @Transactional
    public WorkItemTypeDefinition updateDisplay(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String name,
        String icon,
        String description,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        WorkItemTypeDefinition before = get(workspaceId, spaceId, typeId);
        requireActiveSpace(workspaceId, spaceId);
        if (before.system()) {
            throw failure("SYSTEM_TYPE_PROTECTED", "System work item type display is protected");
        }
        if (before.parsedStatus() == WorkItemTypeStatus.retired) {
            throw failure("RETIRED_TYPE", "Retired work item types cannot be edited");
        }
        int updated = repository.updateDisplay(
            workspaceId,
            spaceId,
            typeId,
            normalizeName(name),
            normalizeIcon(icon),
            normalizeDescription(description),
            actorId,
            expectedAggregateVersion
        );
        if (updated != 1) {
            throw failure("VERSION_CONFLICT", "Work item type was changed by another request");
        }
        return get(workspaceId, spaceId, typeId);
    }

    @Transactional
    public WorkItemTypeDefinition reorder(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        int sortOrder,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        WorkItemTypeDefinition before = get(workspaceId, spaceId, typeId);
        requireActiveSpace(workspaceId, spaceId);
        if (before.parsedStatus() == WorkItemTypeStatus.retired) {
            throw failure("RETIRED_TYPE", "Retired work item types cannot be reordered");
        }
        int updated = repository.updateSortOrder(
            workspaceId,
            spaceId,
            typeId,
            normalizeSortOrder(sortOrder),
            actorId,
            expectedAggregateVersion
        );
        if (updated != 1) {
            throw failure("VERSION_CONFLICT", "Work item type was changed by another request");
        }
        return get(workspaceId, spaceId, typeId);
    }

    private void requireIds(CreateWorkItemType command) {
        if (command == null || command.workspaceId() == null || command.spaceId() == null || command.actorId() == null) {
            throw failure("INVALID_SCOPE", "Workspace, project space, and actor are required");
        }
    }

    private void requireActiveSpace(UUID workspaceId, UUID spaceId) {
        String status = repository.findSpaceStatus(workspaceId, spaceId)
            .orElseThrow(() -> failure("SPACE_NOT_FOUND", "Project space not found"));
        if (!"active".equals(status)) {
            throw failure("SPACE_UNAVAILABLE", "Project space must be active for work item type configuration");
        }
    }

    private WorkItemTypeException failure(String code, String message) {
        return new WorkItemTypeException(code, message);
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
}
