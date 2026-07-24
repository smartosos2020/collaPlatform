package com.colla.platform.shared.realtime;

import com.colla.platform.config.runtime.RuntimeRole;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

public class RealtimeRedisHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;
    private final RedisMessageListenerContainer listenerContainer;
    private final RealtimeProperties properties;
    private final RuntimeRole runtimeRole;

    public RealtimeRedisHealthIndicator(
        RedisConnectionFactory connectionFactory,
        RedisMessageListenerContainer listenerContainer,
        RealtimeProperties properties,
        RuntimeRole runtimeRole
    ) {
        this.connectionFactory = connectionFactory;
        this.listenerContainer = listenerContainer;
        this.properties = properties;
        this.runtimeRole = runtimeRole;
    }

    @Override
    public Health health() {
        if (runtimeRole != RuntimeRole.EVENT_GATEWAY && runtimeRole != RuntimeRole.COMBINED) {
            return Health.up()
                .withDetail("realtimeRedis", "not_applicable")
                .withDetail("runtimeRole", runtimeRole.value())
                .build();
        }
        if (listenerContainer == null || connectionFactory == null) {
            return Health.down()
                .withDetail("redis", "subscriber_not_configured")
                .withDetail("runtimeRole", runtimeRole.value())
                .withDetail("channel", properties.getChannel())
                .build();
        }
        if (!listenerContainer.isRunning()) {
            return Health.down()
                .withDetail("redis", "subscriber_stopped")
                .withDetail("channel", properties.getChannel())
                .build();
        }
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if (!"PONG".equalsIgnoreCase(response)) {
                return Health.down()
                    .withDetail("redis", "unexpected_ping")
                    .withDetail("channel", properties.getChannel())
                    .build();
            }
            return Health.up()
                .withDetail("redis", "up")
                .withDetail("subscriber", "running")
                .withDetail("channel", properties.getChannel())
                .build();
        } catch (RuntimeException exception) {
            return Health.down()
                .withDetail("redis", "down")
                .withDetail("subscriber", listenerContainer.isRunning() ? "running" : "stopped")
                .withDetail("channel", properties.getChannel())
                .build();
        }
    }
}
