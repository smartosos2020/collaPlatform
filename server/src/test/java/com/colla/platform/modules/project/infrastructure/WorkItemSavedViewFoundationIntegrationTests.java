package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class WorkItemSavedViewFoundationIntegrationTests {
    @Test
    void createsImmutableVersionedSavedViewAndPlatformObjectFoundation() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .load();
            assertTrue(flyway.migrate().migrationsExecuted >= 109);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<String> tables = jdbc.queryForList(
                """
                    select table_name from information_schema.tables
                     where table_schema='public' and table_name in (
                       'project_work_item_saved_views',
                       'project_work_item_saved_view_versions',
                       'project_work_item_saved_view_shares',
                       'project_work_item_saved_view_commands'
                     ) order by table_name
                    """,
                String.class
            );
            assertEquals(4, tables.size());
            assertEquals("saved_view", jdbc.queryForObject(
                "select object_type from object_type_rules where object_type='saved_view'",
                String.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 3
                      from pg_indexes
                     where schemaname='public'
                       and (
                         indexname like 'idx_project_work_item_saved_view_%'
                         or indexname like 'uk_project_work_item_saved_view_%'
                       )
                    """,
                Boolean.class
            ));
        } finally {
            container.stop();
        }
    }
}
