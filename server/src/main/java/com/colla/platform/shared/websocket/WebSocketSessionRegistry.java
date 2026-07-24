package com.colla.platform.shared.websocket;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class WebSocketSessionRegistry {
    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId);
        }
    }

    public Set<WebSocketSession> sessions(UUID userId) {
        return sessionsByUser.getOrDefault(userId, Collections.emptySet());
    }

    public int sessionCount() {
        return sessionsByUser.values().stream().mapToInt(Set::size).sum();
    }

    public void closeAll() {
        sessionsByUser.values().stream().flatMap(Set::stream).forEach(session -> {
            try {
                session.close(org.springframework.web.socket.CloseStatus.SERVICE_RESTARTED);
            } catch (Exception ignored) {
                // Clients recover through reconnect and REST calibration.
            }
        });
        sessionsByUser.clear();
    }
}
