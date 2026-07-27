package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityImpact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class WorkItemRelationDefinitionTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer =
        new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
    private final WorkItemConfigurationValidator validator = new WorkItemConfigurationValidator(
        canonicalizer,
        new WorkItemStateFlowValidator(
            new WorkItemStateFlowGuardRegistry(),
            new WorkItemStateFlowSideEffectRegistry()
        ),
        new WorkItemNodeFlowValidator(new WorkItemNodeTypeRegistry()),
        new WorkItemRelationDefinitionValidator()
    );
    private final WorkItemRelationDefinitionPresetCatalog presets =
        new WorkItemRelationDefinitionPresetCatalog(objectMapper);

    @Test
    void deterministicSystemPresetsAreValidAndCanonicallyOrdered() {
        for (String typeKey : new String[]{
            "project", "requirement", "task", "bug", "iteration", "release"
        }) {
            ObjectNode snapshot = baseSnapshot(typeKey);
            snapshot.set("relationDefinitions", presets.definitionsFor(typeKey).orElseThrow());

            var result = validator.validate(snapshot);

            assertTrue(result.valid(), () -> typeKey + ": " + result.diagnostics());
            var canonical = canonicalizer.canonicalize(snapshot);
            assertEquals("relates_to",
                canonical.payload().path("relationDefinitions").get(0).path("relationKey").asText());
            assertEquals(canonical.configHash(),
                canonicalizer.canonicalize(snapshot.deepCopy()).configHash());
        }
        assertTrue(presets.definitionsFor("custom").isEmpty());
    }

    @Test
    void rejectsUnboundUndirectedStructuralAndDuplicateTypeContracts() {
        ObjectNode snapshot = baseSnapshot("task");
        ArrayNode definitions = snapshot.putArray("relationDefinitions");
        ObjectNode relation = definitions.addObject();
        relation.put("relationKey", "parent_child");
        relation.put("kind", "parent_child");
        relation.put("direction", "undirected");
        relation.put("forwardName", "Parent");
        relation.put("reverseName", "Child");
        relation.putArray("sourceTypeKeys").add("bug").add("bug");
        relation.putArray("targetTypeKeys").add("task");
        relation.put("sourceCardinality", "many");
        relation.put("targetCardinality", "one");
        relation.put("deletionPolicy", "restrict");
        relation.put("allowSelf", true);
        relation.put("maxDepth", 65);
        relation.put("sortOrder", 0);

        var result = validator.validate(snapshot);

        assertFalse(result.valid());
        assertTrue(result.diagnostics().stream()
            .anyMatch(value -> "structural_relation_must_be_directed".equals(value.code())));
        assertTrue(result.diagnostics().stream()
            .anyMatch(value -> "relation_source_type_unbound".equals(value.code())));
        assertTrue(result.diagnostics().stream()
            .anyMatch(value -> "invalid_relation_max_depth".equals(value.code())));
    }

    @Test
    void compatibilityBlocksSemanticMutationAndRequiresMigrationForConstraintChange() {
        ObjectNode before = baseSnapshot("task");
        before.set("relationDefinitions", presets.definitionsFor("task").orElseThrow());
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("relationDefinitions").get(0)).put("direction", "directed");
        ((ObjectNode) after.path("relationDefinitions").get(1)).put("maxDepth", 32);

        var report = new WorkItemConfigurationCompatibilityAnalyzer().analyze(
            canonicalizer.canonicalize(before).configHash(),
            before,
            canonicalizer.canonicalize(after).configHash(),
            after
        );

        assertEquals(CompatibilityImpact.blocked, report.overallImpact());
        assertTrue(report.findings().stream()
            .anyMatch(value -> "relation_semantics_changed".equals(value.reasonCode())));
        assertTrue(report.findings().stream()
            .anyMatch(value -> "relation_constraint_changed".equals(value.reasonCode())));
    }

    private ObjectNode baseSnapshot(String typeKey) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotSchemaVersion", 4);
        root.putObject("typeDefinition").put("typeKey", typeKey);
        root.putArray("fields");
        root.putArray("layouts");
        return root;
    }
}
