package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.application.WorkItemFieldTypeRegistry.FieldTypeDescriptor;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Canonical value boundary for the published work-item snapshot.
 *
 * <p>Unset and explicit JSON null both mean "remove the value"; persisted canonical values never
 * contain null. Arrays are sets and are sorted so semantically equal values share one hash.
 * Interval and computed values remain explicitly unsupported until they are registered by a later
 * stage.</p>
 */
@Component
public class WorkItemFieldValueCodec {
    private final WorkItemFieldTypeRegistry registry;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;

    public WorkItemFieldValueCodec(
        WorkItemFieldTypeRegistry registry,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
    }

    public CanonicalValues canonicalize(RuntimeConfiguration configuration, JsonNode input) {
        if (input == null || !input.isObject()) {
            throw failure("INVALID_FIELD_VALUES", "Work item field values must be an object");
        }
        Map<String, JsonNode> fields = fields(configuration.snapshot());
        ObjectNode values = objectMapper.createObjectNode();
        List<FieldProjection> projections = new ArrayList<>();
        input.fields().forEachRemaining(entry -> {
            JsonNode field = fields.get(entry.getKey());
            if (field == null || !"active".equals(field.path("status").asText())) {
                throw failure("NOT_FOUND_OR_HIDDEN", "Work item field is not available");
            }
            if (entry.getValue() == null || entry.getValue().isNull()) {
                return;
            }
            FieldTypeDescriptor descriptor = registry.require(field.path("fieldType").asText());
            JsonNode normalized = normalize(descriptor.key(), entry.getValue(), field);
            validateRules(normalized, field.path("config").path("validationRules"));
            values.set(entry.getKey(), normalized);
            projections.add(projection(
                entry.getKey(), descriptor, configuration.configHash(), normalized
            ));
        });
        JsonNode canonical = canonicalizer.sort(values);
        return new CanonicalValues(canonical, canonicalizer.hash(canonical), List.copyOf(projections));
    }

    private JsonNode normalize(String type, JsonNode value, JsonNode field) {
        return switch (type) {
            case "text" -> text(value, "Field value must be text", field);
            case "number" -> number(value);
            case "boolean" -> bool(value);
            case "single_select" -> option(value, field);
            case "multi_select" -> options(value, field);
            case "user", "attachment", "work_item_reference" -> references(value, type, field);
            case "date" -> date(value, field);
            case "datetime" -> datetime(value, field);
            case "url" -> url(value, field);
            default -> throw failure(
                "FIELD_VALUE_TYPE_UNSUPPORTED",
                "The published field type is not supported by this runtime"
            );
        };
    }

    private JsonNode text(JsonNode value, String message) {
        if (!value.isTextual()) {
            throw failure("INVALID_FIELD_VALUE", message);
        }
        return TextNode.valueOf(value.textValue());
    }

    private JsonNode text(JsonNode value, String message, JsonNode field) {
        JsonNode normalized = text(value, message);
        // Legacy published snapshots may not carry the presentation-era limit yet.
        // Keep them readable while newly canonicalized fields always publish an explicit limit.
        int maximum = field.path("config").path("typeConfig").path("maxLength").asInt(100000);
        if (normalized.asText().codePointCount(0, normalized.asText().length()) > maximum) {
            throw failure("INVALID_FIELD_VALUE", "Text value exceeds the published maximum length");
        }
        return normalized;
    }

    private JsonNode number(JsonNode value) {
        if (!value.isNumber()) {
            throw failure("INVALID_FIELD_VALUE", "Field value must be a finite number");
        }
        BigDecimal decimal = value.decimalValue().stripTrailingZeros();
        if (decimal.scale() < 0) {
            decimal = decimal.setScale(0);
        }
        return DecimalNode.valueOf(decimal);
    }

    private JsonNode bool(JsonNode value) {
        if (!value.isBoolean()) {
            throw failure("INVALID_FIELD_VALUE", "Field value must be boolean");
        }
        return objectMapper.getNodeFactory().booleanNode(value.booleanValue());
    }

