package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MetricRiskFoundationIntegrationTests {
    @Test
    void createsVersionedRiskFoundationOnRealPostgres() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = Flyway.configure().dataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
            ).load();
            assertTrue(flyway.migrate().migrationsExecuted >= 133);
            assertEquals(0, flyway.migrate().migrationsExecuted);

            var dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertEquals(6, jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema='public' and table_name in (
                   'project_risk_policies','project_risk_policy_versions',
                   'project_risk_signals','project_risk_signal_actions',
                   'project_risk_commands','project_risk_stats'
                 )
                """, Integer.class));
            assertEquals(3, jdbc.queryForObject("""
                select count(distinct trigger_name) from information_schema.triggers
                 where trigger_schema='public'
                   and trigger_name in (
                     'trg_project_risk_policy_version_immutable',
                     'trg_project_risk_signal_action_immutable',
                     'trg_project_risk_command_immutable'
                   )
                """, Integer.class));
            assertEquals("139", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        }
    }
}
