package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.application.WorkItemFieldAccessPolicyEvaluator.EvaluationContext;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkItemRuntimeProjection {
    private final WorkItemFieldAccessPolicyEvaluator policyEvaluator;
    private final ObjectMapper objectMapper;

    public WorkItemRuntimeProjection(
        WorkItemFieldAccessPolicyEvaluator policyEvaluator,
        ObjectMapper objectMapper
    ) {
        this.policyEvaluator = policyEvaluator;
        this.objectMapper = objectMapper;
    }

    public JsonNode prepareCreate(
        RuntimeConfiguration configuration,
        String role,
        String spaceStatus,
        JsonNode requestedValues
    ) {
        ObjectNode values = objectMapper.createObjectNode();
        for (JsonNode field : configuration.snapshot().path("fields")) {
            JsonNode defaultValue = field.path("config").get("defaultValue");
            if (defaultValue != null && !defaultValue.isNull()) {
                values.set(field.path("fieldKey").asText(), defaultValue.deepCopy());
            }
        }
        applyWritable(configuration, role, spaceStatus, "create", values, requestedValues);
        requireFields(configuration, role, spaceStatus, "create", values);
        return values;
    }

    public JsonNode prepareUpdate(
        RuntimeConfiguration configuration,
        String role,
        String spaceStatus,
        JsonNode currentValues,
        JsonNode requestedValues
    ) {
        ObjectNode values = object(currentValues).deepCopy();
        applyWritable(configuration, role, spaceStatus, "detail", values, requestedValues);
        requireFields(configuration, role, spaceStatus, "detail", values);
        return values;
    }

    public JsonNode projectDetail(
        RuntimeConfiguration configuration,
        String role,
        String spaceStatus,
        JsonNode storedValues
    ) {
        ObjectNode source = object(storedValues);
        ObjectNode projected = objectMapper.createObjectNode();
        Map<String, JsonNode> context = valueMap(source);
        for (JsonNode field : configuration.snapshot().path("fields")) {
            String key = field.path("fieldKey").asText();
            if (!source.has(key) || "hidden".equals(mode(
                configuration, field, role, spaceStatus, "detail", context
            ))) {
                continue;
            }
            projected.set(key, source.get(key).deepCopy());
        }
        return projected;
    }

    public JsonNode requireQueryableField(
        RuntimeConfiguration configuration,
        String role,
        String spaceStatus,
        String fieldKey
    ) {
        JsonNode field = fields(configuration).get(fieldKey);
        if (field == null || !"active".equals(field.path("status").asText())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Work item field is not available");
        }
        String mode = mode(
            configuration,
            field,
            role,
            spaceStatus,
            "detail",
            Map.of()
        );
        if ("hidden".equals(mode)) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Work item field is not available");
        }
        return field;
    }

    public JsonNode runtimePresentation(
        RuntimeConfiguration configuration,
        String role,
        String spaceStatus,
        String layoutKind,
        JsonNode values
    ) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("snapshotSchemaVersion", configuration.snapshotSchemaVersion());
        result.put("typeVersionId", configuration.versionId().toString());
        result.put("configHash", configuration.configHash());
        result.put("layoutKind", layoutKind);
        ObjectNode access = result.putObject("accessProjection");
        Map<String, JsonNode> context = valueMap(object(values));
        Set<String> hiddenFields = new HashSet<>();
        for (JsonNode field : configuration.snapshot().path("fields")) {
            if (!"active".equals(field.path("status").asText())) {
                continue;
            }
            var decision = decision(configuration, field, role, spaceStatus, layoutKind, context);
            String fieldKey = field.path("fieldKey").asText();
            if ("hidden".equals(decision.mode())) {
                hiddenFields.add(fieldKey);
                continue;
            }
            access.set(fieldKey, objectMapper.valueToTree(decision));
        }
        result.set("snapshot", visibleSnapshot(configuration.snapshot(), hiddenFields));
        return result;
    }

    private JsonNode visibleSnapshot(JsonNode snapshot, Set<String> hiddenFields) {
        ObjectNode visible = snapshot.deepCopy();
        ArrayNode fields = objectMapper.createArrayNode();
        snapshot.path("fields").forEach(field -> {
            if (!hiddenFields.contains(field.path("fieldKey").asText())) {
                fields.add(field.deepCopy());
            }
        });
        visible.set("fields", fields);

        ArrayNode layouts = objectMapper.createArrayNode();
        snapshot.path("layouts").forEach(layout -> {
            ObjectNode projectedLayout = layout.deepCopy();
            ArrayNode nodes = objectMapper.createArrayNode();
            layout.path("nodes").forEach(node -> {
                if (!hiddenFields.contains(node.path("fieldKey").asText())) {
                    nodes.add(node.deepCopy());
                }
            });
            ArrayNode policies = objectMapper.createArrayNode();
            layout.path("policies").forEach(policy -> {
                if (!hiddenFields.contains(policy.path("fieldKey").asText())) {
                    policies.add(policy.deepCopy());
                }
            });
            projectedLayout.set("nodes", nodes);
            projectedLayout.set("policies", policies);
            layouts.add(projectedLayout);
        });
        visible.set("layouts", layouts);
        return visible;
    }

    private void applyWritable(
        RuntimeConfiguration configuration,
        String role,
        String spaceStatus,
        String layoutKind,
        ObjectNode target,
        JsonNode requested
    ) {
        ObjectNode input = object(requested);
        Map<String, JsonNode> fields = fields(configuration);
        Map<String, JsonNode> context = valueMap(target);
        input.fields().forEachRemaining(entry -> {
            JsonNode field = fields.get(entry.getKey());
            if (field == null || !"active".equals(field.path("status").asText())) {
                throw failure("NOT_FOUND_OR_HIDDEN", "Work item field is not available");
            }
            String mode = mode(configuration, field, role, spaceStatus, layoutKind, context);
            if ("hidden".equals(mode)) {
                throw failure("NOT_FOUND_OR_HIDDEN", "Work item field is not available");
            }
            if (!"write".equals(mode)) {
                throw failure("FORBIDDEN", "Work item field is read-only");
            }
            if (entry.getValue() == null || entry.getValue().isNull()) {
                target.remove(entry.getKey());
            } else {
                target.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    private void requireFields(
        RuntimeConfiguration configuration,
        String role,
        String spaceStatus,
        String layoutKind,
        ObjectNode values
    ) {
        Map<String, JsonNode> context = valueMap(values);
        for (JsonNode field : configuration.snapshot().path("fields")) {
            String key = field.path("fieldKey").asText();
            if (!"active".equals(field.path("status").asText())) {
                continue;
            }
            var decision = decision(configuration, field, role, spaceStatus, layoutKind, context);
            boolean required = field.path("config").path("required").asBoolean(false)
                || decision.required();
            if (required && "write".equals(decision.mode())
                && (!values.has(key) || values.get(key).isNull() || blank(values.get(key)))) {
                throw failure("REQUIRED_FIELD_MISSING", "Required work item field is missing");
            }
        }
    }

    private boolean blank(JsonNode value) {
        return value.isTextual() && value.asText().isBlank();
    }

    private String mode(
        RuntimeConfiguration configuration,
        JsonNode field,
        String role,
        String spaceStatus,
        String layoutKind,
        Map<String, JsonNode> values
    ) {
        return decision(configuration, field, role, spaceStatus, layoutKind, values).mode();
    }

    private WorkItemFieldAccessPolicyEvaluator.FieldAccessDecision decision(
        RuntimeConfiguration configuration,
        JsonNode field,
        String role,
        String spaceStatus,
        String layoutKind,
        Map<String, JsonNode> values
    ) {
        return policyEvaluator.evaluate(
            policy(configuration.snapshot(), layoutKind, field.path("fieldKey").asText()),
            new EvaluationContext(
                role,
                spaceStatus,
                configuration.snapshot().path("typeDefinition").path("status").asText("active"),
                field.path("status").asText("active"),
                layoutKind,
                false,
                values
            )
        );
    }

    private JsonNode policy(JsonNode snapshot, String layoutKind, String fieldKey) {
        for (JsonNode layout : snapshot.path("layouts")) {
            if (!layoutKind.equals(layout.path("layoutKind").asText())) {
                continue;
            }
            for (JsonNode policy : layout.path("policies")) {
                if (fieldKey.equals(policy.path("fieldKey").asText())) {
                    return policy.path("policy");
                }
            }
        }
        return null;
    }

    private Map<String, JsonNode> fields(RuntimeConfiguration configuration) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode field : configuration.snapshot().path("fields")) {
            result.put(field.path("fieldKey").asText(), field);
        }
        return result;
    }

    private Map<String, JsonNode> valueMap(ObjectNode values) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        values.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private ObjectNode object(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw failure("INVALID_FIELD_VALUES", "Work item field values must be an object");
        }
        return (ObjectNode) value;
    }
}
