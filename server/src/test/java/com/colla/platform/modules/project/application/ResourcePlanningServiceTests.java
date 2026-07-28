package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.CalendarException;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.Estimate;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.PlanningFoundation;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.SaveCalendarCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourcePlanningRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourcePlanningServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID VISIBLE_ITEM = UUID.randomUUID();
    private static final UUID HIDDEN_ITEM = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void derivesTimeScheduleWithoutConvertingPointsAndHidesRevokedItems() {
        Fixture fixture = fixture("owner");
        when(fixture.repository.findCalendar(WORKSPACE, SPACE))
            .thenReturn(Optional.of(new com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar(
                UUID.randomUUID(), "Asia/Shanghai", List.of(1, 2, 3, 4, 5), 480,
                List.of(new CalendarException(
                    UUID.randomUUID(), LocalDate.parse("2026-07-29"), 240, "half day"
                )), 2, USER, NOW
            )));
        when(fixture.repository.listEstimates(WORKSPACE, SPACE, 200))
            .thenReturn(List.of(
                estimate(VISIBLE_ITEM, "hour", "12"),
                estimate(HIDDEN_ITEM, "point", "8")
            ));
        when(fixture.workItems.get(any(), eq(SPACE), eq(VISIBLE_ITEM)))
            .thenReturn(view(VISIBLE_ITEM));
        when(fixture.workItems.get(any(), eq(SPACE), eq(HIDDEN_ITEM)))
            .thenThrow(new WorkItemRuntimeException("FORBIDDEN", "revoked"));

        PlanningFoundation result = fixture.service.get(user(), SPACE);

        assertThat(result.estimates()).extracting(Estimate::workItemId)
            .containsExactly(VISIBLE_ITEM);
        assertThat(result.schedule()).singleElement().satisfies(value -> {
            assertThat(value.timeComparable()).isTrue();
            assertThat(value.requiredMinutes()).isEqualTo(720);
            assertThat(value.projectedStart()).isEqualTo(LocalDate.parse("2026-07-28"));
            assertThat(value.projectedFinish()).isEqualTo(LocalDate.parse("2026-07-29"));
        });
    }

    @Test
    void defaultCalendarIsBoundedAndGuestCannotMutate() {
        Fixture fixture = fixture("guest");
        when(fixture.repository.findCalendar(WORKSPACE, SPACE))
            .thenReturn(Optional.empty());
        when(fixture.repository.listEstimates(WORKSPACE, SPACE, 200))
            .thenReturn(List.of());

        PlanningFoundation result = fixture.service.get(user(), SPACE);

        assertThat(result.calendar().timezone()).isEqualTo("UTC");
        assertThat(result.calendar().version()).isZero();
        assertThatThrownBy(() -> fixture.service.saveCalendar(
            user(), SPACE, new SaveCalendarCommand(
                1, "calendar-save", 0, "UTC", List.of(1, 2, 3, 4, 5),
                480, List.of()
            )
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("read-only");
    }

    @Test
    void rejectsInvalidTimezoneAndDuplicateExceptionDates() {
        Fixture fixture = fixture("owner");
        UUID first = UUID.randomUUID();
        SaveCalendarCommand command = new SaveCalendarCommand(
            1, "calendar-save", 0, "Mars/Olympus", List.of(1, 2, 3, 4, 5),
            480, List.of(
                new com.colla.platform.modules.project.domain.ResourcePlanningModels.CalendarExceptionInput(
                    first, LocalDate.parse("2026-07-30"), 0, "holiday"
                ),
                new com.colla.platform.modules.project.domain.ResourcePlanningModels.CalendarExceptionInput(
                    UUID.randomUUID(), LocalDate.parse("2026-07-30"), 240, "duplicate"
                )
            )
        );

        assertThatThrownBy(() -> fixture.service.saveCalendar(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");
    }

    private static Fixture fixture(String role) {
        ResourcePlanningRepository repository = mock(ResourcePlanningRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        WorkItemService workItems = mock(WorkItemService.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        ResourcePlanningService service = new ResourcePlanningService(
            repository, spaces, workItems, mock(AuditLog.class),
            mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(repository, workItems, service);
    }

    private static Estimate estimate(UUID itemId, String unit, String amount) {
        return new Estimate(
            UUID.randomUUID(), itemId, unit, new BigDecimal(amount), 3, 2, USER, NOW
        );
    }

    private static WorkItemView view(UUID id) {
        WorkItem item = new WorkItem(
            id, WORKSPACE, SPACE, UUID.randomUUID(), UUID.randomUUID(),
            "task", "Task", "hash", 1, "DELIVERY-1", "Visible",
            new ObjectMapper().createObjectNode(), "active", 3, USER, NOW, USER, NOW, null
        );
        return new WorkItemView(
            item, item.fieldValues(), new ObjectMapper().createObjectNode(), List.of()
        );
    }

    private static ProjectSpaceSummary space(String role) {
        return new ProjectSpaceSummary(
            SPACE, WORKSPACE, "DELIVERY", "Delivery", "", "active", "private",
            1, role, 1, USER, NOW, USER, NOW, null, null
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER, WORKSPACE, UUID.randomUUID(), "owner", "Owner",
            Set.of("owner"), Set.of()
        );
    }

    private record Fixture(
        ResourcePlanningRepository repository,
        WorkItemService workItems,
        ResourcePlanningService service
    ) {
    }
}
