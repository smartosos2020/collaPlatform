package com.colla.platform.modules.doc.infrastructure;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentComment;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPermission;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentRelation;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDocumentRepository implements DocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID createDocument(UUID workspaceId, UUID parentId, String title, String content, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into documents
                    (id, workspace_id, parent_id, title, doc_type, content, current_version_no,
                     status, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'markdown', ?, 1, 'active', ?, now(), ?, now())
                """,
            id,
            workspaceId,
            parentId,
            title,
            content,
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public void updateDocument(UUID workspaceId, UUID documentId, UUID parentId, String title, String content, int nextVersionNo, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update documents
                set parent_id = ?, title = ?, content = ?, current_version_no = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            parentId,
            title,
            content,
            nextVersionNo,
            updatedBy,
            workspaceId,
            documentId
        );
    }

    @Override
    public void moveDocument(UUID workspaceId, UUID documentId, UUID parentId, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update documents
                set parent_id = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            parentId,
            updatedBy,
            workspaceId,
            documentId
        );
    }

    @Override
    public void addVersion(UUID workspaceId, UUID documentId, int versionNo, String title, String content, UUID createdBy) {
        jdbcTemplate.update(
            """
                insert into document_versions
                    (id, workspace_id, document_id, version_no, title, content, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            documentId,
            versionNo,
            title,
            content,
            createdBy
        );
    }

    @Override
    public List<DocumentSummary> listDocuments(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select d.id, d.parent_id, d.title, d.doc_type, d.current_version_no,
                       coalesce(dp.permission_level, case when d.created_by = ? then 'manage' end) permission_level,
                       d.created_by, cu.display_name created_by_name, d.created_at,
                       d.updated_by, uu.display_name updated_by_name, d.updated_at
                from documents d
                join users cu on cu.id = d.created_by
                join users uu on uu.id = d.updated_by
                left join document_permissions dp on dp.document_id = d.id and dp.user_id = ? and dp.revoked_at is null
                where d.workspace_id = ? and d.deleted_at is null
                  and (d.created_by = ? or dp.user_id is not null)
                order by d.updated_at desc
                """,
            this::mapDocumentSummary,
            userId,
            userId,
            workspaceId,
            userId
        );
    }

    @Override
    public Optional<DocumentSummary> findDocument(UUID workspaceId, UUID documentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select d.id, d.parent_id, d.title, d.doc_type, d.current_version_no,
                           'manage' permission_level,
                           d.created_by, cu.display_name created_by_name, d.created_at,
                           d.updated_by, uu.display_name updated_by_name, d.updated_at
                    from documents d
                    join users cu on cu.id = d.created_by
                    join users uu on uu.id = d.updated_by
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    """,
                this::mapDocumentSummary,
                workspaceId,
                documentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findContent(UUID workspaceId, UUID documentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "select content from documents where workspace_id = ? and id = ? and deleted_at is null",
                String.class,
                workspaceId,
                documentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<DocumentVersion> listVersions(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select v.id, v.document_id, v.version_no, v.title, v.content, v.created_by, u.display_name created_by_name, v.created_at
                from document_versions v
                join users u on u.id = v.created_by
                where v.workspace_id = ? and v.document_id = ?
                order by v.version_no desc
                """,
            this::mapVersion,
            workspaceId,
            documentId
        );
    }

    @Override
    public Optional<DocumentVersion> findVersion(UUID workspaceId, UUID documentId, int versionNo) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select v.id, v.document_id, v.version_no, v.title, v.content, v.created_by, u.display_name created_by_name, v.created_at
                    from document_versions v
                    join users u on u.id = v.created_by
                    where v.workspace_id = ? and v.document_id = ? and v.version_no = ?
                    """,
                this::mapVersion,
                workspaceId,
                documentId,
                versionNo
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void upsertPermission(UUID workspaceId, UUID documentId, UUID userId, String permissionLevel, UUID actorId) {
        jdbcTemplate.update(
            """
                insert into document_permissions
                    (id, workspace_id, document_id, user_id, permission_level, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, now(), ?, now())
                on conflict (document_id, user_id)
                do update set permission_level = excluded.permission_level, revoked_at = null, updated_by = excluded.updated_by, updated_at = now()
                """,
            UUID.randomUUID(),
            workspaceId,
            documentId,
            userId,
            permissionLevel,
            actorId,
            actorId
        );
    }

    @Override
    public Optional<String> findPermissionLevel(UUID workspaceId, UUID documentId, UUID userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select case
                        when d.created_by = ? then 'manage'
                        else dp.permission_level
                    end
                    from documents d
                    left join document_permissions dp on dp.document_id = d.id and dp.user_id = ? and dp.revoked_at is null
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    """,
                String.class,
                userId,
                userId,
                workspaceId,
                documentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<DocumentPermission> listPermissions(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select dp.id, dp.document_id, dp.user_id, u.username, u.display_name, dp.permission_level, dp.created_at
                from document_permissions dp
                join users u on u.id = dp.user_id
                where dp.workspace_id = ? and dp.document_id = ? and dp.revoked_at is null
                order by dp.created_at
                """,
            (rs, rowNum) -> new DocumentPermission(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("permission_level"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            documentId
        );
    }

    @Override
    public void addRelation(UUID workspaceId, UUID documentId, String targetType, UUID targetId, UUID createdBy) {
        jdbcTemplate.update(
            """
                insert into document_relations
                    (id, workspace_id, document_id, target_type, target_id, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, document_id, target_type, target_id)
                do update set deleted_at = null
                """,
            UUID.randomUUID(),
            workspaceId,
            documentId,
            targetType,
            targetId,
            createdBy
        );
    }

    @Override
    public List<DocumentRelation> listRelations(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select r.id, r.document_id, r.target_type, r.target_id,
                       coalesce(ol.title_snapshot, r.target_type || ':' || r.target_id::text) title,
                       ol.web_path,
                       r.created_at
                from document_relations r
                left join object_links ol on ol.workspace_id = r.workspace_id and ol.object_type = r.target_type and ol.object_id = r.target_id
                where r.workspace_id = ? and r.document_id = ? and r.deleted_at is null
                order by r.created_at desc
                """,
            (rs, rowNum) -> new DocumentRelation(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getString("title"),
                rs.getString("web_path"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            documentId
        );
    }

    @Override
    public UUID addComment(UUID workspaceId, UUID documentId, UUID authorId, String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into document_comments (id, workspace_id, document_id, author_id, content, created_at)
                values (?, ?, ?, ?, ?, now())
                """,
            id,
            workspaceId,
            documentId,
            authorId,
            content
        );
        return id;
    }

    @Override
    public List<DocumentComment> listComments(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select c.id, c.document_id, c.author_id, u.display_name author_name, c.content, c.created_at
                from document_comments c
                join users u on u.id = c.author_id
                where c.workspace_id = ? and c.document_id = ? and c.deleted_at is null
                order by c.created_at
                """,
            (rs, rowNum) -> new DocumentComment(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            documentId
        );
    }

    @Override
    public List<UUID> findActiveUserIdsByUsernames(UUID workspaceId, List<String> usernames) {
        if (usernames.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", usernames.stream().map(item -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        args.addAll(usernames);
        return jdbcTemplate.queryForList(
            """
                select id
                from users
                where workspace_id = ? and status = 'active' and deleted_at is null and username in (%s)
                """.formatted(placeholders),
            UUID.class,
            args.toArray()
        );
    }

    private DocumentSummary mapDocumentSummary(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("parent_id", UUID.class),
            rs.getString("title"),
            rs.getString("doc_type"),
            rs.getInt("current_version_no"),
            rs.getString("permission_level"),
            rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("updated_by", UUID.class),
            rs.getString("updated_by_name"),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private DocumentVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentVersion(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getInt("version_no"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
