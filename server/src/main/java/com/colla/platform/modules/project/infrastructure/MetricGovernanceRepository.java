package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.MetricGovernanceModels.AuditReport;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ExportReceipt;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.GovernanceOverview;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ReportRun;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface MetricGovernanceRepository {
    List<AuditReport> reports(UUID workspaceId, UUID spaceId, int limit);

    Optional<AuditReport> report(UUID workspaceId, UUID spaceId, UUID reportId);

    List<ReportRun> runs(UUID workspaceId, UUID spaceId, int limit);

    Optional<ReportRun> run(UUID workspaceId, UUID spaceId, UUID runId);

    AuditReport save(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID reportId,
        String reportKey,
        String name,
        String description,
        List<String> sections,
        long expectedVersion,
        String requestId,
        String requestHash
    );

    ReportRun run(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        AuditReport report,
        GovernanceOverview overview,
        String sourceFingerprint,
        String requestId,
        String requestHash
    );

    ExportReceipt export(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        ReportRun run,
        String format,
        List<Map<String, String>> rows,
        boolean truncated,
        String contentHash,
        String requestId,
        String requestHash
    );
}
