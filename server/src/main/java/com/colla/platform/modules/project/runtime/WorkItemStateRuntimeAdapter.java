package com.colla.platform.modules.project.runtime;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionKind;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.GuardDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.GuardKind;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.StateCategory;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.StateDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.TransitionDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.RuntimeFlow;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemStateRuntimeAdapter {
    public RuntimeFlow adapt(RuntimeConfiguration configuration) {
        if (!configuration.hasStateFlow()) {
            return new RuntimeFlow(
                "not_configured",
                policyVersion(configuration),
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
            );
        }
        JsonNode flow = configuration.snapshot().path("stateFlow");
        Map<String, StateDefinition> states = states(flow.path("states"));
        List<StateDefinition> initial = states.values().stream()
            .filter(value -> value.category() == StateCategory.initial)
            .toList();
        if (initial.size() != 1) {
            throw failure("INVALID_STATE_FLOW_SNAPSHOT", "State flow must contain exactly one initial state");
        }
        return new RuntimeFlow(
            "available",
            policyVersion(configuration),
            initial.get(0),
            Collections.unmodifiableMap(states),
            Collections.unmodifiableMap(actions(flow.path("actions"))),
            Collections.unmodifiableMap(guards(flow.path("guards"))),
            Collections.unmodifiableList(transitions(flow.path("transitions")))
        );
    }

    private Map<String, StateDefinition> states(JsonNode nodes) {
        LinkedHashMap<String, StateDefinition> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            StateDefinition value = new StateDefinition(
                node.path("stateKey").asText(),
                node.path("label").asText(),
                node.path("description").asText(),
                node.path("color").asText(),
                StateCategory.parse(node.path("category").asText()),
                node.path("sortOrder").asInt()
            );
            duplicate(result, value.stateKey(), value);
        }
        return result;
    }

    private Map<String, ActionDefinition> actions(JsonNode nodes) {
        LinkedHashMap<String, ActionDefinition> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            ActionDefinition value = new ActionDefinition(
                node.path("actionKey").asText(),
                node.path("label").asText(),
                node.path("description").asText(),
                ActionKind.parse(node.path("kind").asText()),
                strings(node.path("authorizedRoles")),
                strings(node.path("requiredFieldKeys")),
                node.path("fieldPatch").deepCopy(),
                strings(node.path("sideEffectKeys")),
                node.path("sortOrder").asInt()
            );
            duplicate(result, value.actionKey(), value);
        }
        return result;
    }

    private Map<String, GuardDefinition> guards(JsonNode nodes) {
        LinkedHashMap<String, GuardDefinition> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            GuardDefinition value = new GuardDefinition(
                node.path("guardKey").asText(),
                GuardKind.parse(node.path("kind").asText()),
                node.path("operator").asText(),
                nullable(node, "fieldKey"),
                nullable(node, "participantRole"),
                strings(node.path("spaceRoles")),
                node.path("value").deepCopy(),
                strings(node.path("guardKeys"))
            );
            duplicate(result, value.guardKey(), value);
        }
        return result;
    }

    private List<TransitionDefinition> transitions(JsonNode nodes) {
        ArrayList<TransitionDefinition> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            result.add(new TransitionDefinition(
                node.path("transitionKey").asText(),
                node.path("actionKey").asText(),
                node.path("fromStateKey").asText(),
                node.path("toStateKey").asText(),
                nullable(node, "guardKey"),
                node.path("sortOrder").asInt()
            ));
        }
        result.sort(Comparator.comparingInt(TransitionDefinition::sortOrder)
            .thenComparing(TransitionDefinition::transitionKey));
        return result;
    }

    private List<String> strings(JsonNode node) {
        ArrayList<String> result = new ArrayList<>();
        node.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private String nullable(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String policyVersion(RuntimeConfiguration configuration) {
        return configuration.versionId() + ":" + configuration.configHash();
    }

    private <T> void duplicate(Map<String, T> values, String key, T value) {
        if (values.putIfAbsent(key, value) != null) {
            throw failure("INVALID_STATE_FLOW_SNAPSHOT", "State flow contains duplicate semantic keys");
        }
    }
}
