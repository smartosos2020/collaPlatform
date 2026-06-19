package com.colla.platform.modules.search.infrastructure;

import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JdbcSearchRepository implements SearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResult> search(UUID workspaceId, UUID userId, String query, int limit) {
        String likeQuery = "%" + query.toLowerCase() + "%";
        return jdbcTemplate.query(
            """
                select s.object_type, s.object_id, s.title, s.excerpt, s.web_path, s.deep_link,
                       ts_rank_cd(to_tsvector('simple', s.search_text), plainto_tsquery('simple', ?))
                         + case when lower(s.search_text) like ? or lower(s.title) like ? then 0.25 else 0 end score,
                       s.updated_at
                from search_index_documents s
                where s.workspace_id = ?
                  and (
                      to_tsvector('simple', s.search_text) @@ plainto_tsquery('simple', ?)
                      or lower(s.search_text) like ?
                      or lower(s.title) like ?
                  )
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
                              left join document_permissions dp on dp.document_id = d.id and dp.user_id = ? and dp.revoked_at is null
                              where d.workspace_id = s.workspace_id and d.id = s.object_id and d.deleted_at is null
                                and d.archived_at is null
                                and (d.created_by = ? or dp.user_id is not null)
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
                """,
            this::mapResult,
            query,
            likeQuery,
            likeQuery,
            workspaceId,
            query,
            likeQuery,
            likeQuery,
            userId,
            userId,
            userId,
            userId,
            userId,
            userId,
            userId,
            limit
        );
    }

    @Override
    @Transactional
    public synchronized void refreshWorkspaceIndex(UUID workspaceId) {
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
                    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
                select d.workspace_id,
                       'document',
                       d.id,
                       d.title,
                       left(coalesce(d.content, d.title), 240),
                       '/docs/' || d.id::text,
                       'colla://document/' || d.id::text,
                       coalesce(d.title, '') || ' ' || coalesce(d.content, ''),
                       d.updated_at,
                       now()
                from documents d
                where d.workspace_id = ? and d.deleted_at is null and d.archived_at is null
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
            "当前用户具备该对象的查看权限。"
        );
    }
}
