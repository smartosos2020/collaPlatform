package com.colla.platform.modules.project.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class ProjectPlatformArchitectureSpikeTests {
    private static final int FIELD_SPIKE_ROWS = 20_000;
    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void comparesJsonbTypedRowsAndHybridProjectionWithEquivalentResults() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                create table spike_jsonb_items (
                    id bigint primary key,
                    fields jsonb not null
                );
                create index idx_spike_jsonb_team_score
                    on spike_jsonb_items ((fields ->> 'team'), (((fields ->> 'score')::integer)));

                create table spike_typed_values (
                    item_id bigint not null,
                    field_key text not null,
                    text_value text,
                    number_value numeric,
                    primary key (item_id, field_key)
                );
                create index idx_spike_typed_text
                    on spike_typed_values (field_key, text_value, item_id);
                create index idx_spike_typed_number
                    on spike_typed_values (field_key, number_value, item_id);

                create table spike_hybrid_items (
                    id bigint primary key,
                    fields jsonb not null
                );
                create table spike_hybrid_values (
                    item_id bigint not null references spike_hybrid_items(id),
                    field_key text not null,
                    text_value text,
                    number_value numeric,
                    primary key (item_id, field_key)
                );
                create index idx_spike_hybrid_text
                    on spike_hybrid_values (field_key, text_value, item_id);
                create index idx_spike_hybrid_number
                    on spike_hybrid_values (field_key, number_value, item_id);

                insert into spike_jsonb_items (id, fields)
                select value,
                       jsonb_build_object(
                           'team', case value % 4 when 0 then 'A' when 1 then 'B' when 2 then 'C' else 'D' end,
                           'score', value % 100,
                           'title', 'Item ' || value
                       )
                from generate_series(1, 20000) value;

                insert into spike_typed_values (item_id, field_key, text_value)
                select value, 'team', case value % 4 when 0 then 'A' when 1 then 'B' when 2 then 'C' else 'D' end
                from generate_series(1, 20000) value;
                insert into spike_typed_values (item_id, field_key, number_value)
                select value, 'score', value % 100
                from generate_series(1, 20000) value;

                insert into spike_hybrid_items select * from spike_jsonb_items;
                insert into spike_hybrid_values select * from spike_typed_values;
                analyze spike_jsonb_items;
                analyze spike_typed_values;
                analyze spike_hybrid_items;
                analyze spike_hybrid_values;
                """);

            String jsonbQuery = """
                select count(*) from spike_jsonb_items
                where fields ->> 'team' = 'A'
                  and (fields ->> 'score')::integer >= 96
                """;
            String typedQuery = """
                select count(*)
                from spike_typed_values team
                join spike_typed_values score on score.item_id = team.item_id
                where team.field_key = 'team' and team.text_value = 'A'
                  and score.field_key = 'score' and score.number_value >= 96
                """;
            String hybridQuery = """
                select count(*)
                from spike_hybrid_items item
                join spike_hybrid_values team on team.item_id = item.id
                join spike_hybrid_values score on score.item_id = item.id
                where team.field_key = 'team' and team.text_value = 'A'
                  and score.field_key = 'score' and score.number_value >= 96
                """;

            long expected = queryLong(connection, jsonbQuery);
            assertEquals(expected, queryLong(connection, typedQuery));
            assertEquals(expected, queryLong(connection, hybridQuery));
            assertEquals(200, expected);
            assertEquals(5, queryLong(connection, "select count(*) from pg_indexes where indexname like 'idx_spike_%'"));

            double jsonbP95 = p95Millis(connection, jsonbQuery);
            double typedP95 = p95Millis(connection, typedQuery);
            double hybridP95 = p95Millis(connection, hybridQuery);
            assertTrue(jsonbP95 < 500, "JSONB query exceeded the 500ms spike ceiling: " + jsonbP95);
            assertTrue(typedP95 < 500, "Typed-row query exceeded the 500ms spike ceiling: " + typedP95);
            assertTrue(hybridP95 < 500, "Hybrid query exceeded the 500ms spike ceiling: " + hybridP95);
            System.out.printf(
                "SPIKE_DYNAMIC_FIELDS rows=%d matches=%d jsonb_p95_ms=%.3f typed_p95_ms=%.3f hybrid_p95_ms=%.3f%n",
                FIELD_SPIKE_ROWS,
                expected,
                jsonbP95,
                typedP95,
                hybridP95
            );
        }
    }

    @Test
    void migratesLegacyIdsInIdempotentProjectBatches() throws Exception {
        UUID workspaceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID projectA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID projectB = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
        UUID issueA = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID issueB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
        UUID batchId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                create table spike_legacy_projects (
                    id uuid primary key, workspace_id uuid not null, project_key text not null, name text not null
                );
                create table spike_legacy_issues (
                    id uuid primary key, project_id uuid not null, issue_key text not null,
                    issue_type text not null, title text not null
                );
                create table spike_target_spaces (
                    id uuid primary key, workspace_id uuid not null, space_key text not null,
                    name text not null, unique (workspace_id, space_key)
                );
                create table spike_target_types (
                    id uuid primary key, space_id uuid not null, type_key text not null,
                    version_number integer not null, unique (space_id, type_key)
                );
                create table spike_target_items (
                    id uuid primary key, space_id uuid not null, type_id uuid not null,
                    display_number text not null, title text not null, legacy_type text not null
                );
                create table spike_legacy_id_map (
                    legacy_type text not null, legacy_id uuid not null, target_type text not null,
                    target_id uuid not null, space_id uuid not null, batch_id uuid not null,
                    checksum text not null, primary key (legacy_type, legacy_id)
                );
                create table spike_migration_batches (
                    id uuid primary key, status text not null, source_count integer not null default 0,
                    target_count integer not null default 0, source_checksum text, target_checksum text
                );
                create table spike_number_counters (
                    space_id uuid not null, type_id uuid not null, next_value integer not null,
                    primary key (space_id, type_id)
                );
                """);
            try (PreparedStatement insertProject = connection.prepareStatement(
                "insert into spike_legacy_projects values (?, ?, ?, ?)"
            )) {
                insertProject.setObject(1, projectA);
                insertProject.setObject(2, workspaceId);
                insertProject.setString(3, "ALPHA");
                insertProject.setString(4, "Alpha project");
                insertProject.addBatch();
                insertProject.setObject(1, projectB);
                insertProject.setObject(2, workspaceId);
                insertProject.setString(3, "BETA");
                insertProject.setString(4, "Beta project");
                insertProject.addBatch();
                insertProject.executeBatch();
            }
            try (PreparedStatement insertIssue = connection.prepareStatement(
                "insert into spike_legacy_issues values (?, ?, ?, ?, ?)"
            )) {
                insertIssue.setObject(1, issueA);
                insertIssue.setObject(2, projectA);
                insertIssue.setString(3, "ALPHA-7");
                insertIssue.setString(4, "task");
                insertIssue.setString(5, "Prepare release");
                insertIssue.addBatch();
                insertIssue.setObject(1, issueB);
                insertIssue.setObject(2, projectB);
                insertIssue.setString(3, "BETA-12");
                insertIssue.setString(4, "bug");
                insertIssue.setString(5, "Fix report");
                insertIssue.addBatch();
                insertIssue.executeBatch();
            }

            migrateLegacyBatch(connection, batchId);
            migrateLegacyBatch(connection, batchId);

            assertEquals(2, queryLong(connection, "select count(*) from spike_target_spaces"));
            assertEquals(4, queryLong(connection, "select count(*) from spike_target_items"));
            assertEquals(4, queryLong(connection, "select count(*) from spike_legacy_id_map"));
            assertEquals(4, queryLong(connection, "select count(*) from spike_legacy_id_map where legacy_id = target_id"));
            assertEquals(1, queryLong(connection, "select count(*) from spike_migration_batches"));
            assertEquals(
                queryString(connection, "select source_checksum from spike_migration_batches where id = '" + batchId + "'"),
                queryString(connection, "select target_checksum from spike_migration_batches where id = '" + batchId + "'")
            );
            assertEquals(4, queryLong(connection, "select source_count from spike_migration_batches where id = '" + batchId + "'"));
            assertEquals(4, queryLong(connection, "select target_count from spike_migration_batches where id = '" + batchId + "'"));
            assertEquals(8, queryLong(connection, "select next_value from spike_number_counters where next_value = 8"));
            assertEquals(13, queryLong(connection, "select next_value from spike_number_counters where next_value = 13"));
            System.out.println("SPIKE_MIGRATION batch=" + batchId + " source=4 target=4 rerun_duplicates=0 checksum=match");
        }
    }

    @Test
    void bindsInstancesToImmutableConfigurationVersionsAndUpgradesExplicitly() throws Exception {
        UUID typeId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID version1 = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd1");
        UUID version2 = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd2");
        UUID version3 = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd3");
        UUID itemId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                create table spike_type_versions (
                    id uuid primary key, type_id uuid not null, version_number integer not null,
                    status text not null, config jsonb not null, config_hash text not null,
                    unique (type_id, version_number)
                );
                create function spike_reject_published_version_update() returns trigger language plpgsql as $$
                begin
                    if old.status in ('published', 'superseded') then
                        raise exception 'published configuration versions are immutable';
                    end if;
                    return new;
                end $$;
                create trigger trg_spike_immutable_version before update or delete on spike_type_versions
                    for each row execute function spike_reject_published_version_update();
                create table spike_versioned_items (
                    id uuid primary key, type_version_id uuid not null references spike_type_versions(id),
                    fields jsonb not null, lock_version integer not null default 0
                );
                """);
            insertVersion(connection, version1, typeId, 1, "{\"fields\":[\"title\"]}");
            try (PreparedStatement insert = connection.prepareStatement(
                "insert into spike_versioned_items (id, type_version_id, fields) values (?, ?, ?::jsonb)"
            )) {
                insert.setObject(1, itemId);
                insert.setObject(2, version1);
                insert.setString(3, "{\"title\":\"Legacy item\"}");
                insert.executeUpdate();
            }
            insertVersion(connection, version2, typeId, 2, "{\"fields\":[\"title\",\"priority\"],\"defaults\":{\"priority\":\"normal\"}}");

            assertEquals(version1.toString(), queryString(connection, "select type_version_id::text from spike_versioned_items"));
            assertThrows(SQLException.class, () -> {
                try (PreparedStatement update = connection.prepareStatement(
                    "update spike_type_versions set config = '{}'::jsonb where id = ?"
                )) {
                    update.setObject(1, version1);
                    update.executeUpdate();
                }
            });

            try (PreparedStatement upgrade = connection.prepareStatement("""
                update spike_versioned_items
                set type_version_id = ?, fields = fields || '{"priority":"normal"}'::jsonb,
                    lock_version = lock_version + 1
                where id = ? and type_version_id = ? and lock_version = 0
                """)) {
                upgrade.setObject(1, version2);
                upgrade.setObject(2, itemId);
                upgrade.setObject(3, version1);
                assertEquals(1, upgrade.executeUpdate());
                assertEquals(0, upgrade.executeUpdate());
            }
            insertVersion(connection, version3, typeId, 3, "{\"fields\":[\"title\"]}");
            assertEquals(version2.toString(), queryString(connection, "select type_version_id::text from spike_versioned_items"));
            assertEquals("normal", queryString(connection, "select fields ->> 'priority' from spike_versioned_items"));
            assertEquals(3, queryLong(connection, "select count(*) from spike_type_versions"));
            System.out.println("SPIKE_CONFIG_VERSION immutable_versions=3 explicit_upgrade=1 stale_upgrade=0 rollback_as_new_version=3");
        }
    }

    @Test
    void stateAndNodeFlowsShareCommandHistoryWithoutSharingRuntimeState() throws Exception {
        UUID stateItem = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");
        UUID nodeItem = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff2");

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                create table spike_workflow_history (
                    id bigserial primary key, work_item_id uuid not null, runtime_kind text not null,
                    action_key text not null, idempotency_key text not null unique,
                    from_version integer not null, to_version integer not null, payload jsonb not null
                );
                create table spike_state_flow_instances (
                    work_item_id uuid primary key, current_state text not null, aggregate_version integer not null
                );
                create table spike_node_flow_instances (
                    work_item_id uuid primary key, aggregate_version integer not null
                );
                create table spike_node_tokens (
                    work_item_id uuid not null, token_key text not null, node_key text not null,
                    status text not null, primary key (work_item_id, token_key)
                );
                """);
            try (PreparedStatement insertState = connection.prepareStatement(
                "insert into spike_state_flow_instances values (?, 'open', 0)"
            )) {
                insertState.setObject(1, stateItem);
                insertState.executeUpdate();
            }
            assertEquals(1, update(connection,
                "update spike_state_flow_instances set current_state='done', aggregate_version=1 where work_item_id=? and aggregate_version=0",
                stateItem
            ));
            insertHistory(connection, stateItem, "state", "complete", "state-command-1", 0, 1);

            try (PreparedStatement insertNode = connection.prepareStatement(
                "insert into spike_node_flow_instances values (?, 0)"
            )) {
                insertNode.setObject(1, nodeItem);
                insertNode.executeUpdate();
            }
            try (PreparedStatement insertToken = connection.prepareStatement(
                "insert into spike_node_tokens values (?, 'token-review', 'review', 'active')"
            )) {
                insertToken.setObject(1, nodeItem);
                insertToken.executeUpdate();
            }
            assertEquals(1, update(connection,
                "update spike_node_tokens set status='completed' where work_item_id=? and token_key='token-review' and status='active'",
                nodeItem
            ));
            try (PreparedStatement activate = connection.prepareStatement(
                "insert into spike_node_tokens values (?, 'token-approve', 'approve', 'active')"
            )) {
                activate.setObject(1, nodeItem);
                activate.executeUpdate();
            }
            assertEquals(1, update(connection,
                "update spike_node_flow_instances set aggregate_version=1 where work_item_id=? and aggregate_version=0",
                nodeItem
            ));
            insertHistory(connection, nodeItem, "node", "submit_review", "node-command-1", 0, 1);

            assertEquals(2, queryLong(connection, "select count(*) from spike_workflow_history"));
            assertEquals(1, queryLong(connection, "select count(*) from spike_node_tokens where work_item_id='" + nodeItem + "' and status='active'"));
            assertEquals(0, queryLong(connection, "select count(*) from spike_node_tokens where work_item_id='" + stateItem + "'"));
            assertEquals(0, queryLong(connection, "select count(*) from spike_state_flow_instances where work_item_id='" + nodeItem + "'"));
            assertEquals(0, update(connection,
                "update spike_state_flow_instances set current_state='closed' where work_item_id=? and aggregate_version=0",
                stateItem
            ));
            assertThrows(SQLException.class, () -> insertHistory(
                connection,
                stateItem,
                "state",
                "complete",
                "state-command-1",
                1,
                2
            ));
            System.out.println("SPIKE_WORKFLOW shared_history=2 state_runtime=current_state node_runtime=active_tokens stale_command=0 duplicate_command=rejected");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static double p95Millis(Connection connection, String sql) throws SQLException {
        queryLong(connection, sql);
        List<Double> samples = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            long started = System.nanoTime();
            queryLong(connection, sql);
            samples.add((System.nanoTime() - started) / 1_000_000.0);
        }
        Collections.sort(samples);
        return samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private static int update(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            return statement.executeUpdate();
        }
    }

    private static void insertVersion(Connection connection, UUID id, UUID typeId, int number, String config) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into spike_type_versions (id, type_id, version_number, status, config, config_hash)
            values (?, ?, ?, 'published', ?::jsonb, md5(?))
            """)) {
            statement.setObject(1, id);
            statement.setObject(2, typeId);
            statement.setInt(3, number);
            statement.setString(4, config);
            statement.setString(5, config);
            statement.executeUpdate();
        }
    }

    private static void insertHistory(
        Connection connection,
        UUID workItemId,
        String runtimeKind,
        String action,
        String idempotencyKey,
        int fromVersion,
        int toVersion
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into spike_workflow_history
                (work_item_id, runtime_kind, action_key, idempotency_key, from_version, to_version, payload)
            values (?, ?, ?, ?, ?, ?, '{}'::jsonb)
            """)) {
            statement.setObject(1, workItemId);
            statement.setString(2, runtimeKind);
            statement.setString(3, action);
            statement.setString(4, idempotencyKey);
            statement.setInt(5, fromVersion);
            statement.setInt(6, toVersion);
            statement.executeUpdate();
        }
    }

    private static void migrateLegacyBatch(Connection connection, UUID batchId) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement batch = connection.prepareStatement(
                "insert into spike_migration_batches (id, status) values (?, 'running') on conflict (id) do nothing"
            )) {
                batch.setObject(1, batchId);
                batch.executeUpdate();
            }
            try (PreparedStatement projects = connection.prepareStatement(
                "select id, workspace_id, project_key, name from spike_legacy_projects order by id"
            ); ResultSet resultSet = projects.executeQuery()) {
                while (resultSet.next()) {
                    UUID projectId = resultSet.getObject("id", UUID.class);
                    UUID workspaceId = resultSet.getObject("workspace_id", UUID.class);
                    String projectKey = resultSet.getString("project_key");
                    String name = resultSet.getString("name");
                    UUID spaceId = deterministicId("project-space:" + projectId);
                    UUID projectTypeId = deterministicId("type:" + spaceId + ":project");
                    upsertSpace(connection, spaceId, workspaceId, projectKey, name);
                    upsertType(connection, projectTypeId, spaceId, "project");
                    upsertItem(connection, projectId, spaceId, projectTypeId, projectKey, name, "project");
                    upsertMap(connection, "project", projectId, projectId, spaceId, batchId, projectKey + ":" + name);

                    try (PreparedStatement issues = connection.prepareStatement(
                        "select id, issue_key, issue_type, title from spike_legacy_issues where project_id = ? order by id"
                    )) {
                        issues.setObject(1, projectId);
                        try (ResultSet issueRows = issues.executeQuery()) {
                            while (issueRows.next()) {
                                UUID issueId = issueRows.getObject("id", UUID.class);
                                String issueKey = issueRows.getString("issue_key");
                                String issueType = issueRows.getString("issue_type");
                                String title = issueRows.getString("title");
                                UUID typeId = deterministicId("type:" + spaceId + ":" + issueType);
                                upsertType(connection, typeId, spaceId, issueType);
                                upsertItem(connection, issueId, spaceId, typeId, issueKey, title, "issue");
                                upsertMap(connection, "issue", issueId, issueId, spaceId, batchId, issueKey + ":" + title);
                            }
                        }
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    insert into spike_number_counters (space_id, type_id, next_value)
                    select space_id, type_id,
                           max((regexp_match(display_number, '([0-9]+)$'))[1]::integer) + 1
                    from spike_target_items
                    where legacy_type = 'issue'
                    group by space_id, type_id
                    on conflict (space_id, type_id) do update
                    set next_value = greatest(spike_number_counters.next_value, excluded.next_value)
                    """);
            }
            try (PreparedStatement completeBatch = connection.prepareStatement("""
                    update spike_migration_batches batch
                    set status = 'completed',
                        source_count = source_data.row_count,
                        target_count = target_data.row_count,
                        source_checksum = source_data.checksum,
                        target_checksum = target_data.checksum
                    from (
                        select count(*)::integer row_count,
                               md5(string_agg(kind || ':' || id || ':' || number || ':' || title, '|' order by kind, id)) checksum
                        from (
                            select 'project' kind, id::text id, project_key number, name title from spike_legacy_projects
                            union all
                            select 'issue', id::text, issue_key, title from spike_legacy_issues
                        ) source_rows
                    ) source_data,
                    (
                        select count(*)::integer row_count,
                               md5(string_agg(map.legacy_type || ':' || map.legacy_id || ':' || item.display_number || ':' || item.title,
                                             '|' order by map.legacy_type, map.legacy_id)) checksum
                        from spike_legacy_id_map map
                        join spike_target_items item on item.id = map.target_id
                    ) target_data
                    where batch.id = ?
                    """)) {
                completeBatch.setObject(1, batchId);
                assertEquals(1, completeBatch.executeUpdate());
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void upsertSpace(Connection connection, UUID id, UUID workspaceId, String key, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into spike_target_spaces values (?, ?, ?, ?)
            on conflict (id) do update set name = excluded.name
            """)) {
            statement.setObject(1, id);
            statement.setObject(2, workspaceId);
            statement.setString(3, key);
            statement.setString(4, name);
            statement.executeUpdate();
        }
    }

    private static void upsertType(Connection connection, UUID id, UUID spaceId, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into spike_target_types values (?, ?, ?, 1)
            on conflict (id) do nothing
            """)) {
            statement.setObject(1, id);
            statement.setObject(2, spaceId);
            statement.setString(3, key);
            statement.executeUpdate();
        }
    }

    private static void upsertItem(
        Connection connection,
        UUID id,
        UUID spaceId,
        UUID typeId,
        String number,
        String title,
        String legacyType
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into spike_target_items values (?, ?, ?, ?, ?, ?)
            on conflict (id) do update set display_number = excluded.display_number, title = excluded.title
            """)) {
            statement.setObject(1, id);
            statement.setObject(2, spaceId);
            statement.setObject(3, typeId);
            statement.setString(4, number);
            statement.setString(5, title);
            statement.setString(6, legacyType);
            statement.executeUpdate();
        }
    }

    private static void upsertMap(
        Connection connection,
        String legacyType,
        UUID legacyId,
        UUID targetId,
        UUID spaceId,
        UUID batchId,
        String checksumSource
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into spike_legacy_id_map
                (legacy_type, legacy_id, target_type, target_id, space_id, batch_id, checksum)
            values (?, ?, 'work_item', ?, ?, ?, md5(?))
            on conflict (legacy_type, legacy_id) do update
            set checksum = excluded.checksum, batch_id = excluded.batch_id
            """)) {
            statement.setString(1, legacyType);
            statement.setObject(2, legacyId);
            statement.setObject(3, targetId);
            statement.setObject(4, spaceId);
            statement.setObject(5, batchId);
            statement.setString(6, checksumSource);
            statement.executeUpdate();
        }
    }

    private static UUID deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
