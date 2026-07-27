package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectDetailModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SIGNALS = 50;
    public static final int MAX_DETAIL_PLANS = 20;
    public static final int MAX_DETAIL_REGISTER = 200;
    public static final int MAX_DETAIL_DELIVERABLES = 100;

    private ProjectDetailModels() {
    }

    public record PreferenceCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        List<String> visibleSections,
        boolean compact
    ) {
    }

    public record DetailPreference(
        int schemaVersion,
        List<String> visibleSections,
        boolean compact,
        long version,
        Instant updatedAt
    ) {
    }

    public record HealthSignal(
        String code,
        String severity,
        String sourceType,
        UUID sourceId,
        long sourceVersion,
        String rule,
        String explanation,
        Instant observedAt
    ) {
    }

    public record Deviation(
        UUID planId,
        long planVersion,
        int completionPercent,
        int overdueMilestones,
        int visibleMilestones
    ) {
    }

    public record BlockingSummary(
        int openIssues,
        int highRisks,
        int pendingChanges,
        int pendingAcceptances,
        int rejectedDeliverables
    ) {
    }

    public record HealthStatus(
        String status,
        List<HealthSignal> signals,
        boolean truncated,
        String policyVersion,
        Instant derivedAt
    ) {
    }

    public record ProjectDetail(
        List<PlanSummary> plans,
        List<RegisterSummary> registerEntries,
        List<DeliverableSummary> deliverables,
        List<Deviation> deviations,
        BlockingSummary blocking,
        HealthStatus health,
        DetailPreference preference
    ) {
    }
}
