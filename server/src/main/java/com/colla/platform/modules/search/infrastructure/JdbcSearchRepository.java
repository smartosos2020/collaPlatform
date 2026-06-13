package com.colla.platform.modules.search.infrastructure;

import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSearchRepository implements SearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResult> search(UUID workspaceId, UUID userId, String query, int limit) {
        List<SearchResult> results = new ArrayList<>();
        results.addAll(searchIssues(workspaceId, userId, query, limit));
        results.addAll(searchDocuments(workspaceId, userId, query, limit));
        results.addAll(searchBaseRecords(workspaceId, userId, query, limit));
        results.addAll(searchMessages(workspaceId, userId, query, limit));
        return results.stream()
            .sorted(Comparator.comparingDouble(SearchResult::score).reversed().thenComparing(SearchResult::updatedAt, Comparator.reverseOrder()))
            .limit(limit)
            .toList();
    }

    private List<SearchResult> searchIssues(UUID workspaceId, UUID userId, String query, int limit) {
        return jdbcTemplate.query(
            """
                select 'issue' object_type, i.id object_id,
                       i.issue_key || ' ' || i.title title,
                       left(coalesce(i.description, i.title), 240) excerpt,
                       '/issues/' || i.id::text web_path,
                       'colla://issue/' || i.id::text deep_link,
                       ts_rank_cd(to_tsvector('simple', coalesce(i.issue_key, '') || ' ' || coalesce(i.title, '') || ' ' || coalesce(i.description, '')), plainto_tsquery('simple', ?)) score,
                       i.updated_at
                from issues i
                join project_members pm on pm.project_id = i.project_id and pm.user_id = ? and pm.archived_at is null
                where i.workspace_id = ? and i.deleted_at is null
                  and to_tsvector('simple', coalesce(i.issue_key, '') || ' ' || coalesce(i.title, '') || ' ' || coalesce(i.description, ''))
                      @@ plainto_tsquery('simple', ?)
                order by score desc, i.updated_at desc
                limit ?
                """,
            this::mapResult,
            query,
            userId,
            workspaceId,
            query,
            limit
        );
    }

    private List<SearchResult> searchDocuments(UUID workspaceId, UUID userId, String query, int limit) {
        return jdbcTemplate.query(
            """
                select 'document' object_type, d.id object_id,
                       d.title,
                       left(coalesce(d.content, d.title), 240) excerpt,
                       '/docs/' || d.id::text web_path,
                       'colla://document/' || d.id::text deep_link,
                       ts_rank_cd(to_tsvector('simple', coalesce(d.title, '') || ' ' || coalesce(d.content, '')), plainto_tsquery('simple', ?)) score,
                       d.updated_at
                from documents d
                left join document_permissions dp on dp.document_id = d.id and dp.user_id = ? and dp.revoked_at is null
                where d.workspace_id = ? and d.deleted_at is null
                  and (d.created_by = ? or dp.user_id is not null)
                  and to_tsvector('simple', coalesce(d.title, '') || ' ' || coalesce(d.content, '')) @@ plainto_tsquery('simple', ?)
                order by score desc, d.updated_at desc
                limit ?
                """,
            this::mapResult,
            query,
            userId,
            workspaceId,
            userId,
            query,
            limit
        );
    }

    private List<SearchResult> searchBaseRecords(UUID workspaceId, UUID userId, String query, int limit) {
        return jdbcTemplate.query(
            """
                select 'base_record' object_type, br.id object_id,
                       coalesce(max(case when bf.id = bt.primary_field_id then brv.value_text end), '记录 #' || br.record_no::text) title,
                       left(string_agg(coalesce(brv.value_text, ''), ' ' order by bf.sort_order), 240) excerpt,
                       '/bases/' || b.id::text || '/tables/' || bt.id::text || '/records/' || br.id::text web_path,
                       'colla://base_record/' || br.id::text deep_link,
                       max(ts_rank_cd(to_tsvector('simple', coalesce(brv.value_text, '')), plainto_tsquery('simple', ?))) score,
                       br.updated_at
                from base_records br
                join base_tables bt on bt.id = br.table_id and bt.archived_at is null
                join bases b on b.id = bt.base_id and b.archived_at is null
                join base_members bm on bm.base_id = b.id and bm.user_id = ? and bm.revoked_at is null
                join base_record_values brv on brv.record_id = br.id
                join base_fields bf on bf.id = brv.field_id and bf.archived_at is null
                where br.workspace_id = ? and br.deleted_at is null
                  and to_tsvector('simple', coalesce(brv.value_text, '')) @@ plainto_tsquery('simple', ?)
                group by br.id, br.record_no, b.id, bt.id, br.updated_at
                order by score desc, br.updated_at desc
                limit ?
                """,
            this::mapResult,
            query,
            userId,
            workspaceId,
            query,
            limit
        );
    }

    private List<SearchResult> searchMessages(UUID workspaceId, UUID userId, String query, int limit) {
        return jdbcTemplate.query(
            """
                select 'message' object_type, m.id object_id,
                       coalesce(c.title, '会话消息') title,
                       left(m.content, 240) excerpt,
                       '/im?conversationId=' || c.id::text web_path,
                       'colla://message/' || m.id::text deep_link,
                       ts_rank_cd(to_tsvector('simple', coalesce(m.content, '')), plainto_tsquery('simple', ?)) score,
                       m.created_at updated_at
                from messages m
                join conversations c on c.id = m.conversation_id and c.archived_at is null
                join conversation_members cm on cm.conversation_id = c.id and cm.user_id = ? and cm.archived_at is null
                where m.workspace_id = ? and m.deleted_at is null and m.revoked_at is null
                  and to_tsvector('simple', coalesce(m.content, '')) @@ plainto_tsquery('simple', ?)
                order by score desc, m.created_at desc
                limit ?
                """,
            this::mapResult,
            query,
            userId,
            workspaceId,
            query,
            limit
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
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
