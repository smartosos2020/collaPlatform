package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ProjectPlanFoundationIntegrationTests {
    @Test
    void createsPlanGraphHistoryReceiptsAndCompositeBoundaries() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
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
                       'project_plans',
                       'project_plan_phases',
                       'project_plan_milestones',
                       'project_plan_links',
                       'project_plan_changes',
                       'project_plan_commands'
                     ) order by table_name
                    """,
                String.class
            );
            assertEquals(6, tables.size());
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 5
                      from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_project_plans_list',
                         'idx_project_plan_phases_order',
                         'idx_project_plan_milestones_order',
                         'idx_project_plan_links_item',
                         'idx_project_plan_changes_page'
                       )
                    """,
                Boolean.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 1 from information_schema.triggers
                     where trigger_schema='public'
                       and trigger_name='trg_project_plan_change'
                    """,
                Boolean.class
            ));
            assertEquals("137", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        } finally {
            container.stop();
        }
    }
}
