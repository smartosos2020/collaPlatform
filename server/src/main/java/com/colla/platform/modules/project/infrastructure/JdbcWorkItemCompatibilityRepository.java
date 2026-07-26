package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.CutoverState;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyProfile;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyWorkItemMap;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.ReadStage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemCompatibilityRepository implements WorkItemCompatibilityRepository {
    private final JdbcTemplate jdbc;

    public JdbcWorkItemCompatibilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LegacyProfile profile(UUID workspaceId) {
        return jdbc.queryForObject("""
            select
              coalesce(max(watermark), timestamp with time zone 'epoch') as watermark,
              count(*) filter (where kind = 'project') as projects,
              count(*) filter (where kind = 'issue') as issues,
              count(*) filter (where kind = 'member') as members,
              count(*) filter (where kind = 'comment') as comments,
              count(*) filter (where kind = 'attachment') as attachments,
              count(*) filter (where kind = 'activity') as activities,
              count(*) filter (where kind = 'relation') as relations,
              (select count(*) from issues i left join projects p on p.id=i.project_id
                 where i.workspace_id=? and (p.id is null or p.workspace_id<>i.workspace_id)) as orphan_issues,
              (select count(*) from issue_comments c left join issues i on i.id=c.issue_id
                 where c.workspace_id=? and (i.id is null or i.workspace_id<>c.workspace_id)) as orphan_comments,
              (select count(*) from issue_attachments a left join issues i on i.id=a.issue_id
                 where a.workspace_id=? and (i.id is null or i.workspace_id<>a.workspace_id)) as orphan_attachments,
              (select count(*) from issue_relations r left join issues i on i.id=r.issue_id
                 where r.workspace_id=? and (i.id is null or i.workspace_id<>r.workspace_id)) as cross_refs,
              md5(coalesce(string_agg(kind||':'||id||':'||watermark, ',' order by kind,id), '')) as fingerprint
            from (
              select 'project' kind,id::text,updated_at watermark from projects where workspace_id=?
              union all select 'issue',id::text,updated_at from issues where workspace_id=?
              union all select 'member',id::text,joined_at from project_members where workspace_id=?
              union all select 'comment',id::text,created_at from issue_comments where workspace_id=?
              union all select 'attachment',id::text,created_at from issue_attachments where workspace_id=?
              union all select 'activity',id::text,created_at from issue_activity_logs where workspace_id=?
              union all select 'relation',id::text,created_at from issue_relations where workspace_id=?
            ) source
            """, (rs, rowNum) -> new LegacyProfile(
                workspaceId,
                rs.getTimestamp("watermark").toInstant(),
                rs.getLong("projects"),
                rs.getLong("issues"),
                rs.getLong("members"),
                rs.getLong("comments"),
                rs.getLong("attachments"),
                rs.getLong("activities"),
                rs.getLong("relations"),
                rs.getLong("orphan_issues"),
                rs.getLong("orphan_comments"),
                rs.getLong("orphan_attachments"),
                rs.getLong("cross_refs"),
                rs.getString("fingerprint")
            ), workspaceId, workspaceId, workspaceId, workspaceId,
            workspaceId, workspaceId, workspaceId, workspaceId, workspaceId, workspaceId, workspaceId);
    }

    @Override
    public Optional<LegacyWorkItemMap> findMap(UUID workspaceId, String sourceType, UUID sourceId) {
        return jdbc.query("""
            select source_type,source_id,source_project_id,space_id,work_item_id,
                   identity_decision,source_fingerprint,status
              from project_legacy_work_item_maps
             where workspace_id=? and source_type=? and source_id=? and status='active'
            """, this::map, workspaceId, sourceType, sourceId).stream().findFirst();
    }

    @Override
    public Optional<CutoverState> findCutover(UUID workspaceId, UUID spaceId) {
        var scoped = jdbc.query("""
            select space_id,read_stage,legacy_write_enabled,kill_switch_enabled,version
              from project_work_item_cutovers
             where workspace_id=? and space_id is not distinct from ?
            """, this::cutover, workspaceId, spaceId);
        if (!scoped.isEmpty()) {
            return Optional.of(scoped.getFirst());
        }
        if (spaceId == null) {
            return Optional.empty();
        }
        return jdbc.query("""
            select space_id,read_stage,legacy_write_enabled,kill_switch_enabled,version
              from project_work_item_cutovers
             where workspace_id=? and space_id is null
            """, this::cutover, workspaceId).stream().findFirst();
    }

    @Override
    public Optional<UUID> findLegacyProjectSpace(UUID workspaceId, UUID projectId) {
        return jdbc.query("""
            select space_id from project_legacy_space_maps
             where workspace_id=? and legacy_project_id=? and mapping_status='active'
            """, (rs, rowNum) -> rs.getObject("space_id", UUID.class), workspaceId, projectId)
            .stream().findFirst();
    }

    @Override
    public Optional<UUID> findIssueProject(UUID workspaceId, UUID issueId) {
        return jdbc.query("""
            select project_id from issues where workspace_id=? and id=?
            """, (rs, rowNum) -> rs.getObject("project_id", UUID.class), workspaceId, issueId)
            .stream().findFirst();
    }

    @Override
    public CutoverState changeCutover(
        UUID workspaceId,
        UUID spaceId,
        String readStage,
        boolean legacyWriteEnabled,
        boolean killSwitchEnabled,
        long expectedVersion,
        UUID actorId
    ) {
        int updated = jdbc.update("""
            update project_work_item_cutovers
               set read_stage=?, legacy_write_enabled=?, kill_switch_enabled=?,
                   version=version+1, changed_by=?, changed_at=now()
             where workspace_id=? and space_id is not distinct from ? and version=?
            """, readStage, legacyWriteEnabled, killSwitchEnabled, actorId,
            workspaceId, spaceId, expectedVersion);
        if (updated == 0 && expectedVersion == 0) {
            try {
                jdbc.update("""
                    insert into project_work_item_cutovers(
                      id,workspace_id,space_id,read_stage,legacy_write_enabled,
                      kill_switch_enabled,version,changed_by,changed_at
                    ) values (?,?,?,?,?,?,1,?,now())
                    """, UUID.randomUUID(), workspaceId, spaceId, readStage,
                    legacyWriteEnabled, killSwitchEnabled, actorId);
            } catch (org.springframework.dao.DuplicateKeyException exception) {
                throw new IllegalStateException("CUTOVER_VERSION_CONFLICT", exception);
            }
        } else if (updated == 0) {
            throw new IllegalStateException("CUTOVER_VERSION_CONFLICT");
        }
        return findCutover(workspaceId, spaceId).orElseThrow();
    }

    @Override
    public void recordShadowSample(
        UUID workspaceId,
        UUID spaceId,
        String sourceType,
        UUID sourceId,
        String primarySource,
        String legacyFingerprint,
        String canonicalFingerprint,
        String outcome,
        int primaryLatencyMs,
        Integer shadowLatencyMs
    ) {
        jdbc.update("""
            insert into project_work_item_shadow_samples(
              id,workspace_id,space_id,source_type,source_id,primary_source,
              legacy_fingerprint,canonical_fingerprint,outcome,
              primary_latency_ms,shadow_latency_ms,sampled_at
            ) values (?,?,?,?,?,?,?,?,?,?,?,now())
            """, UUID.randomUUID(), workspaceId, spaceId, sourceType, sourceId, primarySource,
            legacyFingerprint, canonicalFingerprint, outcome, primaryLatencyMs, shadowLatencyMs);
    }

    private LegacyWorkItemMap map(ResultSet rs, int rowNum) throws SQLException {
        return new LegacyWorkItemMap(
            rs.getString("source_type"),
            rs.getObject("source_id", UUID.class),
            rs.getObject("source_project_id", UUID.class),
            rs.getObject("space_id", UUID.class),
            rs.getObject("work_item_id", UUID.class),
            rs.getString("identity_decision"),
            rs.getString("source_fingerprint"),
            rs.getString("status")
        );
    }

    private CutoverState cutover(ResultSet rs, int rowNum) throws SQLException {
        return new CutoverState(
            rs.getObject("space_id", UUID.class),
            ReadStage.parse(rs.getString("read_stage")),
            rs.getBoolean("legacy_write_enabled"),
            rs.getBoolean("kill_switch_enabled"),
            rs.getLong("version")
        );
    }
}
