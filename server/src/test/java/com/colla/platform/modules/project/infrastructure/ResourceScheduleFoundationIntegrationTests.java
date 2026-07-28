package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ResourceScheduleFoundationIntegrationTests {
    @Test
    void createsPreferencesDisposableIndexAdjustmentReceiptsAndStats() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(
                    container.getJdbcUrl(), container.getUsername(),
                    container.getPassword()
                ).load();
            assertTrue(flyway.migrate().migrationsExecuted >= 126);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            org.postgresql.ds.PGSimpleDataSource dataSource =
                new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertEquals(4, jdbc.queryForObject(
                """
                    select count(*) from information_schema.tables
                     where table_schema='public' and table_name in (
                       'project_resource_schedule_preferences',
                       'project_resource_schedule_index',
                       'project_resource_adjustment_commands',
                       'project_resource_schedule_stats'
                     )
                    """,
                Integer.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 2 from pg_indexes
                     where schemaname='public'
                       and indexname like 'idx_project_resource_schedule_%'
                    """,
                Boolean.class
            ));
            assertEquals("130", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        } finally {
            container.stop();
        }
    }
}
