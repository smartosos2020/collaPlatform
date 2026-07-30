package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WorkItemStateFlowFoundationIntegrationTests {
    private static final UUID WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
        UUID.fromString("21000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        var dataSource = dataSource();
        Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .load()
            .clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        insertUser();
    }

    @Test
    void enforcesScopedSingleCurrentStateAndKeepsNodeRuntimeSeparate() throws Exception {
        Fixture first = fixture("flow_a");
        Fixture second = fixture("flow_b");

        insertCurrentState(first, "open");
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from project_work_item_current_states where work_item_id=?",
            Integer.class,
            first.workItemId()
        ));
        assertThrows(DataIntegrityViolationException.class, () -> insertCurrentState(first, "open"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            """
                insert into project_work_item_current_states (
                    workspace_id, space_id, work_item_id, type_definition_id, type_version_id,
                    config_hash, current_state_key, work_item_version, aggregate_version,
                    initialized_by, initialized_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, 'open', 0, 0, ?, now(), ?, now())
                """,
            WORKSPACE_ID,
            second.spaceId(),
            first.workItemId(),
            second.typeId(),
            second.versionId(),
            "b".repeat(64),
            USER_ID,
            USER_ID
        ));
        assertEquals(8, jdbc.queryForObject(
            """
                select count(*) from information_schema.tables
                 where table_schema='public'
                   and table_name in (
                       'project_node_workflow_instances',
                       'project_node_workflow_tokens',
                       'project_node_workflow_tasks',
                       'project_node_workflow_votes',
                       'project_node_workflow_joins',
                       'project_node_workflow_join_arrivals',
                       'project_node_workflow_commands',
                       'project_node_workflow_history'
                   )
                """,
            Integer.class
        ));
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from project_node_workflow_instances where work_item_id=?",
            Integer.class,
            first.workItemId()
        ));
    }

    @Test
    void freezesCompletedReceiptsAndAppendOnlyHistory() throws Exception {
        Fixture fixture = fixture("flow_history");
        insertCurrentState(fixture, "open");
        UUID commandId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_work_item_workflow_commands (
                    id, workspace_id, space_id, work_item_id, operation, action_key,
                    from_state_key, expected_work_item_version, request_id, request_hash,
                    status, response_schema_version, created_by, created_at
                ) values (?, ?, ?, ?, 'execute', 'start_progress', 'open', 0,
                          'request-1', ?, 'pending', 1, ?, now())
                """,
            commandId,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.workItemId(),
            "c".repeat(64),
            USER_ID
        );
        jdbc.update(
            """
                update project_work_item_workflow_commands
                   set status='completed', response_payload='{"state":"in_progress"}'::jsonb,
                       completed_at=now()
                 where id=?
                """,
            commandId
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "update project_work_item_workflow_commands set response_payload='{}'::jsonb where id=?",
            commandId
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            """
                insert into project_work_item_workflow_commands (
                    id, workspace_id, space_id, work_item_id, operation, action_key,
                    from_state_key, expected_work_item_version, request_id, request_hash,
                    status, response_schema_version, created_by, created_at
                ) values (?, ?, ?, ?, 'execute', 'start_progress', 'open', 0,
                          'request-1', ?, 'pending', 1, ?, now())
                """,
            UUID.randomUUID(),
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.workItemId(),
            "d".repeat(64),
            USER_ID
        ));

        UUID historyId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_work_item_workflow_history (
                    id, workspace_id, space_id, work_item_id, sequence_number,
                    type_definition_id, type_version_id, config_hash, from_state_key,
                    to_state_key, action_key, action_kind, actor_id, actor_class,
                    decision_reference, correlation_id, public_payload, occurred_at
                ) values (?, ?, ?, ?, 1, ?, ?, ?, 'open', 'in_progress',
                          'start_progress', 'forward', ?, 'user', 'decision-1',
                          'correlation-1', '{}'::jsonb, now())
                """,
            historyId,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.workItemId(),
            fixture.typeId(),
            fixture.versionId(),
            "a".repeat(64),
            USER_ID
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "update project_work_item_workflow_history set public_payload='{\"changed\":true}'::jsonb where id=?",
            historyId
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "delete from project_work_item_workflow_history where id=?",
            historyId
        ));
    }

    @Test
    void nodeFoundationScopesIndependentRuntimeFactsAndFreezesReceiptsAndHistory() throws Exception {
        Fixture fixture = fixture("node_flow");
        Fixture other = fixture("node_other");
        UUID instanceId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_instances (
                    id, workspace_id, space_id, work_item_id, type_definition_id,
                    type_version_id, config_hash, status, work_item_version,
                    aggregate_version, started_by, started_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'active', 0, 0, ?, now(), ?, now())
                """,
            instanceId,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.workItemId(),
            fixture.typeId(),
            fixture.versionId(),
            "a".repeat(64),
            USER_ID,
            USER_ID
        );
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from project_work_item_current_states where work_item_id=?",
            Integer.class,
            fixture.workItemId()
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            """
                insert into project_node_workflow_instances (
                    id, workspace_id, space_id, work_item_id, type_definition_id,
                    type_version_id, config_hash, status, work_item_version,
                    aggregate_version, started_by, started_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'active', 0, 0, ?, now(), ?, now())
                """,
            UUID.randomUUID(),
            WORKSPACE_ID,
            other.spaceId(),
            fixture.workItemId(),
            other.typeId(),
            other.versionId(),
            "a".repeat(64),
            USER_ID,
            USER_ID
        ));

        UUID tokenId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_tokens (
                    id, workspace_id, space_id, instance_id, node_key, stage_key,
                    status, correlation_key, aggregate_version, entered_at
                ) values (?, ?, ?, ?, 'review', 'delivery', 'active', 'correlation-1', 0, now())
                """,
            tokenId, WORKSPACE_ID, fixture.spaceId(), instanceId
        );
        UUID taskId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_tasks (
                    id, workspace_id, space_id, instance_id, token_id, node_key,
                    assignment_strategy, candidate_roles, status, aggregate_version, created_at
                ) values (?, ?, ?, ?, ?, 'review', 'single', '["member"]'::jsonb, 'pending', 0, now())
                """,
            taskId, WORKSPACE_ID, fixture.spaceId(), instanceId, tokenId
        );
        UUID voteId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_votes (
                    id, workspace_id, space_id, instance_id, task_id, token_id,
                    node_key, voter_id, decision, sequence_number, occurred_at
                ) values (?, ?, ?, ?, ?, ?, 'review', ?, 'approve', 1, now())
                """,
            voteId, WORKSPACE_ID, fixture.spaceId(), instanceId, taskId, tokenId, USER_ID
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "update project_node_workflow_votes set decision='reject' where id=?",
            voteId
        ));
        UUID withdrawalId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_votes (
                    id, workspace_id, space_id, instance_id, task_id, token_id,
                    node_key, voter_id, decision, supersedes_vote_id,
                    sequence_number, occurred_at
                ) values (?, ?, ?, ?, ?, ?, 'review', ?, 'withdraw', ?, 2, now())
                """,
            withdrawalId, WORKSPACE_ID, fixture.spaceId(), instanceId, taskId, tokenId,
            USER_ID, voteId
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            """
                insert into project_node_workflow_votes (
                    id, workspace_id, space_id, instance_id, task_id, token_id,
                    node_key, voter_id, decision, supersedes_vote_id,
                    sequence_number, occurred_at
                ) values (?, ?, ?, ?, ?, ?, 'review', ?, 'withdraw', ?, 3, now())
                """,
            UUID.randomUUID(), WORKSPACE_ID, fixture.spaceId(), instanceId, taskId, tokenId,
            USER_ID, voteId
        ));

        UUID joinId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_joins (
                    id, workspace_id, space_id, instance_id, join_key, node_key,
                    correlation_key, policy, expected_count, arrived_count, status,
                    aggregate_version, created_at
                ) values (?, ?, ?, ?, 'delivery_all', 'review', 'correlation-1',
                          'all', 1, 0, 'waiting', 0, now())
                """,
            joinId, WORKSPACE_ID, fixture.spaceId(), instanceId
        );
        UUID arrivalId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_join_arrivals (
                    id, workspace_id, space_id, instance_id, join_id, token_id, arrived_at
                ) values (?, ?, ?, ?, ?, ?, now())
                """,
            arrivalId, WORKSPACE_ID, fixture.spaceId(), instanceId, joinId, tokenId
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "update project_node_workflow_join_arrivals set arrived_at=now() where id=?",
            arrivalId
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            """
                insert into project_node_workflow_join_arrivals (
                    id, workspace_id, space_id, instance_id, join_id, token_id, arrived_at
                ) values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), WORKSPACE_ID, fixture.spaceId(), instanceId, joinId, tokenId
        ));

        UUID commandId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_commands (
                    id, workspace_id, space_id, work_item_id, instance_id, operation,
                    node_key, expected_work_item_version, expected_instance_version,
                    request_id, request_hash, status, response_schema_version,
                    created_by, created_at
                ) values (?, ?, ?, ?, ?, 'vote', 'review', 0, 0, 'request-node-1',
                          ?, 'pending', 1, ?, now())
                """,
            commandId,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.workItemId(),
            instanceId,
            "d".repeat(64),
            USER_ID
        );
        jdbc.update(
            """
                update project_node_workflow_commands
                   set status='completed', response_payload='{"status":"active"}'::jsonb,
                       completed_at=now()
                 where id=?
                """,
            commandId
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "update project_node_workflow_commands set response_payload='{}'::jsonb where id=?",
            commandId
        ));

        UUID historyId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_history (
                    id, workspace_id, space_id, instance_id, work_item_id, sequence_number,
                    type_definition_id, type_version_id, config_hash, event_kind, node_key,
                    token_id, task_id, actor_id, actor_class, correlation_id, occurred_at
                ) values (?, ?, ?, ?, ?, 1, ?, ?, ?, 'voted', 'review', ?, ?, ?,
                          'user', 'correlation-node-1', now())
                """,
            historyId,
            WORKSPACE_ID,
            fixture.spaceId(),
            instanceId,
            fixture.workItemId(),
            fixture.typeId(),
            fixture.versionId(),
            "a".repeat(64),
            tokenId,
            taskId,
            USER_ID
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "delete from project_node_workflow_history where id=?",
            historyId
        ));
    }

    @Test
    void migratesHistoricalBaselinesToV099Repeatably() {
        for (String baseline : new String[]{"001", "061", "078", "085", "090", "093", "095"}) {
            Flyway latest = Flyway.configure()
                .dataSource(dataSource())
                .cleanDisabled(false)
                .load();
            latest.clean();
            Flyway.configure().dataSource(dataSource()).target(baseline).load().migrate();
            latest.migrate();
            assertEquals("141", latest.info().current().getVersion().getVersion());
            assertEquals(0, latest.migrate().migrationsExecuted);
        }
    }

    @Test
    void recoverySchemaFreezesManifestsAndBlocksDirectBindingChanges() throws Exception {
        Fixture fixture = fixture("flow_recovery");
        insertCurrentState(fixture, "open");
        assertEquals(6, jdbc.queryForObject(
            """
                select count(*) from information_schema.tables
                 where table_schema='public'
                   and table_name in (
                       'project_work_item_state_backfill_batches',
                       'project_work_item_state_backfill_units',
                       'project_node_workflow_compensation_runs',
                       'project_node_workflow_compensation_steps',
                       'project_node_workflow_backfill_batches',
                       'project_node_workflow_backfill_units'
                   )
                """,
            Integer.class
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            """
                update project_work_item_current_states
                   set config_hash=?
                 where workspace_id=? and space_id=? and work_item_id=?
                """,
            "b".repeat(64), WORKSPACE_ID, fixture.spaceId(), fixture.workItemId()
        ));
        UUID instanceId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_node_workflow_instances (
                    id, workspace_id, space_id, work_item_id, type_definition_id,
                    type_version_id, config_hash, status, work_item_version,
                    aggregate_version, started_by, started_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'active', 0, 0, ?, now(), ?, now())
                """,
            instanceId, WORKSPACE_ID, fixture.spaceId(), fixture.workItemId(),
            fixture.typeId(), fixture.versionId(), "a".repeat(64), USER_ID, USER_ID
        );
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "update project_node_workflow_instances set config_hash=? where id=?",
            "b".repeat(64), instanceId
        ));
    }

    private Fixture fixture(String key) throws Exception {
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, status, visibility, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', 'private', 0, ?, now(), ?, now())
                """,
            spaceId,
            WORKSPACE_ID,
            key,
            key,
            USER_ID,
            USER_ID
        );
        jdbc.execute((Connection connection) -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (
                var type = connection.prepareStatement("""
                    insert into project_work_item_types (
                        id, workspace_id, space_id, type_key, name, icon, description,
                        sort_order, status, is_system, current_version_id, created_by,
                        created_at, updated_by, updated_at, aggregate_version
                    ) values (?, ?, ?, ?, ?, '', '', 0, 'active', false, ?, ?, now(), ?, now(), 0)
                    """);
                var version = connection.prepareStatement("""
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, snapshot_schema_version,
                        created_by, created_at, published_by, published_at
                    ) values (?, ?, ?, ?, 1, ?, 'published',
                              '{"snapshotSchemaVersion":2}'::jsonb, 2, ?, now(), ?, now())
                    """)
            ) {
                type.setObject(1, typeId);
                type.setObject(2, WORKSPACE_ID);
                type.setObject(3, spaceId);
                type.setString(4, key);
                type.setString(5, key);
                type.setObject(6, versionId);
                type.setObject(7, USER_ID);
                type.setObject(8, USER_ID);
                type.executeUpdate();
                version.setObject(1, versionId);
                version.setObject(2, WORKSPACE_ID);
                version.setObject(3, spaceId);
                version.setObject(4, typeId);
                version.setString(5, "a".repeat(64));
                version.setObject(6, USER_ID);
                version.setObject(7, USER_ID);
                version.executeUpdate();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
        jdbc.update(
            """
                insert into project_work_items (
                    id, workspace_id, space_id, type_definition_id, type_version_id,
                    config_hash, item_number, display_key, title, field_values, status,
                    version, created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, 1, ?, ?, '{}'::jsonb, 'active', 0, ?, now(), ?, now())
                """,
            workItemId,
            WORKSPACE_ID,
            spaceId,
            typeId,
            versionId,
            "a".repeat(64),
            key.toUpperCase() + "-1",
            key,
            USER_ID,
            USER_ID
        );
        return new Fixture(spaceId, typeId, versionId, workItemId);
    }

    private void insertCurrentState(Fixture fixture, String stateKey) {
        jdbc.update(
            """
                insert into project_work_item_current_states (
                    workspace_id, space_id, work_item_id, type_definition_id, type_version_id,
                    config_hash, current_state_key, work_item_version, aggregate_version,
                    initialized_by, initialized_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, now(), ?, now())
                """,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.workItemId(),
            fixture.typeId(),
            fixture.versionId(),
            "a".repeat(64),
            stateKey,
            USER_ID,
            USER_ID
        );
    }

    private void insertUser() {
        jdbc.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, 's08-flow', 'not-used', 'S08 Flow', 'active', now(), now())
                on conflict (id) do nothing
                """,
            USER_ID,
            WORKSPACE_ID
        );
    }

    private org.postgresql.ds.PGSimpleDataSource dataSource() {
        var dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private record Fixture(UUID spaceId, UUID typeId, UUID versionId, UUID workItemId) {
    }
}
