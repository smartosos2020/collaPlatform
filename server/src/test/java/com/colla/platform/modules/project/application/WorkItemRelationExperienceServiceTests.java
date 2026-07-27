package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Cardinality;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.DeletionPolicy;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Direction;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationDefinitionBinding;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository.ImpactEdge;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.WorkItemRelationRuntimeAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkItemRelationExperienceServiceTests {
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();
    private final CurrentUser user = new CurrentUser(
        UUID.randomUUID(), workspaceId, UUID.randomUUID(), "member", "Member",
        Set.of(), Set.of()
    );
    private final WorkItemRepository workItems = mock(WorkItemRepository.class);
    private final WorkItemRelationRepository relations =
        mock(WorkItemRelationRepository.class);
    private final WorkItemRelationRuntimeAdapter runtime =
        mock(WorkItemRelationRuntimeAdapter.class);
    private final WorkItemRelationAccessDecisionService access =
        mock(WorkItemRelationAccessDecisionService.class);
    private final WorkItemRelationService relationService =
        mock(WorkItemRelationService.class);
    private WorkItemRelationExperienceService service;

    @BeforeEach
    void setUp() {
        service = new WorkItemRelationExperienceService(
            workItems, relations, runtime, access, relationService
        );
    }

    @Test
    void targetSearchAppliesPublishedTypeMatrixAndHidesSelf() {
        WorkItem source = item("TASK-1", "task", 3);
        WorkItem target = item("BUG-2", "bug", 4);
        when(workItems.find(workspaceId, spaceId, source.id()))
            .thenReturn(java.util.Optional.of(source));
        when(runtime.requireForSource(source, "blocks"))
            .thenReturn(binding(RelationKind.blocking, List.of("bug"), false));
        when(workItems.searchRelationTargets(
            workspaceId, spaceId, List.of("bug"), "BUG", null, 26
        )).thenReturn(List.of(source, target));

        var result = service.targets(
            user, spaceId, source.id(), "blocks", "BUG", null, 25
        );

        assertEquals(1, result.items().size());
        assertEquals(target.id(), result.items().getFirst().id());
        assertFalse(result.truncated());
        verify(access).requireVisible(user, spaceId);
    }

    @Test
    void impactIsBoundedAndUsesOneBatchEndpointRead() {
        WorkItem source = item("TASK-1", "task", 3);
        WorkItem target = item("BUG-2", "bug", 4);
        UUID relationId = UUID.randomUUID();
        when(workItems.find(workspaceId, spaceId, source.id()))
            .thenReturn(java.util.Optional.of(source));
        when(runtime.requireForSource(source, "depends_on"))
            .thenReturn(binding(RelationKind.dependency, List.of("bug"), false));
        when(relations.listImpact(
            workspaceId, spaceId, "depends_on", source.id(), "downstream", 8, 2
        )).thenReturn(List.of(
            new ImpactEdge(relationId, source.id(), target.id(), 1),
            new ImpactEdge(UUID.randomUUID(), target.id(), source.id(), 2)
        ));
        when(workItems.findAll(
            workspaceId, spaceId, List.of(source.id(), target.id())
        )).thenReturn(List.of(source, target));

        var result = service.impact(
            user, spaceId, source.id(), "depends_on", "downstream", 8, 1
        );

        assertTrue(result.truncated());
        assertEquals("node_budget_reached", result.truncationReason());
        assertEquals(1, result.links().size());
        assertEquals(2, result.nodes().size());
    }

    private WorkItem item(String displayKey, String typeKey, long version) {
        UUID actor = UUID.randomUUID();
        return new WorkItem(
            UUID.randomUUID(), workspaceId, spaceId, UUID.randomUUID(),
            UUID.randomUUID(), typeKey, typeKey, "a".repeat(64), 1,
            displayKey, displayKey + " title", new ObjectMapper().createObjectNode(),
            "active", version, actor, Instant.now(), actor, Instant.now(), null
        );
    }

    private RelationDefinitionBinding binding(
        RelationKind kind, List<String> targets, boolean allowSelf
    ) {
        return new RelationDefinitionBinding(
            UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64),
            kind == RelationKind.blocking ? "blocks" : "depends_on",
            kind, Direction.directed, "Forward", "Reverse",
            List.of("task"), targets, Cardinality.many, Cardinality.many,
            DeletionPolicy.detach, allowSelf, 16, 0
        );
    }
}
