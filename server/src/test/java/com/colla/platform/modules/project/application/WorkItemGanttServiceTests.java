package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.contract.WorkItemDependencyProjectionProvider;
import com.colla.platform.modules.project.contract.WorkItemDependencyProjectionProvider.DependencyEdge;
import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarDay;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarEvent;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarResult;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateBinding;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.RangeWindow;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttRequest;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemGanttRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemGanttServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID EDGE = UUID.randomUUID();

    @Test
    void derivesCriticalPathOnlyFromVisibleScheduleAndPublicDependencyProjection() {
        Fixture fixture = fixture(List.of(new DependencyEdge(
            EDGE, "depends_on", SECOND, FIRST, 1
        )));

        var result = fixture.service.render(user(), SPACE, request());

        assertThat(result.rows()).hasSize(2);
        assertThat(result.dependencies()).singleElement()
            .satisfies(line -> assertThat(line.critical()).isTrue());
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.bar().critical()).isTrue());
        assertThat(result.criticalPathAvailable()).isTrue();
        assertThat(result.criticalPathReason()).isEqualTo("ok");
        verify(fixture.dependencies).edges(WORKSPACE, SPACE, List.of(FIRST, SECOND), 200);
        verify(fixture.repository).recordRender(WORKSPACE, SPACE, "delivery", 2, 1, 0);
    }

    @Test
    void degradesExplicitlyWhenVisibleDependencyProjectionContainsCycle() {
        Fixture fixture = fixture(List.of(
            new DependencyEdge(EDGE, "depends_on", SECOND, FIRST, 1),
            new DependencyEdge(UUID.randomUUID(), "depends_on", FIRST, SECOND, 1)
        ));

        var result = fixture.service.render(user(), SPACE, request());

        assertThat(result.criticalPathAvailable()).isFalse();
        assertThat(result.criticalPathReason()).isEqualTo("relation_cycle");
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.bar().critical()).isFalse());
    }

    @Test
    void freezesGanttBudgets() {
        assertThat(com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_ROWS)
            .isEqualTo(100);
        assertThat(com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_DEPENDENCIES)
            .isEqualTo(200);
        assertThat(com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_DEPTH)
            .isEqualTo(32);
        assertThat(com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_EXPANDED)
            .isEqualTo(64);
    }

    private static Fixture fixture(List<DependencyEdge> edges) {
        WorkItemCalendarService calendars = mock(WorkItemCalendarService.class);
        WorkItemService workItems = mock(WorkItemService.class);
        WorkItemHierarchyProjectionProvider hierarchy =
            mock(WorkItemHierarchyProjectionProvider.class);
        WorkItemDependencyProjectionProvider dependencies =
            mock(WorkItemDependencyProjectionProvider.class);
        WorkItemGanttRepository repository = mock(WorkItemGanttRepository.class);
        when(calendars.render(eq(user()), eq(SPACE), any())).thenReturn(calendar());
        when(hierarchy.ancestors(WORKSPACE, SPACE, "parent_child", List.of(FIRST, SECOND)))
            .thenReturn(Map.of());
        when(dependencies.edges(WORKSPACE, SPACE, List.of(FIRST, SECOND), 200))
            .thenReturn(edges);
        when(repository.findPreference(WORKSPACE, SPACE, USER, "delivery"))
            .thenReturn(Optional.empty());
        return new Fixture(
            dependencies,
            repository,
            new WorkItemGanttService(
                calendars, workItems, hierarchy, dependencies, repository
            )
        );
    }

    private static CalendarResult calendar() {
        LocalDate firstDate = LocalDate.parse("2026-07-01");
        LocalDate secondDate = LocalDate.parse("2026-07-03");
        CalendarEvent first = event(FIRST, "TASK-1", "First", firstDate, firstDate, 1);
        CalendarEvent second = event(SECOND, "TASK-2", "Second", secondDate, secondDate, 2);
        return new CalendarResult(
            1,
            "gantt-delivery",
            "a".repeat(64),
            new DateBinding("start_date", "end_date", true),
            new RangeWindow(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                "Asia/Shanghai",
                "month"
            ),
            List.of(
                new CalendarDay(firstDate, List.of(first)),
                new CalendarDay(secondDate, List.of(second))
            ),
            List.of(),
            2,
            null,
            false
        );
    }

    private static CalendarEvent event(
        UUID id, String key, String title, LocalDate start, LocalDate end, long version
    ) {
        return new CalendarEvent(
            id,
            key,
            title,
            version,
            start.toString(),
            end.toString(),
            start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            end.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            start,
            end,
            true,
            0,
            List.of("view", "edit")
        );
    }

    private static GanttRequest request() {
        return new GanttRequest(
            1,
            "delivery",
            new DateBinding("start_date", "end_date", true),
            new RangeWindow(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                "Asia/Shanghai",
                "month"
            ),
            new QueryDefinition(1, UUID.randomUUID(), null, List.of(), null, List.of(), 100, null),
            "parent_child",
            List.of(),
            true
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER, WORKSPACE, DEVICE, "member", "Member", Set.of("member"), Set.of()
        );
    }

    private record Fixture(
        WorkItemDependencyProjectionProvider dependencies,
        WorkItemGanttRepository repository,
        WorkItemGanttService service
    ) {
    }
}
