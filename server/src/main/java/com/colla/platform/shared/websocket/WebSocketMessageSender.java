package com.colla.platform.shared.websocket;

import com.colla.platform.shared.realtime.RealtimeProperties;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketMessageSender {
    private final ObjectProvider<WebSocketSessionRegistry> registryProvider;
    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;
    private final ThreadPoolExecutor executor;
    private final Map<String, SessionQueue> sessionQueues = new ConcurrentHashMap<>();
    private final Counter enqueued;
    private final Counter sent;
    private final Counter failed;
    private final Counter dropped;
    private final Counter slowClosed;

    public WebSocketMessageSender(
        ObjectProvider<WebSocketSessionRegistry> registryProvider,
        ObjectMapper objectMapper,
        RealtimeProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.registryProvider = registryProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
        properties.validate();
        this.executor = new ThreadPoolExecutor(
            properties.getSendThreads(),
            properties.getSendThreads(),
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(properties.getExecutorQueueCapacity()),
            senderThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
        this.enqueued = meterRegistry.counter("colla.realtime.websocket.send", "outcome", "enqueued");
        this.sent = meterRegistry.counter("colla.realtime.websocket.send", "outcome", "sent");
        this.failed = meterRegistry.counter("colla.realtime.websocket.send", "outcome", "failed");
        this.dropped = meterRegistry.counter("colla.realtime.websocket.send", "outcome", "dropped");
        this.slowClosed = meterRegistry.counter("colla.realtime.websocket.send", "outcome", "slow_closed");
        Gauge.builder("colla.realtime.websocket.session.queues", sessionQueues, Map::size)
            .description("Local per-session realtime send queues")
            .register(meterRegistry);
    }

    public void sendToUser(UUID userId, String type, Map<String, Object> payload) {
        WebSocketEventPayload event = WebSocketEventPayload.of(type, payload);
        fanout(sessions(userId), event);
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
        WebSocketSessionRegistry registry = registryProvider.getIfAvailable();
        fanout(registry == null ? Set.of() : registry.sessions(userId, workspaceId), event);
    }

    public void sendRealtime(RealtimeSignalEnvelope envelope) {
        WebSocketSessionRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Set<WebSocketSession> targets = switch (envelope.audience().kind()) {
            case USER -> registry.sessions(envelope.audience().recipientId(), envelope.workspaceId());
            case WORKSPACE -> registry.workspaceSessions(envelope.workspaceId());
        };
        fanout(targets, WebSocketEventPayload.fromRealtime(envelope));
    }

    private void fanout(Set<WebSocketSession> sessions, WebSocketEventPayload event) {
        sessionQueues.entrySet().removeIf(entry -> !entry.getValue().session().isOpen());
        if (sessions.isEmpty()) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(event);
            if (body.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                dropped.increment(sessions.size());
                return;
            }
            sessions.forEach(session -> enqueue(session, body));
        } catch (Exception exception) {
            dropped.increment(sessions.size());
        }
    }

    private void enqueue(WebSocketSession session, String body) {
        if (!session.isOpen()) {
            unregister(session);
            return;
        }
        SessionQueue queue = sessionQueues.computeIfAbsent(
            session.getId(),
            ignored -> new SessionQueue(session, properties.getSessionQueueCapacity())
        );
        boolean schedule = queue.offer(body);
        if (queue.overflowed()) {
            slowClosed.increment();
            closeAndRemove(queue, CloseStatus.POLICY_VIOLATION.withReason("realtime send queue exceeded"));
            return;
        }
        enqueued.increment();
        if (schedule) {
            try {
                executor.execute(() -> drain(queue));
            } catch (RuntimeException exception) {
                dropped.increment();
                closeAndRemove(queue, CloseStatus.SERVICE_OVERLOAD);
            }
        }
    }

    private void drain(SessionQueue queue) {
        while (true) {
            String body = queue.poll();
            if (body == null) {
                return;
            }
            if (!queue.session().isOpen()) {
                closeAndRemove(queue, CloseStatus.NORMAL);
                return;
            }
            try {
                queue.session().sendMessage(new TextMessage(body));
                sent.increment();
            } catch (Exception exception) {
                failed.increment();
                closeAndRemove(queue, CloseStatus.SESSION_NOT_RELIABLE);
                return;
            }
        }
    }

    private void closeAndRemove(SessionQueue queue, CloseStatus status) {
        queue.clear();
        sessionQueues.remove(queue.session().getId(), queue);
        unregister(queue.session());
        try {
            queue.session().close(status);
        } catch (Exception ignored) {
            // The connection is already unusable; REST calibration remains authoritative.
        }
    }

    private void unregister(WebSocketSession session) {
        WebSocketSessionRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.unregister(session);
        }
    }

    private java.util.Set<WebSocketSession> sessions(UUID userId) {
        WebSocketSessionRegistry registry = registryProvider.getIfAvailable();
        return registry == null ? java.util.Set.of() : registry.sessions(userId);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(properties.getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        sessionQueues.clear();
    }

    private static ThreadFactory senderThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "realtime-websocket-send-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class SessionQueue {
        private final WebSocketSession session;
        private final int capacity;
        private final ArrayDeque<String> messages = new ArrayDeque<>();
        private boolean draining;
        private boolean overflowed;

        private SessionQueue(WebSocketSession session, int capacity) {
            this.session = session;
            this.capacity = capacity;
        }

        synchronized boolean offer(String body) {
            if (messages.size() >= capacity) {
                overflowed = true;
                return false;
            }
            messages.addLast(body);
            if (draining) {
                return false;
            }
            draining = true;
            return true;
        }

        synchronized String poll() {
            String next = messages.pollFirst();
            if (next == null) {
                draining = false;
            }
            return next;
        }

        synchronized boolean overflowed() {
            return overflowed;
        }

        synchronized void clear() {
            messages.clear();
            draining = false;
        }

        WebSocketSession session() {
            return session;
        }
    }
}
