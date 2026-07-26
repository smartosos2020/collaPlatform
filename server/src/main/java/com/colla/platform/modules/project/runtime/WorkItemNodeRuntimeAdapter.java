package com.colla.platform.modules.project.runtime;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.BranchDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.BranchMode;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.EdgeDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.JoinDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.JoinPolicy;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.NodeDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.NodeKind;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.ProcessingStrategy;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.RecoveryCommandKind;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.RecoveryCommandDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.CompensationDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.StageDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.RuntimeNodeFlow;
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
public final class WorkItemNodeRuntimeAdapter {
    public RuntimeNodeFlow adapt(RuntimeConfiguration configuration) {
        if (!configuration.hasNodeFlowDefinition()) {
            return new RuntimeNodeFlow(
                "not_configured", policyVersion(configuration), null,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of()
            );
        }
        JsonNode flow = configuration.snapshot().path("nodeFlow");
        Map<String, StageDefinition> stages = stages(flow.path("stages"));
        Map<String, NodeDefinition> nodes = nodes(flow.path("nodes"));
        Map<String, EdgeDefinition> edges = edges(flow.path("edges"));
        Map<String, BranchDefinition> branches = branches(flow.path("branches"));
        Map<String, JoinDefinition> joins = joins(flow.path("joins"));
        Map<String, RecoveryCommandDefinition> recoveryCommands = recoveryCommands(
            flow.path("recoveryCommands")
        );
        Map<String, List<CompensationDefinition>> compensations = compensations(
            flow.path("compensations")
        );
        List<NodeDefinition> starts = nodes.values().stream()
            .filter(node -> node.kind() == NodeKind.start)
            .toList();
        if (starts.size() != 1) {
            throw failure("INVALID_NODE_FLOW_SNAPSHOT", "Node flow must contain exactly one start node");
        }
        return new RuntimeNodeFlow(
            "available",
            policyVersion(configuration),
            starts.get(0),
            Collections.unmodifiableMap(stages),
            Collections.unmodifiableMap(nodes),
            Collections.unmodifiableMap(edges),
            Collections.unmodifiableMap(indexBranches(branches)),
            Collections.unmodifiableMap(indexJoins(joins)),
            Collections.unmodifiableMap(recoveryCommands),
            Collections.unmodifiableMap(compensations),
            Collections.unmodifiableMap(indexEdges(edges.values(), true)),
            Collections.unmodifiableMap(indexEdges(edges.values(), false))
        );
    }

