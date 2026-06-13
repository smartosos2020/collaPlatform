package com.colla.platform.modules.platform.infrastructure;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectLinkRecord;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlatformObjectRepository implements PlatformObjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPlatformObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ObjectLinkRecord> findObjectLink(UUID workspaceId, String objectType, UUID objectId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, deleted_at
                    from object_links
                    where workspace_id = ? and object_type = ? and object_id = ?
                    """,
                this::mapObjectLink,
                workspaceId,
                objectType,
                objectId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ObjectLinkRecord> findObjectLinkByPath(UUID workspaceId, String pathOrDeepLink) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, deleted_at
                    from object_links
                    where workspace_id = ? and (web_path = ? or deep_link = ?)
                    """,
                this::mapObjectLink,
                workspaceId,
                pathOrDeepLink,
                pathOrDeepLink
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void upsertObjectLink(UUID workspaceId, String objectType, UUID objectId, String webPath, String deepLink, String titleSnapshot) {
        jdbcTemplate.update(
            """
                insert into object_links
                    (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, now(), now())
                on conflict (workspace_id, object_type, object_id)
                do update set web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              title_snapshot = excluded.title_snapshot,
                              updated_at = now(),
                              deleted_at = null
                """,
            UUID.randomUUID(),
            workspaceId,
            objectType,
            objectId,
            webPath,
            deepLink,
            titleSnapshot
        );
    }

    @Override
    public List<String> listObjectTypes() {
        return jdbcTemplate.queryForList("select object_type from object_type_rules order by object_type", String.class);
    }

    @Override
    public void recordRecentAccess(UUID workspaceId, UUID userId, String objectType, UUID objectId, String webPath, String deepLink, String titleSnapshot) {
        jdbcTemplate.update(
            """
                insert into object_recent_accesses
                    (id, workspace_id, user_id, object_type, object_id, web_path, deep_link, title_snapshot,
                     access_count, last_accessed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now(), now())
                on conflict (workspace_id, user_id, object_type, object_id)
                do update set web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              title_snapshot = excluded.title_snapshot,
                              access_count = object_recent_accesses.access_count + 1,
                              last_accessed_at = now(),
                              updated_at = now()
                """,
            UUID.randomUUID(),
            workspaceId,
            userId,
            objectType,
            objectId,
            webPath,
            deepLink,
            titleSnapshot
        );
    }

    @Override
    public List<PlatformObjectReference> listRecentAccesses(UUID workspaceId, UUID userId, int limit) {
        return jdbcTemplate.query(
            """
                select object_type, object_id, web_path, deep_link, title_snapshot, last_accessed_at touched_at
                from object_recent_accesses
                where workspace_id = ? and user_id = ?
                order by last_accessed_at desc
                limit ?
                """,
            this::mapReference,
            workspaceId,
            userId,
            Math.min(Math.max(limit, 1), 50)
        );
    }

    @Override
    public void addFavorite(UUID workspaceId, UUID userId, String objectType, UUID objectId) {
        jdbcTemplate.update(
            """
                insert into object_favorites (id, workspace_id, user_id, object_type, object_id, created_at)
                values (?, ?, ?, ?, ?, now())
                on conflict (workspace_id, user_id, object_type, object_id) do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            userId,
            objectType,
            objectId
        );
    }

    @Override
    public void removeFavorite(UUID workspaceId, UUID userId, String objectType, UUID objectId) {
        jdbcTemplate.update(
            "delete from object_favorites where workspace_id = ? and user_id = ? and object_type = ? and object_id = ?",
            workspaceId,
            userId,
            objectType,
            objectId
        );
    }

    @Override
    public List<PlatformObjectReference> listFavorites(UUID workspaceId, UUID userId, int limit) {
        return jdbcTemplate.query(
            """
                select f.object_type,
                       f.object_id,
                       ol.web_path,
                       ol.deep_link,
                       ol.title_snapshot,
                       f.created_at touched_at
                from object_favorites f
                left join object_links ol on ol.workspace_id = f.workspace_id
                    and ol.object_type = f.object_type
                    and ol.object_id = f.object_id
                where f.workspace_id = ? and f.user_id = ?
                order by f.created_at desc
                limit ?
                """,
            this::mapReference,
            workspaceId,
            userId,
            Math.min(Math.max(limit, 1), 50)
        );
    }

    private ObjectLinkRecord mapObjectLink(ResultSet rs, int rowNum) throws SQLException {
        return new ObjectLinkRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getString("object_type"),
            rs.getObject("object_id", UUID.class),
            rs.getString("web_path"),
            rs.getString("deep_link"),
            rs.getString("title_snapshot"),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
        );
    }

    private PlatformObjectReference mapReference(ResultSet rs, int rowNum) throws SQLException {
        return new PlatformObjectReference(
            rs.getString("object_type"),
            rs.getObject("object_id", UUID.class),
            rs.getString("web_path"),
            rs.getString("deep_link"),
            rs.getString("title_snapshot"),
            rs.getTimestamp("touched_at").toInstant()
        );
    }
}
