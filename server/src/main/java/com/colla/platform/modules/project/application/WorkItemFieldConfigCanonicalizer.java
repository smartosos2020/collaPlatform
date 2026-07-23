package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.application.WorkItemFieldTypeRegistry.FieldTypeDescriptor;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

@Component
public class WorkItemFieldConfigCanonicalizer {
    public static final int CONFIG_SCHEMA_VERSION = 1;
    public static final int RULE_SCHEMA_VERSION = 1;
    private static final Pattern RULE_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern MIME_TYPE = Pattern.compile(
        "[a-z0-9][a-z0-9!#$&^_.+-]*/(?:[a-z0-9][a-z0-9!#$&^_.+-]*|\\*)"
    );
    private static final Set<String> TOP_LEVEL_PROPERTIES = Set.of(
        "schemaVersion", "required", "defaultValue", "validationRules", "typeConfig"
    );

    private final WorkItemFieldTypeRegistry registry;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;

    public WorkItemFieldConfigCanonicalizer(
        WorkItemFieldTypeRegistry registry,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
    }

    public CanonicalFieldConfig canonicalize(String fieldType, JsonNode config) {
        return canonicalize(fieldType, config, List.of());
    }

    public CanonicalFieldConfig canonicalize(
        String fieldType,
        JsonNode config,
        List<ConfigureFieldOption> options
    ) {
        FieldTypeDescriptor descriptor = registry.require(fieldType);
        JsonNode value = config == null || config.isNull() || (config.isObject() && config.isEmpty())
            ? descriptor.defaultConfig()
            : config;
        requireObject(value, "INVALID_FIELD_CONFIGURATION", "Field configuration must be an object");
        rejectUnknown(value, TOP_LEVEL_PROPERTIES, "INVALID_FIELD_CONFIGURATION", "Unknown field configuration property");
        int schemaVersion = integer(value, "schemaVersion", CONFIG_SCHEMA_VERSION, "INVALID_FIELD_CONFIGURATION");
        if (schemaVersion != CONFIG_SCHEMA_VERSION) {
            throw failure("INVALID_FIELD_CONFIGURATION", "Unsupported field configuration schema version");
        }
        boolean required = booleanValue(value, "required", false, "INVALID_FIELD_CONFIGURATION");
        Map<String, ConfigureFieldOption> optionMap = optionMap(options);
        JsonNode typeConfig = normalizeTypeConfig(
            descriptor.key(),
            value.get("typeConfig"),
            descriptor.defaultConfig().path("typeConfig")
        );
        JsonNode defaultValue = normalizeDefault(descriptor.key(), value.get("defaultValue"), optionMap, typeConfig);
        ArrayNode rules = normalizeRules(descriptor.key(), value.get("validationRules"), optionMap);
        validateDefaultAgainstRules(descriptor.key(), defaultValue, rules);
        validateComplexDefault(descriptor.key(), defaultValue, typeConfig);

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("schemaVersion", CONFIG_SCHEMA_VERSION);
        normalized.put("required", required);
        normalized.set("defaultValue", defaultValue);
        normalized.set("validationRules", rules);
        normalized.set("typeConfig", typeConfig);
        JsonNode canonical = canonicalizer.sort(normalized);
        return new CanonicalFieldConfig(canonical, canonicalizer.hash(canonical));
    }

    private Map<String, ConfigureFieldOption> optionMap(List<ConfigureFieldOption> options) {
        Map<String, ConfigureFieldOption> result = new LinkedHashMap<>();
        for (ConfigureFieldOption option : options == null ? List.<ConfigureFieldOption>of() : options) {
            if (result.putIfAbsent(option.optionKey(), option) != null) {
                throw failure("INVALID_FIELD_OPTION", "Option keys must be unique within a field");
            }
        }
        return result;
    }

    private JsonNode normalizeDefault(
        String fieldType,
        JsonNode value,
        Map<String, ConfigureFieldOption> options,
        JsonNode typeConfig
    ) {
        if (value == null || value.isNull()) {
            return objectMapper.nullNode();
        }
        return switch (fieldType) {
            case "text" -> requireText(value);
            case "number" -> requireNumber(value);
            case "boolean" -> requireBoolean(value);
            case "single_select" -> requireOption(value, options);
            case "multi_select" -> requireOptions(value, options);
            case "user" -> requireUuidArray(value, "User default", typeConfig.path("maxSelections").asInt());
            case "date" -> requireDate(value);
            case "datetime" -> requireInstant(value, typeConfig.path("precision").asText());
            case "url" -> requireUrl(value, typeConfig);
            case "attachment" -> requireUuidArray(value, "Attachment default", typeConfig.path("maxFiles").asInt());
            case "work_item_reference" -> requireUuidArray(
                value, "Work-item reference default", typeConfig.path("maxReferences").asInt()
            );
            default -> throw new IllegalStateException("Registered field type has no default normalizer: " + fieldType);
        };
    }

