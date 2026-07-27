package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectPlanModels.LinkInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.MilestoneInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PhaseInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPlanRepository {
    List<PlanSummary> list(UUID workspaceId, UUID spaceId, int limit);

    Optional<ProjectPlan> find(
        UUID workspaceId,
        UUID spaceId,
        UUID planId,
        int changeLimit
    );

    Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId
    );

    ProjectPlan create(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String requestId,
        String requestHash,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links,
        Map<UUID, Long> workItemVersions
    );

    ProjectPlan mutate(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID planId,
        String operation,
        String requestId,
        String requestHash,
        long expectedVersion,
        String reason,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links,
        Map<UUID, Long> workItemVersions
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
