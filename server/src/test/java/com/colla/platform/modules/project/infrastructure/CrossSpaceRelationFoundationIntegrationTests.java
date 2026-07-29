package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class CrossSpaceRelationFoundationIntegrationTests {
    @Test
    void createsPolicyIntentCanonicalEdgeHistoryAndReceiptBoundaries() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = Flyway.configure()
                .dataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
                )
                .load();
            assertTrue(flyway.migrate().migrationsExecuted >= 128);
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
                   'project_cross_space_relation_policies',
                   'project_cross_space_link_intents',
                   'project_cross_space_relation_receipts',
                   'project_work_item_cross_space_relations',
                   'project_work_item_cross_space_relation_history'
                 ) order by table_name
                """, String.class);
            assertEquals(5, tables.size());
            assertTrue(jdbc.queryForObject("""
                select count(*) >= 4 from pg_indexes
                 where schemaname='public' and indexname in (
                   'idx_project_cross_space_relation_policy_visible',
                   'idx_project_cross_space_link_intent_party',
                   'idx_project_work_item_cross_space_relations_source',
                   'idx_project_work_item_cross_space_relations_target'
                 )
                """, Boolean.class));
            assertEquals("137", jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class
            ));
        }
    }
}
