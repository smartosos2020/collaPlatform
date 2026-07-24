package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandler.Subscription;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
@Import(ReliableDomainEventWorkerIntegrationTests.HandlerConfiguration.class)
class ReliableDomainEventWorkerIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private TransactionalOutbox outbox;
    @Autowired private DomainEventDeliveryCoordinator coordinator;
    @Autowired private DomainEventDeliveryProperties deliveryProperties;
    @Autowired private DomainEventHandlerRegistry registry;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordingHandler handler;

    private ReliableDomainEventWorker workerA;
    private ReliableDomainEventWorker workerB;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from realtime_signals");
        jdbcTemplate.update("delete from domain_event_delivery_replays");
        jdbcTemplate.update("delete from domain_event_handler_receipts");
        jdbcTemplate.update("delete from domain_event_handler_deliveries");
        jdbcTemplate.update("delete from domain_events");
        handler.reset();
    }

    @AfterEach
    void stopWorkers() {
        if (workerA != null) workerA.stop();
        if (workerB != null) workerB.stop();
    }

    @Test
    void twoRealWorkersDistributeDeliveriesWithoutDuplicateReceipts() throws Exception {
        for (int index = 0; index < 12; index += 1) {
            append("distribution-" + index);
        }
        workerA = worker("worker-a", properties(1, 0, 1));
        workerB = worker("worker-b", properties(1, 0, 1));
        workerA.start();
        workerB.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        try (var pollers = Executors.newFixedThreadPool(2)) {
            while (processed() < 12 && System.nanoTime() < deadline) {
                var a = pollers.submit(workerA::pollOnce);
                var b = pollers.submit(workerB::pollOnce);
                a.get(2, TimeUnit.SECONDS);
                b.get(2, TimeUnit.SECONDS);
                Thread.sleep(20);
            }
        }

        assertThat(processed()).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_event_handler_receipts", Long.class
        )).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject(
            """
                select count(distinct worker_id)
                from domain_event_handler_deliveries
                where status = 'processed'
                """,
            Long.class
        )).isEqualTo(2);
        assertThat(handler.eventIds).hasSize(12);
    }

    @Test
    void boundedQueueStopsClaimingWhenExecutionCapacityIsFull() throws Exception {
        for (int index = 0; index < 5; index += 1) {
            append("backpressure-" + index);
        }
        handler.block();
        workerA = worker("worker-a", properties(1, 1, 10));
        workerA.start();

        workerA.pollOnce();
        assertThat(handler.started.await(3, TimeUnit.SECONDS)).isTrue();
        workerA.pollOnce();
        awaitProcessing(2);
        assertThat(coordinator.stats(Instant.now()).processing()).isEqualTo(2);
        assertThat(coordinator.stats(Instant.now()).pending()).isEqualTo(3);

        workerA.pollOnce();
        assertThat(coordinator.stats(Instant.now()).processing()).isEqualTo(2);
        handler.release();
        awaitProcessed(2);
    }

    @Test
    void stoppedWorkerDoesNotClaimNewDeliveries() {
        workerA = worker("worker-a", properties(1, 0, 1));
        workerA.start();
        workerA.stop();
        append("after-drain");

        workerA.pollOnce();

        assertThat(coordinator.stats(Instant.now()).pending()).isEqualTo(1);
        assertThat(workerA.acceptingClaims()).isFalse();
        assertThat(workerA.ready()).isFalse();
        assertThat(workerA.readinessDetail()).isEqualTo("stopped");
    }

    private ReliableDomainEventWorker worker(String id, DomainEventWorkerProperties properties) {
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("worker");
        runtime.setInstanceId(id);
        return new ReliableDomainEventWorker(
            coordinator, registry, properties, deliveryProperties, runtime, new SimpleMeterRegistry()
        );
    }

    private DomainEventWorkerProperties properties(int concurrency, int queueCapacity, int claimBatch) {
        DomainEventWorkerProperties properties = new DomainEventWorkerProperties();
        properties.setEnabled(true);
        properties.setConcurrency(concurrency);
        properties.setQueueCapacity(queueCapacity);
        properties.setClaimBatch(claimBatch);
        properties.setConnectionBudget(concurrency + 2);
        properties.setHeartbeatInterval(Duration.ofSeconds(5));
        properties.setShutdownGrace(Duration.ofSeconds(3));
        return properties;
    }

    private void append(String value) {
        UUID eventId = UUID.randomUUID();
        outbox.append(new EventEnvelope(
            eventId, WORKSPACE_ID, "project.updated", 1, "project", UUID.randomUUID(), null,
            "s03-m3:" + eventId, eventId, null, Instant.now(), Map.of("value", value)
        ));
    }

    private long processed() {
        return jdbcTemplate.queryForObject(
            "select count(*) from domain_event_handler_deliveries where status = 'processed'",
            Long.class
        );
    }

    private void awaitProcessed(long expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (processed() < expected && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(processed()).isEqualTo(expected);
    }

    private void awaitProcessing(long expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (coordinator.stats(Instant.now()).processing() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(coordinator.stats(Instant.now()).processing()).isEqualTo(expected);
    }

    @TestConfiguration
    static class HandlerConfiguration {
        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    static class RecordingHandler implements DomainEventHandler {
        private final Set<UUID> eventIds = ConcurrentHashMap.newKeySet();
        private volatile CountDownLatch release = new CountDownLatch(0);
        private volatile CountDownLatch started = new CountDownLatch(1);

        @Override
        public Descriptor descriptor() {
            return new Descriptor(
                "test.worker-distribution", 1,
                Set.of(new Subscription("project.updated", 1)), false
            );
        }

        @Override
        public void handle(EventMessage event) {
            eventIds.add(event.eventId());
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DomainEventTransientFailureException("handler interrupted");
            }
        }

        void reset() {
            eventIds.clear();
            release = new CountDownLatch(0);
            started = new CountDownLatch(1);
        }

        void block() {
            release = new CountDownLatch(1);
            started = new CountDownLatch(1);
        }

        void release() {
            release.countDown();
        }
    }
}
