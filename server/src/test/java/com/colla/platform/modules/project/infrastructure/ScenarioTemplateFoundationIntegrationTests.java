package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ScenarioTemplateFoundationIntegrationTests {
    @Test
    void createsScenarioCatalogAndInstallRunFoundationOnRealPostgres() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = Flyway.configure().dataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
            ).load();
            assertTrue(flyway.migrate().migrationsExecuted >= 139);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            var dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertEquals(9, jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema='public' and table_name in (
                   'project_scenario_templates','project_scenario_template_versions',
                   'project_scenario_template_components',
                   'project_scenario_template_installations',
                   'project_scenario_template_install_runs',
                   'project_scenario_template_install_steps',
                   'project_scenario_template_commands',
                   'project_scenario_template_upgrade_diffs',
                   'project_scenario_template_upgrade_conflicts'
                 )
                """, Integer.class));
            assertEquals("139", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        }
    }
}