    private JsonNode option(JsonNode value, JsonNode field) {
        String key = text(value, "Select value must be an option key").asText();
        if (!activeOptions(field).contains(key)) {
            throw failure("INVALID_FIELD_OPTION", "Select value is not an active published option");
        }
        return TextNode.valueOf(key);
    }

    private JsonNode options(JsonNode value, JsonNode field) {
        if (!value.isArray()) {
            throw failure("INVALID_FIELD_VALUE", "Multi-select value must be an array");
        }
        Set<String> allowed = activeOptions(field);
        Set<String> unique = new HashSet<>();
        for (JsonNode item : value) {
            String key = text(item, "Multi-select entries must be option keys").asText();
            if (!allowed.contains(key)) {
                throw failure("INVALID_FIELD_OPTION", "Multi-select value contains an unavailable option");
            }
            unique.add(key);
        }
        ArrayNode result = objectMapper.createArrayNode();
        unique.stream().sorted().forEach(result::add);
        return result;
    }

    private JsonNode references(JsonNode value, String type, JsonNode field) {
        if (!value.isArray()) {
            throw failure("INVALID_FIELD_VALUE", "Reference value must be an array");
        }
        int maximum = switch (type) {
            case "user" -> field.path("config").path("typeConfig").path("maxSelections").asInt(100);
            case "attachment" -> field.path("config").path("typeConfig").path("maxFiles").asInt(10);
            default -> field.path("config").path("typeConfig").path("maxReferences").asInt(10);
        };
        Set<String> unique = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw failure("INVALID_FIELD_REFERENCE", "Reference identifiers must be UUID strings");
            }
            try {
                unique.add(UUID.fromString(item.asText()).toString());
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_FIELD_REFERENCE", "Reference identifiers must be UUID strings");
            }
        }
        if (unique.size() > maximum) {
            throw failure("INVALID_FIELD_VALUE", "Reference value exceeds the published selection limit");
        }
        ArrayNode result = objectMapper.createArrayNode();
        unique.stream().sorted().forEach(result::add);
        return result;
    }

    private JsonNode date(JsonNode value, JsonNode field) {
        String candidate = text(value, "Date value must use ISO-8601").asText();
        try {
            LocalDate parsed = LocalDate.parse(candidate);
            JsonNode config = field.path("config").path("typeConfig");
            if (config.hasNonNull("min") && parsed.isBefore(LocalDate.parse(config.path("min").asText()))
                || config.hasNonNull("max") && parsed.isAfter(LocalDate.parse(config.path("max").asText()))) {
                throw failure("INVALID_FIELD_VALUE", "Date value is outside the published range");
            }
            return TextNode.valueOf(parsed.toString());
        } catch (java.time.DateTimeException exception) {
            throw failure("INVALID_FIELD_VALUE", "Date value must use ISO-8601");
        }
    }

    private JsonNode datetime(JsonNode value, JsonNode field) {
        String candidate = text(value, "Datetime value must be an ISO-8601 instant").asText();
        try {
            Instant instant = Instant.parse(candidate);
            String precision = field.path("config").path("typeConfig").path("precision").asText("second");
            Instant normalized = switch (precision) {
                case "minute" -> instant.truncatedTo(ChronoUnit.MINUTES);
                case "millisecond" -> instant.truncatedTo(ChronoUnit.MILLIS);
                default -> instant.truncatedTo(ChronoUnit.SECONDS);
            };
            return TextNode.valueOf(normalized.toString());
        } catch (java.time.DateTimeException exception) {
            throw failure("INVALID_FIELD_VALUE", "Datetime value must be an ISO-8601 instant");
        }
    }

    private JsonNode url(JsonNode value, JsonNode field) {
        String candidate = text(value, "URL value must be text").asText().trim();
        JsonNode config = field.path("config").path("typeConfig");
        if (candidate.length() > config.path("maxLength").asInt(2048)) {
            throw failure("INVALID_FIELD_VALUE", "URL value exceeds the published length limit");
        }
        try {
            URI uri = URI.create(candidate).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean allowed = false;
            for (JsonNode item : config.path("allowedSchemes")) {
                allowed |= scheme.equals(item.asText().toLowerCase(Locale.ROOT));
            }
            if (!allowed || uri.getHost() == null || uri.getUserInfo() != null) {
                throw failure("INVALID_FIELD_VALUE", "URL value violates the published URL policy");
            }
            return TextNode.valueOf(uri.toASCIIString());
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_FIELD_VALUE", "URL value is invalid");
        }
    }

    private void validateRules(JsonNode value, JsonNode rules) {
        for (JsonNode rule : rules) {
            String kind = rule.path("kind").asText();
            JsonNode parameters = rule.path("parameters");
            if ("length".equals(kind) && value.isTextual()) {
                int length = value.asText().codePointCount(0, value.asText().length());
                int min = parameters.path("min").asInt(0);
                int max = parameters.path("max").asInt(Integer.MAX_VALUE);
                if (length < min || length > max) {
                    throw failure("FIELD_VALIDATION_FAILED", "Field value violates a length rule");
                }
            }
            if ("number_range".equals(kind) && value.isNumber()) {
                BigDecimal candidate = value.decimalValue();
                if (parameters.hasNonNull("min")
                    && candidate.compareTo(parameters.path("min").decimalValue()) < 0
                    || parameters.hasNonNull("max")
                    && candidate.compareTo(parameters.path("max").decimalValue()) > 0) {
                    throw failure("FIELD_VALIDATION_FAILED", "Field value violates a numeric range rule");
                }
            }
        }
    }

    private FieldProjection projection(
        String key,
        FieldTypeDescriptor descriptor,
        String configHash,
        JsonNode value
    ) {
        String text = switch (descriptor.key()) {
            case "text", "url", "single_select" -> value.asText();
            default -> null;
        };
        BigDecimal number = "number".equals(descriptor.key()) ? value.decimalValue() : null;
        Boolean bool = "boolean".equals(descriptor.key()) ? value.booleanValue() : null;
        LocalDate date = "date".equals(descriptor.key()) ? LocalDate.parse(value.asText()) : null;
        Instant timestamp = "datetime".equals(descriptor.key()) ? Instant.parse(value.asText()) : null;
        JsonNode references = switch (descriptor.key()) {
            case "multi_select", "user", "attachment", "work_item_reference" -> value.deepCopy();
            default -> null;
        };
        return new FieldProjection(
            key,
            descriptor.key(),
            configHash,
            canonicalizer.hash(value),
            value.deepCopy(),
            text,
            number,
            bool,
            date,
            timestamp,
            references,
            descriptor.filterable(),
            descriptor.sortable(),
            descriptor.indexCapability()
        );
    }

    private Map<String, JsonNode> fields(JsonNode snapshot) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode field : snapshot.path("fields")) {
            result.put(field.path("fieldKey").asText(), field);
        }
        return result;
    }

    private Set<String> activeOptions(JsonNode field) {
        Set<String> result = new HashSet<>();
        for (JsonNode option : field.path("options")) {
            if (!option.has("status") || "active".equals(option.path("status").asText())) {
                result.add(option.path("optionKey").asText());
            }
        }
        return result;
    }

    public record CanonicalValues(JsonNode values, String hash, List<FieldProjection> projections) {
    }

    public record FieldProjection(
        String fieldKey,
        String fieldType,
        String configHash,
        String canonicalHash,
        JsonNode canonicalValue,
        String textValue,
        BigDecimal numberValue,
        Boolean booleanValue,
        LocalDate dateValue,
        Instant timestampValue,
        JsonNode referenceValues,
        boolean filterable,
        boolean sortable,
        String indexCapability
    ) {
    }
}
