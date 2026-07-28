package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationRule;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAutomationRuleRepository implements AutomationRuleRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAutomationRuleRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AutomationRule> list(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            """
                select id, name, status, trigger_json, condition_json, actions_json,
                       aggregate_version, published_version, updated_by, updated_at
                  from project_automation_rules
                 where workspace_id=? and space_id=? and status <> 'archived'
                 order by updated_at desc, id
                 limit ?
                """,
            this::rule, workspaceId, spaceId, limit
        );
    }

    @Override
    public Optional<AutomationRule> find(
        UUID workspaceId, UUID spaceId, UUID ruleId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select id, name, status, trigger_json, condition_json, actions_json,
                           aggregate_version, published_version, updated_by, updated_at
                      from project_automation_rules
                     where workspace_id=? and space_id=? and id=?
                    """,
                this::rule, workspaceId, spaceId, ruleId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RuleVersion> findVersion(
        UUID workspaceId, UUID spaceId, UUID ruleId, int versionNumber
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select id, rule_id, version_number, definition_hash,
                           definition_json, published_by, published_at
                      from project_automation_rule_versions
                     where workspace_id=? and space_id=? and rule_id=? and version_number=?
                    """,
                this::version, workspaceId, spaceId, ruleId, versionNumber
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
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
                      from project_automation_rule_commands
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
    public AutomationRule save(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID requestedRuleId,
        String name, JsonNode trigger, JsonNode condition, JsonNode actions,
        long expectedVersion, String requestId, String requestHash
    ) {
        UUID ruleId = requestedRuleId == null ? UUID.randomUUID() : requestedRuleId;
        int changed;
        if (expectedVersion == 0) {
            changed = jdbc.update(
                """
                    insert into project_automation_rules(
                        id, workspace_id, space_id, name, status,
                        trigger_json, condition_json, actions_json,
                        aggregate_version, published_version, updated_by, updated_at
                    ) values (?, ?, ?, ?, 'draft', cast(? as jsonb), cast(? as jsonb),
                              cast(? as jsonb), 1, null, ?, now())
                    on conflict do nothing
                    """,
                ruleId, workspaceId, spaceId, name,
                json(trigger), json(condition), json(actions), actorId
            );
        } else {
            changed = jdbc.update(
                """
                    update project_automation_rules
                       set name=?, trigger_json=cast(? as jsonb),
                           condition_json=cast(? as jsonb), actions_json=cast(? as jsonb),
                           status=case when status='archived' then status else 'draft' end,
                           aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                       and aggregate_version=? and status <> 'archived'
                    """,
                name, json(trigger), json(condition), json(actions), actorId,
                workspaceId, spaceId, ruleId, expectedVersion
            );
        }
        if (changed != 1) {
            throw failure(
                "AUTOMATION_RULE_VERSION_CONFLICT",
                "Automation rule changed; refresh before saving"
            );
        }
        AutomationRule result = find(workspaceId, spaceId, ruleId).orElseThrow();
        storeCommand(
            workspaceId, spaceId, actorId, "save_rule",
            requestId, requestHash, result
        );
        updateStats(workspaceId, spaceId);
        return result;
    }

    @Override
    @Transactional
    public AutomationRule changeLifecycle(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID ruleId,
        String action, long expectedVersion, String requestId, String requestHash
    ) {
        String operation = action + "_rule";
        String status = switch (action) {
            case "enable" -> "enabled";
            case "disable" -> "disabled";
            case "archive" -> "archived";
            default -> throw new IllegalArgumentException("Unsupported lifecycle action");
        };
        int changed = jdbc.update(
            """
                update project_automation_rules
                   set status=?, aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and aggregate_version=? and status <> 'archived'
                   and (? <> 'enabled' or published_version is not null)
                """,
            status, actorId, workspaceId, spaceId, ruleId,
            expectedVersion, status
        );
        if (changed != 1) {
            throw failure(
                "AUTOMATION_RULE_VERSION_CONFLICT",
                "Automation rule cannot change lifecycle at this version"
            );
        }
        AutomationRule result = find(workspaceId, spaceId, ruleId).orElseThrow();
        storeCommand(
            workspaceId, spaceId, actorId, operation,
            requestId, requestHash, result
        );
        updateStats(workspaceId, spaceId);
        return result;
    }

    @Override
    @Transactional
    public RuleVersion publish(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID ruleId,
        long expectedVersion, String definitionHash, JsonNode definition,
        String requestId, String requestHash
    ) {
        Integer versionNumber = jdbc.queryForObject(
            """
                select coalesce(max(version_number), 0) + 1
                  from project_automation_rule_versions
                 where workspace_id=? and space_id=? and rule_id=?
                """,
            Integer.class, workspaceId, spaceId, ruleId
        );
        UUID versionId = UUID.randomUUID();
        int changed = jdbc.update(
            """
                update project_automation_rules
                   set published_version=?, status='disabled',
                       aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and aggregate_version=? and status <> 'archived'
                """,
            versionNumber, actorId, workspaceId, spaceId, ruleId, expectedVersion
        );
        if (changed != 1) {
            throw failure(
                "AUTOMATION_RULE_VERSION_CONFLICT",
                "Automation rule changed; refresh before publishing"
            );
        }
        jdbc.update(
            """
                insert into project_automation_rule_versions(
                    id, workspace_id, space_id, rule_id, version_number,
                    definition_hash, definition_json, published_by, published_at
                ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, now())
                """,
            versionId, workspaceId, spaceId, ruleId, versionNumber,
            definitionHash, json(definition), actorId
        );
        RuleVersion result = jdbc.queryForObject(
            """
                select id, rule_id, version_number, definition_hash,
                       definition_json, published_by, published_at
                  from project_automation_rule_versions
                 where id=?
                """,
            this::version, versionId
        );
        storeCommand(
            workspaceId, spaceId, actorId, "publish_rule",
            requestId, requestHash, result
        );
        updateStats(workspaceId, spaceId);
        return result;
    }

    private AutomationRule rule(ResultSet rs, int row) throws SQLException {
        return new AutomationRule(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("status"),
            tree(rs.getString("trigger_json")),
            tree(rs.getString("condition_json")),
            tree(rs.getString("actions_json")),
            rs.getLong("aggregate_version"),
            rs.getObject("published_version", Integer.class),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RuleVersion version(ResultSet rs, int row) throws SQLException {
        return new RuleVersion(
            rs.getObject("id", UUID.class),
            rs.getObject("rule_id", UUID.class),
            rs.getInt("version_number"),
            rs.getString("definition_hash"),
            tree(rs.getString("definition_json")),
            rs.getObject("published_by", UUID.class),
            rs.getTimestamp("published_at").toInstant()
        );
    }

    private void storeCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation,
        String requestId, String requestHash, Object response
    ) {
        jdbc.update(
            """
                insert into project_automation_rule_commands(
                    id, workspace_id, space_id, actor_id, operation,
                    request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, operation,
            requestId, requestHash, json(response)
        );
    }

    private void updateStats(UUID workspaceId, UUID spaceId) {
        jdbc.update(
            """
                insert into project_automation_rule_stats(
                    workspace_id, space_id, observed_date, rule_count,
                    enabled_count, truncated, updated_at
                )
                select ?, ?, current_date,
                       least(count(*), 100)::int,
                       least(count(*) filter (where status='enabled'), 100)::int,
                       count(*) > 100, now()
                  from project_automation_rules
                 where workspace_id=? and space_id=? and status <> 'archived'
                on conflict (workspace_id, space_id, observed_date)
                do update set rule_count=excluded.rule_count,
                              enabled_count=excluded.enabled_count,
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
