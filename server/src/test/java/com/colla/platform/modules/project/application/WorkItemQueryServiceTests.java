package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.platform.contract.PlatformSearchProjectionProvider;
import com.colla.platform.modules.project.contract.WorkItemQueryContextProvider;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.AggregateSpec;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.GroupSpec;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemQueryServiceTests {
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VISIBLE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID HIDDEN = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void removesHiddenCandidatesBeforeGroupsCountsItemsAndCursor() {
        WorkItemRepository repository = mock(WorkItemRepository.class);
        WorkItemService workItems = mock(WorkItemService.class);
        PlatformSearchProjectionProvider permissions = mock(PlatformSearchProjectionProvider.class);
        WorkItemQueryContextProvider contexts = mock(WorkItemQueryContextProvider.class);
        CurrentUser user = user("member");
        WorkItem visible = item(VISIBLE, "Visible");
        WorkItem hidden = item(HIDDEN, "Hidden");
        when(repository.list(WORKSPACE, SPACE, null, null, 201)).thenReturn(List.of(visible, hidden));
        when(permissions.objectType()).thenReturn("work_item");
        when(permissions.allowed(eq(user), anyList(), anySet())).thenReturn(Set.of(VISIBLE));
        when(workItems.get(user, SPACE, VISIBLE)).thenReturn(view(visible));
        when(contexts.load(eq(WORKSPACE), eq(SPACE), eq(user.id()), anyList())).thenReturn(Map.of());
        WorkItemQueryService service = service(repository, workItems, permissions, contexts);

        var result = service.execute(user, SPACE, query());

        assertThat(result.items()).singleElement()
            .extracting(value -> value.id())
            .isEqualTo(VISIBLE);
        assertThat(result.groups()).singleElement()
            .satisfies(group -> {
                assertThat(group.key()).isEqualTo("active");
                assertThat(group.count()).isEqualTo(1);
            });
        assertThat(result.nextCursor()).isNull();
        verify(workItems, never()).get(user, SPACE, HIDDEN);
    }

    @Test
    void sixIdentityMatrixNeverTreatsEnterpriseAdministrationAsContentAccess() {
        for (String identity : List.of(
            "owner", "space-admin", "member", "guest", "non-member", "enterprise-admin"
        )) {
            WorkItemRepository repository = mock(WorkItemRepository.class);
            WorkItemService workItems = mock(WorkItemService.class);
            PlatformSearchProjectionProvider permissions = mock(PlatformSearchProjectionProvider.class);
            WorkItemQueryContextProvider contexts = mock(WorkItemQueryContextProvider.class);
            CurrentUser actor = user(identity);
            WorkItem candidate = item(VISIBLE, "Visible");
            when(repository.list(WORKSPACE, SPACE, null, null, 201)).thenReturn(List.of(candidate));
            when(permissions.objectType()).thenReturn("work_item");
            boolean contentMember = Set.of("owner", "space-admin", "member", "guest").contains(identity);
            when(permissions.allowed(eq(actor), anyList(), anySet()))
                .thenReturn(contentMember ? Set.of(VISIBLE) : Set.of());
            if (contentMember) when(workItems.get(actor, SPACE, VISIBLE)).thenReturn(view(candidate));
            when(contexts.load(eq(WORKSPACE), eq(SPACE), eq(actor.id()), anyList())).thenReturn(Map.of());

            var result = service(repository, workItems, permissions, contexts)
                .execute(actor, SPACE, query());

            assertThat(result.items()).hasSize(contentMember ? 1 : 0);
            assertThat(result.groups()).hasSize(contentMember ? 1 : 0);
            if (!contentMember) verify(workItems, never()).get(any(), any(), any());
        }
    }

    private static WorkItemQueryService service(
        WorkItemRepository repository,
        WorkItemService workItems,
        PlatformSearchProjectionProvider permissions,
        WorkItemQueryContextProvider contexts
    ) {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setAccessSecret("work-item-query-test-secret-with-entropy");
        return new WorkItemQueryService(
            repository,
            workItems,
            List.of(permissions),
            contexts,
            new WorkItemQueryCanonicalizer(),
            new WorkItemQueryCursorCodec(properties),
            new SimpleMeterRegistry()
        );
    }

    private static QueryDefinition query() {
        return new QueryDefinition(
            1,
            null,
            null,
            List.of(new SortSpec("updatedAt", "desc", "last")),
            new GroupSpec("status", List.of(new AggregateSpec("count", null, "count"))),
            List.of("id", "title", "status"),
            50,
            null
        );
    }

    private static CurrentUser user(String identity) {
        UUID id = UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Set<String> roles = "enterprise-admin".equals(identity) ? Set.of("admin") : Set.of(identity);
        return new CurrentUser(id, WORKSPACE, UUID.randomUUID(), identity, identity, roles, Set.of());
    }

    private static WorkItem item(UUID id, String title) {
        return new WorkItem(
            id,
            WORKSPACE,
            SPACE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "task",
            "Task",
            "a".repeat(64),
            1,
            "TASK-1",
            title,
            JSON.createObjectNode(),
            "active",
            1,
            UUID.randomUUID(),
            NOW.minusSeconds(60),
            UUID.randomUUID(),
            NOW,
            null
        );
    }

    private static WorkItemView view(WorkItem item) {
        return new WorkItemView(
            item,
            JSON.createObjectNode(),
            JSON.createObjectNode(),
            List.of("view")
        );
    }
}
