package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.PresentationConfig;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedView;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.ViewShare;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkItemSavedViewRepository implements WorkItemSavedViewRepository {
    private static final String SELECT = """
        select v.id, v.space_id, v.owner_user_id, v.scope, v.name, v.description,
               v.status, v.aggregate_version, vv.version_number, vv.config_hash,
               vv.query_json, vv.presentation_json, v.created_at, v.updated_at
          from project_work_item_saved_views v
          join project_work_item_saved_view_versions vv
            on vv.workspace_id=v.workspace_id and vv.space_id=v.space_id
           and vv.view_id=v.id and vv.id=v.current_version_id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemSavedViewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SavedView> listAccessible(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        int limit
    ) {
        return jdbcTemplate.query(
            SELECT + """
                 where v.workspace_id=? and v.space_id=? and v.status='active'
                   and (
                     v.owner_user_id=?
                     or exists (
                       select 1 from project_work_item_saved_view_shares s
                        where s.workspace_id=v.workspace_id and s.space_id=v.space_id
                          and s.view_id=v.id and s.subject_user_id=? and s.status='active'
                     )
                   )
                 order by v.updated_at desc, v.id
                 limit ?
                """,
            (resultSet, rowNum) -> map(resultSet, workspaceId, spaceId, userId),
            workspaceId, spaceId, userId, userId, Math.max(1, Math.min(limit, 100))
        );
    }

    @Override
    public Optional<SavedView> findAccessible(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        UUID viewId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                SELECT + """
                     where v.workspace_id=? and v.space_id=? and v.id=? and v.status='active'
                       and (
                         v.owner_user_id=?
                         or exists (
                           select 1 from project_work_item_saved_view_shares s
                            where s.workspace_id=v.workspace_id and s.space_id=v.space_id
                              and s.view_id=v.id and s.subject_user_id=? and s.status='active'
                         )
                       )
                    """,
                (resultSet, rowNum) -> map(resultSet, workspaceId, spaceId, userId),
                workspaceId, spaceId, viewId, userId, userId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<SavedView> findAny(UUID workspaceId, UUID spaceId, UUID viewId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                SELECT + " where v.workspace_id=? and v.space_id=? and v.id=?",
                (resultSet, rowNum) -> map(resultSet, workspaceId, spaceId, null),
                workspaceId, spaceId, viewId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> findSpaceId(UUID workspaceId, UUID viewId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select space_id from project_work_item_saved_views
                     where workspace_id=? and id=? and status='active'
                    """,
                UUID.class,
                workspaceId,
                viewId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public SavedView create(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID ownerUserId,
        String scope,
        String name,
        String description,
        QueryDefinition query,
        PresentationConfig presentation,
        String configHash
    ) {
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                insert into project_work_item_saved_views (
                    id, workspace_id, space_id, owner_user_id, scope, name, description,
                    status, aggregate_version, current_version_id,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'active', 1, ?, ?, ?, ?, ?)
                """,
            id, workspaceId, spaceId, ownerUserId, scope, name, description,
            versionId, ownerUserId, Timestamp.from(now), ownerUserId, Timestamp.from(now)
        );
        insertVersion(
            versionId, workspaceId, spaceId, id, 1, query, presentation,
            configHash, ownerUserId, now
        );
        return findAccessible(workspaceId, spaceId, ownerUserId, id)
            .orElseThrow(() -> failure("SAVED_VIEW_CONFLICT", "Saved view was not created"));
    }

    @Override
    @Transactional
    public SavedView update(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        String scope,
        String name,
        String description,
        QueryDefinition query,
        PresentationConfig presentation,
        String configHash,
        UUID actorId
    ) {
        SavedView current = require(workspaceId, spaceId, viewId);
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
            """
                update project_work_item_saved_views
                   set scope=?, name=?, description=?, current_version_id=?,
                       aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=?
                 where workspace_id=? and space_id=? and id=?
                   and status='active' and aggregate_version=?
                """,
            scope, name, description, versionId, actorId, Timestamp.from(now),
            workspaceId, spaceId, viewId, expectedVersion
        );
        if (updated != 1) {
            throw failure("SAVED_VIEW_VERSION_CONFLICT", "Saved view version changed");
        }
        // The current-version FK is deferred so the aggregate compare-and-set can select
        // the single winner before inserting its immutable version. A losing concurrent
        // request therefore returns a stable version conflict instead of racing on the
        // version-number unique constraint.
        insertVersion(
            versionId, workspaceId, spaceId, viewId, current.versionNumber() + 1,
            query, presentation, configHash, actorId, now
        );
        return findAccessible(workspaceId, spaceId, actorId, viewId)
            .orElseGet(() -> require(workspaceId, spaceId, viewId));
    }

    @Override
    @Transactional
    public SavedView share(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID subjectUserId,
        String permission,
        UUID actorId
    ) {
        bump(workspaceId, spaceId, viewId, expectedVersion, actorId);
        jdbcTemplate.update(
            """
                insert into project_work_item_saved_view_shares (
                    id, workspace_id, space_id, view_id, subject_user_id,
                    permission, status, aggregate_version, shared_by, shared_at
                ) values (?, ?, ?, ?, ?, ?, 'active', 1, ?, now())
                on conflict (workspace_id, space_id, view_id, subject_user_id) do update
                    set permission=excluded.permission, status='active',
                        aggregate_version=project_work_item_saved_view_shares.aggregate_version+1,
                        shared_by=excluded.shared_by, shared_at=excluded.shared_at,
                        revoked_by=null, revoked_at=null
                """,
            UUID.randomUUID(), workspaceId, spaceId, viewId, subjectUserId, permission, actorId
        );
        return findAccessible(workspaceId, spaceId, actorId, viewId)
            .orElseGet(() -> require(workspaceId, spaceId, viewId));
    }

    @Override
    @Transactional
    public SavedView revoke(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID subjectUserId,
        UUID actorId
    ) {
        bump(workspaceId, spaceId, viewId, expectedVersion, actorId);
        int updated = jdbcTemplate.update(
            """
                update project_work_item_saved_view_shares
                   set status='revoked', aggregate_version=aggregate_version+1,
                       revoked_by=?, revoked_at=now()
                 where workspace_id=? and space_id=? and view_id=?
                   and subject_user_id=? and status='active'
                """,
            actorId, workspaceId, spaceId, viewId, subjectUserId
        );
        if (updated != 1) {
            throw failure("SAVED_VIEW_SHARE_NOT_FOUND", "Saved view share is not available");
        }
        return findAccessible(workspaceId, spaceId, actorId, viewId)
            .orElseGet(() -> require(workspaceId, spaceId, viewId));
    }

    @Override
    @Transactional
    public SavedView transfer(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID newOwnerUserId,
        UUID actorId
    ) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_saved_views
                   set owner_user_id=?, scope='personal',
                       aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and status='active' and aggregate_version=?
                """,
            newOwnerUserId, actorId, workspaceId, spaceId, viewId, expectedVersion
        );
        if (updated != 1) {
            throw failure("SAVED_VIEW_VERSION_CONFLICT", "Saved view version changed");
        }
        return require(workspaceId, spaceId, viewId);
    }

    @Override
    @Transactional
    public SavedView delete(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID actorId
    ) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_saved_views
                   set status='deleted', aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and status='active' and aggregate_version=?
                """,
            actorId, workspaceId, spaceId, viewId, expectedVersion
        );
        if (updated != 1) {
            throw failure("SAVED_VIEW_VERSION_CONFLICT", "Saved view version changed");
        }
        return require(workspaceId, spaceId, viewId);
    }

    @Override
    public boolean tryStartCommand(CommandStart start) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_saved_view_commands (
                    id, workspace_id, space_id, view_id, operation, request_id,
                    request_hash, expected_version, actor_id, status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', now())
                on conflict (workspace_id, space_id, operation, request_id) do nothing
                """,
            start.id(), start.workspaceId(), start.spaceId(), start.viewId(),
            start.operation(), start.requestId(), start.requestHash(),
            start.expectedVersion(), start.actorId()
        ) == 1;
    }

    @Override
    public Optional<CommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, space_id, view_id, operation, request_id, request_hash,
                           expected_version, actor_id, status, response_json
                      from project_work_item_saved_view_commands
                     where workspace_id=? and space_id=? and operation=? and request_id=?
                    """,
                (resultSet, rowNum) -> new CommandReceipt(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("space_id", UUID.class),
                    resultSet.getObject("view_id", UUID.class),
                    resultSet.getString("operation"),
                    resultSet.getString("request_id"),
                    resultSet.getString("request_hash"),
                    resultSet.getLong("expected_version"),
                    resultSet.getObject("actor_id", UUID.class),
                    resultSet.getString("status"),
                    json(resultSet.getObject("response_json"))
                ),
                workspaceId, spaceId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void completeCommand(UUID commandId, UUID viewId, JsonNode response) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_saved_view_commands
                   set view_id=?, status='completed', response_json=?::jsonb, completed_at=now()
                 where id=? and status='pending'
                """,
            viewId, response.toString(), commandId
        );
        if (updated != 1) {
            throw failure("SAVED_VIEW_COMMAND_CONFLICT", "Saved view command was not completed");
        }
    }

    private SavedView require(UUID workspaceId, UUID spaceId, UUID viewId) {
        return findAny(workspaceId, spaceId, viewId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Saved view is not available"));
    }

    private void bump(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID actorId
    ) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_saved_views
                   set scope='shared', aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and status='active' and aggregate_version=?
                """,
            actorId, workspaceId, spaceId, viewId, expectedVersion
        );
        if (updated != 1) {
            throw failure("SAVED_VIEW_VERSION_CONFLICT", "Saved view version changed");
        }
    }

    private void insertVersion(
        UUID versionId,
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long versionNumber,
        QueryDefinition query,
        PresentationConfig presentation,
        String configHash,
        UUID actorId,
        Instant now
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_saved_view_versions (
                    id, workspace_id, space_id, view_id, version_number,
                    schema_version, query_json, presentation_json, config_hash,
                    created_by, created_at
                ) values (?, ?, ?, ?, ?, 1, ?::jsonb, ?::jsonb, ?, ?, ?)
                """,
            versionId, workspaceId, spaceId, viewId, versionNumber,
            tree(query).toString(), tree(presentation).toString(), configHash,
            actorId, Timestamp.from(now)
        );
    }

    private SavedView map(
        ResultSet resultSet,
        UUID workspaceId,
        UUID spaceId,
        UUID userId
    ) throws SQLException {
        UUID id = resultSet.getObject("id", UUID.class);
        UUID owner = resultSet.getObject("owner_user_id", UUID.class);
        List<ViewShare> shares = shares(workspaceId, spaceId, id);
        boolean ownerAccess = userId != null && owner.equals(userId);
        Optional<ViewShare> ownShare = userId == null ? Optional.empty() : shares.stream()
            .filter(share -> share.subjectUserId().equals(userId) && "active".equals(share.status()))
            .findFirst();
        return new SavedView(
            id,
            resultSet.getObject("space_id", UUID.class),
            owner,
            resultSet.getString("scope"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getString("status"),
            resultSet.getLong("aggregate_version"),
            resultSet.getLong("version_number"),
            resultSet.getString("config_hash"),
            read(resultSet.getObject("query_json"), QueryDefinition.class),
            read(resultSet.getObject("presentation_json"), PresentationConfig.class),
            shares,
            ownerAccess || ownShare.isPresent(),
            ownerAccess || ownShare.map(share -> "manage".equals(share.permission())).orElse(false),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private List<ViewShare> shares(UUID workspaceId, UUID spaceId, UUID viewId) {
        return jdbcTemplate.query(
            """
                select subject_user_id, permission, status, aggregate_version,
                       shared_at, revoked_at
                  from project_work_item_saved_view_shares
                 where workspace_id=? and space_id=? and view_id=?
                 order by shared_at, subject_user_id
                """,
            (resultSet, rowNum) -> new ViewShare(
                resultSet.getObject("subject_user_id", UUID.class),
                resultSet.getString("permission"),
                resultSet.getString("status"),
                resultSet.getLong("aggregate_version"),
                resultSet.getTimestamp("shared_at").toInstant(),
                resultSet.getTimestamp("revoked_at") == null
                    ? null : resultSet.getTimestamp("revoked_at").toInstant()
            ),
            workspaceId, spaceId, viewId
        );
    }

    private JsonNode tree(Object value) {
        return objectMapper.valueToTree(value);
    }

    private JsonNode json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.readTree(String.valueOf(value));
        } catch (JsonProcessingException exception) {
            throw failure("SAVED_VIEW_STORAGE_INVALID", "Stored saved view JSON is invalid", exception);
        }
    }

    private <T> T read(Object value, Class<T> type) {
        try {
            return objectMapper.readValue(String.valueOf(value), type);
        } catch (JsonProcessingException exception) {
            throw failure("SAVED_VIEW_STORAGE_INVALID", "Stored saved view contract is invalid", exception);
        }
    }
}
