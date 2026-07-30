package com.colla.platform.modules.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class AutomationConnectorFoundationIntegrationTests {
    @Test
    void migratesConnectorDeliveryAttemptDeadLetterAndReceiptFoundation() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            Flyway.configure().dataSource((DataSource) dataSource).locations("classpath:db/migration").load().migrate();
            assertThat(Flyway.configure().dataSource(dataSource).load().info().current().getVersion().getVersion())
                .isEqualTo("141");
            try (var connection=dataSource.getConnection(); var statement=connection.createStatement();
                 var result=statement.executeQuery("""
                    select count(*) from information_schema.tables where table_name in
                    ('project_automation_connectors','project_automation_deliveries',
                     'project_automation_delivery_attempts','project_automation_dead_letters',
                     'project_automation_connector_commands')
                    """)) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(5);
            }
        }
    }
}
