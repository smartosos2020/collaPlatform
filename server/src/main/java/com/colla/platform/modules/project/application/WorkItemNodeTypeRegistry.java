package com.colla.platform.modules.project.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemNodeTypeRegistry {
    private static final Set<String> NODE_KINDS = Set.of(
        "start", "manual", "automatic", "branch", "join", "end"
    );
    private static final Set<String> PROCESSING_STRATEGIES = Set.of(
        "automatic", "single", "any", "all", "quorum"
    );
    private static final Set<String> CONDITION_OPERATORS = Set.of(
        "eq", "ne", "in", "not_in", "present", "absent", "gt", "gte", "lt", "lte", "all", "any", "not"
    );

    public boolean supportsNodeKind(String value) {
        return NODE_KINDS.contains(normalize(value));
    }

    public boolean supportsProcessingStrategy(String value) {
        return PROCESSING_STRATEGIES.contains(normalize(value));
    }

    public boolean supportsConditionOperator(String value) {
        return CONDITION_OPERATORS.contains(normalize(value));
    }

    public List<String> nodeKinds() {
        return NODE_KINDS.stream().sorted().toList();
    }

    public List<String> processingStrategies() {
        return PROCESSING_STRATEGIES.stream().sorted().toList();
    }

    public boolean conditionShapeSupported(JsonNode condition) {
        if (condition == null || condition.isNull()) {
            return true;
        }
        if (!condition.isObject()) {
            return false;
        }
        String operator = normalize(condition.path("operator").asText());
        if (!supportsConditionOperator(operator)) {
            return false;
        }
        if (Set.of("all", "any").contains(operator)) {
            JsonNode operands = condition.path("operands");
            if (!operands.isArray() || operands.isEmpty()) {
                return false;
            }
            for (JsonNode operand : operands) {
                if (!conditionShapeSupported(operand)) {
                    return false;
                }
            }
            return true;
        }
        if ("not".equals(operator)) {
            return conditionShapeSupported(condition.get("operand"));
        }
        if (Set.of("present", "absent").contains(operator)) {
            return semanticKey(condition.path("fieldKey").asText());
        }
        return semanticKey(condition.path("fieldKey").asText()) && condition.has("value");
    }

    private boolean semanticKey(String value) {
        return value != null && value.matches("[a-z][a-z0-9_]{0,63}");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
