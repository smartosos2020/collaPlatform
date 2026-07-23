package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and table_name='project_work_items'",
            Integer.class
        ));
    }

    @Test
    void v063UpgradePreservesLegacyRowsAndAddsOnlyFieldConfigurationSchema() throws Exception {
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
                .load();
            assertEquals(2, latest.migrate().migrationsExecuted);
            assertEquals(0, latest.migrate().migrationsExecuted);

            assertEquals(latestMigrationVersion(), isolated.queryForObject(
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

    private String latestMigrationVersion() throws Exception {
        try (var migrationFiles = Files.list(Path.of("src/main/resources/db/migration"))) {
            return migrationFiles
                .map(path -> path.getFileName().toString())
                .filter(name -> name.matches("V\\d{3}__.+\\.sql"))
                .map(name -> name.substring(1, 4))
                .max(Comparator.naturalOrder())
                .orElseThrow();
        }
    }
}
