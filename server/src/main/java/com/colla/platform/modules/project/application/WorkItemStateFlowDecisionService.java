package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionKind;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.GuardDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.GuardKind;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.StateCategory;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.TransitionDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.ActionDecision;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.AvailableAction;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.DecisionContext;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.RuntimeFlow;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public final class WorkItemStateFlowDecisionService {
    public ActionDecision decide(
        RuntimeFlow flow,
        String currentStateKey,
        String actionKey,
        DecisionContext context
    ) {
        if (!flow.configured()) {
            return ActionDecision.denied(actionKey, "capability_missing");
        }
        ActionDefinition action = flow.actions().get(actionKey);
        if (action == null) {
            return ActionDecision.denied(actionKey, "action_unavailable");
        }
        TransitionDefinition transition = flow.transitions().stream()
            .filter(value -> value.actionKey().equals(actionKey))
            .filter(value -> value.fromStateKey().equals(currentStateKey))
            .findFirst()
            .orElse(null);
        if (transition == null) {
            return ActionDecision.denied(actionKey, "action_unavailable");
        }
        if (!validLifecycle(flow, action, transition)) {
            return ActionDecision.denied(actionKey, "action_unavailable");
        }
        if (!authorized(action, context)) {
            return ActionDecision.denied(actionKey, "not_authorized");
        }
        if (transition.guardKey() != null
            && !guard(flow, transition.guardKey(), context, new HashSet<>())) {
            return ActionDecision.denied(actionKey, "guard_not_satisfied");
        }
        if (!required(action, context.fieldValues())) {
            return ActionDecision.denied(actionKey, "required_fields_missing");
        }
        return new ActionDecision(actionKey, true, "allowed", action, transition);
    }

    private boolean validLifecycle(
        RuntimeFlow flow,
        ActionDefinition action,
        TransitionDefinition transition
    ) {
        var source = flow.states().get(transition.fromStateKey());
        var target = flow.states().get(transition.toStateKey());
        if (source == null || target == null) {
            return false;
        }
        StateCategory from = source.category();
        StateCategory to = target.category();
        return switch (action.kind()) {
            case forward -> from != StateCategory.terminal && from != StateCategory.canceled;
            case return_action -> from != StateCategory.terminal
                && from != StateCategory.canceled
                && to != StateCategory.terminal
                && to != StateCategory.canceled;
            case reopen -> from == StateCategory.terminal
                && to != StateCategory.terminal
                && to != StateCategory.canceled;
            case terminate -> from != StateCategory.terminal
                && from != StateCategory.canceled
                && to == StateCategory.canceled;
            case restore -> from == StateCategory.canceled
                && to != StateCategory.terminal
                && to != StateCategory.canceled;
            case correction -> true;
        };
    }

    public List<AvailableAction> available(
        RuntimeFlow flow,
        String currentStateKey,
        DecisionContext context
    ) {
        ArrayList<AvailableAction> result = new ArrayList<>();
        flow.actions().values().stream()
            .sorted(Comparator.comparingInt(ActionDefinition::sortOrder)
                .thenComparing(ActionDefinition::actionKey))
            .forEach(action -> {
                ActionDecision decision = decide(flow, currentStateKey, action.actionKey(), context);
                if (decision.allowed() || "required_fields_missing".equals(decision.reasonCode())) {
                    result.add(new AvailableAction(
                        action.actionKey(),
                        action.label(),
                        action.kind().name(),
                        action.requiredFieldKeys(),
                        action.sortOrder(),
                        flow.policyVersion()
                    ));
                }
            });
        return List.copyOf(result);
    }

    private boolean authorized(ActionDefinition action, DecisionContext context) {
        if (action.authorizedRoles().contains(context.spaceRole())) {
            return true;
        }
        return context.participantRoles().stream().anyMatch(action.authorizedRoles()::contains);
    }

    private boolean required(ActionDefinition action, JsonNode values) {
        for (String fieldKey : action.requiredFieldKeys()) {
            JsonNode value = values == null ? null : values.get(fieldKey);
            if (missing(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean guard(
        RuntimeFlow flow,
        String guardKey,
        DecisionContext context,
        Set<String> evaluating
    ) {
        GuardDefinition guard = flow.guards().get(guardKey);
        if (guard == null || !evaluating.add(guardKey)) {
            return false;
        }
        try {
            return switch (guard.kind()) {
                case field -> field(guard, context.fieldValues());
                case participant -> participant(guard, context.participantRoles());
                case space_role -> spaceRole(guard, context.spaceRole());
                case all -> guard.guardKeys().stream().allMatch(key -> guard(flow, key, context, evaluating));
                case any -> guard.guardKeys().stream().anyMatch(key -> guard(flow, key, context, evaluating));
                case not -> guard.guardKeys().size() == 1
                    && !guard(flow, guard.guardKeys().get(0), context, evaluating);
            };
        } finally {
            evaluating.remove(guardKey);
        }
    }

    private boolean field(GuardDefinition guard, JsonNode values) {
        JsonNode actual = values == null ? null : values.get(guard.fieldKey());
        JsonNode expected = guard.value();
        return switch (guard.operator()) {
            case "present" -> !missing(actual);
            case "absent" -> missing(actual);
            case "eq" -> actual != null && actual.equals(expected);
            case "neq" -> actual == null || !actual.equals(expected);
            case "in" -> expected != null && expected.isArray()
                && containsNode(expected, actual);
            case "not_in" -> expected == null || !expected.isArray()
                || !containsNode(expected, actual);
            case "contains" -> containsNode(actual, expected);
            case "not_contains" -> !containsNode(actual, expected);
            default -> false;
        };
    }

    private boolean participant(GuardDefinition guard, Set<String> participantRoles) {
        boolean contains = participantRoles.contains(guard.participantRole());
        return "has_role".equals(guard.operator()) ? contains
            : "missing_role".equals(guard.operator()) && !contains;
    }

    private boolean spaceRole(GuardDefinition guard, String role) {
        boolean contains = guard.spaceRoles().contains(role);
        return "in".equals(guard.operator()) ? contains
            : "not_in".equals(guard.operator()) && !contains;
    }

    private boolean containsNode(JsonNode container, JsonNode candidate) {
        if (container == null || candidate == null) {
            return false;
        }
        if (container.isArray()) {
            for (JsonNode value : container) {
                if (value.equals(candidate)) {
                    return true;
                }
            }
            return false;
        }
        return container.isTextual() && candidate.isTextual()
            && container.asText().contains(candidate.asText());
    }

    private boolean missing(JsonNode value) {
        return value == null || value.isNull()
            || value.isTextual() && value.asText().isBlank()
            || value.isArray() && value.isEmpty();
    }
}
