package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ProjectRegisterFoundationIntegrationTests {
    @Test
    void createsRegisterDetailsReferencesResponsesHistoryAndReceipts() {
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
                       'project_register_entries',
                       'project_register_references',
                       'project_register_responses',
                       'project_register_history',
                       'project_register_commands'
                     ) order by table_name
                    """,
                String.class
            );
            assertEquals(5, tables.size());
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 4
                      from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_project_register_list',
                         'idx_project_register_owner_due',
                         'idx_project_register_reference_lookup',
                         'idx_project_register_history_page'
                       )
                    """,
                Boolean.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 1 from information_schema.triggers
                     where trigger_schema='public'
                       and trigger_name='trg_project_register_history'
                    """,
                Boolean.class
            ));
            assertEquals("126", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        } finally {
            container.stop();
        }
    }
}
