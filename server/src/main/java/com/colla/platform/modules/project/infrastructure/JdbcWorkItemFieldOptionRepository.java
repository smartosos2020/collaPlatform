package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemFieldOptionRepository implements WorkItemFieldOptionRepository {
    private static final String SELECT = """
        select id, workspace_id, space_id, type_definition_id, field_definition_id,
               option_key, name, color, sort_order, status, created_by, created_at, updated_by, updated_at
          from project_work_item_field_options
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkItemFieldOptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FieldOption> listByField(UUID workspaceId, UUID spaceId, UUID typeId, UUID fieldId) {
        return jdbcTemplate.query(
            SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ? and field_definition_id = ?
                 order by sort_order, option_key, id
                """,
            this::map,
            workspaceId,
            spaceId,
            typeId,
            fieldId
        );
    }

    @Override
    public void insert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        ConfigureFieldOption option,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_field_options
                    (id, workspace_id, space_id, type_definition_id, field_definition_id,
                     option_key, name, color, sort_order, status,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now())
                """,
            id, workspaceId, spaceId, typeId, fieldId,
            option.optionKey(), option.name(), option.color(), option.sortOrder(), option.status(),
            actorId, actorId
        );
    }

    @Override
    public int update(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String optionKey,
        ConfigureFieldOption option,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_field_options
                   set name = ?, color = ?, sort_order = ?, status = ?, updated_by = ?, updated_at = now()
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and field_definition_id = ? and option_key = ?
                """,
            option.name(), option.color(), option.sortOrder(), option.status(), actorId,
            workspaceId, spaceId, typeId, fieldId, optionKey
        );
    }

    private FieldOption map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FieldOption(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getObject("field_definition_id", UUID.class),
            resultSet.getString("option_key"),
            resultSet.getString("name"),
            resultSet.getString("color"),
            resultSet.getInt("sort_order"),
            resultSet.getString("status"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("updated_by", UUID.class),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
