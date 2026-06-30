package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectLinkRecord;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IssuePlatformObjectResolver implements PlatformObjectResolver {
    private final ProjectRepository projectRepository;
    private final PlatformObjectRepository objectRepository;

    public IssuePlatformObjectResolver(ProjectRepository projectRepository, PlatformObjectRepository objectRepository) {
        this.projectRepository = projectRepository;
        this.objectRepository = objectRepository;
    }

    @Override
    public String objectType() {
        return "issue";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        Optional<IssueSummary> issue = projectRepository.findIssue(currentUser.workspaceId(), objectId);
        if (issue.isEmpty()) {
            return objectRepository.findObjectLink(currentUser.workspaceId(), "issue", objectId)
                .map(this::fallbackSummary);
        }
        IssueSummary value = issue.get();
        if (!projectRepository.isProjectMember(currentUser.workspaceId(), value.projectId(), currentUser.id())) {
            return Optional.of(PlatformObjectSummary.unavailable("issue", objectId, ObjectAccessState.forbidden));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("projectId", value.projectId().toString());
        metadata.put("issueKey", value.issueKey());
        metadata.put("priority", value.priority());
        metadata.put("sourceModule", "project");
        metadata.put("updatedAt", value.updatedAt().toString());
        metadata.put("backReferencePath", "/issues/" + objectId + "#relations");
        if (value.workflowReason() != null) {
            metadata.put("workflowReason", value.workflowReason());
        }
        if (value.resolution() != null) {
            metadata.put("resolution", value.resolution());
        }
        return Optional.of(new PlatformObjectSummary(
            "issue",
            objectId,
            ObjectAccessState.available,
            value.issueKey() + " " + value.title(),
            value.projectKey() + " / " + value.issueType(),
            value.status(),
            "/issues/" + objectId,
            "colla://issue/" + objectId,
            metadata
        ));
    }

    private PlatformObjectSummary fallbackSummary(ObjectLinkRecord link) {
        ObjectAccessState state = link.deletedAt() == null ? ObjectAccessState.available : ObjectAccessState.deleted;
        return new PlatformObjectSummary(
            link.objectType(),
            link.objectId(),
            state,
            state == ObjectAccessState.available ? link.titleSnapshot() : null,
            null,
            null,
            state == ObjectAccessState.available ? link.webPath() : null,
            state == ObjectAccessState.available ? link.deepLink() : null,
            Map.of()
        );
    }
}
