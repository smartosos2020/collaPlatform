package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemTypePresetCatalog.PresetTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemNodeFlowPresetCatalog {
    private static final Set<String> NODE_FLOW_PRESET_KEYS = Set.of("project", "release");
    private static final List<String> EXECUTION_ROLES = List.of("admin", "member", "owner");
    private final ObjectMapper objectMapper;

    public WorkItemNodeFlowPresetCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<JsonNode> nodeFlowFor(PresetTemplate preset) {
        return nodeFlowFor(preset.typeKey());
    }

    public Optional<JsonNode> nodeFlowFor(String typeKey) {
        if (!NODE_FLOW_PRESET_KEYS.contains(typeKey)) {
            return Optional.empty();
        }
        return Optional.of(standardDeliveryFlow());
    }

    private JsonNode standardDeliveryFlow() {
        ObjectNode flow = objectMapper.createObjectNode();
        flow.putArray("stages");
        flow.putArray("nodes");
        flow.putArray("edges");
        flow.putArray("branches");
        flow.putArray("joins");
        flow.putArray("recoveryCommands");
        flow.putArray("compensations");

        addStage(flow, "intake", "启动", 100);
        addStage(flow, "delivery", "执行", 200);
        addStage(flow, "acceptance", "验收", 300);

        addNode(flow, "start", "intake", "开始", "start", "automatic", 100, null);
        addNode(flow, "plan", "intake", "制定计划", "manual", "single", 200, null);
        addNode(flow, "delivery_split", "delivery", "并行交付", "branch", "automatic", 300, null);
        addNode(flow, "primary_delivery", "delivery", "主要交付", "manual", "all", 400, null);
        addNode(flow, "quality_review", "delivery", "质量复核", "manual", "any", 500, null);
        addNode(flow, "delivery_join", "delivery", "交付汇聚", "join", "automatic", 600, null);
        addNode(flow, "acceptance_review", "acceptance", "验收会签", "manual", "quorum", 700, 2);
        addNode(flow, "completed", "acceptance", "完成", "end", "automatic", 800, null);

        addEdge(flow, "start_plan", "start", "plan", 100);
        addEdge(flow, "plan_delivery_split", "plan", "delivery_split", 100);
        addEdge(flow, "split_primary_delivery", "delivery_split", "primary_delivery", 100);
        addEdge(flow, "split_quality_review", "delivery_split", "quality_review", 200);
        addEdge(flow, "primary_delivery_join", "primary_delivery", "delivery_join", 100);
        addEdge(flow, "quality_review_join", "quality_review", "delivery_join", 100);
        addEdge(flow, "join_acceptance_review", "delivery_join", "acceptance_review", 100);
        addEdge(flow, "acceptance_completed", "acceptance_review", "completed", 100);

        ObjectNode branch = flow.withArray("branches").addObject();
        branch.put("branchKey", "delivery_parallel");
        branch.put("nodeKey", "delivery_split");
        branch.put("mode", "parallel");
        branch.putArray("edgeKeys").add("split_primary_delivery").add("split_quality_review");

        ObjectNode join = flow.withArray("joins").addObject();
        join.put("joinKey", "delivery_all");
        join.put("nodeKey", "delivery_join");
        join.put("policy", "all");
        join.putArray("inboundEdgeKeys").add("primary_delivery_join").add("quality_review_join");
        join.putNull("quorumCount");

        addRecovery(
            flow, "return_to_plan", "return_to",
            List.of("primary_delivery", "quality_review", "acceptance_review"),
            "plan", "RETURN_NODE_WORKFLOW"
        );
        addRecovery(
            flow, "jump_to_acceptance", "jump",
            List.of("plan", "primary_delivery", "quality_review"),
            "acceptance_review", "JUMP_NODE_WORKFLOW"
        );
        addRecovery(
            flow, "terminate_delivery", "terminate",
            List.of("plan", "primary_delivery", "quality_review", "acceptance_review"),
            null, "TERMINATE_NODE_WORKFLOW"
        );
        addRecovery(
            flow, "correct_to_plan", "correct",
            List.of("plan", "primary_delivery", "quality_review", "acceptance_review"),
            "plan", "CORRECT_NODE_WORKFLOW"
        );
        addCompensation(
            flow, "terminate_audit_marker", "terminate_delivery", "record_audit_marker", 100
        );
        return flow;
    }

    private void addRecovery(
        ObjectNode flow,
        String commandKey,
        String kind,
        List<String> fromNodeKeys,
        String targetNodeKey,
        String confirmation
    ) {
        ObjectNode command = flow.withArray("recoveryCommands").addObject();
        command.put("commandKey", commandKey);
        command.put("kind", kind);
        ArrayNode sources = command.putArray("fromNodeKeys");
        fromNodeKeys.forEach(sources::add);
        if (targetNodeKey == null) {
            command.putNull("targetNodeKey");
        } else {
            command.put("targetNodeKey", targetNodeKey);
        }
        command.putArray("authorizedRoles").add("admin").add("owner");
        command.put("closeMode", "cancel_open");
        command.put("confirmation", confirmation);
    }

    private void addCompensation(
        ObjectNode flow,
        String compensationKey,
        String commandKey,
        String actionKey,
        int sortOrder
    ) {
        ObjectNode compensation = flow.withArray("compensations").addObject();
        compensation.put("compensationKey", compensationKey);
        compensation.put("commandKey", commandKey);
        compensation.put("actionKey", actionKey);
        compensation.put("sortOrder", sortOrder);
    }

    private void addStage(ObjectNode flow, String key, String label, int sortOrder) {
        ObjectNode stage = flow.withArray("stages").addObject();
        stage.put("stageKey", key);
        stage.put("label", label);
        stage.put("description", "");
        stage.put("sortOrder", sortOrder);
    }

    private void addNode(
        ObjectNode flow,
        String key,
        String stageKey,
        String label,
        String kind,
        String strategy,
        int sortOrder,
        Integer quorumCount
    ) {
        ObjectNode node = flow.withArray("nodes").addObject();
        node.put("nodeKey", key);
        node.put("stageKey", stageKey);
        node.put("label", label);
        node.put("description", "");
        node.put("kind", kind);
        node.put("processingStrategy", strategy);
        ArrayNode roles = node.putArray("candidateRoles");
        if ("manual".equals(kind)) {
            EXECUTION_ROLES.forEach(roles::add);
        }
        if (quorumCount == null) {
            node.putNull("quorumCount");
        } else {
            node.put("quorumCount", quorumCount);
        }
        ObjectNode configuration = objectMapper.createObjectNode();
        if ("manual".equals(kind)) {
            configuration.putObject("schedule")
                .put("plannedDelayMinutes", 0)
                .put("dueAfterMinutes", 1440)
                .put("escalationAfterMinutes", 60)
                .put("timeZone", "UTC")
                .put("calendar", "elapsed")
                .put("pausePolicy", "not_supported");
        }
        node.set("configuration", configuration);
        node.put("sortOrder", sortOrder);
    }

    private void addEdge(ObjectNode flow, String key, String from, String to, int priority) {
        ObjectNode edge = flow.withArray("edges").addObject();
        edge.put("edgeKey", key);
        edge.put("fromNodeKey", from);
        edge.put("toNodeKey", to);
        edge.put("priority", priority);
        edge.putNull("condition");
    }
}
