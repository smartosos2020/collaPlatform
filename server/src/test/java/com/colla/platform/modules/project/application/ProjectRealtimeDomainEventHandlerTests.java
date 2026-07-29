package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectRealtimeDomainEventHandlerTests {
    @Test
    void permissionTighteningUsesCanonicalSpaceInvalidationAndIncludesRemovedMember() {
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        ProjectSpaceMembershipRepository memberships = mock(ProjectSpaceMembershipRepository.class);
        when(memberships.listMembers(workspaceId, spaceId)).thenReturn(List.of());
        List<TransactionalOutbox.EventEnvelope> emitted = new ArrayList<>();
        TransactionalOutbox outbox = event -> {
            emitted.add(event);
            return event.eventId();
        };

        new ProjectRealtimeDomainEventHandler(memberships, outbox).handle(event(
            workspaceId,
            ProjectRealtimeDomainEventHandler.PROJECT_SPACE_CHANGED,
            "project_space",
            spaceId,
            9,
            Map.of("affectedUserId", removedUserId.toString(), "accessInvalidated", true)
        ));

        assertThat(emitted).singleElement().satisfies(signal -> {
            assertThat(signal.payload()).containsEntry("recipientId", removedUserId.toString())
                .containsEntry("signalType", "project_space.invalidated")
                .containsEntry("sourceVersion", 9L)
                .containsEntry("calibrationPath", "/api/project-spaces/" + spaceId);
            assertThat(signal.payload()).doesNotContainKeys("acl", "members", "content");
        });
    }

    private static EventMessage event(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        long sequence,
        Map<String, Object> payload
    ) {
        UUID eventId = UUID.randomUUID();
        return new EventMessage(
            eventId,
            workspaceId,
            eventType,
            1,
            aggregateType,
            aggregateId,
            sequence,
            UUID.randomUUID(),
            eventType + ":" + aggregateId,
            eventId,
            null,
            Instant.now(),
            payload
        );
    }
}
