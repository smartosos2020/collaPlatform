package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class CrossSpaceSyncFoundationIntegrationTests {
    @Test
    void createsVersionedRulesRunsStepsConflictsAndReceipts() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = Flyway.configure()
                .dataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
                )
                .load();
            assertTrue(flyway.migrate().migrationsExecuted >= 129);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            org.postgresql.ds.PGSimpleDataSource dataSource =
                new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                 where table_schema='public' and table_name in (
                   'project_cross_space_sync_rules',
                   'project_cross_space_sync_rule_versions',
                   'project_cross_space_sync_runs',
                   'project_cross_space_sync_steps',
                   'project_cross_space_sync_conflicts',
                   'project_cross_space_sync_receipts'
                 ) order by table_name
                """, String.class);
            assertEquals(6, tables.size());
            assertTrue(jdbc.queryForObject("""
                select count(*) >= 3 from pg_indexes
                 where schemaname='public' and indexname in (
                   'idx_project_cross_space_sync_rule_party',
                   'idx_project_cross_space_sync_run_timeline',
                   'idx_project_cross_space_sync_conflict_open'
                 )
                """, Boolean.class));
            assertEquals("134", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        }
    }
}
