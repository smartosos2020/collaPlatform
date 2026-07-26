package com.colla.platform.modules.project.contract;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal public event for notification/search consumers. It carries no form values,
 * artifact metadata, candidate identities, or provider-private references.
 */
public record NodeTaskLifecycleEvent(
    UUID spaceId,
    UUID taskId,
    UUID workItemId,
    String eventKind,
    String nodeKey,
    Instant dueAt
) {
    public static final String EVENT_TYPE = "node_task.lifecycle";
    public static final String AGGREGATE_TYPE = "work_item";
    public static final int EVENT_SCHEMA_VERSION = 1;

    public Map<String, Object> payload() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("eventSchemaVersion", EVENT_SCHEMA_VERSION);
        result.put("spaceId", spaceId.toString());
        result.put("taskId", taskId.toString());
        result.put("workItemId", workItemId.toString());
        result.put("eventKind", eventKind);
        result.put("nodeKey", nodeKey);
        result.put("dueAt", dueAt == null ? null : dueAt.toString());
        return java.util.Collections.unmodifiableMap(result);
    }
}
