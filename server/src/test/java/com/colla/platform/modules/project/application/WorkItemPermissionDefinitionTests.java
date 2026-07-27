package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityImpact;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class WorkItemPermissionDefinitionTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemPermissionPresetCatalog presets = new WorkItemPermissionPresetCatalog(objectMapper);
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

    @Test
    void deterministicPresetIsValidAndDoesNotMapEnterpriseAdminToContentOwner() {
        ObjectNode first = baseSnapshot();
        first.set("permissionModel", presets.modelFor("task"));
        ObjectNode second = baseSnapshot();
        second.set("permissionModel", presets.modelFor("task"));

        ValidationResult result = validator.validate(first);
        assertTrue(result.valid(), () -> result.diagnostics().toString());
        assertEquals(
            canonicalizer.canonicalize(first).configHash(),
            canonicalizer.canonicalize(second).configHash()
        );
        assertEquals(4, first.path("permissionModel").path("spaceRoleDefinitions").size());
        assertEquals(4, first.path("permissionModel").path("workItemRoleDefinitions").size());
        assertFalse(first.path("permissionModel").path("legacyMappings").toString()
            .contains("enterprise_role"));
    }

    @Test
    void rejectsRoleCyclesUnknownActionsAndEnterpriseOwnerMapping() {
        ObjectNode snapshot = baseSnapshot();
        ObjectNode model = (ObjectNode) presets.modelFor("task");
        ((ObjectNode) model.withArray("spaceRoleDefinitions").get(0))
            .withArray("inheritedRoleKeys").add("owner");
        ((ObjectNode) model.withArray("permissionPolicies").get(0))
            .withArray("actionKeys").add("read_private_table");
        ObjectNode mapping = model.withArray("legacyMappings").addObject();
        mapping.put("sourceKind", "enterprise_role");
        mapping.put("sourceKey", "enterprise_admin");
        mapping.put("disposition", "map");
        mapping.put("targetLayer", "space_role");
        mapping.put("targetKey", "owner");
        snapshot.set("permissionModel", model);

        ValidationResult result = validator.validate(snapshot);
        assertFalse(result.valid());
        assertTrue(result.diagnostics().stream()
            .anyMatch(value -> value.code().equals("space_role_inheritance_cycle")));
        assertTrue(result.diagnostics().stream()
            .anyMatch(value -> value.code().equals("duplicate_or_unsupported_value")));
        assertTrue(result.diagnostics().stream()
            .anyMatch(value -> value.code().equals("enterprise_role_cannot_map_content_owner")));
    }

    @Test
    void classifiesPermissionTighteningAsMigrationRequired() {
        ObjectNode before = baseSnapshot();
        before.set("permissionModel", presets.modelFor("task"));
        ObjectNode after = before.deepCopy();
        ((ObjectNode) after.path("permissionModel").path("permissionPolicies").get(0))
            .put("effect", "deny");

        var report = new WorkItemConfigurationCompatibilityAnalyzer().analyze(
            "a".repeat(64), before, "b".repeat(64), after
        );
        assertEquals(CompatibilityImpact.migration_required, report.overallImpact());
        assertTrue(report.findings().stream()
            .anyMatch(value -> value.reasonCode().equals("permission_decision_contract_changed")));
    }

    private ObjectNode baseSnapshot() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotSchemaVersion", 5);
        ObjectNode type = root.putObject("typeDefinition");
        type.put("typeKey", "task");
        type.put("workspaceId", "00000000-0000-0000-0000-000000000001");
        type.put("spaceId", "00000000-0000-0000-0000-000000000002");
        root.putArray("fields");
        root.putArray("layouts");
        root.putArray("relationDefinitions");
        return root;
    }
}
