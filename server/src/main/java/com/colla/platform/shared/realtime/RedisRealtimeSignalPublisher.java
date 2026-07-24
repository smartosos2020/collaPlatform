package com.colla.platform.shared.realtime;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.COMBINED})
public class RedisRealtimeSignalPublisher implements RealtimeSignalPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;
    private final Counter published;
    private final Counter failed;
    private final DistributionSummary subscribers;

    public RedisRealtimeSignalPublisher(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        RealtimeProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.published = meterRegistry.counter("colla.realtime.redis.publish", "outcome", "published");
        this.failed = meterRegistry.counter("colla.realtime.redis.publish", "outcome", "failed");
        this.subscribers = DistributionSummary.builder("colla.realtime.redis.subscribers")
            .description("Redis subscribers reported for realtime publishes")
            .register(meterRegistry);
    }

    @Override
    public PublishResult publish(RealtimeSignalEnvelope envelope) {
        try {
            String body = objectMapper.writeValueAsString(envelope);
            if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                failed.increment();
                return PublishResult.failed("Realtime envelope exceeds the transport byte budget");
            }
            Long subscriberCount = redisTemplate.convertAndSend(properties.getChannel(), body);
            long count = subscriberCount == null ? 0 : subscriberCount;
            published.increment();
            subscribers.record(count);
            return PublishResult.published(count);
        } catch (RuntimeException exception) {
            failed.increment();
            return PublishResult.failed("Redis realtime publish failed: " + exception.getClass().getSimpleName());
        } catch (Exception exception) {
            failed.increment();
            return PublishResult.failed("Realtime envelope serialization failed");
        }
    }
}
