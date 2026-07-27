package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterEntry;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterReference;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectRegisterRepository;
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

class ProjectRegisterServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ENTRY = UUID.randomUUID();
    private static final UUID HIDDEN_ITEM = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void getRecalibratesHiddenReferencesAndFreezesBudgets() {
        Fixture fixture = fixture("owner");
        when(fixture.repository.find(WORKSPACE, SPACE, ENTRY, 100))
            .thenReturn(Optional.of(entry()));
        when(fixture.workItems.get(any(), eq(SPACE), eq(HIDDEN_ITEM)))
            .thenThrow(new WorkItemRuntimeException("FORBIDDEN", "revoked"));

        RegisterEntry result = fixture.service.get(user(), SPACE, ENTRY);

        assertThat(result.references()).isEmpty();
        assertThat(result.referencesTruncated()).isTrue();
        assertThat(com.colla.platform.modules.project.domain.ProjectRegisterModels.MAX_ENTRIES)
            .isEqualTo(200);
        assertThat(com.colla.platform.modules.project.domain.ProjectRegisterModels.MAX_REFERENCES)
            .isEqualTo(100);
        assertThat(com.colla.platform.modules.project.domain.ProjectRegisterModels.MAX_RESPONSES)
            .isEqualTo(20);
    }

    @Test
    void guestCannotCreateRisk() {
        Fixture fixture = fixture("guest");
        CreateCommand command = new CreateCommand(
            1, "risk-create", "risk", "Delivery risk", "", null,
            LocalDate.parse("2026-08-01"), 3, 4, "", "",
            List.of(), List.of()
        );

        assertThatThrownBy(() -> fixture.service.create(user(), SPACE, command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("read-only");
    }

    private static Fixture fixture(String role) {
        ProjectRegisterRepository repository = mock(ProjectRegisterRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        ProjectSpaceMembershipRepository members =
            mock(ProjectSpaceMembershipRepository.class);
        WorkItemService workItems = mock(WorkItemService.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        ProjectRegisterService service = new ProjectRegisterService(
            repository, spaces, members, workItems, mock(ProjectPlanService.class),
            mock(AuditLog.class), mock(TransactionalOutbox.class),
            new ObjectMapper().findAndRegisterModules()
        );
        return new Fixture(repository, workItems, service);
    }

    private static RegisterEntry entry() {
        RegisterSummary summary = new RegisterSummary(
            ENTRY, "risk", "Delivery risk", "", "monitoring", null,
            LocalDate.parse("2026-08-01"), 3, 4, 12, "", "", null, "",
            2, USER, NOW, USER, NOW
        );
        return new RegisterEntry(
            summary,
            List.of(new RegisterReference(
                UUID.randomUUID(), "work_item", HIDDEN_ITEM, 1
            )),
            List.of(), List.of(), false
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
        ProjectRegisterRepository repository,
        WorkItemService workItems,
        ProjectRegisterService service
    ) {
    }
}
