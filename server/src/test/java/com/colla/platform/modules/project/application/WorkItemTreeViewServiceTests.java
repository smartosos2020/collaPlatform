package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider;
import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider.AncestorRef;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeRequest;
import com.colla.platform.modules.project.infrastructure.WorkItemTreePreferenceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemTreeViewServiceTests {
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ROOT = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID HIDDEN_PARENT = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID CHILD = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void flattensHiddenParentWithoutLeakingBreakOrHiddenChildCount() {
        WorkItemQueryService queries = mock(WorkItemQueryService.class);
        WorkItemService workItems = mock(WorkItemService.class);
        WorkItemHierarchyProjectionProvider hierarchy = mock(WorkItemHierarchyProjectionProvider.class);
        WorkItemTreePreferenceRepository preferences = mock(WorkItemTreePreferenceRepository.class);
        when(queries.execute(any(), eq(SPACE), any())).thenReturn(new QueryResult(
            "a".repeat(64),
            List.of(item(ROOT, "Root"), item(CHILD, "Child")),
            List.of(),
            null,
            3,
            false
        ));
        when(hierarchy.ancestors(eq(WORKSPACE), eq(SPACE), eq("parent_child"), any()))
            .thenReturn(Map.of(
                CHILD,
                List.of(new AncestorRef(HIDDEN_PARENT, 1), new AncestorRef(ROOT, 2))
            ));
        WorkItemTreeViewService service = service(queries, workItems, hierarchy, preferences);

        var roots = service.render(user(), SPACE, request(null));
        var children = service.render(user(), SPACE, request(ROOT));

        assertThat(roots.items()).singleElement().satisfies(root -> {
            assertThat(root.id()).isEqualTo(ROOT);
            assertThat(root.visibleChildCount()).isEqualTo(1);
            assertThat(root.expandable()).isTrue();
        });
        assertThat(children.items()).singleElement().satisfies(child -> {
            assertThat(child.id()).isEqualTo(CHILD);
            assertThat(child.parentId()).isEqualTo(ROOT);
            assertThat(child.depth()).isEqualTo(1);
            assertThat(child.visibleChildCount()).isZero();
        });
        assertThat(children.toString()).doesNotContain(HIDDEN_PARENT.toString());
    }

    @Test
    void failsClosedWhenCanonicalProjectionContainsCycle() {
        WorkItemQueryService queries = mock(WorkItemQueryService.class);
        WorkItemService workItems = mock(WorkItemService.class);
        WorkItemHierarchyProjectionProvider hierarchy = mock(WorkItemHierarchyProjectionProvider.class);
        WorkItemTreePreferenceRepository preferences = mock(WorkItemTreePreferenceRepository.class);
        when(queries.execute(any(), eq(SPACE), any())).thenReturn(new QueryResult(
            "b".repeat(64), List.of(item(ROOT, "Root")), List.of(), null, 1, false
        ));
        when(hierarchy.ancestors(eq(WORKSPACE), eq(SPACE), eq("parent_child"), any()))
            .thenReturn(Map.of(ROOT, List.of(new AncestorRef(ROOT, 1))));

        assertThatThrownBy(() -> service(queries, workItems, hierarchy, preferences)
            .render(user(), SPACE, request(null)))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");
    }

    private static WorkItemTreeViewService service(
        WorkItemQueryService queries,
        WorkItemService workItems,
        WorkItemHierarchyProjectionProvider hierarchy,
        WorkItemTreePreferenceRepository preferences
    ) {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setAccessSecret("tree-view-test-secret-with-sufficient-entropy");
        return new WorkItemTreeViewService(
            queries,
            workItems,
            hierarchy,
            new WorkItemQueryCursorCodec(properties),
            preferences
        );
    }

    private static TreeRequest request(UUID parentId) {
        return new TreeRequest(1, "parent_child", query(), parentId, 50, 32, null);
    }

    private static QueryDefinition query() {
        return new QueryDefinition(
            1, null, null,
            List.of(new SortSpec("updatedAt", "desc", "last")),
            null, List.of("displayKey", "title", "status"), 100, null
        );
    }

    private static QueryItem item(UUID id, String title) {
        return new QueryItem(
            id, SPACE, UUID.randomUUID(), "TASK-" + id.toString().substring(30),
            title, "active", 1, user().id(), Instant.parse("2026-07-27T00:00:00Z"),
            Instant.parse("2026-07-27T00:00:01Z"), JSON.createObjectNode(),
            Map.of("title", title), List.of("view", "archive")
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            WORKSPACE,
            UUID.randomUUID(),
            "member",
            "Member",
            Set.of("member"),
            Set.of()
        );
    }
}
