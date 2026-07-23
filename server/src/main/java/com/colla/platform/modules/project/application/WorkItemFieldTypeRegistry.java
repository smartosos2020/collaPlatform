package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkItemFieldTypeRegistry {
    private final Map<String, FieldTypeDescriptor> descriptors;

    public WorkItemFieldTypeRegistry(ObjectMapper objectMapper) {
        Map<String, FieldTypeDescriptor> values = new LinkedHashMap<>();
        register(values, descriptor(objectMapper, "text", "string", List.of("eq", "neq", "contains", "is_empty"), true, true, "text", false, List.of("length", "regex", "format", "allowed_values")));
        register(values, descriptor(objectMapper, "number", "decimal", List.of("eq", "neq", "gt", "gte", "lt", "lte", "is_empty"), true, true, "numeric", false, List.of("number_range", "number_precision", "allowed_values")));
        register(values, descriptor(objectMapper, "boolean", "boolean", List.of("eq", "is_empty"), true, true, "scalar", false, List.of("allowed_values")));
        register(values, descriptor(objectMapper, "single_select", "option_key", List.of("eq", "neq", "in", "is_empty"), true, true, "keyword", true, List.of("allowed_values")));
        register(values, descriptor(objectMapper, "multi_select", "option_keys", List.of("contains_any", "contains_all", "is_empty"), true, false, "keyword_array", true, List.of("allowed_values")));
        register(values, descriptor(objectMapper, "user", "principal_refs", List.of("contains_any", "contains_all", "is_empty"), true, false, "principal_array", false, List.of()));
        register(values, descriptor(objectMapper, "date", "date", List.of("eq", "before", "after", "between", "is_empty"), true, true, "date", false, List.of()));
        register(values, descriptor(objectMapper, "datetime", "instant", List.of("eq", "before", "after", "between", "is_empty"), true, true, "timestamp", false, List.of()));
        register(values, descriptor(objectMapper, "url", "string", List.of("eq", "contains", "is_empty"), true, false, "text", false, List.of("length", "regex", "format")));
        register(values, descriptor(objectMapper, "attachment", "file_refs", List.of("contains_any", "is_empty"), true, false, "file_array", false, List.of()));
        register(values, descriptor(objectMapper, "work_item_reference", "work_item_refs", List.of("contains_any", "is_empty"), true, false, "reference_array", false, List.of()));
        descriptors = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public List<FieldTypeDescriptor> list() {
        return descriptors.values().stream().toList();
    }

    public FieldTypeDescriptor require(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        FieldTypeDescriptor descriptor = descriptors.get(key);
        if (descriptor == null) {
            throw new WorkItemFieldException("FIELD_TYPE_UNSUPPORTED", "Unsupported work item field type");
        }
        return descriptor;
    }

    private void register(Map<String, FieldTypeDescriptor> target, FieldTypeDescriptor descriptor) {
        if (target.putIfAbsent(descriptor.key(), descriptor) != null) {
            throw new IllegalStateException("Duplicate work item field type: " + descriptor.key());
        }
    }

    private FieldTypeDescriptor descriptor(
        ObjectMapper objectMapper,
        String key,
        String storageKind,
        List<String> operators,
        boolean filterable,
        boolean sortable,
        String indexCapability,
        boolean supportsOptions,
        List<String> validationRuleKinds
    ) {
        JsonNode valueSchema = valueSchema(objectMapper, key);
        JsonNode typeConfigSchema = typeConfigSchema(objectMapper, key);
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("schemaVersion").put("type", "integer").put("const", 1);
        properties.putObject("required").put("type", "boolean");
        properties.set("defaultValue", valueSchema.deepCopy());
        properties.putObject("validationRules").put("type", "array").put("maxItems", 20);
        properties.set("typeConfig", typeConfigSchema.deepCopy());
        schema.putArray("required")
            .add("schemaVersion")
            .add("required")
            .add("defaultValue")
            .add("validationRules")
            .add("typeConfig");

        ObjectNode defaultConfig = objectMapper.createObjectNode();
        defaultConfig.put("schemaVersion", WorkItemFieldConfigCanonicalizer.CONFIG_SCHEMA_VERSION);
        defaultConfig.put("required", false);
        defaultConfig.putNull("defaultValue");
        defaultConfig.putArray("validationRules");
        defaultConfig.set("typeConfig", defaultTypeConfig(objectMapper, key));
        String referencePolicy = switch (key) {
            case "user" -> "identity_module";
            case "attachment" -> "file_module";
            case "work_item_reference" -> "work_item_type_module";
            default -> "not_applicable";
        };
        String invalidReferencePolicy = "not_applicable".equals(referencePolicy)
            ? "not_applicable"
            : "unavailable_without_snapshot";
        return new FieldTypeDescriptor(
            key,
            storageKind,
            1,
            List.copyOf(operators),
            filterable,
            sortable,
            indexCapability,
            supportsOptions,
            List.copyOf(validationRuleKinds),
            valueSchema,
            typeConfigSchema,
            referencePolicy,
            invalidReferencePolicy,
            schema,
            defaultConfig
        );
    }

    private JsonNode valueSchema(ObjectMapper objectMapper, String key) {
        ObjectNode schema = objectMapper.createObjectNode();
        switch (key) {
            case "text", "url", "date", "datetime", "single_select" -> schema.put("type", "string");
            case "number" -> schema.put("type", "number");
            case "boolean" -> schema.put("type", "boolean");
            case "multi_select" -> {
                schema.put("type", "array");
                schema.putObject("items").put("type", "string");
                schema.put("uniqueItems", true);
            }
            case "user", "attachment", "work_item_reference" -> {
                schema.put("type", "array");
                schema.putObject("items").put("type", "string").put("format", "uuid");
                schema.put("uniqueItems", true);
            }
            default -> throw new IllegalStateException("Missing value schema for " + key);
        }
        schema.put("nullable", true);
        return schema;
    }

    private JsonNode typeConfigSchema(ObjectMapper objectMapper, String key) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        switch (key) {
            case "user" -> {
                properties.putObject("allowedSubjectTypes").put("type", "array").put("uniqueItems", true);
                properties.putObject("selectionScope").put("type", "array").put("maxItems", 100);
                properties.putObject("maxSelections").put("type", "integer").put("minimum", 1).put("maximum", 100);
                required.add("allowedSubjectTypes").add("selectionScope").add("maxSelections");
            }
            case "date" -> {
                properties.putObject("calendar").put("type", "string").put("const", "iso8601");
                properties.putObject("precision").put("type", "string").put("const", "day");
                properties.putObject("defaultStrategy").put("type", "string");
                properties.putObject("min").put("type", "string").put("format", "date").put("nullable", true);
                properties.putObject("max").put("type", "string").put("format", "date").put("nullable", true);
                required.add("calendar").add("precision").add("defaultStrategy").add("min").add("max");
            }
            case "datetime" -> {
                properties.putObject("storageTimezone").put("type", "string").put("const", "UTC");
                properties.putObject("displayTimezone").put("type", "string");
                properties.putObject("precision").put("type", "string");
                properties.putObject("defaultStrategy").put("type", "string");
                properties.putObject("min").put("type", "string").put("format", "date-time").put("nullable", true);
                properties.putObject("max").put("type", "string").put("format", "date-time").put("nullable", true);
                required.add("storageTimezone").add("displayTimezone").add("precision")
                    .add("defaultStrategy").add("min").add("max");
            }
            case "url" -> {
                properties.putObject("allowedSchemes").put("type", "array").put("uniqueItems", true);
                properties.putObject("maxLength").put("type", "integer").put("minimum", 1).put("maximum", 4096);
                properties.putObject("allowCredentials").put("type", "boolean").put("const", false);
                required.add("allowedSchemes").add("maxLength").add("allowCredentials");
            }
            case "attachment" -> {
                properties.putObject("maxFiles").put("type", "integer").put("minimum", 1).put("maximum", 100);
                properties.putObject("allowedContentTypes").put("type", "array").put("uniqueItems", true);
                properties.putObject("maxFileSizeBytes").put("type", "integer").put("minimum", 1);
                required.add("maxFiles").add("allowedContentTypes").add("maxFileSizeBytes");
            }
            case "work_item_reference" -> {
                properties.putObject("targetTypeIds").put("type", "array").put("uniqueItems", true);
                properties.putObject("maxReferences").put("type", "integer").put("minimum", 1).put("maximum", 100);
                properties.putObject("direction").put("type", "string").put("const", "outbound");
                properties.putObject("relationCapability").put("type", "string").put("const", "deferred");
                required.add("targetTypeIds").add("maxReferences").add("direction").add("relationCapability");
            }
            default -> {
                // Scalar and select fields intentionally have no type-specific configuration in S04.
            }
        }
        return schema;
    }

    private JsonNode defaultTypeConfig(ObjectMapper objectMapper, String key) {
        ObjectNode config = objectMapper.createObjectNode();
        switch (key) {
            case "user" -> {
                config.putArray("allowedSubjectTypes").add("member").add("department").add("user_group");
                config.putArray("selectionScope");
                config.put("maxSelections", 100);
            }
            case "date" -> {
                config.put("calendar", "iso8601");
                config.put("precision", "day");
                config.put("defaultStrategy", "none");
                config.putNull("min");
                config.putNull("max");
            }
            case "datetime" -> {
                config.put("storageTimezone", "UTC");
                config.put("displayTimezone", "UTC");
                config.put("precision", "second");
                config.put("defaultStrategy", "none");
                config.putNull("min");
                config.putNull("max");
            }
            case "url" -> {
                config.putArray("allowedSchemes").add("https");
                config.put("maxLength", 2048);
                config.put("allowCredentials", false);
            }
            case "attachment" -> {
                config.put("maxFiles", 10);
                config.putArray("allowedContentTypes");
                config.put("maxFileSizeBytes", 104857600L);
            }
            case "work_item_reference" -> {
                config.putArray("targetTypeIds");
                config.put("maxReferences", 10);
                config.put("direction", "outbound");
                config.put("relationCapability", "deferred");
            }
            default -> {
                // Keep an explicit empty object so the canonical envelope is stable across all types.
            }
        }
        return config;
    }

    public record FieldTypeDescriptor(
        String key,
        String storageKind,
        int configSchemaVersion,
        List<String> operators,
        boolean filterable,
        boolean sortable,
        String indexCapability,
        boolean supportsOptions,
        List<String> validationRuleKinds,
        JsonNode valueSchema,
        JsonNode typeConfigSchema,
        String referencePolicy,
        String invalidReferencePolicy,
        JsonNode configSchema,
        JsonNode defaultConfig
    ) {
    }
}
