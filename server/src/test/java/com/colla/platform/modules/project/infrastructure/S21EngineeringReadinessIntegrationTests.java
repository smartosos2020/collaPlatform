package com.colla.platform.modules.project.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Fresh real-system evidence for PROJECT-PLATFORM-S21-M3.
 *
 * <p>The budgets are local isolated regression budgets, not production SLOs.
 * All facts are deterministic, synthetic and use only project_space/work_item
 * product identities.
 */
class S21EngineeringReadinessIntegrationTests {
    private static final UUID WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
        UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final List<String> SCENARIOS =
        List.of("development", "marketing", "hr", "delivery");
    private static final int ITEMS_PER_SCENARIO = 250;
    private static final String CONFIG_HASH = "21".repeat(32);

    @Test
    void provesCanonicalCapacitySecurityAndQuiescedBackupRestoreOnPostgres16()
        throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load();
            assertThat(flyway.migrate().migrationsExecuted).isGreaterThanOrEqualTo(139);
            assertThat(flyway.migrate().migrationsExecuted).isZero();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource(postgres, postgres.getDatabaseName()));
            insertSyntheticActor(jdbc);
            List<Fixture> fixtures = new ArrayList<>();
            for (String scenario : SCENARIOS) {
                Fixture fixture = insertScenario(jdbc, scenario);
                insertWorkItems(jdbc, fixture);
                fixtures.add(fixture);
            }

            assertThat(jdbc.queryForObject(
                "select count(*) from project_work_items where workspace_id=?",
                Integer.class,
                WORKSPACE_ID
            )).isEqualTo(SCENARIOS.size() * ITEMS_PER_SCENARIO);
            assertThat(jdbc.queryForList(
                """
                    select distinct field_values->>'scenario'
                      from project_work_items
                     where workspace_id=?
                     order by 1
                    """,
                String.class,
                WORKSPACE_ID
            )).containsExactlyElementsOf(SCENARIOS.stream().sorted().toList());

            BudgetObservation budgets = exerciseLocalBudgets(jdbc, fixtures);
            assertThat(budgets.indexedReadP95Ms()).isLessThanOrEqualTo(250);
            assertThat(budgets.aggregateReadP95Ms()).isLessThanOrEqualTo(500);
            assertThat(budgets.writeP95Ms()).isLessThanOrEqualTo(500);
            assertThat(budgets.bytesPerWorkItem()).isLessThanOrEqualTo(65_536);

            assertCanonicalSecurityBoundary(jdbc, fixtures.getFirst());
            assertRecoverySetRegistered(jdbc);

            String sourceDigest = canonicalDigest(jdbc);
            long backupStarted = System.nanoTime();
            var dump = postgres.execInContainer(
                "pg_dump",
                "-Fc",
                "-U",
                postgres.getUsername(),
                "-d",
                postgres.getDatabaseName(),
                "-f",
                "/tmp/s21-engineering-readiness.dump"
            );
            assertThat(dump.getExitCode()).as(dump.getStderr()).isZero();
            long backupSeconds = elapsedSeconds(backupStarted);
            assertThat(backupSeconds).isLessThanOrEqualTo(120);
            var checksum = postgres.execInContainer(
                "sha256sum",
                "/tmp/s21-engineering-readiness.dump"
            );
            assertThat(checksum.getExitCode()).as(checksum.getStderr()).isZero();
            assertThat(checksum.getStdout()).matches("(?s)^[0-9a-f]{64}\\s+.*$");

            String restoredDatabase =
                "s21_restore_" + UUID.randomUUID().toString().replace("-", "");
            assertThat(postgres.execInContainer(
                "createdb",
                "-U",
                postgres.getUsername(),
                restoredDatabase
            ).getExitCode()).isZero();
            long restoreStarted = System.nanoTime();
            var restore = postgres.execInContainer(
                "pg_restore",
                "--exit-on-error",
                "--single-transaction",
                "-U",
                postgres.getUsername(),
                "-d",
                restoredDatabase,
                "/tmp/s21-engineering-readiness.dump"
            );
            assertThat(restore.getExitCode()).as(restore.getStderr()).isZero();
            long restoreSeconds = elapsedSeconds(restoreStarted);
            assertThat(restoreSeconds).isLessThanOrEqualTo(180);

            JdbcTemplate restored = new JdbcTemplate(dataSource(postgres, restoredDatabase));
            assertThat(restored.queryForObject(
                "select max(version) from flyway_schema_history",
                String.class
            )).isEqualTo("139");
            assertThat(canonicalDigest(restored)).isEqualTo(sourceDigest);
            assertThat(restored.queryForObject(
                "select count(*) from project_work_items where workspace_id=?",
                Integer.class,
                WORKSPACE_ID
            )).isEqualTo(SCENARIOS.size() * ITEMS_PER_SCENARIO);
            assertInterruptedChangeRollsBack(restored);

            System.out.printf(
                "S21 engineering readiness PASS scenarios=%d workItems=%d "
                    + "indexedReadP95Ms=%d aggregateReadP95Ms=%d writeP95Ms=%d "
                    + "bytesPerWorkItem=%d backupSeconds=%d restoreSeconds=%d "
                    + "rpoSeconds=0 productionSloClaim=false%n",
                SCENARIOS.size(),
                SCENARIOS.size() * ITEMS_PER_SCENARIO,
                budgets.indexedReadP95Ms(),
                budgets.aggregateReadP95Ms(),
                budgets.writeP95Ms(),
                budgets.bytesPerWorkItem(),
                backupSeconds,
                restoreSeconds
            );
        }
    }

    private Fixture insertScenario(JdbcTemplate jdbc, String scenario) throws Exception {
        UUID spaceId = deterministic("space:" + scenario);
        UUID typeId = deterministic("type:" + scenario);
        UUID versionId = deterministic("version:" + scenario);
        jdbc.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, description, status, visibility,
                    version, created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'S21 synthetic engineering evidence',
                          'active', 'private', 0, ?, '2026-07-29T00:00:00Z',
                          ?, '2026-07-29T00:00:00Z')
                """,
            spaceId,
            WORKSPACE_ID,
            "s21_" + scenario,
            "S21 " + scenario,
            USER_ID,
            USER_ID
        );
        jdbc.execute((Connection connection) -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (
                var type = connection.prepareStatement(
                    """
                        insert into project_work_item_types (
                            id, workspace_id, space_id, type_key, name, icon, description,
                            sort_order, status, is_system, current_version_id, created_by,
                            created_at, updated_by, updated_at, aggregate_version
                        ) values (?, ?, ?, 'scenario_item', 'Scenario Item', '', '',
                                  0, 'active', false, ?, ?,
                                  '2026-07-29T00:00:00Z', ?,
                                  '2026-07-29T00:00:00Z', 0)
                        """
                );
                var version = connection.prepareStatement(
                    """
                        insert into project_work_item_type_versions (
                            id, workspace_id, space_id, type_definition_id, version_number,
                            config_hash, status, config, created_by, created_at,
                            published_by, published_at
                        ) values (?, ?, ?, ?, 1, ?, 'published',
                                  '{"source":"s21-engineering-readiness"}'::jsonb, ?,
                                  '2026-07-29T00:00:00Z', ?,
                                  '2026-07-29T00:00:00Z')
                        """
                )
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
                version.setString(5, CONFIG_HASH);
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
        return new Fixture(scenario, spaceId, typeId, versionId);
    }

    private void insertWorkItems(JdbcTemplate jdbc, Fixture fixture) {
        for (int ordinal = 1; ordinal <= ITEMS_PER_SCENARIO; ordinal++) {
            UUID itemId = deterministic("item:" + fixture.scenario() + ":" + ordinal);
            jdbc.update(
                """
                    insert into project_work_items (
                        id, workspace_id, space_id, type_definition_id, type_version_id,
                        config_hash, item_number, display_key, title, field_values, status,
                        version, created_by, created_at, updated_by, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'active', 0, ?,
                              '2026-07-29T00:00:00Z', ?, '2026-07-29T00:00:00Z')
                    """,
                itemId,
                WORKSPACE_ID,
                fixture.spaceId(),
                fixture.typeId(),
                fixture.versionId(),
                CONFIG_HASH,
                ordinal,
                fixture.scenario()
                    .substring(0, Math.min(3, fixture.scenario().length()))
                    .toUpperCase() + "-" + ordinal,
                fixture.scenario() + " synthetic item " + ordinal,
                """
                    {"scenario":"%s","operation":"step-%d","synthetic":true}
                    """.formatted(fixture.scenario(), ((ordinal - 1) % 5) + 1),
                USER_ID,
                USER_ID
            );
        }
    }

    private BudgetObservation exerciseLocalBudgets(
        JdbcTemplate jdbc,
        List<Fixture> fixtures
    ) {
        List<Long> indexedReads = new ArrayList<>();
        List<Long> aggregateReads = new ArrayList<>();
        List<Long> writes = new ArrayList<>();
        for (int iteration = 0; iteration < 20; iteration++) {
            Fixture fixture = fixtures.get(iteration % fixtures.size());
            long started = System.nanoTime();
            assertThat(jdbc.queryForList(
                """
                    select id, display_key, title
                      from project_work_items
                     where workspace_id=? and space_id=? and status='active'
                     order by updated_at desc, id desc
                     limit 50
                    """,
                WORKSPACE_ID,
                fixture.spaceId()
            )).hasSize(50);
            indexedReads.add(elapsedMillis(started));

            started = System.nanoTime();
            assertThat(jdbc.queryForObject(
                """
                    select count(*) from project_work_items
                     where workspace_id=? and field_values->>'scenario'=?
                    """,
                Integer.class,
                WORKSPACE_ID,
                fixture.scenario()
            )).isEqualTo(ITEMS_PER_SCENARIO);
            aggregateReads.add(elapsedMillis(started));

            started = System.nanoTime();
            assertThat(jdbc.update(
                """
                    update project_work_items
                       set version=version+1, updated_at=now()
                     where id=? and workspace_id=?
                    """,
                deterministic("item:" + fixture.scenario() + ":1"),
                WORKSPACE_ID
            )).isEqualTo(1);
            writes.add(elapsedMillis(started));
        }
        long bytes = jdbc.queryForObject(
            "select pg_total_relation_size('project_work_items')",
            Long.class
        );
        return new BudgetObservation(
            percentile95(indexedReads),
            percentile95(aggregateReads),
            percentile95(writes),
            bytes / (SCENARIOS.size() * ITEMS_PER_SCENARIO)
        );
    }

    private void assertCanonicalSecurityBoundary(JdbcTemplate jdbc, Fixture fixture) {
        assertThat(jdbc.queryForObject(
            "select count(*) from permissions where code like 'issue.%'",
            Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from object_type_rules where object_type='issue'",
            Integer.class
        )).isZero();
        assertThatThrownBy(() -> jdbc.update(
            """
                insert into project_work_items (
                    id, workspace_id, space_id, type_definition_id, type_version_id,
                    config_hash, item_number, display_key, title, field_values, status,
                    version, created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, 9999, 'FORGED-9999', 'forged',
                          '{}'::jsonb, 'active', 0, ?, now(), ?, now())
                """,
            deterministic("forged"),
            WORKSPACE_ID,
            UUID.randomUUID(),
            fixture.typeId(),
            fixture.versionId(),
            CONFIG_HASH,
            USER_ID,
            USER_ID
        )).hasMessageContaining("project_work_items");
        assertThat(jdbc.queryForObject(
            "select count(*) from project_work_items where display_key='FORGED-9999'",
            Integer.class
        )).isZero();
    }

    private void assertRecoverySetRegistered(JdbcTemplate jdbc) {
        for (String table : List.of(
            "project_work_items",
            "project_work_item_type_versions",
            "project_legacy_work_item_maps",
            "project_legacy_audit_snapshots",
            "project_legacy_removal_decisions",
            "audit_logs",
            "domain_events"
        )) {
            assertThat(jdbc.queryForObject(
                "select to_regclass(?) is not null",
                Boolean.class,
                table
            )).as(table).isTrue();
        }
    }

    private void assertInterruptedChangeRollsBack(JdbcTemplate restored)
        throws Exception {
        Integer before = restored.queryForObject(
            "select count(*) from project_work_items where workspace_id=?",
            Integer.class,
            WORKSPACE_ID
        );
        restored.execute((Connection connection) -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (var delete = connection.prepareStatement(
                "delete from project_work_items where workspace_id=?"
            )) {
                delete.setObject(1, WORKSPACE_ID);
                assertThat(delete.executeUpdate()).isEqualTo(before);
                connection.rollback();
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
        assertThat(restored.queryForObject(
            "select count(*) from project_work_items where workspace_id=?",
            Integer.class,
            WORKSPACE_ID
        )).isEqualTo(before);
    }

    private String canonicalDigest(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
            """
                select md5(coalesce(string_agg(
                    id::text || ':' || display_key || ':' || version::text,
                    ',' order by id
                ), ''))
                  from project_work_items
                 where workspace_id=?
                """,
            String.class,
            WORKSPACE_ID
        );
    }

    private void insertSyntheticActor(JdbcTemplate jdbc) {
        jdbc.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, 's21-readiness', 'not-used',
                          'S21 Synthetic Readiness', 'active',
                          '2026-07-29T00:00:00Z', '2026-07-29T00:00:00Z')
                """,
            USER_ID,
            WORKSPACE_ID
        );
    }

    private PGSimpleDataSource dataSource(
        PostgreSQLContainer<?> postgres,
        String database
    ) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(
            "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(5432),
                database
            )
        );
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private static UUID deterministic(String value) {
        return UUID.nameUUIDFromBytes(("s21:" + value).getBytes(UTF_8));
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private static long elapsedSeconds(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000_000);
    }

    private static long percentile95(List<Long> values) {
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1));
    }

    private record Fixture(
        String scenario,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    ) {
    }

    private record BudgetObservation(
        long indexedReadP95Ms,
        long aggregateReadP95Ms,
        long writeP95Ms,
        long bytesPerWorkItem
    ) {
    }
}
