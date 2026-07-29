package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MetricGovernanceFoundationIntegrationTests {
    @Test
    void createsGovernanceReportFoundationOnRealPostgres() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = Flyway.configure().dataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
            ).load();
            assertTrue(flyway.migrate().migrationsExecuted >= 134);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            var dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertEquals(4, jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema='public' and table_name in (
                   'project_governance_reports','project_governance_report_runs',
                   'project_governance_exports','project_governance_commands'
                 )
                """, Integer.class));
            assertEquals(3, jdbc.queryForObject("""
                select count(distinct trigger_name) from information_schema.triggers
                 where trigger_schema='public'
                   and trigger_name like 'trg_project_governance_%_immutable'
                """, Integer.class));
            assertEquals("137", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        }
    }
}
