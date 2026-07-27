package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository.PlatformTemplateImport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ConfigurationTemplateFoundationIntegrationTests {
    @Test
    void migratesRepeatablyAndKeepsPlatformVersionsImmutable() throws Exception {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            var dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertEquals("109", jdbc.queryForObject(
                "select max(version) from flyway_schema_history",
                String.class
            ));

            ObjectMapper objectMapper = new ObjectMapper();
            var repository = new JdbcConfigurationTemplateRepository(jdbc, objectMapper);
            UUID templateId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            var snapshot = objectMapper.readTree("""
                {
                  "snapshotSchemaVersion":1,
                  "typeDefinition":{"typeKey":"task"},
                  "fields":[],
                  "layouts":[]
                }
                """);
            PlatformTemplateImport imported = new PlatformTemplateImport(
                templateId,
                versionId,
                "platform-task",
                "Task",
                "Platform task template",
                1,
                "a".repeat(64),
                snapshot,
                "development-v1"
            );
            repository.importPlatformTemplate(imported);
            repository.importPlatformTemplate(imported);

            assertEquals(1, repository.listVisible(UUID.randomUUID()).size());
            assertEquals(versionId, repository.findVisible(
                UUID.randomUUID(), templateId
            ).orElseThrow().currentVersionId());
            assertEquals(1, repository.listVersions(UUID.randomUUID(), templateId).size());
            assertThrows(DataAccessException.class, () -> jdbc.update(
                """
                    update project_work_item_configuration_template_versions
                       set config_hash=?
                     where id=?
                    """,
                "b".repeat(64),
                versionId
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                "delete from project_work_item_configuration_template_versions where id=?",
                versionId
            ));
        } finally {
            container.stop();
        }
    }
}
