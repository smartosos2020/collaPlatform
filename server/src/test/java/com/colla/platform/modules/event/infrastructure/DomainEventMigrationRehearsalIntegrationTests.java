package com.colla.platform.modules.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class DomainEventMigrationRehearsalIntegrationTests {
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void freshDatabaseReachesLatestEventSchemaWithRequiredIndexesAndConstraints() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway flyway = flyway(postgres);
            assertThat(flyway.migrate().migrationsExecuted).isGreaterThan(0);

            try (Connection connection = connection(postgres)) {
                assertThat(appliedVersion(connection)).isEqualTo(69);
                assertThat(count(connection, "select count(*) from pg_indexes where indexname = 'idx_event_handler_deliveries_claim'"))
                    .isEqualTo(1);
                assertThat(count(connection, "select count(*) from pg_indexes where indexname = 'idx_realtime_signals_pending'"))
                    .isEqualTo(1);
                assertThat(count(
                    connection,
                    "select count(*) from pg_constraint where conname = 'uk_domain_events_aggregate_sequence'"
                )).isEqualTo(1);
                assertThat(count(
                    connection,
                    "select count(*) from pg_constraint where conname = 'ck_event_handler_deliveries_processing_owner'"
                )).isEqualTo(1);
            }
        }
    }

    @Test
    void v066UpgradePreservesHistoricalEventsAndConcurrentMigratorsConverge() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(MIGRATIONS)
                .target("66")
                .load()
                .migrate();

            UUID eventId = UUID.randomUUID();
            UUID aggregateId = UUID.randomUUID();
            try (Connection connection = connection(postgres);
                 var statement = connection.prepareStatement(
                     """
                         insert into domain_events (
                             id, workspace_id, event_type, aggregate_type, aggregate_id,
                             payload, status, created_at
                         ) values (?, ?, ?, ?, ?, '{}'::jsonb, 'pending', ?)
                         """
                 )) {
                statement.setObject(1, eventId);
                statement.setObject(2, WORKSPACE_ID);
                statement.setString(3, "project.updated");
                statement.setString(4, "project");
                statement.setObject(5, aggregateId);
                statement.setTimestamp(6, Timestamp.from(Instant.parse("2026-07-24T00:00:00Z")));
                statement.executeUpdate();
            }

            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> migrateAfter(start, postgres));
                var second = executor.submit(() -> migrateAfter(start, postgres));
                start.countDown();
                first.get(60, TimeUnit.SECONDS);
                second.get(60, TimeUnit.SECONDS);
            }

            try (Connection connection = connection(postgres);
                 var statement = connection.prepareStatement(
                     """
                         select event_version, aggregate_sequence, correlation_id, occurred_at
                         from domain_events
                         where id = ?
                         """
                 )) {
                statement.setObject(1, eventId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("event_version")).isEqualTo(1);
                    assertThat(result.getLong("aggregate_sequence")).isEqualTo(1);
                    assertThat(result.getObject("correlation_id", UUID.class)).isEqualTo(eventId);
                    assertThat(result.getObject("occurred_at")).isNotNull();
                }
                assertThat(appliedVersion(connection)).isEqualTo(69);
                assertThat(count(
                    connection,
                    "select count(*) from flyway_schema_history where success = true and version in ('067', '068', '069')"
                )).isEqualTo(3);
            }
        }
    }

    private static int migrateAfter(CountDownLatch start, PostgreSQLContainer<?> postgres) throws Exception {
        start.await();
        return flyway(postgres).migrate().migrationsExecuted;
    }

    private static Flyway flyway(PostgreSQLContainer<?> postgres) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(MIGRATIONS)
            .load();
    }

    private static Connection connection(PostgreSQLContainer<?> postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static int appliedVersion(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(
                 "select max(version::integer) from flyway_schema_history where success = true"
             )) {
            result.next();
            return result.getInt(1);
        }
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
