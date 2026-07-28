package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class CrossTeamPanoramaFoundationIntegrationTests {
    @Test
    void createsPreferenceRebuildableStatsAndGovernanceReceipt() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = Flyway.configure().dataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
            ).load();
            assertTrue(flyway.migrate().migrationsExecuted >= 130);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            org.postgresql.ds.PGSimpleDataSource dataSource =
                new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertEquals(3, jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema='public' and table_name in (
                   'project_cross_team_panorama_preferences',
                   'project_cross_team_panorama_slice_stats',
                   'project_cross_team_panorama_governance_receipts'
                 )
                """, Integer.class));
            assertEquals("130", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        }
    }
}
