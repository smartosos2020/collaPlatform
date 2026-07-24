package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectRealtimeDomainEventHandlerTests {
    @Test
    void publishesMinimalOrderedIssueInvalidationsOnlyToSameWorkspaceMembers() {
        UUID workspaceId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectSpaceMembershipRepository memberships = mock(ProjectSpaceMembershipRepository.class);
        when(projects.listProjectMemberIds(workspaceId, projectId)).thenReturn(List.of(memberId));
        when(projects.listProjectMemberIds(otherWorkspaceId, projectId)).thenReturn(List.of(UUID.randomUUID()));
        List<TransactionalOutbox.EventEnvelope> emitted = new ArrayList<>();
        TransactionalOutbox outbox = event -> {
            emitted.add(event);
            return event.eventId();
        };
        EventMessage event = event(
            workspaceId,
            ProjectRealtimeDomainEventHandler.ISSUE_CHANGED,
            "issue",
            issueId,
            41,
            Map.of("projectId", projectId.toString())
        );

        new ProjectRealtimeDomainEventHandler(projects, memberships, outbox).handle(event);

        assertThat(emitted).hasSize(1);
        TransactionalOutbox.EventEnvelope signal = emitted.getFirst();
        assertThat(signal.workspaceId()).isEqualTo(workspaceId);
        assertThat(signal.idempotencyKey()).isEqualTo("realtime:" + event.eventId() + ":" + memberId);
        assertThat(signal.payload()).containsEntry("recipientId", memberId.toString())
            .containsEntry("signalType", "issue.changed")
            .containsEntry("sourceVersion", 41L)
            .containsEntry("calibrationPath", "/api/issues/" + issueId);
        assertThat(signal.payload().keySet()).isEqualTo(Set.of(
            "recipientId", "signalType", "objectType", "objectId", "sourceVersion", "calibrationPath"
        ));
        assertThat(signal.payload()).doesNotContainKeys("title", "body", "acl", "members", "roles");
        verify(projects).listProjectMemberIds(workspaceId, projectId);
        verify(projects, never()).listProjectMemberIds(otherWorkspaceId, projectId);
    }

    @Test
    void permissionTighteningUsesExplicitInvalidationAndIncludesRemovedMember() {
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectSpaceMembershipRepository memberships = mock(ProjectSpaceMembershipRepository.class);
        when(memberships.listMembers(workspaceId, spaceId)).thenReturn(List.of());
        List<TransactionalOutbox.EventEnvelope> emitted = new ArrayList<>();
        TransactionalOutbox outbox = event -> {
            emitted.add(event);
            return event.eventId();
        };

        new ProjectRealtimeDomainEventHandler(projects, memberships, outbox).handle(event(
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
                .containsEntry("sourceVersion", 9L);
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
