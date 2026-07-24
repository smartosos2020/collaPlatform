package com.colla.platform.shared.realtime;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class RedisRealtimeSignalSubscriber implements MessageListener {
    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;
    private final List<RealtimeSignalConsumer> consumers;
    private final RecentRealtimeSignalCache recentSignals;
    private final Counter received;
    private final Counter duplicate;
    private final Counter malformed;
    private final Counter unknownEnvelopeVersion;
    private final Counter unknownSignalVersion;
    private final Counter consumerFailure;
    private final Counter redisFailure;

    public RedisRealtimeSignalSubscriber(
        ObjectMapper objectMapper,
        RealtimeProperties properties,
        List<RealtimeSignalConsumer> consumers,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.consumers = List.copyOf(consumers);
        this.recentSignals = new RecentRealtimeSignalCache(properties.getRecentSignalCapacity());
        this.received = counter(meterRegistry, "received");
        this.duplicate = counter(meterRegistry, "duplicate");
        this.malformed = counter(meterRegistry, "malformed");
        this.unknownEnvelopeVersion = counter(meterRegistry, "unknown_envelope_version");
        this.unknownSignalVersion = counter(meterRegistry, "unknown_signal_version");
        this.consumerFailure = counter(meterRegistry, "consumer_failure");
        this.redisFailure = counter(meterRegistry, "redis_failure");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        byte[] body = message.getBody();
        if (body.length > properties.getMaxPayloadBytes()) {
            malformed.increment();
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            if (root.path("envelopeVersion").asInt(-1) != RealtimeSignalEnvelope.CURRENT_ENVELOPE_VERSION) {
                unknownEnvelopeVersion.increment();
                return;
            }
            if (root.path("signalVersion").asInt(-1) != RealtimeSignalEnvelope.CURRENT_SIGNAL_VERSION) {
                unknownSignalVersion.increment();
                return;
            }
            RealtimeSignalEnvelope envelope = objectMapper.treeToValue(root, RealtimeSignalEnvelope.class);
            if (!recentSignals.firstSeen(envelope)) {
                duplicate.increment();
                return;
            }
            received.increment();
            for (RealtimeSignalConsumer consumer : consumers) {
                try {
                    consumer.consume(envelope);
                } catch (RuntimeException exception) {
                    consumerFailure.increment();
                }
            }
        } catch (Exception exception) {
            malformed.increment();
        }
    }

    public void recordRedisFailure(Throwable failure) {
        redisFailure.increment();
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return registry.counter("colla.realtime.redis.consume", "outcome", outcome);
    }
}
