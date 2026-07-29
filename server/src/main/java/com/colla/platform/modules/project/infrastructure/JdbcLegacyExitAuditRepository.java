package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditFinding;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditObservation;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditSnapshot;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacySurface;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.RemovalDecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcLegacyExitAuditRepository implements LegacyExitAuditRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcLegacyExitAuditRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public LegacyAuditObservation observe(UUID workspaceId) {
        return jdbc.queryForObject("""
            select
              (select count(*) from projects where workspace_id=?) legacy_projects,
              (select count(*) from issues where workspace_id=?) legacy_issues,
              (select count(*) from project_members where workspace_id=?) legacy_members,
              (select count(*) from issue_comments where workspace_id=?) legacy_comments,
              (select count(*) from issue_attachments where workspace_id=?) legacy_attachments,
              (select count(*) from issue_activity_logs where workspace_id=?) legacy_activities,
              (select count(*) from project_legacy_space_maps where workspace_id=? and mapping_status='active') active_space_maps,
              (select count(*) from project_legacy_work_item_maps where workspace_id=? and status='active') active_item_maps,
              (select count(*) from project_work_items where workspace_id=?) canonical_items,
              (select count(*) from projects p where p.workspace_id=? and not exists (
                 select 1 from project_legacy_space_maps m
                  where m.workspace_id=p.workspace_id and m.legacy_project_id=p.id and m.mapping_status='active'
               )) unmapped_projects,
              (select count(*) from issues i where i.workspace_id=? and not exists (
                 select 1 from project_legacy_work_item_maps m
                  where m.workspace_id=i.workspace_id and m.source_type='issue'
                    and m.source_id=i.id and m.status='active'
               )) unmapped_issues,
              (select count(*) from project_legacy_work_item_maps m
                left join project_work_items w on w.workspace_id=m.workspace_id
                 and w.space_id=m.space_id and w.id=m.work_item_id
               where m.workspace_id=? and m.status='active' and w.id is null) dangling_maps,
              (select count(*) from project_work_item_shadow_samples
                where workspace_id=? and outcome<>'match') shadow_drifts,
              (select count(*) from project_work_item_cutovers
                where workspace_id=? and legacy_write_enabled) legacy_write_scopes,
              (select count(*) from project_work_item_cutovers
                where workspace_id=? and read_stage in ('legacy','shadow','canonical_read')) legacy_read_scopes,
              (select count(*) from project_work_item_migration_failures where workspace_id=?) migration_failures,
              (select count(*) from project_work_item_migration_verifications
                where workspace_id=? and status='mismatched') mismatched_verifications,
              md5(concat_ws(':',
                (select count(*) from projects where workspace_id=?),
                (select count(*) from issues where workspace_id=?),
                (select count(*) from project_legacy_work_item_maps where workspace_id=? and status='active'),
                (select count(*) from project_work_item_migration_provenance where workspace_id=?),
                (select count(*) from project_work_item_migration_verifications where workspace_id=?),
                (select count(*) from project_work_item_shadow_samples where workspace_id=?)
              )) source_fingerprint
            """, (rs, rowNum) -> {
                Map<String, Long> totals = new LinkedHashMap<>();
                for (String key : List.of(
                    "legacyProjects", "legacyIssues", "legacyMembers", "legacyComments",
                    "legacyAttachments", "legacyActivities", "activeSpaceMaps", "activeItemMaps",
                    "canonicalItems", "unmappedProjects", "unmappedIssues", "danglingMaps",
                    "shadowDrifts", "legacyWriteScopes", "legacyReadScopes",
                    "migrationFailures", "mismatchedVerifications"
                )) {
                    totals.put(key, rs.getLong(snake(key)));
                }
                return new LegacyAuditObservation(Map.copyOf(totals), rs.getString("source_fingerprint"));
            }, repeat(workspaceId, 23));
    }

    @Override
    @Transactional
    public LegacyAuditSnapshot insertSnapshot(
        UUID workspaceId,
        String inventoryVersion,
        LegacyAuditObservation observation,
        List<LegacySurface> surfaces,
        List<LegacyAuditFinding> findings,
        UUID actorId
    ) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String status = findings.stream().anyMatch(value -> "blocking".equals(value.severity()))
            ? "blocked" : "ready";
        jdbc.update("""
            insert into project_legacy_audit_snapshots(
              id,workspace_id,inventory_version,status,source_fingerprint,
              totals,surfaces,generated_by,generated_at
            ) values (?,?,?,?,?,?::jsonb,?::jsonb,?,?)
            """, id, workspaceId, inventoryVersion, status, observation.sourceFingerprint(),
            json(observation.totals()), json(surfaces), actorId, Timestamp.from(now));
        for (LegacyAuditFinding finding : findings) {
            jdbc.update("""
                insert into project_legacy_audit_findings(
                  id,workspace_id,snapshot_id,finding_key,category,severity,status,
                  affected_count,safe_detail,recorded_at
                ) values (?,?,?,?,?,?,?, ?,?::jsonb,?)
                """, finding.id(), workspaceId, id, finding.key(), finding.category(),
                finding.severity(), finding.status(), finding.affectedCount(),
                json(finding.safeDetail()), Timestamp.from(finding.recordedAt()));
        }
        return findSnapshot(workspaceId, id).orElseThrow();
    }

    @Override
    public Optional<LegacyAuditSnapshot> findSnapshot(UUID workspaceId, UUID snapshotId) {
        return jdbc.query("""
            select * from project_legacy_audit_snapshots
             where workspace_id=? and id=?
            """, this::snapshot, workspaceId, snapshotId).stream().findFirst()
            .map(value -> withChildren(value, workspaceId));
    }

    @Override
    public List<LegacyAuditSnapshot> listSnapshots(UUID workspaceId, int limit) {
        return jdbc.query("""
            select * from project_legacy_audit_snapshots
             where workspace_id=?
             order by generated_at desc,id desc
             limit ?
            """, this::snapshot, workspaceId, limit).stream()
            .map(value -> withChildren(value, workspaceId))
            .toList();
    }

    @Override
    public Optional<RemovalDecision> findDecisionByRequest(UUID workspaceId, String requestId) {
        return jdbc.query("""
            select * from project_legacy_removal_decisions
             where workspace_id=? and request_id=?
            """, this::decision, workspaceId, requestId).stream().findFirst();
    }

    @Override
    public RemovalDecision insertDecision(
        UUID workspaceId,
        UUID snapshotId,
        String surfaceKey,
        String decision,
        String reason,
        String requestId,
        String requestHash,
        UUID actorId
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into project_legacy_removal_decisions(
              id,workspace_id,snapshot_id,surface_key,decision,reason,
              request_id,request_hash,decided_by,decided_at
            ) values (?,?,?,?,?,?,?,?,?,now())
            """, id, workspaceId, snapshotId, surfaceKey, decision, reason,
            requestId, requestHash, actorId);
        return findDecisionByRequest(workspaceId, requestId).orElseThrow();
    }

    private LegacyAuditSnapshot withChildren(LegacyAuditSnapshot snapshot, UUID workspaceId) {
        List<LegacyAuditFinding> findings = jdbc.query("""
            select * from project_legacy_audit_findings
             where workspace_id=? and snapshot_id=?
             order by severity desc,finding_key
            """, this::finding, workspaceId, snapshot.id());
        List<RemovalDecision> decisions = jdbc.query("""
            select * from project_legacy_removal_decisions
             where workspace_id=? and snapshot_id=?
             order by decided_at,id
            """, this::decision, workspaceId, snapshot.id());
        return new LegacyAuditSnapshot(
            snapshot.id(), snapshot.workspaceId(), snapshot.inventoryVersion(), snapshot.status(),
            snapshot.sourceFingerprint(), snapshot.totals(), snapshot.surfaces(),
            findings, decisions, snapshot.generatedBy(), snapshot.generatedAt()
        );
    }

    private LegacyAuditSnapshot snapshot(ResultSet rs, int rowNum) throws SQLException {
        return new LegacyAuditSnapshot(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getString("inventory_version"),
            rs.getString("status"),
            rs.getString("source_fingerprint"),
            read(rs.getString("totals"), new TypeReference<>() {}),
            read(rs.getString("surfaces"), new TypeReference<>() {}),
            List.of(),
            List.of(),
            rs.getObject("generated_by", UUID.class),
            rs.getTimestamp("generated_at").toInstant()
        );
    }

    private LegacyAuditFinding finding(ResultSet rs, int rowNum) throws SQLException {
        return new LegacyAuditFinding(
            rs.getObject("id", UUID.class), rs.getString("finding_key"),
            rs.getString("category"), rs.getString("severity"), rs.getString("status"),
            rs.getLong("affected_count"),
            read(rs.getString("safe_detail"), new TypeReference<>() {}),
            rs.getTimestamp("recorded_at").toInstant()
        );
    }

    private RemovalDecision decision(ResultSet rs, int rowNum) throws SQLException {
        return new RemovalDecision(
            rs.getObject("id", UUID.class),
            rs.getObject("snapshot_id", UUID.class),
            rs.getString("surface_key"),
            rs.getString("decision"),
            rs.getString("reason"),
            rs.getString("request_id"),
            rs.getString("request_hash"),
            rs.getObject("decided_by", UUID.class),
            rs.getTimestamp("decided_at").toInstant(),
            false
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode legacy audit evidence", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid legacy audit evidence", exception);
        }
    }

    private Object[] repeat(UUID workspaceId, int count) {
        Object[] values = new Object[count];
        java.util.Arrays.fill(values, workspaceId);
        return values;
    }

    private String snake(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
