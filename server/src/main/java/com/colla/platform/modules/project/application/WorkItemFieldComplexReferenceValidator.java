package com.colla.platform.modules.project.application;

import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.identity.infrastructure.OrganizationRepository;
import com.colla.platform.modules.identity.infrastructure.UserGroupRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
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
    private final IdentityRepository identityRepository;
    private final OrganizationRepository organizationRepository;
    private final UserGroupRepository userGroupRepository;
    private final FileRepository fileRepository;
    private final PlatformObjectResolverRegistry objectResolverRegistry;
    private final WorkItemTypeRepository typeRepository;

    public WorkItemFieldComplexReferenceValidator(
        IdentityRepository identityRepository,
        OrganizationRepository organizationRepository,
        UserGroupRepository userGroupRepository,
        FileRepository fileRepository,
        PlatformObjectResolverRegistry objectResolverRegistry,
        WorkItemTypeRepository typeRepository
    ) {
        this.identityRepository = identityRepository;
        this.organizationRepository = organizationRepository;
        this.userGroupRepository = userGroupRepository;
        this.fileRepository = fileRepository;
        this.objectResolverRegistry = objectResolverRegistry;
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
        Set<UUID> permittedMembers = permittedMembers(actor.workspaceId(), typeConfig.path("selectionScope"));
        boolean unrestricted = typeConfig.path("selectionScope").isEmpty();
        JsonNode defaults = config.path("defaultValue");
        if (defaults.isNull()) {
            return;
        }
        for (JsonNode value : defaults) {
            UUID userId = UUID.fromString(value.asText());
            UserAccount account = identityRepository.findUserById(userId).orElseThrow(this::hiddenReference);
            if (!actor.workspaceId().equals(account.workspaceId())
                || !"active".equals(account.status())
                || (!unrestricted && !permittedMembers.contains(userId))) {
                throw hiddenReference();
            }
        }
    }

    private Set<UUID> permittedMembers(UUID workspaceId, JsonNode scope) {
        Set<UUID> result = new HashSet<>();
        for (JsonNode entry : scope) {
            UUID subjectId = UUID.fromString(entry.path("subjectId").asText());
            switch (entry.path("subjectType").asText()) {
                case "member" -> {
                    UserAccount account = identityRepository.findUserById(subjectId).orElseThrow(this::hiddenReference);
                    if (!workspaceId.equals(account.workspaceId()) || !"active".equals(account.status())) {
                        throw hiddenReference();
                    }
                    result.add(account.id());
                }
                case "department" -> {
                    var department = organizationRepository.findDepartment(workspaceId, subjectId)
                        .orElseThrow(this::hiddenReference);
                    if (!"active".equals(department.status())) {
                        throw hiddenReference();
                    }
                    organizationRepository.listDepartmentMembers(workspaceId, subjectId).stream()
                        .filter(member -> "active".equals(member.status()) && member.endedAt() == null)
                        .map(member -> member.userId())
                        .forEach(result::add);
                }
                case "user_group" -> {
                    var group = userGroupRepository.findGroup(workspaceId, subjectId)
                        .orElseThrow(this::hiddenReference);
                    if (!"active".equals(group.status())) {
                        throw hiddenReference();
                    }
                    userGroupRepository.listExpandedMembers(workspaceId, subjectId).stream()
                        .filter(member -> "active".equals(member.status()))
                        .map(member -> member.userId())
                        .forEach(result::add);
                }
                default -> throw hiddenReference();
            }
        }
        return result;
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
            FileMetadata file = fileRepository.find(actor.workspaceId(), fileId).orElseThrow(this::hiddenReference);
            if (!"completed".equals(file.status())
                || file.sizeBytes() > maxSize
                || !contentTypeAllowed(file.contentType(), allowedTypes)
                || objectResolverRegistry.resolve(actor, "file", fileId).accessState() != ObjectAccessState.available) {
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
