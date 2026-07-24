package com.colla.platform.shared.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class PlatformWebSocketHandlerTests {
    @Test
    void gatewayWithoutLegacyHandlersDoesNotDispatchKnowledgeProtocol() throws Exception {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        WebSocketSession session = session();
        PlatformWebSocketHandler handler = new PlatformWebSocketHandler(registry, List.of(), new ObjectMapper());

        handler.handleTextMessage(
            session,
            new TextMessage("{\"type\":\"knowledge.content.update\",\"itemId\":\"ignored\"}")
        );

        verify(registry, never()).unregister(session);
    }

    @Test
    void combinedRoleCanTemporarilyDelegateLegacyProtocol() throws Exception {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        CollaborationMessageHandler legacy = mock(CollaborationMessageHandler.class);
        WebSocketSession session = session();
        when(legacy.supports("knowledge.content.update")).thenReturn(true);
        PlatformWebSocketHandler handler = new PlatformWebSocketHandler(
            registry,
            List.of(legacy),
            new ObjectMapper()
        );
        String payload = "{\"type\":\"knowledge.content.update\"}";

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(legacy).handle(currentUser(session), session, payload);
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
}
