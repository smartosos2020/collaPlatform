package com.colla.platform.modules.search.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.search.infrastructure.SearchRepository.RebuildPage;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchIndexMaintenanceService {
    private final SearchIndexService searchIndexService;
    private final AuditLog auditLog;

    public SearchIndexMaintenanceService(SearchIndexService searchIndexService, AuditLog auditLog) {
        this.searchIndexService = searchIndexService;
        this.auditLog = auditLog;
    }

    @Transactional
    public RebuildPage rebuildBatch(
        CurrentUser actor,
        String objectType,
        UUID afterId,
        int requestedLimit,
        String reason
    ) {
        requireOperator(actor);
        String normalizedReason = requireReason(reason);
        int limit = Math.min(Math.max(requestedLimit, 1), 250);
        RebuildPage page;
        try {
            page = searchIndexService.rebuildBatch(actor.workspaceId(), objectType, afterId, limit);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        auditLog.log(actor, "search_index.rebuild_batch", "search_index", actor.workspaceId(), Map.of(
            "objectType", page.objectType(),
            "requestedAfterId", afterId == null ? "start" : afterId.toString(),
            "nextCursor", page.nextCursor() == null ? "none" : page.nextCursor().toString(),
            "processed", page.processed(),
            "done", page.done(),
            "reason", normalizedReason
        ));
        return page;
    }

    @Transactional
    public void rebuildAll(CurrentUser actor, String reason) {
        requireOperator(actor);
        String normalizedReason = requireReason(reason);
        searchIndexService.refreshWorkspaceIndex(actor.workspaceId());
        auditLog.log(actor, "search_index.rebuilt", "search_index", actor.workspaceId(), Map.of(
            "scope", "workspace",
            "reason", normalizedReason
        ));
    }

    private static void requireOperator(CurrentUser actor) {
        if (actor == null || (!actor.hasRole("admin") && !actor.hasPermission("admin.access"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason must contain 10 to 512 characters");
        }
        return normalized;
    }
}
