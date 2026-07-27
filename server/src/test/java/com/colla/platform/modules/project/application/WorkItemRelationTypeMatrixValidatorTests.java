package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemRelationTypeMatrixValidatorTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsEndpointTypesThatDoNotResolveInsideTheOwningSpace() {
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        WorkItemTypeRepository repository = mock(WorkItemTypeRepository.class);
        WorkItemTypeDefinition task = type(
            typeId, workspaceId, spaceId, "task"
        );
        when(repository.findById(workspaceId, spaceId, typeId)).thenReturn(Optional.of(task));
        when(repository.listBySpace(workspaceId, spaceId, "")).thenReturn(List.of(task));
        var snapshot = objectMapper.createObjectNode();
        snapshot.put("snapshotSchemaVersion", 4);
        snapshot.putObject("typeDefinition")
            .put("typeKey", "task")
            .put("workspaceId", workspaceId.toString())
            .put("spaceId", spaceId.toString());
        var relation = snapshot.putArray("relationDefinitions").addObject();
        relation.putArray("sourceTypeKeys").add("task");
        relation.putArray("targetTypeKeys").add("other_space_type");

        var diagnostics = new WorkItemRelationTypeMatrixValidator(repository).validate(
            workspaceId, spaceId, typeId, snapshot
        );

        assertEquals(1, diagnostics.size());
        assertEquals("relation_type_key_not_in_space", diagnostics.getFirst().code());
    }

    @Test
    void acceptsOnlyResolvedSameSpaceTypeKeys() {
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        WorkItemTypeDefinition task = type(taskId, workspaceId, spaceId, "task");
        WorkItemTypeDefinition bug = type(UUID.randomUUID(), workspaceId, spaceId, "bug");
        WorkItemTypeRepository repository = mock(WorkItemTypeRepository.class);
        when(repository.findById(workspaceId, spaceId, taskId)).thenReturn(Optional.of(task));
        when(repository.listBySpace(workspaceId, spaceId, "")).thenReturn(List.of(task, bug));
        var snapshot = objectMapper.createObjectNode();
        snapshot.put("snapshotSchemaVersion", 4);
        snapshot.putObject("typeDefinition")
            .put("typeKey", "task")
            .put("workspaceId", workspaceId.toString())
            .put("spaceId", spaceId.toString());
        var relation = snapshot.putArray("relationDefinitions").addObject();
        relation.putArray("sourceTypeKeys").add("task");
        relation.putArray("targetTypeKeys").add("bug");

        assertTrue(new WorkItemRelationTypeMatrixValidator(repository).validate(
            workspaceId, spaceId, taskId, snapshot
        ).isEmpty());
    }

    private WorkItemTypeDefinition type(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String typeKey
    ) {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        return new WorkItemTypeDefinition(
            id, workspaceId, spaceId, typeKey, typeKey, "task", "", 0, "active", false,
            UUID.randomUUID(), 1, "published", "0".repeat(64), objectMapper.createObjectNode(),
            actorId, now, actorId, now, 0
        );
    }
}
