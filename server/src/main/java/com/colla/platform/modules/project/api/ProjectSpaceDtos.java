package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.LegacySpaceResolution;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ProjectSpaceDtos {
    private ProjectSpaceDtos() {
    }

    static UserProjectSpaceView user(ProjectSpaceSummary space) {        List<String> actions = new ArrayList<>();
        actions.add("open");
        if (space.canManage()) {
            actions.add("settings");
            if ("active".equals(space.status())) {
                actions.add("disable");
                actions.add("archive");
            } else if ("disabled".equals(space.status())) {
                actions.add("restore");
                actions.add("archive");
            } else if ("archived".equals(space.status())) {
                actions.add("restore");
            }
        }
        return new UserProjectSpaceView(
            space.id(), space.spaceKey(), space.name(), space.description(), space.status(), space.visibility(),
            space.version(), space.currentUserRole(), space.memberCount(), space.isMember(),
            space.createdAt(), space.updatedAt(), space.disabledAt(), space.archivedAt(), List.copyOf(actions)
        );
    }

    static AdminProjectSpaceView admin(
        ProjectSpaceSummary space,
        boolean contentAccessGranted,
        WorkItemTypeApiDtos.AdminWorkItemTypeCounts workItemTypes
    ) {
        List<String> actions = switch (space.status()) {
            case "active" -> List.of("disable", "archive");
            case "disabled" -> List.of("restore", "archive");
            case "archived" -> List.of("restore");
            default -> List.of();
        };
        return new AdminProjectSpaceView(
            space.id(), space.workspaceId(), space.spaceKey(), space.name(), space.description(),
            space.status(), space.visibility(), space.version(), space.memberCount(), space.createdBy(),
            space.createdAt(), space.updatedBy(), space.updatedAt(), space.disabledAt(), space.archivedAt(),
            contentAccessGranted, "project.manage", workItemTypes, actions
        );
    }

    record UserProjectSpaceView(
        UUID id,
        String spaceKey,
        String name,
        String description,
        String status,
        String visibility,
        long version,
        String currentUserRole,
        int memberCount,
        boolean member,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant archivedAt,
        List<String> availableActions
    ) {
    }

    record AdminProjectSpaceView(
        UUID id,
        UUID workspaceId,
        String spaceKey,
        String name,
        String description,
        String status,
        String visibility,
        long version,
        int memberCount,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Instant disabledAt,
        Instant archivedAt,
        boolean contentAccessGranted,
        String governancePermission,
        WorkItemTypeApiDtos.AdminWorkItemTypeCounts workItemTypes,
        List<String> availableGovernanceActions
    ) {
    }

    record ProjectSpacePermissionExplanation(
        UUID spaceId,
        String governancePermission,
        boolean governanceAllowed,
        boolean contentAccessGranted,
        String contentAccessSource,
        String explanation
    ) {
    }

    static LegacySpaceResolutionView legacyResolution(LegacySpaceResolution resolution) {
        return new LegacySpaceResolutionView(resolution.status(), resolution.spaceId());
    }

    record LegacySpaceResolutionView(
        String status,
        UUID spaceId
    ) {
    }
}
