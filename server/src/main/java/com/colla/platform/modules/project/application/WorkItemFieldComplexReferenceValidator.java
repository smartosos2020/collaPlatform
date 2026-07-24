package com.colla.platform.modules.project.application;

import com.colla.platform.modules.file.contract.FileAccess;
import com.colla.platform.modules.identity.contract.SubjectDirectory;
import com.colla.platform.modules.identity.contract.SubjectDirectory.SubjectRef;
import com.colla.platform.modules.identity.contract.SubjectDirectory.SubjectState;
import com.colla.platform.modules.identity.contract.SubjectDirectory.SubjectType;
import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectRegistry;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkItemFieldComplexReferenceValidator {
    private final SubjectDirectory subjectDirectory;
    private final FileAccess fileAccess;
    private final PlatformObjectRegistry objectRegistry;
    private final WorkItemTypeRepository typeRepository;

    public WorkItemFieldComplexReferenceValidator(
        SubjectDirectory subjectDirectory,
        FileAccess fileAccess,
        PlatformObjectRegistry objectRegistry,
        WorkItemTypeRepository typeRepository
    ) {
        this.subjectDirectory = subjectDirectory;
        this.fileAccess = fileAccess;
        this.objectRegistry = objectRegistry;
        this.typeRepository = typeRepository;
    }

    public void validate(
        CurrentUser actor,
        UUID spaceId,
        UUID typeDefinitionId,
        String fieldType,
        JsonNode canonicalConfig
    ) {
        switch (fieldType) {
            case "user" -> validateUser(actor, canonicalConfig);
            case "attachment" -> validateAttachments(actor, canonicalConfig);
            case "work_item_reference" -> validateWorkItemTypes(
                actor.workspaceId(), spaceId, typeDefinitionId, canonicalConfig
            );
            default -> {
                // Scalar, select and temporal configurations contain no cross-module references.
            }
        }
    }

    private void validateUser(CurrentUser actor, JsonNode config) {
        JsonNode typeConfig = config.path("typeConfig");
        Set<UUID> permittedMembers = permittedMembers(actor.workspaceId(), actor.id(), typeConfig.path("selectionScope"));
        boolean unrestricted = typeConfig.path("selectionScope").isEmpty();
        JsonNode defaults = config.path("defaultValue");
        if (defaults.isNull()) {
            return;
        }
        for (JsonNode value : defaults) {
            UUID userId = UUID.fromString(value.asText());
            SubjectRef subject = new SubjectRef(SubjectType.MEMBER, userId);
            var snapshot = subjectDirectory.resolve(actor.workspaceId(), actor.id(), Set.of(subject)).get(subject);
            if (snapshot == null
                || snapshot.state() != SubjectState.ACTIVE
                || (!unrestricted && !permittedMembers.contains(userId))) {
                throw hiddenReference();
            }
        }
    }

    private Set<UUID> permittedMembers(UUID workspaceId, UUID actorId, JsonNode scope) {
        Set<SubjectRef> subjects = new HashSet<>();
        for (JsonNode entry : scope) {
            UUID subjectId = UUID.fromString(entry.path("subjectId").asText());
            SubjectType subjectType = switch (entry.path("subjectType").asText()) {
                case "member" -> SubjectType.MEMBER;
                case "department" -> SubjectType.DEPARTMENT;
                case "user_group" -> SubjectType.USER_GROUP;
                default -> throw hiddenReference();
            };
            subjects.add(new SubjectRef(subjectType, subjectId));
        }
        var resolved = subjectDirectory.resolve(workspaceId, actorId, subjects);
        if (subjects.stream().anyMatch(subject -> {
            var snapshot = resolved.get(subject);
            return snapshot == null || snapshot.state() != SubjectState.ACTIVE;
        })) {
            throw hiddenReference();
        }
        return subjectDirectory.expandActiveMembers(workspaceId, actorId, subjects);
    }

    private void validateAttachments(CurrentUser actor, JsonNode config) {
        JsonNode defaults = config.path("defaultValue");
        if (defaults.isNull()) {
            return;
        }
        JsonNode typeConfig = config.path("typeConfig");
        long maxSize = typeConfig.path("maxFileSizeBytes").asLong();
        Set<String> allowedTypes = new HashSet<>();
        typeConfig.path("allowedContentTypes").forEach(value -> allowedTypes.add(value.asText()));
        for (JsonNode value : defaults) {
            UUID fileId = UUID.fromString(value.asText());
            var fileResult = fileAccess.resolve(actor.workspaceId(), actor.id(), Set.of(fileId)).get(fileId);
            if (fileResult == null
                || fileResult.availability() != FileAccess.Availability.AVAILABLE
                || fileResult.metadata().size() > maxSize
                || !contentTypeAllowed(fileResult.metadata().mimeType(), allowedTypes)
                || objectRegistry.accessState(actor.workspaceId(), actor.id(), "file", fileId) != ObjectAccessState.available) {
                throw hiddenReference();
            }
        }
    }

    private boolean contentTypeAllowed(String contentType, Set<String> allowedTypes) {
        if (allowedTypes.isEmpty()) {
            return true;
        }
        String normalized = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        if (allowedTypes.contains(normalized)) {
            return true;
        }
        int slash = normalized.indexOf('/');
        return slash > 0 && allowedTypes.contains(normalized.substring(0, slash) + "/*");
    }

    private void validateWorkItemTypes(
        UUID workspaceId,
        UUID spaceId,
        UUID currentTypeId,
        JsonNode config
    ) {
        for (JsonNode value : config.path("typeConfig").path("targetTypeIds")) {
            UUID targetTypeId = UUID.fromString(value.asText());
            var target = typeRepository.findById(workspaceId, spaceId, targetTypeId)
                .orElseThrow(this::hiddenReference);
            if ("retired".equals(target.status())) {
                throw hiddenReference();
            }
        }
        if (!config.path("defaultValue").isNull() && !config.path("defaultValue").isEmpty()) {
            throw new WorkItemFieldException(
                "INVALID_DEFAULT_VALUE",
                "Work-item instance references remain unavailable until S07"
            );
        }
    }

    private WorkItemFieldException hiddenReference() {
        return new WorkItemFieldException(
            "INVALID_COMPLEX_FIELD_REFERENCE",
            "One or more configured references are unavailable"
        );
    }
}