    private Map<String, StageDefinition> stages(JsonNode values) {
        LinkedHashMap<String, StageDefinition> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            StageDefinition definition = new StageDefinition(
                value.path("stageKey").asText(), value.path("label").asText(),
                value.path("description").asText(), value.path("sortOrder").asInt()
            );
            duplicate(result, definition.stageKey(), definition);
        }
        return result;
    }

    private Map<String, NodeDefinition> nodes(JsonNode values) {
        LinkedHashMap<String, NodeDefinition> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            NodeDefinition definition = new NodeDefinition(
                value.path("nodeKey").asText(),
                value.path("stageKey").asText(),
                value.path("label").asText(),
                value.path("description").asText(),
                parseKind(value.path("kind").asText()),
                parseStrategy(value.path("processingStrategy").asText()),
                strings(value.path("candidateRoles")),
                nullableInt(value.get("quorumCount")),
                value.path("configuration").deepCopy(),
                value.path("sortOrder").asInt()
            );
            duplicate(result, definition.nodeKey(), definition);
        }
        return result;
    }

    private Map<String, EdgeDefinition> edges(JsonNode values) {
        LinkedHashMap<String, EdgeDefinition> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            EdgeDefinition definition = new EdgeDefinition(
                value.path("edgeKey").asText(),
                value.path("fromNodeKey").asText(),
                value.path("toNodeKey").asText(),
                value.path("priority").asInt(),
                nullableNode(value.get("condition"))
            );
            duplicate(result, definition.edgeKey(), definition);
        }
        return result;
    }

    private Map<String, BranchDefinition> branches(JsonNode values) {
        LinkedHashMap<String, BranchDefinition> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            BranchDefinition definition = new BranchDefinition(
                value.path("branchKey").asText(), value.path("nodeKey").asText(),
                parseBranch(value.path("mode").asText()), strings(value.path("edgeKeys"))
            );
            duplicate(result, definition.branchKey(), definition);
        }
        return result;
    }

    private Map<String, JoinDefinition> joins(JsonNode values) {
        LinkedHashMap<String, JoinDefinition> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            JoinDefinition definition = new JoinDefinition(
                value.path("joinKey").asText(), value.path("nodeKey").asText(),
                parseJoin(value.path("policy").asText()), strings(value.path("inboundEdgeKeys")),
                nullableInt(value.get("quorumCount"))
            );
            duplicate(result, definition.joinKey(), definition);
        }
        return result;
    }

    private Map<String, RecoveryCommandDefinition> recoveryCommands(JsonNode values) {
        LinkedHashMap<String, RecoveryCommandDefinition> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            RecoveryCommandDefinition definition = new RecoveryCommandDefinition(
                value.path("commandKey").asText(),
                parseRecoveryKind(value.path("kind").asText()),
                strings(value.path("fromNodeKeys")),
                nullableText(value.get("targetNodeKey")),
                strings(value.path("authorizedRoles")),
                value.path("closeMode").asText(),
                value.path("confirmation").asText()
            );
            duplicate(result, definition.commandKey(), definition);
        }
        return result;
    }

    private Map<String, List<CompensationDefinition>> compensations(JsonNode values) {
        LinkedHashMap<String, List<CompensationDefinition>> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            CompensationDefinition definition = new CompensationDefinition(
                value.path("compensationKey").asText(),
                value.path("commandKey").asText(),
                value.path("actionKey").asText(),
                value.path("sortOrder").asInt()
            );
            result.computeIfAbsent(definition.commandKey(), ignored -> new ArrayList<>())
                .add(definition);
        }
        result.replaceAll((key, definitions) -> definitions.stream()
            .sorted(Comparator.comparingInt(CompensationDefinition::sortOrder)
                .thenComparing(CompensationDefinition::compensationKey))
            .toList());
        return result;
    }

    private Map<String, BranchDefinition> indexBranches(Map<String, BranchDefinition> values) {
        LinkedHashMap<String, BranchDefinition> result = new LinkedHashMap<>();
        values.values().forEach(value -> duplicate(result, value.nodeKey(), value));
        return result;
    }

    private Map<String, JoinDefinition> indexJoins(Map<String, JoinDefinition> values) {
        LinkedHashMap<String, JoinDefinition> result = new LinkedHashMap<>();
        values.values().forEach(value -> duplicate(result, value.nodeKey(), value));
        return result;
    }

    private Map<String, List<EdgeDefinition>> indexEdges(
        java.util.Collection<EdgeDefinition> values,
        boolean outgoing
    ) {
        LinkedHashMap<String, List<EdgeDefinition>> result = new LinkedHashMap<>();
        values.forEach(value -> result.computeIfAbsent(
            outgoing ? value.fromNodeKey() : value.toNodeKey(), ignored -> new ArrayList<>()
        ).add(value));
        Comparator<EdgeDefinition> order = Comparator.comparingInt(EdgeDefinition::priority)
            .thenComparing(EdgeDefinition::edgeKey);
        result.replaceAll((key, value) -> value.stream().sorted(order).toList());
        return result;
    }

    private List<String> strings(JsonNode node) {
        ArrayList<String> result = new ArrayList<>();
        node.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private Integer nullableInt(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private JsonNode nullableNode(JsonNode node) {
        return node == null || node.isNull() ? null : node.deepCopy();
    }

    private String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private NodeKind parseKind(String value) {
        try {
            return NodeKind.parse(value);
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_NODE_FLOW_SNAPSHOT", "Unknown node kind in bound snapshot");
        }
    }

    private ProcessingStrategy parseStrategy(String value) {
        try {
            return ProcessingStrategy.parse(value);
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_NODE_FLOW_SNAPSHOT", "Unknown processing strategy in bound snapshot");
        }
    }

    private BranchMode parseBranch(String value) {
        try {
            return BranchMode.parse(value);
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_NODE_FLOW_SNAPSHOT", "Unknown branch mode in bound snapshot");
        }
    }

    private JoinPolicy parseJoin(String value) {
        try {
            return JoinPolicy.parse(value);
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_NODE_FLOW_SNAPSHOT", "Unknown join policy in bound snapshot");
        }
    }

    private RecoveryCommandKind parseRecoveryKind(String value) {
        try {
            return RecoveryCommandKind.parse(value);
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_NODE_FLOW_SNAPSHOT", "Unknown recovery command kind in bound snapshot");
        }
    }

    private String policyVersion(RuntimeConfiguration configuration) {
        return configuration.versionId() + ":" + configuration.configHash();
    }

    private <T> void duplicate(Map<String, T> values, String key, T value) {
        if (values.putIfAbsent(key, value) != null) {
            throw failure("INVALID_NODE_FLOW_SNAPSHOT", "Node flow contains duplicate semantic keys");
        }
    }
}
