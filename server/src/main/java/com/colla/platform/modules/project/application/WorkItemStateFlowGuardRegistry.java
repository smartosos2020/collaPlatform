package com.colla.platform.modules.project.application;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemStateFlowGuardRegistry {
    private static final Map<String, Set<String>> OPERATORS = Map.of(
        "field", Set.of("eq", "neq", "in", "not_in", "present", "absent", "contains", "not_contains"),
        "participant", Set.of("has_role", "missing_role"),
        "space_role", Set.of("in", "not_in"),
        "all", Set.of("all"),
        "any", Set.of("any"),
        "not", Set.of("not")
    );

    public boolean supports(String kind, String operator) {
        return kind != null
            && operator != null
            && OPERATORS.getOrDefault(kind, Set.of()).contains(operator);
    }

    public Set<String> operators(String kind) {
        return OPERATORS.getOrDefault(kind, Set.of());
    }
}
