package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.stableKey;

import com.colla.platform.modules.project.application.WorkItemLayoutConditionDsl.FieldReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkItemFieldAccessPolicySchema {
    public static final int SCHEMA_VERSION = 1;
    public static final Set<String> ROLES = Set.of(
        "owner", "admin", "member", "guest", "non_member", "enterprise_admin"
    );
    public static final Set<String> MODES = Set.of("hidden", "read", "write");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "default", "rules");
    private static final Set<String> EFFECT_FIELDS = Set.of("mode", "required");
    private static final Set<String> RULE_FIELDS = Set.of(
        "ruleKey", "roles", "mode", "required", "when"
    );
    private static final Set<String> LAYOUT_KINDS = Set.of("create", "detail");
    private static final Set<String> EVALUATION_MODES = Set.of("synthetic", "runtime");

    private final ObjectMapper objectMapper;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final WorkItemLayoutConditionDsl conditionDsl;

    public WorkItemFieldAccessPolicySchema(
        ObjectMapper objectMapper,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        WorkItemLayoutConditionDsl conditionDsl
    ) {
        this.objectMapper = objectMapper;
        this.canonicalizer = canonicalizer;
        this.conditionDsl = conditionDsl;
    }

    public JsonNode canonicalize(JsonNode requested) {
        ObjectNode root = object(requested, "Field access policy must be an object");
        rejectUnknown(root, ROOT_FIELDS, "Field access policy");
        if (!root.path("schemaVersion").isInt()
            || root.path("schemaVersion").asInt() != SCHEMA_VERSION) {
            throw failure(
                "INVALID_FIELD_ACCESS_POLICY",
                "Field access policy schemaVersion must be 1"
            );
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("schemaVersion", SCHEMA_VERSION);
        normalized.set("default", normalizeEffect(root.path("default"), "Policy default"));
        JsonNode requestedRules = root.path("rules");
        if (!requestedRules.isArray()) {
            throw failure("INVALID_FIELD_ACCESS_POLICY", "Field access policy rules must be an array");
        }
        if (requestedRules.size() > 64) {
            throw failure("FIELD_ACCESS_POLICY_LIMIT", "Field access policy can contain at most 64 rules");
        }
        ArrayNode rules = normalized.putArray("rules");
        List<ObjectNode> normalizedRules = new ArrayList<>();
        Set<String> ruleKeys = new LinkedHashSet<>();
        Map<String, String> unconditionalByRole = new HashMap<>();
        for (JsonNode value : requestedRules) {
            ObjectNode rule = object(value, "Field access policy rule must be an object");
            rejectUnknown(rule, RULE_FIELDS, "Field access policy rule");
            String ruleKey = stableKey(
                text(rule, "ruleKey"),
                "INVALID_FIELD_ACCESS_POLICY",
                "Field access policy rule key"
            );
            if (!ruleKeys.add(ruleKey)) {
                throw failure("DUPLICATE_FIELD_ACCESS_POLICY_RULE", "Policy rule keys must be unique");
            }
            List<String> roles = normalizeRoles(rule.path("roles"));
            ObjectNode effect = normalizeEffect(rule, "Policy rule");
            JsonNode when = rule.has("when") && !rule.path("when").isNull()
                ? conditionDsl.canonicalize(rule.path("when"))
                : null;
            if (when != null) {
                validateContextLiterals(when.path("expression"));
            } else {
                String signature = effect.path("mode").asText() + ":" + effect.path("required").asBoolean();
                for (String role : roles) {
                    String existing = unconditionalByRole.putIfAbsent(role, signature);
                    if (existing != null && !existing.equals(signature)) {
                        throw failure(
                            "CONFLICTING_FIELD_ACCESS_POLICY_RULE",
                            "Unconditional policy rules conflict for role " + role
                        );
                    }
                }
            }
            ObjectNode normalizedRule = objectMapper.createObjectNode();
            normalizedRule.put("ruleKey", ruleKey);
            ArrayNode normalizedRoles = normalizedRule.putArray("roles");
            roles.forEach(normalizedRoles::add);
            normalizedRule.put("mode", effect.path("mode").asText());
            normalizedRule.put("required", effect.path("required").asBoolean());
            if (when != null) {
                normalizedRule.set("when", when);
            }
            normalizedRules.add(normalizedRule);
        }
        normalizedRules.stream()
            .sorted(java.util.Comparator.comparing(rule -> rule.path("ruleKey").asText()))
            .forEach(rules::add);
        return canonicalizer.sort(normalized);
    }

    public List<FieldReference> fieldReferences(JsonNode policy) {
        List<FieldReference> references = new ArrayList<>();
        policy.path("rules").forEach(rule -> {
            if (rule.has("when")) {
                references.addAll(conditionDsl.fieldReferences(rule.path("when")));
            }
        });
        return List.copyOf(references);
    }

    private ObjectNode normalizeEffect(JsonNode value, String label) {
        ObjectNode effect = object(value, label + " must be an object");
        if ("Policy default".equals(label)) {
            rejectUnknown(effect, EFFECT_FIELDS, label);
        }
        String mode = text(effect, "mode");
        if (!MODES.contains(mode)) {
            throw failure("INVALID_FIELD_ACCESS_POLICY", label + " mode is not supported");
        }
        if (!effect.path("required").isBoolean()) {
            throw failure("INVALID_FIELD_ACCESS_POLICY", label + " required must be boolean");
        }
        boolean required = effect.path("required").asBoolean();
        if (required && !"write".equals(mode)) {
            throw failure(
                "INVALID_FIELD_ACCESS_POLICY",
                label + " can be required only when mode is write"
            );
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("mode", mode);
        normalized.put("required", required);
        return normalized;
    }

    private List<String> normalizeRoles(JsonNode value) {
        if (!value.isArray() || value.isEmpty()) {
            throw failure(
                "INVALID_FIELD_ACCESS_POLICY",
                "Policy rule roles must contain at least one allowed role"
            );
        }
        Set<String> roles = new HashSet<>();
        value.forEach(role -> {
            String normalized = role.asText("").trim().toLowerCase();
            if (!ROLES.contains(normalized)) {
                throw failure("INVALID_FIELD_ACCESS_POLICY", "Policy rule role is not allowed");
            }
            roles.add(normalized);
        });
        return roles.stream().sorted().toList();
    }

    private void validateContextLiterals(JsonNode expression) {
        if (expression == null || expression.isMissingNode() || expression.isNull()) {
            return;
        }
        String kind = expression.path("kind").asText();
        if ("predicate".equals(kind) && "context".equals(expression.path("source").asText())) {
            String key = expression.path("contextKey").asText();
            JsonNode value = expression.path("value");
            Set<String> allowed = switch (key) {
                case "actor_role" -> ROLES;
                case "layout_kind" -> LAYOUT_KINDS;
                case "mode" -> EVALUATION_MODES;
                default -> Set.of();
            };
            if (!"is_empty".equals(expression.path("operator").asText())) {
                if (value.isArray()) {
                    value.forEach(item -> requireAllowedContextValue(item, allowed));
                } else {
                    requireAllowedContextValue(value, allowed);
                }
            }
            return;
        }
        if ("not".equals(kind)) {
            validateContextLiterals(expression.path("operand"));
            return;
        }
        expression.path("operands").forEach(this::validateContextLiterals);
    }

    private void requireAllowedContextValue(JsonNode value, Set<String> allowed) {
        if (!value.isTextual() || !allowed.contains(value.asText().trim().toLowerCase())) {
            throw failure(
                "INVALID_FIELD_ACCESS_POLICY",
                "Policy condition contains an unsupported context value"
            );
        }
    }

    private ObjectNode object(JsonNode value, String message) {
        if (value == null || !value.isObject()) {
            throw failure("INVALID_FIELD_ACCESS_POLICY", message);
        }
        return (ObjectNode) value.deepCopy();
    }

    private String text(JsonNode value, String field) {
        String result = value.path(field).asText("").trim().toLowerCase();
        if (result.isEmpty()) {
            throw failure("INVALID_FIELD_ACCESS_POLICY", "Policy " + field + " is required");
        }
        return result;
    }

    private void rejectUnknown(ObjectNode value, Set<String> allowed, String label) {
        value.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw failure("INVALID_FIELD_ACCESS_POLICY", label + " contains unknown field " + field);
            }
        });
    }
}
