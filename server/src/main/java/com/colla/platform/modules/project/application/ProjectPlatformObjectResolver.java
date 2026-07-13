package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectSummary;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
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
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        Optional<ProjectSummary> project = projectRepository.findProjectById(currentUser.workspaceId(), objectId);
        if (project.isEmpty()) {
            return Optional.empty();
        }
        if (!projectRepository.isProjectMember(currentUser.workspaceId(), objectId, currentUser.id())) {
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
}
