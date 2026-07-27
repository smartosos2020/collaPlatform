package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryPlan;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.CreateCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.PresentationConfig;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedView;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ColumnSpec;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemSavedViewRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemSavedViewRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemSavedViewRepository.CommandStart;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkItemSavedViewServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void completedCreateCommandReplaysExactStoredResponseWithoutDuplicateMutation() {
        Fixture fixture = fixture();
        String requestId = "saved-view-create";
        UUID viewId = UUID.nameUUIDFromBytes(
            (WORKSPACE + ":" + SPACE + ":" + USER + ":create:" + requestId)
                .getBytes(StandardCharsets.UTF_8)
        );
        SavedView stored = savedView(viewId, USER, true);
        AtomicReference<CommandStart> started = new AtomicReference<>();
        when(fixture.queries.explain(any(), eq(SPACE), any())).thenReturn(
            new QueryPlan("a".repeat(64), query(), List.of("system-field"), 100, true)
        );
        when(fixture.repository.tryStartCommand(any())).thenAnswer(invocation -> {
            started.set(invocation.getArgument(0));
            return true;
        });
        when(fixture.repository.findCommand(eq(WORKSPACE), eq(SPACE), eq("create"), eq(requestId)))
            .thenAnswer(ignored -> {
                CommandStart command = started.get();
                return Optional.of(new CommandReceipt(
                    UUID.randomUUID(),
                    SPACE,
                    viewId,
                    "create",
                    requestId,
                    command.requestHash(),
                    command.expectedVersion(),
                    USER,
                    "completed",
                    JSON.valueToTree(stored)
                ));
            });

        SavedView replay = fixture.service.create(
            user(),
            SPACE,
            new CreateCommand(
                requestId,
                stored.name(),
                stored.description(),
                stored.scope(),
                query(),
                presentation()
            )
        );

        assertThat(replay).isEqualTo(stored);
        verify(fixture.repository, never()).create(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void sharedViewExecutionStillFailsClosedWhenUnderlyingRowsAreRevoked() {
        Fixture fixture = fixture();
        UUID owner = UUID.fromString("30000000-0000-0000-0000-000000000002");
        SavedView shared = savedView(UUID.randomUUID(), owner, false);
        when(fixture.repository.findAccessible(WORKSPACE, SPACE, USER, shared.id()))
            .thenReturn(Optional.of(shared));
        when(fixture.queries.explain(any(), eq(SPACE), any())).thenReturn(
            new QueryPlan("b".repeat(64), query(), List.of("system-field"), 100, true)
        );
        when(fixture.views.render(any(), eq(SPACE), any()))
            .thenThrow(new WorkItemRuntimeException("FORBIDDEN", "Underlying row access was revoked"));

        assertThatThrownBy(() -> fixture.service.execute(user(), SPACE, shared.id()))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("revoked");
    }

    private static Fixture fixture() {
        WorkItemSavedViewRepository repository = mock(WorkItemSavedViewRepository.class);
        WorkItemService workItems = mock(WorkItemService.class);
        WorkItemQueryService queries = mock(WorkItemQueryService.class);
        WorkItemViewService views = mock(WorkItemViewService.class);
        WorkItemTreeViewService trees = mock(WorkItemTreeViewService.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        WorkItemSavedViewService service = new WorkItemSavedViewService(
            repository,
            workItems,
            queries,
            views,
            trees,
            spaces,
            mock(AuditLog.class),
            mock(TransactionalOutbox.class),
            JSON
        );
        return new Fixture(service, repository, queries, views);
    }

    private static SavedView savedView(UUID id, UUID ownerId, boolean canManage) {
        return new SavedView(
            id,
            SPACE,
            ownerId,
            "personal",
            "我的交付视图",
            "只保存查询和展示配置",
            "active",
            1,
            1,
            "c".repeat(64),
            query(),
            presentation(),
            List.of(),
            true,
            canManage,
            NOW,
            NOW
        );
    }

    private static QueryDefinition query() {
        return new QueryDefinition(
            1,
            null,
            null,
            List.of(new SortSpec("updatedAt", "desc", "last")),
            null,
            List.of("title"),
            50,
            null
        );
    }

    private static PresentationConfig presentation() {
        return new PresentationConfig(
            1,
            "table",
            "comfortable",
            List.of(new ColumnSpec("title", "标题", 320, true, "text")),
            "parent_child",
            32
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER,
            WORKSPACE,
            UUID.randomUUID(),
            "member",
            "Member",
            Set.of("member"),
            Set.of()
        );
    }

    private record Fixture(
        WorkItemSavedViewService service,
        WorkItemSavedViewRepository repository,
        WorkItemQueryService queries,
        WorkItemViewService views
    ) {
    }
}
