package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectPlanModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PLANS = 20;
    public static final int MAX_PHASES = 24;
    public static final int MAX_MILESTONES = 100;
    public static final int MAX_LINKS = 200;
    public static final int MAX_CHANGES = 100;

    private ProjectPlanModels() {
    }

    public record PhaseInput(
        UUID id,
        String phaseKey,
        String name,
        int position,
        LocalDate startDate,
        LocalDate endDate,
        String status
    ) {
    }

    public record MilestoneInput(
        UUID id,
        UUID phaseId,
        String milestoneKey,
        String name,
        int position,
        LocalDate targetDate,
        String status,
        UUID ownerUserId
    ) {
    }

    public record LinkInput(
        UUID id,
        UUID milestoneId,
        UUID workItemId
    ) {
    }

    public record CreateCommand(
        int schemaVersion,
        String requestId,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links
    ) {
    }

    public record MutateCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String operation,
        String reason,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links
    ) {
    }

    public record PlanSummary(
        UUID id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        long version,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Instant archivedAt
    ) {
    }

    public record PlanPhase(
        UUID id,
        String phaseKey,
        String name,
        int position,
        LocalDate startDate,
        LocalDate endDate,
        String status
    ) {
    }

    public record PlanMilestone(
        UUID id,
        UUID phaseId,
        String milestoneKey,
        String name,
        int position,
        LocalDate targetDate,
        String status,
        UUID ownerUserId
    ) {
    }

    public record PlanLink(
        UUID id,
        UUID milestoneId,
        UUID workItemId,
        long sourceWorkItemVersion
    ) {
    }

    public record PlanChange(
        long sequence,
        String operation,
        String reason,
        UUID actorId,
        long planVersion,
        Instant occurredAt
    ) {
    }

    public record PlanProgress(
        int visibleMilestones,
        int completedMilestones,
        int visibleLinks,
        int overdueMilestones,
        int completionPercent,
        boolean truncated
    ) {
    }

    public record ProjectPlan(
        PlanSummary plan,
        List<PlanPhase> phases,
        List<PlanMilestone> milestones,
        List<PlanLink> links,
        List<PlanChange> changes,
        PlanProgress progress
    ) {
    }
}
