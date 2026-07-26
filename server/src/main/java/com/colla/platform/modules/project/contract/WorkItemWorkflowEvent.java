package com.colla.platform.modules.project.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record WorkItemWorkflowEvent(
    UUID spaceId,
    UUID typeDefinitionId,
    UUID typeVersionId,
    String configHash,
    String actionKey,
    String actionKind,
    String fromStateKey,
    String toStateKey,
    long workItemVersion,
    long aggregateVersion,
    String decisionReference
) {
    public static final String AGGREGATE_TYPE = "work_item";
    public static final String ACTION_EXECUTED = "workflow.action_executed";
    public static final String STATE_CHANGED = "workflow.state_changed";
    public static final String INITIALIZED = "workflow.initialized";
    public static final String BINDING_CHANGED = "workflow.binding_changed";
    public static final int EVENT_SCHEMA_VERSION = 1;

    public Map<String, Object> payload() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("eventSchemaVersion", EVENT_SCHEMA_VERSION);
        result.put("spaceId", spaceId.toString());
        result.put("typeDefinitionId", typeDefinitionId.toString());
        result.put("typeVersionId", typeVersionId.toString());
        result.put("configHash", configHash);
        result.put("actionKey", actionKey);
        result.put("actionKind", actionKind);
        result.put("fromStateKey", fromStateKey);
        result.put("toStateKey", toStateKey);
        result.put("workItemVersion", workItemVersion);
        result.put("aggregateVersion", aggregateVersion);
        result.put("decisionReference", decisionReference);
        return java.util.Collections.unmodifiableMap(result);
    }
}
