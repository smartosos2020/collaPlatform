package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandler.Subscription;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.colla.platform.shared.auth.CurrentUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "colla.events.worker.enabled=false")
@Import(DomainEventFleetCloseoutIntegrationTests.HandlerConfiguration.class)
class DomainEventFleetCloseoutIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BURST_TYPE = "stage.s03.named-burst";
    private static final String ISOLATION_TYPE = "stage.s03.handler-isolation";

    @Autowired private TransactionalOutbox outbox;
    @Autowired private DomainEventDeliveryCoordinator coordinator;
    @Autowired private DomainEventDeliveryProperties deliveryProperties;
    @Autowired private DomainEventHandlerRegistry registry;
    @Autowired private DomainEventMaintenanceService maintenanceService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordingHandler recordingHandler;
    @Autowired private PoisonHandler poisonHandler;

    private final List<ReliableDomainEventWorker> workers = new ArrayList<>();

    @BeforeEach
    void clean() {
        cleanLedger();
        recordingHandler.reset();
        poisonHandler.reset();
    }

    @AfterEach
    void stopWorkers() {
        workers.forEach(ReliableDomainEventWorker::stop);
        workers.clear();
    }

    @Test
    void namedBurstScaleOutImprovesRecoveryAndUsesBothOwners() throws Exception {
        recordingHandler.setDelayMillis(40);
        long singleWorkerMillis = runNamedBurst("s03-burst-single", 24, 1);

        stopWorkers();
        cleanLedger();
        recordingHandler.reset();
        recordingHandler.setDelayMillis(40);
        long dualWorkerMillis = runNamedBurst("s03-burst-dual", 24, 2);

        assertThat(dualWorkerMillis)
            .as("two workers must recover the named burst at least 20%% faster")
            .isLessThan(singleWorkerMillis * 4 / 5);
        assertThat(jdbcTemplate.queryForObject(
            "select count(distinct worker_id) from domain_event_handler_deliveries where status = 'processed'",
            Long.class
        )).isEqualTo(2);
        assertThat(coordinator.stats(Instant.now()).pending()).isZero();
        assertThat(coordinator.stats(Instant.now()).processing()).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) - count(distinct event_id) from domain_event_handler_receipts",
            Long.class
        )).isZero();

        System.out.printf(
            "S03_M5_NAMED_BURST events=24 single_ms=%d dual_ms=%d improvement_pct=%d pending=0%n",
            singleWorkerMillis,
            dualWorkerMillis,
            Math.max(0, 100 - (dualWorkerMillis * 100 / Math.max(1, singleWorkerMillis)))
        );
    }

    @Test
    void poisonHandlerIsIsolatedAndReplayDoesNotRepeatSuccessfulHandler() throws Exception {
        UUID eventId = append(ISOLATION_TYPE, "poison-isolation");
        ReliableDomainEventWorker workerA = startWorker("s03-isolation-a");
        ReliableDomainEventWorker workerB = startWorker("s03-isolation-b");

        pollUntilTerminal(List.of(workerA, workerB), 2, Duration.ofSeconds(15));

        assertThat(status("test.s03-recorder")).isEqualTo("processed");
        assertThat(status("test.s03-poison")).isEqualTo("dead_letter");
        assertThat(recordingHandler.calls(eventId)).isEqualTo(1);
        assertThat(poisonHandler.calls(eventId)).isEqualTo(1);

        UUID deadDelivery = jdbcTemplate.queryForObject(
            "select id from domain_event_handler_deliveries where handler_key = 'test.s03-poison'",
            UUID.class
        );
        poisonHandler.repair();
        maintenanceService.replay(admin(), deadDelivery, "S03 controlled replay after handler repair");

        pollUntilTerminal(List.of(workerA, workerB), 2, Duration.ofSeconds(15));

        assertThat(status("test.s03-recorder")).isEqualTo("processed");
        assertThat(status("test.s03-poison")).isEqualTo("processed");
        assertThat(recordingHandler.calls(eventId)).isEqualTo(1);
        assertThat(poisonHandler.calls(eventId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_event_handler_receipts where event_id = ?",
            Long.class,
            eventId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_event_delivery_replays where delivery_id = ? and action = 'replay'",
            Long.class,
            deadDelivery
        )).isEqualTo(1);
    }

    @Test
    void rollingScaleDownFallsBackToOneWorkerWithoutChangingFacts() throws Exception {
        ReliableDomainEventWorker workerA = startWorker("s03-roll-a");
        ReliableDomainEventWorker workerB = startWorker("s03-roll-b");
        for (int index = 0; index < 12; index += 1) {
            append(BURST_TYPE, "before-scale-down-" + index);
        }
        pollUntilTerminal(List.of(workerA, workerB), 12, Duration.ofSeconds(15));

        workerA.stop();
        workers.remove(workerA);
        for (int index = 0; index < 8; index += 1) {
            append(BURST_TYPE, "single-worker-fallback-" + index);
        }
        pollUntilTerminal(List.of(workerB), 20, Duration.ofSeconds(15));

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_event_handler_receipts",
            Long.class
        )).isEqualTo(20);
        assertThat(jdbcTemplate.queryForObject(
            """
                select count(*)
                from domain_event_handler_deliveries
                where status <> 'processed' or worker_id is null
                """,
            Long.class
        )).isZero();
        assertThat(coordinator.stats(Instant.now()).pending()).isZero();
        assertThat(workerA.acceptingClaims()).isFalse();
        assertThat(workerB.ready()).isTrue();
    }

    private long runNamedBurst(String name, int count, int workerCount) throws Exception {
        for (int index = 0; index < count; index += 1) {
            append(BURST_TYPE, name + "-" + index);
        }
        List<ReliableDomainEventWorker> active = new ArrayList<>();
        for (int index = 0; index < workerCount; index += 1) {
            active.add(startWorker(name + "-" + (index + 1)));
        }
        long started = System.nanoTime();
        pollUntilTerminal(active, count, Duration.ofSeconds(20));
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private ReliableDomainEventWorker startWorker(String id) {
        DomainEventWorkerProperties properties = new DomainEventWorkerProperties();
        properties.setEnabled(true);
        properties.setConcurrency(1);
        properties.setQueueCapacity(0);
        properties.setClaimBatch(1);
        properties.setConnectionBudget(3);
        properties.setExpectedInstances(2);
        properties.setPollInterval(Duration.ofMillis(100));
        properties.setRecoveryInterval(Duration.ofSeconds(1));
        properties.setHeartbeatInterval(Duration.ofSeconds(5));
        properties.setShutdownGrace(Duration.ofSeconds(3));
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("worker");
        runtime.setInstanceId(id);
        ReliableDomainEventWorker worker = new ReliableDomainEventWorker(
            coordinator,
            registry,
            properties,
            deliveryProperties,
            runtime,
            new SimpleMeterRegistry()
        );
        worker.start();
        workers.add(worker);
        return worker;
    }

    private void pollUntilTerminal(
        List<ReliableDomainEventWorker> active,
        long expected,
        Duration timeout
    ) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (var pollers = Executors.newFixedThreadPool(active.size())) {
            while (terminalCount() < expected && System.nanoTime() < deadline) {
                var calls = active.stream().map(worker -> pollers.submit(worker::pollOnce)).toList();
                for (var call : calls) {
                    call.get(3, TimeUnit.SECONDS);
                }
                Thread.sleep(10);
            }
        }
        assertThat(terminalCount()).isEqualTo(expected);
    }

    private long terminalCount() {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from domain_event_handler_deliveries
                where status in ('processed', 'dead_letter', 'abandoned', 'unsupported')
                """,
            Long.class
        );
    }

    private String status(String handlerKey) {
        return jdbcTemplate.queryForObject(
            "select status from domain_event_handler_deliveries where handler_key = ?",
            String.class,
            handlerKey
        );
    }

    private UUID append(String type, String value) {
        UUID eventId = UUID.randomUUID();
        return outbox.append(new EventEnvelope(
            eventId,
            WORKSPACE_ID,
            type,
            1,
            "stage_closeout",
            UUID.randomUUID(),
            null,
            "s03-m5:" + eventId,
            eventId,
            null,
            Instant.now(),
            Map.of("value", value)
        ));
    }

    private CurrentUser admin() {
        return new CurrentUser(
            UUID.randomUUID(),
            WORKSPACE_ID,
            null,
            "admin",
            "Administrator",
            Set.of("admin"),
            Set.of("admin.access")
        );
    }

    private void cleanLedger() {
        jdbcTemplate.update("delete from realtime_signals");
        jdbcTemplate.update("delete from domain_event_delivery_replays");
        jdbcTemplate.update("delete from domain_event_handler_receipts");
        jdbcTemplate.update("delete from domain_event_handler_deliveries");
        jdbcTemplate.update("delete from domain_events");
    }

    @TestConfiguration
    static class HandlerConfiguration {
        @Bean
        RecordingHandler s03RecordingHandler() {
            return new RecordingHandler();
        }

        @Bean
        PoisonHandler s03PoisonHandler() {
            return new PoisonHandler();
        }
    }

    static class RecordingHandler implements DomainEventHandler {
        private final Map<UUID, AtomicInteger> calls = new ConcurrentHashMap<>();
        private final AtomicLong delayMillis = new AtomicLong();

        @Override
        public Descriptor descriptor() {
            return new Descriptor(
                "test.s03-recorder",
                1,
                Set.of(new Subscription(BURST_TYPE, 1), new Subscription(ISOLATION_TYPE, 1)),
                false
            );
        }

        @Override
        public void handle(EventMessage event) {
            calls.computeIfAbsent(event.eventId(), ignored -> new AtomicInteger()).incrementAndGet();
            if (delayMillis.get() > 0) {
                try {
                    Thread.sleep(delayMillis.get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new DomainEventTransientFailureException("named burst handler interrupted");
                }
            }
        }

        int calls(UUID eventId) {
            return calls.getOrDefault(eventId, new AtomicInteger()).get();
        }

        void setDelayMillis(long value) {
            delayMillis.set(value);
        }

        void reset() {
            calls.clear();
            delayMillis.set(0);
        }
    }

    static class PoisonHandler implements DomainEventHandler {
        private final Map<UUID, AtomicInteger> calls = new ConcurrentHashMap<>();
        private volatile boolean repaired;

        @Override
        public Descriptor descriptor() {
            return new Descriptor(
                "test.s03-poison",
                1,
                Set.of(new Subscription(ISOLATION_TYPE, 1)),
                false
            );
        }

        @Override
        public void handle(EventMessage event) {
            calls.computeIfAbsent(event.eventId(), ignored -> new AtomicInteger()).incrementAndGet();
            if (!repaired) {
                throw new DomainEventPermanentFailureException("S03 poison handler fixture");
            }
        }

        int calls(UUID eventId) {
            return calls.getOrDefault(eventId, new AtomicInteger()).get();
        }

        void repair() {
            repaired = true;
        }

        void reset() {
            calls.clear();
            repaired = false;
        }
    }
}
