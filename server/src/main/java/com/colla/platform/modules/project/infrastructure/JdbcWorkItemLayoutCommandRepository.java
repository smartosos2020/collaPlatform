package com.colla.platform.modules.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemLayoutCommandRepository implements WorkItemLayoutCommandRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkItemLayoutCommandRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryStart(CommandStart command) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_layout_commands
                    (id, workspace_id, space_id, type_definition_id, request_id, operation,
                     request_hash, status, response_schema_version, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, 'pending', 1, ?, now())
                on conflict (workspace_id, request_id) do nothing
                """,
            command.id(),
            command.workspaceId(),
            command.spaceId(),
            command.typeDefinitionId(),
            command.requestId(),
            command.operation(),
            command.requestHash(),
            command.actorId()
        ) == 1;
    }

    @Override
    public Optional<CommandReceipt> find(UUID workspaceId, String requestId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, space_id, type_definition_id, request_id, operation,
                           request_hash, status, response_schema_version, response_layout_id,
                           response_aggregate_version, response_config_hash, response_payload,
                           created_by, created_at, completed_at
                      from project_work_item_layout_commands
                     where workspace_id = ? and request_id = ?
                    """,
                this::map,
                workspaceId,
                requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void complete(UUID commandId, CommandResponse response) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_layout_commands
                   set status = 'completed',
                       response_layout_id = ?,
                       response_aggregate_version = ?,
                       response_config_hash = ?,
                       response_payload = ?::jsonb,
                       completed_at = now()
                 where id = ?
                   and status = 'pending'
                   and response_schema_version = 1
                   and response_layout_id is null
                   and response_payload is null
                """,
            response.layoutId(),
            response.aggregateVersion(),
            response.configHash(),
            response.payload(),
            commandId
        );
        if (updated != 1) {
            throw new IllegalStateException("Work item layout command could not be completed");
        }
    }

    private CommandReceipt map(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp completedAt = resultSet.getTimestamp("completed_at");
        return new CommandReceipt(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getString("request_id"),
            resultSet.getString("operation"),
            resultSet.getString("request_hash"),
            resultSet.getString("status"),
            resultSet.getObject("response_schema_version", Integer.class),
            resultSet.getObject("response_layout_id", UUID.class),
            resultSet.getObject("response_aggregate_version", Long.class),
            resultSet.getString("response_config_hash"),
            resultSet.getString("response_payload"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            completedAt == null ? null : completedAt.toInstant()
        );
    }
}
