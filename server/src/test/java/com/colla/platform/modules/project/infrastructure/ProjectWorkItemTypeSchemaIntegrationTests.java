package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
class ProjectWorkItemTypeSchemaIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void activeSchemaContainsTypeDefinitionAndImmutableVersionContract() {
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select count(*) from information_schema.tables
                 where table_schema = 'public'
                   and table_name in ('project_work_item_types', 'project_work_item_type_versions')
                """,
            Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select count(*) from information_schema.table_constraints
                 where table_schema = 'public'
                   and table_name = 'project_work_item_types'
                   and constraint_name = 'fk_project_work_item_types_current_version'
                   and constraint_type = 'FOREIGN KEY'
                """,
            Integer.class
        ));
        assertEquals(3, jdbcTemplate.queryForObject(
            """
                select count(*) from pg_indexes
                 where schemaname = 'public'
                   and indexname in (
                     'idx_project_work_item_types_space_status_order',
                     'idx_project_work_item_types_workspace_status',
                     'idx_project_work_item_type_versions_definition'
                   )
                """,
            Integer.class
        ));
        assertTrue(jdbcTemplate.queryForObject(
            "select exists(select 1 from pg_trigger where tgname='trg_project_work_item_type_version_immutability' and not tgisinternal)",
            Boolean.class
        ));
    }

    @Test
    void v060SchemaUpgradesToLatestInIsolatedDatabase() throws Exception {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("60")
                .load()
                .migrate();
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .load()
                .migrate();

            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate isolated = new JdbcTemplate(dataSource);
            assertEquals(latestMigrationVersion(), isolated.queryForObject(
                "select max(version) from flyway_schema_history",
                String.class
            ));
            assertEquals(2, isolated.queryForObject(
                """
                    select count(*) from information_schema.tables
                     where table_schema='public'
                       and table_name in ('project_work_item_types', 'project_work_item_type_versions')
                    """,
                Integer.class
            ));
            assertEquals(1, isolated.queryForObject(
                "select count(*) from information_schema.tables where table_schema='public' and table_name='project_work_item_type_commands'",
                Integer.class
            ));
            assertEquals(0, isolated.queryForObject(
                "select count(*) from information_schema.tables where table_schema='public' and table_name='project_work_items'",
                Integer.class
            ));
            assertEquals(2, isolated.queryForObject(
                "select count(*) from pg_trigger where tgname in ('trg_project_work_item_type_identity', 'trg_project_work_item_type_version_immutability') and not tgisinternal",
                Integer.class
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
