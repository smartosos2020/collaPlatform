package com.colla.platform.modules.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IdentityRealtimeDomainEventHandlerTests {
    @Test
    void disabledMemberSignalIsWorkspaceScopedAndContainsNoIdentityDetails() {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AtomicReference<TransactionalOutbox.EventEnvelope> emitted = new AtomicReference<>();
        TransactionalOutbox outbox = event -> {
            emitted.set(event);
            return event.eventId();
        };
        EventMessage event = new EventMessage(
            eventId,
            workspaceId,
            IdentityRealtimeDomainEventHandler.SECURITY_CHANGED,
            1,
            "user",
            userId,
            23,
            UUID.randomUUID(),
            "identity-test",
            eventId,
            null,
            Instant.now(),
            Map.of("calibrationPath", "/api/admin/users", "roles", ListFixture.PRIVATE_ROLES)
        );

        new IdentityRealtimeDomainEventHandler(outbox).handle(event);

        assertThat(emitted.get().workspaceId()).isEqualTo(workspaceId);
        assertThat(emitted.get().idempotencyKey()).isEqualTo("realtime:" + eventId);
        assertThat(emitted.get().payload()).containsEntry("signalType", "identity.invalidated")
            .containsEntry("sourceVersion", 23L)
            .containsEntry("calibrationPath", "/api/admin/users");
        assertThat(emitted.get().payload().keySet()).isEqualTo(Set.of(
            "signalType", "objectType", "objectId", "sourceVersion", "calibrationPath"
        ));
        assertThat(emitted.get().payload()).doesNotContainKeys("recipientId", "roles", "members", "email");
    }

    private static final class ListFixture {
        private static final java.util.List<String> PRIVATE_ROLES = java.util.List.of("admin");

        private ListFixture() {
        }
    }
}
