package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
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
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        Optional<ProjectSpaceSummary> found = projectSpaceRepository.findById(
            currentUser.workspaceId(), objectId, currentUser.id()
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
}
