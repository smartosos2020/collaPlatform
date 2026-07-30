package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.ChecklistStep;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingState;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class ProjectSpaceOnboardingCatalog {
    public String track(ProjectSpaceSummary space) {
        if (space.canManage()) {
            return "manager";
        }
        return "guest".equals(space.currentUserRole()) ? "guest" : "member";
    }

    public List<ChecklistStep> checklist(ProjectSpaceSummary space, OnboardingState state) {
        String track = track(space);
        boolean readOnly = !"active".equals(space.status());
        if ("manager".equals(track)) {
            return managerSteps(space.id(), state, readOnly);
        }
        if ("guest".equals(track)) {
            return List.of(step(
                space.id(), "find_work", "/work-items", List.of(),
                "project.work-items", readOnly
            ));
        }
        return memberSteps(space.id(), readOnly);
    }

    public Set<String> stepKeys(ProjectSpaceSummary space, OnboardingState state) {
        return checklist(space, state).stream()
            .map(ChecklistStep::stepKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<ChecklistStep> managerSteps(
        UUID spaceId,
        OnboardingState state,
        boolean readOnly
    ) {
        List<ChecklistStep> result = new ArrayList<>();
        result.add(step(
            spaceId, "choose_starting_point", "", List.of(),
            "project.onboarding-experience", false
        ));
        boolean scenario = "scenario".equals(state.startingPoint());
        String configurationDependency = "choose_starting_point";
        if (scenario) {
            result.add(step(
                spaceId, "preview_impact", "/management", List.of("choose_starting_point"),
                "project.scenario-templates.validation", readOnly
            ));
            result.add(step(
                spaceId, "install_scenario", "/management", List.of("preview_impact"),
                "project.scenario-templates.installation", readOnly
            ));
            configurationDependency = "install_scenario";
        }
        result.add(step(
            spaceId, "configure_work_model", "/types", List.of(configurationDependency),
            "project.work-item-types", readOnly
        ));
        result.add(step(
            spaceId, "configure_fields_and_pages", "/types", List.of("configure_work_model"),
            "project.work-item-fields-layouts", readOnly
        ));
        result.add(step(
            spaceId, "configure_workflow", "/types", List.of("configure_fields_and_pages"),
            "project.work-item-workflow", readOnly
        ));
        result.add(step(
            spaceId, "configure_permissions", "/members", List.of("configure_workflow"),
            "project.space-permissions", readOnly
        ));
        result.add(step(
            spaceId, "publish_configuration", "/management", List.of("configure_permissions"),
            "project.configuration-publication", readOnly
        ));
        result.add(step(
            spaceId, "configure_automation", "/management", List.of("publish_configuration"),
            "project.automation", readOnly
        ));
        result.add(step(
            spaceId, "configure_metrics", "/management", List.of("publish_configuration"),
            "project.metrics", readOnly
        ));
        result.add(step(
            spaceId, "invite_members", "/members", List.of(configurationDependency),
            "project.space-membership", readOnly
        ));
        result.add(step(
            spaceId, "create_first_work_item", "/work-items", List.of("invite_members"),
            "project.work-items", readOnly
        ));
        result.add(step(
            spaceId, "handoff_first_work_item", "/work-items", List.of("create_first_work_item"),
            "project.work-item-participants", readOnly
        ));
        return List.copyOf(result);
    }

    private List<ChecklistStep> memberSteps(UUID spaceId, boolean readOnly) {
        return List.of(
            step(spaceId, "find_work", "/work-items", List.of(), "project.work-items", readOnly),
            step(
                spaceId, "create_or_update_work", "/work-items", List.of("find_work"),
                "project.work-items", readOnly
            ),
            step(
                spaceId, "comment_on_work", "/work-items", List.of("find_work"),
                "project.work-item-comments", readOnly
            ),
            step(
                spaceId, "attach_file", "/work-items", List.of("find_work"),
                "project.work-item-attachments", readOnly
            ),
            step(
                spaceId, "transition_state", "/work-items", List.of("find_work"),
                "project.work-item-state-flow", readOnly
            ),
            new ChecklistStep(
                "review_notifications",
                "project.onboarding.step.review_notifications",
                "project.onboarding.help.review_notifications",
                "/notifications",
                List.of(),
                "notifications.user-api",
                readOnly ? "blocked" : "verify_on_owner_api"
            )
        );
    }

    private ChecklistStep step(
        UUID spaceId,
        String stepKey,
        String suffix,
        List<String> dependencies,
        String ownerContract,
        boolean blocked
    ) {
        String status = "project.onboarding-experience".equals(ownerContract)
            ? "available"
            : blocked ? "blocked" : "verify_on_owner_api";
        return new ChecklistStep(
            stepKey,
            "project.onboarding.step." + stepKey,
            "project.onboarding.help." + stepKey,
            "/project-spaces/" + spaceId + suffix,
            dependencies,
            ownerContract,
            status
        );
    }
}
