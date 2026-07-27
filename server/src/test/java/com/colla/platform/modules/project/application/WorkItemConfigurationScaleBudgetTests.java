package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldOptionRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationScaleBudgetTests {
    private static final Duration BUDGET = Duration.ofSeconds(3);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assemblesAndHashes120FieldsAnd2400OptionsWithoutNPlusOne() {
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        WorkItemTypeRepository types = mock(WorkItemTypeRepository.class);
        WorkItemFieldRepository fields = mock(WorkItemFieldRepository.class);
        WorkItemFieldOptionRepository options = mock(WorkItemFieldOptionRepository.class);
        WorkItemLayoutRepository layouts = mock(WorkItemLayoutRepository.class);
        when(types.findById(workspaceId, spaceId, typeId)).thenReturn(Optional.of(
            new WorkItemTypeDefinition(
                typeId, workspaceId, spaceId, "scale", "Scale", "task", "",
                0, "active", false, UUID.randomUUID(), 1, "published", "0".repeat(64),
                objectMapper.createObjectNode(), actorId, now, actorId, now, 0
            )
        ));
        List<FieldDefinition> fieldValues = new ArrayList<>();
        List<FieldOption> optionValues = new ArrayList<>();
        for (int fieldIndex = 0; fieldIndex < 120; fieldIndex++) {
            UUID fieldId = UUID.nameUUIDFromBytes(("field-" + fieldIndex).getBytes());
            ObjectNode config = objectMapper.createObjectNode().put("required", false);
            fieldValues.add(new FieldDefinition(
                fieldId, workspaceId, spaceId, typeId, "field_" + fieldIndex,
                "Field " + fieldIndex, "", "single_select", config,
                "1".repeat(64), fieldIndex, "active", false,
                actorId, now, actorId, now, 0
            ));
            for (int optionIndex = 0; optionIndex < 20; optionIndex++) {
                optionValues.add(new FieldOption(
                    UUID.nameUUIDFromBytes(("option-" + fieldIndex + "-" + optionIndex).getBytes()),
                    workspaceId, spaceId, typeId, fieldId,
                    "option_" + optionIndex, "Option " + optionIndex,
                    "#1677FF", optionIndex, "active", actorId, now, actorId, now
                ));
            }
        }
        when(fields.listByType(workspaceId, spaceId, typeId, "")).thenReturn(fieldValues);
        when(options.listByType(workspaceId, spaceId, typeId)).thenReturn(optionValues);
        when(layouts.findByKind(workspaceId, spaceId, typeId, "create")).thenReturn(Optional.empty());
        when(layouts.findByKind(workspaceId, spaceId, typeId, "detail")).thenReturn(Optional.empty());
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var assembler = new WorkItemConfigurationSnapshotAssembler(
            types, fields, options, layouts, new WorkItemStateFlowPresetCatalog(objectMapper),
            new WorkItemNodeFlowPresetCatalog(objectMapper),
            new WorkItemRelationDefinitionPresetCatalog(objectMapper),
            new WorkItemPermissionPresetCatalog(objectMapper),
            canonicalizer, objectMapper
        );

        long started = System.nanoTime();
        var snapshot = assembler.assemble(workspaceId, spaceId, typeId);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(elapsed.compareTo(BUDGET) < 0, "snapshot assembly exceeded " + BUDGET);
        assertEquals(120, snapshot.payload().path("fields").size());
        assertEquals(2400, snapshot.payload().path("fields").valueStream()
            .mapToInt(field -> field.path("options").size()).sum());
        assertEquals(64, snapshot.configHash().length());
        assertEquals(
            snapshot.configHash(),
            canonicalizer.canonicalize(snapshot.payload().deepCopy()).configHash()
        );
        verify(types, times(1)).findById(workspaceId, spaceId, typeId);
        verify(fields, times(1)).listByType(workspaceId, spaceId, typeId, "");
        verify(options, times(1)).listByType(workspaceId, spaceId, typeId);
        verify(layouts, times(1)).findByKind(workspaceId, spaceId, typeId, "create");
        verify(layouts, times(1)).findByKind(workspaceId, spaceId, typeId, "detail");
    }

    @Test
    void analyzesAndThreeWayMergesLargeSnapshotsWithinBudget() {
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var base = largeSnapshot();
        var upstream = base.deepCopy();
        var local = base.deepCopy();
        ((ObjectNode) upstream.path("fields").get(0).path("options").get(0))
            .put("name", "Upstream option");
        ((ObjectNode) local.path("fields").get(1)).put("name", "Local field");
        String baseHash = canonicalizer.canonicalize(base).configHash();
        String upstreamHash = canonicalizer.canonicalize(upstream).configHash();
        var analyzer = new WorkItemConfigurationCompatibilityAnalyzer();
        var merge = new WorkItemConfigurationThreeWayMerge(objectMapper, canonicalizer);

        long started = System.nanoTime();
        var report = analyzer.analyze(baseHash, base, upstreamHash, upstream);
        var merged = merge.merge(base, upstream, local, Map.of());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(elapsed.compareTo(BUDGET) < 0, "analysis and merge exceeded " + BUDGET);
        assertEquals(1, report.findings().size());
        assertEquals(
            "$.fields[field_0].options[option_0].name",
            report.findings().getFirst().keyPath()
        );
        assertTrue(merged.conflicts().isEmpty());
        assertEquals(
            "Upstream option",
            merged.snapshot().path("fields").get(0).path("options").get(0).path("name").asText()
        );
        assertEquals("Local field", merged.snapshot().path("fields").get(1).path("name").asText());
    }

    private ObjectNode largeSnapshot() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotSchemaVersion", 1);
        root.putObject("typeDefinition").put("typeKey", "scale");
        var fields = root.putArray("fields");
        for (int fieldIndex = 0; fieldIndex < 120; fieldIndex++) {
            ObjectNode field = fields.addObject();
            field.put("id", "field-id-" + fieldIndex);
            field.put("fieldKey", "field_" + fieldIndex);
            field.put("name", "Field " + fieldIndex);
            field.put("fieldType", "single_select");
            field.putObject("config").put("required", false);
            field.put("sortOrder", fieldIndex);
            field.put("status", "active");
            field.put("system", false);
            var options = field.putArray("options");
            for (int optionIndex = 0; optionIndex < 20; optionIndex++) {
                options.addObject()
                    .put("id", "option-id-" + fieldIndex + "-" + optionIndex)
                    .put("optionKey", "option_" + optionIndex)
                    .put("name", "Option " + optionIndex)
                    .put("color", "#1677FF")
                    .put("sortOrder", optionIndex)
                    .put("status", "active");
            }
        }
        root.putArray("layouts");
        return root;
    }
}
