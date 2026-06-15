package com.colla.platform.shared.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketMessageSender {
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public WebSocketMessageSender(WebSocketSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public void sendToUser(UUID userId, String type, Map<String, Object> payload) {
        WebSocketEventPayload event = WebSocketEventPayload.of(type, payload);
        for (WebSocketSession session : registry.sessions(userId)) {
            send(session, event);
        }
    }

    public void sendToUser(
        UUID userId,
        String type,
        UUID workspaceId,
        String objectType,
        UUID objectId,
        Map<String, Object> payload
    ) {
        WebSocketEventPayload event = WebSocketEventPayload.of(type, workspaceId, objectType, objectId, payload);
        for (WebSocketSession session : registry.sessions(userId)) {
            send(session, event);
        }
    }

    private void send(WebSocketSession session, WebSocketEventPayload event) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (Exception ignored) {
            // Failed WebSocket pushes are recovered by REST history sync after reconnect.
        }
    }
}
