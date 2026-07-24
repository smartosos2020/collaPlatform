package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectResolver;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProjectSpacePlatformObjectResolver implements PlatformObjectResolver {
    private final ProjectSpaceRepository projectSpaceRepository;

    public ProjectSpacePlatformObjectResolver(ProjectSpaceRepository projectSpaceRepository) {
        this.projectSpaceRepository = projectSpaceRepository;
    }

    @Override
    public String objectType() {
        return "project_space";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(UUID workspaceId, UUID actorId, UUID objectId) {
        Optional<ProjectSpaceSummary> found = projectSpaceRepository.findById(
            workspaceId, objectId, actorId
        );
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ProjectSpaceSummary space = found.get();
        if ("archived".equals(space.status())) {
            return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.deleted));
        }
        if (!space.isMember() && "private".equals(space.visibility())) {
            return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.forbidden));
        }
        return Optional.of(new PlatformObjectSummary(
            objectType(),
            objectId,
            ObjectAccessState.available,
            space.name(),
            space.spaceKey() + " / " + space.memberCount() + " members",
            space.status(),
            "/project-spaces/" + objectId,
            "colla://project-space/" + objectId,
            Map.of(
                "spaceKey", space.spaceKey(),
                "visibility", space.visibility(),
                "memberCount", space.memberCount(),
                "sourceModule", "project",
                "updatedAt", space.updatedAt().toString()
            )
        ));
    }

    @Override
    public ObjectAccessState accessState(UUID workspaceId, UUID actorId, UUID objectId) {
        return resolve(workspaceId, actorId, objectId)
            .map(PlatformObjectSummary::accessState)
            .orElse(ObjectAccessState.not_found);
    }
}
