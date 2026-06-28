package com.colla.platform.shared.websocket;

import com.colla.platform.modules.doc.application.DocumentCollaborationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class PlatformWebSocketHandler extends TextWebSocketHandler {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WebSocketSessionRegistry registry;
    private final DocumentCollaborationService documentCollaborationService;
    private final ObjectMapper objectMapper;

    public PlatformWebSocketHandler(
        WebSocketSessionRegistry registry,
        DocumentCollaborationService documentCollaborationService,
        ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.documentCollaborationService = documentCollaborationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        CurrentUser currentUser = currentUser(session);
        registry.register(currentUser.id(), session);
        session.sendMessage(new TextMessage("{\"type\":\"connection.ready\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> command;
        try {
            command = objectMapper.readValue(message.getPayload(), MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return;
        }
        String type = command.get("type") == null ? "" : String.valueOf(command.get("type"));
        if (documentCollaborationService.supports(type)) {
            documentCollaborationService.handle(currentUser(session), session, message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        CurrentUser currentUser = currentUser(session);
        documentCollaborationService.disconnect(session, currentUser);
        registry.unregister(currentUser.id(), session);
    }

    private CurrentUser currentUser(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        return (CurrentUser) attributes.get(WebSocketAuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }
}
