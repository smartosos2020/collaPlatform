package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class WorkItemQueryFoundationIntegrationTests {
    @Test
    void createsVersionedQueryReceiptAndProjectionStatisticsFoundation() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .load();
            assertTrue(flyway.migrate().migrationsExecuted >= 106);
            assertEquals(0, flyway.migrate().migrationsExecuted);

            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            List<String> tables = jdbc.queryForList(
                """
                    select table_name
                      from information_schema.tables
                     where table_schema='public'
                       and table_name in (
                         'project_work_item_query_definitions',
                         'project_work_item_query_receipts',
                         'project_work_item_query_projection_stats'
                       )
                     order by table_name
                    """,
                String.class
            );
            assertEquals(3, tables.size());
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 3
                      from pg_indexes
                     where schemaname='public'
                       and (
                         indexname like 'idx_project_work_item_query_%'
                         or indexname like 'uk_project_work_item_query_%'
                       )
                    """,
                Boolean.class
            ));
            assertEquals(2, jdbc.queryForObject(
                """
                    select count(*)
                      from information_schema.triggers
                     where trigger_schema='public'
                       and trigger_name='trg_project_work_item_query_receipt'
                    """,
                Integer.class
            ));
        } finally {
            container.stop();
        }
    }
}
