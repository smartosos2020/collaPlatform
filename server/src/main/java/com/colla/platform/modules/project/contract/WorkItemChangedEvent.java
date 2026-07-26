package com.colla.platform.modules.project.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal public event payload for downstream projection consumers.
 *
 * <p>Consumers must resolve the work item through the public object/API boundary. Titles, field
 * values, participants and access-policy material are intentionally excluded.</p>
 */
public record WorkItemChangedEvent(
    UUID spaceId,
    UUID typeDefinitionId,
    UUID typeVersionId,
    String configHash,
    long workItemVersion,
    String status,
    String mutation
) {
    public static final String EVENT_TYPE = "work_item.changed";
    public static final int EVENT_VERSION = 1;
    public static final String AGGREGATE_TYPE = "work_item";

    public Map<String, Object> payload() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spaceId", spaceId.toString());
        result.put("typeDefinitionId", typeDefinitionId.toString());
        result.put("typeVersionId", typeVersionId.toString());
        result.put("configHash", configHash);
        result.put("version", workItemVersion);
        result.put("status", status);
        result.put("mutation", mutation);
        return Map.copyOf(result);
    }
}
