package com.colla.platform.modules.project.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal public event contract for relation projection consumers.
 *
 * <p>The event deliberately excludes titles, field values, participants, policy details and
 * workflow-private identifiers. Consumers resolve visible objects through public APIs.</p>
 */
public record WorkItemRelationChangedEvent(
    UUID spaceId,
    UUID relationId,
    String relationKey,
    UUID sourceWorkItemId,
    UUID targetWorkItemId,
    long relationVersion,
    String mutation
) {
    public static final String EVENT_TYPE = "work_item_relation.changed";
    public static final int EVENT_VERSION = 1;
    public static final String AGGREGATE_TYPE = "work_item_relation";

    public Map<String, Object> payload() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spaceId", spaceId.toString());
        result.put("relationId", relationId.toString());
        result.put("relationKey", relationKey);
        result.put("sourceWorkItemId", sourceWorkItemId.toString());
        result.put("targetWorkItemId", targetWorkItemId.toString());
        result.put("version", relationVersion);
        result.put("mutation", mutation);
        return Map.copyOf(result);
    }
}
