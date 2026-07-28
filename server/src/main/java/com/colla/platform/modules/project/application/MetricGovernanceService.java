package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.MetricGovernanceModels.MAX_EXPORT_ROWS;
import static com.colla.platform.modules.project.domain.MetricGovernanceModels.MAX_REPORTS;
import static com.colla.platform.modules.project.domain.MetricGovernanceModels.MAX_RUNS;
import static com.colla.platform.modules.project.domain.MetricGovernanceModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.AuditReport;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ConfigHealth;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ExportReceipt;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ExportReportCommand;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.GovernanceFoundation;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.GovernanceOverview;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ReportRun;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.RunReportCommand;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.SaveReportCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignal;
import com.colla.platform.modules.project.infrastructure.MetricGovernanceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricGovernanceService {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{1,63}");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");
    private static final Set<String> SECTIONS = Set.of(
        "metrics", "dashboards", "risks", "configuration", "audit"
    );

    private final MetricGovernanceRepository repository;
    private final MetricSemanticService metrics;
    private final MetricDashboardService dashboards;
    private final MetricRiskService risks;
    private final WorkItemRelationAccessDecisionService access;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper json;

    public MetricGovernanceService(
        MetricGovernanceRepository repository,
        MetricSemanticService metrics,
        MetricDashboardService dashboards,
        MetricRiskService risks,
        WorkItemRelationAccessDecisionService access,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper json
    ) {
        this.repository = repository;
        this.metrics = metrics;
        this.dashboards = dashboards;
        this.risks = risks;
        this.access = access;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.json = json;
    }

    public GovernanceFoundation foundation(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        return new GovernanceFoundation(
            overview(user, spaceId),
            repository.reports(user.workspaceId(), spaceId, MAX_REPORTS),
            repository.runs(user.workspaceId(), spaceId, MAX_RUNS),
            Map.of("reports", MAX_REPORTS, "runs", MAX_RUNS, "exportRows", MAX_EXPORT_ROWS)
        );
    }

    public GovernanceOverview overview(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        var metric = metrics.foundation(user, spaceId);
        var dashboard = dashboards.foundation(user, spaceId);
        var risk = risks.foundation(user, spaceId);
        int published = (int) metric.metrics().stream()
            .filter(value -> value.publishedVersion() != null).count();
        int active = (int) dashboard.dashboards().stream()
            .filter(value -> "active".equals(value.status())).count();
        int open = (int) risk.signals().stream()
            .filter(value -> Set.of("open", "acknowledged").contains(value.state())).count();
        boolean truncated = metric.truncated() || dashboard.truncated() || risk.truncated();
        List<ConfigHealth> health = List.of(
            health("metrics", metric.metrics().isEmpty() ? "unknown" : "healthy",
                metric.metrics().size(), "MetricSemanticService:v1", metric.truncated(),
                metric.metrics().isEmpty() ? "No visible metric definition" : "Visible metric definitions are versioned"),
            health("dashboards", dashboard.dashboards().isEmpty() ? "unknown" : "healthy",
                dashboard.dashboards().size(), "MetricDashboardService:v1", dashboard.truncated(),
                dashboard.dashboards().isEmpty() ? "No visible dashboard" : "Visible dashboards use published metric versions"),
            health("risks", open > 0 ? "attention" : risk.signals().isEmpty() ? "unknown" : "healthy",
                risk.signals().size(), "MetricRiskService:v1", risk.truncated(),
                open > 0 ? "Open or acknowledged risk signals require review" : "No open visible risk signal")
        );
        return new GovernanceOverview(
            SCHEMA_VERSION, health, open, published, active, truncated,
            Instant.now(), truncated
                ? "Unknown: at least one public source is truncated"
                : "Current authorized governance metadata only"
        );
    }

    @Transactional
    public AuditReport save(
        CurrentUser user,
        UUID spaceId,
        SaveReportCommand command
    ) {
        access.requireManager(user, spaceId);
        validateSave(command);
        UUID reportId = command.reportId() == null
            ? stableId(user, spaceId, "report", command.requestId()) : command.reportId();
        AuditReport result = repository.save(
            user.workspaceId(), spaceId, user.id(), reportId,
            command.reportKey(), command.name().trim(), command.description().trim(),
            command.sections().stream().distinct().sorted().toList(),
            command.expectedVersion(), command.requestId(), hash(command)
        );
        emit(user, spaceId, reportId, result.version(), "report_saved", command.requestId());
        return result;
    }

    @Transactional
    public ReportRun run(
        CurrentUser user,
        UUID spaceId,
        UUID reportId,
        RunReportCommand command
    ) {
        access.requireManager(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 1) {
            throw failure("GOVERNANCE_REPORT_RUN_INVALID", "Report run input is invalid");
        }
        AuditReport report = repository.report(user.workspaceId(), spaceId, reportId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Report is not available"));
        if (report.version() != command.expectedVersion()) throw versionConflict();
        GovernanceOverview current = overview(user, spaceId);
        ReportRun result = repository.run(
            user.workspaceId(), spaceId, user.id(), report, current,
            hash(current), command.requestId(), hash(command)
        );
        emit(user, spaceId, reportId, report.version(), "report_run", command.requestId());
        return result;
    }

    @Transactional
    public ExportReceipt export(
        CurrentUser user,
        UUID spaceId,
        UUID runId,
        ExportReportCommand command
    ) {
        access.requireManager(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId())
            || !Set.of("csv", "json").contains(command.format())) {
            throw failure("GOVERNANCE_EXPORT_INVALID", "Report export input is invalid");
        }
        ReportRun run = repository.run(user.workspaceId(), spaceId, runId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Report run is not available"));
        GovernanceOverview current = overview(user, spaceId);
        List<Map<String, String>> rows = new ArrayList<>();
        for (ConfigHealth item : current.health()) {
            rows.add(Map.of(
                "component", item.component(),
                "status", item.status(),
                "visibleCount", Integer.toString(item.visibleCount()),
                "sourceVersion", item.sourceVersion(),
                "explanation", item.explanation()
            ));
        }
        boolean truncated = current.truncated() || rows.size() > MAX_EXPORT_ROWS;
        List<Map<String, String>> bounded = rows.stream().limit(MAX_EXPORT_ROWS).toList();
        return repository.export(
            user.workspaceId(), spaceId, user.id(), run, command.format(),
            bounded, truncated, hash(bounded), command.requestId(), hash(command)
        );
    }

    private ConfigHealth health(
        String component, String status, int count, String sourceVersion,
        boolean truncated, String explanation
    ) {
        return new ConfigHealth(
            component, truncated ? "unknown" : status, truncated ? 0 : count,
            sourceVersion, truncated, truncated ? "Source truncated; health is unknown" : explanation
        );
    }

    private void validateSave(SaveReportCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.reportKey() == null
            || !KEY.matcher(command.reportKey()).matches()
            || command.name() == null || command.name().isBlank() || command.name().length() > 160
            || command.description() == null || command.description().length() > 2000
            || command.sections() == null || command.sections().isEmpty()
            || command.sections().size() > SECTIONS.size()
            || !SECTIONS.containsAll(command.sections())
            || new HashSet<>(command.sections()).size() != command.sections().size()
            || command.expectedVersion() < 0) {
            throw failure("GOVERNANCE_REPORT_INVALID", "Governance report input is invalid");
        }
    }

    private void emit(
        CurrentUser user, UUID spaceId, UUID objectId, long version,
        String change, String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "spaceId", spaceId, "objectId", objectId, "version", version, "change", change
        );
        auditLog.log(user, "project_governance." + change, "project_governance", objectId, metadata);
        outbox.append(
            user.workspaceId(), "project.governance.changed", "project_governance",
            objectId, user.id(), metadata,
            "project-governance:" + stableId(user, spaceId, "event", requestId)
        );
    }

    private RuntimeException versionConflict() {
        return failure("GOVERNANCE_VERSION_CONFLICT", "Governance object changed; refresh before retrying");
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private UUID stableId(CurrentUser user, UUID spaceId, String kind, String requestId) {
        return UUID.nameUUIDFromBytes(
            (user.workspaceId() + ":" + spaceId + ":" + user.id() + ":" + kind + ":" + requestId)
                .getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hash(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(write(value).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
