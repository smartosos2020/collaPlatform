package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectResolver;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemCompatibilityRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IssuePlatformObjectResolver implements PlatformObjectResolver {
    private final ProjectRepository projectRepository;
    private final WorkItemCompatibilityRepository compatibilityRepository;
    private final WorkItemRepository workItemRepository;

    public IssuePlatformObjectResolver(
        ProjectRepository projectRepository,
        WorkItemCompatibilityRepository compatibilityRepository,
        WorkItemRepository workItemRepository
    ) {
        this.projectRepository = projectRepository;
        this.compatibilityRepository = compatibilityRepository;
        this.workItemRepository = workItemRepository;
    }

    @Override
    public String objectType() {
        return "issue";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(UUID workspaceId, UUID actorId, UUID objectId) {
        Optional<IssueSummary> issue = projectRepository.findIssue(workspaceId, objectId);
        if (issue.isEmpty()) {
            return Optional.empty();
        }
        IssueSummary value = issue.get();
        if (!projectRepository.isProjectMember(workspaceId, value.projectId(), actorId)) {
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
        var mapping = compatibilityRepository.findMap(workspaceId, "issue", objectId).orElse(null);
        String webPath = "/issues/" + objectId;
        String deepLink = "colla://issue/" + objectId;
        if (mapping != null && workItemRepository
            .find(workspaceId, mapping.spaceId(), mapping.workItemId()).isPresent()) {
            webPath = mapping.canonicalLocation();
            deepLink = "colla://work-item/" + mapping.workItemId();
            metadata.put("canonicalObjectType", "work_item");
            metadata.put("canonicalId", mapping.workItemId().toString());
            metadata.put("canonicalLocation", mapping.canonicalLocation());
        }
        return Optional.of(new PlatformObjectSummary(
            "issue",
            objectId,
            ObjectAccessState.available,
            value.issueKey() + " " + value.title(),
            value.projectKey() + " / " + value.issueType(),
            value.status(),
            webPath,
            deepLink,
            metadata
        ));
    }

    @Override
    public ObjectAccessState accessState(UUID workspaceId, UUID actorId, UUID objectId) {
        return resolve(workspaceId, actorId, objectId)
            .map(PlatformObjectSummary::accessState)
            .orElse(ObjectAccessState.not_found);
    }
}
