package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.DraftCommandResponse;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.DraftCommandStart;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.NewDraft;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.UpdateDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class ConfigurationDraftFoundationIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEGACY_SPACE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID LEGACY_TYPE_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID PUBLISHED_VERSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID LEGACY_DRAFT_VERSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");

    @Test
    void migratesLegacyDraftAndPersistsOptimisticDraftsWithExactReceipts() throws Exception {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("79")
                .load()
                .migrate();
            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertIdentityAndLegacyDraft(jdbc);

            Flyway latest = Flyway.configure().dataSource(dataSource).load();
            assertEquals(30, latest.migrate().migrationsExecuted);
            assertEquals(0, latest.migrate().migrationsExecuted);
            assertEquals("109", jdbc.queryForObject("select max(version) from flyway_schema_history", String.class));
            assertEquals("superseded", jdbc.queryForObject(
                "select status from project_work_item_type_versions where id=?",
                String.class,
                LEGACY_DRAFT_VERSION_ID
            ));
            assertEquals(0, jdbc.queryForObject(
                "select count(*) from project_work_item_type_versions where status='draft'",
                Integer.class
            ));
            assertEquals("invalid", jdbc.queryForObject(
                """
                    select status
                      from project_work_item_configuration_drafts
                     where source_legacy_version_id=?
                    """,
                String.class,
                LEGACY_DRAFT_VERSION_ID
            ));
            assertEquals(1, jdbc.queryForObject(
                """
                    select count(*)
                      from project_work_item_legacy_draft_diagnostics
                     where legacy_version_id=? and diagnostic_code='legacy_partial_snapshot'
                    """,
                Integer.class,
                LEGACY_DRAFT_VERSION_ID
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                """
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, created_by, created_at
                    ) values (?, ?, ?, ?, 3, ?, 'draft', '{}'::jsonb, ?, now())
                    """,
                UUID.randomUUID(),
                WORKSPACE_ID,
                LEGACY_SPACE_ID,
                LEGACY_TYPE_ID,
                "2".repeat(64),
                USER_ID
            ));

            verifyRepositoryContract(jdbc);
        } finally {
            container.stop();
        }
    }

    private void verifyRepositoryContract(JdbcTemplate jdbc) {
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        insertType(jdbc, spaceId, typeId, versionId, "repository");
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcConfigurationDraftRepository repository = new JdbcConfigurationDraftRepository(jdbc, objectMapper);
        UUID draftId = UUID.randomUUID();
        var snapshot = objectMapper.createObjectNode()
            .put("snapshotSchemaVersion", 1)
            .set("fields", objectMapper.createArrayNode());
        var diagnostics = objectMapper.createArrayNode();
        assertTrue(repository.tryInsert(new NewDraft(
            draftId, WORKSPACE_ID, spaceId, typeId, "editing", 1,
            "3".repeat(64), snapshot, diagnostics, null, "live_edit", USER_ID
        )));
        assertFalse(repository.tryInsert(new NewDraft(
            UUID.randomUUID(), WORKSPACE_ID, spaceId, typeId, "editing", 1,
            "3".repeat(64), snapshot, diagnostics, null, "live_edit", USER_ID
        )));
        assertEquals(draftId, repository.findActive(WORKSPACE_ID, spaceId, typeId).orElseThrow().id());
        assertEquals(1, repository.update(new UpdateDraft(
            WORKSPACE_ID, spaceId, typeId, draftId, "valid", 1,
            "4".repeat(64), snapshot, diagnostics, USER_ID, 0
        )));
        assertEquals(0, repository.update(new UpdateDraft(
            WORKSPACE_ID, spaceId, typeId, draftId, "valid", 1,
            "4".repeat(64), snapshot, diagnostics, USER_ID, 0
        )));

        UUID commandId = UUID.randomUUID();
        String requestId = "draft-save-" + UUID.randomUUID();
        assertTrue(repository.tryStartCommand(new DraftCommandStart(
            commandId, WORKSPACE_ID, spaceId, typeId, requestId,
            "save", "5".repeat(64), USER_ID
        )));
        repository.completeCommand(commandId, new DraftCommandResponse(
            draftId, 1, "4".repeat(64), objectMapper.createObjectNode().put("draftId", draftId.toString())
        ));
        var receipt = repository.findCommand(
            WORKSPACE_ID, spaceId, typeId, "save", requestId
        ).orElseThrow();
        assertEquals("completed", receipt.status());
        assertEquals(draftId.toString(), receipt.responsePayload().path("draftId").asText());
        assertThrows(DataAccessException.class, () -> jdbc.update(
            "update project_work_item_configuration_draft_commands set response_config_hash=? where id=?",
            "6".repeat(64),
            commandId
        ));
    }

    private void insertIdentityAndLegacyDraft(JdbcTemplate jdbc) {
        jdbc.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, 's06-migration', 'not-used', 'S06 Migration', 'active', now(), now())
                """,
            USER_ID,
            WORKSPACE_ID
        );
        insertType(jdbc, LEGACY_SPACE_ID, LEGACY_TYPE_ID, PUBLISHED_VERSION_ID, "legacy");
        jdbc.update(
            """
                insert into project_work_item_type_versions (
                    id, workspace_id, space_id, type_definition_id, version_number,
                    config_hash, status, config, created_by, created_at,
                    published_by, published_at
                ) values (?, ?, ?, ?, 2, ?, 'draft', '{"legacy":true}'::jsonb, ?, now(), null, null)
                """,
            LEGACY_DRAFT_VERSION_ID,
            WORKSPACE_ID,
            LEGACY_SPACE_ID,
            LEGACY_TYPE_ID,
            "1".repeat(64),
            USER_ID
        );
    }

    private void insertType(
        JdbcTemplate jdbc,
        UUID spaceId,
        UUID typeId,
        UUID versionId,
        String key
    ) {
        jdbc.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, status, visibility, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', 'private', 0, ?, now(), ?, now())
                """,
            spaceId,
            WORKSPACE_ID,
            key + "_space",
            key + " space",
            USER_ID,
            USER_ID
        );
        jdbc.execute((Connection connection) -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (
                var type = connection.prepareStatement("""
                    insert into project_work_item_types (
                        id, workspace_id, space_id, type_key, name, icon, description,
                        sort_order, status, is_system, current_version_id, created_by,
                        created_at, updated_by, updated_at, aggregate_version
                    ) values (?, ?, ?, ?, ?, '', '', 0, 'active', false, ?, ?, now(), ?, now(), 0)
                    """);
                var version = connection.prepareStatement("""
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, created_by, created_at,
                        published_by, published_at
                    ) values (?, ?, ?, ?, 1, ?, 'published', '{}'::jsonb, ?, now(), ?, now())
                    """)
            ) {
                type.setObject(1, typeId);
                type.setObject(2, WORKSPACE_ID);
                type.setObject(3, spaceId);
                type.setString(4, key);
                type.setString(5, key);
                type.setObject(6, versionId);
                type.setObject(7, USER_ID);
                type.setObject(8, USER_ID);
                type.executeUpdate();

                version.setObject(1, versionId);
                version.setObject(2, WORKSPACE_ID);
                version.setObject(3, spaceId);
                version.setObject(4, typeId);
                version.setString(5, "0".repeat(64));
                version.setObject(6, USER_ID);
                version.setObject(7, USER_ID);
                version.executeUpdate();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }
}
