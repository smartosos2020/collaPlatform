package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationCapabilities;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemRelationAccessDecisionService {
    private final ProjectSpaceRepository spaceRepository;

    public WorkItemRelationAccessDecisionService(ProjectSpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    public ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project space is not available"));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    public ProjectSpaceSummary requireWritable(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if ("guest".equals(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Guest project space members have read-only relation access");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
        return space;
    }

    public ProjectSpaceSummary requireManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireWritable(user, spaceId);
        if (!"owner".equals(space.currentUserRole())
            && !"admin".equals(space.currentUserRole())) {
            throw failure(
                "FORBIDDEN",
                "Hierarchy recovery requires a project space owner or administrator"
            );
        }
        return space;
    }

    public RelationCapabilities capabilities(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        WorkItem source,
        WorkItem target,
        String relationStatus
    ) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        List<String> reasons = new ArrayList<>();
        boolean writableRole = !"guest".equals(space.currentUserRole());
        if (!writableRole) {
            reasons.add("read_only_role");
        }
        if (!"active".equals(space.status())) {
            reasons.add("space_not_active");
        }
        if (source != null && !"active".equals(source.status())) {
            reasons.add("source_not_active");
        }
        if (target != null && !"active".equals(target.status())) {
            reasons.add("target_not_active");
        }
        boolean endpointsActive = (source == null || "active".equals(source.status()))
            && (target == null || "active".equals(target.status()));
        boolean canMutate = writableRole && "active".equals(space.status());
        return new RelationCapabilities(
            relationKey,
            true,
            canMutate && endpointsActive,
            canMutate && "active".equals(relationStatus),
            canMutate && endpointsActive && "withdrawn".equals(relationStatus),
            List.copyOf(reasons)
        );
    }
}
