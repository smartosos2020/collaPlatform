package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.infrastructure.WorkItemFieldCommandRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldCommandRepository.CommandStart;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemFieldCommandRepository implements WorkItemFieldCommandRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkItemFieldCommandRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryStart(CommandStart command) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_field_commands
                    (id, workspace_id, space_id, type_definition_id, request_id, operation,
                     request_hash, status, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, 'pending', ?, now())
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
                           request_hash, status, response_field_id, created_by, created_at, completed_at
                      from project_work_item_field_commands
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
    public void complete(UUID commandId, UUID responseFieldId) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_field_commands
                   set status = 'completed', response_field_id = ?, completed_at = now()
                 where id = ? and status = 'pending'
                """,
            responseFieldId,
            commandId
        );
        if (updated != 1) {
            throw new IllegalStateException("Work item field command could not be completed");
        }
    }

    private CommandReceipt map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommandReceipt(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getString("request_id"),
            resultSet.getString("operation"),
            resultSet.getString("request_hash"),
            resultSet.getString("status"),
            resultSet.getObject("response_field_id", UUID.class),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            instant(resultSet.getTimestamp("completed_at"))
        );
    }

    private java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
