package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemRelationModels.SYSTEM_TYPE_KEYS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemRelationDefinitionPresetCatalog {
    private final ObjectMapper objectMapper;

    public WorkItemRelationDefinitionPresetCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<JsonNode> definitionsFor(String typeKey) {
        if (!SYSTEM_TYPE_KEYS.contains(typeKey)) {
            return Optional.empty();
        }
        ArrayNode definitions = objectMapper.createArrayNode();
        add(definitions, typeKey, "relates_to", "normal", "undirected", "关联", "关联",
            "many", "many", "detach", false, 64, 100);
        add(definitions, typeKey, "parent_child", "parent_child", "directed", "父项", "子项",
            "many", "one", "restrict", false, 64, 200);
        add(definitions, typeKey, "depends_on", "dependency", "directed", "依赖", "被依赖",
            "many", "many", "retain_history", false, 64, 300);
        add(definitions, typeKey, "blocks", "blocking", "directed", "阻塞", "被阻塞",
            "many", "many", "retain_history", false, 64, 400);
        return Optional.of(definitions);
    }

    private void add(
        ArrayNode definitions,
        String sourceTypeKey,
        String relationKey,
        String kind,
        String direction,
        String forwardName,
        String reverseName,
        String sourceCardinality,
        String targetCardinality,
        String deletionPolicy,
        boolean allowSelf,
        int maxDepth,
        int sortOrder
    ) {
        ObjectNode definition = definitions.addObject();
        definition.put("relationKey", relationKey);
        definition.put("kind", kind);
        definition.put("direction", direction);
        definition.put("forwardName", forwardName);
        definition.put("reverseName", reverseName);
        definition.putArray("sourceTypeKeys").add(sourceTypeKey);
        ArrayNode targets = definition.putArray("targetTypeKeys");
        SYSTEM_TYPE_KEYS.stream().sorted().forEach(targets::add);
        definition.put("sourceCardinality", sourceCardinality);
        definition.put("targetCardinality", targetCardinality);
        definition.put("deletionPolicy", deletionPolicy);
        definition.put("allowSelf", allowSelf);
        definition.put("maxDepth", maxDepth);
        definition.put("sortOrder", sortOrder);
        definition.put("system", true);
    }
}
