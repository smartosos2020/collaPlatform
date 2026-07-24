package com.colla.platform.shared.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.config.runtime.RuntimeRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class RealtimeRedisHealthIndicatorTests {
    @Test
    void gatewayIsReadyOnlyWhenSubscriberRunsAndRedisResponds() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisMessageListenerContainer listener = mock(RedisMessageListenerContainer.class);
        when(listener.isRunning()).thenReturn(true);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        var health = new RealtimeRedisHealthIndicator(
            connectionFactory,
            listener,
            new RealtimeProperties(),
            RuntimeRole.EVENT_GATEWAY,
            availability()
        ).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
            .containsEntry("redis", "up")
            .containsEntry("subscriber", "running");
    }

    @Test
    void gatewayIsNotReadyWhenRedisCannotBeReached() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisMessageListenerContainer listener = mock(RedisMessageListenerContainer.class);
        when(listener.isRunning()).thenReturn(true);
        when(connectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        var health = new RealtimeRedisHealthIndicator(
            connectionFactory,
            listener,
            new RealtimeProperties(),
            RuntimeRole.EVENT_GATEWAY,
            availability()
        ).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
            .containsEntry("redis", "down")
            .containsEntry("subscriber", "running");
    }

    private static RealtimeRedisAvailability availability() {
        return new RealtimeRedisAvailability(() -> {
        });
    }
}
