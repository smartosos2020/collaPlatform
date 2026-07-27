package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditTimelineQuery;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateBinding;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.RangeWindow;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttRequest;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttResult;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.HierarchyRow;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.ScheduleBar;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineEntry;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSnapshot;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSummary;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineEvent;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineRequest;
import com.colla.platform.modules.project.infrastructure.WorkItemScheduleRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemScheduleServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID HIDDEN = UUID.randomUUID();
    private static final UUID BASELINE = UUID.randomUUID();

    @Test
    void compareRecalibratesBaselineEntriesAgainstCurrentVisibleRows() {
        Fixture fixture = fixture();
        when(fixture.repository.findBaseline(WORKSPACE, SPACE, USER, BASELINE))
            .thenReturn(Optional.of(new BaselineSnapshot(
                summary(),
                List.of(
                    new BaselineEntry(ITEM, 1, date("2026-07-01"), date("2026-07-02"), null, 0),
                    new BaselineEntry(HIDDEN, 1, date("2026-07-01"), date("2026-07-02"), null, 0)
                ),
                List.of()
            )));

        var result = fixture.service.compare(user(), SPACE, BASELINE, request());

        assertThat(result.entries()).singleElement()
            .satisfies(diff -> {
                assertThat(diff.workItemId()).isEqualTo(ITEM);
                assertThat(diff.change()).isEqualTo("changed");
            });
        assertThat(result.entries()).noneMatch(diff -> diff.workItemId().equals(HIDDEN));
    }

    @Test
    void timelineUsesOnlyCurrentVisibleIdentitiesAndFreezesBudgets() {
        Fixture fixture = fixture();
        TimelineEvent event = new TimelineEvent(
            UUID.randomUUID(), "activity", UUID.randomUUID(), ITEM,
            "work_item.updated", USER, Instant.parse("2026-07-01T00:00:00Z")
        );
        when(fixture.repository.timeline(WORKSPACE, SPACE, List.of(ITEM), 21))
            .thenReturn(List.of(event));

        var result = fixture.service.timeline(
            user(), SPACE, new TimelineRequest(1, request(), 20)
        );

        assertThat(result.events()).containsExactly(event);
        assertThat(com.colla.platform.modules.project.domain.WorkItemScheduleModels.MAX_BASELINES)
            .isEqualTo(20);
        assertThat(com.colla.platform.modules.project.domain.WorkItemScheduleModels.MAX_BASELINE_ENTRIES)
            .isEqualTo(100);
        assertThat(com.colla.platform.modules.project.domain.WorkItemScheduleModels.MAX_TIMELINE_EVENTS)
            .isEqualTo(200);
        assertThat(com.colla.platform.modules.project.domain.WorkItemScheduleModels.RETENTION_DAYS)
            .isEqualTo(90);
    }

    private static Fixture fixture() {
        WorkItemService workItems = mock(WorkItemService.class);
        WorkItemGanttService gantts = mock(WorkItemGanttService.class);
        WorkItemScheduleRepository repository = mock(WorkItemScheduleRepository.class);
        AuditTimelineQuery auditTimeline = mock(AuditTimelineQuery.class);
        when(auditTimeline.workItemEntries(any(), any(), any(Integer.class)))
            .thenReturn(List.of());
        when(gantts.render(any(CurrentUser.class), eq(SPACE), any())).thenReturn(gantt());
        return new Fixture(
            repository,
            new WorkItemScheduleService(
                workItems,
                gantts,
                repository,
                auditTimeline,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
            )
        );
    }

    private static GanttResult gantt() {
        ScheduleBar bar = new ScheduleBar(
            ITEM, "TASK-1", "Visible", 2,
            date("2026-07-03"), date("2026-07-04"), true, true, 0, List.of()
        );
        return new GanttResult(
            1, "delivery", "a".repeat(64),
            new DateBinding("start_date", "end_date", true),
            new RangeWindow(date("2026-07-01"), date("2026-07-31"), "UTC", "month"),
            List.of(new HierarchyRow(ITEM, null, 0, false, false, bar)),
            List.of(), true, "ok", false
        );
    }

    private static GanttRequest request() {
        return new GanttRequest(
            1, "delivery",
            new DateBinding("start_date", "end_date", true),
            new RangeWindow(date("2026-07-01"), date("2026-07-31"), "UTC", "month"),
            new QueryDefinition(1, null, null, List.of(), null, List.of(), 50, null),
            "parent_child", List.of(), true
        );
    }

    private static BaselineSummary summary() {
        return new BaselineSummary(
            BASELINE, "Release", "a".repeat(64),
            date("2026-07-01"), date("2026-07-31"), 1, "active",
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-09-29T00:00:00Z")
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER, WORKSPACE, UUID.randomUUID(), "owner", "Owner",
            java.util.Set.of("owner"), java.util.Set.of()
        );
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    private record Fixture(
        WorkItemScheduleRepository repository,
        WorkItemScheduleService service
    ) {
    }
}
