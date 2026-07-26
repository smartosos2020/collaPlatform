package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.MergeConflict;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class WorkItemConfigurationThreeWayMerge {
    private final ObjectMapper objectMapper;
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer;

    public WorkItemConfigurationThreeWayMerge(
        ObjectMapper objectMapper,
        WorkItemConfigurationSnapshotCanonicalizer canonicalizer
    ) {
        this.objectMapper = objectMapper;
        this.canonicalizer = canonicalizer;
    }

    public MergeResult merge(
        JsonNode base,
        JsonNode upstream,
        JsonNode local,
        Map<String, String> resolutions
    ) {
        List<MergeConflict> conflicts = new ArrayList<>();
        JsonNode merged = mergeNode(
            "$",
            canonicalizer.canonicalize(base).payload(),
            canonicalizer.canonicalize(upstream).payload(),
            canonicalizer.canonicalize(local).payload(),
            resolutions == null ? Map.of() : resolutions,
            conflicts
        );
        var canonical = canonicalizer.canonicalize(merged);
        return new MergeResult(canonical.payload(), canonical.configHash(), List.copyOf(conflicts));
    }

    private JsonNode mergeNode(
        String path,
        JsonNode base,
        JsonNode upstream,
        JsonNode local,
        Map<String, String> resolutions,
        List<MergeConflict> conflicts
    ) {
        JsonNode b = value(base);
        JsonNode u = value(upstream);
        JsonNode l = value(local);
        if (l.equals(b)) {
            return u.deepCopy();
        }
        if (u.equals(b) || l.equals(u)) {
            return l.deepCopy();
        }
        if (b.isObject() && u.isObject() && l.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Set<String> fields = new TreeSet<>();
            b.fieldNames().forEachRemaining(fields::add);
            u.fieldNames().forEachRemaining(fields::add);
            l.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode merged = mergeNode(
                    path + "." + field,
                    b.get(field),
                    u.get(field),
                    l.get(field),
                    resolutions,
                    conflicts
                );
                if (!merged.isMissingNode() && !merged.isNull()) {
                    result.set(field, merged);
                } else if (l.has(field) || u.has(field)) {
                    result.set(field, merged);
                }
            }
            return result;
        }
        String arrayKey = arrayKey(path);
        if (arrayKey != null && b.isArray() && u.isArray() && l.isArray()) {
            return mergeKeyedArray(path, arrayKey, b, u, l, resolutions, conflicts);
        }
        String choice = resolutions.get(path);
        conflicts.add(new MergeConflict(
            path,
            copy(b),
            copy(u),
            copy(l),
            deletionConflict(b, u, l) ? "delete_or_modify" : "concurrent_change"
        ));
        return "upstream".equals(choice) ? copy(u) : copy(l);
    }

    private ArrayNode mergeKeyedArray(
        String path,
        String keySpec,
        JsonNode base,
        JsonNode upstream,
        JsonNode local,
        Map<String, String> resolutions,
        List<MergeConflict> conflicts
    ) {
        Map<String, JsonNode> bases = index(base, keySpec);
        Map<String, JsonNode> upstreams = index(upstream, keySpec);
        Map<String, JsonNode> locals = index(local, keySpec);
        Set<String> keys = new TreeSet<>();
        keys.addAll(bases.keySet());
        keys.addAll(upstreams.keySet());
        keys.addAll(locals.keySet());
        ArrayNode result = objectMapper.createArrayNode();
        for (String key : keys) {
            JsonNode merged = mergeNode(
                path + "[" + key + "]",
                bases.get(key),
                upstreams.get(key),
                locals.get(key),
                resolutions,
                conflicts
            );
            if (!merged.isNull() && !merged.isMissingNode()) {
                result.add(merged);
            }
        }
        return result;
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

    private String arrayKey(String path) {
        if (path.endsWith(".fields")) return "fieldKey";
        if (path.endsWith(".options")) return "optionKey";
        if (path.endsWith(".layouts")) return "layoutKind";
        if (path.endsWith(".nodes")) return "nodeKey";
        if (path.endsWith(".policies")) return "fieldKey+policyKey";
        return null;
    }

    private boolean deletionConflict(JsonNode base, JsonNode upstream, JsonNode local) {
        return !base.isNull() && (upstream.isNull() || local.isNull());
    }

    private JsonNode value(JsonNode value) {
        return value == null || value.isMissingNode() ? NullNode.getInstance() : value;
    }

    private JsonNode copy(JsonNode value) {
        return value(value).deepCopy();
    }

    public record MergeResult(JsonNode snapshot, String configHash, List<MergeConflict> conflicts) {
        public Map<String, Integer> summary() {
            Map<String, Integer> result = new LinkedHashMap<>();
            result.put("conflicts", conflicts.size());
            result.put("resolved", (int) conflicts.stream()
                .map(MergeConflict::keyPath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .size());
            return Map.copyOf(result);
        }
    }
}
