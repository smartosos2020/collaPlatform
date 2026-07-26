package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.AUTHORIZATION_ROLES;
import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.MAX_ACTIONS;
import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.MAX_GUARDS;
import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.MAX_REQUIRED_FIELDS;
import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.MAX_SIDE_EFFECTS;
import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.MAX_STATES;
import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.MAX_TRANSITIONS;
import static com.colla.platform.modules.project.domain.WorkItemStateFlowModels.SEMANTIC_KEY;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiagnosticSeverity;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionKind;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.StateCategory;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemStateFlowValidator {
    private static final Set<String> SPACE_ROLES = Set.of("owner", "admin", "member", "guest");
    private static final Set<String> PARTICIPANT_ROLES = Set.of("owner", "assignee", "collaborator", "watcher");
    private final WorkItemStateFlowGuardRegistry guardRegistry;
    private final WorkItemStateFlowSideEffectRegistry sideEffectRegistry;

    public WorkItemStateFlowValidator(
        WorkItemStateFlowGuardRegistry guardRegistry,
        WorkItemStateFlowSideEffectRegistry sideEffectRegistry
    ) {
        this.guardRegistry = guardRegistry;
        this.sideEffectRegistry = sideEffectRegistry;
    }

    public void validate(
        JsonNode flow,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (flow == null || flow.isMissingNode() || flow.isNull()) {
            return;
        }
        if (!flow.isObject()) {
            error(diagnostics, "invalid_state_flow", "$.stateFlow", "State flow must be an object");
            return;
        }
        JsonNode states = requireArray(flow, "states", diagnostics);
        JsonNode actions = requireArray(flow, "actions", diagnostics);
        JsonNode transitions = requireArray(flow, "transitions", diagnostics);
        JsonNode guards = requireArray(flow, "guards", diagnostics);
        if (states == null || actions == null || transitions == null || guards == null) {
            return;
        }
        budget(states, MAX_STATES, "state_budget_exceeded", "$.stateFlow.states", diagnostics);
        budget(actions, MAX_ACTIONS, "action_budget_exceeded", "$.stateFlow.actions", diagnostics);
        budget(transitions, MAX_TRANSITIONS, "transition_budget_exceeded", "$.stateFlow.transitions", diagnostics);
        budget(guards, MAX_GUARDS, "guard_budget_exceeded", "$.stateFlow.guards", diagnostics);

        Map<String, StateCategory> stateCategories = validateStates(states, diagnostics);
        Map<String, ActionKind> actionKinds = validateActions(
            actions, fieldKeys, activeFieldKeys, hiddenFieldKeys, diagnostics
        );
        Set<String> guardKeys = validateGuards(
            guards, fieldKeys, activeFieldKeys, hiddenFieldKeys, diagnostics
        );
        validateTransitions(
            transitions, stateCategories, actionKinds, guardKeys, diagnostics
        );
        validateGuardGraph(guards, guardKeys, diagnostics);
    }

    private Map<String, StateCategory> validateStates(
        JsonNode states,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Map<String, StateCategory> categories = new LinkedHashMap<>();
        int initialCount = 0;
        for (int index = 0; index < states.size(); index++) {
            JsonNode state = states.get(index);
            String path = "$.stateFlow.states[" + index + "]";
            String key = semanticKey(state, "stateKey", path, categories.keySet(), diagnostics);
            String label = state.path("label").asText("").trim();
            if (label.isEmpty()) {
                error(diagnostics, "missing_state_label", path + ".label", "State label is required");
            }
            try {
                StateCategory category = StateCategory.parse(state.path("category").asText());
                if (key != null) {
                    categories.put(key, category);
                }
                if (category == StateCategory.initial) {
                    initialCount++;
                }
            } catch (IllegalArgumentException exception) {
                error(diagnostics, "invalid_state_category", path + ".category", "State category is unsupported");
            }
        }
        if (initialCount != 1) {
            error(
                diagnostics,
                "initial_state_count_invalid",
                "$.stateFlow.states",
                "Exactly one initial state is required"
            );
        }
        return categories;
    }

    private Map<String, ActionKind> validateActions(
        JsonNode actions,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Map<String, ActionKind> kinds = new LinkedHashMap<>();
        for (int index = 0; index < actions.size(); index++) {
            JsonNode action = actions.get(index);
            String path = "$.stateFlow.actions[" + index + "]";
            String key = semanticKey(action, "actionKey", path, kinds.keySet(), diagnostics);
            if (action.path("label").asText("").trim().isEmpty()) {
                error(diagnostics, "missing_action_label", path + ".label", "Action label is required");
            }
            try {
                ActionKind kind = ActionKind.parse(action.path("kind").asText());
                if (key != null) {
                    kinds.put(key, kind);
                }
            } catch (IllegalArgumentException exception) {
                error(diagnostics, "invalid_action_kind", path + ".kind", "Action kind is unsupported");
            }
            validateStringSet(
                action.path("authorizedRoles"),
                AUTHORIZATION_ROLES,
                true,
                "invalid_action_authorization",
                path + ".authorizedRoles",
                diagnostics
            );
            validateFieldReferences(
                action.path("requiredFieldKeys"),
                fieldKeys,
                activeFieldKeys,
                hiddenFieldKeys,
                true,
                path + ".requiredFieldKeys",
                diagnostics
            );
            if (action.path("requiredFieldKeys").isArray()
                && action.path("requiredFieldKeys").size() > MAX_REQUIRED_FIELDS) {
                error(diagnostics, "required_field_budget_exceeded", path + ".requiredFieldKeys", "Too many required fields");
            }
            JsonNode patch = action.path("fieldPatch");
            if (!patch.isObject()) {
                error(diagnostics, "invalid_action_field_patch", path + ".fieldPatch", "Field patch must be an object");
            } else {
                patch.fieldNames().forEachRemaining(fieldKey -> {
                    if (!activeFieldKeys.contains(fieldKey) || hiddenFieldKeys.contains(fieldKey)) {
                        error(
                            diagnostics,
                            "invalid_action_patch_field",
                            path + ".fieldPatch." + fieldKey,
                            "Field patch must reference a visible active field"
                        );
                    }
                });
            }
            JsonNode sideEffects = action.path("sideEffectKeys");
            if (!sideEffects.isArray()) {
                error(diagnostics, "invalid_action_side_effects", path + ".sideEffectKeys", "Side effects must be an array");
            } else {
                if (sideEffects.size() > MAX_SIDE_EFFECTS) {
                    error(diagnostics, "side_effect_budget_exceeded", path + ".sideEffectKeys", "Too many side effects");
                }
                Set<String> seen = new HashSet<>();
                for (int effectIndex = 0; effectIndex < sideEffects.size(); effectIndex++) {
                    String effect = sideEffects.get(effectIndex).asText("");
                    if (!seen.add(effect) || !sideEffectRegistry.supports(effect)) {
                        error(
                            diagnostics,
                            "unknown_side_effect",
                            path + ".sideEffectKeys[" + effectIndex + "]",
                            "Side effect key is not registered"
                        );
                    }
                }
            }
        }
        return kinds;
    }

    private Set<String> validateGuards(
        JsonNode guards,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> keys = new LinkedHashSet<>();
        for (int index = 0; index < guards.size(); index++) {
            JsonNode guard = guards.get(index);
            String path = "$.stateFlow.guards[" + index + "]";
            String key = semanticKey(guard, "guardKey", path, keys, diagnostics);
            if (key != null) {
                keys.add(key);
            }
            String kind = guard.path("kind").asText("");
            String operator = guard.path("operator").asText("");
            if (!guardRegistry.supports(kind, operator)) {
                error(diagnostics, "unknown_guard_operator", path + ".operator", "Guard operator is not registered");
                continue;
            }
            switch (kind) {
                case "field" -> validateFieldGuard(
                    guard, fieldKeys, activeFieldKeys, hiddenFieldKeys, path, diagnostics
                );
                case "participant" -> {
                    if (!PARTICIPANT_ROLES.contains(guard.path("participantRole").asText())) {
                        error(diagnostics, "invalid_participant_guard", path + ".participantRole", "Participant role is unsupported");
                    }
                }
                case "space_role" -> validateStringSet(
                    guard.path("spaceRoles"),
                    SPACE_ROLES,
                    true,
                    "invalid_space_role_guard",
                    path + ".spaceRoles",
                    diagnostics
                );
                case "all", "any", "not" -> {
                    JsonNode children = guard.path("guardKeys");
                    int minimum = "not".equals(kind) ? 1 : 1;
                    int maximum = "not".equals(kind) ? 1 : MAX_GUARDS;
                    if (!children.isArray() || children.size() < minimum || children.size() > maximum) {
                        error(diagnostics, "invalid_composite_guard", path + ".guardKeys", "Composite guard children are invalid");
                    }
                }
                default -> {
                    // The registry already fails closed for unknown kinds.
                }
            }
        }
        return keys;
    }

    private void validateFieldGuard(
        JsonNode guard,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        String fieldKey = guard.path("fieldKey").asText("");
        if (!fieldKeys.contains(fieldKey) || !activeFieldKeys.contains(fieldKey)) {
            error(diagnostics, "unknown_guard_field", path + ".fieldKey", "Guard field must be active in this snapshot");
        } else if (hiddenFieldKeys.contains(fieldKey)) {
            error(diagnostics, "hidden_guard_field", path + ".fieldKey", "Hidden fields cannot be guard inputs");
        }
        String operator = guard.path("operator").asText();
        JsonNode value = guard.get("value");
        if (Set.of("in", "not_in").contains(operator) && (value == null || !value.isArray())) {
            error(diagnostics, "invalid_guard_operand", path + ".value", "Set operators require an array operand");
        } else if (Set.of("present", "absent").contains(operator) && value != null && !value.isNull()) {
            error(diagnostics, "invalid_guard_operand", path + ".value", "Presence operators do not accept an operand");
        } else if (!Set.of("in", "not_in", "present", "absent").contains(operator)
            && (value == null || value.isNull() || value.isContainerNode())) {
            error(diagnostics, "invalid_guard_operand", path + ".value", "Comparison operators require a scalar operand");
        }
    }

    private void validateTransitions(
        JsonNode transitions,
        Map<String, StateCategory> stateCategories,
        Map<String, ActionKind> actionKinds,
        Set<String> guardKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> transitionKeys = new LinkedHashSet<>();
        Set<String> referencedActions = new HashSet<>();
        Map<String, Set<String>> edges = new HashMap<>();
        for (int index = 0; index < transitions.size(); index++) {
            JsonNode transition = transitions.get(index);
            String path = "$.stateFlow.transitions[" + index + "]";
            String key = semanticKey(transition, "transitionKey", path, transitionKeys, diagnostics);
            if (key != null) {
                transitionKeys.add(key);
            }
            String actionKey = transition.path("actionKey").asText("");
            String from = transition.path("fromStateKey").asText("");
            String to = transition.path("toStateKey").asText("");
            ActionKind kind = actionKinds.get(actionKey);
            if (kind == null) {
                error(diagnostics, "dangling_transition_action", path + ".actionKey", "Transition action is missing");
            } else {
                referencedActions.add(actionKey);
            }
            if (!stateCategories.containsKey(from)) {
                error(diagnostics, "dangling_transition_source", path + ".fromStateKey", "Transition source state is missing");
            }
            if (!stateCategories.containsKey(to)) {
                error(diagnostics, "dangling_transition_target", path + ".toStateKey", "Transition target state is missing");
            }
            String guardKey = transition.path("guardKey").isNull() ? "" : transition.path("guardKey").asText("");
            if (!guardKey.isEmpty() && !guardKeys.contains(guardKey)) {
                error(diagnostics, "dangling_transition_guard", path + ".guardKey", "Transition guard is missing");
            }
            if (from.equals(to) && kind != ActionKind.correction) {
                error(diagnostics, "self_transition_not_allowed", path, "Only correction actions may keep the same state");
            }
            StateCategory sourceCategory = stateCategories.get(from);
            StateCategory targetCategory = stateCategories.get(to);
            if (sourceCategory == StateCategory.terminal && kind != ActionKind.reopen && kind != ActionKind.correction) {
                error(diagnostics, "invalid_terminal_transition", path, "Terminal states only allow reopen or correction");
            }
            if (sourceCategory == StateCategory.canceled && kind != ActionKind.restore && kind != ActionKind.correction) {
                error(diagnostics, "invalid_canceled_transition", path, "Canceled states only allow restore or correction");
            }
            if (kind == ActionKind.terminate && targetCategory != StateCategory.canceled) {
                error(diagnostics, "invalid_terminate_target", path, "Terminate actions must target a canceled state");
            }
            if (kind == ActionKind.reopen
                && (sourceCategory != StateCategory.terminal
                    || targetCategory == StateCategory.terminal
                    || targetCategory == StateCategory.canceled)) {
                error(diagnostics, "invalid_reopen_transition", path, "Reopen must leave a terminal state for a non-terminal state");
            }
            if (kind == ActionKind.restore
                && (sourceCategory != StateCategory.canceled
                    || targetCategory == StateCategory.terminal
                    || targetCategory == StateCategory.canceled)) {
                error(diagnostics, "invalid_restore_transition", path, "Restore must leave a canceled state for a non-terminal state");
            }
            if (kind == ActionKind.return_action
                && (targetCategory == StateCategory.terminal || targetCategory == StateCategory.canceled)) {
                error(diagnostics, "invalid_return_target", path, "Return actions must target an active or initial state");
            }
            if (stateCategories.containsKey(from) && stateCategories.containsKey(to)) {
                edges.computeIfAbsent(from, ignored -> new LinkedHashSet<>()).add(to);
            }
        }
        for (String actionKey : actionKinds.keySet()) {
            if (!referencedActions.contains(actionKey)) {
                error(
                    diagnostics,
                    "action_without_transition",
                    "$.stateFlow.actions[" + actionKey + "]",
                    "Every action must be used by a transition"
                );
            }
        }
        validateReachabilityAndDeadEnds(stateCategories, edges, diagnostics);
    }

    private void validateReachabilityAndDeadEnds(
        Map<String, StateCategory> stateCategories,
        Map<String, Set<String>> edges,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        String initial = stateCategories.entrySet().stream()
            .filter(entry -> entry.getValue() == StateCategory.initial)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        if (initial != null) {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> pending = new ArrayDeque<>();
            pending.add(initial);
            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                if (visited.add(current)) {
                    pending.addAll(edges.getOrDefault(current, Set.of()));
                }
            }
            for (String stateKey : stateCategories.keySet()) {
                if (!visited.contains(stateKey)) {
                    error(
                        diagnostics,
                        "unreachable_state",
                        "$.stateFlow.states[" + stateKey + "]",
                        "State is unreachable from the initial state"
                    );
                }
            }
        }
        stateCategories.forEach((stateKey, category) -> {
            if ((category == StateCategory.initial || category == StateCategory.active)
                && edges.getOrDefault(stateKey, Set.of()).isEmpty()) {
                error(
                    diagnostics,
                    "active_state_dead_end",
                    "$.stateFlow.states[" + stateKey + "]",
                    "Initial and active states require an outgoing transition"
                );
            }
        });
    }

    private void validateGuardGraph(
        JsonNode guards,
        Set<String> guardKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (JsonNode guard : guards) {
            String key = guard.path("guardKey").asText("");
            List<String> children = new ArrayList<>();
            JsonNode values = guard.path("guardKeys");
            if (values.isArray()) {
                for (int index = 0; index < values.size(); index++) {
                    String child = values.get(index).asText("");
                    if (!guardKeys.contains(child)) {
                        error(
                            diagnostics,
                            "dangling_guard_reference",
                            "$.stateFlow.guards[" + key + "].guardKeys[" + index + "]",
                            "Composite guard references an unknown guard"
                        );
                    } else {
                        children.add(child);
                    }
                }
            }
            graph.put(key, children);
        }
        for (String key : graph.keySet()) {
            if (guardCycle(key, graph, new HashSet<>(), new HashSet<>())) {
                error(
                    diagnostics,
                    "guard_cycle",
                    "$.stateFlow.guards[" + key + "]",
                    "Guard graph contains a cycle"
                );
            }
        }
    }

    private boolean guardCycle(
        String key,
        Map<String, List<String>> graph,
        Set<String> visiting,
        Set<String> visited
    ) {
        if (visiting.contains(key)) {
            return true;
        }
        if (!visited.add(key)) {
            return false;
        }
        visiting.add(key);
        for (String child : graph.getOrDefault(key, List.of())) {
            if (guardCycle(child, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(key);
        return false;
    }

    private JsonNode requireArray(
        JsonNode flow,
        String name,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        JsonNode value = flow.path(name);
        if (!value.isArray()) {
            error(diagnostics, "invalid_state_flow_" + name, "$.stateFlow." + name, name + " must be an array");
            return null;
        }
        return value;
    }

    private void budget(
        JsonNode values,
        int maximum,
        String code,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (values.size() > maximum) {
            error(diagnostics, code, path, "State flow definition exceeds its configured budget");
        }
    }

    private String semanticKey(
        JsonNode value,
        String field,
        String path,
        Set<String> existing,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        String key = value.path(field).asText("");
        if (!SEMANTIC_KEY.matcher(key).matches()) {
            error(diagnostics, "invalid_semantic_key", path + "." + field, "Semantic key is invalid");
            return null;
        }
        if (existing.contains(key)) {
            error(diagnostics, "duplicate_semantic_key", path + "." + field, "Semantic key must be unique");
            return null;
        }
        return key;
    }

    private void validateFieldReferences(
        JsonNode values,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        boolean requireArray,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!values.isArray()) {
            if (requireArray) {
                error(diagnostics, "invalid_field_references", path, "Field references must be an array");
            }
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String key = values.get(index).asText("");
            if (!seen.add(key) || !fieldKeys.contains(key) || !activeFieldKeys.contains(key)
                || hiddenFieldKeys.contains(key)) {
                error(
                    diagnostics,
                    "invalid_required_field",
                    path + "[" + index + "]",
                    "Required field must be unique, active, and visible"
                );
            }
        }
    }

    private void validateStringSet(
        JsonNode values,
        Set<String> allowed,
        boolean required,
        String code,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!values.isArray() || (required && values.isEmpty())) {
            error(diagnostics, code, path, "A non-empty array is required");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index).asText("");
            if (!seen.add(value) || !allowed.contains(value)) {
                error(diagnostics, code, path + "[" + index + "]", "Value is duplicated or unsupported");
            }
        }
    }

    private void error(
        List<ConfigurationDiagnostic> diagnostics,
        String code,
        String path,
        String message
    ) {
        diagnostics.add(new ConfigurationDiagnostic(code, DiagnosticSeverity.error, path, message));
    }
}
