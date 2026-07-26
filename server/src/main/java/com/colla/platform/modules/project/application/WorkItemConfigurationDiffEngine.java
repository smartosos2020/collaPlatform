package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiff;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiffEntry;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiffImpact;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class WorkItemConfigurationDiffEngine {
    private static final int MAX_DIFF_ITEMS = 1000;

    public ConfigurationDiff diff(String fromHash, JsonNode before, String toHash, JsonNode after) {
        List<ConfigurationDiffEntry> items = new ArrayList<>();
        compare("$", before, after, items);
        items.sort(Comparator.comparing(ConfigurationDiffEntry::keyPath)
            .thenComparing(ConfigurationDiffEntry::changeType));
        if (items.size() > MAX_DIFF_ITEMS) {
            throw new IllegalArgumentException("Configuration diff exceeds the 1000 item budget");
        }
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (DiffImpact impact : DiffImpact.values()) {
            summary.put(impact.name(), 0);
        }
        items.forEach(item -> summary.compute(item.impact().name(), (key, count) -> count == null ? 1 : count + 1));
        return new ConfigurationDiff(
            fromHash,
            toHash,
            List.copyOf(items),
            Map.copyOf(summary),
            items.stream().anyMatch(item -> item.impact() == DiffImpact.breaking)
        );
    }

    private void compare(String path, JsonNode before, JsonNode after, List<ConfigurationDiffEntry> items) {
        if (equals(before, after)) {
            return;
        }
        if (missing(before)) {
            items.add(entry(path, "added", before, after));
            return;
        }
        if (missing(after)) {
            items.add(entry(path, "removed", before, after));
            return;
        }
        if (before.isObject() && after.isObject()) {
            List<String> names = new ArrayList<>();
            before.fieldNames().forEachRemaining(names::add);
            after.fieldNames().forEachRemaining(names::add);
            names.stream().distinct().sorted().forEach(name ->
                compare(path + "." + escape(name), before.get(name), after.get(name), items)
            );
            return;
        }
        if (before.isArray() && after.isArray()) {
            String keySpec = keySpec(path);
            if (keySpec != null && canIndex(before, keySpec) && canIndex(after, keySpec)) {
                compareKeyedArray(path, keySpec, before, after, items);
                return;
            }
            int size = Math.max(before.size(), after.size());
            for (int index = 0; index < size; index++) {
                compare(path + "[" + index + "]", before.get(index), after.get(index), items);
            }
            return;
        }
        items.add(entry(path, "changed", before, after));
    }

    private boolean canIndex(JsonNode values, String keySpec) {
        Set<String> keys = new TreeSet<>();
        for (JsonNode value : values) {
            StringBuilder key = new StringBuilder();
            for (String field : keySpec.split("\\+")) {
                String part = value.path(field).asText("");
                if (part.isBlank()) {
                    return false;
                }
                if (!key.isEmpty()) {
                    key.append('|');
                }
                key.append(part);
            }
            if (!keys.add(key.toString())) {
                return false;
            }
        }
        return true;
    }

    private void compareKeyedArray(
        String path,
        String keySpec,
        JsonNode before,
        JsonNode after,
        List<ConfigurationDiffEntry> items
    ) {
        Map<String, JsonNode> left = index(before, keySpec);
        Map<String, JsonNode> right = index(after, keySpec);
        Set<String> keys = new TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        for (String key : keys) {
            compare(path + "[" + escape(key) + "]", left.get(key), right.get(key), items);
        }
    }

    private Map<String, JsonNode> index(JsonNode values, String keySpec) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
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

    private String keySpec(String path) {
        if (path.endsWith(".fields")) {
            return "fieldKey";
        }
        if (path.endsWith(".options")) {
            return "optionKey";
        }
        if (path.endsWith(".layouts")) {
            return "layoutKind";
        }
        if (path.endsWith(".nodes")) {
            return "nodeKey";
        }
        if (path.endsWith(".policies")) {
            return "fieldKey+policyKey";
        }
        if (path.endsWith(".states")) {
            return "stateKey";
        }
        if (path.endsWith(".actions")) {
            return "actionKey";
        }
        if (path.endsWith(".transitions")) {
            return "transitionKey";
        }
        if (path.endsWith(".guards")) {
            return "guardKey";
        }
        return null;
    }

    private ConfigurationDiffEntry entry(String path, String changeType, JsonNode before, JsonNode after) {
        return new ConfigurationDiffEntry(
            path,
            changeType,
            impact(path, changeType, before, after),
            missing(before) ? null : before.deepCopy(),
            missing(after) ? null : after.deepCopy()
        );
    }

    private DiffImpact impact(String path, String changeType, JsonNode before, JsonNode after) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if ("removed".equals(changeType)) {
            return DiffImpact.breaking;
        }
        if (normalized.contains("access") || normalized.contains("permission")
            || normalized.contains("condition") || normalized.contains("visibility")) {
            return "added".equals(changeType) ? DiffImpact.conditional : DiffImpact.breaking;
        }
        if ("added".equals(changeType)) {
            return DiffImpact.additive;
        }
        if (normalized.endsWith(".required") && after != null && after.isBoolean() && after.booleanValue()) {
            return DiffImpact.breaking;
        }
        return DiffImpact.behavioral;
    }

    private boolean missing(JsonNode value) {
        return value == null || value.isMissingNode();
    }

    private boolean equals(JsonNode left, JsonNode right) {
        if (missing(left) && missing(right)) {
            return true;
        }
        return !missing(left) && left.equals(right);
    }

    private String escape(String value) {
        return value.replace("~", "~0").replace(".", "~1");
    }
}
