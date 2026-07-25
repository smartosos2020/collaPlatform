package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityProbeAcknowledgement;
import com.colla.platform.modules.event.infrastructure.JdbcDomainEventDeliveryRepository;
import com.colla.platform.modules.event.infrastructure.JdbcDomainEventRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class CapacityEventProbeConcurrencyIntegrationTests {
    private static final String SECRET = "capacity-probe-test-secret-".repeat(3);
    private static final UUID WORKSPACE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcDomainEventDeliveryRepository deliveries;
    private static CapacityEventProbeService service;
    private static CurrentUser admin;
    private static CurrentUser otherWorkspaceAdmin;

    @BeforeAll
    static void setUpDatabase() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcDomainEventRepository events = new JdbcDomainEventRepository(jdbc, objectMapper);
        deliveries = new JdbcDomainEventDeliveryRepository(jdbc, objectMapper);
        TransactionalOutbox outbox = envelope -> events.appendEnvelope(envelope).eventId();
        service = service(outbox, events, 100);
        admin = admin(WORKSPACE_ID);
        otherWorkspaceAdmin = admin(OTHER_WORKSPACE_ID);
        insertWorkspace(WORKSPACE_ID, "capacity-concurrency-one");
        insertWorkspace(OTHER_WORKSPACE_ID, "capacity-concurrency-two");
    }

    @Test
    void concurrentSameKeyReturnsOneStableEvent() throws Exception {
        UUID runId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ProduceOutcome> first = executor.submit(
                () -> produceAfterBarrier(service, admin, runId, "aggregate", "same-key", ready, start)
            );
            Future<ProduceOutcome> second = executor.submit(
                () -> produceAfterBarrier(service, admin, runId, "aggregate", "same-key", ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ProduceOutcome firstResult = first.get(20, TimeUnit.SECONDS);
            ProduceOutcome secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult.status()).isEqualTo(HttpStatus.OK.value());
            assertThat(secondResult.status()).isEqualTo(HttpStatus.OK.value());
            assertThat(firstResult.acknowledgement()).isEqualTo(secondResult.acknowledgement());
        }

        assertThat(deliveries.countCapacityProbeEvents(WORKSPACE_ID, runId)).isEqualTo(1);
    }

    @Test
    void concurrentRunLimitCompetitionAdmitsOnlyOneEvent() throws Exception {
        UUID runId = UUID.randomUUID();
        CapacityEventProbeService limited = serviceWithLimit(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ProduceOutcome> first = executor.submit(
                () -> produceAfterBarrier(limited, admin, runId, "aggregate-a", "request-a", ready, start)
            );
            Future<ProduceOutcome> second = executor.submit(
                () -> produceAfterBarrier(limited, admin, runId, "aggregate-b", "request-b", ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                first.get(20, TimeUnit.SECONDS).status(),
                second.get(20, TimeUnit.SECONDS).status()
            )).containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.TOO_MANY_REQUESTS.value());
        }

        assertThat(deliveries.countCapacityProbeEvents(WORKSPACE_ID, runId)).isEqualTo(1);
    }

    @Test
    void sameRunAndRequestKeyRemainIsolatedAcrossWorkspaces() throws Exception {
        UUID runId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ProduceOutcome> first = executor.submit(
                () -> produceAfterBarrier(service, admin, runId, "aggregate", "shared-key", ready, start)
            );
            Future<ProduceOutcome> second = executor.submit(
                () -> produceAfterBarrier(
                    service,
                    otherWorkspaceAdmin,
                    runId,
                    "aggregate",
                    "shared-key",
                    ready,
                    start
                )
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ProduceOutcome firstResult = first.get(20, TimeUnit.SECONDS);
            ProduceOutcome secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult.status()).isEqualTo(HttpStatus.OK.value());
            assertThat(secondResult.status()).isEqualTo(HttpStatus.OK.value());
            assertThat(firstResult.acknowledgement().eventId())
                .isNotEqualTo(secondResult.acknowledgement().eventId());
        }

        assertThat(deliveries.countCapacityProbeEvents(WORKSPACE_ID, runId)).isEqualTo(1);
        assertThat(deliveries.countCapacityProbeEvents(OTHER_WORKSPACE_ID, runId)).isEqualTo(1);
    }

    @Test
    void concurrentRequestsForOneAggregateReceiveDistinctMonotonicSequences() throws Exception {
        UUID runId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ProduceOutcome> first = executor.submit(
                () -> produceAfterBarrier(service, admin, runId, "aggregate", "request-a", ready, start)
            );
            Future<ProduceOutcome> second = executor.submit(
                () -> produceAfterBarrier(service, admin, runId, "aggregate", "request-b", ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                first.get(20, TimeUnit.SECONDS).acknowledgement().sequence(),
                second.get(20, TimeUnit.SECONDS).acknowledgement().sequence()
            )).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Test
    void signedMembershipWatermarkCursorIsBoundToWorkspaceRunHandlerAndSchema() {
        UUID runId = UUID.randomUUID();
        produce(service, admin, runId, "aggregate", "request-a");
        produce(service, admin, runId, "aggregate", "request-b");

        String cursor = service.ledger(admin, runId, SECRET, null, 1).nextCursor();
        assertThat(cursor).isNotBlank();
        assertThat(service.ledger(admin, runId, SECRET, cursor, 1).entries()).hasSize(1);
        assertBadCursor(() -> service.ledger(admin, UUID.randomUUID(), SECRET, cursor, 1));
        assertBadCursor(() -> service.ledger(otherWorkspaceAdmin, runId, SECRET, cursor, 1));
        assertBadCursor(() -> service.decodeCursor(cursor, WORKSPACE_ID, runId, "other.handler"));

        int signatureOffset = cursor.indexOf('.') + 1;
        char replacement = cursor.charAt(signatureOffset) == 'A' ? 'B' : 'A';
        String tampered = cursor.substring(0, signatureOffset)
            + replacement
            + cursor.substring(signatureOffset + 1);
        assertBadCursor(() -> service.ledger(admin, runId, SECRET, tampered, 1));
    }

    private static CapacityEventProbeService serviceWithLimit(int maxEventsPerRun) {
        JdbcDomainEventRepository events = new JdbcDomainEventRepository(jdbc, new ObjectMapper());
        TransactionalOutbox outbox = envelope -> events.appendEnvelope(envelope).eventId();
        return service(outbox, events, maxEventsPerRun);
    }

    private static CapacityEventProbeService service(
        TransactionalOutbox outbox,
        JdbcDomainEventRepository events,
        int maxEventsPerRun
    ) {
        CapacityEventProbeProperties properties = new CapacityEventProbeProperties();
        properties.setEnabled(true);
        properties.setSecret(SECRET);
        properties.setMaxEventsPerRun(maxEventsPerRun);
        properties.setMaxPageSize(100);
        return new CapacityEventProbeService(properties, outbox, events, deliveries);
    }

    private static ProduceOutcome produceAfterBarrier(
        CapacityEventProbeService target,
        CurrentUser actor,
        UUID runId,
        String aggregateKey,
        String requestKey,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent capacity probe start timed out");
        }
        try {
            return new ProduceOutcome(
                produce(target, actor, runId, aggregateKey, requestKey),
                HttpStatus.OK.value()
            );
        } catch (ResponseStatusException exception) {
            return new ProduceOutcome(null, exception.getStatusCode().value());
        }
    }

    private static CapacityProbeAcknowledgement produce(
        CapacityEventProbeService target,
        CurrentUser actor,
        UUID runId,
        String aggregateKey,
        String requestKey
    ) {
        return Objects.requireNonNull(transactions.execute(status ->
            target.produce(actor, runId, SECRET, aggregateKey, requestKey)
        ));
    }

    private static void assertBadCursor(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error ->
                assertThat(((ResponseStatusException) error).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    private static CurrentUser admin(UUID workspaceId) {
        return new CurrentUser(
            UUID.randomUUID(),
            workspaceId,
            null,
            "admin",
            "Administrator",
            Set.of("admin"),
            Set.of("admin.access")
        );
    }

    private static void insertWorkspace(UUID id, String slug) {
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

    private record ProduceOutcome(CapacityProbeAcknowledgement acknowledgement, int status) {
    }
}
