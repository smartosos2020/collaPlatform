package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class WorkItemLayoutReceiptMigrationIntegrationTests {
    @Test
    void v077UpgradeToV078AddsVersionedImmutableReceiptColumns() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("77")
                .load()
                .migrate();
            Flyway latest = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("78")
                .load();

            assertEquals(1, latest.migrate().migrationsExecuted);
            assertEquals(0, latest.migrate().migrationsExecuted);

            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate isolated = new JdbcTemplate(dataSource);
            assertEquals("078", isolated.queryForObject(
                "select max(version) from flyway_schema_history",
                String.class
            ));
            assertEquals(4, isolated.queryForObject(
                """
                    select count(*)
                      from information_schema.columns
                     where table_schema='public'
                       and table_name='project_work_item_layout_commands'
                       and column_name in (
                         'response_schema_version',
                         'response_aggregate_version',
                         'response_config_hash',
                         'response_payload'
                       )
                    """,
                Integer.class
            ));
            assertTrue(isolated.queryForObject(
                """
                    select exists(
                        select 1
                          from pg_trigger
                         where tgname='trg_project_work_item_layout_command_receipt'
                           and not tgisinternal
                    )
                    """,
                Boolean.class
            ));
        } finally {
            container.stop();
        }
    }

    @Test
    void v078UpgradeToLatestAddsStableIssueVerificationSequence() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("78")
                .load()
                .migrate();
            Flyway latest = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("79")
                .load();

            assertEquals(1, latest.migrate().migrationsExecuted);
            assertEquals(0, latest.migrate().migrationsExecuted);

            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate isolated = new JdbcTemplate(dataSource);
            assertEquals("079", isolated.queryForObject(
                "select max(version) from flyway_schema_history",
                String.class
            ));
            assertEquals("NO", isolated.queryForObject(
                """
                    select is_nullable
                      from information_schema.columns
                     where table_schema='public'
                       and table_name='issue_verification_logs'
                       and column_name='sequence_no'
                    """,
                String.class
            ));
            assertTrue(isolated.queryForObject(
                """
                    select column_default like 'nextval(%'
                      from information_schema.columns
                     where table_schema='public'
                       and table_name='issue_verification_logs'
                       and column_name='sequence_no'
                    """,
                Boolean.class
            ));
            assertTrue(isolated.queryForObject(
                """
                    select exists(
                        select 1
                          from pg_indexes
                         where schemaname='public'
                           and indexname='idx_issue_verification_logs_issue_sequence'
                    )
                    """,
                Boolean.class
            ));
        } finally {
            container.stop();
        }
    }
}
