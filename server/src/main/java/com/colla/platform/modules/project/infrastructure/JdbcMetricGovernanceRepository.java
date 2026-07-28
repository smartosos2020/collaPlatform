package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.MetricGovernanceModels.AuditReport;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ExportReceipt;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.GovernanceOverview;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ReportRun;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMetricGovernanceRepository implements MetricGovernanceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcMetricGovernanceRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<AuditReport> reports(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query("""
            select id,report_key,name,description,sections::text,status,row_version,updated_at
              from project_governance_reports
             where workspace_id=? and space_id=? and status <> 'archived'
             order by updated_at desc,id limit ?
            """, this::mapReport, workspaceId, spaceId, limit);
    }

    @Override
    public Optional<AuditReport> report(UUID workspaceId, UUID spaceId, UUID reportId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                select id,report_key,name,description,sections::text,status,row_version,updated_at
                  from project_governance_reports
                 where workspace_id=? and space_id=? and id=?
                """, this::mapReport, workspaceId, spaceId, reportId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<ReportRun> runs(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query("""
            select id,report_id,report_version,status,result_payload::text,
                   source_fingerprint,run_by,started_at,completed_at
              from project_governance_report_runs
             where workspace_id=? and space_id=?
             order by started_at desc,id limit ?
            """, this::mapRun, workspaceId, spaceId, limit);
    }

    @Override
    public Optional<ReportRun> run(UUID workspaceId, UUID spaceId, UUID runId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                select id,report_id,report_version,status,result_payload::text,
                       source_fingerprint,run_by,started_at,completed_at
                  from project_governance_report_runs
                 where workspace_id=? and space_id=? and id=?
                """, this::mapRun, workspaceId, spaceId, runId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public AuditReport save(
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
    ) {
        int changed = expectedVersion == 0
            ? jdbc.update("""
                insert into project_governance_reports(
                  id,workspace_id,space_id,report_key,name,description,sections,
                  status,row_version,created_by,updated_by
                ) values (?,?,?,?,?,?,?::jsonb,'published',1,?,?)
                on conflict do nothing
                """, reportId, workspaceId, spaceId, reportKey, name, description,
                write(sections), actorId, actorId)
            : jdbc.update("""
                update project_governance_reports
                   set name=?,description=?,sections=?::jsonb,row_version=row_version+1,
                       updated_by=?,updated_at=now()
                 where workspace_id=? and space_id=? and id=? and row_version=?
                   and status <> 'archived'
                """, name, description, write(sections), actorId,
                workspaceId, spaceId, reportId, expectedVersion);
        if (changed != 1) throw versionConflict();
        AuditReport result = report(workspaceId, spaceId, reportId).orElseThrow();
        receipt(workspaceId, spaceId, actorId, "save_report", requestId,
            requestHash, reportId, result);
        return result;
    }

    @Override
    @Transactional
    public ReportRun run(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        AuditReport report,
        GovernanceOverview overview,
        String sourceFingerprint,
        String requestId,
        String requestHash
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into project_governance_report_runs(
              id,workspace_id,space_id,report_id,report_version,status,
              result_payload,source_fingerprint,run_by,started_at,completed_at
            ) values (?,?,?,?,?,'completed',?::jsonb,?,?,now(),now())
            """, id, workspaceId, spaceId, report.id(), report.version(),
            write(overview), sourceFingerprint, actorId);
        ReportRun result = run(workspaceId, spaceId, id).orElseThrow();
        receipt(workspaceId, spaceId, actorId, "run_report", requestId,
            requestHash, id, result);
        return result;
    }

    @Override
    @Transactional
    public ExportReceipt export(
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
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into project_governance_exports(
              id,workspace_id,space_id,run_id,format,row_count,truncated,
              content_hash,exported_by,request_id,request_hash
            ) values (?,?,?,?,?,?,?,?,?,?,?)
            """, id, workspaceId, spaceId, run.id(), format, rows.size(), truncated,
            contentHash, actorId, requestId, requestHash);
        ExportReceipt result = new ExportReceipt(
            id, run.id(), format, rows.size(), truncated, contentHash, rows, Instant.now()
        );
        receipt(workspaceId, spaceId, actorId, "export_report", requestId,
            requestHash, id, result);
        return result;
    }

    private void receipt(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation,
        String requestId, String requestHash, UUID objectId, Object response
    ) {
        jdbc.update("""
            insert into project_governance_commands(
              id,workspace_id,space_id,actor_id,operation,request_id,
              request_hash,object_id,response_payload,status
            ) values (?,?,?,?,?,?,?,?,?::jsonb,'completed')
            """, UUID.randomUUID(), workspaceId, spaceId, actorId, operation,
            requestId, requestHash, objectId, write(response));
    }

    private AuditReport mapReport(ResultSet result, int row) throws SQLException {
        return new AuditReport(
            result.getObject("id", UUID.class),
            result.getString("report_key"),
            result.getString("name"),
            result.getString("description"),
            read(result.getString("sections"), new TypeReference<>() {
            }),
            result.getString("status"),
            result.getLong("row_version"),
            result.getTimestamp("updated_at").toInstant()
        );
    }

    private ReportRun mapRun(ResultSet result, int row) throws SQLException {
        return new ReportRun(
            result.getObject("id", UUID.class),
            result.getObject("report_id", UUID.class),
            result.getLong("report_version"),
            result.getString("status"),
            read(result.getString("result_payload"), GovernanceOverview.class),
            result.getString("source_fingerprint"),
            result.getObject("run_by", UUID.class),
            result.getTimestamp("started_at").toInstant(),
            result.getTimestamp("completed_at").toInstant()
        );
    }

    private RuntimeException versionConflict() {
        return failure("GOVERNANCE_VERSION_CONFLICT", "Governance object changed; refresh before retrying");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
