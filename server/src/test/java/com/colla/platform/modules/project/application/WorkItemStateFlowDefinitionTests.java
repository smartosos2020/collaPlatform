package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemTypePresetCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkItemStateFlowDefinitionTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer =
        new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
    private final WorkItemConfigurationValidator validator = new WorkItemConfigurationValidator(
        canonicalizer,
        new WorkItemStateFlowValidator(
            new WorkItemStateFlowGuardRegistry(),
            new WorkItemStateFlowSideEffectRegistry()
        )
    );
    private final WorkItemStateFlowPresetCatalog presets =
        new WorkItemStateFlowPresetCatalog(objectMapper);

    @Test
    void deterministicPresetFlowsAreValidAndDoNotGuessUnknownTypes() {
        WorkItemTypePresetCatalog typeCatalog = new WorkItemTypePresetCatalog();
        for (var preset : typeCatalog.developmentPresets()) {
            ObjectNode snapshot = baseSnapshot();
            snapshot.set("stateFlow", presets.stateFlowFor(preset).orElseThrow());

            var result = validator.validate(snapshot);
            assertTrue(result.valid(), () -> preset.typeKey() + ": " + result.diagnostics());
            assertEquals(
                canonicalizer.canonicalize(snapshot).configHash(),
                canonicalizer.canonicalize(snapshot.deepCopy()).configHash()
            );
        }
        assertTrue(presets.stateFlowFor("custom").isEmpty());
        assertNotEquals(
            presets.stateFlowFor("bug").orElseThrow(),
            presets.stateFlowFor("task").orElseThrow()
        );
    }

    @Test
    void rejectsDuplicateInitialUnreachableDeadEndAndDanglingDefinitionsDeterministically() {
        ObjectNode snapshot = baseSnapshot();
        ObjectNode flow = (ObjectNode) presets.stateFlowFor("task").orElseThrow().deepCopy();
        ((ObjectNode) flow.path("states").get(1)).put("category", "initial");
        ((ArrayNode) flow.path("transitions")).removeAll();
        ObjectNode dangling = ((ArrayNode) flow.path("transitions")).addObject();
        dangling.put("transitionKey", "dangling");
        dangling.put("actionKey", "missing_action");
        dangling.put("fromStateKey", "missing_state");
        dangling.put("toStateKey", "also_missing");
        dangling.put("guardKey", "missing_guard");
        dangling.put("sortOrder", 1);
        snapshot.set("stateFlow", flow);

        var first = validator.validate(snapshot);
        var second = validator.validate(snapshot.deepCopy());

        assertFalse(first.valid());
        assertEquals(first.diagnostics(), second.diagnostics());
        assertTrue(first.diagnostics().stream().anyMatch(value ->
            "initial_state_count_invalid".equals(value.code())
        ));
        assertTrue(first.diagnostics().stream().anyMatch(value ->
            "unreachable_state".equals(value.code())
        ));
        assertTrue(first.diagnostics().stream().anyMatch(value ->
            "dangling_transition_action".equals(value.code())
        ));
        assertTrue(first.diagnostics().stream().anyMatch(value ->
            "dangling_transition_guard".equals(value.code())
        ));
    }

    @Test
    void guardAndActionContractsFailClosedForHiddenFieldsUnknownOperatorsAndSideEffects() throws Exception {
        ObjectNode snapshot = baseSnapshot();
        ArrayNode fields = (ArrayNode) snapshot.path("fields");
        ObjectNode field = fields.addObject();
        field.put("fieldKey", "secret");
        field.put("fieldType", "text");
        field.put("status", "active");
        field.put("sortOrder", 1);
        field.set("config", objectMapper.createObjectNode());
        field.putArray("options");
        ObjectNode detail = (ObjectNode) snapshot.path("layouts").get(1);
        ObjectNode policy = detail.withArray("policies").addObject();
        policy.put("fieldKey", "secret");
        policy.put("policyKey", "default");
        policy.set("policy", objectMapper.readTree("{\"effect\":\"hidden\"}"));

        ObjectNode flow = (ObjectNode) presets.stateFlowFor("task").orElseThrow().deepCopy();
        ObjectNode guard = flow.withArray("guards").addObject();
        guard.put("guardKey", "secret_guard");
        guard.put("kind", "field");
        guard.put("operator", "execute_sql");
        guard.put("fieldKey", "secret");
        guard.put("value", "x");
        ObjectNode action = (ObjectNode) flow.path("actions").get(0);
        action.withArray("requiredFieldKeys").add("secret");
        action.withArray("sideEffectKeys").add("network_call");
        ((ObjectNode) flow.path("transitions").get(0)).put("guardKey", "secret_guard");
        snapshot.set("stateFlow", flow);

        var result = validator.validate(snapshot);

        assertFalse(result.valid());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
            "unknown_guard_operator".equals(value.code())
        ));
        assertTrue(result.diagnostics().stream().anyMatch(value ->
            "invalid_required_field".equals(value.code())
        ));
        assertTrue(result.diagnostics().stream().anyMatch(value ->
            "unknown_side_effect".equals(value.code())
        ));
    }

    @Test
    void schemaV1RemainsValidWithoutFlowButCannotCarryStateFlow() {
        ObjectNode legacy = baseSnapshot();
        legacy.put("snapshotSchemaVersion", 1);
        assertTrue(validator.validate(legacy).valid());

        legacy.set("stateFlow", presets.stateFlowFor("task").orElseThrow());
        var result = validator.validate(legacy);
        assertFalse(result.valid());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
            "state_flow_requires_schema_v2".equals(value.code())
        ));
    }

    private ObjectNode baseSnapshot() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotSchemaVersion", 2);
        ObjectNode type = root.putObject("typeDefinition");
        type.put("typeKey", "task");
        type.put("workspaceId", "00000000-0000-0000-0000-000000000001");
        type.put("spaceId", "00000000-0000-0000-0000-000000000002");
        root.putArray("fields");
        ArrayNode layouts = root.putArray("layouts");
        for (String kind : List.of("create", "detail")) {
            ObjectNode layout = layouts.addObject();
            layout.put("layoutKind", kind);
            layout.putArray("nodes");
            layout.putArray("policies");
        }
        return root;
    }
}
