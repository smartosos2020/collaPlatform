package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.Allocation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityFoundation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.LoadBucket;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.AdjustmentCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourceScheduleRepository;
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

class ResourceScheduleServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID ALLOCATION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void derivesRowsBarsAndConflictMarkersFromCurrentCapacityContract() {
        Fixture fixture = fixture("owner");
        when(fixture.repository.findPreference(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.empty());
        when(fixture.capacity.get(any(), eq(SPACE))).thenReturn(new CapacityFoundation(
            List.of(new Allocation(
                ALLOCATION, ITEM, USER, LocalDate.parse("2026-07-28"),
                LocalDate.parse("2026-07-29"), new BigDecimal("100"),
                "active", 3, USER, NOW
            )),
            List.of(),
            List.of(new LoadBucket(
                USER, LocalDate.parse("2026-07-28"), 480, 720, 300,
                "overloaded", true, "Current capacity conflict"
            )),
            false
        ));

        var result = fixture.service.get(user(), SPACE);

        assertThat(result.rows()).singleElement().satisfies(value -> {
            assertThat(value.capacityMinutes()).isEqualTo(480);
            assertThat(value.allocatedMinutes()).isEqualTo(720);
            assertThat(value.conflictCount()).isEqualTo(1);
        });
        assertThat(result.bars()).singleElement()
            .extracting(value -> value.sourceVersion()).isEqualTo(3L);
        assertThat(result.conflicts()).singleElement()
            .extracting(value -> value.signal()).isEqualTo("overloaded");
    }

    @Test
    void ordinaryMemberCannotPreviewAdjustments() {
        Fixture fixture = fixture("member");
        AdjustmentCommand command = new AdjustmentCommand(
            1, "adjust-preview", true, ALLOCATION, 1,
            LocalDate.parse("2026-07-28"), LocalDate.parse("2026-07-29"),
            new BigDecimal("50"), "rebalance"
        );
        assertThatThrownBy(() -> fixture.service.adjust(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("owner/admin");
    }

    private static Fixture fixture(String role) {
        ResourceScheduleRepository repository = mock(ResourceScheduleRepository.class);
        ResourceCapacityService capacity = mock(ResourceCapacityService.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        ResourceScheduleService service = new ResourceScheduleService(
            repository, capacity, spaces, mock(AuditLog.class),
            mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(repository, capacity, service);
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
        ResourceScheduleRepository repository,
        ResourceCapacityService capacity,
        ResourceScheduleService service
    ) {
    }
}
