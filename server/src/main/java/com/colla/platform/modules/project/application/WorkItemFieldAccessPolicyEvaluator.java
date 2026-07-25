package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkItemFieldAccessPolicyEvaluator {
    private static final Map<String, Integer> MODE_ORDER = Map.of(
        "hidden", 0,
        "read", 1,
        "write", 2
    );

    private final WorkItemFieldAccessPolicySchema schema;
    private final WorkItemLayoutConditionDsl conditionDsl;
    private final ObjectMapper objectMapper;

    public WorkItemFieldAccessPolicyEvaluator(
        WorkItemFieldAccessPolicySchema schema,
        WorkItemLayoutConditionDsl conditionDsl,
        ObjectMapper objectMapper
    ) {
        this.schema = schema;
        this.conditionDsl = conditionDsl;
        this.objectMapper = objectMapper;
    }

    public FieldAccessDecision evaluate(JsonNode requestedPolicy, EvaluationContext context) {
        validateContext(context);
        JsonNode policy = requestedPolicy == null
            ? defaultPolicy()
            : schema.canonicalize(requestedPolicy);
        JsonNode selected = policy.path("default");
        List<String> matchedRules = new ArrayList<>();
        for (JsonNode rule : policy.path("rules")) {
            if (!contains(rule.path("roles"), context.role())) {
                continue;
            }
            if (rule.has("when") && !conditionDsl.evaluate(
                rule.path("when"),
                context.fieldValues(),
                contextValues(context)
            )) {
                continue;
            }
            matchedRules.add(rule.path("ruleKey").asText());
            selected = restrictive(selected, rule);
        }
        String configuredMode = selected.path("mode").asText();
        boolean configuredRequired = selected.path("required").asBoolean();
        Ceiling ceiling = ceiling(context);
        String effectiveMode = mostRestrictive(configuredMode, ceiling.mode());
        boolean required = "write".equals(effectiveMode) && configuredRequired;
        String reasonCode = reasonCode(configuredMode, effectiveMode, matchedRules, ceiling);
        List<DecisionStep> explanation = List.of(
            new DecisionStep("membership", ceiling.roleMode(), "role_ceiling"),
            new DecisionStep("resource_state", ceiling.mode(), ceiling.reasonCode()),
            new DecisionStep(
                matchedRules.isEmpty() ? "default" : String.join(",", matchedRules),
                configuredMode,
                matchedRules.isEmpty() ? "policy_default" : "policy_rule"
            ),
            new DecisionStep("effective", effectiveMode, reasonCode)
        );
        return new FieldAccessDecision(
            effectiveMode,
            required,
            reasonCode,
            List.copyOf(matchedRules),
            explanation
        );
    }

    private JsonNode defaultPolicy() {
        var root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        var effect = root.putObject("default");
        effect.put("mode", "write");
        effect.put("required", false);
        root.putArray("rules");
        return root;
    }

    private JsonNode restrictive(JsonNode left, JsonNode right) {
        String mode = mostRestrictive(left.path("mode").asText(), right.path("mode").asText());
        boolean required = "write".equals(mode)
            && (left.path("required").asBoolean() || right.path("required").asBoolean());
        var result = objectMapper.createObjectNode();
        result.put("mode", mode);
        result.put("required", required);
        return result;
    }

    private Ceiling ceiling(EvaluationContext context) {
        String roleMode = switch (context.role()) {
            case "owner", "admin", "member" -> "write";
            case "guest" -> "read";
            case "non_member", "enterprise_admin" -> "hidden";
            default -> throw failure("INVALID_FIELD_ACCESS_CONTEXT", "Unsupported actor role");
        };
        if ("archived".equals(context.spaceStatus())) {
            return new Ceiling(roleMode, "hidden", "space_archived");
        }
        if ("retired".equals(context.typeStatus())) {
            return new Ceiling(roleMode, "hidden", "type_retired");
        }
        if (!"active".equals(context.fieldStatus())) {
            return new Ceiling(roleMode, "hidden", "field_" + context.fieldStatus());
        }
        if ("disabled".equals(context.spaceStatus())) {
            return new Ceiling(roleMode, mostRestrictive(roleMode, "read"), "space_disabled");
        }
        if ("disabled".equals(context.typeStatus())) {
            return new Ceiling(roleMode, mostRestrictive(roleMode, "read"), "type_disabled");
        }
        return new Ceiling(roleMode, roleMode, "role_ceiling");
    }

    private String reasonCode(
        String configuredMode,
        String effectiveMode,
        List<String> matchedRules,
        Ceiling ceiling
    ) {
        if (!ceiling.mode().equals(ceiling.roleMode())) {
            return ceiling.reasonCode();
        }
        if (!effectiveMode.equals(configuredMode)) {
            return "role_ceiling";
        }
        return matchedRules.isEmpty() ? "policy_default" : "policy_rule";
    }

    private String mostRestrictive(String left, String right) {
        return Comparator.comparingInt((String mode) -> MODE_ORDER.getOrDefault(mode, -1))
            .compare(left, right) <= 0 ? left : right;
    }

    private boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, JsonNode> contextValues(EvaluationContext context) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put("actor_role", objectMapper.getNodeFactory().textNode(context.role()));
        values.put("layout_kind", objectMapper.getNodeFactory().textNode(context.layoutKind()));
        values.put(
            "mode",
            objectMapper.getNodeFactory().textNode(context.synthetic() ? "synthetic" : "runtime")
        );
        return values;
    }

    private void validateContext(EvaluationContext context) {
        if (context == null
            || !WorkItemFieldAccessPolicySchema.ROLES.contains(context.role())
            || !List.of("active", "disabled", "archived").contains(context.spaceStatus())
            || !List.of("active", "disabled", "retired").contains(context.typeStatus())
            || !List.of("active", "disabled", "retired").contains(context.fieldStatus())
            || !List.of("create", "detail").contains(context.layoutKind())) {
            throw failure("INVALID_FIELD_ACCESS_CONTEXT", "Field access evaluation context is invalid");
        }
    }

    public record EvaluationContext(
        String role,
        String spaceStatus,
        String typeStatus,
        String fieldStatus,
        String layoutKind,
        boolean synthetic,
        Map<String, JsonNode> fieldValues
    ) {
        public EvaluationContext {
            fieldValues = fieldValues == null ? Map.of() : Map.copyOf(fieldValues);
        }
    }

    public record FieldAccessDecision(
        String mode,
        boolean required,
        String reasonCode,
        List<String> matchedRuleKeys,
        List<DecisionStep> explanation
    ) {
    }

    public record DecisionStep(String source, String mode, String reasonCode) {
    }

    private record Ceiling(String roleMode, String mode, String reasonCode) {
    }
}
