package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.Allocation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.MutateAllocationCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Worklog;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourceCapacityRepository;
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

class ResourceCapacityServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void derivesOverloadFromCalendarAllocationsAndSubmittedActual() {
        Fixture fixture = fixture("owner");
        when(fixture.members.listMembers(WORKSPACE, SPACE))
            .thenReturn(List.of(member(USER)));
        when(fixture.repository.listAllocations(WORKSPACE, SPACE, 201))
            .thenReturn(List.of(
                allocation("100"),
                allocation("50")
            ));
        when(fixture.repository.listRules(WORKSPACE, SPACE, 200))
            .thenReturn(List.of());
        when(fixture.planning.findCalendar(WORKSPACE, SPACE))
            .thenReturn(Optional.of(new WorkCalendar(
                UUID.randomUUID(), "UTC", List.of(1, 2, 3, 4, 5),
                480, List.of(), 1, USER, NOW
            )));
        when(fixture.worklogs.list(WORKSPACE, SPACE, 200, 1))
            .thenReturn(List.of(new Worklog(
                UUID.randomUUID(), ITEM, USER, LocalDate.parse("2026-07-28"),
                300, "manual", "submitted", 1, 1, USER, NOW, List.of()
            )));
        when(fixture.workItems.get(any(), eq(SPACE), eq(ITEM))).thenReturn(view());

        var result = fixture.service.get(user(), SPACE);

        assertThat(result.buckets()).singleElement().satisfies(value -> {
            assertThat(value.capacityMinutes()).isEqualTo(480);
            assertThat(value.allocatedMinutes()).isEqualTo(720);
            assertThat(value.actualMinutes()).isEqualTo(300);
            assertThat(value.signal()).isEqualTo("overloaded");
            assertThat(value.conflict()).isTrue();
        });
    }

    @Test
    void ordinaryMemberCannotCreateAllocations() {
        Fixture fixture = fixture("member");
        MutateAllocationCommand command = new MutateAllocationCommand(
            1, "allocation-create", "create", null, 0, ITEM, USER,
            LocalDate.parse("2026-07-28"), LocalDate.parse("2026-07-29"),
            new BigDecimal("50"), "planned"
        );
        assertThatThrownBy(() -> fixture.service.mutate(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("owner/admin");
    }

    private static Fixture fixture(String role) {
        ResourceCapacityRepository repository = mock(ResourceCapacityRepository.class);
        ResourcePlanningRepository planning = mock(ResourcePlanningRepository.class);
        ResourceWorklogRepository worklogs = mock(ResourceWorklogRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        ProjectSpaceMembershipRepository members =
            mock(ProjectSpaceMembershipRepository.class);
        WorkItemService workItems = mock(WorkItemService.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        ResourceCapacityService service = new ResourceCapacityService(
            repository, planning, worklogs, spaces, members, workItems,
            mock(AuditLog.class), mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(repository, planning, worklogs, members, workItems, service);
    }

    private static Allocation allocation(String percent) {
        return new Allocation(
            UUID.randomUUID(), ITEM, USER, LocalDate.parse("2026-07-28"),
            LocalDate.parse("2026-07-28"), new BigDecimal(percent), "active",
            1, USER, NOW
        );
    }

    private static ProjectSpaceMember member(UUID userId) {
        return new ProjectSpaceMember(
            UUID.randomUUID(), SPACE, userId, "user", "User", null,
            "user@example.com", "active", "active", "member", NOW, null, NOW
        );
    }

    private static WorkItemView view() {
        ObjectMapper mapper = new ObjectMapper();
        WorkItem item = new WorkItem(
            ITEM, WORKSPACE, SPACE, UUID.randomUUID(), UUID.randomUUID(),
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
        ResourceCapacityRepository repository,
        ResourcePlanningRepository planning,
        ResourceWorklogRepository worklogs,
        ProjectSpaceMembershipRepository members,
        WorkItemService workItems,
        ResourceCapacityService service
    ) {
    }
}
