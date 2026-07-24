package com.colla.platform.modules.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "colla.events.worker.enabled=false")
class RealtimeSignalPersistenceIntegrationTests {
    private static final UUID WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TransactionalOutbox outbox;

    @Autowired
    private RealtimeSignalRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndMarksVersionedUserSignalWithPostgresTimestamps() {
        UUID recipientId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant occurredAt = Instant.now().minusSeconds(2);
        Instant transportedAt = Instant.now();

        jdbcTemplate.update(
            """
                insert into users
                    (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                values (?, ?, ?, 'test-only', 'Realtime recipient', 'active', now(), now())
                """,
            recipientId,
            WORKSPACE_ID,
            "realtime-" + recipientId
        );
        outbox.append(new EventEnvelope(
            sourceEventId,
            WORKSPACE_ID,
            "test.realtime.source",
            1,
            "test_object",
            objectId,
            recipientId,
            "test:realtime-source:" + sourceEventId,
            correlationId,
            null,
            occurredAt,
            Map.of()
        ));
        RealtimeSignalEnvelope envelope = new RealtimeSignalEnvelope(
            RealtimeSignalEnvelope.CURRENT_ENVELOPE_VERSION,
            "notification.changed",
            RealtimeSignalEnvelope.CURRENT_SIGNAL_VERSION,
            signalId,
            WORKSPACE_ID,
            Audience.user(recipientId),
            new ObjectReference("notification", objectId),
            new Sequence(SequenceScope.OBJECT, "notification:" + objectId, 3),
            occurredAt,
            correlationId,
            "/api/notifications",
            Map.of("unreadCount", 1)
        );

        try {
            assertThat(repository.create(sourceEventId, envelope)).isTrue();
            assertThat(repository.create(sourceEventId, envelope)).isFalse();

            var stored = repository.find(signalId).orElseThrow();
            assertThat(stored.envelope())
                .usingRecursiveComparison()
                .ignoringFields("occurredAt")
                .isEqualTo(envelope);
            assertThat(stored.envelope().occurredAt())
                .isBetween(occurredAt.minusMillis(1), occurredAt.plusMillis(1));
            assertThat(stored.transported()).isFalse();
            assertThat(repository.markTransported(signalId, transportedAt)).isTrue();
            assertThat(repository.markTransported(signalId, transportedAt)).isFalse();
            assertThat(repository.find(signalId).orElseThrow().transportedAt())
                .isBetween(transportedAt.minusMillis(1), transportedAt.plusMillis(1));
        } finally {
            jdbcTemplate.update("delete from realtime_signals where id = ?", signalId);
            jdbcTemplate.update("delete from domain_event_handler_deliveries where event_id = ?", sourceEventId);
            jdbcTemplate.update("delete from domain_events where id = ?", sourceEventId);
            jdbcTemplate.update("delete from users where id = ?", recipientId);
        }
    }
}
