package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class WorkItemRelationFoundationIntegrationTests {
    @Test
    void createsIndependentRelationAuthorityWithImmutableLedgerAndNoWorkflowPrivateDependency() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .load();
            assertEquals(141, flyway.migrate().migrationsExecuted);
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
                         'project_work_item_relations',
                         'project_work_item_relation_commands',
                         'project_work_item_relation_history',
                         'project_work_item_hierarchy_paths',
                         'project_work_item_relation_migration_batches',
                         'project_work_item_relation_migration_units',
                         'project_work_item_relation_migration_verifications'
                       )
                     order by table_name
                    """,
                String.class
            );
            assertEquals(7, tables.size());
            assertEquals(4, jdbc.queryForObject(
                """
                    select count(*)
                      from information_schema.triggers
                     where trigger_schema='public'
                       and trigger_name in (
                         'trg_project_work_item_relation_command',
                         'trg_project_work_item_relation_history'
                       )
                    """,
                Integer.class
            ));
            assertEquals(0, jdbc.queryForObject(
                """
                    select count(*)
                      from pg_constraint c
                      join pg_class source_table on source_table.oid=c.conrelid
                      join pg_class target_table on target_table.oid=c.confrelid
                     where c.contype='f'
                       and source_table.relname in (
                         'project_work_item_relations',
                         'project_work_item_relation_commands',
                         'project_work_item_relation_history',
                         'project_work_item_hierarchy_paths'
                       )
                       and (
                         target_table.relname like 'project_work_item_workflow_%'
                         or target_table.relname like 'project_node_workflow_%'
                       )
                    """,
                Integer.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) > 0
                      from pg_indexes
                     where schemaname='public'
                       and indexname='uk_project_work_item_relations_active_edge'
                    """,
                Boolean.class
            ));
        } finally {
            container.stop();
        }
    }
}
