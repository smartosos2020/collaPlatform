package com.colla.platform.shared.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class WebSocketSessionRegistryTests {
    @Test
    void indexesLocalSessionsByUserWorkspaceAndDevice() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(new SimpleMeterRegistry());
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
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(new SimpleMeterRegistry());
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
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(new SimpleMeterRegistry());
        WebSocketSession session = session("late-session");

        registry.closeAll();
        registry.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), session);

        assertThat(registry.sessionCount()).isZero();
        verify(session).close(CloseStatus.SERVICE_RESTARTED);
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());
        return session;
    }
}
