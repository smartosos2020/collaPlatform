package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class WorkItemPermissionFoundationIntegrationTests {
    @Test
    void createsVersionBoundRoleAndDecisionFoundationWithoutPermissionPrivateForeignKeys() {
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
                         'project_space_permission_role_bindings',
                         'project_work_item_role_assignments',
                         'project_permission_command_receipts',
                         'project_permission_decision_evidence'
                       )
                     order by table_name
                    """,
                String.class
            );
            assertEquals(4, tables.size());
            assertEquals(4, jdbc.queryForObject(
                """
                    select count(*)
                      from information_schema.triggers
                     where trigger_schema='public'
                       and trigger_name in (
                         'trg_project_permission_command_receipt',
                         'trg_project_permission_decision_evidence'
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
                         'project_space_permission_role_bindings',
                         'project_work_item_role_assignments',
                         'project_permission_command_receipts',
                         'project_permission_decision_evidence'
                       )
                       and target_table.relname in (
                         'resource_permissions', 'resource_permission_requests',
                         'roles', 'role_assignments'
                       )
                    """,
                Integer.class
            ));
            assertTrue(jdbc.queryForObject(
                """
                    select count(*) = 2
                      from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_project_permission_decision_replay',
                         'idx_project_permission_decision_policy'
                       )
                    """,
                Boolean.class
            ));
        } finally {
            container.stop();
        }
    }
}
