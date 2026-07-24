package com.colla.platform.shared.websocket;

import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.web.socket.WebSocketSession;

/**
 * Application-neutral port for collaboration WebSocket messages.
 */
public interface CollaborationMessageHandler {

    boolean supports(String type);

    void handle(CurrentUser currentUser, WebSocketSession session, String rawPayload);

    void disconnect(WebSocketSession session, CurrentUser currentUser);
}
