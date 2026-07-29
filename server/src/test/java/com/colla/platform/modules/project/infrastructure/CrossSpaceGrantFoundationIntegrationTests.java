package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class CrossSpaceGrantFoundationIntegrationTests {
    @Test
    void createsGrantVersionConfirmationHistoryAndReceiptBoundaries() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(
                    container.getJdbcUrl(), container.getUsername(),
                    container.getPassword()
                )
                .load();
            assertTrue(flyway.migrate().migrationsExecuted >= 127);
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
                       'project_cross_space_grants',
                       'project_cross_space_grant_versions',
                       'project_cross_space_grant_receipts'
                     ) order by table_name
                    """,
                String.class
            );
            assertEquals(3, tables.size());
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 3 from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_cross_space_grants_source',
                         'idx_cross_space_grants_target',
                         'idx_cross_space_grant_versions_history'
                       )
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
