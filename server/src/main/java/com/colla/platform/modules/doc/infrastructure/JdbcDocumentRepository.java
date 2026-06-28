package com.colla.platform.modules.doc.infrastructure;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentComment;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCommentAnchor;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCollaborationState;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPermission;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentRelation;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentShareLink;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTemplate;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public UUID createDocument(
        UUID workspaceId,
        UUID parentId,
        String title,
        String docType,
        String content,
        int sortOrder,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        boolean knowledgeBase,
        UUID createdBy
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into documents
                    (id, workspace_id, parent_id, title, doc_type, content, current_version_no,
                     status, sort_order, description, cover_url, default_permission_level, knowledge_base,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, 1, 'active', ?, ?, ?, ?, ?, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            parentId,
            title,
            docType,
            content,
            sortOrder,
            description,
            coverUrl,
            defaultPermissionLevel,
            knowledgeBase,
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
    public void updateDocumentSnapshot(UUID workspaceId, UUID documentId, String title, String content, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update documents
                set title = ?, content = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            title,
            content,
            updatedBy,
            workspaceId,
            documentId
        );
    }

    @Override
    public void moveDocument(UUID workspaceId, UUID documentId, UUID parentId, int sortOrder, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update documents
                set parent_id = ?, sort_order = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            parentId,
            sortOrder,
            updatedBy,
            workspaceId,
            documentId
        );
    }

    @Override
    public void archiveDocumentTree(UUID workspaceId, UUID documentId, UUID updatedBy) {
        jdbcTemplate.update(
            """
                with recursive subtree as (
                    select id
                    from documents
                    where workspace_id = ? and id = ? and deleted_at is null
                    union all
                    select child.id
                    from documents child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                update documents d
                set archived_at = coalesce(d.archived_at, now()),
                    status = 'archived',
                    updated_by = ?,
                    updated_at = now()
                from subtree
                where d.id = subtree.id
                """,
            workspaceId,
            documentId,
            workspaceId,
            updatedBy
        );
    }

    @Override
    public void restoreDocumentTree(UUID workspaceId, UUID documentId, UUID updatedBy) {
        jdbcTemplate.update(
            """
                with recursive subtree as (
                    select id
                    from documents
                    where workspace_id = ? and id = ? and deleted_at is null
                    union all
                    select child.id
                    from documents child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                update documents d
                set archived_at = null,
                    status = 'active',
                    updated_by = ?,
                    updated_at = now()
                from subtree
                where d.id = subtree.id
                """,
            workspaceId,
            documentId,
            workspaceId,
            updatedBy
        );
    }

    @Override
    public boolean isDescendant(UUID workspaceId, UUID documentId, UUID candidateParentId) {
        if (candidateParentId == null) {
            return false;
        }
        Boolean result = jdbcTemplate.queryForObject(
            """
                with recursive subtree as (
                    select id
                    from documents
                    where workspace_id = ? and id = ? and deleted_at is null
                    union all
                    select child.id
                    from documents child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                select exists(select 1 from subtree where id = ?)
                """,
            Boolean.class,
            workspaceId,
            documentId,
            workspaceId,
            candidateParentId
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void copyParentPermissions(UUID workspaceId, UUID documentId, UUID parentId, UUID actorId) {
        if (parentId == null) {
            return;
        }
        List<Map<String, Object>> parentPermissions = jdbcTemplate.queryForList(
            """
                select subject_type, subject_id, user_id, permission_level
                from document_permissions
                where workspace_id = ? and document_id = ? and revoked_at is null
                """,
            workspaceId,
            parentId
        );
        for (Map<String, Object> permission : parentPermissions) {
            jdbcTemplate.update(
            """
                insert into document_permissions
                    (id, workspace_id, document_id, subject_type, subject_id, user_id, permission_level, source_type, source_document_id,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'inherited', ?, ?, now(), ?, now())
                on conflict (document_id, subject_type, subject_id) do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            documentId,
            permission.get("subject_type"),
            permission.get("subject_id"),
            permission.get("user_id"),
            permission.get("permission_level"),
            parentId,
            actorId,
            actorId
            );
            jdbcTemplate.update(
                """
                    insert into resource_permissions
                        (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
                         source_type, source_id, status, created_by, created_at, updated_by, updated_at)
                    values (?, ?, 'document', ?, ?, ?, ?, 'inherited', ?, 'active', ?, now(), ?, now())
                    """,
                UUID.randomUUID(),
                workspaceId,
                documentId,
                permission.get("subject_type"),
                permission.get("subject_id"),
                permission.get("permission_level"),
                parentId,
                actorId,
                actorId
            );
        }
        List<Map<String, Object>> parentResourcePermissions = jdbcTemplate.queryForList(
            """
                select subject_type, subject_id, permission_level, expires_at
                from resource_permissions rp
                where rp.workspace_id = ?
                  and rp.resource_type = 'document'
                  and rp.resource_id = ?
                  and rp.status = 'active'
                  and (rp.expires_at is null or rp.expires_at > now())
                  and not exists (
                      select 1
                      from resource_permissions existing
                      where existing.workspace_id = rp.workspace_id
                        and existing.resource_type = 'document'
                        and existing.resource_id = ?
                        and existing.subject_type = rp.subject_type
                        and existing.subject_id = rp.subject_id
                        and existing.status = 'active'
                  )
                """,
            workspaceId,
            parentId,
            documentId
        );
        for (Map<String, Object> permission : parentResourcePermissions) {
            jdbcTemplate.update(
                """
                    insert into resource_permissions
                        (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
                         source_type, source_id, expires_at, status, created_by, created_at, updated_by, updated_at)
                    values (?, ?, 'document', ?, ?, ?, ?, 'inherited', ?, ?, 'active', ?, now(), ?, now())
                    """,
                UUID.randomUUID(),
                workspaceId,
                documentId,
                permission.get("subject_type"),
                permission.get("subject_id"),
                permission.get("permission_level"),
                parentId,
                permission.get("expires_at"),
                actorId,
                actorId
            );
        }
    }

    @Override
    public void addVersion(
        UUID workspaceId,
        UUID documentId,
        int versionNo,
        String title,
        String content,
        UUID createdBy,
        String versionName,
        String versionType,
        String summary,
        Integer sourceVersionNo,
        String blockSnapshot
    ) {
        jdbcTemplate.update(
            """
                insert into document_versions
                    (id, workspace_id, document_id, version_no, version_name, version_type, title, content,
                     summary, source_version_no, block_snapshot, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            documentId,
            versionNo,
            versionName,
            versionType,
            title,
            content,
            summary,
            sourceVersionNo,
            blockSnapshot,
            createdBy
        );
    }

    @Override
    public void replaceBlocks(UUID workspaceId, UUID documentId, List<DocumentBlockDraft> blocks, UUID actorId) {
        jdbcTemplate.update(
            """
                update document_blocks
                set deleted_at = now(), updated_by = ?, updated_at = now()
                where workspace_id = ? and document_id = ? and deleted_at is null
                """,
            actorId,
            workspaceId,
            documentId
        );
        for (DocumentBlockDraft block : blocks) {
            jdbcTemplate.update(
                """
                    insert into document_blocks
                        (id, workspace_id, document_id, block_type, content, sort_order,
                         schema_version, attrs, rich_content, plain_text,
                         created_by, created_at, updated_by, updated_at)
                    values (?, ?, ?, ?, ?, ?, 2, '{}'::jsonb, jsonb_build_object('type', 'text', 'text', ?), ?, ?, now(), ?, now())
                    """,
                UUID.randomUUID(),
                workspaceId,
                documentId,
                block.blockType(),
                block.content(),
                block.sortOrder(),
                block.content(),
                block.content(),
                actorId,
                actorId
            );
        }
    }

    @Override
    public List<DocumentBlock> listBlocks(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select id, document_id, block_type, content, sort_order, created_at, updated_at
                from document_blocks
                where workspace_id = ? and document_id = ? and deleted_at is null
                order by sort_order, created_at
                """,
            (rs, rowNum) -> new DocumentBlock(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("block_type"),
                rs.getString("content"),
                rs.getInt("sort_order"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                null,
                Map.of()
            ),
            workspaceId,
            documentId
        );
    }

    @Override
    public List<DocumentSummary> listDocuments(UUID workspaceId, UUID userId, boolean includeArchived) {
        return jdbcTemplate.query(
            """
                with visible_permissions as (
                    select dp.document_id, dp.permission_level,
                           case dp.permission_level
                               when 'owner' then 5
                               when 'manage' then 4
                               when 'edit' then 3
                               when 'comment' then 2
                               when 'view' then 1
                               else 0
                           end permission_rank
                    from document_permissions dp
                    where dp.workspace_id = ?
                      and dp.subject_type = 'user'
                      and dp.subject_id = ?
                      and dp.revoked_at is null
                    union all
                    select dp.document_id, dp.permission_level,
                           case dp.permission_level
                               when 'owner' then 5
                               when 'manage' then 4
                               when 'edit' then 3
                               when 'comment' then 2
                               when 'view' then 1
                               else 0
                           end permission_rank
                    from document_permissions dp
                    join user_groups ug on ug.id = dp.subject_id
                        and ug.workspace_id = dp.workspace_id
                        and ug.status = 'active'
                        and ug.deleted_at is null
                    where dp.workspace_id = ?
                      and dp.subject_type = 'user_group'
                      and dp.revoked_at is null
                      and exists (
                          select 1
                          from user_group_members ugm
                          where ugm.workspace_id = dp.workspace_id
                            and ugm.group_id = ug.id
                            and ugm.subject_type = 'user'
                            and ugm.subject_id = ?
                            and ugm.removed_at is null
                          union all
                          select 1
                          from user_group_members ugm
                          join department_members dm on dm.department_id = ugm.subject_id
                              and dm.user_id = ?
                              and dm.ended_at is null
                          join departments d on d.id = ugm.subject_id
                              and d.workspace_id = ugm.workspace_id
                              and d.status = 'active'
                              and d.deleted_at is null
                          where ugm.workspace_id = dp.workspace_id
                            and ugm.group_id = ug.id
                            and ugm.subject_type = 'department'
                            and ugm.removed_at is null
                      )
                ),
                best_permissions as (
                    select document_id, permission_level
                    from (
                        select document_id, permission_level,
                               row_number() over (partition by document_id order by permission_rank desc) rn
                        from visible_permissions
                    ) ranked
                    where rn = 1
                )
                select d.id, d.parent_id, d.title, d.doc_type, d.current_version_no,
                       coalesce(case when d.created_by = ? then 'owner' end, bp.permission_level) permission_level,
                       d.created_by, cu.display_name created_by_name, d.created_at,
                       d.updated_by, uu.display_name updated_by_name, d.updated_at,
                       d.sort_order, d.description, d.cover_url, d.default_permission_level, d.knowledge_base,
                       d.archived_at is not null archived
                from documents d
                join users cu on cu.id = d.created_by
                join users uu on uu.id = d.updated_by
                left join best_permissions bp on bp.document_id = d.id
                where d.workspace_id = ? and d.deleted_at is null
                  and (d.created_by = ? or bp.document_id is not null)
                  and (? or d.archived_at is null)
                order by d.parent_id nulls first, d.sort_order, lower(d.title), d.updated_at desc
                """,
            this::mapDocumentSummary,
            workspaceId,
            userId,
            workspaceId,
            userId,
            userId,
            userId,
            workspaceId,
            userId,
            includeArchived
        );
    }

    @Override
    public Optional<DocumentSummary> findDocument(UUID workspaceId, UUID documentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select d.id, d.parent_id, d.title, d.doc_type, d.current_version_no,
                           'owner' permission_level,
                           d.created_by, cu.display_name created_by_name, d.created_at,
                           d.updated_by, uu.display_name updated_by_name, d.updated_at,
                           d.sort_order, d.description, d.cover_url, d.default_permission_level, d.knowledge_base,
                           d.archived_at is not null archived
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
    public Optional<DocumentCollaborationState> findCollaborationState(UUID workspaceId, UUID documentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select document_id, state_vector, snapshot_content, snapshot_payload::text snapshot_payload,
                           server_clock, last_client_id, updated_by, last_saved_at, updated_at
                    from document_collaboration_states
                    where workspace_id = ? and document_id = ?
                    """,
                (rs, rowNum) -> new DocumentCollaborationState(
                    rs.getObject("document_id", UUID.class),
                    rs.getString("state_vector"),
                    rs.getString("snapshot_content"),
                    rs.getString("snapshot_payload"),
                    rs.getLong("server_clock"),
                    rs.getString("last_client_id"),
                    rs.getObject("updated_by", UUID.class),
                    rs.getTimestamp("last_saved_at") == null ? null : rs.getTimestamp("last_saved_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ),
                workspaceId,
                documentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void upsertCollaborationState(
        UUID workspaceId,
        UUID documentId,
        String stateVector,
        String snapshotContent,
        String snapshotPayload,
        long serverClock,
        String lastClientId,
        UUID updatedBy
    ) {
        jdbcTemplate.update(
            """
                insert into document_collaboration_states
                    (id, workspace_id, document_id, state_vector, snapshot_content, snapshot_payload,
                     server_clock, last_client_id, updated_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, now(), now())
                on conflict (workspace_id, document_id)
                do update set state_vector = excluded.state_vector,
                              snapshot_content = excluded.snapshot_content,
                              snapshot_payload = excluded.snapshot_payload,
                              server_clock = excluded.server_clock,
                              last_client_id = excluded.last_client_id,
                              updated_by = excluded.updated_by,
                              updated_at = now()
                """,
            UUID.randomUUID(),
            workspaceId,
            documentId,
            stateVector,
            snapshotContent,
            snapshotPayload,
            serverClock,
            lastClientId,
            updatedBy
        );
    }

    @Override
    public void markCollaborationStateSaved(UUID workspaceId, UUID documentId, long serverClock) {
        jdbcTemplate.update(
            """
                update document_collaboration_states
                set last_saved_at = now()
                where workspace_id = ? and document_id = ? and server_clock <= ?
                """,
            workspaceId,
            documentId,
            serverClock
        );
    }

    @Override
    public List<DocumentVersion> listVersions(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select v.id, v.document_id, v.version_no, v.version_name, v.version_type, v.title, v.content,
                       v.summary, v.source_version_no, v.block_snapshot::text block_snapshot,
                       v.created_by, u.display_name created_by_name, v.created_at
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
                    select v.id, v.document_id, v.version_no, v.version_name, v.version_type, v.title, v.content,
                           v.summary, v.source_version_no, v.block_snapshot::text block_snapshot,
                           v.created_by, u.display_name created_by_name, v.created_at
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
    public List<DocumentTemplate> listTemplates(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select id, title, description, category, content, built_in, created_at
                from document_templates
                where status = 'active' and (workspace_id is null or workspace_id = ?)
                order by built_in desc, category, title
                """,
            this::mapTemplate,
            workspaceId
        );
    }

    @Override
    public Optional<DocumentTemplate> findTemplate(UUID workspaceId, UUID templateId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, title, description, category, content, built_in, created_at
                    from document_templates
                    where id = ? and status = 'active' and (workspace_id is null or workspace_id = ?)
                    """,
                this::mapTemplate,
                templateId,
                workspaceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void upsertPermission(UUID workspaceId, UUID documentId, UUID userId, String permissionLevel, UUID actorId) {
        upsertSubjectPermission(workspaceId, documentId, "user", userId, permissionLevel, actorId);
    }

    @Override
    public void upsertSubjectPermission(
        UUID workspaceId,
        UUID documentId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        UUID actorId
    ) {
        UUID userId = "user".equals(subjectType) ? subjectId : null;
        jdbcTemplate.update(
            """
                insert into document_permissions
                    (id, workspace_id, document_id, subject_type, subject_id, user_id, permission_level, source_type, source_document_id,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'direct', null, ?, now(), ?, now())
                on conflict (document_id, subject_type, subject_id)
                do update set permission_level = excluded.permission_level,
                    user_id = excluded.user_id,
                    source_type = 'direct',
                    source_document_id = null,
                    revoked_at = null,
                    updated_by = excluded.updated_by,
                    updated_at = now()
                """,
            UUID.randomUUID(),
            workspaceId,
            documentId,
            subjectType,
            subjectId,
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
                    select permission_level
                    from (
                        select 'owner' permission_level, 5 permission_rank
                        from documents d
                        where d.workspace_id = ?
                          and d.id = ?
                          and d.deleted_at is null
                          and d.created_by = ?
                        union all
                        select dp.permission_level,
                               case dp.permission_level
                                   when 'owner' then 5
                                   when 'manage' then 4
                                   when 'edit' then 3
                                   when 'comment' then 2
                                   when 'view' then 1
                                   else 0
                               end permission_rank
                        from document_permissions dp
                        join documents d on d.id = dp.document_id and d.deleted_at is null
                        where dp.workspace_id = ?
                          and dp.document_id = ?
                          and dp.subject_type = 'user'
                          and dp.subject_id = ?
                          and dp.revoked_at is null
                        union all
                        select dp.permission_level,
                               case dp.permission_level
                                   when 'owner' then 5
                                   when 'manage' then 4
                                   when 'edit' then 3
                                   when 'comment' then 2
                                   when 'view' then 1
                                   else 0
                               end permission_rank
                        from document_permissions dp
                        join documents doc on doc.id = dp.document_id and doc.deleted_at is null
                        join user_groups ug on ug.id = dp.subject_id
                            and ug.workspace_id = dp.workspace_id
                            and ug.status = 'active'
                            and ug.deleted_at is null
                        where dp.workspace_id = ?
                          and dp.document_id = ?
                          and dp.subject_type = 'user_group'
                          and dp.revoked_at is null
                          and exists (
                              select 1
                              from user_group_members ugm
                              where ugm.workspace_id = dp.workspace_id
                                and ugm.group_id = ug.id
                                and ugm.subject_type = 'user'
                                and ugm.subject_id = ?
                                and ugm.removed_at is null
                              union all
                              select 1
                              from user_group_members ugm
                              join department_members dm on dm.department_id = ugm.subject_id
                                  and dm.user_id = ?
                                  and dm.ended_at is null
                              join departments d on d.id = ugm.subject_id
                                  and d.workspace_id = ugm.workspace_id
                                  and d.status = 'active'
                                  and d.deleted_at is null
                              where ugm.workspace_id = dp.workspace_id
                                and ugm.group_id = ug.id
                                and ugm.subject_type = 'department'
                                and ugm.removed_at is null
                          )
                        union all
                        select dsl.permission_level,
                               case dsl.permission_level
                                   when 'edit' then 3
                                   when 'comment' then 2
                                   when 'view' then 1
                                   else 0
                               end permission_rank
                        from document_share_links dsl
                        join documents d on d.id = dsl.document_id and d.deleted_at is null
                        where d.workspace_id = ?
                          and d.id = ?
                          and dsl.enabled = true
                          and (dsl.expires_at is null or dsl.expires_at > now())
                    ) permissions
                    order by permission_rank desc
                    limit 1
                    """,
                String.class,
                workspaceId,
                documentId,
                userId,
                workspaceId,
                documentId,
                userId,
                workspaceId,
                documentId,
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
    public boolean isShareLinkAccess(UUID workspaceId, UUID documentId, UUID userId) {
        Boolean value = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from documents d
                    join document_share_links dsl on dsl.document_id = d.id
                    where d.workspace_id = ?
                      and d.id = ?
                      and d.deleted_at is null
                      and d.created_by <> ?
                      and not exists (
                          select 1
                          from document_permissions dp
                          where dp.document_id = d.id
                            and dp.subject_type = 'user'
                            and dp.subject_id = ?
                            and dp.revoked_at is null
                      )
                      and not exists (
                          select 1
                          from document_permissions dp
                          join user_groups ug on ug.id = dp.subject_id
                              and ug.workspace_id = dp.workspace_id
                              and ug.status = 'active'
                              and ug.deleted_at is null
                          where dp.document_id = d.id
                            and dp.subject_type = 'user_group'
                            and dp.revoked_at is null
                            and exists (
                                select 1
                                from user_group_members ugm
                                where ugm.workspace_id = dp.workspace_id
                                  and ugm.group_id = ug.id
                                  and ugm.subject_type = 'user'
                                  and ugm.subject_id = ?
                                  and ugm.removed_at is null
                                union all
                                select 1
                                from user_group_members ugm
                                join department_members dm on dm.department_id = ugm.subject_id
                                    and dm.user_id = ?
                                    and dm.ended_at is null
                                join departments dep on dep.id = ugm.subject_id
                                    and dep.workspace_id = ugm.workspace_id
                                    and dep.status = 'active'
                                    and dep.deleted_at is null
                                where ugm.workspace_id = dp.workspace_id
                                  and ugm.group_id = ug.id
                                  and ugm.subject_type = 'department'
                                  and ugm.removed_at is null
                            )
                      )
                      and dsl.enabled = true
                      and (dsl.expires_at is null or dsl.expires_at > now())
                )
                """,
            Boolean.class,
            workspaceId,
            documentId,
            userId,
            userId,
            userId,
            userId
        );
        return Boolean.TRUE.equals(value);
    }

    @Override
    public List<DocumentPermission> listPermissions(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select dp.id, dp.document_id, dp.subject_type, dp.subject_id, dp.user_id,
                       u.username, u.display_name,
                       case dp.subject_type when 'user' then u.display_name else ug.name end subject_name,
                       case dp.subject_type when 'user' then u.username else ug.code end subject_detail,
                       dp.permission_level, dp.source_type, dp.source_document_id, sd.title source_title, dp.created_at
                from document_permissions dp
                left join users u on u.id = dp.user_id
                left join user_groups ug on ug.id = dp.subject_id and dp.subject_type = 'user_group'
                left join documents sd on sd.id = dp.source_document_id
                where dp.workspace_id = ? and dp.document_id = ? and dp.revoked_at is null
                order by dp.subject_type, dp.created_at
                """,
            (rs, rowNum) -> new DocumentPermission(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("subject_type"),
                rs.getObject("subject_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("subject_name"),
                rs.getString("subject_detail"),
                rs.getString("permission_level"),
                rs.getString("source_type"),
                rs.getObject("source_document_id", UUID.class),
                rs.getString("source_title"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            documentId
        );
    }

    @Override
    public List<DocumentShareLink> listShareLinks(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.query(
            """
                select dsl.id, dsl.document_id, dsl.token, dsl.scope, dsl.permission_level, dsl.enabled,
                       dsl.expires_at, dsl.created_by, cu.display_name created_by_name, dsl.created_at,
                       dsl.updated_by, uu.display_name updated_by_name, dsl.updated_at
                from document_share_links dsl
                join users cu on cu.id = dsl.created_by
                left join users uu on uu.id = dsl.updated_by
                where dsl.workspace_id = ? and dsl.document_id = ?
                order by dsl.created_at desc
                """,
            this::mapShareLink,
            workspaceId,
            documentId
        );
    }

    @Override
    public DocumentShareLink upsertShareLink(
        UUID workspaceId,
        UUID documentId,
        String token,
        String scope,
        String permissionLevel,
        boolean enabled,
        Instant expiresAt,
        UUID actorId
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into document_share_links
                    (id, workspace_id, document_id, token, scope, permission_level, enabled, expires_at,
                     created_by, created_at, updated_by, updated_at, disabled_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now(), case when ? then null else now() end)
                on conflict (document_id)
                do update set scope = excluded.scope,
                    permission_level = excluded.permission_level,
                    enabled = excluded.enabled,
                    expires_at = excluded.expires_at,
                    updated_by = excluded.updated_by,
                    updated_at = now(),
                    disabled_at = case when excluded.enabled then null else coalesce(document_share_links.disabled_at, now()) end
                """,
            id,
            workspaceId,
            documentId,
            token,
            scope,
            permissionLevel,
            enabled,
            expiresAt,
            actorId,
            actorId,
            enabled
        );
        return listShareLinks(workspaceId, documentId).stream()
            .findFirst()
            .orElseThrow(() -> new EmptyResultDataAccessException(1));
    }

    @Override
    public Optional<DocumentShareLink> setShareLinkEnabled(UUID workspaceId, UUID documentId, boolean enabled, UUID actorId) {
        int changed = jdbcTemplate.update(
            """
                update document_share_links
                set enabled = ?,
                    disabled_at = case when ? then null else now() end,
                    updated_by = ?,
                    updated_at = now()
                where workspace_id = ? and document_id = ?
                """,
            enabled,
            enabled,
            actorId,
            workspaceId,
            documentId
        );
        if (changed == 0) {
            return Optional.empty();
        }
        return listShareLinks(workspaceId, documentId).stream().findFirst();
    }

    @Override
    public void updateKnowledgeBaseSettings(
        UUID workspaceId,
        UUID documentId,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        boolean knowledgeBase,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
                update documents
                set description = ?, cover_url = ?, default_permission_level = ?, knowledge_base = ?,
                    updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            description,
            coverUrl,
            defaultPermissionLevel,
            knowledgeBase,
            actorId,
            workspaceId,
            documentId
        );
    }

    @Override
    public List<UUID> findDocumentManagerUserIds(UUID workspaceId, UUID documentId) {
        return jdbcTemplate.queryForList(
            """
                select distinct user_id
                from (
                    select d.created_by user_id
                    from documents d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select dp.user_id
                    from document_permissions dp
                    where dp.workspace_id = ? and dp.document_id = ? and dp.revoked_at is null
                      and dp.permission_level in ('manage', 'owner')
                ) managers
                """,
            UUID.class,
            workspaceId,
            documentId,
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
    public Optional<DocumentComment> findComment(UUID workspaceId, UUID documentId, UUID commentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select c.id, c.thread_id, c.parent_comment_id, c.document_id, c.block_id, c.author_id,
                           u.display_name author_name, c.content,
                           c.anchor_type, c.anchor_start, c.anchor_end, c.anchor_text, c.anchor_prefix, c.anchor_suffix,
                           c.anchor_version_no, c.resolved_at, c.resolved_by, resolved_user.display_name resolved_by_name,
                           c.reopened_at, c.reopened_by, reopened_user.display_name reopened_by_name, c.created_at
                    from document_comments c
                    join users u on u.id = c.author_id
                    left join users resolved_user on resolved_user.id = c.resolved_by
                    left join users reopened_user on reopened_user.id = c.reopened_by
                    where c.workspace_id = ? and c.document_id = ? and c.id = ? and c.deleted_at is null
                    """,
                (rs, rowNum) -> mapComment(rs, List.of()),
                workspaceId,
                documentId,
                commentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public UUID addComment(UUID workspaceId, UUID documentId, UUID authorId, String content, DocumentCommentAnchor anchor) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into document_comments
                    (id, workspace_id, document_id, thread_id, parent_comment_id, block_id, author_id, content,
                     anchor_type, anchor_start, anchor_end, anchor_text, anchor_prefix, anchor_suffix, anchor_version_no, created_at)
                values (?, ?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            id,
            workspaceId,
            documentId,
            id,
            anchor.blockId(),
            authorId,
            content,
            anchor.anchorType(),
            anchor.anchorStart(),
            anchor.anchorEnd(),
            anchor.anchorText(),
            anchor.anchorPrefix(),
            anchor.anchorSuffix(),
            anchor.anchorVersionNo()
        );
        return id;
    }

    @Override
    public UUID addCommentReply(UUID workspaceId, UUID documentId, UUID threadId, UUID parentCommentId, UUID authorId, String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into document_comments
                    (id, workspace_id, document_id, thread_id, parent_comment_id, block_id, author_id, content,
                     anchor_type, anchor_start, anchor_end, anchor_text, anchor_prefix, anchor_suffix, anchor_version_no, created_at)
                select ?, workspace_id, document_id, thread_id, ?, block_id, ?, ?,
                       anchor_type, anchor_start, anchor_end, anchor_text, anchor_prefix, anchor_suffix, anchor_version_no, now()
                from document_comments
                where workspace_id = ? and document_id = ? and id = ? and thread_id = ? and deleted_at is null
                """,
            id,
            parentCommentId,
            authorId,
            content,
            workspaceId,
            documentId,
            threadId,
            threadId
        );
        return id;
    }

    @Override
    public void updateCommentThreadSelectionAnchor(UUID workspaceId, UUID documentId, UUID threadId, int anchorStart, int anchorEnd) {
        jdbcTemplate.update(
            """
                update document_comments
                set anchor_start = ?,
                    anchor_end = ?,
                    anchor_version_no = (
                        select current_version_no
                        from documents
                        where workspace_id = ? and id = ?
                    )
                where workspace_id = ?
                  and document_id = ?
                  and thread_id = ?
                  and anchor_type = 'selection'
                  and deleted_at is null
                """,
            anchorStart,
            anchorEnd,
            workspaceId,
            documentId,
            workspaceId,
            documentId,
            threadId
        );
    }

    @Override
    public void resolveCommentThread(UUID workspaceId, UUID documentId, UUID commentId, UUID resolvedBy) {
        jdbcTemplate.update(
            """
                with target as (
                    select thread_id
                    from document_comments
                    where workspace_id = ? and document_id = ? and id = ? and deleted_at is null
                )
                update document_comments
                set resolved_at = coalesce(resolved_at, now()), resolved_by = coalesce(resolved_by, ?)
                where workspace_id = ? and document_id = ? and thread_id = (select thread_id from target) and deleted_at is null
                """,
            workspaceId,
            documentId,
            commentId,
            resolvedBy,
            workspaceId,
            documentId
        );
    }

    @Override
    public void reopenCommentThread(UUID workspaceId, UUID documentId, UUID commentId, UUID reopenedBy) {
        jdbcTemplate.update(
            """
                with target as (
                    select thread_id
                    from document_comments
                    where workspace_id = ? and document_id = ? and id = ? and deleted_at is null
                )
                update document_comments
                set resolved_at = null,
                    resolved_by = null,
                    reopened_at = now(),
                    reopened_by = ?
                where workspace_id = ? and document_id = ? and thread_id = (select thread_id from target) and deleted_at is null
                """,
            workspaceId,
            documentId,
            commentId,
            reopenedBy,
            workspaceId,
            documentId
        );
    }

    @Override
    public List<DocumentComment> listComments(UUID workspaceId, UUID documentId) {
        List<DocumentComment> rows = jdbcTemplate.query(
            """
                select c.id, c.thread_id, c.parent_comment_id, c.document_id, c.block_id, c.author_id,
                       u.display_name author_name, c.content,
                       c.anchor_type, c.anchor_start, c.anchor_end, c.anchor_text, c.anchor_prefix, c.anchor_suffix,
                       c.anchor_version_no, c.resolved_at, c.resolved_by, resolved_user.display_name resolved_by_name,
                       c.reopened_at, c.reopened_by, reopened_user.display_name reopened_by_name, c.created_at
                from document_comments c
                join document_comments root_comment on root_comment.id = c.thread_id
                join users u on u.id = c.author_id
                left join users resolved_user on resolved_user.id = c.resolved_by
                left join users reopened_user on reopened_user.id = c.reopened_by
                where c.workspace_id = ? and c.document_id = ? and c.deleted_at is null
                order by root_comment.created_at, c.created_at
                """,
            (rs, rowNum) -> mapComment(rs, List.of()),
            workspaceId,
            documentId
        );
        Map<UUID, DocumentComment> roots = new LinkedHashMap<>();
        Map<UUID, List<DocumentComment>> repliesByThread = new LinkedHashMap<>();
        for (DocumentComment comment : rows) {
            if (comment.root()) {
                roots.put(comment.id(), comment);
            } else {
                repliesByThread.computeIfAbsent(comment.threadId(), ignored -> new ArrayList<>()).add(comment);
            }
        }
        return roots.values().stream()
            .map(root -> withReplies(root, repliesByThread.getOrDefault(root.id(), List.of())))
            .toList();
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
            rs.getTimestamp("updated_at").toInstant(),
            rs.getInt("sort_order"),
            rs.getString("description"),
            rs.getString("cover_url"),
            rs.getString("default_permission_level"),
            rs.getBoolean("knowledge_base"),
            rs.getBoolean("archived")
        );
    }

    private DocumentShareLink mapShareLink(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentShareLink(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getString("token"),
            rs.getString("scope"),
            rs.getString("permission_level"),
            rs.getBoolean("enabled"),
            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
            rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("updated_by", UUID.class),
            rs.getString("updated_by_name"),
            rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant()
        );
    }

    private DocumentVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentVersion(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getInt("version_no"),
            rs.getString("version_name"),
            rs.getString("version_type"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("summary"),
            rs.getObject("source_version_no", Integer.class),
            rs.getString("block_snapshot"),
            rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private DocumentTemplate mapTemplate(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentTemplate(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("category"),
            rs.getString("content"),
            rs.getBoolean("built_in"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private DocumentComment mapComment(ResultSet rs, List<DocumentComment> replies) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID threadId = rs.getObject("thread_id", UUID.class);
        return new DocumentComment(
            id,
            threadId,
            rs.getObject("parent_comment_id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getObject("block_id", UUID.class),
            rs.getObject("author_id", UUID.class),
            rs.getString("author_name"),
            rs.getString("content"),
            rs.getString("anchor_type"),
            rs.getObject("anchor_start", Integer.class),
            rs.getObject("anchor_end", Integer.class),
            rs.getString("anchor_text"),
            rs.getString("anchor_prefix"),
            rs.getString("anchor_suffix"),
            rs.getObject("anchor_version_no", Integer.class),
            id.equals(threadId),
            rs.getTimestamp("resolved_at") != null,
            rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant(),
            rs.getObject("resolved_by", UUID.class),
            rs.getString("resolved_by_name"),
            rs.getTimestamp("reopened_at") == null ? null : rs.getTimestamp("reopened_at").toInstant(),
            rs.getObject("reopened_by", UUID.class),
            rs.getString("reopened_by_name"),
            rs.getTimestamp("created_at").toInstant(),
            replies
        );
    }

    private DocumentComment withReplies(DocumentComment comment, List<DocumentComment> replies) {
        return new DocumentComment(
            comment.id(),
            comment.threadId(),
            comment.parentCommentId(),
            comment.documentId(),
            comment.blockId(),
            comment.authorId(),
            comment.authorName(),
            comment.content(),
            comment.anchorType(),
            comment.anchorStart(),
            comment.anchorEnd(),
            comment.anchorText(),
            comment.anchorPrefix(),
            comment.anchorSuffix(),
            comment.anchorVersionNo(),
            comment.root(),
            comment.resolved(),
            comment.resolvedAt(),
            comment.resolvedBy(),
            comment.resolvedByName(),
            comment.reopenedAt(),
            comment.reopenedBy(),
            comment.reopenedByName(),
            comment.createdAt(),
            replies
        );
    }
}
