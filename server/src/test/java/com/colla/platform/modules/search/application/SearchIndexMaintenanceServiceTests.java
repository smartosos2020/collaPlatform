package com.colla.platform.modules.search.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.search.infrastructure.SearchRepository.RebuildPage;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SearchIndexMaintenanceServiceTests {
    @Test
    void adminRunsBoundedResumableAuditedBatch() {
        SearchIndexService indexService = mock(SearchIndexService.class);
        AuditLog auditLog = mock(AuditLog.class);
        CurrentUser admin = user(Set.of("admin"), Set.of());
        UUID cursor = UUID.randomUUID();
        RebuildPage page = new RebuildPage("issue", cursor, 250, false);
        when(indexService.rebuildBatch(admin.workspaceId(), "issue", null, 250)).thenReturn(page);

        new SearchIndexMaintenanceService(indexService, auditLog)
            .rebuildBatch(admin, "issue", null, 999, "Rebuild after projection migration");

        verify(indexService).rebuildBatch(admin.workspaceId(), "issue", null, 250);
        verify(auditLog).log(
            eq(admin),
            eq("search_index.rebuild_batch"),
            eq("search_index"),
            eq(admin.workspaceId()),
            any()
        );
    }

    @Test
    void nonAdminCannotRunRebuild() {
        SearchIndexService indexService = mock(SearchIndexService.class);
        AuditLog auditLog = mock(AuditLog.class);
        SearchIndexMaintenanceService service = new SearchIndexMaintenanceService(indexService, auditLog);

        assertThatThrownBy(() -> service.rebuildBatch(
            user(Set.of("member"), Set.of()),
            "issue",
            null,
            50,
            "Unauthorized rebuild attempt"
        )).isInstanceOf(ResponseStatusException.class);
    }

    private static CurrentUser user(Set<String> roles, Set<String> permissions) {
        return new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "tester",
            "Tester",
            roles,
            permissions
        );
    }
}
