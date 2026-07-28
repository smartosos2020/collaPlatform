package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.CrossSpaceGrant;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCrossSpaceGrantRepository implements CrossSpaceGrantRepository {
    private static final String SELECT = """
        SELECT g.id,g.source_space_id,g.target_space_id,g.name,g.status,g.current_version,
               g.source_confirmed_at,g.target_confirmed_at,
               g.source_confirmed_by,g.target_confirmed_by,v.scope_json,v.scope_hash,
               g.updated_by,g.updated_at,g.revoked_at,g.archived_at
          FROM project_cross_space_grants g
          JOIN project_cross_space_grant_versions v
            ON v.workspace_id=g.workspace_id AND v.grant_id=g.id
           AND v.version_number=g.current_version
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcCrossSpaceGrantRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<CrossSpaceGrant> listVisible(UUID workspaceId, UUID userId, UUID spaceId, int limit) {
        return jdbc.query(SELECT + """
             WHERE g.workspace_id=? AND (?::uuid IS NULL OR g.source_space_id=? OR g.target_space_id=?)
               AND (
                 EXISTS (SELECT 1 FROM project_space_members m
                         WHERE m.workspace_id=g.workspace_id AND m.space_id=g.source_space_id
                           AND m.user_id=? AND m.status='active')
                 OR
                 EXISTS (SELECT 1 FROM project_space_members m
                         WHERE m.workspace_id=g.workspace_id AND m.space_id=g.target_space_id
                           AND m.user_id=? AND m.status='active')
               )
             ORDER BY g.updated_at DESC,g.id
             LIMIT ?
            """, this::mapGrant, workspaceId, spaceId, spaceId, spaceId, userId, userId, limit);
    }

    @Override
    public Optional<CrossSpaceGrant> find(UUID workspaceId, UUID grantId) {
        return jdbc.query(SELECT + " WHERE g.workspace_id=? AND g.id=?",
            this::mapGrant, workspaceId, grantId).stream().findFirst();
    }

    @Override
    public boolean isCurrentlyAuthorized(UUID workspaceId, UUID grantId) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*)
              FROM project_cross_space_grants g
              JOIN project_spaces source
                ON source.workspace_id=g.workspace_id AND source.id=g.source_space_id
              JOIN project_spaces target
                ON target.workspace_id=g.workspace_id AND target.id=g.target_space_id
             WHERE g.workspace_id=? AND g.id=? AND g.status='active'
               AND source.status='active' AND target.status='active'
               AND EXISTS (
                 SELECT 1 FROM project_space_members m
                 JOIN project_space_role_assignments r
                   ON r.workspace_id=m.workspace_id AND r.space_id=m.space_id
                  AND r.member_id=m.id AND r.revoked_at IS NULL
                  WHERE m.workspace_id=g.workspace_id AND m.space_id=g.source_space_id
                    AND m.user_id=g.source_confirmed_by AND m.status='active'
                    AND r.role_key IN ('owner','admin'))
               AND EXISTS (
                 SELECT 1 FROM project_space_members m
                 JOIN project_space_role_assignments r
                   ON r.workspace_id=m.workspace_id AND r.space_id=m.space_id
                  AND r.member_id=m.id AND r.revoked_at IS NULL
                  WHERE m.workspace_id=g.workspace_id AND m.space_id=g.target_space_id
                    AND m.user_id=g.target_confirmed_by AND m.status='active'
                    AND r.role_key IN ('owner','admin'))
            """, Integer.class, workspaceId, grantId);
        return count != null && count == 1;
    }

    @Override
    public List<GrantVersion> listVersions(UUID workspaceId, UUID grantId, int limit) {
        return jdbc.query("""
            SELECT version_number,scope_json,scope_hash,created_by,created_at
              FROM project_cross_space_grant_versions
             WHERE workspace_id=? AND grant_id=?
             ORDER BY version_number DESC LIMIT ?
            """, (rs, row) -> new GrantVersion(
                rs.getInt("version_number"), json(rs.getString("scope_json")),
                rs.getString("scope_hash"), uuid(rs, "created_by"),
                rs.getTimestamp("created_at").toInstant()
            ), workspaceId, grantId, limit);
    }

    @Override
    public CrossSpaceGrant create(
        UUID workspaceId, UUID sourceSpaceId, UUID targetSpaceId, UUID actorId,
        String name, JsonNode scope, String scopeHash
    ) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO project_cross_space_grants(
                  workspace_id,id,source_space_id,target_space_id,name,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?)
                """, workspaceId, id, sourceSpaceId, targetSpaceId, name, actorId, actorId);
            jdbc.update("""
                INSERT INTO project_cross_space_grant_versions(
                  workspace_id,grant_id,version_number,scope_json,scope_hash,created_by)
                VALUES(?,?,1,?::jsonb,?,?)
                """, workspaceId, id, scope.toString(), scopeHash, actorId);
        } catch (DataIntegrityViolationException exception) {
            throw failure("CROSS_SPACE_TARGET_NOT_AVAILABLE", "Target space is not available");
        }
        return find(workspaceId, id).orElseThrow();
    }

    @Override
    public CrossSpaceGrant revise(
        UUID workspaceId, UUID grantId, UUID actorId, long expectedVersion,
        String name, JsonNode scope, String scopeHash
    ) {
        int updated = jdbc.update("""
            UPDATE project_cross_space_grants
               SET name=?,current_version=current_version+1,status='draft',
                   source_confirmed_at=NULL,source_confirmed_by=NULL,
                   target_confirmed_at=NULL,target_confirmed_by=NULL,
                   updated_by=?,updated_at=now()
             WHERE workspace_id=? AND id=? AND current_version=?
               AND status NOT IN ('revoked','archived')
            """, name, actorId, workspaceId, grantId, expectedVersion);
        if (updated != 1) {
            throw failure("CROSS_SPACE_GRANT_VERSION_CONFLICT", "Grant changed; refresh and retry");
        }
        int nextVersion = Math.toIntExact(expectedVersion + 1);
        jdbc.update("""
            INSERT INTO project_cross_space_grant_versions(
              workspace_id,grant_id,version_number,scope_json,scope_hash,created_by)
            VALUES(?,?,?,?::jsonb,?,?)
            """, workspaceId, grantId, nextVersion,
            scope.toString(), scopeHash, actorId);
        return find(workspaceId, grantId).orElseThrow();
    }

    @Override
    public CrossSpaceGrant transition(
        UUID workspaceId, UUID grantId, UUID actorId, long expectedVersion,
        String action, String party
    ) {
        String sql = switch (action) {
            case "request" -> """
                UPDATE project_cross_space_grants
                   SET status='requested',updated_by=?,updated_at=now()
                 WHERE workspace_id=? AND id=? AND current_version=? AND status='draft'
                """;
            case "confirm" -> "source".equals(party) ? """
                UPDATE project_cross_space_grants
                   SET source_confirmed_at=now(),source_confirmed_by=?,
                       status=CASE WHEN target_confirmed_at IS NOT NULL THEN 'active' ELSE 'requested' END,
                       updated_by=?,updated_at=now()
                 WHERE workspace_id=? AND id=? AND current_version=? AND status='requested'
                """ : """
                UPDATE project_cross_space_grants
                   SET target_confirmed_at=now(),target_confirmed_by=?,
                       status=CASE WHEN source_confirmed_at IS NOT NULL THEN 'active' ELSE 'requested' END,
                       updated_by=?,updated_at=now()
                 WHERE workspace_id=? AND id=? AND current_version=? AND status='requested'
                """;
            case "pause" -> """
                UPDATE project_cross_space_grants SET status='paused',updated_by=?,updated_at=now()
                 WHERE workspace_id=? AND id=? AND current_version=? AND status='active'
                """;
            case "resume" -> """
                UPDATE project_cross_space_grants SET status='active',updated_by=?,updated_at=now()
                 WHERE workspace_id=? AND id=? AND current_version=? AND status='paused'
                   AND source_confirmed_at IS NOT NULL AND target_confirmed_at IS NOT NULL
                """;
            case "revoke" -> """
                UPDATE project_cross_space_grants
                   SET status='revoked',revoked_at=now(),updated_by=?,updated_at=now()
                 WHERE workspace_id=? AND id=? AND current_version=?
                   AND status NOT IN ('revoked','archived')
                """;
            case "archive" -> """
                UPDATE project_cross_space_grants
                   SET status='archived',archived_at=now(),updated_by=?,updated_at=now()
                 WHERE workspace_id=? AND id=? AND current_version=? AND status='revoked'
                """;
            default -> throw failure("CROSS_SPACE_GRANT_ACTION_INVALID", "Grant action is invalid");
        };
        Object[] args = "confirm".equals(action)
            ? new Object[]{actorId, actorId, workspaceId, grantId, expectedVersion}
            : new Object[]{actorId, workspaceId, grantId, expectedVersion};
        if (jdbc.update(sql, args) != 1) {
            throw failure("CROSS_SPACE_GRANT_STATE_CONFLICT", "Grant state changed; refresh and retry");
        }
        return find(workspaceId, grantId).orElseThrow();
    }

    @Override
    public Optional<CommandReceipt> findReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId
    ) {
        return jdbc.query("""
            SELECT request_hash,response_json FROM project_cross_space_grant_receipts
             WHERE workspace_id=? AND actor_id=? AND operation=? AND request_id=?
            """, (rs, row) -> new CommandReceipt(
                rs.getString("request_hash"), json(rs.getString("response_json"))
            ), workspaceId, actorId, operation, requestId).stream().findFirst();
    }

    @Override
    public void saveReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId,
        String requestHash, UUID grantId, JsonNode response
    ) {
        jdbc.update("""
            INSERT INTO project_cross_space_grant_receipts(
              workspace_id,request_id,actor_id,operation,grant_id,request_hash,response_json)
            VALUES(?,?,?,?,?,?,?::jsonb)
            """, workspaceId, requestId, actorId, operation, grantId,
            requestHash, response.toString());
    }

    private CrossSpaceGrant mapGrant(ResultSet rs, int row) throws SQLException {
        return new CrossSpaceGrant(
            uuid(rs, "id"), uuid(rs, "source_space_id"), uuid(rs, "target_space_id"),
            rs.getString("name"), rs.getString("status"), rs.getInt("current_version"),
            rs.getTimestamp("source_confirmed_at") != null,
            rs.getTimestamp("target_confirmed_at") != null,
            uuid(rs, "source_confirmed_by"), uuid(rs, "target_confirmed_by"),
            json(rs.getString("scope_json")), rs.getString("scope_hash"),
            uuid(rs, "updated_by"), instant(rs, "updated_at"),
            instant(rs, "revoked_at"), instant(rs, "archived_at")
        );
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
