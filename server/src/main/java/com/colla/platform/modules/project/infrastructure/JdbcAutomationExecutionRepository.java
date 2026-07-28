package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.AutomationExecutionModels.AutomationRun;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.AutomationStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
public class JdbcAutomationExecutionRepository implements AutomationExecutionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAutomationExecutionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StartResult begin(
        UUID workspaceId, UUID spaceId, UUID ruleId, int ruleVersion,
        String sourceType, String sourceKey, UUID actorId, boolean dryRun,
        String inputHash
    ) {
        Optional<AutomationRun> existing = findBySource(
            workspaceId, spaceId, ruleId, ruleVersion, sourceType, sourceKey
        );
        if (existing.isPresent()) {
            if (!existing.get().inputHash().equals(inputHash)
                || existing.get().dryRun() != dryRun) {
                throw failure(
                    "AUTOMATION_RUN_REQUEST_CONFLICT",
                    "Automation source key was reused with different input"
                );
            }
            return new StartResult(existing.get(), true);
        }
        UUID runId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_automation_runs(
                    id, workspace_id, space_id, rule_id, rule_version,
                    source_type, source_key, actor_id, status, dry_run,
                    input_hash, worker_id, lease_until, fencing_token,
                    attempt, started_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'running', ?, ?,
                          'inline', now() + interval '30 seconds', 1, 1, now())
                """,
            runId, workspaceId, spaceId, ruleId, ruleVersion,
            sourceType, sourceKey, actorId, dryRun, inputHash
        );
        return new StartResult(get(workspaceId, spaceId, runId), false);
    }

    @Override
    @Transactional
    public AutomationStep startStep(
        UUID workspaceId, UUID spaceId, UUID runId, int stepNumber,
        String actionType, String inputHash
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_automation_run_steps(
                    id, workspace_id, space_id, run_id, step_number,
                    action_type, status, input_hash, started_at
                ) values (?, ?, ?, ?, ?, ?, 'running', ?, now())
                on conflict (workspace_id, space_id, run_id, step_number) do nothing
                """,
            id, workspaceId, spaceId, runId, stepNumber, actionType, inputHash
        );
        return requireStep(workspaceId, spaceId, runId, stepNumber);
    }

    @Override
    @Transactional
    public void completeStep(
        UUID workspaceId, UUID spaceId, UUID runId, int stepNumber,
        String status, JsonNode result, String errorCode
    ) {
        jdbc.update(
            """
                update project_automation_run_steps
                   set status=?, result_json=cast(? as jsonb), error_code=?,
                       completed_at=now()
                 where workspace_id=? and space_id=? and run_id=? and step_number=?
                """,
            status, json(result == null ? objectMapper.createObjectNode() : result),
            errorCode, workspaceId, spaceId, runId, stepNumber
        );
    }

    @Override
    @Transactional
    public void completeRun(
        UUID workspaceId, UUID spaceId, UUID runId,
        String status, JsonNode output, String errorCode
    ) {
        jdbc.update(
            """
                update project_automation_runs
                   set status=?, output_json=cast(? as jsonb), error_code=?,
                       worker_id=null, lease_until=null, completed_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and status in ('pending', 'running')
                """,
            status, json(output == null ? objectMapper.createObjectNode() : output),
            errorCode, workspaceId, spaceId, runId
        );
        updateStats(workspaceId, spaceId);
    }

    @Override
    public Optional<ActionReceipt> findActionReceipt(
        UUID workspaceId, UUID spaceId, UUID ruleId, int ruleVersion,
        int actionIndex, String idempotencyKey
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select input_hash, response_json
                      from project_automation_action_receipts
                     where workspace_id=? and space_id=? and rule_id=?
                       and rule_version=? and action_index=? and idempotency_key=?
                    """,
                (rs, row) -> new ActionReceipt(
                    rs.getString("input_hash"), tree(rs.getString("response_json"))
                ),
                workspaceId, spaceId, ruleId, ruleVersion,
                actionIndex, idempotencyKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void saveActionReceipt(
        UUID workspaceId, UUID spaceId, UUID ruleId, int ruleVersion,
        int actionIndex, String idempotencyKey, String inputHash,
        JsonNode response
    ) {
        jdbc.update(
            """
                insert into project_automation_action_receipts(
                    id, workspace_id, space_id, rule_id, rule_version,
                    action_index, idempotency_key, input_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                on conflict (workspace_id, space_id, rule_id, rule_version,
                             action_index, idempotency_key) do nothing
                """,
            UUID.randomUUID(), workspaceId, spaceId, ruleId, ruleVersion,
            actionIndex, idempotencyKey, inputHash, json(response)
        );
    }

    @Override
    public AutomationRun get(UUID workspaceId, UUID spaceId, UUID runId) {
        try {
            return jdbc.queryForObject(
                """
                    select id, rule_id, rule_version, source_type, source_key,
                           actor_id, status, dry_run, input_hash, error_code,
                           fencing_token, attempt, started_at, completed_at
                      from project_automation_runs
                     where workspace_id=? and space_id=? and id=?
                    """,
                (rs, row) -> run(workspaceId, spaceId, rs),
                workspaceId, spaceId, runId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw failure(
                "NOT_FOUND_OR_HIDDEN", "Automation run is not available"
            );
        }
    }

    @Override
    public List<AutomationRun> list(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            """
                select id, rule_id, rule_version, source_type, source_key,
                       actor_id, status, dry_run, input_hash, error_code,
                       fencing_token, attempt, started_at, completed_at
                  from project_automation_runs
                 where workspace_id=? and space_id=?
                 order by started_at desc, id
                 limit ?
                """,
            (rs, row) -> run(workspaceId, spaceId, rs),
            workspaceId, spaceId, limit
        );
    }

    private Optional<AutomationRun> findBySource(
        UUID workspaceId, UUID spaceId, UUID ruleId, int ruleVersion,
        String sourceType, String sourceKey
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select id, rule_id, rule_version, source_type, source_key,
                           actor_id, status, dry_run, input_hash, error_code,
                           fencing_token, attempt, started_at, completed_at
                      from project_automation_runs
                     where workspace_id=? and space_id=? and rule_id=?
                       and rule_version=? and source_type=? and source_key=?
                    """,
                (rs, row) -> run(workspaceId, spaceId, rs),
                workspaceId, spaceId, ruleId, ruleVersion, sourceType, sourceKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private AutomationRun run(
        UUID workspaceId, UUID spaceId, ResultSet rs
    ) throws SQLException {
        UUID runId = rs.getObject("id", UUID.class);
        List<AutomationStep> steps = jdbc.query(
            """
                select id, step_number, action_type, status, input_hash,
                       result_json, error_code, started_at, completed_at
                  from project_automation_run_steps
                 where workspace_id=? and space_id=? and run_id=?
                 order by step_number
                """,
            this::step, workspaceId, spaceId, runId
        );
        return new AutomationRun(
            runId,
            rs.getObject("rule_id", UUID.class),
            rs.getInt("rule_version"),
            rs.getString("source_type"),
            rs.getString("source_key"),
            rs.getObject("actor_id", UUID.class),
            rs.getString("status"),
            rs.getBoolean("dry_run"),
            rs.getString("input_hash"),
            steps,
            rs.getString("error_code"),
            rs.getLong("fencing_token"),
            rs.getInt("attempt"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("completed_at") == null
                ? null : rs.getTimestamp("completed_at").toInstant()
        );
    }

    private AutomationStep step(ResultSet rs, int row) throws SQLException {
        return new AutomationStep(
            rs.getObject("id", UUID.class),
            rs.getInt("step_number"),
            rs.getString("action_type"),
            rs.getString("status"),
            rs.getString("input_hash"),
            rs.getString("result_json") == null
                ? objectMapper.createObjectNode()
                : tree(rs.getString("result_json")),
            rs.getString("error_code"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("completed_at") == null
                ? null : rs.getTimestamp("completed_at").toInstant()
        );
    }

    private AutomationStep requireStep(
        UUID workspaceId, UUID spaceId, UUID runId, int stepNumber
    ) {
        return jdbc.queryForObject(
            """
                select id, step_number, action_type, status, input_hash,
                       result_json, error_code, started_at, completed_at
                  from project_automation_run_steps
                 where workspace_id=? and space_id=? and run_id=? and step_number=?
                """,
            this::step, workspaceId, spaceId, runId, stepNumber
        );
    }

    private void updateStats(UUID workspaceId, UUID spaceId) {
        jdbc.update(
            """
                insert into project_automation_execution_stats(
                    workspace_id, space_id, observed_date, run_count,
                    success_count, failure_count, truncated, updated_at
                )
                select ?, ?, current_date, least(count(*), 1000)::int,
                       least(count(*) filter (where status='succeeded'), 1000)::int,
                       least(count(*) filter (where status='failed'), 1000)::int,
                       count(*) > 1000, now()
                  from project_automation_runs
                 where workspace_id=? and space_id=? and started_at >= current_date
                on conflict (workspace_id, space_id, observed_date)
                do update set run_count=excluded.run_count,
                              success_count=excluded.success_count,
                              failure_count=excluded.failure_count,
                              truncated=excluded.truncated,
                              updated_at=excluded.updated_at
                """,
            workspaceId, spaceId, workspaceId, spaceId
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode tree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
