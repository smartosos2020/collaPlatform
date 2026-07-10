package com.colla.platform.modules.search.infrastructure;

import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import com.colla.platform.modules.search.domain.SearchModels.SearchFilters;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSearchRepository implements SearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResult> search(UUID workspaceId, UUID userId, String query, SearchFilters filters, int limit) {
        String likeQuery = "%" + query.toLowerCase() + "%";
        List<Object> args = new ArrayList<>();
        args.add(query);
        args.add(likeQuery);
        args.add(likeQuery);
        args.add(likeQuery);
        args.add(likeQuery);
        args.add(likeQuery);
        args.add(likeQuery);
        args.add(likeQuery);
        args.add(likeQuery);
        args.add(workspaceId);
        args.add(query);
        args.add(likeQuery);
        args.add(likeQuery);

        StringBuilder filterSql = new StringBuilder();
        if (filters != null && filters.knowledgeBaseId() != null) {
            filterSql.append(" and s.knowledge_base_id = ?\n");
            args.add(filters.knowledgeBaseId());
        }
        if (filters != null && filters.directoryId() != null) {
            filterSql.append(
                """
                  and s.object_type = 'document'
                  and exists (
                      with recursive subtree(id) as (
                          select d.id
                          from documents d
                          where d.workspace_id = s.workspace_id and d.id = ?
                          union all
                          select child.id
                          from documents child
                          join subtree parent on parent.id = child.parent_id
                          where child.workspace_id = s.workspace_id and child.deleted_at is null
                      )
                      select 1 from subtree where subtree.id = s.object_id
                  )
                """
            );
            args.add(filters.directoryId());
        }
        if (filters != null && filters.docType() != null) {
            filterSql.append(" and s.object_type = 'document' and s.doc_type = ?\n");
            args.add(filters.docType());
        }
        if (filters != null && filters.tags() != null && !filters.tags().isEmpty()) {
            filterSql.append(" and s.object_type = 'document'\n");
            for (String tag : filters.tags()) {
                filterSql.append(" and ? = any(s.tags)\n");
                args.add(tag);
            }
        }
        if (filters != null && filters.maintainerId() != null) {
            filterSql.append(" and s.object_type = 'document' and s.maintainer_id = ?\n");
            args.add(filters.maintainerId());
        }
        if (filters != null && filters.knowledgeStatus() != null) {
            filterSql.append(" and s.object_type = 'document' and s.knowledge_status = ?\n");
            args.add(filters.knowledgeStatus());
        }
        if (filters != null && filters.updatedFrom() != null) {
            filterSql.append(" and s.updated_at >= ?\n");
            args.add(Timestamp.from(filters.updatedFrom()));
        }
        if (filters != null && filters.updatedTo() != null) {
            filterSql.append(" and s.updated_at < ?\n");
            args.add(Timestamp.from(filters.updatedTo()));
        }

        for (int i = 0; i < 11; i++) {
            args.add(userId);
        }
        args.add(limit);

        return jdbcTemplate.query(
            """
                select s.object_type, s.object_id, s.title, s.excerpt,
                       coalesce(
                           case
                               when s.object_type = 'document' and s.knowledge_base_id is not null and document_comment.thread_id is not null
                                   then '/knowledge-bases/' || s.knowledge_base_id::text || '?docId=' || s.object_id::text || '&commentId=' || document_comment.thread_id::text
                           end,
                           case
                               when s.object_type = 'document' and s.knowledge_base_id is not null and document_block.id is not null
                                   then '/knowledge-bases/' || s.knowledge_base_id::text || '?docId=' || s.object_id::text || '#doc-block-' || document_block.id::text
                           end,
                           case
                               when s.object_type = 'document' and s.knowledge_base_id is not null
                                   then '/knowledge-bases/' || s.knowledge_base_id::text || '?docId=' || s.object_id::text
                           end,
                           case
                               when s.object_type = 'document' and document_comment.thread_id is not null
                                   then '/docs/' || s.object_id::text || '?commentId=' || document_comment.thread_id::text
                           end,
                           case
                               when s.object_type = 'document' and document_block.id is not null
                                   then '/docs/' || s.object_id::text || '#doc-block-' || document_block.id::text
                           end,
                           s.web_path
                       ) web_path,
                       s.deep_link,
                       ts_rank_cd(to_tsvector('simple', s.search_text), plainto_tsquery('simple', ?))
                         + case when lower(s.search_text) like ? or lower(s.title) like ? then 0.25 else 0 end score,
                       s.updated_at,
                       s.knowledge_base_id,
                       kb.name knowledge_base_name,
                       s.parent_document_id,
                       s.directory_path,
                       s.tags,
                       s.maintainer_id,
                       coalesce(maintainer.display_name, maintainer.username) maintainer_name,
                       s.knowledge_status,
                       s.doc_type,
                       case
                           when lower(s.title) like ? then 'title'
                           when lower(coalesce(array_to_string(s.tags, ' '), '')) like ? then 'tags'
                           when document_comment.thread_id is not null then 'comment'
                           when document_block.id is not null then 'body_block'
                           when lower(coalesce(s.directory_path, '')) like ? then 'directory_path'
                           else s.hit_source
                       end hit_source
                from search_index_documents s
                left join users maintainer on maintainer.id = s.maintainer_id and maintainer.workspace_id = s.workspace_id
                left join knowledge_base_spaces kb on kb.id = s.knowledge_base_id
                    and kb.workspace_id = s.workspace_id
                    and kb.deleted_at is null
                left join lateral (
                    select c.thread_id
                    from document_comments c
                    where s.object_type = 'document'
                      and c.workspace_id = s.workspace_id
                      and c.document_id = s.object_id
                      and c.deleted_at is null
                      and (
                          lower(c.content) like ?
                          or lower(coalesce(c.anchor_text, '')) like ?
                      )
                    order by c.created_at
                    limit 1
                ) document_comment on true
                left join lateral (
                    select db.id
                    from document_blocks db
                    where s.object_type = 'document'
                      and document_comment.thread_id is null
                      and db.workspace_id = s.workspace_id
                      and db.document_id = s.object_id
                      and db.deleted_at is null
                      and lower(coalesce(nullif(db.plain_text, ''), db.content, '')) like ?
                    order by db.sort_order
                    limit 1
                ) document_block on true
                where s.workspace_id = ?
                  and (
                      to_tsvector('simple', s.search_text) @@ plainto_tsquery('simple', ?)
                      or lower(s.search_text) like ?
                      or lower(s.title) like ?
                  )
                  %s
                  and (
                      (
                          s.object_type = 'issue'
                          and exists (
                              select 1
                              from issues i
                              join project_members pm on pm.project_id = i.project_id and pm.user_id = ? and pm.archived_at is null
                              where i.workspace_id = s.workspace_id and i.id = s.object_id and i.deleted_at is null
                          )
                      )
                      or (
                          s.object_type = 'document'
                          and exists (
                              select 1
                              from documents d
                              where d.workspace_id = s.workspace_id and d.id = s.object_id and d.deleted_at is null
                                and d.archived_at is null
                                and (
                                    d.created_by = ?
                                    or exists (
                                        select 1
                                        from resource_permissions rp
                                        where rp.workspace_id = d.workspace_id
                                          and rp.resource_type = 'document'
                                          and rp.resource_id = d.id
                                          and rp.status = 'active'
                                          and (rp.expires_at is null or rp.expires_at > now())
                                          and (
                                              (rp.subject_type = 'user' and rp.subject_id = ?)
                                              or (
                                                  rp.subject_type = 'department'
                                                  and exists (
                                                      select 1
                                                      from department_members dm
                                                      join departments dep on dep.id = dm.department_id
                                                          and dep.workspace_id = dm.workspace_id
                                                          and dep.status = 'active'
                                                          and dep.deleted_at is null
                                                      where dm.workspace_id = rp.workspace_id
                                                        and dm.department_id = rp.subject_id
                                                        and dm.user_id = ?
                                                        and dm.ended_at is null
                                                  )
                                              )
                                              or (
                                                  rp.subject_type = 'user_group'
                                                  and exists (
                                                      select 1
                                                      from user_groups ug
                                                      where ug.id = rp.subject_id
                                                        and ug.workspace_id = rp.workspace_id
                                                        and ug.status = 'active'
                                                        and ug.deleted_at is null
                                                        and exists (
                                                            select 1
                                                            from user_group_members ugm
                                                            where ugm.workspace_id = rp.workspace_id
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
                                                            where ugm.workspace_id = rp.workspace_id
                                                              and ugm.group_id = ug.id
                                                              and ugm.subject_type = 'department'
                                                              and ugm.removed_at is null
                                                        )
                                                  )
                                              )
                                              or (
                                                  rp.subject_type = 'role'
                                                  and exists (
                                                      select 1
                                                      from user_roles ur
                                                      where ur.workspace_id = rp.workspace_id
                                                        and ur.role_id = rp.subject_id
                                                        and ur.user_id = ?
                                                  )
                                              )
                                          )
                                    )
                                )
                          )
                      )
                      or (
                          s.object_type = 'base'
                          and exists (
                              select 1
                              from bases b
                              join base_members bm on bm.base_id = b.id and bm.user_id = ? and bm.revoked_at is null
                              where b.workspace_id = s.workspace_id and b.id = s.object_id and b.archived_at is null
                          )
                      )
                      or (
                          s.object_type = 'base_table'
                          and exists (
                              select 1
                              from base_tables bt
                              join bases b on b.id = bt.base_id and b.archived_at is null
                              join base_members bm on bm.base_id = b.id and bm.user_id = ? and bm.revoked_at is null
                              where bt.workspace_id = s.workspace_id and bt.id = s.object_id and bt.archived_at is null
                          )
                      )
                      or (
                          s.object_type = 'base_record'
                          and exists (
                              select 1
                              from base_records br
                              join base_tables bt on bt.id = br.table_id and bt.archived_at is null
                              join bases b on b.id = bt.base_id and b.archived_at is null
                              join base_members bm on bm.base_id = b.id and bm.user_id = ? and bm.revoked_at is null
                              where br.workspace_id = s.workspace_id and br.id = s.object_id and br.deleted_at is null
                          )
                      )
                      or (
                          s.object_type = 'message'
                          and exists (
                              select 1
                              from messages m
                              join conversations c on c.id = m.conversation_id and c.archived_at is null
                              join conversation_members cm on cm.conversation_id = c.id and cm.user_id = ? and cm.archived_at is null
                              where m.workspace_id = s.workspace_id and m.id = s.object_id and m.deleted_at is null and m.revoked_at is null
                          )
                      )
                  )
                order by score desc, s.updated_at desc
                limit ?
                """.formatted(filterSql),
            this::mapResult,
            args.toArray()
        );
    }

    @Override
    @Transactional
    public synchronized void refreshWorkspaceIndex(UUID workspaceId) {
        // Serialize same-workspace rebuilds across bean instances and test contexts.
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", rs -> { }, "search-index:" + workspaceId);
        jdbcTemplate.update("delete from search_index_documents where workspace_id = ?", workspaceId);
        indexIssues(workspaceId);
        indexDocuments(workspaceId);
        indexBases(workspaceId);
        indexBaseTables(workspaceId);
        indexBaseRecords(workspaceId);
        indexMessages(workspaceId);
    }

    private void indexIssues(UUID workspaceId) {
        jdbcTemplate.update(
            """
                insert into search_index_documents
                    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
                select i.workspace_id,
                       'issue',
                       i.id,
                       i.issue_key || ' ' || i.title,
                       left(coalesce(i.description, i.title), 240),
                       '/issues/' || i.id::text,
                       'colla://issue/' || i.id::text,
                       coalesce(i.issue_key, '') || ' ' || coalesce(i.title, '') || ' ' || coalesce(i.description, ''),
                       i.updated_at,
                       now()
                from issues i
                where i.workspace_id = ? and i.deleted_at is null
                on conflict (workspace_id, object_type, object_id)
                do update set title = excluded.title,
                              excerpt = excluded.excerpt,
                              web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              search_text = excluded.search_text,
                              updated_at = excluded.updated_at,
                              indexed_at = now()
                """,
            workspaceId
        );
    }

    private void indexDocuments(UUID workspaceId) {
        jdbcTemplate.update(
            """
                insert into search_index_documents
                    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at,
                     knowledge_base_id, parent_document_id, directory_path, tags, maintainer_id, knowledge_status, doc_type, hit_source)
                select d.workspace_id,
                       'document',
                       d.id,
                       d.title,
                       left(coalesce(nullif(blocks.block_text, ''), d.content, d.title), 240),
                       case
                           when kb.id is null then '/docs/' || d.id::text
                           else '/knowledge-bases/' || kb.id::text || '?docId=' || d.id::text
                       end,
                       'colla://document/' || d.id::text,
                       coalesce(d.title, '') || ' ' ||
                           coalesce(kb.name, '') || ' ' ||
                           coalesce(d.description, '') || ' ' ||
                           coalesce(d.content, '') || ' ' ||
                           coalesce(blocks.block_text, '') || ' ' ||
                           coalesce(comments.comment_text, '') || ' ' ||
                           coalesce(array_to_string(d.tags, ' '), '') || ' ' ||
                           coalesce(d.category, '') || ' ' ||
                           coalesce(d.knowledge_status, '') || ' ' ||
                           coalesce(paths.directory_path, ''),
                       d.updated_at,
                       now(),
                       kb.id,
                       d.parent_id,
                       paths.directory_path,
                       d.tags,
                       d.maintainer_id,
                       d.knowledge_status,
                       d.doc_type,
                       'title'
                from documents d
                left join lateral (
                    select k.id, k.name
                    from knowledge_base_spaces k
                    where k.workspace_id = d.workspace_id
                      and k.deleted_at is null
                      and exists (
                          with recursive subtree(id) as (
                              select root.id
                              from documents root
                              where root.workspace_id = k.workspace_id
                                and root.id = k.root_document_id
                                and root.deleted_at is null
                              union all
                              select child.id
                              from documents child
                              join subtree parent on parent.id = child.parent_id
                              where child.workspace_id = k.workspace_id
                                and child.deleted_at is null
                          )
                          select 1 from subtree where subtree.id = d.id
                      )
                    order by k.created_at desc
                    limit 1
                ) kb on true
                left join lateral (
                    with recursive ancestors(id, parent_id, title, depth) as (
                        select current_doc.id, current_doc.parent_id, current_doc.title, 0
                        from documents current_doc
                        where current_doc.workspace_id = d.workspace_id and current_doc.id = d.id
                        union all
                        select parent.id, parent.parent_id, parent.title, ancestors.depth + 1
                        from documents parent
                        join ancestors on ancestors.parent_id = parent.id
                        where parent.workspace_id = d.workspace_id and parent.deleted_at is null
                    )
                    select string_agg(title, ' / ' order by depth desc) directory_path
                    from ancestors
                ) paths on true
                left join lateral (
                    select string_agg(
                        case
                            when coalesce(db.plain_text, '') <> ''
                                then db.plain_text
                            when db.block_type in ('table', 'embed', 'embed_object', 'base_view', 'issue_embed', 'message_embed', 'file_embed', 'link', 'link_card')
                                then translate(coalesce(db.content, ''), '{}[]":,', '       ')
                            else coalesce(db.content, '')
                        end,
                        ' ' order by db.sort_order
                    ) block_text
                    from document_blocks db
                    where db.workspace_id = d.workspace_id
                      and db.document_id = d.id
                      and db.deleted_at is null
                ) blocks on true
                left join lateral (
                    select string_agg(coalesce(c.content, '') || ' ' || coalesce(c.anchor_text, ''), ' ' order by c.created_at) comment_text
                    from document_comments c
                    where c.workspace_id = d.workspace_id
                      and c.document_id = d.id
                      and c.deleted_at is null
                ) comments on true
                where d.workspace_id = ? and d.deleted_at is null and d.archived_at is null
                on conflict (workspace_id, object_type, object_id)
                do update set title = excluded.title,
                              excerpt = excluded.excerpt,
                              web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              search_text = excluded.search_text,
                              updated_at = excluded.updated_at,
                              knowledge_base_id = excluded.knowledge_base_id,
                              parent_document_id = excluded.parent_document_id,
                              directory_path = excluded.directory_path,
                              tags = excluded.tags,
                              maintainer_id = excluded.maintainer_id,
                              knowledge_status = excluded.knowledge_status,
                              doc_type = excluded.doc_type,
                              hit_source = excluded.hit_source,
                              indexed_at = now()
                """,
            workspaceId
        );
    }

    private void indexBaseRecords(UUID workspaceId) {
        jdbcTemplate.update(
            """
                insert into search_index_documents
                    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
                select br.workspace_id,
                       'base_record',
                       br.id,
                       coalesce(max(case when bf.id = bt.primary_field_id then brv.value_text end), '记录 #' || br.record_no::text),
                       left(string_agg(coalesce(brv.value_text, ''), ' ' order by bf.sort_order), 240),
                       '/bases/' || b.id::text || '/tables/' || bt.id::text || '/records/' || br.id::text,
                       'colla://base_record/' || br.id::text,
                       string_agg(coalesce(brv.value_text, ''), ' ' order by bf.sort_order),
                       br.updated_at,
                       now()
                from base_records br
                join base_tables bt on bt.id = br.table_id and bt.archived_at is null
                join bases b on b.id = bt.base_id and b.archived_at is null
                join base_record_values brv on brv.record_id = br.id
                join base_fields bf on bf.id = brv.field_id and bf.archived_at is null
                where br.workspace_id = ? and br.deleted_at is null
                group by br.workspace_id, br.id, br.record_no, b.id, bt.id, br.updated_at
                on conflict (workspace_id, object_type, object_id)
                do update set title = excluded.title,
                              excerpt = excluded.excerpt,
                              web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              search_text = excluded.search_text,
                              updated_at = excluded.updated_at,
                              indexed_at = now()
                """,
            workspaceId
        );
    }

    private void indexBases(UUID workspaceId) {
        jdbcTemplate.update(
            """
                insert into search_index_documents
                    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
                select b.workspace_id,
                       'base',
                       b.id,
                       b.name,
                       left(coalesce(b.description, b.name), 240),
                       '/bases/' || b.id::text,
                       'colla://base/' || b.id::text,
                       coalesce(b.name, '') || ' ' || coalesce(b.description, ''),
                       b.updated_at,
                       now()
                from bases b
                where b.workspace_id = ? and b.archived_at is null
                on conflict (workspace_id, object_type, object_id)
                do update set title = excluded.title,
                              excerpt = excluded.excerpt,
                              web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              search_text = excluded.search_text,
                              updated_at = excluded.updated_at,
                              indexed_at = now()
                """,
            workspaceId
        );
    }

    private void indexBaseTables(UUID workspaceId) {
        jdbcTemplate.update(
            """
                insert into search_index_documents
                    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
                select bt.workspace_id,
                       'base_table',
                       bt.id,
                       b.name || ' / ' || bt.name,
                       left(bt.name, 240),
                       '/bases/' || b.id::text || '/tables/' || bt.id::text,
                       'colla://base_table/' || bt.id::text,
                       coalesce(b.name, '') || ' ' || coalesce(bt.name, ''),
                       bt.updated_at,
                       now()
                from base_tables bt
                join bases b on b.id = bt.base_id and b.archived_at is null
                where bt.workspace_id = ? and bt.archived_at is null
                on conflict (workspace_id, object_type, object_id)
                do update set title = excluded.title,
                              excerpt = excluded.excerpt,
                              web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              search_text = excluded.search_text,
                              updated_at = excluded.updated_at,
                              indexed_at = now()
                """,
            workspaceId
        );
    }

    private void indexMessages(UUID workspaceId) {
        jdbcTemplate.update(
            """
                insert into search_index_documents
                    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
                select m.workspace_id,
                       'message',
                       m.id,
                       coalesce(c.title, '会话消息'),
                       left(m.content, 240),
                       '/im?conversationId=' || c.id::text || '&messageId=' || m.id::text,
                       'colla://message/' || m.id::text,
                       coalesce(c.title, '') || ' ' || coalesce(m.content, ''),
                       m.created_at,
                       now()
                from messages m
                join conversations c on c.id = m.conversation_id and c.archived_at is null
                where m.workspace_id = ? and m.deleted_at is null and m.revoked_at is null and coalesce(m.content, '') <> ''
                on conflict (workspace_id, object_type, object_id)
                do update set title = excluded.title,
                              excerpt = excluded.excerpt,
                              web_path = excluded.web_path,
                              deep_link = excluded.deep_link,
                              search_text = excluded.search_text,
                              updated_at = excluded.updated_at,
                              indexed_at = now()
                """,
            workspaceId
        );
    }

    private SearchResult mapResult(ResultSet rs, int rowNum) throws SQLException {
        return new SearchResult(
            rs.getString("object_type"),
            rs.getObject("object_id", UUID.class),
            rs.getString("title"),
            rs.getString("excerpt"),
            rs.getString("web_path"),
            rs.getString("deep_link"),
            rs.getDouble("score"),
            rs.getTimestamp("updated_at").toInstant(),
            "available",
            "当前用户具备该对象的查看权限。",
            rs.getObject("knowledge_base_id", UUID.class),
            rs.getString("knowledge_base_name"),
            rs.getObject("parent_document_id", UUID.class),
            rs.getString("directory_path"),
            textArray(rs, "tags"),
            rs.getObject("maintainer_id", UUID.class),
            rs.getString("maintainer_name"),
            rs.getString("knowledge_status"),
            rs.getString("doc_type"),
            rs.getString("hit_source")
        );
    }

    private List<String> textArray(ResultSet rs, String column) throws SQLException {
        java.sql.Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        if (value instanceof String[] values) {
            return Arrays.asList(values);
        }
        return List.of();
    }
}
