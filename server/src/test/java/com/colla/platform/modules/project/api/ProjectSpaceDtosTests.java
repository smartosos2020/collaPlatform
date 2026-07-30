package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectSpaceDtosTests {
    @Test
    void calibratesFiveEntryCapabilitiesForCurrentRoleAndMembershipFacts() {
        assertThat(ProjectSpaceDtos.user(space("owner", "active")).availableActions())
            .contains(
                "view_overview",
                "view_work_items",
                "view_project_management",
                "view_members",
                "view_settings"
            );
        assertThat(ProjectSpaceDtos.user(space("member", "active")).availableActions())
            .contains("view_overview", "view_work_items", "view_project_management")
            .doesNotContain("view_members", "view_settings");
        assertThat(ProjectSpaceDtos.user(space("guest", "active")).availableActions())
            .contains("view_overview", "view_work_items")
            .doesNotContain("view_project_management", "view_members", "view_settings");
        assertThat(ProjectSpaceDtos.user(space(null, "active")).availableActions())
            .containsExactly("open", "view_overview");
    }

    @Test
    void preservesReadCapabilitiesWhileLifecycleActionsFollowSpaceState() {
        assertThat(ProjectSpaceDtos.user(space("owner", "disabled")).availableActions())
            .contains("view_overview", "view_work_items", "view_project_management", "view_members", "view_settings")
            .contains("restore", "archive")
            .doesNotContain("disable");
        assertThat(ProjectSpaceDtos.user(space("owner", "archived")).availableActions())
            .contains("view_overview", "view_work_items", "view_project_management", "view_members", "view_settings")
            .contains("restore")
            .doesNotContain("disable", "archive");
    }

    private ProjectSpaceSummary space(String role, String status) {
        Instant now = Instant.now();
        return new ProjectSpaceSummary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "space",
            "Space",
            "",
            status,
            "discoverable",
            1,
            role,
            role == null ? 0 : 1,
            UUID.randomUUID(),
            now,
            UUID.randomUUID(),
            now,
            "disabled".equals(status) ? now : null,
            "archived".equals(status) ? now : null
        );
    }
}
