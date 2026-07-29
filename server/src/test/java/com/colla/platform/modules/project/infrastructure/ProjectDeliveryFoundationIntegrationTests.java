package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ProjectDeliveryFoundationIntegrationTests {
    @Test
    void createsImmutableVersionsReviewsSignoffsAndAcceptance() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(
                    container.getJdbcUrl(), container.getUsername(),
                    container.getPassword()
                )
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
                       'project_deliverables',
                       'project_deliverable_versions',
                       'project_deliverable_materials',
                       'project_deliverable_reviews',
                       'project_deliverable_signoffs',
                       'project_deliverable_acceptances',
                       'project_deliverable_commands'
                     ) order by table_name
                    """,
                String.class
            );
            assertEquals(7, tables.size());
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 4
                      from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_project_deliverables_list',
                         'idx_project_deliverable_material_source',
                         'idx_project_deliverable_reviews_page',
                         'idx_project_deliverable_signoffs_page'
                       )
                    """,
                Boolean.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) >= 4 from information_schema.triggers
                     where trigger_schema='public'
                       and trigger_name like 'trg_project_deliverable_%'
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
