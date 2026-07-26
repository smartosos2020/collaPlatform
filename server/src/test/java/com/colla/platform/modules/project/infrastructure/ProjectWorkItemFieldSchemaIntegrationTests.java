package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
class ProjectWorkItemFieldSchemaIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void schemaContainsScopedFieldDefinitionsOptionsCommandsIndexesAndIdentityGuards() {
        assertEquals(3, jdbcTemplate.queryForObject(
            """
                select count(*) from information_schema.tables
                 where table_schema='public'
                   and table_name in (
                     'project_work_item_field_definitions',
                     'project_work_item_field_options',
                     'project_work_item_field_commands'
                   )
                """,
            Integer.class
        ));
        assertEquals(4, jdbcTemplate.queryForObject(
            """
                select count(*) from information_schema.table_constraints
                 where table_schema='public'
                   and table_name='project_work_item_field_definitions'
                   and constraint_name in (
                     'uk_project_work_item_fields_type_key',
                     'fk_project_work_item_fields_type_scope',
                     'fk_project_work_item_fields_created_by_workspace',
                     'fk_project_work_item_fields_updated_by_workspace'
                   )
                """,
            Integer.class
        ));
        assertEquals(3, jdbcTemplate.queryForObject(
            """
                select count(*) from pg_indexes
                 where schemaname='public'
                   and indexname in (
                     'idx_project_work_item_fields_type_status_order',
                     'idx_project_work_item_fields_space_updated',
                     'idx_project_work_item_field_commands_type_created'
                   )
                """,
            Integer.class
        ));
        assertTrue(jdbcTemplate.queryForObject(
            "select exists(select 1 from pg_trigger where tgname='trg_project_work_item_field_identity' and not tgisinternal)",
            Boolean.class
        ));
        assertEquals(4, jdbcTemplate.queryForObject(
            """
                select count(*) from information_schema.table_constraints
                 where table_schema='public'
                   and table_name='project_work_item_field_options'
                   and constraint_name in (
                     'uk_project_work_item_field_options_field_key',
                     'fk_project_work_item_field_options_field_scope',
                     'fk_project_work_item_field_options_created_by_workspace',
                     'fk_project_work_item_field_options_updated_by_workspace'
                   )
                """,
            Integer.class
        ));
        assertTrue(jdbcTemplate.queryForObject(
            "select exists(select 1 from pg_trigger where tgname='trg_protect_project_work_item_field_option_identity' and not tgisinternal)",
            Boolean.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and table_name='project_work_items'",
            Integer.class
        ));
    }

    @Test
    void schemaContainsScopedLayoutGraphPoliciesReceiptsAndCanonicalWorkItemTable() {
        assertEquals(4, jdbcTemplate.queryForObject(
            """
                select count(*) from information_schema.tables
                 where table_schema='public'
                   and table_name in (
                     'project_work_item_layouts',
                     'project_work_item_layout_nodes',
                     'project_work_item_field_access_policies',
                     'project_work_item_layout_commands'
                   )
                """,
            Integer.class
        ));
        assertEquals(4, jdbcTemplate.queryForObject(
            """
                select count(*) from information_schema.table_constraints
                 where table_schema='public'
                   and constraint_name in (
                     'fk_project_work_item_layouts_type_scope',
                     'fk_project_work_item_layout_nodes_layout_scope',
                     'fk_project_work_item_layout_nodes_field_scope',
                     'fk_project_work_item_field_policies_layout_scope'
                   )
                """,
            Integer.class
        ));
        assertEquals(4, jdbcTemplate.queryForObject(
            """
                select count(*) from pg_indexes
                 where schemaname='public'
                   and indexname in (
                     'idx_project_work_item_layouts_type_updated',
                     'idx_project_work_item_layout_nodes_tree',
                     'idx_project_work_item_field_policies_type',
                     'idx_project_work_item_layout_commands_type_created'
                   )
                """,
            Integer.class
        ));
        assertEquals(3, jdbcTemplate.queryForObject(
            """
                select count(*) from pg_trigger
                 where not tgisinternal
                   and tgname in (
                     'trg_project_work_item_layout_identity',
                     'trg_project_work_item_layout_node_identity',
                     'trg_project_work_item_field_policy_identity'
                   )
                """,
            Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and table_name='project_work_items'",
            Integer.class
        ));
    }

    @Test
    void v065UpgradeToLatestPreservesPublishedConfigurationAndLegacyRows() throws Exception {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("65")
                .load()
                .migrate();

            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate isolated = new JdbcTemplate(dataSource);

            UUID workspaceId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID issueId = UUID.randomUUID();
            UUID spaceId = UUID.randomUUID();
            UUID typeId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            UUID fieldId = UUID.randomUUID();
            UUID optionId = UUID.randomUUID();
            String versionHash = "a".repeat(64);
            String fieldHash = "b".repeat(64);

            isolated.update(
                """
                    insert into workspaces (id, name, slug, status, created_at, updated_at)
                    values (?, 'S05 M5 upgrade', ?, 'active', now(), now())
                    """,
                workspaceId,
                "s05-m5-upgrade-" + workspaceId
            );
            isolated.update(
                """
                    insert into users (
                      id, workspace_id, username, password_hash, display_name, status, created_at, updated_at
                    ) values (?, ?, ?, 'not-a-real-secret', 'S05 M5 Upgrade User', 'active', now(), now())
                    """,
                userId,
                workspaceId,
                "s05-m5-upgrade-" + userId
            );
            isolated.update(
                """
                    insert into projects (
                      id, workspace_id, project_key, name, description, status,
                      created_by, created_at, updated_by, updated_at
                    ) values (?, ?, 'S05M5', 'Legacy project sentinel', 'must survive', 'active', ?, now(), ?, now())
                    """,
                projectId,
                workspaceId,
                userId,
                userId
            );
            isolated.update(
                """
                    insert into issues (
                      id, workspace_id, project_id, issue_key, issue_type, title, description,
                      priority, status, reporter_id, created_by, created_at, updated_by, updated_at
                    ) values (?, ?, ?, 'S05M5-1', 'task', 'Legacy issue sentinel', 'must survive',
                              'medium', 'open', ?, ?, now(), ?, now())
                    """,
                issueId,
                workspaceId,
                projectId,
                userId,
                userId,
                userId
            );
            isolated.update(
                """
                    insert into project_spaces (
                      id, workspace_id, space_key, name, description, status, visibility, version,
                      created_by, created_at, updated_by, updated_at
                    ) values (?, ?, 's05_m5_upgrade', 'S05 M5 Upgrade', 'migration sentinel',
                              'active', 'private', 0, ?, now(), ?, now())
                    """,
                spaceId,
                workspaceId,
                userId,
                userId
            );

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (var statement = connection.prepareStatement(
                        """
                            insert into project_work_item_types (
                              id, workspace_id, space_id, type_key, name, icon, description, sort_order,
                              status, is_system, current_version_id, created_by, created_at, updated_by,
                              updated_at, aggregate_version
                            ) values (?, ?, ?, 'upgrade_type', 'Upgrade Type', 'task', 'migration sentinel',
                                      0, 'active', false, ?, ?, now(), ?, now(), 0)
                            """
                    )) {
                        statement.setObject(1, typeId);
                        statement.setObject(2, workspaceId);
                        statement.setObject(3, spaceId);
                        statement.setObject(4, versionId);
                        statement.setObject(5, userId);
                        statement.setObject(6, userId);
                        statement.executeUpdate();
                    }
                    try (var statement = connection.prepareStatement(
                        """
                            insert into project_work_item_type_versions (
                              id, workspace_id, space_id, type_definition_id, version_number, config_hash,
                              status, config, created_by, created_at, published_by, published_at
                            ) values (?, ?, ?, ?, 1, ?, 'published',
                                      '{"schemaVersion":1,"fields":["priority"]}'::jsonb,
                                      ?, now(), ?, now())
                            """
                    )) {
                        statement.setObject(1, versionId);
                        statement.setObject(2, workspaceId);
                        statement.setObject(3, spaceId);
                        statement.setObject(4, typeId);
                        statement.setString(5, versionHash);
                        statement.setObject(6, userId);
                        statement.setObject(7, userId);
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            }

            isolated.update(
                """
                    insert into project_work_item_field_definitions (
                      id, workspace_id, space_id, type_definition_id, field_key, name, description,
                      field_type, config, config_hash, sort_order, status, is_system,
                      created_by, created_at, updated_by, updated_at, aggregate_version
                    ) values (?, ?, ?, ?, 'priority', 'Priority', 'migration sentinel', 'single_select',
                              '{"required":true,"source":"s05-m5-upgrade"}'::jsonb, ?, 10, 'active', false,
                              ?, now(), ?, now(), 0)
                    """,
                fieldId,
                workspaceId,
                spaceId,
                typeId,
                fieldHash,
                userId,
                userId
            );
            isolated.update(
                """
                    insert into project_work_item_field_options (
                      id, workspace_id, space_id, type_definition_id, field_definition_id,
                      option_key, name, color, sort_order, status,
                      created_by, created_at, updated_by, updated_at
                    ) values (?, ?, ?, ?, ?, 'high', 'High', '#FF0000', 20, 'active', ?, now(), ?, now())
                    """,
                optionId,
                workspaceId,
                spaceId,
                typeId,
                fieldId,
                userId,
                userId
            );

            UUID currentVersionBefore = isolated.queryForObject(
                "select current_version_id from project_work_item_types where id=?",
                UUID.class,
                typeId
            );
            Map<String, Object> versionBefore = isolated.queryForMap(
                """
                    select id, version_number, config_hash, status, config::text as config
                      from project_work_item_type_versions
                     where id=?
                    """,
                versionId
            );
            Map<String, Object> fieldBefore = isolated.queryForMap(
                """
                    select id, field_key, field_type, config_hash, config::text as config, status
                      from project_work_item_field_definitions
                     where id=?
                    """,
                fieldId
            );
            Map<String, Object> optionBefore = isolated.queryForMap(
                """
                    select id, option_key, name, color, sort_order, status
                      from project_work_item_field_options
                     where id=?
                    """,
                optionId
            );
            Map<String, Object> projectBefore = isolated.queryForMap(
                "select id, project_key, name, description, status from projects where id=?",
                projectId
            );
            Map<String, Object> issueBefore = isolated.queryForMap(
                "select id, issue_key, title, description, priority, status from issues where id=?",
                issueId
            );

            Flyway latest = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .load();
            int latestVersion = latestMigrationVersion(latest);
            assertEquals(latestVersion - 65, latest.migrate().migrationsExecuted);
            assertEquals(0, latest.migrate().migrationsExecuted);

            assertEquals(latestVersion, isolated.queryForObject(
                "select max(version::integer) from flyway_schema_history where success=true",
                Integer.class
            ));
            assertEquals(0, isolated.queryForObject(
                """
                    select count(*)
                      from generate_series(66, ?) expected(version)
                      left join flyway_schema_history history
                        on history.success=true
                       and history.version::integer=expected.version
                     where history.version is null
                    """,
                Integer.class,
                latestVersion
            ));
            assertEquals(currentVersionBefore, isolated.queryForObject(
                "select current_version_id from project_work_item_types where id=?",
                UUID.class,
                typeId
            ));
            assertEquals(versionBefore, isolated.queryForMap(
                """
                    select id, version_number, config_hash, status, config::text as config
                      from project_work_item_type_versions
                     where id=?
                    """,
                versionId
            ));
            assertEquals(fieldBefore, isolated.queryForMap(
                """
                    select id, field_key, field_type, config_hash, config::text as config, status
                      from project_work_item_field_definitions
                     where id=?
                    """,
                fieldId
            ));
            assertEquals(optionBefore, isolated.queryForMap(
                """
                    select id, option_key, name, color, sort_order, status
                      from project_work_item_field_options
                     where id=?
                    """,
                optionId
            ));
            assertEquals(projectBefore, isolated.queryForMap(
                "select id, project_key, name, description, status from projects where id=?",
                projectId
            ));
            assertEquals(issueBefore, isolated.queryForMap(
                "select id, issue_key, title, description, priority, status from issues where id=?",
                issueId
            ));

            assertEquals(4, isolated.queryForObject(
                """
                    select count(*) from information_schema.tables
                     where table_schema='public'
                       and table_name in (
                         'project_work_item_layouts',
                         'project_work_item_layout_nodes',
                         'project_work_item_field_access_policies',
                         'project_work_item_layout_commands'
                       )
                    """,
                Integer.class
            ));
            assertEquals(4, isolated.queryForObject(
                """
                    select count(*) from information_schema.table_constraints
                     where table_schema='public'
                       and constraint_name in (
                         'fk_project_work_item_layouts_type_scope',
                         'fk_project_work_item_layout_nodes_layout_scope',
                         'fk_project_work_item_layout_nodes_field_scope',
                         'fk_project_work_item_field_policies_layout_scope'
                       )
                    """,
                Integer.class
            ));
            assertEquals(4, isolated.queryForObject(
                """
                    select count(*) from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_project_work_item_layouts_type_updated',
                         'idx_project_work_item_layout_nodes_tree',
                         'idx_project_work_item_field_policies_type',
                         'idx_project_work_item_layout_commands_type_created'
                       )
                    """,
                Integer.class
            ));
            assertEquals(3, isolated.queryForObject(
                """
                    select count(*) from pg_trigger
                     where not tgisinternal
                       and tgname in (
                         'trg_project_work_item_layout_identity',
                         'trg_project_work_item_layout_node_identity',
                         'trg_project_work_item_field_policy_identity'
                       )
                    """,
                Integer.class
            ));
            assertEquals(0, isolated.queryForObject(
                """
                    select
                      (select count(*) from project_work_item_layouts)
                      + (select count(*) from project_work_item_layout_nodes)
                      + (select count(*) from project_work_item_field_access_policies)
                      + (select count(*) from project_work_item_layout_commands)
                    """,
                Integer.class
            ));
            assertEquals(0, isolated.queryForObject(
                "select count(*) from project_work_items",
                Integer.class
            ));
        } finally {
            container.stop();
        }
    }

    @Test
    void v063UpgradeToV066PreservesLegacyRowsAndAddsOnlyFieldConfigurationSchema() throws Exception {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("63")
                .load()
                .migrate();

            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate isolated = new JdbcTemplate(dataSource);

            UUID workspaceId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID issueId = UUID.randomUUID();
            isolated.update(
                """
                    insert into workspaces (id, name, slug, status, created_at, updated_at)
                    values (?, 'S04 upgrade', ?, 'active', now(), now())
                    """,
                workspaceId,
                "s04-upgrade-" + workspaceId
            );
            isolated.update(
                """
                    insert into users (
                      id, workspace_id, username, password_hash, display_name, status, created_at, updated_at
                    ) values (?, ?, ?, 'not-a-real-secret', 'S04 Upgrade User', 'active', now(), now())
                    """,
                userId,
                workspaceId,
                "s04-upgrade-" + userId
            );
            isolated.update(
                """
                    insert into projects (
                      id, workspace_id, project_key, name, description, status,
                      created_by, created_at, updated_by, updated_at
                    ) values (?, ?, 'S04UPGRADE', 'Legacy sentinel', 'must survive', 'active', ?, now(), ?, now())
                    """,
                projectId,
                workspaceId,
                userId,
                userId
            );
            isolated.update(
                """
                    insert into issues (
                      id, workspace_id, project_id, issue_key, issue_type, title, description,
                      priority, status, reporter_id, created_by, created_at, updated_by, updated_at
                    ) values (?, ?, ?, 'S04UPGRADE-1', 'task', 'Legacy issue sentinel', 'must survive',
                              'medium', 'open', ?, ?, now(), ?, now())
                    """,
                issueId,
                workspaceId,
                projectId,
                userId,
                userId,
                userId
            );

            Flyway latest = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("66")
                .load();
            assertEquals(3, latest.migrate().migrationsExecuted);
            assertEquals(0, latest.migrate().migrationsExecuted);

            assertEquals("066", isolated.queryForObject(
                "select max(version) from flyway_schema_history",
                String.class
            ));
            assertEquals(3, isolated.queryForObject(
                """
                    select count(*) from information_schema.tables
                     where table_schema='public'
                       and table_name in (
                         'project_work_item_field_definitions',
                         'project_work_item_field_options',
                         'project_work_item_field_commands'
                       )
                    """,
                Integer.class
            ));
            assertEquals("Legacy sentinel", isolated.queryForObject(
                "select name from projects where id=? and workspace_id=?",
                String.class,
                projectId,
                workspaceId
            ));
            assertEquals("Legacy issue sentinel", isolated.queryForObject(
                "select title from issues where id=? and workspace_id=?",
                String.class,
                issueId,
                workspaceId
            ));
            assertEquals(0, isolated.queryForObject(
                "select count(*) from project_work_item_field_definitions",
                Integer.class
            ));
            assertEquals(0, isolated.queryForObject(
                "select count(*) from project_work_item_field_options",
                Integer.class
            ));
            assertEquals(0, isolated.queryForObject(
                "select count(*) from information_schema.tables where table_schema='public' and table_name='project_work_items'",
                Integer.class
            ));
            assertTrue(isolated.queryForObject(
                """
                    select exists(
                      select 1 from information_schema.table_constraints
                       where table_schema='public'
                         and table_name='project_work_item_field_definitions'
                         and constraint_name='ck_project_work_item_fields_config_hash'
                    )
                    """,
                Boolean.class
            ));
        } finally {
            container.stop();
        }
    }

    private static int latestMigrationVersion(Flyway flyway) {
        int latest = 0;
        for (MigrationInfo migration : flyway.info().all()) {
            if (migration.getVersion() != null) {
                latest = Math.max(latest, Integer.parseInt(migration.getVersion().getVersion()));
            }
        }
        assertTrue(latest > 0);
        return latest;
    }
}
