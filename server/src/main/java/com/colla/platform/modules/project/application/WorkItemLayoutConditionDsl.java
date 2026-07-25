package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.stableKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkItemLayoutConditionDsl {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_EXPRESSIONS = 64;
    private static final Set<String> CONTEXT_KEYS = Set.of("actor_role", "layout_kind", "mode");
    private static final Set<String> OPERATORS = Set.of(
        "eq", "neq", "contains", "contains_any", "contains_all", "in",
        "gt", "gte", "lt", "lte", "before", "after", "between", "is_empty"
    );

    private final ObjectMapper objectMapper;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;

    public WorkItemLayoutConditionDsl(
        ObjectMapper objectMapper,
        WorkItemTypeConfigCanonicalizer canonicalizer
    ) {
        this.objectMapper = objectMapper;
        this.canonicalizer = canonicalizer;
    }

    public JsonNode canonicalize(JsonNode requested) {
        ObjectNode condition = requested == null || requested.isNull()
            ? objectMapper.createObjectNode()
            : requireObject(requested, "Visibility condition must be an object");
        if (!condition.has("schemaVersion")) {
            condition.put("schemaVersion", SCHEMA_VERSION);
        }
        if (!condition.path("schemaVersion").isInt()
            || condition.path("schemaVersion").asInt() != SCHEMA_VERSION) {
            throw failure("INVALID_LAYOUT_CONDITION", "Visibility condition schemaVersion must be 1");
        }
        if (condition.has("expression") && !condition.path("expression").isNull()) {
            Counter counter = new Counter();
            validateExpression(condition.path("expression"), 1, counter);
        }
        return canonicalizer.sort(condition);
    }

    public List<FieldReference> fieldReferences(JsonNode condition) {
        JsonNode expression = condition == null ? null : condition.path("expression");
        if (expression == null || expression.isMissingNode() || expression.isNull()) {
            return List.of();
        }
        LinkedHashSet<FieldReference> references = new LinkedHashSet<>();
        collectReferences(expression, references);
        return List.copyOf(references);
    }

    public boolean evaluate(
        JsonNode condition,
        Map<String, JsonNode> fieldValues,
        Map<String, JsonNode> contextValues
    ) {
        JsonNode expression = condition == null ? null : condition.path("expression");
        if (expression == null || expression.isMissingNode() || expression.isNull()) {
            return true;
        }
        return evaluateExpression(expression, fieldValues, contextValues);
    }

    private void validateExpression(JsonNode expression, int depth, Counter counter) {
        if (depth > MAX_DEPTH || ++counter.value > MAX_EXPRESSIONS) {
            throw failure("LAYOUT_CONDITION_LIMIT", "Visibility condition is too deeply nested or too large");
        }
        ObjectNode value = requireObject(expression, "Condition expression must be an object");
        String kind = text(value, "kind");
        switch (kind) {
            case "all", "any" -> {
                JsonNode operands = value.path("operands");
                if (!operands.isArray() || operands.isEmpty()) {
                    throw failure("INVALID_LAYOUT_CONDITION", kind + " requires at least one operand");
                }
                operands.forEach(operand -> validateExpression(operand, depth + 1, counter));
            }
            case "not" -> validateExpression(value.path("operand"), depth + 1, counter);
            case "predicate" -> validatePredicate(value);
            default -> throw failure("INVALID_LAYOUT_CONDITION", "Unsupported condition expression kind");
        }
    }

    private void validatePredicate(ObjectNode predicate) {
        String source = text(predicate, "source");
        if ("field".equals(source)) {
            parseUuid(text(predicate, "fieldId"));
            stableKey(text(predicate, "fieldKey"), "INVALID_LAYOUT_CONDITION", "Condition field key");
        } else if ("context".equals(source)) {
            String contextKey = stableKey(
                text(predicate, "contextKey"), "INVALID_LAYOUT_CONDITION", "Condition context key"
            );
            if (!CONTEXT_KEYS.contains(contextKey)) {
                throw failure("INVALID_LAYOUT_CONDITION", "Condition context key is not allowed");
            }
        } else {
            throw failure("INVALID_LAYOUT_CONDITION", "Condition source must be field or context");
        }
        String operator = text(predicate, "operator");
        if (!OPERATORS.contains(operator)) {
            throw failure("INVALID_LAYOUT_CONDITION", "Condition operator is not supported");
        }
        if (!"is_empty".equals(operator) && !predicate.has("value")) {
            throw failure("INVALID_LAYOUT_CONDITION", "Condition predicate requires a value");
        }
        if ("between".equals(operator)
            && (!predicate.path("value").isArray() || predicate.path("value").size() != 2)) {
            throw failure("INVALID_LAYOUT_CONDITION", "between requires exactly two values");
        }
    }

    private void collectReferences(JsonNode expression, Collection<FieldReference> references) {
        String kind = expression.path("kind").asText();
        if ("predicate".equals(kind) && "field".equals(expression.path("source").asText())) {
            references.add(new FieldReference(
                parseUuid(expression.path("fieldId").asText()),
                expression.path("fieldKey").asText(),
                expression.path("operator").asText(),
                expression.path("value")
            ));
            return;
        }
        if ("not".equals(kind)) {
            collectReferences(expression.path("operand"), references);
            return;
        }
        expression.path("operands").forEach(operand -> collectReferences(operand, references));
    }

    private boolean evaluateExpression(
        JsonNode expression,
        Map<String, JsonNode> fieldValues,
        Map<String, JsonNode> contextValues
    ) {
        return switch (expression.path("kind").asText()) {
            case "all" -> all(expression.path("operands"), fieldValues, contextValues);
            case "any" -> any(expression.path("operands"), fieldValues, contextValues);
            case "not" -> !evaluateExpression(expression.path("operand"), fieldValues, contextValues);
            case "predicate" -> evaluatePredicate(expression, fieldValues, contextValues);
            default -> false;
        };
    }

    private boolean all(JsonNode operands, Map<String, JsonNode> fields, Map<String, JsonNode> context) {
        for (JsonNode operand : operands) {
            if (!evaluateExpression(operand, fields, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean any(JsonNode operands, Map<String, JsonNode> fields, Map<String, JsonNode> context) {
        for (JsonNode operand : operands) {
            if (evaluateExpression(operand, fields, context)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluatePredicate(
        JsonNode predicate,
        Map<String, JsonNode> fieldValues,
        Map<String, JsonNode> contextValues
    ) {
        JsonNode actual = "field".equals(predicate.path("source").asText())
            ? fieldValues.get(predicate.path("fieldKey").asText())
            : contextValues.get(predicate.path("contextKey").asText());
        JsonNode expected = predicate.path("value");
        String operator = predicate.path("operator").asText();
        if ("is_empty".equals(operator)) {
            return actual == null || actual.isNull()
                || (actual.isTextual() && actual.asText().isBlank())
                || (actual.isContainerNode() && actual.isEmpty());
        }
        if (actual == null || actual.isNull()) {
            return false;
        }
        return switch (operator) {
            case "eq" -> actual.equals(expected);
            case "neq" -> !actual.equals(expected);
            case "contains" -> actual.asText().contains(expected.asText());
            case "in" -> expected.isArray() && contains(expected, actual);
            case "contains_any" -> intersects(actual, expected, false);
            case "contains_all" -> intersects(actual, expected, true);
            case "gt", "after" -> compare(actual, expected) > 0;
            case "gte" -> compare(actual, expected) >= 0;
            case "lt", "before" -> compare(actual, expected) < 0;
            case "lte" -> compare(actual, expected) <= 0;
            case "between" -> compare(actual, expected.get(0)) >= 0 && compare(actual, expected.get(1)) <= 0;
            default -> false;
        };
    }

    private boolean intersects(JsonNode actual, JsonNode expected, boolean requireAll) {
        if (!actual.isArray() || !expected.isArray()) {
            return false;
        }
        for (JsonNode item : expected) {
            boolean found = contains(actual, item);
            if (requireAll && !found) {
                return false;
            }
            if (!requireAll && found) {
                return true;
            }
        }
        return requireAll;
    }

    private boolean contains(JsonNode array, JsonNode expected) {
        for (JsonNode item : array) {
            if (item.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private int compare(JsonNode left, JsonNode right) {
        if (left.isNumber() && right.isNumber()) {
            return new BigDecimal(left.asText()).compareTo(new BigDecimal(right.asText()));
        }
        return left.asText().compareTo(right.asText());
    }

    private ObjectNode requireObject(JsonNode value, String message) {
        if (!value.isObject()) {
            throw failure("INVALID_LAYOUT_CONDITION", message);
        }
        return (ObjectNode) value.deepCopy();
    }

    private String text(JsonNode value, String key) {
        String result = value.path(key).asText("").trim().toLowerCase();
        if (result.isEmpty()) {
            throw failure("INVALID_LAYOUT_CONDITION", "Condition " + key + " is required");
        }
        return result;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_LAYOUT_CONDITION", "Condition fieldId must be a UUID");
        }
    }

    private static final class Counter {
        private int value;
    }

    public record FieldReference(UUID fieldId, String fieldKey, String operator, JsonNode value) {
    }
}
