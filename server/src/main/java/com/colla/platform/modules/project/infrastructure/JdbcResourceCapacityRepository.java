package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ResourceCapacityModels.Allocation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityRule;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.MutateAllocationCommand;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.SaveCapacityRuleCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcResourceCapacityRepository implements ResourceCapacityRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcResourceCapacityRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Allocation> listAllocations(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            """
                select id, work_item_id, user_id, start_date, end_date,
                       allocation_percent, status, aggregate_version, updated_by, updated_at
                  from project_resource_allocations
                 where workspace_id=? and space_id=?
                 order by start_date, user_id, id
                 limit ?
                """,
            this::allocation, workspaceId, spaceId, limit
        );
    }

    @Override
    public List<CapacityRule> listRules(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            """
                select id, user_id, daily_minutes, warning_percent,
                       aggregate_version, updated_by, updated_at
                  from project_resource_capacity_rules
                 where workspace_id=? and space_id=?
                 order by user_id
                 limit ?
                """,
            this::rule, workspaceId, spaceId, limit
        );
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId,
        String operation, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select request_hash, response_json
                      from project_resource_capacity_commands
                     where workspace_id=? and space_id=? and actor_id=?
                       and operation=? and request_id=?
                    """,
                (rs, row) -> new CommandRecord(
                    rs.getString("request_hash"), rs.getString("response_json")
                ),
                workspaceId, spaceId, actorId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Allocation mutateAllocation(
        UUID workspaceId, UUID spaceId, UUID actorId,
        MutateAllocationCommand command, String hash
    ) {
        UUID id = command.allocationId() == null ? UUID.randomUUID() : command.allocationId();
        int changed;
        if ("create".equals(command.operation())) {
            changed = jdbc.update(
                """
                    insert into project_resource_allocations(
                        id, workspace_id, space_id, work_item_id, user_id,
                        start_date, end_date, allocation_percent, status,
                        aggregate_version, updated_by, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, 'active', 1, ?, now())
                    on conflict (id) do nothing
                    """,
                id, workspaceId, spaceId, command.workItemId(), command.userId(),
                command.startDate(), command.endDate(), command.allocationPercent(), actorId
            );
        } else if ("update".equals(command.operation())) {
            changed = jdbc.update(
                """
                    update project_resource_allocations
                       set start_date=?, end_date=?, allocation_percent=?,
                           aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                       and status='active' and aggregate_version=?
                    """,
                command.startDate(), command.endDate(), command.allocationPercent(),
                actorId, workspaceId, spaceId, id, command.expectedVersion()
            );
        } else {
            String target = "end".equals(command.operation()) ? "ended" : "archived";
            changed = jdbc.update(
                """
                    update project_resource_allocations
                       set status=?, aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                       and status<>'archived' and aggregate_version=?
                    """,
                target, actorId, workspaceId, spaceId, id, command.expectedVersion()
            );
        }
        if (changed != 1) {
            throw failure(
                "RESOURCE_ALLOCATION_VERSION_CONFLICT",
                "Resource allocation changed concurrently"
            );
        }
        Allocation result = jdbc.queryForObject(
            """
                select id, work_item_id, user_id, start_date, end_date,
                       allocation_percent, status, aggregate_version, updated_by, updated_at
                  from project_resource_allocations
                 where workspace_id=? and space_id=? and id=?
                """,
            this::allocation, workspaceId, spaceId, id
        );
        receipt(
            workspaceId, spaceId, actorId, command.operation(),
            command.requestId(), hash, result
        );
        return result;
    }

    @Override
    @Transactional
    public CapacityRule saveRule(
        UUID workspaceId, UUID spaceId, UUID actorId,
        SaveCapacityRuleCommand command, String hash
    ) {
        int changed;
        if (command.expectedVersion() == 0) {
            changed = jdbc.update(
                """
                    insert into project_resource_capacity_rules(
                        id, workspace_id, space_id, user_id, daily_minutes,
                        warning_percent, aggregate_version, updated_by, updated_at
                    ) values (?, ?, ?, ?, ?, ?, 1, ?, now())
                    on conflict (workspace_id, space_id, user_id) do nothing
                    """,
                UUID.randomUUID(), workspaceId, spaceId, command.userId(),
                command.dailyMinutes(), command.warningPercent(), actorId
            );
        } else {
            changed = jdbc.update(
                """
                    update project_resource_capacity_rules
                       set daily_minutes=?, warning_percent=?,
                           aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and user_id=?
                       and aggregate_version=?
                    """,
                command.dailyMinutes(), command.warningPercent(), actorId,
                workspaceId, spaceId, command.userId(), command.expectedVersion()
            );
        }
        if (changed != 1) {
            throw failure(
                "RESOURCE_CAPACITY_RULE_VERSION_CONFLICT",
                "Capacity rule changed concurrently"
            );
        }
        CapacityRule result = jdbc.queryForObject(
            """
                select id, user_id, daily_minutes, warning_percent,
                       aggregate_version, updated_by, updated_at
                  from project_resource_capacity_rules
                 where workspace_id=? and space_id=? and user_id=?
                """,
            this::rule, workspaceId, spaceId, command.userId()
        );
        receipt(
            workspaceId, spaceId, actorId, "save_rule",
            command.requestId(), hash, result
        );
        return result;
    }

    private Allocation allocation(ResultSet rs, int row) throws SQLException {
        return new Allocation(
            rs.getObject("id", UUID.class),
            rs.getObject("work_item_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("start_date", java.time.LocalDate.class),
            rs.getObject("end_date", java.time.LocalDate.class),
            rs.getBigDecimal("allocation_percent"),
            rs.getString("status"),
            rs.getLong("aggregate_version"),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private CapacityRule rule(ResultSet rs, int row) throws SQLException {
        return new CapacityRule(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getInt("daily_minutes"),
            rs.getBigDecimal("warning_percent"),
            rs.getLong("aggregate_version"),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private void receipt(
        UUID workspaceId, UUID spaceId, UUID actorId,
        String operation, String requestId, String hash, Object response
    ) {
        jdbc.update(
            """
                insert into project_resource_capacity_commands(
                    id, workspace_id, space_id, actor_id, operation,
                    request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, operation,
            requestId, hash, json(response)
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize resource capacity", exception);
        }
    }
}
