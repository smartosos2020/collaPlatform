package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyProfile;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemCompatibilityRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Retained history-only compatibility boundary.
 *
 * <p>S21-M2 removed legacy reads, writes, cutover mutation and shadow serving. The remaining user
 * operation only resolves an authorized old identity to an immutable canonical location.</p>
 */
@Service
public class WorkItemCompatibilityService {
    private final WorkItemCompatibilityRepository compatibilityRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAuthorization authorization;

    public WorkItemCompatibilityService(
        WorkItemCompatibilityRepository compatibilityRepository,
        ProjectRepository projectRepository,
        ProjectAuthorization authorization
    ) {
        this.compatibilityRepository = compatibilityRepository;
        this.projectRepository = projectRepository;
        this.authorization = authorization;
    }

    public LegacyProfile profile(CurrentUser user) {
        authorization.requireManageProjects(user);
        return compatibilityRepository.profile(user.workspaceId());
    }

    public String canonicalLocation(CurrentUser user, UUID issueId) {
        UUID projectId = compatibilityRepository.findIssueProject(user.workspaceId(), issueId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
        if (!projectRepository.isProjectMember(user.workspaceId(), projectId, user.id())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Work item is not available");
        }
        return compatibilityRepository.findMap(user.workspaceId(), "issue", issueId)
            .map(map -> map.canonicalLocation())
            .orElseThrow(() -> failure("LEGACY_REFERENCE_RETIRED", "Legacy issue has no canonical WorkItem mapping"));
    }
}
