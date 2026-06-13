package com.colla.platform.shared.websocket;

import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class PlatformWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketSessionRegistry registry;

    public PlatformWebSocketHandler(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        CurrentUser currentUser = currentUser(session);
        registry.register(currentUser.id(), session);
        session.sendMessage(new TextMessage("{\"type\":\"connection.ready\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client commands are intentionally handled through REST in the MVP.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(currentUser(session).id(), session);
    }

    private CurrentUser currentUser(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        return (CurrentUser) attributes.get(WebSocketAuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }
}
