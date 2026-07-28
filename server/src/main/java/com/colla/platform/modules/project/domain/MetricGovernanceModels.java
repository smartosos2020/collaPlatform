package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MetricGovernanceModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_REPORTS = 50;
    public static final int MAX_RUNS = 100;
    public static final int MAX_EXPORT_ROWS = 500;

    private MetricGovernanceModels() {
    }

    public record ConfigHealth(
        String component,
        String status,
        int visibleCount,
        String sourceVersion,
        boolean truncated,
        String explanation
    ) {
    }

    public record GovernanceOverview(
        int schemaVersion,
        List<ConfigHealth> health,
        int openRisks,
        int publishedMetrics,
        int activeDashboards,
        boolean truncated,
        Instant observedAt,
        String diagnostic
    ) {
    }

    public record AuditReport(
        UUID id,
        String reportKey,
        String name,
        String description,
        List<String> sections,
        String status,
        long version,
        Instant updatedAt
    ) {
    }

    public record ReportRun(
        UUID id,
        UUID reportId,
        long reportVersion,
        String status,
        GovernanceOverview result,
        String sourceFingerprint,
        UUID runBy,
        Instant startedAt,
        Instant completedAt
    ) {
    }

    public record ExportReceipt(
        UUID id,
        UUID runId,
        String format,
        int rowCount,
        boolean truncated,
        String contentHash,
        List<Map<String, String>> rows,
        Instant exportedAt
    ) {
    }

    public record GovernanceFoundation(
        GovernanceOverview overview,
        List<AuditReport> reports,
        List<ReportRun> runs,
        Map<String, Integer> budgets
    ) {
    }

    public record SaveReportCommand(
        int schemaVersion,
        String requestId,
        UUID reportId,
        long expectedVersion,
        String reportKey,
        String name,
        String description,
        List<String> sections
    ) {
    }

    public record RunReportCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion
    ) {
    }

    public record ExportReportCommand(
        int schemaVersion,
        String requestId,
        String format
    ) {
    }
}
