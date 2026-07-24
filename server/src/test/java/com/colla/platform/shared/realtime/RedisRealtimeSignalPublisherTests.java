package com.colla.platform.shared.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRealtimeSignalPublisherTests {
    @Test
    void publishesVersionedEnvelopeToConfiguredChannel() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RealtimeProperties properties = new RealtimeProperties();
        properties.setChannel("colla:test:realtime:v1");
        RealtimeSignalEnvelope envelope = envelope();
        String body = objectMapper.writeValueAsString(envelope);
        when(redis.convertAndSend(properties.getChannel(), body)).thenReturn(2L);

        RealtimeSignalPublisher.PublishResult result = new RedisRealtimeSignalPublisher(
            redis,
            objectMapper,
            properties,
            new SimpleMeterRegistry()
        ).publish(envelope);

        assertThat(result.published()).isTrue();
        assertThat(result.subscriberCount()).isEqualTo(2);
        verify(redis).convertAndSend(properties.getChannel(), body);
    }

    @Test
    void redisFailureIsReturnedForExistingDeliveryRetry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.convertAndSend(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )).thenThrow(new IllegalStateException("redis unavailable"));

        RealtimeSignalPublisher.PublishResult result = new RedisRealtimeSignalPublisher(
            redis,
            new ObjectMapper().findAndRegisterModules(),
            new RealtimeProperties(),
            new SimpleMeterRegistry()
        ).publish(envelope());

        assertThat(result.published()).isFalse();
        assertThat(result.failure()).contains("Redis realtime publish failed");
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
            new Sequence(SequenceScope.OBJECT, "notification:" + objectId, 1),
            Instant.now(),
            UUID.randomUUID(),
            "/api/notifications",
            Map.of()
        );
    }
}
