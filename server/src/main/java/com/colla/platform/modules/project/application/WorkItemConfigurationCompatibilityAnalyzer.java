package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityFinding;
import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityImpact;
import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityReport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class WorkItemConfigurationCompatibilityAnalyzer {
    private static final int MAX_FINDINGS = 1000;

    public CompatibilityReport analyze(
        String fromHash,
        JsonNode before,
        String toHash,
        JsonNode after
    ) {
        List<CompatibilityFinding> findings = new ArrayList<>();
        compareObject(
            "$.typeDefinition",
            before.path("typeDefinition"),
            after.path("typeDefinition"),
            findings
        );
        compareKeyedArray("$.fields", "fieldKey", before.path("fields"), after.path("fields"), findings);
        compareKeyedArray("$.layouts", "layoutKind", before.path("layouts"), after.path("layouts"), findings);
        compareStateFlow(before.get("stateFlow"), after.get("stateFlow"), findings);
        findings.sort(Comparator.comparing(CompatibilityFinding::keyPath)
            .thenComparing(CompatibilityFinding::reasonCode));
        if (findings.size() > MAX_FINDINGS) {
            throw new IllegalArgumentException("Configuration compatibility analysis exceeds 1000 findings");
        }
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (CompatibilityImpact impact : CompatibilityImpact.values()) {
            summary.put(impact.name(), 0);
        }
        findings.forEach(finding -> summary.compute(
            finding.impact().name(),
            (key, count) -> count == null ? 1 : count + 1
        ));
        CompatibilityImpact overall = findings.stream()
            .map(CompatibilityFinding::impact)
            .max(Comparator.comparingInt(this::rank))
            .orElse(CompatibilityImpact.compatible);
        return new CompatibilityReport(
            fromHash,
            toHash,
            overall,
            List.copyOf(findings),
            Map.copyOf(summary),
            findings.stream().anyMatch(finding ->
                finding.impact() == CompatibilityImpact.migration_required
                    || finding.impact() == CompatibilityImpact.blocked
            )
        );
    }

    private void compareStateFlow(
        JsonNode before,
        JsonNode after,
        List<CompatibilityFinding> findings
    ) {
        if (before == null && after == null) {
            return;
        }
        if (before == null) {
            findings.add(new CompatibilityFinding(
                "$.stateFlow",
                CompatibilityImpact.migration_required,
                "state_flow_added",
                "Initialize existing work items through an explicit manifest before enabling runtime actions",
                null,
                copy(after)
            ));
            return;
        }
        if (after == null) {
            findings.add(new CompatibilityFinding(
                "$.stateFlow",
                CompatibilityImpact.blocked,
                "state_flow_removed",
                "Retain the state flow or explicitly migrate every bound instance",
                copy(before),
                null
            ));
            return;
        }
        compareKeyedArray("$.stateFlow.states", "stateKey", before.path("states"), after.path("states"), findings);
        compareKeyedArray("$.stateFlow.actions", "actionKey", before.path("actions"), after.path("actions"), findings);
        compareKeyedArray(
            "$.stateFlow.transitions",
            "transitionKey",
            before.path("transitions"),
            after.path("transitions"),
            findings
        );
        compareKeyedArray("$.stateFlow.guards", "guardKey", before.path("guards"), after.path("guards"), findings);
    }

    private void compareObject(
        String path,
        JsonNode before,
        JsonNode after,
        List<CompatibilityFinding> findings
    ) {
        Set<String> names = new TreeSet<>();
        before.fieldNames().forEachRemaining(names::add);
        after.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            JsonNode left = before.get(name);
            JsonNode right = after.get(name);
            String childPath = path + "." + escape(name);
            if ("options".equals(name)) {
                compareKeyedArray(childPath, "optionKey", left, right, findings);
            } else if ("nodes".equals(name)) {
                compareKeyedArray(childPath, "nodeKey", left, right, findings);
            } else if ("policies".equals(name)) {
                compareKeyedArray(childPath, "fieldKey+policyKey", left, right, findings);
            } else {
                compareValue(childPath, left, right, findings);
            }
        }
    }

    private void compareKeyedArray(
        String path,
        String keySpec,
        JsonNode before,
        JsonNode after,
        List<CompatibilityFinding> findings
    ) {
        Map<String, JsonNode> left = index(before, keySpec);
        Map<String, JsonNode> right = index(after, keySpec);
        Set<String> keys = new TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        for (String key : keys) {
            JsonNode leftValue = left.get(key);
            JsonNode rightValue = right.get(key);
            String childPath = path + "[" + escape(key) + "]";
            if (leftValue == null || rightValue == null) {
                addFinding(childPath, leftValue, rightValue, findings);
            } else {
                compareObject(childPath, leftValue, rightValue, findings);
            }
        }
    }

    private void compareValue(
        String path,
        JsonNode before,
        JsonNode after,
        List<CompatibilityFinding> findings
    ) {
        if (same(before, after)) {
            return;
        }
        if (object(before) && object(after)) {
            compareObject(path, before, after, findings);
            return;
        }
        if (array(before) && array(after)) {
            if (!before.equals(after)) {
                addFinding(path, before, after, findings);
            }
            return;
        }
        addFinding(path, before, after, findings);
    }

    private void addFinding(
        String path,
        JsonNode before,
        JsonNode after,
        List<CompatibilityFinding> findings
    ) {
        Classification classification = classify(path, before, after);
        findings.add(new CompatibilityFinding(
            path,
            classification.impact(),
            classification.reasonCode(),
            classification.recommendation(),
            copy(before),
            copy(after)
        ));
    }

    private Classification classify(String path, JsonNode before, JsonNode after) {
        boolean removed = before != null && after == null;
        boolean added = before == null && after != null;
        if (path.matches("^\\$\\.stateFlow\\.states\\[[^]]+]$") && removed) {
            return migration("state_removed", "Provide an explicit state key mapping for every bound instance");
        }
        if (path.matches("^\\$\\.stateFlow\\.actions\\[[^]]+]$") && removed) {
            return review("action_removed", "Review clients and permissions that reference this action key");
        }
        if (path.matches("^\\$\\.stateFlow\\.transitions\\[[^]]+]$") && removed) {
            return review("transition_removed", "Review reachability and available action changes");
        }
        if (path.matches("^\\$\\.stateFlow\\.guards\\[[^]]+]$") && removed) {
            return migration("guard_removed", "Revalidate transitions and bound instances before upgrade");
        }
        if (path.contains("$.stateFlow.states") && path.endsWith(".category")) {
            return migration("state_category_changed", "Revalidate terminal, canceled, and initial-state mappings");
        }
        if (path.contains("$.stateFlow.states") && path.endsWith(".stateKey")) {
            return blocked("state_key_changed", "State semantic keys are immutable; add an explicit mapped state instead");
        }
        if (path.contains("$.stateFlow.actions") && path.endsWith(".actionKey")) {
            return blocked("action_key_changed", "Action semantic keys are immutable");
        }
        if (path.contains("$.stateFlow.guards")) {
            return migration("guard_changed", "Revalidate affected instances against the changed guard");
        }
        if (path.contains("$.stateFlow.actions")
            && (path.contains(".authorizedRoles") || path.contains(".requiredFieldKeys"))) {
            return migration("action_contract_changed", "Revalidate authorization and required-field coverage");
        }
        if (path.matches("^\\$\\.fields\\[[^]]+]$") && removed) {
            return migration("field_removed", "Map or retire values before upgrading existing work items");
        }
        if (path.endsWith(".fieldType")) {
            return blocked("field_type_changed", "Define an explicit value converter before instance migration");
        }
        if (path.endsWith(".required") && booleanValue(after) && !booleanValue(before)) {
            return migration("required_tightened", "Provide a deterministic default or backfill mapping");
        }
        if (path.contains(".options[") && path.matches(".*\\.options\\[[^]]+]$") && removed) {
            return migration("option_removed", "Map existing option values before removing the option");
        }
        if (path.contains(".policies[") && (removed || narrowed(before, after))) {
            return review("access_narrowed", "Review affected identities and preserve least-privilege access");
        }
        if (path.contains(".nodes[") && removed) {
            return review("layout_entry_removed", "Confirm the field remains reachable through another layout entry");
        }
        if (path.toLowerCase().contains("reference") && (removed || blank(after))) {
            return blocked("dangling_reference", "Repair the referenced stable key before publication or migration");
        }
        if (added) {
            return compatible("additive_change", "No instance migration is required");
        }
        if (removed) {
            return migration("configuration_removed", "Assess existing values and define an explicit mapping");
        }
        return review("behavior_changed", "Review the semantic change before upgrading bound instances");
    }

    private Map<String, JsonNode> index(JsonNode values, String keySpec) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
        if (values == null || !values.isArray()) {
            return indexed;
        }
        for (JsonNode value : values) {
            StringBuilder key = new StringBuilder();
            for (String field : keySpec.split("\\+")) {
                if (!key.isEmpty()) {
                    key.append('|');
                }
                key.append(value.path(field).asText());
            }
            indexed.put(key.toString(), value);
        }
        return indexed;
    }

    private boolean narrowed(JsonNode before, JsonNode after) {
        if (before == null || after == null) {
            return false;
        }
        String left = before.toString().toLowerCase();
        String right = after.toString().toLowerCase();
        return !left.equals(right)
            && (right.contains("\"hidden\"") || right.contains("\"write\":false"));
    }

    private int rank(CompatibilityImpact impact) {
        return switch (impact) {
            case compatible -> 0;
            case review_required -> 1;
            case migration_required -> 2;
            case blocked -> 3;
        };
    }

    private Classification compatible(String code, String recommendation) {
        return new Classification(CompatibilityImpact.compatible, code, recommendation);
    }

    private Classification review(String code, String recommendation) {
        return new Classification(CompatibilityImpact.review_required, code, recommendation);
    }

    private Classification migration(String code, String recommendation) {
        return new Classification(CompatibilityImpact.migration_required, code, recommendation);
    }

    private Classification blocked(String code, String recommendation) {
        return new Classification(CompatibilityImpact.blocked, code, recommendation);
    }

    private boolean same(JsonNode left, JsonNode right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean object(JsonNode value) {
        return value != null && value.isObject();
    }

    private boolean array(JsonNode value) {
        return value != null && value.isArray();
    }

    private boolean booleanValue(JsonNode value) {
        return value != null && value.isBoolean() && value.booleanValue();
    }

    private boolean blank(JsonNode value) {
        return value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank());
    }

    private JsonNode copy(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }

    private String escape(String value) {
        return value.replace("~", "~0").replace(".", "~1").replace("[", "~2").replace("]", "~3");
    }

    private record Classification(
        CompatibilityImpact impact,
        String reasonCode,
        String recommendation
    ) {
    }
}
