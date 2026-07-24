package com.colla.platform.shared.websocket;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.realtime.RealtimeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class WebSocketSessionRegistry {
    private final Map<String, SessionRegistration> sessionsById = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> sessionsByWorkspace = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> sessionsByDevice = new ConcurrentHashMap<>();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private final Object registrationsMonitor = new Object();
    private final int maxConnections;
    private final Counter capacityRejections;

    public WebSocketSessionRegistry(RealtimeProperties properties, MeterRegistry meterRegistry) {
        this.maxConnections = properties.getMaxConnections();
        this.capacityRejections = Counter.builder("colla.realtime.websocket.capacity.rejections")
            .description("Realtime WebSocket connections rejected by the local capacity budget")
            .register(meterRegistry);
        Gauge.builder("colla.realtime.websocket.connections", this, WebSocketSessionRegistry::sessionCount)
            .description("Local realtime WebSocket connections")
            .register(meterRegistry);
    }

    public void register(UUID userId, WebSocketSession session) {
        CurrentUser currentUser = currentUser(session);
        UUID workspaceId = currentUser == null ? null : currentUser.workspaceId();
        UUID deviceId = currentUser == null ? null : currentUser.deviceId();
        register(userId, workspaceId, deviceId, session);
    }

    public void register(UUID userId, UUID workspaceId, UUID deviceId, WebSocketSession session) {
        if (userId == null || session == null) {
            throw new IllegalArgumentException("Realtime session identity is required");
        }
        lifecycle.readLock().lock();
        try {
            if (closing.get()) {
                close(session, CloseStatus.SERVICE_RESTARTED);
                return;
            }

            synchronized (registrationsMonitor) {
                String sessionId = session.getId();
                if (!sessionsById.containsKey(sessionId) && sessionsById.size() >= maxConnections) {
                    capacityRejections.increment();
                    close(session, new CloseStatus(1013, "Realtime capacity reached"));
                    return;
                }
                SessionRegistration registration = new SessionRegistration(
                    sessionId,
                    userId,
                    workspaceId,
                    deviceId,
                    session
                );
                SessionRegistration replaced = sessionsById.put(sessionId, registration);
                if (replaced != null) {
                    unindex(replaced);
                }
                index(sessionsByUser, userId, sessionId);
                if (workspaceId != null) {
                    index(sessionsByWorkspace, workspaceId, sessionId);
                }
                if (deviceId != null) {
                    index(sessionsByDevice, deviceId, sessionId);
                }
            }
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    public void unregister(UUID userId, WebSocketSession session) {
        unregister(session);
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        synchronized (registrationsMonitor) {
            SessionRegistration removed = sessionsById.remove(session.getId());
            if (removed != null) {
                unindex(removed);
            }
        }
    }

    public Set<WebSocketSession> sessions(UUID userId) {
        return resolve(sessionsByUser.get(userId), null);
    }

    public Set<WebSocketSession> sessions(UUID userId, UUID workspaceId) {
        return resolve(sessionsByUser.get(userId), workspaceId);
    }

    public Set<WebSocketSession> workspaceSessions(UUID workspaceId) {
        return resolve(sessionsByWorkspace.get(workspaceId), workspaceId);
    }

    public Set<WebSocketSession> deviceSessions(UUID deviceId) {
        return resolve(sessionsByDevice.get(deviceId), null);
    }

    public int sessionCount() {
        return sessionsById.size();
    }

    public Set<SessionRegistration> registrations() {
        return Set.copyOf(sessionsById.values());
    }

    public void closeAll() {
        lifecycle.writeLock().lock();
        try {
            closing.set(true);
            sessionsById.values().forEach(registration -> close(registration.session(), CloseStatus.SERVICE_RESTARTED));
            synchronized (registrationsMonitor) {
                sessionsById.clear();
                sessionsByUser.clear();
                sessionsByWorkspace.clear();
                sessionsByDevice.clear();
            }
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    public void closeForTransportInterruption() {
        lifecycle.writeLock().lock();
        try {
            CloseStatus status = new CloseStatus(1013, "Realtime transport unavailable");
            sessionsById.values().forEach(registration -> close(registration.session(), status));
            synchronized (registrationsMonitor) {
                sessionsById.clear();
                sessionsByUser.clear();
                sessionsByWorkspace.clear();
                sessionsByDevice.clear();
            }
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    private void index(Map<UUID, Set<String>> index, UUID key, String sessionId) {
        index.compute(key, (ignored, current) -> {
            Set<String> sessions = current == null ? ConcurrentHashMap.newKeySet() : current;
            sessions.add(sessionId);
            return sessions;
        });
    }

    private void unindex(SessionRegistration registration) {
        removeIndex(sessionsByUser, registration.userId(), registration.sessionId());
        if (registration.workspaceId() != null) {
            removeIndex(sessionsByWorkspace, registration.workspaceId(), registration.sessionId());
        }
        if (registration.deviceId() != null) {
            removeIndex(sessionsByDevice, registration.deviceId(), registration.sessionId());
        }
    }

    private void removeIndex(Map<UUID, Set<String>> index, UUID key, String sessionId) {
        index.computeIfPresent(key, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private Set<WebSocketSession> resolve(Set<String> ids, UUID requiredWorkspaceId) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<WebSocketSession> result = new LinkedHashSet<>();
        for (String id : Set.copyOf(ids)) {
            SessionRegistration registration = sessionsById.get(id);
            if (registration != null
                && (requiredWorkspaceId == null || requiredWorkspaceId.equals(registration.workspaceId()))) {
                result.add(registration.session());
            }
        }
        return Set.copyOf(result);
    }

    private CurrentUser currentUser(WebSocketSession session) {
        Object value = session.getAttributes().get(WebSocketAuthInterceptor.CURRENT_USER_ATTRIBUTE);
        return value instanceof CurrentUser currentUser ? currentUser : null;
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // Clients recover through reconnect and REST calibration.
        }
    }

    public record SessionRegistration(
        String sessionId,
        UUID userId,
        UUID workspaceId,
        UUID deviceId,
        WebSocketSession session
    ) {
    }
}
