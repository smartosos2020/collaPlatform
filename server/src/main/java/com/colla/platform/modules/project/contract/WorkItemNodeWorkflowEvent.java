package com.colla.platform.modules.project.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record WorkItemNodeWorkflowEvent(
    UUID spaceId,
    UUID instanceId,
    UUID typeDefinitionId,
    UUID typeVersionId,
    String configHash,
    String eventKind,
    String nodeKey,
    UUID taskId,
    long workItemVersion,
    long aggregateVersion,
    String decisionReference
) {
    public static final String AGGREGATE_TYPE = "work_item";
    public static final String EVENT_TYPE = "node_workflow.changed";
    public static final int EVENT_SCHEMA_VERSION = 1;

    public Map<String, Object> payload() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("eventSchemaVersion", EVENT_SCHEMA_VERSION);
        result.put("spaceId", spaceId.toString());
        result.put("instanceId", instanceId.toString());
        result.put("typeDefinitionId", typeDefinitionId.toString());
        result.put("typeVersionId", typeVersionId.toString());
        result.put("configHash", configHash);
        result.put("eventKind", eventKind);
        result.put("nodeKey", nodeKey);
        result.put("taskId", taskId == null ? null : taskId.toString());
        result.put("workItemVersion", workItemVersion);
        result.put("aggregateVersion", aggregateVersion);
        result.put("decisionReference", decisionReference);
        return java.util.Collections.unmodifiableMap(result);
    }
}
