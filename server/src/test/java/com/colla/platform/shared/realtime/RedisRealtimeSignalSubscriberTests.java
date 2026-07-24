package com.colla.platform.shared.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class RedisRealtimeSignalSubscriberTests {
    @Test
    void duplicateSignalAndSequenceFanOutOnlyOnce() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AtomicInteger consumed = new AtomicInteger();
        RedisRealtimeSignalSubscriber subscriber = new RedisRealtimeSignalSubscriber(
            objectMapper,
            properties(8),
            List.of(ignored -> consumed.incrementAndGet()),
            meters,
            availability()
        );
        Message message = message(objectMapper.writeValueAsBytes(envelope()));

        subscriber.onMessage(message, null);
        subscriber.onMessage(message, null);

        assertThat(consumed).hasValue(1);
        assertThat(meters.counter("colla.realtime.redis.consume", "outcome", "duplicate").count())
            .isEqualTo(1);
    }

    @Test
    void recentSignalWindowIsBoundedAndEvictsOldestSignal() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AtomicInteger consumed = new AtomicInteger();
        RedisRealtimeSignalSubscriber subscriber = new RedisRealtimeSignalSubscriber(
            objectMapper,
            properties(1),
            List.of(ignored -> consumed.incrementAndGet()),
            new SimpleMeterRegistry(),
            availability()
        );
        RealtimeSignalEnvelope first = envelope();
        RealtimeSignalEnvelope second = envelope();

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(first)), null);
        subscriber.onMessage(message(objectMapper.writeValueAsBytes(second)), null);
        subscriber.onMessage(message(objectMapper.writeValueAsBytes(first)), null);

        assertThat(consumed).hasValue(3);
    }

    @Test
    void unknownVersionIsDroppedBeforeDeserializationOrFanout() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AtomicInteger consumed = new AtomicInteger();
        RedisRealtimeSignalSubscriber subscriber = new RedisRealtimeSignalSubscriber(
            objectMapper,
            properties(8),
            List.of(ignored -> consumed.incrementAndGet()),
            meters,
            availability()
        );
        ObjectNode root = objectMapper.valueToTree(envelope());
        root.put("envelopeVersion", 99);

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(root)), null);

        assertThat(consumed).hasValue(0);
        assertThat(meters.counter(
            "colla.realtime.redis.consume",
            "outcome",
            "unknown_envelope_version"
        ).count()).isEqualTo(1);
    }

    @Test
    void redisFailureClosesTheAvailabilityGateUntilTransportResumes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AtomicInteger interruptions = new AtomicInteger();
        RealtimeRedisAvailability availability =
            new RealtimeRedisAvailability(interruptions::incrementAndGet);
        RedisRealtimeSignalSubscriber subscriber = new RedisRealtimeSignalSubscriber(
            objectMapper,
            properties(8),
            List.of(ignored -> {
            }),
            meters,
            availability
        );

        subscriber.recordRedisFailure(new IllegalStateException("redis unavailable"));

        assertThat(availability.isAvailable()).isFalse();
        assertThat(interruptions).hasValue(1);
        assertThat(meters.counter("colla.realtime.redis.consume", "outcome", "redis_failure").count())
            .isEqualTo(1);

        subscriber.onMessage(message(objectMapper.writeValueAsBytes(envelope())), null);

        assertThat(availability.isAvailable()).isTrue();
    }

    private static RealtimeProperties properties(int recentCapacity) {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setRecentSignalCapacity(recentCapacity);
        return properties;
    }

    private static RealtimeRedisAvailability availability() {
        return new RealtimeRedisAvailability(() -> {
        });
    }

    private static Message message(byte[] body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        when(message.getChannel()).thenReturn("colla:test:realtime:v1".getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private static RealtimeSignalEnvelope envelope() {
        UUID objectId = UUID.randomUUID();
        return new RealtimeSignalEnvelope(
            1,
            "notification.changed",
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Audience.user(UUID.randomUUID()),
            new ObjectReference("notification", objectId),
            new Sequence(SequenceScope.OBJECT, "notification:" + objectId, 7),
            Instant.now(),
            UUID.randomUUID(),
            "/api/notifications",
            Map.of("notificationId", objectId.toString())
        );
    }
}
