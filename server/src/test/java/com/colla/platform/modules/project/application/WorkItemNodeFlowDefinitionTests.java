package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityImpact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

class WorkItemNodeFlowDefinitionTests {
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
        new WorkItemRelationDefinitionValidator(),
        new WorkItemPermissionDefinitionValidator()
    );
    private final WorkItemNodeFlowPresetCatalog presets = new WorkItemNodeFlowPresetCatalog(objectMapper);

    @Test
    void deterministicProjectAndReleasePresetsAreValidAndUnknownTypesAreNotGuessed() {
        for (String typeKey : new String[]{"project", "release"}) {
            ObjectNode snapshot = baseSnapshot(3, typeKey);
            snapshot.set("nodeFlow", presets.nodeFlowFor(typeKey).orElseThrow());

            var validation = validator.validate(snapshot);

            assertTrue(validation.valid(), () -> typeKey + ": " + validation.diagnostics());
            assertEquals(
                canonicalizer.canonicalize(snapshot).configHash(),
                canonicalizer.canonicalize(snapshot.deepCopy()).configHash()
            );
        }
        assertTrue(presets.nodeFlowFor("project").isPresent());
        assertTrue(presets.nodeFlowFor("release").isPresent());
        assertTrue(presets.nodeFlowFor("custom").isEmpty());
    }

    @Test
    void nodeFlowRequiresSchemaV3AndCannotShareRuntimeAuthorityWithStateFlow() {
        ObjectNode v2 = baseSnapshot(2, "project");
        v2.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow());
        var v2Validation = validator.validate(v2);
        assertFalse(v2Validation.valid());
        assertTrue(v2Validation.diagnostics().stream()
            .anyMatch(value -> value.code().equals("node_flow_requires_schema_v3")));

        ObjectNode dual = baseSnapshot(3, "project");
        dual.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow());
        dual.set("stateFlow", new WorkItemStateFlowPresetCatalog(objectMapper)
            .stateFlowFor("task").orElseThrow());
        var dualValidation = validator.validate(dual);
        assertFalse(dualValidation.valid());
        assertTrue(dualValidation.diagnostics().stream()
            .anyMatch(value -> value.code().equals("multiple_workflow_authorities")));
    }

    @Test
    void rejectsUnknownConditionOperatorsCyclesAndBrokenJoinReferences() {
        ObjectNode condition = baseSnapshot(3, "project");
        condition.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        ((ObjectNode) condition.path("nodeFlow").path("edges").get(0)).set(
            "condition",
            objectMapper.createObjectNode().put("operator", "execute_sql").put("fieldKey", "title")
        );
        assertTrue(validator.validate(condition).diagnostics().stream()
            .anyMatch(value -> value.code().equals("invalid_branch_condition")));

        ObjectNode cycle = baseSnapshot(3, "project");
        cycle.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        var edge = ((ObjectNode) cycle.path("nodeFlow")).withArray("edges").addObject();
        edge.put("edgeKey", "completed_plan");
        edge.put("fromNodeKey", "completed");
        edge.put("toNodeKey", "plan");
        edge.put("priority", 100);
        edge.putNull("condition");
        assertTrue(validator.validate(cycle).diagnostics().stream()
            .anyMatch(value -> value.code().equals("node_flow_cycle")));

        ObjectNode join = baseSnapshot(3, "project");
        join.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        ((ObjectNode) join.path("nodeFlow").path("joins").get(0))
            .withArray("inboundEdgeKeys").set(0, "start_plan");
        assertTrue(validator.validate(join).diagnostics().stream()
            .anyMatch(value -> value.code().equals("invalid_join_edge_reference")));
    }

    @Test
    void canonicalizerAndDiffUseSemanticKeysForNodeGraphCollections() {
        ObjectNode first = baseSnapshot(3, "project");
        first.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        ObjectNode second = first.deepCopy();
        second.withObject("/nodeFlow").withArray("nodes")
            .insert(0, second.withObject("/nodeFlow").withArray("nodes").remove(7));
        second.withObject("/nodeFlow").withArray("edges")
            .insert(0, second.withObject("/nodeFlow").withArray("edges").remove(7));

        var left = canonicalizer.canonicalize(first);
        var right = canonicalizer.canonicalize(second);

        assertEquals(left.payload(), right.payload());
        assertEquals(left.configHash(), right.configHash());
        assertTrue(new WorkItemConfigurationDiffEngine()
            .diff(left.configHash(), left.payload(), right.configHash(), right.payload())
            .items().isEmpty());
    }

    @Test
    void compatibilityRequiresExplicitMigrationForNodeFlowAndNodeRemoval() {
        ObjectNode before = baseSnapshot(2, "project");
        ObjectNode after = baseSnapshot(3, "project");
        after.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        var analyzer = new WorkItemConfigurationCompatibilityAnalyzer();

        var addition = analyzer.analyze("before", before, "after", after);
        assertEquals(CompatibilityImpact.migration_required, addition.overallImpact());
        assertTrue(addition.findings().stream().anyMatch(value -> value.reasonCode().equals("node_flow_added")));

        ObjectNode removed = after.deepCopy();
        removed.withObject("/nodeFlow").withArray("nodes").remove(1);
        var removal = analyzer.analyze("after", after, "removed", removed);
        assertEquals(CompatibilityImpact.migration_required, removal.overallImpact());
        assertTrue(removal.findings().stream().anyMatch(value -> value.reasonCode().equals("node_removed")));
    }

    @Test
    void collaborationConfigurationFailsClosedForUnknownFieldsAndDynamicRules() {
        ObjectNode snapshot = baseSnapshot(3, "project");
        snapshot.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        ObjectNode manual = null;
        for (var node : snapshot.path("nodeFlow").path("nodes")) {
            if ("manual".equals(node.path("kind").asText())) {
                manual = (ObjectNode) node;
                break;
            }
        }
        ObjectNode configuration = manual.withObject("/configuration");
        configuration.putObject("form").putArray("fields").addObject()
            .put("fieldKey", "secret")
            .put("mode", "write")
            .put("required", true)
            .put("sortOrder", 0);
        configuration.putObject("assignment").put("script", "return everyone");

        var diagnostics = validator.validate(snapshot).diagnostics();

        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("invalid_node_form_field")));
        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("unsupported_assignment_rule")));
    }

    @Test
    void canonicalizerStabilizesControlledAssignmentSources() {
        ObjectNode first = baseSnapshot(3, "project");
        first.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        ObjectNode manual = null;
        for (var node : first.path("nodeFlow").path("nodes")) {
            if ("manual".equals(node.path("kind").asText())) {
                manual = (ObjectNode) node;
                break;
            }
        }
        manual.withObject("/configuration").putObject("assignment")
            .putArray("explicitUserIds")
            .add("00000000-0000-0000-0000-000000000009")
            .add("00000000-0000-0000-0000-000000000008");
        ObjectNode second = first.deepCopy();
        var users = ((ObjectNode) second.path("nodeFlow").path("nodes").get(1))
            .withObject("/configuration/assignment").withArray("explicitUserIds");
        users.insert(0, users.remove(1));

        assertEquals(
            canonicalizer.canonicalize(first).configHash(),
            canonicalizer.canonicalize(second).configHash()
        );
    }

    @Test
    void recoveryAndCompensationAreBoundedDeclarativeAndCanonical() {
        ObjectNode valid = baseSnapshot(3, "project");
        valid.set("nodeFlow", presets.nodeFlowFor("project").orElseThrow().deepCopy());
        assertTrue(validator.validate(valid).valid());

        ObjectNode reordered = valid.deepCopy();
        ArrayNode sources = (ArrayNode) reordered.withObject("/nodeFlow")
            .withArray("recoveryCommands").get(0).path("fromNodeKeys");
        sources.insert(0, sources.remove(sources.size() - 1));
        assertEquals(
            canonicalizer.canonicalize(valid).configHash(),
            canonicalizer.canonicalize(reordered).configHash()
        );

        ObjectNode arbitraryAction = valid.deepCopy();
        ((ObjectNode) arbitraryAction.path("nodeFlow").path("compensations").get(0))
            .put("actionKey", "execute_sql");
        assertTrue(validator.validate(arbitraryAction).diagnostics().stream()
            .anyMatch(value -> value.code().equals("unknown_compensation_action")));

        ObjectNode arbitraryTarget = valid.deepCopy();
        ((ObjectNode) arbitraryTarget.path("nodeFlow").path("recoveryCommands").get(0))
            .put("targetNodeKey", "completed");
        assertTrue(validator.validate(arbitraryTarget).diagnostics().stream()
            .anyMatch(value -> value.code().equals("invalid_recovery_target")));
    }

    private ObjectNode baseSnapshot(int schemaVersion, String typeKey) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotSchemaVersion", schemaVersion);
        ObjectNode type = root.putObject("typeDefinition");
        type.put("typeKey", typeKey);
        type.put("workspaceId", "00000000-0000-0000-0000-000000000001");
        type.put("spaceId", "00000000-0000-0000-0000-000000000002");
        root.putArray("fields");
        root.putArray("layouts");
        return root;
    }
}
