package com.colla.platform.modules.audit.application;

import com.colla.platform.modules.audit.contract.AuditTimelineQuery;
import com.colla.platform.modules.audit.infrastructure.AuditRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class AuditTimelineQueryAdapter implements AuditTimelineQuery {
    private final AuditRepository repository;

    public AuditTimelineQueryAdapter(AuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AuditTimelineEntry> workItemEntries(
        UUID workspaceId, List<UUID> visibleWorkItemIds, int limit
    ) {
        if (visibleWorkItemIds.size() > 100 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Audit timeline projection budget exceeded");
        }
        return visibleWorkItemIds.stream()
            .flatMap(workItemId -> repository.list(
                workspaceId, null, "work_item", workItemId, null, limit
            ).stream().map(entry -> new AuditTimelineEntry(
                entry.id(), workItemId, entry.action(), entry.actorId(), entry.createdAt()
            )))
            .sorted(Comparator.comparing(AuditTimelineEntry::occurredAt).reversed()
                .thenComparing(entry -> entry.id().toString()))
            .limit(limit)
            .toList();
    }
}
