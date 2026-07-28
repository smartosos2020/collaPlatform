package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncConflict;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRule;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRuleVersion;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRun;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCrossSpaceSyncRepository implements CrossSpaceSyncRepository {
    private static final String RULE_SELECT = """
        select r.id,r.grant_id,r.policy_id,r.canonical_relation_id,
               r.source_space_id,r.target_space_id,r.name,r.status,r.current_version,
               r.source_confirmed_by,r.target_confirmed_by,r.updated_by,r.updated_at,
               v.id version_id,v.direction,v.trigger_kind,v.field_mappings,
               v.state_mappings,v.conflict_strategy,v.config_hash,
               v.created_by version_created_by,v.created_at version_created_at
          from project_cross_space_sync_rules r
          join project_cross_space_sync_rule_versions v
            on v.workspace_id=r.workspace_id and v.rule_id=r.id
           and v.version_number=r.current_version
        """;
    private static final String RUN_COLUMNS = """
        id,rule_id,rule_version_id,rule_version_number,canonical_relation_id,
        direction,origin_id,causation_id,chain_depth,input_fingerprint,
        source_space_id,source_work_item_id,source_version,
        target_space_id,target_work_item_id,target_version,status,retry_count,
        fencing_token,result_target_version,failure_code,created_at,completed_at
        """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcCrossSpaceSyncRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<SyncRule> listRules(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            RULE_SELECT + """
                 where r.workspace_id=? and (r.source_space_id=? or r.target_space_id=?)
                 order by r.updated_at desc,r.id limit ?
                """,
            this::mapRule, workspaceId, spaceId, spaceId, limit
        );
    }

    @Override
    public List<SyncRun> listRuns(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            "select " + RUN_COLUMNS
                + " from project_cross_space_sync_runs"
                + " where workspace_id=? and (source_space_id=? or target_space_id=?)"
                + " order by created_at desc,id limit ?",
            this::mapRun, workspaceId, spaceId, spaceId, limit
        );
    }

    @Override
    public List<SyncConflict> listConflicts(
        UUID workspaceId, UUID spaceId, int limit
    ) {
        return jdbc.query("""
            select c.id,c.run_id,c.conflict_kind,c.source_fingerprint,
                   c.target_fingerprint,c.status,c.version,c.resolution,
                   c.created_at,c.resolved_at
              from project_cross_space_sync_conflicts c
              join project_cross_space_sync_runs r
                on r.workspace_id=c.workspace_id and r.id=c.run_id
             where c.workspace_id=? and (r.source_space_id=? or r.target_space_id=?)
             order by c.created_at desc,c.id limit ?
            """, this::mapConflict, workspaceId, spaceId, spaceId, limit);
    }

    @Override
    public List<SyncStep> listSteps(UUID workspaceId, UUID runId, int limit) {
        return jdbc.query("""
            select step_index,step_kind,mapping_key,input_fingerprint,
                   command_request_id,status,before_version,after_version,error_code
              from project_cross_space_sync_steps
             where workspace_id=? and run_id=?
             order by step_index limit ?
            """, this::mapStep, workspaceId, runId, limit);
    }

    @Override
    public Optional<SyncRule> findRule(
        UUID workspaceId, UUID ruleId, boolean lock
    ) {
        return jdbc.query(
            RULE_SELECT + " where r.workspace_id=? and r.id=?"
                + (lock ? " for update of r" : ""),
            this::mapRule, workspaceId, ruleId
        ).stream().findFirst();
    }

    @Override
    public Optional<SyncRun> findRun(UUID workspaceId, UUID runId) {
        return jdbc.query(
            "select " + RUN_COLUMNS
                + " from project_cross_space_sync_runs where workspace_id=? and id=?",
            this::mapRun, workspaceId, runId
        ).stream().findFirst();
    }

    @Override
    public Optional<SyncConflict> findConflict(
        UUID workspaceId, UUID conflictId, boolean lock
    ) {
        return jdbc.query("""
            select id,run_id,conflict_kind,source_fingerprint,target_fingerprint,
                   status,version,resolution,created_at,resolved_at
              from project_cross_space_sync_conflicts
             where workspace_id=? and id=?
            """ + (lock ? " for update" : ""),
            this::mapConflict, workspaceId, conflictId
        ).stream().findFirst();
    }

    @Override
    public SyncRule createRule(NewRule rule) {
        UUID id = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
            insert into project_cross_space_sync_rules(
                id,workspace_id,grant_id,policy_id,canonical_relation_id,
                source_space_id,target_space_id,name,status,current_version,
                created_by,updated_by
            ) values (?,?,?,?,?,?,?,?,'draft',1,?,?)
            """,
            id, rule.workspaceId(), rule.grantId(), rule.policyId(), rule.relationId(),
            rule.sourceSpaceId(), rule.targetSpaceId(), rule.name(),
            rule.actorId(), rule.actorId()
        );
        insertVersion(
            versionId, rule.workspaceId(), id, 1, rule.actorId(),
            rule.direction(), rule.trigger(), rule.fieldMappings(), rule.stateMappings(),
            rule.conflictStrategy(), rule.configHash()
        );
        return findRule(rule.workspaceId(), id, false).orElseThrow();
    }

    @Override
    public SyncRule reviseRule(NewVersion version, long expectedVersion) {
        int next = Math.toIntExact(expectedVersion + 1);
        int updated = jdbc.update("""
            update project_cross_space_sync_rules
               set name=?,status='draft',current_version=?,
                   source_confirmed_by=null,source_confirmed_at=null,
                   target_confirmed_by=null,target_confirmed_at=null,
                   updated_by=?,updated_at=now()
             where workspace_id=? and id=? and current_version=?
               and status in ('draft','active','paused')
            """,
            version.name(), next, version.actorId(),
            version.workspaceId(), version.ruleId(), expectedVersion
        );
        if (updated != 1) {
            throw new IllegalStateException("sync rule version conflict");
        }
        insertVersion(
            UUID.randomUUID(), version.workspaceId(), version.ruleId(), next,
            version.actorId(), version.direction(), version.trigger(),
            version.fieldMappings(), version.stateMappings(),
            version.conflictStrategy(), version.configHash()
        );
        return findRule(version.workspaceId(), version.ruleId(), false).orElseThrow();
    }

    @Override
    public int transitionRule(
        UUID workspaceId, UUID ruleId, long expectedVersion,
        UUID actorId, String action, String party
    ) {
        String sql = switch (action) {
            case "request" -> """
                update project_cross_space_sync_rules
                   set status='requested',source_confirmed_by=null,source_confirmed_at=null,
                       target_confirmed_by=null,target_confirmed_at=null,
                       updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and current_version=? and status='draft'
                """;
            case "confirm" -> "source".equals(party) ? """
                update project_cross_space_sync_rules
                   set source_confirmed_by=?,source_confirmed_at=now(),
                       status=case when target_confirmed_by is not null then 'active' else status end,
                       updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and current_version=? and status='requested'
                   and source_confirmed_by is null
                """ : """
                update project_cross_space_sync_rules
                   set target_confirmed_by=?,target_confirmed_at=now(),
                       status=case when source_confirmed_by is not null then 'active' else status end,
                       updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and current_version=? and status='requested'
                   and target_confirmed_by is null
                """;
            case "pause" -> """
                update project_cross_space_sync_rules
                   set status='paused',updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and current_version=? and status='active'
                """;
            case "resume" -> """
                update project_cross_space_sync_rules
                   set status='active',updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and current_version=? and status='paused'
                """;
            case "revoke" -> """
                update project_cross_space_sync_rules
                   set status='revoked',revoked_at=now(),updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and current_version=?
                   and status in ('requested','active','paused')
                """;
            case "archive" -> """
                update project_cross_space_sync_rules
                   set status='archived',archived_at=now(),updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and current_version=? and status='revoked'
                """;
            default -> throw new IllegalArgumentException("unsupported sync rule action");
        };
        if ("confirm".equals(action)) {
            return jdbc.update(sql, actorId, actorId, workspaceId, ruleId, expectedVersion);
        }
        return jdbc.update(sql, actorId, workspaceId, ruleId, expectedVersion);
    }

    @Override
    public SyncRun createRun(NewRun run) {
        jdbc.update("""
            insert into project_cross_space_sync_runs(
                id,workspace_id,rule_id,rule_version_id,rule_version_number,
                canonical_relation_id,direction,origin_id,causation_id,chain_depth,
                input_fingerprint,source_space_id,source_work_item_id,source_version,
                target_space_id,target_work_item_id,target_version,status,
                retry_count,fencing_token,created_by
            ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'running',0,1,?)
            """,
            run.id(), run.workspaceId(), run.rule().id(),
            run.rule().configuration().id(), run.rule().currentVersion(),
            run.rule().canonicalRelationId(), run.direction(), run.originId(),
            run.causationId(), run.chainDepth(), run.inputFingerprint(),
            run.sourceSpaceId(), run.sourceWorkItemId(), run.sourceVersion(),
            run.targetSpaceId(), run.targetWorkItemId(), run.targetVersion(),
            run.actorId()
        );
        return findRun(run.workspaceId(), run.id()).orElseThrow();
    }

    @Override
    public Optional<SyncRun> findRunByOrigin(
        UUID workspaceId, UUID ruleId, String direction,
        String originId, String fingerprint
    ) {
        return jdbc.query(
            "select " + RUN_COLUMNS
                + " from project_cross_space_sync_runs"
                + " where workspace_id=? and rule_id=? and direction=?"
                + " and origin_id=? and input_fingerprint=?",
            this::mapRun, workspaceId, ruleId, direction, originId, fingerprint
        ).stream().findFirst();
    }

    @Override
    public void appendStep(UUID workspaceId, UUID runId, SyncStep step) {
        jdbc.update("""
            insert into project_cross_space_sync_steps(
                id,workspace_id,run_id,step_index,step_kind,mapping_key,
                input_fingerprint,command_request_id,status,before_version,
                after_version,error_code
            ) values (?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            UUID.randomUUID(), workspaceId, runId, step.index(), step.kind(),
            step.mappingKey(), step.inputFingerprint(), step.commandRequestId(),
            step.status(), step.beforeVersion(), step.afterVersion(), step.errorCode()
        );
    }

    @Override
    public void finishRun(
        UUID workspaceId, UUID runId, String status,
        Long resultTargetVersion, String failureCode
    ) {
        jdbc.update("""
            update project_cross_space_sync_runs
               set status=?,result_target_version=?,failure_code=?,completed_at=now()
             where workspace_id=? and id=? and status='running'
                or (workspace_id=? and id=? and status in ('conflict','failed'))
            """, status, resultTargetVersion, failureCode,
            workspaceId, runId, workspaceId, runId);
    }

    @Override
    public SyncConflict createConflict(
        UUID workspaceId, UUID runId, String kind,
        String sourceFingerprint, String targetFingerprint
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into project_cross_space_sync_conflicts(
                id,workspace_id,run_id,conflict_kind,source_fingerprint,target_fingerprint,
                status,version
            ) values (?,?,?,?,?,?,'open',1)
            """, id, workspaceId, runId, kind, sourceFingerprint, targetFingerprint);
        return findConflict(workspaceId, id, false).orElseThrow();
    }

    @Override
    public int resolveConflict(
        UUID workspaceId, UUID conflictId, long expectedVersion,
        UUID actorId, String resolution, String reasonHash
    ) {
        String status = switch (resolution) {
            case "compensate" -> "compensated";
            case "dead_letter" -> "dead_letter";
            default -> "resolved";
        };
        return jdbc.update("""
            update project_cross_space_sync_conflicts
               set status=?,version=version+1,resolution=?,resolution_reason_hash=?,
                   resolved_by=?,resolved_at=now()
             where workspace_id=? and id=? and version=? and status='open'
            """,
            status, resolution, reasonHash, actorId,
            workspaceId, conflictId, expectedVersion
        );
    }

    @Override
    public Optional<CommandReceipt> findReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId
    ) {
        return jdbc.query("""
            select request_hash,response_payload
              from project_cross_space_sync_receipts
             where workspace_id=? and actor_id=? and operation=? and request_id=?
            """, (rs, row) -> new CommandReceipt(
                rs.getString("request_hash"), json(rs.getString("response_payload"))
            ), workspaceId, actorId, operation, requestId).stream().findFirst();
    }

    @Override
    public void saveReceipt(
        UUID workspaceId, UUID actorId, String operation,
        String requestId, String requestHash, JsonNode response
    ) {
        jdbc.update("""
            insert into project_cross_space_sync_receipts(
                id,workspace_id,actor_id,operation,request_id,request_hash,response_payload
            ) values (?,?,?,?,?,?,?::jsonb)
            """,
            UUID.randomUUID(), workspaceId, actorId, operation,
            requestId, requestHash, response.toString()
        );
    }

    private void insertVersion(
        UUID id, UUID workspaceId, UUID ruleId, int version, UUID actorId,
        String direction, String trigger, JsonNode fields, JsonNode states,
        String strategy, String hash
    ) {
        jdbc.update("""
            insert into project_cross_space_sync_rule_versions(
                id,workspace_id,rule_id,version_number,direction,trigger_kind,
                field_mappings,state_mappings,conflict_strategy,config_hash,created_by
            ) values (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?)
            """,
            id, workspaceId, ruleId, version, direction, trigger,
            fields.toString(), states.toString(), strategy, hash, actorId
        );
    }

    private SyncRule mapRule(ResultSet rs, int row) throws SQLException {
        SyncRuleVersion version = new SyncRuleVersion(
            uuid(rs, "version_id"), rs.getInt("current_version"),
            rs.getString("direction"), rs.getString("trigger_kind"),
            json(rs.getString("field_mappings")), json(rs.getString("state_mappings")),
            rs.getString("conflict_strategy"), rs.getString("config_hash"),
            uuid(rs, "version_created_by"),
            rs.getTimestamp("version_created_at").toInstant()
        );
        return new SyncRule(
            uuid(rs, "id"), uuid(rs, "grant_id"), uuid(rs, "policy_id"),
            uuid(rs, "canonical_relation_id"),
            uuid(rs, "source_space_id"), uuid(rs, "target_space_id"),
            rs.getString("name"), rs.getString("status"),
            rs.getInt("current_version"),
            uuid(rs, "source_confirmed_by"), uuid(rs, "target_confirmed_by"),
            version, uuid(rs, "updated_by"), rs.getTimestamp("updated_at").toInstant()
        );
    }

    private SyncRun mapRun(ResultSet rs, int row) throws SQLException {
        Long resultVersion = (Long) rs.getObject("result_target_version");
        return new SyncRun(
            uuid(rs, "id"), uuid(rs, "rule_id"), uuid(rs, "rule_version_id"),
            rs.getInt("rule_version_number"), uuid(rs, "canonical_relation_id"),
            rs.getString("direction"), rs.getString("origin_id"),
            rs.getString("causation_id"), rs.getInt("chain_depth"),
            rs.getString("input_fingerprint"),
            uuid(rs, "source_space_id"), uuid(rs, "source_work_item_id"),
            rs.getLong("source_version"),
            uuid(rs, "target_space_id"), uuid(rs, "target_work_item_id"),
            rs.getLong("target_version"), rs.getString("status"),
            rs.getInt("retry_count"), rs.getLong("fencing_token"),
            resultVersion, rs.getString("failure_code"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("completed_at") == null
                ? null : rs.getTimestamp("completed_at").toInstant()
        );
    }

    private SyncStep mapStep(ResultSet rs, int row) throws SQLException {
        Long after = (Long) rs.getObject("after_version");
        return new SyncStep(
            rs.getInt("step_index"), rs.getString("step_kind"),
            rs.getString("mapping_key"), rs.getString("input_fingerprint"),
            rs.getString("command_request_id"), rs.getString("status"),
            rs.getLong("before_version"), after,
            rs.getString("error_code")
        );
    }

    private SyncConflict mapConflict(ResultSet rs, int row) throws SQLException {
        return new SyncConflict(
            uuid(rs, "id"), uuid(rs, "run_id"), rs.getString("conflict_kind"),
            rs.getString("source_fingerprint"), rs.getString("target_fingerprint"),
            rs.getString("status"), rs.getLong("version"),
            rs.getString("resolution"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("resolved_at") == null
                ? null : rs.getTimestamp("resolved_at").toInstant()
        );
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
