package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.MAX_FIELDS;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.MAX_OPTIONS;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_DEPTH;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_NODES;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_POLICIES;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiagnosticSeverity;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class WorkItemConfigurationValidator {
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer;
    private final WorkItemStateFlowValidator stateFlowValidator;
    private final WorkItemNodeFlowValidator nodeFlowValidator;

    public WorkItemConfigurationValidator(
        WorkItemConfigurationSnapshotCanonicalizer canonicalizer,
        WorkItemStateFlowValidator stateFlowValidator,
        WorkItemNodeFlowValidator nodeFlowValidator
    ) {
        this.canonicalizer = canonicalizer;
        this.stateFlowValidator = stateFlowValidator;
        this.nodeFlowValidator = nodeFlowValidator;
    }

    public ValidationResult validate(JsonNode requested) {
        JsonNode snapshot = canonicalizer.canonicalize(requested).payload();
        List<ConfigurationDiagnostic> diagnostics = new ArrayList<>();
        JsonNode type = snapshot.path("typeDefinition");
        if (!type.isObject() || type.path("typeKey").asText("").isBlank()) {
            error(diagnostics, "missing_type_definition", "$.typeDefinition", "Type definition is required");
        }

        JsonNode fields = snapshot.path("fields");
        if (!fields.isArray()) {
            error(diagnostics, "invalid_fields", "$.fields", "Fields must be an array");
            return ValidationResult.of(diagnostics);
        }
        if (fields.size() > MAX_FIELDS) {
            error(diagnostics, "field_budget_exceeded", "$.fields", "At most " + MAX_FIELDS + " fields are allowed");
        }

        Set<String> fieldKeys = new LinkedHashSet<>();
        Set<String> activeFieldKeys = new LinkedHashSet<>();
        int optionCount = 0;
        for (int index = 0; index < fields.size(); index++) {
            JsonNode field = fields.get(index);
            String path = "$.fields[" + index + "]";
            String key = field.path("fieldKey").asText("");
            if (key.isBlank() || !fieldKeys.add(key)) {
                error(diagnostics, "duplicate_or_missing_field_key", path + ".fieldKey", "Field keys must be present and unique");
            }
            if ("active".equals(field.path("status").asText())) {
                activeFieldKeys.add(key);
            }
            if (!field.path("config").isObject()) {
                error(diagnostics, "invalid_field_config", path + ".config", "Field config must be an object");
            }
            JsonNode options = field.path("options");
            if (!options.isArray()) {
                error(diagnostics, "invalid_field_options", path + ".options", "Field options must be an array");
                continue;
            }
            optionCount += options.size();
            Set<String> optionKeys = new HashSet<>();
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                String optionKey = options.get(optionIndex).path("optionKey").asText("");
                if (optionKey.isBlank() || !optionKeys.add(optionKey)) {
                    error(
                        diagnostics,
                        "duplicate_or_missing_option_key",
                        path + ".options[" + optionIndex + "].optionKey",
                        "Option keys must be present and unique within a field"
                    );
                }
            }
            validateReferenceScope(type, field.path("config"), path + ".config", diagnostics);
        }
        if (optionCount > MAX_OPTIONS) {
            error(diagnostics, "option_budget_exceeded", "$.fields", "At most " + MAX_OPTIONS + " options are allowed");
        }

        JsonNode layouts = snapshot.path("layouts");
        if (!layouts.isArray()) {
            error(diagnostics, "invalid_layouts", "$.layouts", "Layouts must be an array");
            return ValidationResult.of(diagnostics);
        }
        Set<String> kinds = new HashSet<>();
        for (int index = 0; index < layouts.size(); index++) {
            JsonNode layout = layouts.get(index);
            String path = "$.layouts[" + index + "]";
            String kind = layout.path("layoutKind").asText("");
            if (!Set.of("create", "detail").contains(kind) || !kinds.add(kind)) {
                error(diagnostics, "duplicate_or_invalid_layout_kind", path + ".layoutKind", "Layout kind must be unique");
            }
            validateLayout(layout, path, fieldKeys, activeFieldKeys, diagnostics);
        }
        if (!kinds.contains("create") || !kinds.contains("detail")) {
            warning(diagnostics, "missing_layout_kind", "$.layouts", "Both create and detail layouts should be configured");
        }
        Set<String> hiddenFieldKeys = hiddenFieldKeys(layouts);
        if (snapshot.path("stateFlow").isObject()
            && snapshot.path("snapshotSchemaVersion").asInt() < 2) {
            error(
                diagnostics,
                "state_flow_requires_schema_v2",
                "$.stateFlow",
                "State flow definitions require snapshot schema version 2"
            );
        }
        if (snapshot.path("nodeFlow").isObject()
            && snapshot.path("snapshotSchemaVersion").asInt() < 3) {
            error(
                diagnostics,
                "node_flow_requires_schema_v3",
                "$.nodeFlow",
                "Node flow definitions require snapshot schema version 3"
            );
        }
        if (snapshot.path("stateFlow").isObject() && snapshot.path("nodeFlow").isObject()) {
            error(
                diagnostics,
                "multiple_workflow_authorities",
                "$",
                "A configuration snapshot cannot activate stateFlow and nodeFlow together"
            );
        }
        stateFlowValidator.validate(
            snapshot.path("stateFlow"),
            fieldKeys,
            activeFieldKeys,
            hiddenFieldKeys,
            diagnostics
        );
        nodeFlowValidator.validate(
            snapshot.path("nodeFlow"),
            fieldKeys,
            activeFieldKeys,
            hiddenFieldKeys,
            diagnostics
        );
        diagnostics.sort(Comparator.comparing(ConfigurationDiagnostic::keyPath)
            .thenComparing(ConfigurationDiagnostic::code));
        return ValidationResult.of(diagnostics);
    }

    private Set<String> hiddenFieldKeys(JsonNode layouts) {
        Set<String> hidden = new HashSet<>();
        for (JsonNode layout : layouts) {
            for (JsonNode policy : layout.path("policies")) {
                if (containsHiddenEffect(policy.path("policy"))) {
                    hidden.add(policy.path("fieldKey").asText(""));
                }
            }
        }
        hidden.remove("");
        return hidden;
    }

    private boolean containsHiddenEffect(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return "hidden".equals(value.asText());
        }
        if (value.isArray()) {
            for (JsonNode child : value) {
                if (containsHiddenEffect(child)) {
                    return true;
                }
            }
            return false;
        }
        Iterator<JsonNode> children = value.elements();
        while (children.hasNext()) {
            if (containsHiddenEffect(children.next())) {
                return true;
            }
        }
        return false;
    }

    private void validateLayout(
        JsonNode layout,
        String path,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        JsonNode nodes = layout.path("nodes");
        if (!nodes.isArray()) {
            error(diagnostics, "invalid_layout_nodes", path + ".nodes", "Layout nodes must be an array");
            return;
        }
        if (nodes.size() > MAX_NODES) {
            error(diagnostics, "layout_node_budget_exceeded", path + ".nodes", "Layout node budget exceeded");
        }
        Map<String, String> parents = new LinkedHashMap<>();
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode node = nodes.get(index);
            String nodePath = path + ".nodes[" + index + "]";
            String nodeKey = node.path("nodeKey").asText("");
            if (nodeKey.isBlank() || !keys.add(nodeKey)) {
                error(diagnostics, "duplicate_or_missing_node_key", nodePath + ".nodeKey", "Node keys must be present and unique");
            }
            String parentKey = node.path("parentKey").isNull() ? null : node.path("parentKey").asText(null);
            parents.put(nodeKey, parentKey);
            String fieldKey = node.path("fieldKey").isNull() ? null : node.path("fieldKey").asText(null);
            if (fieldKey != null && !fieldKeys.contains(fieldKey)) {
                error(diagnostics, "unknown_layout_field", nodePath + ".fieldKey", "Layout references an unknown field");
            } else if (fieldKey != null && !activeFieldKeys.contains(fieldKey)) {
                error(diagnostics, "inactive_layout_field", nodePath + ".fieldKey", "Layout references an inactive field");
            }
            validateConditionReferences(
                node.path("visibilityCondition"),
                nodePath + ".visibilityCondition",
                fieldKeys,
                diagnostics
            );
        }
        for (Map.Entry<String, String> entry : parents.entrySet()) {
            if (entry.getValue() != null && !parents.containsKey(entry.getValue())) {
                error(diagnostics, "missing_layout_parent", path + ".nodes", "Layout node parent is missing");
            }
            int depth = depth(entry.getKey(), parents, new HashSet<>());
            if (depth < 0) {
                error(diagnostics, "layout_cycle", path + ".nodes", "Layout graph contains a cycle");
            } else if (depth > MAX_DEPTH) {
                error(diagnostics, "layout_depth_exceeded", path + ".nodes", "Layout depth exceeds " + MAX_DEPTH);
            }
        }

        JsonNode policies = layout.path("policies");
        if (!policies.isArray()) {
            error(diagnostics, "invalid_access_policies", path + ".policies", "Policies must be an array");
            return;
        }
        if (policies.size() > MAX_POLICIES) {
            error(diagnostics, "access_policy_budget_exceeded", path + ".policies", "Access policy budget exceeded");
        }
        Set<String> policyKeys = new HashSet<>();
        for (int index = 0; index < policies.size(); index++) {
            JsonNode policy = policies.get(index);
            String policyPath = path + ".policies[" + index + "]";
            String policyKey = policy.path("policyKey").asText("");
            if (policyKey.isBlank() || !policyKeys.add(policyKey)) {
                error(diagnostics, "duplicate_or_missing_policy_key", policyPath + ".policyKey", "Policy keys must be unique");
            }
            String fieldKey = policy.path("fieldKey").asText("");
            if (!activeFieldKeys.contains(fieldKey)) {
                error(diagnostics, "inactive_policy_field", policyPath + ".fieldKey", "Policy must reference an active field");
            }
            validateConditionReferences(policy.path("policy"), policyPath + ".policy", fieldKeys, diagnostics);
        }
    }

    private int depth(String key, Map<String, String> parents, Set<String> path) {
        if (!path.add(key)) {
            return -1;
        }
        String parent = parents.get(key);
        int depth = parent == null ? 1 : depth(parent, parents, path);
        path.remove(key);
        return depth < 0 ? -1 : depth + (parent == null ? 0 : 1);
    }

    private void validateConditionReferences(
        JsonNode value,
        String path,
        Set<String> fieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return;
        }
        if (value.isArray()) {
            for (int index = 0; index < value.size(); index++) {
                validateConditionReferences(value.get(index), path + "[" + index + "]", fieldKeys, diagnostics);
            }
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("fieldKey".equals(entry.getKey()) && entry.getValue().isTextual()
                && !fieldKeys.contains(entry.getValue().asText())) {
                error(diagnostics, "unknown_condition_field", path + ".fieldKey", "Condition references an unknown field");
            } else {
                validateConditionReferences(entry.getValue(), path + "." + entry.getKey(), fieldKeys, diagnostics);
            }
        }
    }

    private void validateReferenceScope(
        JsonNode type,
        JsonNode config,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!config.isObject()) {
            return;
        }
        String targetWorkspaceId = config.path("targetWorkspaceId").asText("");
        String targetSpaceId = config.path("targetSpaceId").asText("");
        if (!targetWorkspaceId.isBlank()
            && !targetWorkspaceId.equals(type.path("workspaceId").asText())) {
            error(diagnostics, "cross_workspace_reference", path + ".targetWorkspaceId", "Cross-workspace references are forbidden");
        }
        if (!targetSpaceId.isBlank() && !targetSpaceId.equals(type.path("spaceId").asText())) {
            error(diagnostics, "cross_space_reference", path + ".targetSpaceId", "Cross-space references are forbidden");
        }
    }

    private void error(List<ConfigurationDiagnostic> diagnostics, String code, String path, String message) {
        diagnostics.add(new ConfigurationDiagnostic(code, DiagnosticSeverity.error, path, message));
    }

    private void warning(List<ConfigurationDiagnostic> diagnostics, String code, String path, String message) {
        diagnostics.add(new ConfigurationDiagnostic(code, DiagnosticSeverity.warning, path, message));
    }
}
