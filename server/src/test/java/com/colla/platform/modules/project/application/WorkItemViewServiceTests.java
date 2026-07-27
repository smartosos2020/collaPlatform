package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.modules.project.domain.WorkItemViewModels.BulkCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.BulkSelection;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ColumnSpec;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewMode;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewRequest;
import com.colla.platform.modules.project.infrastructure.WorkItemViewRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemViewServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void omitsDeniedDynamicCellsAndKeepsServerCapabilities() {
        WorkItemQueryService queries = mock(WorkItemQueryService.class);
        WorkItemService workItems = mock(WorkItemService.class);
        WorkItemViewRepository repository = mock(WorkItemViewRepository.class);
        QueryItem item = new QueryItem(
            ITEM,
            SPACE,
            UUID.randomUUID(),
            "TASK-1",
            "Visible",
            "active",
            3,
            USER,
            NOW.minusSeconds(10),
            NOW,
            JSON.createObjectNode(),
            Map.of("title", "Visible"),
            List.of("view", "archive")
        );
        when(queries.execute(any(CurrentUser.class), eq(SPACE), any())).thenReturn(
            new QueryResult("a".repeat(64), List.of(item), List.of(), null, 1, false)
        );
        WorkItemViewService service = new WorkItemViewService(
            queries, workItems, repository, JSON
        );

        var result = service.render(
            user(),
            SPACE,
            new ViewRequest(
                1,
                ViewMode.table,
                "compact",
                List.of(
                    new ColumnSpec("title", "标题", 320, true, "text"),
                    new ColumnSpec("field.secret", "受限字段", 180, false, "text")
                ),
                query()
            )
        );

        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.cells()).extracting(value -> value.columnKey())
                .containsExactly("title");
            assertThat(row.availableActions()).containsExactly("view", "archive");
        });
    }

    @Test
    void bulkActionReportsPerObjectFailureWithoutLeakingObjectContent() {
        WorkItemQueryService queries = mock(WorkItemQueryService.class);
        WorkItemService workItems = mock(WorkItemService.class);
        WorkItemViewRepository repository = mock(WorkItemViewRepository.class);
        UUID denied = UUID.fromString("40000000-0000-0000-0000-000000000002");
        WorkItem item = item();
        when(workItems.transition(any(CurrentUser.class), eq(SPACE), eq(ITEM), eq("archived"), eq(3L), any()))
            .thenReturn(new WorkItemView(item, JSON.createObjectNode(), JSON.createObjectNode(), List.of()));
        when(workItems.transition(any(CurrentUser.class), eq(SPACE), eq(denied), eq("archived"), eq(4L), any()))
            .thenThrow(new WorkItemRuntimeException("FORBIDDEN", "secret object title"));
        WorkItemViewService service = new WorkItemViewService(
            queries, workItems, repository, JSON
        );

        var result = service.bulk(
            user(),
            SPACE,
            new BulkCommand(
                "bulk-request",
                "archive",
                List.of(new BulkSelection(ITEM, 3), new BulkSelection(denied, 4))
            )
        );

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items().get(1).reasonCode()).isEqualTo("FORBIDDEN");
        assertThat(result.toString()).doesNotContain("secret object title");
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

    private static WorkItem item() {
        return new WorkItem(
            ITEM,
            WORKSPACE,
            SPACE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "task",
            "Task",
            "a".repeat(64),
            1,
            "TASK-1",
            "Visible",
            JSON.createObjectNode(),
            "archived",
            4,
            USER,
            NOW.minusSeconds(10),
            USER,
            NOW,
            NOW
        );
    }
}
