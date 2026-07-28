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
import com.colla.platform.modules.project.domain.ResourcePlanningModels.Estimate;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.MutateWorklogCommand;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Worklog;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourcePlanningRepository;
import com.colla.platform.modules.project.infrastructure.ResourceWorklogRepository;
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

class ResourceWorklogServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID HIDDEN = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void recalibratesVisibilityAndDerivesSubmittedVariance() {
        Fixture fixture = fixture("owner");
        when(fixture.repository.list(WORKSPACE, SPACE, 201, 100))
            .thenReturn(List.of(
                worklog(ITEM, "submitted", 600),
                worklog(HIDDEN, "submitted", 300)
            ));
        when(fixture.workItems.get(any(), eq(SPACE), eq(ITEM))).thenReturn(view(ITEM));
        when(fixture.workItems.get(any(), eq(SPACE), eq(HIDDEN)))
            .thenThrow(new WorkItemRuntimeException("FORBIDDEN", "revoked"));
        when(fixture.planning.findCalendar(WORKSPACE, SPACE))
            .thenReturn(Optional.of(new WorkCalendar(
                UUID.randomUUID(), "UTC", List.of(1, 2, 3, 4, 5),
                480, List.of(), 1, USER, NOW
            )));
        when(fixture.planning.listEstimates(WORKSPACE, SPACE, 200))
            .thenReturn(List.of(new Estimate(
                UUID.randomUUID(), ITEM, "day", new BigDecimal("1"),
                1, 1, USER, NOW
            )));

        var result = fixture.service.get(user(), SPACE);

        assertThat(result.worklogs()).extracting(Worklog::workItemId)
            .containsExactly(ITEM);
        assertThat(result.variance()).singleElement().satisfies(value -> {
            assertThat(value.comparable()).isTrue();
            assertThat(value.estimatedMinutes()).isEqualTo(480);
            assertThat(value.actualMinutes()).isEqualTo(600);
            assertThat(value.varianceMinutes()).isEqualTo(120);
        });
    }

    @Test
    void guestAndFutureEntriesAreRejected() {
        Fixture guest = fixture("guest");
        MutateWorklogCommand command = new MutateWorklogCommand(
            1, "worklog-create", "create", null, 0,
            ITEM, USER, LocalDate.parse("2026-07-29"), 60, "manual", ""
        );
        assertThatThrownBy(() -> guest.service.mutate(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("read-only");

        Fixture owner = fixture("owner");
        assertThatThrownBy(() -> owner.service.mutate(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");
    }

    private static Fixture fixture(String role) {
        ResourceWorklogRepository repository = mock(ResourceWorklogRepository.class);
        ResourcePlanningRepository planning = mock(ResourcePlanningRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        WorkItemService workItems = mock(WorkItemService.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        ResourceWorklogService service = new ResourceWorklogService(
            repository, planning, spaces, workItems, mock(AuditLog.class),
            mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(repository, planning, workItems, service);
    }

    private static Worklog worklog(UUID itemId, String state, int minutes) {
        return new Worklog(
            UUID.randomUUID(), itemId, USER, LocalDate.parse("2026-07-27"),
            minutes, "manual", state, 1, 1, USER, NOW, List.of()
        );
    }

    private static WorkItemView view(UUID id) {
        ObjectMapper mapper = new ObjectMapper();
        WorkItem item = new WorkItem(
            id, WORKSPACE, SPACE, UUID.randomUUID(), UUID.randomUUID(),
            "task", "Task", "hash", 1, "DELIVERY-1", "Visible",
            mapper.createObjectNode(), "active", 1, USER, NOW, USER, NOW, null
        );
        return new WorkItemView(item, item.fieldValues(), mapper.createObjectNode(), List.of());
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
        ResourceWorklogRepository repository,
        ResourcePlanningRepository planning,
        WorkItemService workItems,
        ResourceWorklogService service
    ) {
    }
}