    private JsonNode normalizeTypeConfig(String fieldType, JsonNode value, JsonNode fallback) {
        JsonNode candidate = value == null || value.isNull() ? fallback.deepCopy() : value;
        requireObject(
            candidate,
            "INVALID_COMPLEX_FIELD_CONFIGURATION",
            "Field type configuration must be an object"
        );
        return switch (fieldType) {
            case "user" -> normalizeUserConfig(candidate);
            case "date" -> normalizeDateConfig(candidate);
            case "datetime" -> normalizeDatetimeConfig(candidate);
            case "url" -> normalizeUrlConfig(candidate);
            case "attachment" -> normalizeAttachmentConfig(candidate);
            case "work_item_reference" -> normalizeReferenceConfig(candidate);
            default -> {
                rejectUnknown(
                    candidate,
                    Set.of(),
                    "INVALID_COMPLEX_FIELD_CONFIGURATION",
                    "This field type does not accept type-specific configuration"
                );
                yield objectMapper.createObjectNode();
            }
        };
    }

    private JsonNode normalizeUserConfig(JsonNode value) {
        String code = "INVALID_COMPLEX_FIELD_CONFIGURATION";
        rejectUnknown(value, Set.of("allowedSubjectTypes", "selectionScope", "maxSelections"), code,
            "Unknown user field configuration property");
        List<String> allowedTypes = normalizedStrings(
            value.get("allowedSubjectTypes"),
            Set.of("member", "department", "user_group"),
            3,
            false,
            code,
            "allowedSubjectTypes"
        );
        int maxSelections = integer(value, "maxSelections", 1, code);
        if (maxSelections < 1 || maxSelections > 100) {
            throw failure(code, "maxSelections must be between 1 and 100");
        }
        JsonNode scope = value.get("selectionScope");
        if (scope == null || !scope.isArray() || scope.size() > 100) {
            throw failure(code, "selectionScope must be an array containing at most 100 entries");
        }
        List<ObjectNode> normalizedScope = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonNode entry : scope) {
            requireObject(entry, code, "User selection scope entry must be an object");
            rejectUnknown(entry, Set.of("subjectType", "subjectId"), code,
                "Unknown user selection scope property");
            String subjectType = text(entry, "subjectType", code).toLowerCase(Locale.ROOT);
            if (!allowedTypes.contains(subjectType)) {
                throw failure(code, "selectionScope subjectType must be enabled by allowedSubjectTypes");
            }
            String subjectId = uuidText(entry.get("subjectId"), code, "selectionScope subjectId");
            if (!unique.add(subjectType + ":" + subjectId)) {
                throw failure(code, "selectionScope entries must be unique");
            }
            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("subjectType", subjectType);
            normalized.put("subjectId", subjectId);
            normalizedScope.add(normalized);
        }
        normalizedScope.sort(Comparator.comparing(node ->
            node.path("subjectType").asText() + ":" + node.path("subjectId").asText()
        ));
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode types = result.putArray("allowedSubjectTypes");
        allowedTypes.forEach(types::add);
        ArrayNode scopes = result.putArray("selectionScope");
        normalizedScope.forEach(scopes::add);
        result.put("maxSelections", maxSelections);
        return result;
    }

    private JsonNode normalizeDateConfig(JsonNode value) {
        String code = "INVALID_COMPLEX_FIELD_CONFIGURATION";
        rejectUnknown(value, Set.of("calendar", "precision", "defaultStrategy", "min", "max"), code,
            "Unknown date field configuration property");
        String calendar = text(value, "calendar", code).toLowerCase(Locale.ROOT);
        String precision = text(value, "precision", code).toLowerCase(Locale.ROOT);
        String strategy = text(value, "defaultStrategy", code).toLowerCase(Locale.ROOT);
        if (!"iso8601".equals(calendar) || !"day".equals(precision) || !Set.of("none", "today").contains(strategy)) {
            throw failure(code, "Date fields require iso8601/day and defaultStrategy none or today");
        }
        LocalDate min = optionalDate(value.get("min"), code, "min");
        LocalDate max = optionalDate(value.get("max"), code, "max");
        if (min != null && max != null && min.isAfter(max)) {
            throw failure(code, "Date min cannot be after max");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("calendar", calendar);
        result.put("precision", precision);
        result.put("defaultStrategy", strategy);
        result.set("min", min == null ? objectMapper.nullNode() : objectMapper.getNodeFactory().textNode(min.toString()));
        result.set("max", max == null ? objectMapper.nullNode() : objectMapper.getNodeFactory().textNode(max.toString()));
        return result;
    }

    private JsonNode normalizeDatetimeConfig(JsonNode value) {
        String code = "INVALID_COMPLEX_FIELD_CONFIGURATION";
        rejectUnknown(
            value,
            Set.of("storageTimezone", "displayTimezone", "precision", "defaultStrategy", "min", "max"),
            code,
            "Unknown datetime field configuration property"
        );
        String storageTimezone = text(value, "storageTimezone", code);
        if (!"UTC".equalsIgnoreCase(storageTimezone)) {
            throw failure(code, "Datetime storageTimezone must be UTC");
        }
        String displayTimezone;
        try {
            displayTimezone = ZoneId.of(text(value, "displayTimezone", code)).getId();
        } catch (DateTimeException exception) {
            throw failure(code, "displayTimezone must be a valid IANA timezone");
        }
        String precision = text(value, "precision", code).toLowerCase(Locale.ROOT);
        String strategy = text(value, "defaultStrategy", code).toLowerCase(Locale.ROOT);
        if (!Set.of("minute", "second", "millisecond").contains(precision)
            || !Set.of("none", "now").contains(strategy)) {
            throw failure(code, "Datetime precision or defaultStrategy is unsupported");
        }
        Instant min = optionalInstant(value.get("min"), code, "min", precision);
        Instant max = optionalInstant(value.get("max"), code, "max", precision);
        if (min != null && max != null && min.isAfter(max)) {
            throw failure(code, "Datetime min cannot be after max");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("storageTimezone", "UTC");
        result.put("displayTimezone", displayTimezone);
        result.put("precision", precision);
        result.put("defaultStrategy", strategy);
        result.set("min", min == null ? objectMapper.nullNode() : objectMapper.getNodeFactory().textNode(min.toString()));
        result.set("max", max == null ? objectMapper.nullNode() : objectMapper.getNodeFactory().textNode(max.toString()));
        return result;
    }

    private JsonNode normalizeUrlConfig(JsonNode value) {
        String code = "INVALID_COMPLEX_FIELD_CONFIGURATION";
        rejectUnknown(value, Set.of("allowedSchemes", "maxLength", "allowCredentials"), code,
            "Unknown URL field configuration property");
        List<String> schemes = normalizedStrings(
            value.get("allowedSchemes"),
            Set.of("http", "https"),
            2,
            false,
            code,
            "allowedSchemes"
        );
        int maxLength = integer(value, "maxLength", 2048, code);
        if (maxLength < 1 || maxLength > 4096 || booleanValue(value, "allowCredentials", false, code)) {
            throw failure(code, "URL maxLength must be 1-4096 and credentials cannot be enabled");
        }
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode array = result.putArray("allowedSchemes");
        schemes.forEach(array::add);
        result.put("maxLength", maxLength);
        result.put("allowCredentials", false);
        return result;
    }

    private JsonNode normalizeAttachmentConfig(JsonNode value) {
        String code = "INVALID_COMPLEX_FIELD_CONFIGURATION";
        rejectUnknown(value, Set.of("maxFiles", "allowedContentTypes", "maxFileSizeBytes"), code,
            "Unknown attachment field configuration property");
        int maxFiles = integer(value, "maxFiles", 10, code);
        long maxFileSizeBytes = longValue(value, "maxFileSizeBytes", 104857600L, code);
        if (maxFiles < 1 || maxFiles > 100 || maxFileSizeBytes < 1 || maxFileSizeBytes > 10737418240L) {
            throw failure(code, "Attachment limits are outside the supported range");
        }
        JsonNode contentTypes = value.get("allowedContentTypes");
        if (contentTypes == null || !contentTypes.isArray() || contentTypes.size() > 100) {
            throw failure(code, "allowedContentTypes must be an array containing at most 100 entries");
        }
        Set<String> unique = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (JsonNode item : contentTypes) {
            if (!item.isTextual()) {
                throw failure(code, "allowedContentTypes entries must be strings");
            }
            String contentType = item.textValue().trim().toLowerCase(Locale.ROOT);
            if (!MIME_TYPE.matcher(contentType).matches() || !unique.add(contentType)) {
                throw failure(code, "allowedContentTypes contains an invalid or duplicate MIME type");
            }
            normalized.add(contentType);
        }
        normalized.sort(String::compareTo);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("maxFiles", maxFiles);
        ArrayNode array = result.putArray("allowedContentTypes");
        normalized.forEach(array::add);
        result.put("maxFileSizeBytes", maxFileSizeBytes);
        return result;
    }

    private JsonNode normalizeReferenceConfig(JsonNode value) {
        String code = "INVALID_COMPLEX_FIELD_CONFIGURATION";
        rejectUnknown(value, Set.of("targetTypeIds", "maxReferences", "direction", "relationCapability"), code,
            "Unknown work-item reference configuration property");
        List<String> targets = uuidArray(value.get("targetTypeIds"), 100, code, "targetTypeIds");
        int maxReferences = integer(value, "maxReferences", 10, code);
        String direction = text(value, "direction", code).toLowerCase(Locale.ROOT);
        String relationCapability = text(value, "relationCapability", code).toLowerCase(Locale.ROOT);
        if (maxReferences < 1 || maxReferences > 100
            || !"outbound".equals(direction)
            || !"deferred".equals(relationCapability)) {
            throw failure(code, "Reference fields only support outbound deferred relations and 1-100 references");
        }
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode array = result.putArray("targetTypeIds");
        targets.forEach(array::add);
        result.put("maxReferences", maxReferences);
        result.put("direction", direction);
        result.put("relationCapability", relationCapability);
        return result;
    }

    private ArrayNode normalizeRules(
        String fieldType,
        JsonNode value,
        Map<String, ConfigureFieldOption> options
    ) {
        if (value == null || value.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (!value.isArray() || value.size() > 20) {
            throw failure("INVALID_VALIDATION_RULE", "Validation rules must be an array containing at most 20 entries");
        }
        List<ObjectNode> normalized = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> kinds = new HashSet<>();
        for (JsonNode rule : value) {
            requireObject(rule, "INVALID_VALIDATION_RULE", "Validation rule must be an object");
            rejectUnknown(rule, Set.of("ruleKey", "kind", "schemaVersion", "config"),
                "INVALID_VALIDATION_RULE", "Unknown validation rule property");
            String ruleKey = text(rule, "ruleKey", "INVALID_VALIDATION_RULE");
            String kind = text(rule, "kind", "INVALID_VALIDATION_RULE");
            int version = integer(rule, "schemaVersion", -1, "INVALID_VALIDATION_RULE");
            if (ruleKey.length() > 64 || !RULE_KEY.matcher(ruleKey).matches()) {
                throw failure("INVALID_VALIDATION_RULE", "Rule key must match [a-z][a-z0-9_]* and be at most 64 characters");
            }
            if (!keys.add(ruleKey) || !kinds.add(kind)) {
                throw failure("INVALID_VALIDATION_RULE", "Rule keys and kinds must be unique within a field");
            }
            if (version != RULE_SCHEMA_VERSION) {
                throw failure("INVALID_VALIDATION_RULE", "Unsupported validation rule schema version");
            }
            JsonNode config = normalizeRuleConfig(fieldType, kind, rule.get("config"), options);
            ObjectNode item = objectMapper.createObjectNode();
            item.put("ruleKey", ruleKey);
            item.put("kind", kind);
            item.put("schemaVersion", RULE_SCHEMA_VERSION);
            item.set("config", config);
            normalized.add(item);
        }
        normalized.sort(Comparator.comparing(node -> node.get("ruleKey").asText()));
        ArrayNode result = objectMapper.createArrayNode();
        normalized.forEach(result::add);
        return result;
    }

    private JsonNode normalizeRuleConfig(
        String fieldType,
        String kind,
        JsonNode value,
        Map<String, ConfigureFieldOption> options
    ) {
        requireObject(value, "INVALID_VALIDATION_RULE", "Validation rule config must be an object");
        return switch (kind) {
            case "length" -> normalizeLength(fieldType, value);
            case "number_range" -> normalizeNumberRange(fieldType, value);
            case "number_precision" -> normalizePrecision(fieldType, value);
            case "regex" -> normalizeRegex(fieldType, value);
            case "format" -> normalizeFormat(fieldType, value);
            case "allowed_values" -> normalizeAllowedValues(fieldType, value, options);
            default -> throw failure("INVALID_VALIDATION_RULE", "Unsupported validation rule kind");
        };
    }

    private JsonNode normalizeLength(String fieldType, JsonNode value) {
        requireType(fieldType, Set.of("text", "url"), "length");
        rejectUnknown(value, Set.of("min", "max"), "INVALID_VALIDATION_RULE", "Unknown length rule property");
        int min = integer(value, "min", 0, "INVALID_VALIDATION_RULE");
        int max = integer(value, "max", 100000, "INVALID_VALIDATION_RULE");
        if (min < 0 || max < min || max > 100000) {
            throw failure("INVALID_VALIDATION_RULE", "Length rule requires 0 <= min <= max <= 100000");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("min", min);
        result.put("max", max);
        return result;
    }

    private JsonNode normalizeNumberRange(String fieldType, JsonNode value) {
        requireType(fieldType, Set.of("number"), "number_range");
        rejectUnknown(value, Set.of("min", "max"), "INVALID_VALIDATION_RULE", "Unknown number range property");
        if (!value.has("min") && !value.has("max")) {
            throw failure("INVALID_VALIDATION_RULE", "Number range requires min or max");
        }
        BigDecimal min = optionalDecimal(value.get("min"));
        BigDecimal max = optionalDecimal(value.get("max"));
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw failure("INVALID_VALIDATION_RULE", "Number range min cannot exceed max");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.set("min", min == null ? objectMapper.nullNode() : DecimalNode.valueOf(min.stripTrailingZeros()));
        result.set("max", max == null ? objectMapper.nullNode() : DecimalNode.valueOf(max.stripTrailingZeros()));
        return result;
    }

    private JsonNode normalizePrecision(String fieldType, JsonNode value) {
        requireType(fieldType, Set.of("number"), "number_precision");
        rejectUnknown(value, Set.of("precision", "scale"), "INVALID_VALIDATION_RULE", "Unknown number precision property");
        int precision = integer(value, "precision", -1, "INVALID_VALIDATION_RULE");
        int scale = integer(value, "scale", -1, "INVALID_VALIDATION_RULE");
        if (precision < 1 || precision > 38 || scale < 0 || scale > precision) {
            throw failure("INVALID_VALIDATION_RULE", "Number precision requires 1 <= precision <= 38 and 0 <= scale <= precision");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("precision", precision);
        result.put("scale", scale);
        return result;
    }

    private JsonNode normalizeRegex(String fieldType, JsonNode value) {
        requireType(fieldType, Set.of("text", "url"), "regex");
        rejectUnknown(value, Set.of("pattern"), "INVALID_VALIDATION_RULE", "Unknown regex property");
        String pattern = text(value, "pattern", "INVALID_VALIDATION_RULE");
        if (pattern.length() > 512) {
            throw failure("INVALID_VALIDATION_RULE", "Regex pattern must be at most 512 characters");
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException exception) {
            throw failure("INVALID_VALIDATION_RULE", "Regex pattern is invalid");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("pattern", pattern);
        return result;
    }

    private JsonNode normalizeFormat(String fieldType, JsonNode value) {
        requireType(fieldType, Set.of("text", "url"), "format");
        rejectUnknown(value, Set.of("format"), "INVALID_VALIDATION_RULE", "Unknown format property");
        String format = text(value, "format", "INVALID_VALIDATION_RULE");
        Set<String> accepted = "url".equals(fieldType) ? Set.of("url") : Set.of("email", "uuid");
        if (!accepted.contains(format)) {
            throw failure("INVALID_VALIDATION_RULE", "Format is not supported by this field type");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("format", format);
        return result;
    }

    private JsonNode normalizeAllowedValues(
        String fieldType,
        JsonNode value,
        Map<String, ConfigureFieldOption> options
    ) {
        requireType(fieldType, Set.of("text", "number", "boolean", "single_select", "multi_select"), "allowed_values");
        rejectUnknown(value, Set.of("values"), "INVALID_VALIDATION_RULE", "Unknown allowed values property");
        JsonNode values = value.get("values");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 100) {
            throw failure("INVALID_VALIDATION_RULE", "Allowed values must contain 1 to 100 entries");
        }
        List<JsonNode> normalized = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonNode candidate : values) {
            JsonNode item = switch (fieldType) {
                case "text" -> requireText(candidate);
                case "number" -> requireNumber(candidate);
                case "boolean" -> requireBoolean(candidate);
                case "single_select", "multi_select" -> requireOption(candidate, options);
                default -> throw new IllegalStateException("Unsupported allowed value type");
            };
            if (!unique.add(item.toString())) {
                throw failure("INVALID_VALIDATION_RULE", "Allowed values cannot contain duplicates");
            }
            normalized.add(item);
        }
        normalized.sort(Comparator.comparing(JsonNode::toString));
        ArrayNode array = objectMapper.createArrayNode();
        normalized.forEach(array::add);
        ObjectNode result = objectMapper.createObjectNode();
        result.set("values", array);
        return result;
    }

    private void validateDefaultAgainstRules(String fieldType, JsonNode defaultValue, ArrayNode rules) {
        if (defaultValue.isNull()) {
            return;
        }
        for (JsonNode rule : rules) {
            if (!"allowed_values".equals(rule.get("kind").asText())) {
                continue;
            }
            JsonNode allowed = rule.path("config").path("values");
            if ("multi_select".equals(fieldType)) {
                for (JsonNode item : defaultValue) {
                    if (!contains(allowed, item)) {
                        throw failure("INVALID_DEFAULT_VALUE", "Default value is outside the allowed values rule");
                    }
                }
            } else if (!contains(allowed, defaultValue)) {
                throw failure("INVALID_DEFAULT_VALUE", "Default value is outside the allowed values rule");
            }
        }
    }

    private void validateComplexDefault(String fieldType, JsonNode defaultValue, JsonNode typeConfig) {
        if ("date".equals(fieldType)) {
            String strategy = typeConfig.path("defaultStrategy").asText();
            if ("today".equals(strategy) && !defaultValue.isNull()) {
                throw failure("INVALID_DEFAULT_VALUE", "Relative date defaults cannot include a fixed value");
            }
            if (!defaultValue.isNull()) {
                LocalDate candidate = LocalDate.parse(defaultValue.asText());
                LocalDate min = nullableDate(typeConfig.path("min"));
                LocalDate max = nullableDate(typeConfig.path("max"));
                if ((min != null && candidate.isBefore(min)) || (max != null && candidate.isAfter(max))) {
                    throw failure("INVALID_DEFAULT_VALUE", "Date default is outside the configured range");
                }
            }
        }
        if ("datetime".equals(fieldType)) {
            String strategy = typeConfig.path("defaultStrategy").asText();
            if ("now".equals(strategy) && !defaultValue.isNull()) {
                throw failure("INVALID_DEFAULT_VALUE", "Relative datetime defaults cannot include a fixed value");
            }
            if (!defaultValue.isNull()) {
                Instant candidate = Instant.parse(defaultValue.asText());
                Instant min = nullableInstant(typeConfig.path("min"));
                Instant max = nullableInstant(typeConfig.path("max"));
                if ((min != null && candidate.isBefore(min)) || (max != null && candidate.isAfter(max))) {
                    throw failure("INVALID_DEFAULT_VALUE", "Datetime default is outside the configured range");
                }
            }
        }
        if ("work_item_reference".equals(fieldType) && !defaultValue.isNull() && !defaultValue.isEmpty()) {
            throw failure(
                "INVALID_DEFAULT_VALUE",
                "Work-item reference defaults remain unavailable until work-item instances are introduced"
            );
        }
    }

    private boolean contains(JsonNode array, JsonNode value) {
        for (JsonNode item : array) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode requireText(JsonNode value) {
        if (!value.isTextual() || value.textValue().length() > 100000) {
            throw failure("INVALID_DEFAULT_VALUE", "Default value must be a string with at most 100000 characters");
        }
        return objectMapper.getNodeFactory().textNode(value.textValue());
    }

    private JsonNode requireNumber(JsonNode value) {
        if (!value.isNumber()) {
            throw failure("INVALID_DEFAULT_VALUE", "Default value must be a number");
        }
        return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());
    }

    private JsonNode requireBoolean(JsonNode value) {
        if (!value.isBoolean()) {
            throw failure("INVALID_DEFAULT_VALUE", "Default value must be a boolean");
        }
        return objectMapper.getNodeFactory().booleanNode(value.booleanValue());
    }

    private JsonNode requireOption(JsonNode value, Map<String, ConfigureFieldOption> options) {
        if (!value.isTextual()) {
            throw failure("INVALID_DEFAULT_VALUE", "Select default must be an option key");
        }
        ConfigureFieldOption option = options.get(value.textValue());
        if (option == null || !"active".equals(option.status())) {
            throw failure("INVALID_DEFAULT_VALUE", "Select default must reference an active option in the same field");
        }
        return objectMapper.getNodeFactory().textNode(option.optionKey());
    }

    private JsonNode requireOptions(JsonNode value, Map<String, ConfigureFieldOption> options) {
        if (!value.isArray() || value.size() > 100) {
            throw failure("INVALID_DEFAULT_VALUE", "Multi-select default must be an array containing at most 100 option keys");
        }
        List<String> keys = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonNode item : value) {
            JsonNode normalized = requireOption(item, options);
            if (!unique.add(normalized.textValue())) {
                throw failure("INVALID_DEFAULT_VALUE", "Multi-select default cannot contain duplicate option keys");
            }
            keys.add(normalized.textValue());
        }
        keys.sort(String::compareTo);
        ArrayNode result = objectMapper.createArrayNode();
        keys.forEach(result::add);
        return result;
    }

    private JsonNode requireUuidArray(JsonNode value, String label, int maximum) {
        if (!value.isArray() || value.size() > maximum) {
            throw failure(
                "INVALID_DEFAULT_VALUE",
                label + " must be an array containing at most " + maximum + " UUID references"
            );
        }
        Set<String> unique = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw failure("INVALID_DEFAULT_VALUE", label + " entries must be UUID strings");
            }
            try {
                String id = UUID.fromString(item.textValue()).toString();
                if (!unique.add(id)) {
                    throw failure("INVALID_DEFAULT_VALUE", label + " cannot contain duplicate references");
                }
                normalized.add(id);
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_DEFAULT_VALUE", label + " entries must be UUID strings");
            }
        }
        normalized.sort(String::compareTo);
        ArrayNode result = objectMapper.createArrayNode();
        normalized.forEach(result::add);
        return result;
    }

    private JsonNode requireDate(JsonNode value) {
        if (!value.isTextual()) {
            throw failure("INVALID_DEFAULT_VALUE", "Date default must be an ISO-8601 calendar date");
        }
        try {
            return objectMapper.getNodeFactory().textNode(LocalDate.parse(value.textValue()).toString());
        } catch (DateTimeException exception) {
            throw failure("INVALID_DEFAULT_VALUE", "Date default must be an ISO-8601 calendar date");
        }
    }

    private JsonNode requireInstant(JsonNode value, String precision) {
        if (!value.isTextual()) {
            throw failure("INVALID_DEFAULT_VALUE", "Datetime default must be an ISO-8601 instant");
        }
        try {
            return objectMapper.getNodeFactory().textNode(truncate(Instant.parse(value.textValue()), precision).toString());
        } catch (DateTimeException exception) {
            throw failure("INVALID_DEFAULT_VALUE", "Datetime default must be an ISO-8601 instant");
        }
    }

    private JsonNode requireUrl(JsonNode value, JsonNode typeConfig) {
        if (!value.isTextual()) {
            throw failure("INVALID_DEFAULT_VALUE", "URL default must be an absolute URL");
        }
        String input = value.textValue();
        int maxLength = typeConfig.path("maxLength").asInt();
        if (input.isBlank() || input.length() > maxLength || input.chars().anyMatch(Character::isISOControl)
            || input.indexOf('\\') >= 0) {
            throw failure("INVALID_DEFAULT_VALUE", "URL default is invalid or exceeds the configured length");
        }
        try {
            URI parsed = new URI(input);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            Set<String> allowed = new HashSet<>();
            typeConfig.path("allowedSchemes").forEach(item -> allowed.add(item.asText()));
            if (!allowed.contains(scheme) || parsed.getHost() == null || parsed.getRawUserInfo() != null) {
                throw failure("INVALID_DEFAULT_VALUE", "URL scheme, host, or credentials are not allowed");
            }
            String host = IDN.toASCII(parsed.getHost().toLowerCase(Locale.ROOT));
            int port = parsed.getPort();
            if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
                port = -1;
            }
            URI normalized = new URI(
                scheme,
                null,
                host,
                port,
                parsed.getPath(),
                parsed.getQuery(),
                parsed.getFragment()
            ).normalize();
            String result = normalized.toASCIIString();
            if (result.length() > maxLength) {
                throw failure("INVALID_DEFAULT_VALUE", "Normalized URL exceeds the configured length");
            }
            return objectMapper.getNodeFactory().textNode(result);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            if (exception instanceof WorkItemFieldException fieldException) {
                throw fieldException;
            }
            throw failure("INVALID_DEFAULT_VALUE", "URL default must be a safe absolute URL");
        }
    }

    private List<String> normalizedStrings(
        JsonNode value,
        Set<String> accepted,
        int maximum,
        boolean emptyAllowed,
        String code,
        String label
    ) {
        if (value == null || !value.isArray() || value.size() > maximum || (!emptyAllowed && value.isEmpty())) {
            throw failure(code, label + " must be an array with a supported number of entries");
        }
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw failure(code, label + " entries must be strings");
            }
            String normalized = item.textValue().trim().toLowerCase(Locale.ROOT);
            if (!accepted.contains(normalized) || !unique.add(normalized)) {
                throw failure(code, label + " contains an unsupported or duplicate value");
            }
            result.add(normalized);
        }
        result.sort(String::compareTo);
        return result;
    }

    private List<String> uuidArray(JsonNode value, int maximum, String code, String label) {
        if (value == null || !value.isArray() || value.size() > maximum) {
            throw failure(code, label + " must be an array containing at most " + maximum + " UUIDs");
        }
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            String normalized = uuidText(item, code, label + " entry");
            if (!unique.add(normalized)) {
                throw failure(code, label + " entries must be unique");
            }
            result.add(normalized);
        }
        result.sort(String::compareTo);
        return result;
    }

    private String uuidText(JsonNode value, String code, String label) {
        if (value == null || !value.isTextual()) {
            throw failure(code, label + " must be a UUID string");
        }
        try {
            return UUID.fromString(value.textValue()).toString();
        } catch (IllegalArgumentException exception) {
            throw failure(code, label + " must be a UUID string");
        }
    }

    private LocalDate optionalDate(JsonNode value, String code, String label) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw failure(code, label + " must be an ISO-8601 date");
        }
        try {
            return LocalDate.parse(value.textValue());
        } catch (DateTimeException exception) {
            throw failure(code, label + " must be an ISO-8601 date");
        }
    }

    private Instant optionalInstant(JsonNode value, String code, String label, String precision) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw failure(code, label + " must be an ISO-8601 instant");
        }
        try {
            return truncate(Instant.parse(value.textValue()), precision);
        } catch (DateTimeException exception) {
            throw failure(code, label + " must be an ISO-8601 instant");
        }
    }

    private LocalDate nullableDate(JsonNode value) {
        return value == null || value.isNull() ? null : LocalDate.parse(value.asText());
    }

    private Instant nullableInstant(JsonNode value) {
        return value == null || value.isNull() ? null : Instant.parse(value.asText());
    }

    private Instant truncate(Instant value, String precision) {
        return switch (precision) {
            case "minute" -> value.truncatedTo(ChronoUnit.MINUTES);
            case "millisecond" -> value.truncatedTo(ChronoUnit.MILLIS);
            case "second" -> value.truncatedTo(ChronoUnit.SECONDS);
            default -> throw failure("INVALID_COMPLEX_FIELD_CONFIGURATION", "Unsupported datetime precision");
        };
    }

    private BigDecimal optionalDecimal(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw failure("INVALID_VALIDATION_RULE", "Number range bounds must be numeric");
        }
        return value.decimalValue();
    }

    private void requireType(String fieldType, Set<String> accepted, String rule) {
        if (!accepted.contains(fieldType)) {
            throw failure("INVALID_VALIDATION_RULE", rule + " is not supported by this field type");
        }
    }

    private void requireObject(JsonNode value, String code, String message) {
        if (value == null || !value.isObject()) {
            throw failure(code, message);
        }
    }

    private void rejectUnknown(JsonNode value, Set<String> accepted, String code, String message) {
        value.fieldNames().forEachRemaining(name -> {
            if (!accepted.contains(name)) {
                throw failure(code, message + ": " + name);
            }
        });
    }

    private int integer(JsonNode value, String name, int defaultValue, String code) {
        JsonNode property = value.get(name);
        if (property == null) {
            return defaultValue;
        }
        if (!property.isIntegralNumber() || !property.canConvertToInt()) {
            throw failure(code, name + " must be an integer");
        }
        return property.intValue();
    }

    private long longValue(JsonNode value, String name, long defaultValue, String code) {
        JsonNode property = value.get(name);
        if (property == null) {
            return defaultValue;
        }
        if (!property.isIntegralNumber() || !property.canConvertToLong()) {
            throw failure(code, name + " must be an integer");
        }
        return property.longValue();
    }

    private boolean booleanValue(JsonNode value, String name, boolean defaultValue, String code) {
        JsonNode property = value.get(name);
        if (property == null) {
            return defaultValue;
        }
        if (!property.isBoolean()) {
            throw failure(code, name + " must be a boolean");
        }
        return property.booleanValue();
    }

    private String text(JsonNode value, String name, String code) {
        JsonNode property = value.get(name);
        if (property == null || !property.isTextual() || property.textValue().isBlank()) {
            throw failure(code, name + " must be a non-empty string");
        }
        return property.textValue().trim();
    }

    private WorkItemFieldException failure(String code, String message) {
        return new WorkItemFieldException(code, message);
    }

    public record CanonicalFieldConfig(JsonNode config, String hash) {
    }
}
