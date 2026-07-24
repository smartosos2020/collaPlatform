package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandler.Subscription;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(DomainEventEnvelopeIntegrationTests.HandlerConfiguration.class)
class DomainEventEnvelopeIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TransactionalOutbox outbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DomainEventReceiptStore receiptStore;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void persistsEnvelopeSequenceDeliveryAndStableIdempotencyResult() {
        UUID aggregateId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        String idempotencyKey = "s03-envelope:" + aggregateId;

        UUID persisted = outbox.append(envelope(
            firstEventId,
            aggregateId,
            idempotencyKey,
            correlationId,
            null,
            "first"
        ));
        UUID duplicate = outbox.append(envelope(
            UUID.randomUUID(),
            aggregateId,
            idempotencyKey,
            UUID.randomUUID(),
            firstEventId,
            "duplicate"
        ));
        UUID second = outbox.append(envelope(
            UUID.randomUUID(),
            aggregateId,
            idempotencyKey + ":second",
            correlationId,
            firstEventId,
            "second"
        ));

        assertThat(persisted).isEqualTo(firstEventId);
        assertThat(duplicate).isEqualTo(firstEventId);
        assertThat(second).isNotEqualTo(firstEventId);
        assertThat(jdbcTemplate.queryForObject(
            "select event_version from domain_events where id = ?",
            Integer.class,
            firstEventId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select correlation_id from domain_events where id = ?",
            UUID.class,
            firstEventId
        )).isEqualTo(correlationId);
        assertThat(jdbcTemplate.queryForList(
            """
                select aggregate_sequence
                from domain_events
                where workspace_id = ? and aggregate_type = 'project' and aggregate_id = ?
                order by aggregate_sequence
                """,
            Long.class,
            WORKSPACE_ID,
            aggregateId
        )).containsExactly(1L, 2L);
        assertThat(jdbcTemplate.queryForObject(
            """
                select count(*)
                from domain_event_handler_deliveries
                where event_id = ? and handler_key = 'test.project-index' and handler_version = 1
                """,
            Long.class,
            firstEventId
        )).isEqualTo(1L);

        UUID deliveryId = jdbcTemplate.queryForObject(
            """
                select id
                from domain_event_handler_deliveries
                where event_id = ? and handler_key = 'test.project-index' and handler_version = 1
                """,
            UUID.class,
            firstEventId
        );
        var firstReceipt = receiptStore.complete(
            WORKSPACE_ID,
            firstEventId,
            deliveryId,
            "test.project-index",
            1,
            Map.of("projectionVersion", 1)
        );
        var duplicateReceipt = receiptStore.complete(
            WORKSPACE_ID,
            firstEventId,
            deliveryId,
            "test.project-index",
            1,
            Map.of("projectionVersion", 2)
        );
        assertThat(firstReceipt.created()).isTrue();
        assertThat(duplicateReceipt.created()).isFalse();
        assertThat(duplicateReceipt.receiptId()).isEqualTo(firstReceipt.receiptId());
        assertThat(duplicateReceipt.result()).containsEntry("projectionVersion", 1);
    }

    @Test
    void rollsBackEnvelopeAndDeliveryWithCallingTransaction() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        try {
            transactionTemplate.executeWithoutResult(status -> {
                outbox.append(envelope(
                    eventId,
                    aggregateId,
                    "s03-rollback:" + eventId,
                    eventId,
                    null,
                    "rollback"
                ));
                throw new IllegalStateException("force rollback");
            });
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessage("force rollback");
        }

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_events where id = ?",
            Long.class,
            eventId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_event_handler_deliveries where event_id = ?",
            Long.class,
            eventId
        )).isZero();
    }

    private static EventEnvelope envelope(
        UUID eventId,
        UUID aggregateId,
        String idempotencyKey,
        UUID correlationId,
        UUID causationId,
        String title
    ) {
        return new EventEnvelope(
            eventId,
            WORKSPACE_ID,
            "project.updated",
            1,
            "project",
            aggregateId,
            null,
            idempotencyKey,
            correlationId,
            causationId,
            Instant.now(),
            Map.of("title", title)
        );
    }

    @TestConfiguration
    static class HandlerConfiguration {
        @Bean
        DomainEventHandler testProjectIndexHandler() {
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
