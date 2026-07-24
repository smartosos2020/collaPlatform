package com.colla.platform.shared.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.shared.realtime.RealtimeProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class WebSocketSessionRegistryTests {
    @Test
    void indexesLocalSessionsByUserWorkspaceAndDevice() {
        WebSocketSessionRegistry registry = registry(5_000);
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        WebSocketSession session = session("session-a");

        registry.register(userId, workspaceId, deviceId, session);

        assertThat(registry.sessions(userId, workspaceId)).containsExactly(session);
        assertThat(registry.workspaceSessions(workspaceId)).containsExactly(session);
        assertThat(registry.deviceSessions(deviceId)).containsExactly(session);
        assertThat(registry.registrations()).singleElement().satisfies(registration -> {
            assertThat(registration.userId()).isEqualTo(userId);
            assertThat(registration.workspaceId()).isEqualTo(workspaceId);
            assertThat(registration.deviceId()).isEqualTo(deviceId);
        });
    }

    @Test
    void closeAllCannotRaceWithRegistrationAndLeaveASurvivingSession() throws Exception {
        WebSocketSessionRegistry registry = registry(5_000);
        WebSocketSession session = mock(WebSocketSession.class);
        CountDownLatch registrationInsideLifecycle = new CountDownLatch(1);
        CountDownLatch releaseRegistration = new CountDownLatch(1);
        when(session.getId()).thenAnswer(ignored -> {
            registrationInsideLifecycle.countDown();
            assertThat(releaseRegistration.await(5, TimeUnit.SECONDS)).isTrue();
            return "racing-session";
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var registration = executor.submit(() ->
                registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), session)
            );
            assertThat(registrationInsideLifecycle.await(5, TimeUnit.SECONDS)).isTrue();
            var shutdown = executor.submit(registry::closeAll);
            releaseRegistration.countDown();
            registration.get(5, TimeUnit.SECONDS);
            shutdown.get(5, TimeUnit.SECONDS);
        }

        assertThat(registry.sessionCount()).isZero();
        verify(session).close(CloseStatus.SERVICE_RESTARTED);
    }

    @Test
    void registrationAfterShutdownIsRejectedAndClosed() throws Exception {
        WebSocketSessionRegistry registry = registry(5_000);
        WebSocketSession session = session("late-session");

        registry.closeAll();
        registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), session);

        assertThat(registry.sessionCount()).isZero();
        verify(session).close(CloseStatus.SERVICE_RESTARTED);
    }

    @Test
    void transportInterruptionClosesCurrentSessionsButAllowsRecovery() throws Exception {
        WebSocketSessionRegistry registry = registry(5_000);
        WebSocketSession interrupted = session("interrupted");
        WebSocketSession recovered = session("recovered");

        registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), interrupted);
        registry.closeForTransportInterruption();
        registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), recovered);

        verify(interrupted).close(new CloseStatus(1013, "Realtime transport unavailable"));
        assertThat(registry.registrations())
            .singleElement()
            .extracting(WebSocketSessionRegistry.SessionRegistration::session)
            .isEqualTo(recovered);
    }

    @Test
    void rejectsConnectionsBeyondTheConfiguredLocalBudget() throws Exception {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxConnections(1);
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(properties, meters);
        WebSocketSession first = session("first");
        WebSocketSession rejected = session("rejected");

        registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), first);
        registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), rejected);

        assertThat(registry.sessionCount()).isEqualTo(1);
        assertThat(meters.counter("colla.realtime.websocket.capacity.rejections").count()).isEqualTo(1);
        verify(rejected).close(new CloseStatus(1013, "Realtime capacity reached"));
    }

    @Test
    void concurrentConnectionBurstNeverExceedsConfiguredBudget() throws Exception {
        int budget = 8;
        int attempts = 64;
        AtomicInteger rejections = new AtomicInteger();
        WebSocketSessionRegistry registry = registry(budget);
        List<WebSocketSession> sessions = new ArrayList<>();
        for (int index = 0; index < attempts; index += 1) {
            WebSocketSession session = session("burst-" + index);
            org.mockito.Mockito.doAnswer(ignored -> {
                rejections.incrementAndGet();
                return null;
            }).when(session).close(new CloseStatus(1013, "Realtime capacity reached"));
            sessions.add(session);
        }

        try (var executor = Executors.newFixedThreadPool(16)) {
            for (WebSocketSession session : sessions) {
                executor.submit(() ->
                    registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), session)
                );
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(registry.sessionCount()).isEqualTo(budget);
        assertThat(rejections).hasValue(attempts - budget);
    }

    private static WebSocketSessionRegistry registry(int maxConnections) {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxConnections(maxConnections);
        return new WebSocketSessionRegistry(properties, new SimpleMeterRegistry());
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());
        return session;
    }
}
