package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CrossSpaceRelationPolicy;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntent;
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
public class JdbcCrossSpaceRelationRepository
    implements CrossSpaceRelationRepository {
    private static final String POLICY_COLUMNS = """
        id,grant_id,source_space_id,target_space_id,relation_key,direction,
        source_type_id,source_version_id,source_config_hash,
        target_type_id,target_version_id,target_config_hash,
        status,version,source_confirmed_by,target_confirmed_by,updated_by,updated_at
        """;
    private static final String INTENT_COLUMNS = """
        id,policy_id,policy_version,source_space_id,source_work_item_id,
        source_expected_version,target_space_id,target_work_item_id,
        target_expected_version,status,version,source_confirmed_by,
        target_confirmed_by,canonical_relation_id,updated_at
        """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcCrossSpaceRelationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<CrossSpaceRelationPolicy> listPolicies(
        UUID workspaceId, UUID spaceId, int limit
    ) {
        return jdbc.query(
            "select " + POLICY_COLUMNS
                + " from project_cross_space_relation_policies"
                + " where workspace_id=? and (source_space_id=? or target_space_id=?)"
                + " order by updated_at desc,id limit ?",
            this::mapPolicy, workspaceId, spaceId, spaceId, limit
        );
    }

    @Override
    public List<LinkIntent> listIntents(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            "select " + INTENT_COLUMNS
                + " from project_cross_space_link_intents"
                + " where workspace_id=? and (source_space_id=? or target_space_id=?)"
                + " order by updated_at desc,id limit ?",
            this::mapIntent, workspaceId, spaceId, spaceId, limit
        );
    }

    @Override
    public Optional<CrossSpaceRelationPolicy> findPolicy(
        UUID workspaceId, UUID policyId, boolean lock
    ) {
        return jdbc.query(
            "select " + POLICY_COLUMNS
                + " from project_cross_space_relation_policies"
                + " where workspace_id=? and id=?"
                + (lock ? " for update" : ""),
            this::mapPolicy, workspaceId, policyId
        ).stream().findFirst();
    }

    @Override
    public Optional<LinkIntent> findIntent(
        UUID workspaceId, UUID intentId, boolean lock
    ) {
        return jdbc.query(
            "select " + INTENT_COLUMNS
                + " from project_cross_space_link_intents"
                + " where workspace_id=? and id=?"
                + (lock ? " for update" : ""),
            this::mapIntent, workspaceId, intentId
        ).stream().findFirst();
    }

    @Override
    public CrossSpaceRelationPolicy createPolicy(
        UUID workspaceId,
        UUID actorId,
        UUID grantId,
        UUID sourceSpaceId,
        UUID targetSpaceId,
        String relationKey,
        String direction,
        UUID sourceTypeId,
        UUID sourceVersionId,
        String sourceConfigHash,
        UUID targetTypeId,
        UUID targetVersionId,
        String targetConfigHash
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into project_cross_space_relation_policies(
                id,workspace_id,grant_id,source_space_id,target_space_id,
                relation_key,direction,source_type_id,source_version_id,source_config_hash,
                target_type_id,target_version_id,target_config_hash,status,version,
                created_by,updated_by
            ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,'draft',1,?,?)
            """,
            id, workspaceId, grantId, sourceSpaceId, targetSpaceId,
            relationKey, direction, sourceTypeId, sourceVersionId, sourceConfigHash,
            targetTypeId, targetVersionId, targetConfigHash, actorId, actorId
        );
        return findPolicy(workspaceId, id, false).orElseThrow();
    }

    @Override
    public int transitionPolicy(
        UUID workspaceId,
        UUID policyId,
        long expectedVersion,
        UUID actorId,
        String action,
        String party
    ) {
        String sql = switch (action) {
            case "request" -> """
                update project_cross_space_relation_policies
                   set status='requested',version=version+1,
                       source_confirmed_by=null,source_confirmed_at=null,
                       target_confirmed_by=null,target_confirmed_at=null,
                       updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and version=? and status='draft'
                """;
            case "confirm" -> "source".equals(party) ? """
                update project_cross_space_relation_policies
                   set source_confirmed_by=?,source_confirmed_at=now(),
                       status=case when target_confirmed_by is not null then 'active' else status end,
                       version=version+1,updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and version=? and status='requested'
                   and source_confirmed_by is null
                """ : """
                update project_cross_space_relation_policies
                   set target_confirmed_by=?,target_confirmed_at=now(),
                       status=case when source_confirmed_by is not null then 'active' else status end,
                       version=version+1,updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and version=? and status='requested'
                   and target_confirmed_by is null
                """;
            case "pause" -> """
                update project_cross_space_relation_policies
                   set status='paused',version=version+1,updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and version=? and status='active'
                """;
            case "resume" -> """
                update project_cross_space_relation_policies
                   set status='active',version=version+1,updated_by=?,updated_at=now()
                 where workspace_id=? and id=? and version=? and status='paused'
                """;
            case "revoke" -> """
                update project_cross_space_relation_policies
                   set status='revoked',version=version+1,updated_by=?,updated_at=now(),
                       revoked_at=now()
                 where workspace_id=? and id=? and version=?
                   and status in ('requested','active','paused')
                """;
            case "archive" -> """
                update project_cross_space_relation_policies
                   set status='archived',version=version+1,updated_by=?,updated_at=now(),
                       archived_at=now()
                 where workspace_id=? and id=? and version=? and status='revoked'
                """;
            default -> throw new IllegalArgumentException("unsupported relation policy action");
        };
        if ("confirm".equals(action)) {
            return jdbc.update(
                sql, actorId, actorId, workspaceId, policyId, expectedVersion
            );
        }
        return jdbc.update(sql, actorId, workspaceId, policyId, expectedVersion);
    }

    @Override
    public LinkIntent createIntent(
        UUID workspaceId,
        UUID actorId,
        CrossSpaceRelationPolicy policy,
        UUID sourceWorkItemId,
        long sourceVersion,
        UUID targetWorkItemId,
        long targetVersion
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into project_cross_space_link_intents(
                id,workspace_id,policy_id,policy_version,
                source_space_id,source_work_item_id,source_expected_version,
                target_space_id,target_work_item_id,target_expected_version,
                status,version,source_confirmed_by
            ) values (?,?,?,?,?,?,?,?,?,?,'requested',1,?)
            """,
            id, workspaceId, policy.id(), policy.version(),
            policy.sourceSpaceId(), sourceWorkItemId, sourceVersion,
            policy.targetSpaceId(), targetWorkItemId, targetVersion, actorId
        );
        return findIntent(workspaceId, id, false).orElseThrow();
    }

    @Override
    public int completeIntent(
        UUID workspaceId,
        UUID intentId,
        long expectedVersion,
        UUID actorId,
        String action,
        UUID relationId,
        String reasonHash
    ) {
        if ("accept".equals(action)) {
            return jdbc.update("""
                update project_cross_space_link_intents
                   set status='linked',version=version+1,target_confirmed_by=?,
                       target_confirmed_at=now(),canonical_relation_id=?,updated_at=now()
                 where workspace_id=? and id=? and version=? and status='requested'
                """,
                actorId, relationId, workspaceId, intentId, expectedVersion
            );
        }
        String status = "reject".equals(action) ? "rejected" : "cancelled";
        return jdbc.update("""
            update project_cross_space_link_intents
               set status=?,version=version+1,reason_hash=?,updated_at=now()
             where workspace_id=? and id=? and version=? and status='requested'
            """,
            status, reasonHash, workspaceId, intentId, expectedVersion
        );
    }

    @Override
    public Optional<CommandReceipt> findReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId
    ) {
        return jdbc.query("""
            select request_hash,response_payload
              from project_cross_space_relation_receipts
             where workspace_id=? and actor_id=? and operation=? and request_id=?
            """, (rs, row) -> new CommandReceipt(
                rs.getString("request_hash"), json(rs.getString("response_payload"))
            ), workspaceId, actorId, operation, requestId).stream().findFirst();
    }

    @Override
    public void saveReceipt(
        UUID workspaceId,
        UUID actorId,
        String operation,
        String requestId,
        String requestHash,
        JsonNode response
    ) {
        jdbc.update("""
            insert into project_cross_space_relation_receipts(
                id,workspace_id,actor_id,operation,request_id,request_hash,response_payload
            ) values (?,?,?,?,?,?,?::jsonb)
            """,
            UUID.randomUUID(), workspaceId, actorId, operation, requestId,
            requestHash, response.toString()
        );
    }

    private CrossSpaceRelationPolicy mapPolicy(ResultSet rs, int row) throws SQLException {
        return new CrossSpaceRelationPolicy(
            uuid(rs, "id"), uuid(rs, "grant_id"),
            uuid(rs, "source_space_id"), uuid(rs, "target_space_id"),
            rs.getString("relation_key"), rs.getString("direction"),
            uuid(rs, "source_type_id"), uuid(rs, "source_version_id"),
            rs.getString("source_config_hash"),
            uuid(rs, "target_type_id"), uuid(rs, "target_version_id"),
            rs.getString("target_config_hash"),
            rs.getString("status"), rs.getLong("version"),
            uuid(rs, "source_confirmed_by"), uuid(rs, "target_confirmed_by"),
            uuid(rs, "updated_by"), rs.getTimestamp("updated_at").toInstant()
        );
    }

    private LinkIntent mapIntent(ResultSet rs, int row) throws SQLException {
        return new LinkIntent(
            uuid(rs, "id"), uuid(rs, "policy_id"), rs.getLong("policy_version"),
            uuid(rs, "source_space_id"), uuid(rs, "source_work_item_id"),
            rs.getLong("source_expected_version"),
            uuid(rs, "target_space_id"), uuid(rs, "target_work_item_id"),
            rs.getLong("target_expected_version"),
            rs.getString("status"), rs.getLong("version"),
            uuid(rs, "source_confirmed_by"), uuid(rs, "target_confirmed_by"),
            uuid(rs, "canonical_relation_id"),
            rs.getTimestamp("updated_at").toInstant()
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
