package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectPlanModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanLink;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanMilestone;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanPhase;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectPlanRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectPlanServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID PHASE = UUID.randomUUID();
    private static final UUID MILESTONE = UUID.randomUUID();
    private static final UUID HIDDEN_ITEM = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void getRecalibratesHiddenLinksAndDerivesBoundedProgress() {
        Fixture fixture = fixture("owner");
        when(fixture.repository.find(WORKSPACE, SPACE, PLAN, 100))
            .thenReturn(Optional.of(plan()));
        when(fixture.workItems.get(any(), eq(SPACE), eq(HIDDEN_ITEM)))
            .thenThrow(new WorkItemRuntimeException("FORBIDDEN", "revoked"));

        ProjectPlan result = fixture.service.get(user(), SPACE, PLAN);

        assertThat(result.links()).isEmpty();
        assertThat(result.progress().visibleLinks()).isZero();
        assertThat(result.progress().overdueMilestones()).isEqualTo(1);
        assertThat(result.progress().truncated()).isTrue();
        assertThat(com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_PHASES)
            .isEqualTo(24);
        assertThat(com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_MILESTONES)
            .isEqualTo(100);
        assertThat(com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_LINKS)
            .isEqualTo(200);
    }

    @Test
    void guestCannotCreatePlanEvenWithValidGraphShape() {
        Fixture fixture = fixture("guest");
        CreateCommand command = new CreateCommand(
            1, "plan-create", "Delivery", "",
            LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-31"),
            List.of(), List.of(), List.of()
        );

        assertThatThrownBy(() -> fixture.service.create(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("read-only");
    }

    private static Fixture fixture(String role) {
        ProjectPlanRepository repository = mock(ProjectPlanRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        ProjectSpaceMembershipRepository members = mock(ProjectSpaceMembershipRepository.class);
        WorkItemService workItems = mock(WorkItemService.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        ProjectPlanService service = new ProjectPlanService(
            repository,
            spaces,
            members,
            workItems,
            mock(AuditLog.class),
            mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(repository, workItems, service);
    }

    private static ProjectPlan plan() {
        PlanSummary summary = new PlanSummary(
            PLAN, "Delivery", "", LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-08-31"), "published", 2, USER, NOW, USER, NOW, null
        );
        PlanPhase phase = new PlanPhase(
            PHASE, "delivery", "Delivery", 0,
            LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-31"), "active"
        );
        PlanMilestone milestone = new PlanMilestone(
            MILESTONE, PHASE, "release", "Release", 0,
            LocalDate.parse("2026-07-20"), "active", null
        );
        PlanLink link = new PlanLink(
            UUID.randomUUID(), MILESTONE, HIDDEN_ITEM, 1
        );
        return new ProjectPlan(
            summary, List.of(phase), List.of(milestone), List.of(link), List.of(), null
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
        ProjectPlanRepository repository,
        WorkItemService workItems,
        ProjectPlanService service
    ) {
    }
}
