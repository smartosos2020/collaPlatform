package com.colla.platform.modules.audit.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditTimelineQuery {
    List<AuditTimelineEntry> workItemEntries(
        UUID workspaceId, List<UUID> visibleWorkItemIds, int limit
    );

    record AuditTimelineEntry(
        UUID id,
        UUID workItemId,
        String action,
        UUID actorId,
        Instant occurredAt
    ) {
    }
}
