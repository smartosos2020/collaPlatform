package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.identity.contract.IdentityGovernance;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.CrossSpaceGrant;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantLifecycleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.SaveGrantCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.CrossSpaceGrantRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CrossSpaceGrantServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID GRANT = UUID.randomUUID();
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsBoundedVersionedGrantAndEmitsMinimalSideEffects() {
        Fixture fixture = fixture("owner");
        SaveGrantCommand command = command();
        CrossSpaceGrant result = grant("draft", false, false);
        when(fixture.repository.findReceipt(
            WORKSPACE, USER, "create", command.requestId()
        )).thenReturn(Optional.empty());
        when(fixture.repository.create(
            eq(WORKSPACE), eq(SOURCE), eq(TARGET), eq(USER),
            eq("Engineering handoff"), any(), any()
        )).thenReturn(result);

        assertThat(fixture.service.save(user(Set.of("owner")), SOURCE, command))
            .isEqualTo(result);
        verify(fixture.repository).saveReceipt(
            eq(WORKSPACE), eq(USER), eq("create"), eq(command.requestId()),
            any(), eq(GRANT), any()
        );
        verify(fixture.audit).log(
            any(), eq("project_cross_space.grant_create"),
            eq("project_cross_space_grant"), eq(GRANT), any()
        );
        verify(fixture.outbox).append(
            eq(WORKSPACE), eq("project.cross-space.grant.changed"),
            eq("project_cross_space_grant"), eq(GRANT), eq(USER), any(), any()
        );
    }

    @Test
    void activatesOnlyAfterEachSpaceManagerConfirms() {
        Fixture fixture = fixture("owner");
        when(fixture.repository.find(WORKSPACE, GRANT))
            .thenReturn(Optional.of(grant("requested", true, false)));
        when(fixture.repository.findReceipt(
            WORKSPACE, USER, "confirm", "confirm-target"
        )).thenReturn(Optional.empty());
        when(fixture.spaces.findActiveRole(WORKSPACE, TARGET, USER))
            .thenReturn(Optional.of("admin"));
        CrossSpaceGrant active = grant("active", true, true);
        when(fixture.repository.transition(
            WORKSPACE, GRANT, USER, 1, "confirm", "target"
        )).thenReturn(active);

        CrossSpaceGrant result = fixture.service.lifecycle(
            user(Set.of()), GRANT,
            new GrantLifecycleCommand(
                1, "confirm-target", 1, "confirm", "target", null
            )
        );

        assertThat(result.status()).isEqualTo("active");
        assertThat(result.sourceConfirmed()).isTrue();
        assertThat(result.targetConfirmed()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("identities")
    void failsClosedForNonManagersIncludingEnterpriseAdmin(
        String role, Set<String> platformRoles
    ) {
        Fixture fixture = fixture(role);
        assertThatThrownBy(() -> fixture.service.save(
            user(platformRoles), SOURCE, command()
        )).isInstanceOf(WorkItemRuntimeException.class);
    }

    private static Stream<Arguments> identities() {
        return Stream.of(
            Arguments.of("member", Set.of()),
            Arguments.of("guest", Set.of()),
            Arguments.of(null, Set.of()),
            Arguments.of(null, Set.of("enterprise-admin"))
        );
    }

    private static Fixture fixture(String role) {
        CrossSpaceGrantRepository repository = mock(CrossSpaceGrantRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        PublishedSnapshotAdapter snapshots = mock(PublishedSnapshotAdapter.class);
        IdentityGovernance identities = mock(IdentityGovernance.class);
        AuditLog audit = mock(AuditLog.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        when(spaces.findById(WORKSPACE, SOURCE, USER))
            .thenReturn(Optional.of(space(SOURCE, role)));
        when(spaces.findById(WORKSPACE, TARGET, USER))
            .thenReturn(Optional.of(space(TARGET, role)));
        when(spaces.findActiveRole(WORKSPACE, SOURCE, USER))
            .thenReturn(Optional.ofNullable(role));
        when(spaces.findActiveRole(WORKSPACE, TARGET, USER))
            .thenReturn(Optional.ofNullable(role));
        when(identities.isActive(eq(WORKSPACE), any(), any())).thenReturn(true);
        CrossSpaceGrantService service = new CrossSpaceGrantService(
            repository, spaces, identities, snapshots, audit, outbox, JSON
        );
        return new Fixture(repository, spaces, audit, outbox, service);
    }

    private static SaveGrantCommand command() {
        return new SaveGrantCommand(
            1, "create-grant", 0, null, TARGET,
            "Engineering handoff", scope()
        );
    }

    private static ObjectNode scope() {
        ObjectNode scope = JSON.createObjectNode();
        scope.put("schemaVersion", 1);
        scope.put("direction", "bidirectional");
        scope.putArray("operations").add("reference").add("relate");
        ObjectNode type = scope.putArray("typeScopes").addObject();
        type.put("sourceTypeId", UUID.randomUUID().toString());
        type.put("sourceVersionId", UUID.randomUUID().toString());
        type.put("targetTypeId", UUID.randomUUID().toString());
        type.put("targetVersionId", UUID.randomUUID().toString());
        return scope;
    }

    private static CrossSpaceGrant grant(
        String status, boolean sourceConfirmed, boolean targetConfirmed
    ) {
        return new CrossSpaceGrant(
            GRANT, SOURCE, TARGET, "Engineering handoff", status, 1,
            sourceConfirmed, targetConfirmed,
            sourceConfirmed ? USER : null, targetConfirmed ? USER : null,
            scope(), "hash", USER,
            Instant.parse("2026-07-28T00:00:00Z"), null, null
        );
    }

    private static ProjectSpaceSummary space(UUID id, String role) {
        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        return new ProjectSpaceSummary(
            id, WORKSPACE, "SPACE", "Space", "", "active", "private",
            1, role, 2, USER, now, USER, now, null, null
        );
    }

    private static CurrentUser user(Set<String> roles) {
        return new CurrentUser(
            USER, WORKSPACE, UUID.randomUUID(), "actor", "Actor",
            roles, Set.of()
        );
    }

    private record Fixture(
        CrossSpaceGrantRepository repository,
        ProjectSpaceRepository spaces,
        AuditLog audit,
        TransactionalOutbox outbox,
        CrossSpaceGrantService service
    ) {
    }
}
