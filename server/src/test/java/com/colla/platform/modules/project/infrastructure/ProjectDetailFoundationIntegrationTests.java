package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ProjectDetailFoundationIntegrationTests {
    @Test
    void createsDisposableHealthProjectionAndPersonalPreferences() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(
                    container.getJdbcUrl(), container.getUsername(),
                    container.getPassword()
                )
                .load();
            assertTrue(flyway.migrate().migrationsExecuted >= 126);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            org.postgresql.ds.PGSimpleDataSource dataSource =
                new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<String> tables = jdbc.queryForList(
                """
                    select table_name from information_schema.tables
                     where table_schema='public' and table_name in (
                       'project_detail_preferences',
                       'project_detail_commands',
                       'project_health_projection_index'
                     ) order by table_name
                    """,
                String.class
            );
            assertEquals(3, tables.size());
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) = 1
                      from pg_indexes
                     where schemaname='public'
                       and indexname = 'idx_project_health_projection_expiry'
                    """,
                Boolean.class
            ));
            assertEquals("134", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        } finally {
            container.stop();
        }
    }
}
