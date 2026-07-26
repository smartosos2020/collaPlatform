package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.SNAPSHOT_SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.FIRST_COMPLETE_SNAPSHOT_SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class WorkItemConfigurationSnapshotCanonicalizer {
    private final ObjectMapper objectMapper;

    public WorkItemConfigurationSnapshotCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ConfigurationSnapshot canonicalize(JsonNode requested) {
        if (requested == null || !requested.isObject()) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Configuration snapshot must be an object");
        }
        JsonNode schemaVersion = requested.path("snapshotSchemaVersion");
        if (!schemaVersion.isInt()
            || schemaVersion.asInt() < FIRST_COMPLETE_SNAPSHOT_SCHEMA_VERSION
            || schemaVersion.asInt() > SNAPSHOT_SCHEMA_VERSION) {
            throw failure(
                "UNSUPPORTED_SNAPSHOT_SCHEMA",
                "Configuration snapshot schema version must be between "
                    + FIRST_COMPLETE_SNAPSHOT_SCHEMA_VERSION + " and " + SNAPSHOT_SCHEMA_VERSION
            );
        }
        JsonNode payload = normalize(requested, null);
        return new ConfigurationSnapshot(schemaVersion.asInt(), payload, hash(payload));
    }

    private JsonNode normalize(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isNumber() && !node.isIntegralNumber()) {
            BigDecimal normalized = node.decimalValue().stripTrailingZeros();
            return DecimalNode.valueOf(normalized);
        }
        if (node.isValueNode()) {
            return node.deepCopy();
        }
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            node.forEach(item -> values.add(normalize(item, null)));
            comparator(fieldName).ifPresent(values::sort);
            ArrayNode result = objectMapper.createArrayNode();
            values.forEach(result::add);
            return result;
        }
        ObjectNode result = objectMapper.createObjectNode();
        StreamSupport.stream(((Iterable<String>) node::fieldNames).spliterator(), false)
            .sorted()
            .forEach(field -> result.set(field, normalize(node.get(field), field)));
        return result;
    }

    private java.util.Optional<Comparator<JsonNode>> comparator(String fieldName) {
        if ("fields".equals(fieldName)) {
            return java.util.Optional.of(byOrderThenKey("fieldKey"));
        }
        if ("options".equals(fieldName)) {
            return java.util.Optional.of(byOrderThenKey("optionKey"));
        }
        if ("layouts".equals(fieldName)) {
            return java.util.Optional.of(Comparator.comparing(value -> value.path("layoutKind").asText()));
        }
        if ("nodes".equals(fieldName)) {
            return java.util.Optional.of(
                Comparator.comparing((JsonNode value) -> value.path("parentKey").asText(""))
                    .thenComparingInt(value -> value.path("sortOrder").asInt())
                    .thenComparing(value -> value.path("nodeKey").asText())
            );
        }
        if ("policies".equals(fieldName)) {
            return java.util.Optional.of(
                Comparator.comparing((JsonNode value) -> value.path("fieldKey").asText())
                    .thenComparing(value -> value.path("policyKey").asText())
            );
        }
        if ("states".equals(fieldName)) {
            return java.util.Optional.of(byOrderThenKey("stateKey"));
        }
        if ("actions".equals(fieldName)) {
            return java.util.Optional.of(byOrderThenKey("actionKey"));
        }
        if ("transitions".equals(fieldName)) {
            return java.util.Optional.of(byOrderThenKey("transitionKey"));
        }
        if ("guards".equals(fieldName)) {
            return java.util.Optional.of(Comparator.comparing(value -> value.path("guardKey").asText()));
        }
        if ("stages".equals(fieldName)) {
            return java.util.Optional.of(byOrderThenKey("stageKey"));
        }
        if ("edges".equals(fieldName)) {
            return java.util.Optional.of(
                Comparator.comparing((JsonNode value) -> value.path("fromNodeKey").asText())
                    .thenComparingInt(value -> value.path("priority").asInt())
                    .thenComparing(value -> value.path("edgeKey").asText())
            );
        }
        if ("branches".equals(fieldName)) {
            return java.util.Optional.of(Comparator.comparing(value -> value.path("branchKey").asText()));
        }
        if ("joins".equals(fieldName)) {
            return java.util.Optional.of(Comparator.comparing(value -> value.path("joinKey").asText()));
        }
        if ("recoveryCommands".equals(fieldName)) {
            return java.util.Optional.of(Comparator.comparing(value -> value.path("commandKey").asText()));
        }
        if ("compensations".equals(fieldName)) {
            return java.util.Optional.of(
                Comparator.comparing((JsonNode value) -> value.path("commandKey").asText())
                    .thenComparingInt(value -> value.path("sortOrder").asInt())
                    .thenComparing(value -> value.path("compensationKey").asText())
            );
        }
        if (List.of(
            "authorizedRoles", "requiredFieldKeys", "sideEffectKeys", "spaceRoles", "guardKeys",
            "candidateRoles", "explicitUserIds", "participantRoles", "fieldParticipantKeys",
            "objectTypes", "edgeKeys", "inboundEdgeKeys", "fromNodeKeys"
        ).contains(fieldName)) {
            return java.util.Optional.of(Comparator.comparing(JsonNode::asText));
        }
        return java.util.Optional.empty();
    }

    private Comparator<JsonNode> byOrderThenKey(String key) {
        return Comparator.comparingInt((JsonNode value) -> value.path("sortOrder").asInt())
            .thenComparing(value -> value.path(key).asText())
            .thenComparing(value -> value.path("id").asText());
    }

    private String hash(JsonNode payload) {
        try {
            byte[] serialized = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw failure("CONFIGURATION_HASH_FAILED", "Unable to hash configuration snapshot", exception);
        }
    }
}
