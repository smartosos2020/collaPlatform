package com.colla.platform.modules.platform.contract;

import java.util.Map;
import java.util.UUID;

public record PlatformObjectSummary(
    String objectType,
    UUID objectId,
    ObjectAccessState accessState,
    String title,
    String subtitle,
    String status,
    String webPath,
    String deepLink,
    Map<String, Object> metadata
) {
    public PlatformObjectSummary {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static PlatformObjectSummary unavailable(String objectType, UUID objectId, ObjectAccessState state) {
        return new PlatformObjectSummary(objectType, objectId, state, null, null, null, null, null, Map.of());
    }
}
