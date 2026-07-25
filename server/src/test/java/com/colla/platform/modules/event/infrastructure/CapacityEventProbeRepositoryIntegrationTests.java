package com.colla.platform.modules.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerSlice;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class CapacityEventProbeRepositoryIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID RUN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void isolatesProbeFactsSplitsMissingStagesAndKeepsAStableMembershipWatermark() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
            );
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            JdbcDomainEventDeliveryRepository repository = new JdbcDomainEventDeliveryRepository(
                jdbc,
                new ObjectMapper()
            );
            insertWorkspace(jdbc, WORKSPACE_ID, "capacity-probe-one");
            insertWorkspace(jdbc, OTHER_WORKSPACE_ID, "capacity-probe-two");

            Instant firstAt = Instant.parse("2026-07-25T01:00:00Z");
            Instant secondAt = firstAt.plusSeconds(1);
            Instant thirdAt = firstAt.plusSeconds(2);
            Instant fourthAt = firstAt.plusSeconds(3);
            UUID first = insertEvent(jdbc, WORKSPACE_ID, RUN_ID, "capacity_probe", firstAt);
            UUID second = insertEvent(jdbc, WORKSPACE_ID, RUN_ID, "capacity_probe", secondAt);
            UUID third = insertEvent(jdbc, WORKSPACE_ID, RUN_ID, "capacity_probe", thirdAt);
            UUID fourth = insertEvent(jdbc, WORKSPACE_ID, RUN_ID, "capacity_probe", fourthAt);
            insertEvent(jdbc, WORKSPACE_ID, RUN_ID, "project", firstAt.plusMillis(500));
            insertEvent(jdbc, OTHER_WORKSPACE_ID, RUN_ID, "capacity_probe", firstAt.plusMillis(750));
            insertProcessedDelivery(jdbc, first, firstAt, true, true);
            insertProcessedDelivery(jdbc, third, thirdAt, false, true);
            insertProcessedDelivery(jdbc, fourth, fourthAt, true, false);

            var summary = repository.capacityRunSummary(
                WORKSPACE_ID,
                RUN_ID,
                "realtime.signal",
                fourthAt.plusSeconds(10)
            );
            assertThat(summary.total()).isEqualTo(4);
            assertThat(summary.backlog()).isEqualTo(3);
            assertThat(summary.missing()).isEqualTo(1);
            assertThat(summary.missingDelivery()).isEqualTo(1);
            assertThat(summary.missingReceipt()).isEqualTo(1);
            assertThat(summary.missingSideEffect()).isEqualTo(1);
            assertThat(summary.oldestPendingAgeSeconds()).isEqualTo(12);
            assertThat(repository.countCapacityProbeEvents(WORKSPACE_ID, RUN_ID)).isEqualTo(4);

            CapacityLedgerSlice firstPage = repository.capacityRunLedger(
                WORKSPACE_ID,
                RUN_ID,
                "realtime.signal",
                null,
                3
            );
            assertThat(firstPage.entries()).extracting(entry -> entry.eventId())
                .containsExactly(first, second, third);
            assertThat(firstPage.entries().get(0).receiptRecorded()).isTrue();
            assertThat(firstPage.entries().get(0).sideEffectId()).isNotNull();
            assertThat(firstPage.entries().get(1).deliveryStatus()).isEqualTo("missing");
            assertThat(firstPage.entries().get(2).deliveryStatus()).isEqualTo("processed");
            assertThat(firstPage.entries().get(2).receiptRecorded()).isFalse();
            assertThat(firstPage.entries().get(2).sideEffectId()).isNotNull();
            assertThat(firstPage.nextCursor()).isNotNull();

            UUID insertedAfterSnapshot = insertEvent(
                jdbc,
                WORKSPACE_ID,
                RUN_ID,
                "capacity_probe",
                fourthAt.plusSeconds(1)
            );
            CapacityLedgerSlice secondPage = repository.capacityRunLedger(
                WORKSPACE_ID,
                RUN_ID,
                "realtime.signal",
                firstPage.nextCursor(),
                3
            );
            assertThat(secondPage.entries()).extracting(entry -> entry.eventId()).containsExactly(fourth);
            assertThat(secondPage.entries().get(0).deliveryStatus()).isEqualTo("processed");
            assertThat(secondPage.entries().get(0).receiptRecorded()).isTrue();
            assertThat(secondPage.entries().get(0).sideEffectId()).isNull();
            assertThat(secondPage.nextCursor()).isNull();
            assertThat(secondPage.entries()).extracting(entry -> entry.eventId())
                .doesNotContain(insertedAfterSnapshot);
        }
    }

    private static void insertWorkspace(JdbcTemplate jdbc, UUID id, String slug) {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        jdbc.update(
            """
                insert into workspaces (id, name, slug, status, created_at, updated_at)
                values (?, ?, ?, 'active', ?, ?)
                """,
            id,
            slug,
            slug,
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private static UUID insertEvent(
        JdbcTemplate jdbc,
        UUID workspaceId,
        UUID runId,
        String aggregateType,
        Instant createdAt
    ) {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
            """
                insert into domain_events (
                    id, workspace_id, event_type, event_version, aggregate_type, aggregate_id,
                    aggregate_sequence, correlation_id, occurred_at, payload, status, created_at
                ) values (?, ?, 'realtime.signal.requested', 1, ?, ?, 1, ?, ?, '{}'::jsonb, 'pending', ?)
                """,
            eventId,
            workspaceId,
            aggregateType,
            UUID.randomUUID(),
            runId,
            Timestamp.from(createdAt),
            Timestamp.from(createdAt)
        );
        return eventId;
    }

    private static void insertProcessedDelivery(
        JdbcTemplate jdbc,
        UUID eventId,
        Instant createdAt,
        boolean withReceipt,
        boolean withSideEffect
    ) {
        UUID deliveryId = UUID.randomUUID();
        UUID workspaceId = WORKSPACE_ID;
        jdbc.update(
            """
                insert into domain_event_handler_deliveries (
                    id, workspace_id, event_id, handler_key, handler_version, status,
                    attempt_count, processed_at, created_at, updated_at
                ) values (?, ?, ?, 'realtime.signal', 1, 'processed', 1, ?, ?, ?)
                """,
            deliveryId,
            workspaceId,
            eventId,
            Timestamp.from(createdAt.plusMillis(10)),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt.plusMillis(10))
        );
        if (withReceipt) {
            jdbc.update(
                """
                    insert into domain_event_handler_receipts (
                        id, workspace_id, event_id, delivery_id, handler_key, handler_version, result, completed_at
                    ) values (?, ?, ?, ?, 'realtime.signal', 1, '{}'::jsonb, ?)
                    """,
                UUID.randomUUID(),
                workspaceId,
                eventId,
                deliveryId,
                Timestamp.from(createdAt.plusMillis(10))
            );
        }
        if (withSideEffect) {
            UUID objectId = UUID.randomUUID();
            jdbc.update(
                """
                    insert into realtime_signals (
                        id, workspace_id, source_event_id, signal_type, object_type, object_id,
                        source_version, calibration_path, envelope_version, signal_version,
                        audience_type, sequence_scope, sequence_key, sequence_value, occurred_at,
                        correlation_id, payload, created_at
                    ) values (
                        ?, ?, ?, 'capacity.probe', 'capacity_probe', ?, 1, '/api/health', 1, 1,
                        'workspace', 'object', ?, 1, ?, ?, '{}'::jsonb, ?
                    )
                    """,
                UUID.randomUUID(),
                workspaceId,
                eventId,
                objectId,
                "capacity_probe:" + objectId,
                Timestamp.from(createdAt.plusMillis(10)),
                eventId,
                Timestamp.from(createdAt.plusMillis(10))
            );
        }
    }
}
