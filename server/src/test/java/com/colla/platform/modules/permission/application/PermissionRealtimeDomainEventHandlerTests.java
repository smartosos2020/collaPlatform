package com.colla.platform.modules.permission.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PermissionRealtimeDomainEventHandlerTests {
    @Test
    void emitsWorkspaceScopedMinimalSecurityInvalidationWithDurableSequence() {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        AtomicReference<TransactionalOutbox.EventEnvelope> emitted = new AtomicReference<>();
        TransactionalOutbox outbox = event -> {
            emitted.set(event);
            return event.eventId();
        };
        EventMessage event = new EventMessage(
            eventId,
            workspaceId,
            PermissionRealtimeDomainEventHandler.SECURITY_CHANGED,
            1,
            "resource_permission",
            resourceId,
            17,
            UUID.randomUUID(),
            "permission-test",
            eventId,
            null,
            Instant.now(),
            Map.of(
                "objectType", "project",
                "calibrationPath", "/api/resource-permissions/project/" + resourceId,
                "title", "must not be copied"
            )
        );

        new PermissionRealtimeDomainEventHandler(outbox).handle(event);

        assertThat(emitted.get().workspaceId()).isEqualTo(workspaceId);
        assertThat(emitted.get().idempotencyKey()).isEqualTo("realtime:" + eventId);
        assertThat(emitted.get().payload()).containsEntry("signalType", "permission.invalidated")
            .containsEntry("sourceVersion", 17L)
            .containsEntry("calibrationPath", "/api/resource-permissions/project/" + resourceId);
        assertThat(emitted.get().payload().keySet()).isEqualTo(Set.of(
            "signalType", "objectType", "objectId", "sourceVersion", "calibrationPath"
        ));
        assertThat(emitted.get().payload()).doesNotContainKeys("recipientId", "title", "acl", "members", "content");
    }
}
