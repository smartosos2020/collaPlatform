package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.ACTION_KEYS;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.MAX_PERMISSION_POLICIES;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.MAX_POLICY_ACTIONS;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.MAX_ROLE_INHERITANCE_DEPTH;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.MAX_SCOPE_VALUES;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.MAX_SPACE_ROLES;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.MAX_SUBJECT_SELECTORS;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.MAX_WORK_ITEM_ROLES;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.SEMANTIC_KEY;
import static com.colla.platform.modules.project.domain.WorkItemPermissionModels.SOURCE_KINDS;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiagnosticSeverity;
import com.colla.platform.modules.project.domain.WorkItemPermissionModels.DataScopeKind;
import com.colla.platform.modules.project.domain.WorkItemPermissionModels.PolicyEffect;
import com.colla.platform.modules.project.domain.WorkItemPermissionModels.SubjectKind;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemPermissionDefinitionValidator {
    public void validate(
        JsonNode model,
        String boundTypeKey,
        Set<String> fieldKeys,
        Set<String> nodeKeys,
        Set<String> relationKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!model.isObject() || model.path("schemaVersion").asInt(-1) != 1) {
            error(diagnostics, "invalid_permission_model", "$.permissionModel",
                "Permission model must be an object using schema version 1");
            return;
        }
        if (!boundTypeKey.equals(model.path("boundTypeKey").asText(""))) {
            error(diagnostics, "permission_model_type_mismatch", "$.permissionModel.boundTypeKey",
                "Permission model must be bound to the owning type semantic key");
        }
        if (!model.path("denyOverridesAllow").asBoolean(false)) {
            error(diagnostics, "permission_deny_precedence_required", "$.permissionModel.denyOverridesAllow",
                "Permission models must declare deny-overrides-allow semantics");
        }
        Map<String, Set<String>> inheritance = validateSpaceRoles(
            model.path("spaceRoleDefinitions"), diagnostics
        );
        validateInheritance(inheritance, diagnostics);
        Set<String> workItemRoles = validateWorkItemRoles(
            model.path("workItemRoleDefinitions"), diagnostics
        );
        validatePolicies(
            model.path("permissionPolicies"),
            inheritance.keySet(),
            workItemRoles,
            fieldKeys,
            nodeKeys,
            relationKeys,
            diagnostics
        );
        validateLegacyMappings(model.path("legacyMappings"), inheritance.keySet(), workItemRoles, diagnostics);
    }

    private Map<String, Set<String>> validateSpaceRoles(
        JsonNode roles,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Map<String, Set<String>> result = new HashMap<>();
        if (!roles.isArray() || roles.isEmpty() || roles.size() > MAX_SPACE_ROLES) {
            error(diagnostics, "invalid_space_role_definitions", "$.permissionModel.spaceRoleDefinitions",
                "Space role definitions must be a non-empty bounded array");
            return result;
        }
        for (int index = 0; index < roles.size(); index++) {
            JsonNode role = roles.get(index);
            String path = "$.permissionModel.spaceRoleDefinitions[" + index + "]";
            String key = role.path("roleKey").asText("");
            if (!SEMANTIC_KEY.matcher(key).matches() || result.containsKey(key)) {
                error(diagnostics, "duplicate_or_invalid_space_role_key", path + ".roleKey",
                    "Space role keys must be stable, valid and unique");
                continue;
            }
            validateNameAndOrder(role, path, diagnostics);
            validateStringSet(role.path("actionKeys"), path + ".actionKeys", ACTION_KEYS,
                MAX_POLICY_ACTIONS, false, diagnostics);
            result.put(key, validateSemanticKeys(
                role.path("inheritedRoleKeys"), path + ".inheritedRoleKeys", MAX_SPACE_ROLES, diagnostics
            ));
        }
        if (!result.containsKey("owner")) {
            error(diagnostics, "minimum_owner_role_required", "$.permissionModel.spaceRoleDefinitions",
                "A stable owner role is required");
        }
        for (Map.Entry<String, Set<String>> entry : result.entrySet()) {
            for (String inherited : entry.getValue()) {
                if (!result.containsKey(inherited) || inherited.equals(entry.getKey())) {
                    error(diagnostics, "invalid_space_role_inheritance",
                        "$.permissionModel.spaceRoleDefinitions",
                        "Role inheritance must reference another declared role");
                }
            }
        }
        return result;
    }

    private Set<String> validateWorkItemRoles(
        JsonNode roles,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> result = new HashSet<>();
        if (!roles.isArray() || roles.isEmpty() || roles.size() > MAX_WORK_ITEM_ROLES) {
            error(diagnostics, "invalid_work_item_role_definitions",
                "$.permissionModel.workItemRoleDefinitions",
                "Work-item role definitions must be a non-empty bounded array");
            return result;
        }
        for (int index = 0; index < roles.size(); index++) {
            JsonNode role = roles.get(index);
            String path = "$.permissionModel.workItemRoleDefinitions[" + index + "]";
            String key = role.path("roleKey").asText("");
            if (!SEMANTIC_KEY.matcher(key).matches() || !result.add(key)) {
                error(diagnostics, "duplicate_or_invalid_work_item_role_key", path + ".roleKey",
                    "Work-item role keys must be stable, valid and unique");
            }
            validateNameAndOrder(role, path, diagnostics);
            validateStringSet(role.path("sourceKinds"), path + ".sourceKinds", SOURCE_KINDS,
                SOURCE_KINDS.size(), false, diagnostics);
            if (!role.path("multiple").isBoolean()) {
                error(diagnostics, "invalid_work_item_role_cardinality", path + ".multiple",
                    "Work-item role cardinality must be explicit");
            }
        }
        return result;
    }

    private void validatePolicies(
        JsonNode policies,
        Set<String> spaceRoles,
        Set<String> workItemRoles,
        Set<String> fieldKeys,
        Set<String> nodeKeys,
        Set<String> relationKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!policies.isArray() || policies.isEmpty() || policies.size() > MAX_PERMISSION_POLICIES) {
            error(diagnostics, "invalid_permission_policies", "$.permissionModel.permissionPolicies",
                "Permission policies must be a non-empty bounded array");
            return;
        }
        Set<String> keys = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        for (int index = 0; index < policies.size(); index++) {
            JsonNode policy = policies.get(index);
            String path = "$.permissionModel.permissionPolicies[" + index + "]";
            String key = policy.path("policyKey").asText("");
            if (!SEMANTIC_KEY.matcher(key).matches() || !keys.add(key)) {
                error(diagnostics, "duplicate_or_invalid_permission_policy_key", path + ".policyKey",
                    "Permission policy keys must be stable, valid and unique");
            }
            try {
                PolicyEffect.valueOf(policy.path("effect").asText(""));
            } catch (IllegalArgumentException exception) {
                error(diagnostics, "invalid_permission_policy_effect", path + ".effect",
                    "Permission policy effect must be allow or deny");
            }
            Set<String> actions = validateStringSet(policy.path("actionKeys"), path + ".actionKeys",
                ACTION_KEYS, MAX_POLICY_ACTIONS, false, diagnostics);
            JsonNode selectors = policy.path("subjectSelectors");
            if (!selectors.isArray() || selectors.isEmpty() || selectors.size() > MAX_SUBJECT_SELECTORS) {
                error(diagnostics, "invalid_permission_subject_selectors", path + ".subjectSelectors",
                    "Subject selectors must be a non-empty bounded array");
            } else {
                for (int selectorIndex = 0; selectorIndex < selectors.size(); selectorIndex++) {
                    validateSelector(
                        selectors.get(selectorIndex),
                        path + ".subjectSelectors[" + selectorIndex + "]",
                        spaceRoles,
                        workItemRoles,
                        diagnostics
                    );
                }
            }
            validateDataScope(policy.path("dataScope"), path + ".dataScope", fieldKeys, workItemRoles, diagnostics);
            validateReferences(policy.path("fieldKeys"), path + ".fieldKeys", fieldKeys, diagnostics);
            validateReferences(policy.path("nodeKeys"), path + ".nodeKeys", nodeKeys, diagnostics);
            validateReferences(policy.path("relationKeys"), path + ".relationKeys", relationKeys, diagnostics);
            int priority = policy.path("priority").asInt(-1);
            if (priority < 0 || priority > 10000) {
                error(diagnostics, "invalid_permission_policy_priority", path + ".priority",
                    "Permission policy priority must be between 0 and 10000");
            }
            String signature = policy.path("effect").asText() + "|" + priority + "|"
                + actions + "|" + selectors;
            if (!signatures.add(signature)) {
                error(diagnostics, "duplicate_permission_policy_signature", path,
                    "Equivalent policies are ambiguous and must be merged");
            }
            validateNameAndOrder(policy, path, diagnostics, false);
        }
    }

    private void validateSelector(
        JsonNode selector,
        String path,
        Set<String> spaceRoles,
        Set<String> workItemRoles,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        SubjectKind kind;
        try {
            kind = SubjectKind.valueOf(selector.path("kind").asText(""));
        } catch (IllegalArgumentException exception) {
            error(diagnostics, "invalid_permission_subject_kind", path + ".kind",
                "Subject selector kind is unsupported");
            return;
        }
        String key = selector.path("key").asText("");
        boolean hasId = selector.path("subjectId").isTextual();
        switch (kind) {
            case space_role -> requireKnownKey(key, spaceRoles, path, diagnostics);
            case work_item_role -> requireKnownKey(key, workItemRoles, path, diagnostics);
            case enterprise_role, participant_role -> requireSemanticKey(key, path, diagnostics);
            case user, department, user_group -> {
                if (!hasId || !uuid(selector.path("subjectId").asText())) {
                    error(diagnostics, "invalid_permission_subject_id", path + ".subjectId",
                        "Concrete identity selectors require a UUID subjectId");
                }
            }
            case everyone -> {
                if (!key.isBlank() || hasId) {
                    error(diagnostics, "invalid_everyone_selector", path,
                        "The everyone selector cannot carry a key or subject id");
                }
            }
        }
    }

    private void validateDataScope(
        JsonNode scope,
        String path,
        Set<String> fieldKeys,
        Set<String> workItemRoles,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!scope.isObject()) {
            error(diagnostics, "invalid_permission_data_scope", path, "Data scope must be an object");
            return;
        }
        DataScopeKind kind;
        try {
            kind = DataScopeKind.valueOf(scope.path("kind").asText(""));
        } catch (IllegalArgumentException exception) {
            error(diagnostics, "invalid_permission_data_scope_kind", path + ".kind",
                "Data scope kind is unsupported");
            return;
        }
        if (kind == DataScopeKind.field_match
            && !fieldKeys.contains(scope.path("fieldKey").asText(""))) {
            error(diagnostics, "unknown_permission_scope_field", path + ".fieldKey",
                "Field-match data scope must reference a declared field");
        }
        if (kind == DataScopeKind.work_item_role
            && !workItemRoles.contains(scope.path("roleKey").asText(""))) {
            error(diagnostics, "unknown_permission_scope_role", path + ".roleKey",
                "Role data scope must reference a declared work-item role");
        }
        if (kind == DataScopeKind.field_match) {
            String operator = scope.path("operator").asText("");
            if (!Set.of("eq", "in", "contains").contains(operator)) {
                error(diagnostics, "invalid_permission_scope_operator", path + ".operator",
                    "Field-match data scope operator is not allowed");
            }
        }
        JsonNode values = scope.path("values");
        if (!values.isMissingNode() && (!values.isArray() || values.size() > MAX_SCOPE_VALUES)) {
            error(diagnostics, "permission_scope_value_budget_exceeded", path + ".values",
                "Data scope values must be a bounded array");
        }
    }

    private void validateLegacyMappings(
        JsonNode mappings,
        Set<String> spaceRoles,
        Set<String> workItemRoles,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!mappings.isArray() || mappings.isEmpty() || mappings.size() > 64) {
            error(diagnostics, "invalid_permission_legacy_manifest", "$.permissionModel.legacyMappings",
                "Legacy mappings must be a non-empty bounded array");
            return;
        }
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < mappings.size(); index++) {
            JsonNode mapping = mappings.get(index);
            String path = "$.permissionModel.legacyMappings[" + index + "]";
            String source = mapping.path("sourceKind").asText("") + "|" + mapping.path("sourceKey").asText("");
            if (!keys.add(source)) {
                error(diagnostics, "duplicate_permission_legacy_mapping", path,
                    "Legacy mapping sources must be unique");
            }
            String disposition = mapping.path("disposition").asText("");
            if (!Set.of("map", "preserve_in_snapshot", "review_required", "reject").contains(disposition)) {
                error(diagnostics, "invalid_permission_legacy_disposition", path + ".disposition",
                    "Legacy mapping disposition is unsupported");
            }
            String targetLayer = mapping.path("targetLayer").asText("");
            String targetKey = mapping.path("targetKey").asText("");
            if ("space_role".equals(targetLayer)) {
                requireKnownKey(targetKey, spaceRoles, path, diagnostics);
            } else if ("work_item_role".equals(targetLayer)) {
                requireKnownKey(targetKey, workItemRoles, path, diagnostics);
            } else if (!Set.of("field_policy", "none").contains(targetLayer)) {
                error(diagnostics, "invalid_permission_legacy_target", path + ".targetLayer",
                    "Legacy mapping target layer is unsupported");
            }
            if ("enterprise_role".equals(mapping.path("sourceKind").asText(""))
                && "owner".equals(targetKey)) {
                error(diagnostics, "enterprise_role_cannot_map_content_owner", path,
                    "Enterprise roles cannot implicitly become content owners");
            }
        }
    }

    private void validateInheritance(
        Map<String, Set<String>> graph,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        for (String key : graph.keySet()) {
            int depth = depth(key, graph, new HashSet<>());
            if (depth < 0) {
                error(diagnostics, "space_role_inheritance_cycle", "$.permissionModel.spaceRoleDefinitions",
                    "Space role inheritance must be acyclic");
            } else if (depth > MAX_ROLE_INHERITANCE_DEPTH) {
                error(diagnostics, "space_role_inheritance_depth_exceeded",
                    "$.permissionModel.spaceRoleDefinitions",
                    "Space role inheritance exceeds the supported depth");
            }
        }
    }

    private int depth(String key, Map<String, Set<String>> graph, Set<String> visiting) {
        if (!visiting.add(key)) {
            return -1;
        }
        int max = 1;
        for (String parent : graph.getOrDefault(key, Set.of())) {
            int parentDepth = depth(parent, graph, visiting);
            if (parentDepth < 0) {
                return -1;
            }
            max = Math.max(max, parentDepth + 1);
        }
        visiting.remove(key);
        return max;
    }

    private Set<String> validateSemanticKeys(
        JsonNode values,
        String path,
        int limit,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> result = new HashSet<>();
        if (!values.isArray() || values.size() > limit) {
            error(diagnostics, "invalid_semantic_key_array", path, "Expected a bounded semantic-key array");
            return result;
        }
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index).asText("");
            if (!SEMANTIC_KEY.matcher(value).matches() || !result.add(value)) {
                error(diagnostics, "duplicate_or_invalid_semantic_key", path + "[" + index + "]",
                    "Semantic keys must be stable, valid and unique");
            }
        }
        return result;
    }

    private Set<String> validateStringSet(
        JsonNode values,
        String path,
        Set<String> allowed,
        int limit,
        boolean allowEmpty,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> result = new HashSet<>();
        if (!values.isArray() || values.size() > limit || (!allowEmpty && values.isEmpty())) {
            error(diagnostics, "invalid_bounded_value_array", path, "Expected a non-empty bounded value array");
            return result;
        }
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index).asText("");
            if (!allowed.contains(value) || !result.add(value)) {
                error(diagnostics, "duplicate_or_unsupported_value", path + "[" + index + "]",
                    "Values must be supported and unique");
            }
        }
        return result;
    }

    private void validateReferences(
        JsonNode values,
        String path,
        Set<String> allowed,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!values.isArray() || values.size() > MAX_SCOPE_VALUES) {
            error(diagnostics, "invalid_permission_reference_array", path,
                "Permission references must be a bounded array");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index).asText("");
            if (!allowed.contains(value) || !seen.add(value)) {
                error(diagnostics, "unknown_or_duplicate_permission_reference", path + "[" + index + "]",
                    "Permission references must be declared and unique");
            }
        }
    }

    private void validateNameAndOrder(
        JsonNode value,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        validateNameAndOrder(value, path, diagnostics, true);
    }

    private void validateNameAndOrder(
        JsonNode value,
        String path,
        List<ConfigurationDiagnostic> diagnostics,
        boolean requireName
    ) {
        if (requireName) {
            String name = value.path("name").asText("");
            if (name.isBlank() || name.length() > 80) {
                error(diagnostics, "invalid_permission_definition_name", path + ".name",
                    "Definition names must contain between 1 and 80 characters");
            }
        }
        if (!value.path("sortOrder").canConvertToInt() || value.path("sortOrder").asInt() < 0) {
            error(diagnostics, "invalid_permission_sort_order", path + ".sortOrder",
                "Definition sortOrder must be a non-negative integer");
        }
    }

    private void requireKnownKey(
        String key,
        Set<String> allowed,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!allowed.contains(key)) {
            error(diagnostics, "unknown_permission_role_key", path + ".key",
                "Subject selector references an unknown role key");
        }
    }

    private void requireSemanticKey(
        String key,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!SEMANTIC_KEY.matcher(key).matches()) {
            error(diagnostics, "invalid_permission_selector_key", path + ".key",
                "Selector key must be a stable semantic key");
        }
    }

    private boolean uuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void error(
        List<ConfigurationDiagnostic> diagnostics,
        String code,
        String path,
        String message
    ) {
        diagnostics.add(new ConfigurationDiagnostic(code, DiagnosticSeverity.error, path, message));
    }
}
