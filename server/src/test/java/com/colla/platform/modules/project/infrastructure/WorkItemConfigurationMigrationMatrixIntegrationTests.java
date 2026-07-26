package com.colla.platform.modules.project.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WorkItemConfigurationMigrationMatrixIntegrationTests {
    private static final String LATEST = "090";
    private static final UUID WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        dataSource();
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .cleanDisabled(false)
            .load()
            .clean();
        jdbc = new JdbcTemplate(dataSource());
    }

    @Test
    void upgradesV001V061V065AndV085ToLatestRepeatably() {
        for (String baseline : new String[]{"001", "061", "065", "085"}) {
            cleanAndMigrateTo(baseline);
            Flyway latest = latest();
            assertTrue(latest.migrate().migrationsExecuted > 0);
            assertEquals(0, latest.migrate().migrationsExecuted);
            assertEquals(LATEST, maxVersion());
        }
    }

    @Test
    void upgradesV078LegacyDraftWithoutRewritingPublishedSentinel() throws Exception {
        cleanAndMigrateTo("078");
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        insertIdentity();
        insertType(spaceId, typeId, publishedId);
        jdbc.update(
            """
                insert into project_work_item_type_versions (
                    id, workspace_id, space_id, type_definition_id, version_number,
                    config_hash, status, config, created_by, created_at
                ) values (?, ?, ?, ?, 2, ?, 'draft', '{"legacy":"sentinel"}'::jsonb, ?, now())
                """,
            draftId,
            WORKSPACE_ID,
            spaceId,
            typeId,
            "d".repeat(64),
            USER_ID
        );
        String publishedHash = jdbc.queryForObject(
            "select config_hash from project_work_item_type_versions where id=?",
            String.class,
            publishedId
        );

        latest().migrate();

        assertEquals(LATEST, maxVersion());
        assertEquals(publishedId, jdbc.queryForObject(
            "select current_version_id from project_work_item_types where id=?",
            UUID.class,
            typeId
        ));
        assertEquals(publishedHash, jdbc.queryForObject(
            "select config_hash from project_work_item_type_versions where id=?",
            String.class,
            publishedId
        ));
        assertEquals("superseded", jdbc.queryForObject(
            "select status from project_work_item_type_versions where id=?",
            String.class,
            draftId
        ));
        assertEquals("invalid", jdbc.queryForObject(
            "select status from project_work_item_configuration_drafts where source_legacy_version_id=?",
            String.class,
            draftId
        ));
        assertEquals(1, jdbc.queryForObject(
            """
                select count(*)
                  from project_work_item_legacy_draft_diagnostics
                 where legacy_version_id=? and diagnostic_code='legacy_partial_snapshot'
                """,
            Integer.class,
            draftId
        ));
        assertEquals(0, latest().migrate().migrationsExecuted);
    }

    @Test
    void restoresPreUpgradeBackupAndCanMigrateTheRestoredDatabase() throws Exception {
        cleanAndMigrateTo("065");
        insertIdentity();
        UUID projectId = UUID.randomUUID();
        jdbc.update(
            """
                insert into projects (
                    id, workspace_id, project_key, name, description, status,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, 'S06-ROLLBACK', 'S06 rollback sentinel',
                          'pre-upgrade backup', 'active', ?, now(), ?, now())
                """,
            projectId,
            WORKSPACE_ID,
            USER_ID,
            USER_ID
        );
        var dump = POSTGRES.execInContainer(
            "pg_dump", "-Fc", "-U", POSTGRES.getUsername(),
            "-d", POSTGRES.getDatabaseName(), "-f", "/tmp/s06-pre-upgrade.dump"
        );
        assertEquals(0, dump.getExitCode(), dump.getStderr());

        latest().migrate();
        String restoredDatabase = "s06_restore_" + UUID.randomUUID().toString().replace("-", "");
        assertEquals(0, POSTGRES.execInContainer(
            "createdb", "-U", POSTGRES.getUsername(), restoredDatabase
        ).getExitCode());
        var restore = POSTGRES.execInContainer(
            "pg_restore", "-U", POSTGRES.getUsername(), "-d", restoredDatabase,
            "/tmp/s06-pre-upgrade.dump"
        );
        assertEquals(0, restore.getExitCode(), restore.getStderr());
        String restoredUrl = POSTGRES.getJdbcUrl().replace(
            "/" + POSTGRES.getDatabaseName() + "?",
            "/" + restoredDatabase + "?"
        );
        Flyway restored = Flyway.configure()
            .dataSource(restoredUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
            .load();
        restored.migrate();
        org.postgresql.ds.PGSimpleDataSource restoredDataSource =
            new org.postgresql.ds.PGSimpleDataSource();
        restoredDataSource.setURL(restoredUrl);
        restoredDataSource.setUser(POSTGRES.getUsername());
        restoredDataSource.setPassword(POSTGRES.getPassword());
        JdbcTemplate restoredJdbc = new JdbcTemplate(restoredDataSource);

        assertEquals("S06 rollback sentinel", restoredJdbc.queryForObject(
            "select name from projects where id=?",
            String.class,
            projectId
        ));
        assertEquals(LATEST, restoredJdbc.queryForObject(
            "select max(version) from flyway_schema_history",
            String.class
        ));
    }

    private void cleanAndMigrateTo(String target) {
        latest().clean();
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .target(target)
            .cleanDisabled(false)
            .load()
            .migrate();
    }

    private Flyway latest() {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .cleanDisabled(false)
            .load();
    }

    private String maxVersion() {
        return jdbc.queryForObject("select max(version) from flyway_schema_history", String.class);
    }

    private org.postgresql.ds.PGSimpleDataSource dataSource() {
        org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private void insertIdentity() {
        jdbc.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, 's06-matrix', 'not-used', 'S06 Matrix', 'active', now(), now())
                on conflict (id) do nothing
                """,
            USER_ID,
            WORKSPACE_ID
        );
    }

    private void insertType(UUID spaceId, UUID typeId, UUID versionId) throws Exception {
        jdbc.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, status, visibility, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, 'S06 matrix space', 'active', 'private', 0, ?, now(), ?, now())
                """,
            spaceId,
            WORKSPACE_ID,
            "s06_matrix_" + spaceId,
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
                    ) values (?, ?, ?, 'matrix', 'Matrix', '', '', 0, 'active', false,
                              ?, ?, now(), ?, now(), 0)
                    """);
                var version = connection.prepareStatement("""
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, created_by, created_at,
                        published_by, published_at
                    ) values (?, ?, ?, ?, 1, ?, 'published',
                              '{"legacy":"published-sentinel"}'::jsonb, ?, now(), ?, now())
                    """)
            ) {
                type.setObject(1, typeId);
                type.setObject(2, WORKSPACE_ID);
                type.setObject(3, spaceId);
                type.setObject(4, versionId);
                type.setObject(5, USER_ID);
                type.setObject(6, USER_ID);
                type.executeUpdate();
                version.setObject(1, versionId);
                version.setObject(2, WORKSPACE_ID);
                version.setObject(3, spaceId);
                version.setObject(4, typeId);
                version.setString(5, "p".repeat(64).replace('p', 'a'));
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
