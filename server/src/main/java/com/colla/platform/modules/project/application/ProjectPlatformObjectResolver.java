package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectResolver;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectSummary;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProjectPlatformObjectResolver implements PlatformObjectResolver {
    private final ProjectRepository projectRepository;

    public ProjectPlatformObjectResolver(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public String objectType() {
        return "project";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(UUID workspaceId, UUID actorId, UUID objectId) {
        Optional<ProjectSummary> project = projectRepository.findProjectById(workspaceId, objectId);
        if (project.isEmpty()) {
            return Optional.empty();
        }
        if (!projectRepository.isProjectMember(workspaceId, objectId, actorId)) {
            return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.forbidden));
        }
        ProjectSummary value = project.get();
        return Optional.of(new PlatformObjectSummary(
            objectType(),
            objectId,
            ObjectAccessState.available,
            value.name(),
            value.projectKey() + " / " + value.openIssueCount() + " 个未完成事项",
            value.status(),
            "/projects/" + objectId,
            "colla://project/" + objectId,
            Map.of(
                "projectKey", value.projectKey(),
                "memberCount", value.memberCount(),
                "openIssueCount", value.openIssueCount(),
                "sourceModule", "project",
                "updatedAt", value.updatedAt().toString()
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
