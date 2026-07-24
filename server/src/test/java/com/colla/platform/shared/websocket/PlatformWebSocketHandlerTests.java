package com.colla.platform.shared.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.realtime.RealtimeProperties;
import com.colla.platform.shared.realtime.RealtimeRedisAvailability;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class PlatformWebSocketHandlerTests {
    @Test
    void rejectsLegacyKnowledgeProtocolWithAnExplicitUpgradeNotice() throws Exception {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        WebSocketSession session = session();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        PlatformWebSocketHandler handler = new PlatformWebSocketHandler(
            registry,
            new ObjectMapper(),
            meters,
            new RealtimeProperties(),
            runtimeProperties("gateway-test"),
            availableRedis()
        );

        handler.handleTextMessage(
            session,
            new TextMessage("{\"type\":\"knowledge.content.update\",\"itemId\":\"ignored\"}")
        );

        ArgumentCaptor<TextMessage> response = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(response.capture());
        org.assertj.core.api.Assertions.assertThat(response.getValue().getPayload())
            .contains("\"type\":\"protocol.upgrade_required\"")
            .contains("\"protocol\":\"colla-yjs-v1\"")
            .contains("\"command\":\"update\"");
        org.assertj.core.api.Assertions.assertThat(
            meters.counter(
                "colla.realtime.websocket.inbound",
                "command", "update",
                "outcome", "rejected"
            ).count()
        ).isEqualTo(1);
        verify(registry, never()).unregister(currentUser(session).id(), session);
    }

    @Test
    void observeFallbackKeepsUnknownClientFramesVisibleWithoutAWritePath() throws Exception {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        WebSocketSession session = session();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        RealtimeProperties properties = new RealtimeProperties();
        properties.setLegacyKnowledgeInboundPolicy("observe");
        PlatformWebSocketHandler handler = new PlatformWebSocketHandler(
            registry,
            new ObjectMapper(),
            meters,
            properties,
            runtimeProperties("combined-test"),
            availableRedis()
        );

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"future.client.signal\"}"));

        verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThat(
            meters.counter(
                "colla.realtime.websocket.inbound",
                "command", "other",
                "outcome", "observed"
            ).count()
        ).isEqualTo(1);
    }

    @Test
    void unavailableRedisRejectsConnectionBeforeReadyFrame() throws Exception {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        WebSocketSession session = session();
        RealtimeRedisAvailability availability = availableRedis();
        availability.markUnavailable();
        PlatformWebSocketHandler handler = new PlatformWebSocketHandler(
            registry,
            new ObjectMapper(),
            new SimpleMeterRegistry(),
            new RealtimeProperties(),
            runtimeProperties("gateway-test"),
            availability
        );

        handler.afterConnectionEstablished(session);

        verify(session).close(new CloseStatus(1013, "Realtime transport unavailable"));
        verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
        verify(registry, never()).register(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private static WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthInterceptor.CURRENT_USER_ATTRIBUTE, new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "member",
            "Member",
            Set.of(),
            Set.of()
        ));
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private static CurrentUser currentUser(WebSocketSession session) {
        return (CurrentUser) session.getAttributes().get(WebSocketAuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private static RuntimeRoleProperties runtimeProperties(String instanceId) {
        RuntimeRoleProperties properties = new RuntimeRoleProperties();
        properties.setInstanceId(instanceId);
        return properties;
    }

    private static RealtimeRedisAvailability availableRedis() {
        return new RealtimeRedisAvailability(() -> {
        });
    }
}
