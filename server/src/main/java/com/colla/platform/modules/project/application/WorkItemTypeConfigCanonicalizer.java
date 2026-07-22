package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemTypeModels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class WorkItemTypeConfigCanonicalizer {
    private final ObjectMapper objectMapper;

    public WorkItemTypeConfigCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalConfig initialPublishedSkeleton(String typeKey, String name, String icon, String description) {
        ObjectNode display = objectMapper.createObjectNode();
        display.put("description", description);
        display.put("icon", icon);
        display.put("name", name);

        ObjectNode root = objectMapper.createObjectNode();
        root.set("display", display);
        root.put("schemaVersion", WorkItemTypeModels.CONFIG_SCHEMA_VERSION);
        root.put("typeKey", typeKey);
        JsonNode canonical = sort(root);
        return new CanonicalConfig(canonical, hash(canonical));
    }

    public String hash(JsonNode config) {
        try {
            byte[] serialized = objectMapper.writeValueAsString(sort(config)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to canonicalize work item type config", exception);
        }
    }

    public JsonNode sort(JsonNode node) {
        if (node == null || node.isValueNode()) {
            return node == null ? objectMapper.nullNode() : node.deepCopy();
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(sort(item)));
            return result;
        }
        ObjectNode result = objectMapper.createObjectNode();
        StreamSupport.stream(((Iterable<String>) node::fieldNames).spliterator(), false)
            .sorted(Comparator.naturalOrder())
            .forEach(field -> result.set(field, sort(node.get(field))));
        return result;
    }

    public record CanonicalConfig(JsonNode config, String hash) {
    }
}
