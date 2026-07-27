package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectRegistry;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Deliverable;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableVersion;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MaterialReference;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectDeliveryRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectDeliveryServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID DELIVERY = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID HIDDEN_FILE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void getHidesRevokedMaterialsAndFreezesBudgets() {
        Fixture fixture = fixture("owner");
        when(fixture.repository.find(WORKSPACE, SPACE, DELIVERY))
            .thenReturn(Optional.of(deliverable()));
        when(fixture.objects.accessState(WORKSPACE, USER, "file", HIDDEN_FILE))
            .thenReturn(ObjectAccessState.forbidden);

        Deliverable result = fixture.service.get(user(), SPACE, DELIVERY);

        assertThat(result.versions().getFirst().materials()).isEmpty();
        assertThat(result.materialsTruncated()).isTrue();
        assertThat(com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_VERSIONS)
            .isEqualTo(50);
        assertThat(com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_MATERIALS)
            .isEqualTo(50);
        assertThat(com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_SIGNERS)
            .isEqualTo(30);
    }

    @Test
    void guestCannotCreateDeliverable() {
        Fixture fixture = fixture("guest");
        CreateCommand command = new CreateCommand(
            1, "deliverable-create", "Release", "", null,
            LocalDate.parse("2026-09-30"), null, null, List.of()
        );

        assertThatThrownBy(() -> fixture.service.create(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("read-only");
    }

    private static Fixture fixture(String role) {
        ProjectDeliveryRepository repository = mock(ProjectDeliveryRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        ProjectSpaceMembershipRepository members =
            mock(ProjectSpaceMembershipRepository.class);
        PlatformObjectRegistry objects = mock(PlatformObjectRegistry.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        ProjectDeliveryService service = new ProjectDeliveryService(
            repository, spaces, members, mock(ProjectPlanService.class),
            mock(ProjectRegisterService.class), objects, mock(AuditLog.class),
            mock(TransactionalOutbox.class), new ObjectMapper().findAndRegisterModules()
        );
        return new Fixture(repository, objects, service);
    }

    private static Deliverable deliverable() {
        DeliverableSummary summary = new DeliverableSummary(
            DELIVERY, "Release", "", "submitted", null,
            LocalDate.parse("2026-09-30"), null, null, List.of(), VERSION,
            2, USER, NOW, USER, NOW
        );
        DeliverableVersion version = new DeliverableVersion(
            VERSION, 1, "v1", "submitted", USER, NOW,
            List.of(new MaterialReference(
                UUID.randomUUID(), "file", HIDDEN_FILE, 1, null
            ))
        );
        return new Deliverable(
            summary, List.of(version), List.of(), List.of(), false
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
        ProjectDeliveryRepository repository,
        PlatformObjectRegistry objects,
        ProjectDeliveryService service
    ) {
    }
}
