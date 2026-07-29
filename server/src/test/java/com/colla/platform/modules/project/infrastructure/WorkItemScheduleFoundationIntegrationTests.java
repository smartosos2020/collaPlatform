package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class WorkItemScheduleFoundationIntegrationTests {
    @Test
    void createsImmutableBaselinesReceiptsAndRebuildableTimelineIndex() {
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
                       'project_work_item_schedule_baselines',
                       'project_work_item_schedule_baseline_entries',
                       'project_work_item_schedule_baseline_dependencies',
                       'project_work_item_schedule_baseline_commands',
                       'project_work_item_timeline_index'
                     ) order by table_name
                    """,
                String.class
            );
            assertEquals(5, tables.size());
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 2
                      from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_project_work_item_schedule_baselines_list',
                         'idx_project_work_item_timeline_index_page'
                       )
                    """,
                Boolean.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 2 from information_schema.triggers
                     where trigger_schema='public'
                       and trigger_name in (
                         'trg_project_work_item_schedule_baseline_entry',
                         'trg_project_work_item_schedule_baseline_dependency'
                       )
                    """,
                Boolean.class
            ));
            assertEquals("139", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        } finally {
            container.stop();
        }
    }
}
