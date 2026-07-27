package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemCalendarModels.MAX_EVENTS;
import static com.colla.platform.modules.project.domain.WorkItemCalendarModels.MAX_OVERLAP_LANES;
import static com.colla.platform.modules.project.domain.WorkItemCalendarModels.MAX_PROJECTION_CONTAINERS;
import static com.colla.platform.modules.project.domain.WorkItemCalendarModels.MAX_WINDOW_DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreference;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarRequest;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateBinding;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutation;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.RangeWindow;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.modules.project.infrastructure.WorkItemCalendarRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemCalendarRepository.CommandRecord;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkItemCalendarServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID TYPE = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void freezesCalendarBudgets() {
        assertThat(MAX_WINDOW_DAYS).isEqualTo(62);
        assertThat(MAX_EVENTS).isEqualTo(100);
        assertThat(MAX_OVERLAP_LANES).isEqualTo(8);
        assertThat(MAX_PROJECTION_CONTAINERS).isEqualTo(163);
    }

    @Test
    void rendersPermissionScopedAllDayRangeAcrossDstWithoutChangingStoredDates() {
        Fixture fixture = fixture();
        when(fixture.workItems.requireQueryCapability(
            user(), SPACE, TYPE, "start_date", "between", "none"
        )).thenReturn(configuration("date", "start_date", "end_date"));
        when(fixture.workItems.requireQueryCapability(
            user(), SPACE, TYPE, "end_date", "between", "none"
        )).thenReturn(configuration("date", "start_date", "end_date"));
        QueryItem visible = new QueryItem(
            ITEM, SPACE, TYPE, "TASK-1", "DST range", "active", 3, USER, NOW, NOW,
            JSON.createObjectNode(),
            Map.of("field.start_date", "2026-03-07", "field.end_date", "2026-03-09"),
            List.of("view", "edit")
        );
        when(fixture.queries.execute(eq(user()), eq(SPACE), any())).thenReturn(
            new QueryResult("a".repeat(64), List.of(visible), List.of(), null, 9, true)
        );

        var result = fixture.service.render(user(), SPACE, request());

        assertThat(result.visibleEventCount()).isOne();
        assertThat(result.candidateBoundReached()).isFalse();
        var event = result.days().stream()
            .filter(day -> day.date().equals(LocalDate.parse("2026-03-07")))
            .findFirst().orElseThrow().events().getFirst();
        assertThat(event.startValue()).isEqualTo("2026-03-07");
        assertThat(event.endValue()).isEqualTo("2026-03-09");
        assertThat(event.endInstant().toEpochMilli() - event.startInstant().toEpochMilli())
            .isEqualTo(71L * 60 * 60 * 1000);
        assertThat(result.days()).hasSize(7);
        verify(fixture.repository).recordRender(WORKSPACE, SPACE, "delivery", 7, 1, 1);
    }

    @Test
    void rejectsOversizedWindowBeforeQuery() {
        Fixture fixture = fixture();
        CalendarRequest request = new CalendarRequest(
            1,
            "delivery",
            new DateBinding("start_date", "end_date", true),
            new RangeWindow(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-04-01"),
                "Asia/Shanghai",
                "month"
            ),
            query()
        );

        assertThatThrownBy(() -> fixture.service.render(user(), SPACE, request))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("62 days");
    }

    @Test
    void exactDateReplayDoesNotUpdateWorkItemTwice() throws Exception {
        Fixture fixture = fixture();
        CalendarPreference preference = new CalendarPreference(
            "delivery",
            new DateBinding("start_date", "end_date", true),
            "Asia/Shanghai",
            "month",
            1,
            NOW
        );
        when(fixture.repository.findPreference(WORKSPACE, SPACE, USER, "delivery"))
            .thenReturn(Optional.of(preference));
        when(fixture.workItems.get(user(), SPACE, ITEM)).thenReturn(view(3, "2026-03-07", "2026-03-09"));
        when(fixture.workItems.requireQueryCapability(
            user(), SPACE, TYPE, "start_date", "between", "none"
        )).thenReturn(configuration("date", "start_date", "end_date"));
        when(fixture.workItems.requireQueryCapability(
            user(), SPACE, TYPE, "end_date", "between", "none"
        )).thenReturn(configuration("date", "start_date", "end_date"));
        AtomicReference<String> hash = new AtomicReference<>();
        AtomicReference<String> response = new AtomicReference<>();
        when(fixture.repository.findCommand(
            WORKSPACE, SPACE, USER, "move_date", "calendar-move-1"
        )).thenAnswer(invocation -> response.get() == null
            ? Optional.empty()
            : Optional.of(new CommandRecord(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                hash.get(), "completed", response.get()
            )));
        when(fixture.repository.beginCommand(
            eq(WORKSPACE), eq(SPACE), eq(USER), eq("delivery"), eq(ITEM),
            eq("move_date"), eq("calendar-move-1"), anyString(), eq(3L)
        )).thenAnswer(invocation -> {
            hash.set(invocation.getArgument(7));
            return new CommandRecord(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                hash.get(), "pending", null
            );
        });
        when(fixture.workItems.update(
            eq(user()), eq(SPACE), eq(ITEM), eq(null), any(), eq(3L), eq("calendar:calendar-move-1")
        )).thenReturn(view(4, "2026-03-10", "2026-03-12"));
        org.mockito.Mockito.doAnswer(invocation -> {
            response.set(invocation.getArgument(1));
            return null;
        }).when(fixture.repository).completeCommand(any(), anyString());
        DateMutation mutation = new DateMutation(
            "calendar-move-1", 3, "move", "2026-03-10", "2026-03-12", "Asia/Shanghai"
        );

        var first = fixture.service.mutateDate(user(), SPACE, "delivery", ITEM, mutation);
        var replay = fixture.service.mutateDate(user(), SPACE, "delivery", ITEM, mutation);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.workItemVersion()).isEqualTo(4);
        verify(fixture.workItems).update(
            eq(user()), eq(SPACE), eq(ITEM), eq(null), any(), eq(3L), eq("calendar:calendar-move-1")
        );
    }

    private static Fixture fixture() {
        WorkItemCalendarRepository repository = mock(WorkItemCalendarRepository.class);
        WorkItemQueryService queries = mock(WorkItemQueryService.class);
        WorkItemService workItems = mock(WorkItemService.class);
        return new Fixture(
            repository,
            queries,
            workItems,
            new WorkItemCalendarService(
                repository,
                queries,
                new WorkItemQueryCanonicalizer(),
                workItems,
                JSON,
                new SimpleMeterRegistry()
            )
        );
    }

    private static CalendarRequest request() {
        return new CalendarRequest(
            1,
            "delivery",
            new DateBinding("start_date", "end_date", true),
            new RangeWindow(
                LocalDate.parse("2026-03-06"),
                LocalDate.parse("2026-03-12"),
                "America/New_York",
                "week"
            ),
            query()
        );
    }

    private static QueryDefinition query() {
        return new QueryDefinition(
            1,
            TYPE,
            null,
            List.of(new SortSpec("updatedAt", "desc", "last")),
            null,
            List.of("title"),
            100,
            null
        );
    }

    private static RuntimeConfiguration configuration(
        String type,
        String... fields
    ) {
        ObjectNode snapshot = JSON.createObjectNode();
        var array = snapshot.putArray("fields");
        for (String field : fields) {
            array.addObject()
                .put("fieldKey", field)
                .put("fieldType", type)
                .put("status", "active");
        }
        return new RuntimeConfiguration(
            UUID.randomUUID(), TYPE, 1, 5, "a".repeat(64), snapshot
        );
    }

    private static WorkItemView view(long version, String start, String end) {
        ObjectNode values = JSON.createObjectNode();
        values.put("start_date", start);
        values.put("end_date", end);
        WorkItem item = new WorkItem(
            ITEM, WORKSPACE, SPACE, TYPE, UUID.randomUUID(),
            "task", "Task", "a".repeat(64), 1, "TASK-1", "Calendar item",
            values, "active", version, USER, NOW, USER, NOW, null
        );
        return new WorkItemView(item, values, JSON.createObjectNode(), List.of("view", "edit"));
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER, WORKSPACE, DEVICE, "member", "Member", Set.of("member"), Set.of()
        );
    }

    private record Fixture(
        WorkItemCalendarRepository repository,
        WorkItemQueryService queries,
        WorkItemService workItems,
        WorkItemCalendarService service
    ) {
    }
}
