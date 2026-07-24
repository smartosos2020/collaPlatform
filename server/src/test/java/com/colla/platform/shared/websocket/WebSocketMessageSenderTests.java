package com.colla.platform.shared.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.shared.realtime.RealtimeProperties;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class WebSocketMessageSenderTests {
    @Test
    void userAudienceOnlyUsesWorkspaceScopedLocalSessions() throws Exception {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WebSocketSessionRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        WebSocketSession target = openSession("target");
        WebSocketSession otherWorkspace = openSession("other");
        RealtimeSignalEnvelope envelope = envelope(Audience.user(UUID.randomUUID()));
        when(registry.sessions(envelope.audience().recipientId(), envelope.workspaceId()))
            .thenReturn(Set.of(target));
        WebSocketMessageSender sender = sender(provider, properties(8));
        try {
            sender.sendRealtime(envelope);

            verify(target, timeout(2_000)).sendMessage(any(TextMessage.class));
            verify(otherWorkspace, never()).sendMessage(any());
        } finally {
            sender.shutdown();
        }
    }

    @Test
    void slowClientIsClosedWhenItsBoundedQueueOverflows() throws Exception {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WebSocketSessionRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        WebSocketSession session = openSession("slow");
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(ignored -> {
            sending.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        RealtimeSignalEnvelope first = envelope(Audience.workspace());
        when(registry.workspaceSessions(first.workspaceId())).thenReturn(Set.of(session));
        WebSocketMessageSender sender = sender(provider, properties(1));
        try {
            sender.sendRealtime(first);
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
            sender.sendRealtime(sameWorkspaceEnvelope(first.workspaceId()));
            sender.sendRealtime(sameWorkspaceEnvelope(first.workspaceId()));

            verify(session, timeout(2_000)).close(any(CloseStatus.class));
            verify(registry).unregister(session);
        } finally {
            release.countDown();
            sender.shutdown();
        }
    }

    private static WebSocketMessageSender sender(
        ObjectProvider<WebSocketSessionRegistry> provider,
        RealtimeProperties properties
    ) {
        return new WebSocketMessageSender(
            provider,
            new ObjectMapper().findAndRegisterModules(),
            properties,
            new SimpleMeterRegistry()
        );
    }

    private static RealtimeProperties properties(int sessionQueueCapacity) {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setSendThreads(1);
        properties.setExecutorQueueCapacity(4);
        properties.setSessionQueueCapacity(sessionQueueCapacity);
        return properties;
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static RealtimeSignalEnvelope envelope(Audience audience) {
        return envelope(UUID.randomUUID(), audience);
    }

    private static RealtimeSignalEnvelope sameWorkspaceEnvelope(UUID workspaceId) {
        return envelope(workspaceId, Audience.workspace());
    }

    private static RealtimeSignalEnvelope envelope(UUID workspaceId, Audience audience) {
        UUID objectId = UUID.randomUUID();
        return new RealtimeSignalEnvelope(
            1,
            "notification.changed",
            1,
            UUID.randomUUID(),
            workspaceId,
            audience,
            new ObjectReference("notification", objectId),
            new Sequence(SequenceScope.OBJECT, "notification:" + objectId, 1),
            Instant.now(),
            UUID.randomUUID(),
            "/api/notifications",
            Map.of()
        );
    }
}
