package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandler.Subscription;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.colla.platform.modules.event.infrastructure.DomainEventDeliveryRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Import(DomainEventDeliveryLeaseIntegrationTests.HandlerConfiguration.class)
class DomainEventDeliveryLeaseIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TransactionalOutbox outbox;

    @Autowired
    private DomainEventDeliveryCoordinator coordinator;

    @Autowired
    private DomainEventDeliveryRepository deliveryRepository;

    @Autowired
    private DomainEventMaintenanceService maintenanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanEventLedger() {
        jdbcTemplate.update("delete from realtime_signals");
        jdbcTemplate.update("delete from domain_event_delivery_replays");
        jdbcTemplate.update("delete from domain_event_handler_receipts");
        jdbcTemplate.update("delete from domain_event_handler_deliveries");
        jdbcTemplate.update("delete from domain_events");
    }

    @Test
    void concurrentWorkersCannotClaimTheSameDelivery() throws Exception {
        append(UUID.randomUUID(), "concurrent");
        Instant now = Instant.now();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var workerA = executor.submit(() -> {
                start.await();
                return coordinator.claim("worker-a", 1, now);
            });
            var workerB = executor.submit(() -> {
                start.await();
                return coordinator.claim("worker-b", 1, now);
            });
            start.countDown();
            var claimedA = workerA.get(10, TimeUnit.SECONDS);
            var claimedB = workerB.get(10, TimeUnit.SECONDS);

            assertThat(claimedA.size() + claimedB.size()).isEqualTo(1);
            assertThat(java.util.stream.Stream.concat(claimedA.stream(), claimedB.stream())
                .map(delivery -> delivery.id())
                .distinct()
                .count()).isEqualTo(1);
        }
    }

    @Test
    void claimsExclusivelyRecoversExpiredLeaseAndRejectsStaleFence() {
        UUID aggregateId = UUID.randomUUID();
        append(aggregateId, "first");
        append(aggregateId, "second");
        Instant start = Instant.parse("2026-07-24T00:00:00Z");

        var workerA = coordinator.claim("worker-a", 10, start);
        var workerBWhileOwned = coordinator.claim("worker-b", 10, start.plusSeconds(1));
        assertThat(workerA).hasSize(1);
        assertThat(workerBWhileOwned).isEmpty();
        assertThat(workerA.getFirst().event().aggregateSequence()).isEqualTo(1);
        assertThat(workerA.getFirst().fencingToken()).isEqualTo(1);

        assertThat(coordinator.recoverExpired(start.plusSeconds(31))).isEqualTo(1);
        var workerB = coordinator.claim("worker-b", 10, start.plusSeconds(31));
        assertThat(workerB).hasSize(1);
        assertThat(workerB.getFirst().id()).isEqualTo(workerA.getFirst().id());
        assertThat(workerB.getFirst().fencingToken()).isEqualTo(2);

        assertThat(coordinator.complete(workerA.getFirst(), Map.of("owner", "stale"), start.plusSeconds(32)).accepted())
            .isFalse();
        assertThat(coordinator.heartbeat(workerB.getFirst(), start.plusSeconds(32))).isTrue();
        assertThat(coordinator.complete(workerB.getFirst(), Map.of("owner", "worker-b"), start.plusSeconds(33)).accepted())
            .isTrue();

        var second = coordinator.claim("worker-b", 10, start.plusSeconds(34));
        assertThat(second).singleElement().satisfies(delivery ->
            assertThat(delivery.event().aggregateSequence()).isEqualTo(2)
        );
    }

    @Test
    void deadLettersReplaysAndAbandonsWithAuditHistory() {
        UUID eventId = append(UUID.randomUUID(), "poison");
        Instant start = Instant.parse("2026-07-24T01:00:00Z");
        var delivery = coordinator.claim("worker-a", 1, start).getFirst();

        assertThat(coordinator.fail(
            delivery,
            new DomainEventPermanentFailureException("invalid target"),
            start.plusSeconds(1)
        )).isTrue();
        assertThat(deliveryRepository.findDeadLetters(WORKSPACE_ID, "test.project-index", 10))
            .singleElement()
            .satisfies(deadLetter -> assertThat(deadLetter.eventId()).isEqualTo(eventId));

        CurrentUser member = new CurrentUser(
            UUID.randomUUID(), WORKSPACE_ID, null, "member", "Member", Set.of("member"), Set.of()
        );
        assertThatThrownBy(() -> maintenanceService.inspect(member, null, 10))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");

        CurrentUser admin = new CurrentUser(
            UUID.randomUUID(), WORKSPACE_ID, null, "admin", "Administrator", Set.of("admin"), Set.of("admin.access")
        );
        Instant replayClock = Instant.now();
        maintenanceService.replay(admin, delivery.id(), "Retry after fixing target mapping");
        var replayed = coordinator.claim("worker-b", 1, replayClock.plusSeconds(1)).getFirst();
        assertThat(replayed.attemptCount()).isEqualTo(2);
        assertThat(coordinator.fail(
            replayed,
            new DomainEventPermanentFailureException("still invalid"),
            replayClock.plusSeconds(2)
        )).isTrue();
        maintenanceService.abandon(admin, delivery.id(), "Confirmed obsolete target delivery");

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_event_delivery_replays where delivery_id = ?",
            Long.class,
            delivery.id()
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            """
                select count(*) from audit_logs
                where workspace_id = ? and target_id = ?
                  and action in ('event_delivery.replayed', 'event_delivery.abandoned')
                """,
            Long.class,
            WORKSPACE_ID,
            delivery.id()
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select status from domain_event_handler_deliveries where id = ?",
            String.class,
            delivery.id()
        )).isEqualTo("abandoned");
    }

    private UUID append(UUID aggregateId, String value) {
        UUID eventId = UUID.randomUUID();
        return outbox.append(new EventEnvelope(
            eventId,
            WORKSPACE_ID,
            "project.updated",
            1,
            "project",
            aggregateId,
            null,
            "s03-m2:" + eventId,
            eventId,
            null,
            Instant.now(),
            Map.of("value", value)
        ));
    }

    @TestConfiguration
    static class HandlerConfiguration {
        @Bean
        DomainEventHandler leaseTestHandler() {
            return new DomainEventHandler() {
                @Override
                public Descriptor descriptor() {
                    return new Descriptor(
                        "test.project-index",
                        1,
                        Set.of(new Subscription("project.updated", 1)),
                        true
                    );
                }

                @Override
                public void handle(EventMessage event) {
                }
            };
        }
    }
}
