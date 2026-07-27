package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectDetailModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanProgress;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectDetailRepository;
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
import org.springframework.dao.TransientDataAccessResourceException;

class ProjectDetailServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void derivesCriticalHealthFromCanonicalServicesWithExplainableSignals() {
        Fixture fixture = fixture(true);
        PlanSummary plan = plan();
        when(fixture.plans.list(any(), eq(SPACE))).thenReturn(List.of(plan));
        when(fixture.plans.get(any(), eq(SPACE), eq(PLAN)))
            .thenReturn(new ProjectPlan(
                plan, List.of(), List.of(), List.of(), List.of(),
                new PlanProgress(2, 1, 0, 1, 50, false)
            ));
        when(fixture.register.list(any(), eq(SPACE), eq(null)))
            .thenReturn(List.of(risk(), issue()));
        when(fixture.deliveries.list(any(), eq(SPACE)))
            .thenReturn(List.of(deliverable("rejected")));
        when(fixture.repository.findPreference(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.empty());

        ProjectDetail detail = fixture.service.get(user(), SPACE);

        assertThat(detail.health().status()).isEqualTo("critical");
        assertThat(detail.health().truncated()).isFalse();
        assertThat(detail.health().policyVersion()).isEqualTo("project-health-v1");
        assertThat(detail.health().signals())
            .extracting(value -> value.code())
            .containsExactlyInAnyOrder(
                "schedule_overdue", "risk_high", "issue_open", "delivery_rejected"
            );
        assertThat(detail.health().signals())
            .allSatisfy(signal -> {
                assertThat(signal.rule()).isNotBlank();
                assertThat(signal.explanation()).isNotBlank();
                assertThat(signal.sourceVersion()).isPositive();
                assertThat(signal.observedAt()).isEqualTo(NOW);
            });
        assertThat(detail.blocking().openIssues()).isEqualTo(1);
        assertThat(detail.blocking().highRisks()).isEqualTo(1);
        assertThat(detail.blocking().rejectedDeliverables()).isEqualTo(1);
        assertThat(detail.deviations()).singleElement()
            .satisfies(value -> {
                assertThat(value.completionPercent()).isEqualTo(50);
                assertThat(value.overdueMilestones()).isEqualTo(1);
            });
        assertThat(detail.preference().visibleSections())
            .containsExactly("plan", "register", "delivery", "health");
    }

    @Test
    void disposableProjectionFailureDoesNotBlockCanonicalDetail() {
        Fixture fixture = fixture(true);
        when(fixture.plans.list(any(), eq(SPACE))).thenReturn(List.of());
        when(fixture.register.list(any(), eq(SPACE), eq(null))).thenReturn(List.of());
        when(fixture.deliveries.list(any(), eq(SPACE))).thenReturn(List.of());
        when(fixture.repository.findPreference(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.empty());
        doThrow(new TransientDataAccessResourceException("projection unavailable"))
            .when(fixture.repository).recordProjection(
                eq(WORKSPACE), eq(SPACE), eq(USER), any(), any()
            );

        ProjectDetail detail = fixture.service.get(user(), SPACE);

        assertThat(detail.health().status()).isEqualTo("healthy");
        assertThat(detail.health().signals()).isEmpty();
    }

    @Test
    void nonMemberCannotObserveProjectHealthOrSourceExistence() {
        Fixture fixture = fixture(false);

        assertThatThrownBy(() -> fixture.service.get(user(), SPACE))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("not available");
    }

    private static Fixture fixture(boolean visible) {
        ProjectDetailRepository repository = mock(ProjectDetailRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        ProjectPlanService plans = mock(ProjectPlanService.class);
        ProjectRegisterService register = mock(ProjectRegisterService.class);
        ProjectDeliveryService deliveries = mock(ProjectDeliveryService.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(visible ? Optional.of(space()) : Optional.empty());
        ProjectDetailService service = new ProjectDetailService(
            repository, spaces, plans, register, deliveries,
            mock(AuditLog.class), mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(repository, plans, register, deliveries, service);
    }

    private static PlanSummary plan() {
        return new PlanSummary(
            PLAN, "Release", "", LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-08-31"), "published", 2,
            USER, NOW, USER, NOW, null
        );
    }

    private static RegisterSummary risk() {
        return register("risk", "open", 5, 4, 20);
    }

    private static RegisterSummary issue() {
        return register("issue", "open", null, null, 0);
    }

    private static RegisterSummary register(
        String type, String status, Integer probability, Integer impact, int score
    ) {
        return new RegisterSummary(
            UUID.randomUUID(), type, type, "", status, USER,
            LocalDate.parse("2026-08-01"), probability, impact, score,
            "", "", null, "", 2, USER, NOW, USER, NOW
        );
    }

    private static DeliverableSummary deliverable(String status) {
        return new DeliverableSummary(
            UUID.randomUUID(), "Package", "", status, USER,
            LocalDate.parse("2026-08-01"), PLAN, null, List.of(), null,
            2, USER, NOW, USER, NOW
        );
    }

    private static ProjectSpaceSummary space() {
        return new ProjectSpaceSummary(
            SPACE, WORKSPACE, "DELIVERY", "Delivery", "", "active", "private",
            1, "member", 1, USER, NOW, USER, NOW, null, null
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER, WORKSPACE, UUID.randomUUID(), "member", "Member",
            Set.of("member"), Set.of()
        );
    }

    private record Fixture(
        ProjectDetailRepository repository,
        ProjectPlanService plans,
        ProjectRegisterService register,
        ProjectDeliveryService deliveries,
        ProjectDetailService service
    ) {
    }
}
