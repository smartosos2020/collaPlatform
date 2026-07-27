package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.contract.DraftSummaryQuery;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectDraftSummaryQuery implements DraftSummaryQuery {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectDraftSummaryQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DraftSummary> listOwn(CurrentUser user, int limit) {
        return jdbcTemplate.query(
            """
                select d.id, d.space_id, s.name space_name, d.type_definition_id,
                       t.name type_name, d.status, d.aggregate_version, d.updated_at
                from project_work_item_configuration_drafts d
                join project_spaces s
                  on s.workspace_id = d.workspace_id and s.id = d.space_id
                join project_work_item_types t
                  on t.workspace_id = d.workspace_id
                 and t.space_id = d.space_id
                 and t.id = d.type_definition_id
                where d.workspace_id = ?
                  and d.updated_by = ?
                  and d.status in ('editing', 'validating', 'valid', 'invalid')
                  and s.status = 'active'
                  and exists (
                      select 1 from project_space_members m
                      where m.workspace_id = d.workspace_id
                        and m.space_id = d.space_id
                        and m.user_id = ?
                        and m.status = 'active'
                  )
                order by d.updated_at desc, d.id
                limit ?
                """,
            (rs, rowNum) -> new DraftSummary(
                rs.getObject("id", java.util.UUID.class),
                rs.getObject("space_id", java.util.UUID.class),
                rs.getString("space_name"),
                rs.getObject("type_definition_id", java.util.UUID.class),
                rs.getString("type_name"),
                rs.getString("status"),
                rs.getLong("aggregate_version"),
                rs.getTimestamp("updated_at").toInstant(),
                "/project-spaces/" + rs.getObject("space_id", java.util.UUID.class)
                    + "/types/" + rs.getObject("type_definition_id", java.util.UUID.class)
            ),
            user.workspaceId(),
            user.id(),
            user.id(),
            Math.min(Math.max(limit, 1), 50)
        );
    }
}
