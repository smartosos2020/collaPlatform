package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_CARDS;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_COLUMNS;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_PRESENTATION_PORT_CALLS;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_PROJECTION_CONTAINERS;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_SWIMLANES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardColumn;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardOrder;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreference;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardRequest;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.MoveIntent;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowPresentation;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeAvailableAction;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.AvailableAction;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowPresentation;
import com.colla.platform.modules.project.infrastructure.WorkItemBoardRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemBoardRepository.CommandRecord;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkItemBoardServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_TWO = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID DEVICE = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void freezesDeterministicBoardProjectionBudgets() {
        assertThat(MAX_COLUMNS).isEqualTo(12);
        assertThat(MAX_SWIMLANES).isEqualTo(24);
        assertThat(MAX_CARDS).isEqualTo(100);
        assertThat(MAX_PRESENTATION_PORT_CALLS).isEqualTo(200);
        assertThat(MAX_PROJECTION_CONTAINERS).isEqualTo(388);
    }

    @Test
    void keepsEmptyColumnsAppliesUserOrderAndOnlyUsesPermissionScopedQueryItems() {
        Fixture fixture = fixture();
        QueryItem visible = queryItem(
            ITEM, Map.of("state", "open", "participantRole", List.of("assignee"))
        );
        QueryItem second = queryItem(
            ITEM_TWO, Map.of("state", "open", "participantRole", List.of("assignee"))
        );
        when(fixture.queries.execute(eq(user()), eq(SPACE), any())).thenReturn(
            new QueryResult("a".repeat(64), List.of(visible, second), List.of(), null, 3, true)
        );
        when(fixture.repository.listOrders(eq(WORKSPACE), eq(SPACE), eq(USER), eq("delivery"), any()))
            .thenReturn(List.of(new BoardOrder(
                ITEM, "open", "assignee", 256, 3, 2, NOW
            )));
        when(fixture.workItems.workflow(user(), SPACE, ITEM)).thenReturn(workflow());
        when(fixture.workItems.workflow(user(), SPACE, ITEM_TWO)).thenReturn(workflow());
        when(fixture.workItems.nodeWorkflow(user(), SPACE, ITEM)).thenReturn(node());
        when(fixture.workItems.nodeWorkflow(user(), SPACE, ITEM_TWO)).thenReturn(node());

        var result = fixture.service.render(user(), SPACE, request());

        assertThat(result.columns()).hasSize(2);
        assertThat(result.columns().getFirst().lanes().getFirst().cards()).hasSize(2)
            .first().satisfies(card -> {
                assertThat(card.rank()).isEqualTo(256);
                assertThat(card.orderVersion()).isEqualTo(2);
                assertThat(card.moveActions()).extracting(action -> action.actionKey())
                    .containsExactly("complete", "approve");
            });
        assertThat(result.columns().getFirst().wipExceeded()).isTrue();
        assertThat(result.columns().get(1).visibleCount()).isZero();
        assertThat(result.evaluatedCandidates()).isEqualTo(2);
        assertThat(result.candidateBoundReached()).isFalse();
        verify(fixture.repository).recordRender(WORKSPACE, SPACE, "delivery", 2, 1, 2);
    }

    @Test
    void exactMoveReplayDoesNotExecuteWorkflowTwice() {
        Fixture fixture = fixture();
        BoardPreference preference = preference();
        when(fixture.repository.findPreference(WORKSPACE, SPACE, USER, "delivery"))
            .thenReturn(Optional.of(preference));
        when(fixture.workItems.get(user(), SPACE, ITEM)).thenReturn(view());
        AtomicReference<String> requestHash = new AtomicReference<>();
        AtomicReference<String> response = new AtomicReference<>();
        when(fixture.repository.findCommand(
            eq(WORKSPACE), eq(SPACE), eq(USER), eq("move_state"), eq("move-1")
        )).thenAnswer(invocation -> response.get() == null
            ? Optional.empty()
            : Optional.of(new CommandRecord(
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                requestHash.get(),
                "completed",
                response.get()
            )));
        when(fixture.repository.beginCommand(
            eq(WORKSPACE), eq(SPACE), eq(USER), eq("delivery"), eq(ITEM),
            eq("move_state"), eq("move-1"), anyString(), eq(3L)
        )).thenAnswer(invocation -> {
            requestHash.set(invocation.getArgument(7));
            return new CommandRecord(
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                requestHash.get(),
                "pending",
                null
            );
        });
        when(fixture.repository.reserveOrder(
            WORKSPACE, SPACE, USER, "delivery", ITEM, "done", "assignee", 512, 0, 3
        )).thenReturn(new BoardOrder(ITEM, "done", "assignee", 512, 3, 1, NOW));
        when(fixture.workItems.executeWorkflowAction(
            user(), SPACE, ITEM, "complete", "open", 3, null, "board:move-1"
        )).thenReturn(new WorkflowCommandResult(ITEM, "complete", "open", "done", 4, 2, false));
        when(fixture.repository.alignOrderSourceVersion(
            WORKSPACE, SPACE, USER, "delivery", ITEM, 1, 4
        )).thenReturn(new BoardOrder(ITEM, "done", "assignee", 512, 4, 1, NOW));
        doAnswer(invocation -> {
            response.set(invocation.getArgument(1));
            return null;
        }).when(fixture.repository).completeCommand(any(), anyString());
        MoveIntent intent = move();

        var first = fixture.service.move(user(), SPACE, "delivery", ITEM, intent);
        var replay = fixture.service.move(user(), SPACE, "delivery", ITEM, intent);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.workItemVersion()).isEqualTo(4);
        verify(fixture.workItems).executeWorkflowAction(
            user(), SPACE, ITEM, "complete", "open", 3, null, "board:move-1"
        );
    }

    @Test
    void orderConflictWinsBeforeAnyWorkflowMutation() {
        Fixture fixture = fixture();
        when(fixture.repository.findPreference(WORKSPACE, SPACE, USER, "delivery"))
            .thenReturn(Optional.of(preference()));
        when(fixture.repository.findCommand(any(), any(), any(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(fixture.workItems.get(user(), SPACE, ITEM)).thenReturn(view());
        when(fixture.repository.beginCommand(
            any(), any(), any(), anyString(), any(), anyString(), anyString(), anyString(), anyLong()
        )).thenAnswer(invocation -> new CommandRecord(
            UUID.randomUUID(), invocation.getArgument(7), "pending", null
        ));
        when(fixture.repository.reserveOrder(
            WORKSPACE, SPACE, USER, "delivery", ITEM, "done", "assignee", 512, 0, 3
        )).thenThrow(new WorkItemRuntimeException(
            "BOARD_ORDER_VERSION_CONFLICT", "refresh"
        ));

        assertThatThrownBy(() -> fixture.service.move(user(), SPACE, "delivery", ITEM, move()))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessage("refresh");
        verify(fixture.workItems, never()).executeWorkflowAction(
            any(), any(), any(), anyString(), anyString(), anyLong(), any(), anyString()
        );
    }

    @Test
    void dynamicGroupingFailsClosedBeforeQueryWhenSnapshotDoesNotPublishCapability() {
        Fixture fixture = fixture();
        UUID typeId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        when(fixture.workItems.requireQueryCapability(
            user(), SPACE, typeId, "sprint", "eq", "none"
        )).thenThrow(new WorkItemRuntimeException(
            "QUERY_CAPABILITY_UNAVAILABLE", "not published"
        ));
        BoardRequest request = new BoardRequest(
            1,
            "sprint-board",
            "field.sprint",
            null,
            List.of(new BoardColumn("sprint-a", "Sprint A", 5, "", "")),
            new QueryDefinition(
                1,
                typeId,
                null,
                List.of(new SortSpec("updatedAt", "desc", "last")),
                null,
                List.of("title"),
                50,
                null
            )
        );

        assertThatThrownBy(() -> fixture.service.render(user(), SPACE, request))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessage("not published");
        verify(fixture.queries, never()).execute(any(), any(), any());
    }

    private static Fixture fixture() {
        WorkItemBoardRepository repository = mock(WorkItemBoardRepository.class);
        WorkItemQueryService queries = mock(WorkItemQueryService.class);
        WorkItemService workItems = mock(WorkItemService.class);
        return new Fixture(
            repository,
            queries,
            workItems,
            new WorkItemBoardService(
                repository,
                queries,
                new WorkItemQueryCanonicalizer(),
                workItems,
                JSON,
                new SimpleMeterRegistry()
            )
        );
    }

    private static BoardRequest request() {
        return new BoardRequest(
            1,
            "delivery",
            "state",
            "participantRole",
            List.of(
                new BoardColumn("open", "待处理", 1, "state", "reopen"),
                new BoardColumn("done", "已完成", 1, "state", "complete")
            ),
            query()
        );
    }

    private static BoardPreference preference() {
        return new BoardPreference(
            "delivery",
            "state",
            "participantRole",
            request().columns(),
            1,
            NOW
        );
    }

    private static MoveIntent move() {
        return new MoveIntent(
            "move-1",
            3,
            0,
            "done",
            "assignee",
            512,
            "state",
            "complete",
            "open",
            null,
            null,
            0,
            null,
            null
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

    private static QueryItem queryItem(UUID id, Map<String, Object> selected) {
        return new QueryItem(
            id,
            SPACE,
            UUID.randomUUID(),
            "TASK-1",
            "Visible",
            "active",
            3,
            USER,
            NOW.minusSeconds(60),
            NOW,
            JSON.createObjectNode(),
            selected,
            List.of("view", "transition")
        );
    }

    private static WorkflowPresentation workflow() {
        return new WorkflowPresentation(
            "available",
            "policy-1",
            "open",
            "待处理",
            "active",
            1,
            List.of(new AvailableAction(
                "complete", "完成", "forward", List.of(), 100, "policy-1"
            ))
        );
    }

    private static NodeWorkflowPresentation node() {
        return new NodeWorkflowPresentation(
            "not_configured", "policy-1", null, null, 3, 0,
            List.of(), List.of(), List.of(new NodeAvailableAction(
                "approve",
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                "review",
                "allowed",
                3,
                2,
                "policy-1"
            ))
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER, WORKSPACE, DEVICE, "member", "Member",
            Set.of("member"), Set.of()
        );
    }

    private static WorkItemView view() {
        return new WorkItemView(item(), JSON.createObjectNode(), JSON.createObjectNode(), List.of());
    }

    private static WorkItem item() {
        return new WorkItem(
            ITEM, WORKSPACE, SPACE, UUID.randomUUID(), UUID.randomUUID(),
            "task", "Task", "a".repeat(64), 1, "TASK-1", "Visible",
            JSON.createObjectNode(), "active", 3, USER, NOW.minusSeconds(60),
            USER, NOW, null
        );
    }

    private record Fixture(
        WorkItemBoardRepository repository,
        WorkItemQueryService queries,
        WorkItemService workItems,
        WorkItemBoardService service
    ) {
    }
}
