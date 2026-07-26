package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldOptionRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationSnapshotAssemblerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assemblesTypeFieldsOptionsAndEmptyLayoutEnvelope() {
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID fieldId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-26T00:00:00Z");

        WorkItemTypeRepository types = mock(WorkItemTypeRepository.class);
        WorkItemFieldRepository fields = mock(WorkItemFieldRepository.class);
        WorkItemFieldOptionRepository options = mock(WorkItemFieldOptionRepository.class);
        WorkItemLayoutRepository layouts = mock(WorkItemLayoutRepository.class);
        when(types.findById(workspaceId, spaceId, typeId)).thenReturn(Optional.of(new WorkItemTypeDefinition(
            typeId, workspaceId, spaceId, "task", "Task", "check", "Task type",
            10, "active", false, UUID.randomUUID(), 1, "published", "0".repeat(64),
            objectMapper.createObjectNode(), actorId, now, actorId, now, 4
        )));
        when(fields.listByType(workspaceId, spaceId, typeId, "")).thenReturn(List.of(new FieldDefinition(
            fieldId, workspaceId, spaceId, typeId, "priority", "Priority", "",
            "single_select", objectMapper.createObjectNode(), "1".repeat(64), 20,
            "active", false, actorId, now, actorId, now, 2
        )));
        when(options.listByType(workspaceId, spaceId, typeId)).thenReturn(List.of(new FieldOption(
            optionId, workspaceId, spaceId, typeId, fieldId, "high", "High",
            "#FF0000", 10, "active", actorId, now, actorId, now
        )));
        when(layouts.findByKind(workspaceId, spaceId, typeId, "create")).thenReturn(Optional.empty());
        when(layouts.findByKind(workspaceId, spaceId, typeId, "detail")).thenReturn(Optional.empty());

        var snapshot = new WorkItemConfigurationSnapshotAssembler(
            types,
            fields,
            options,
            layouts,
            new WorkItemStateFlowPresetCatalog(objectMapper),
            new WorkItemConfigurationSnapshotCanonicalizer(objectMapper),
            objectMapper
        ).assemble(workspaceId, spaceId, typeId);

        assertEquals(2, snapshot.schemaVersion());
        assertEquals("task", snapshot.payload().path("typeDefinition").path("typeKey").asText());
        assertEquals("priority", snapshot.payload().path("fields").get(0).path("fieldKey").asText());
        assertEquals("high", snapshot.payload().path("fields").get(0).path("options").get(0).path("optionKey").asText());
        assertEquals(0, snapshot.payload().path("layouts").size());
    }
}
