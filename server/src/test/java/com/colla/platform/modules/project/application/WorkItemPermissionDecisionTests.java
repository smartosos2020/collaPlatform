package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemPermissionDecisionTests {
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("50000000-0000-0000-0000-000000000001");

    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkItemPermissionPresetCatalog presets = new WorkItemPermissionPresetCatalog(mapper);
    private final WorkItemPermissionDecisionService service = new WorkItemPermissionDecisionService(
        new WorkItemPermissionRuntimeAdapter(),
        Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void singleAndBatchDecisionsAreEquivalentAndRoleInheritanceIsStable() {
        RuntimeConfiguration configuration = configuration(presets.modelFor("task"));
        SubjectContext admin = subject(Set.of("admin"), Set.of());
        var single = service.decide(configuration, admin, SPACE, ITEM, "view");
        var batch = service.decideBatch(List.of(
            new WorkItemPermissionDecisionService.DecisionInput(
                configuration, admin, SPACE, ITEM, "view"
            )
        )).getFirst();

        assertThat(single).isEqualTo(batch);
        assertThat(single.allowed()).isTrue();
        assertThat(single.safePolicySources()).containsExactly("space_member_baseline", "space_guest_baseline");
        assertThat(single.policyVersionId()).isEqualTo(VERSION);
    }

    @Test
    void denyOverridesAllowAndEnterpriseRoleDoesNotBecomeContentOwner() {
        var model = (com.fasterxml.jackson.databind.node.ObjectNode) presets.modelFor("task").deepCopy();
        var deny = model.withArray("permissionPolicies").addObject();
        deny.put("policyKey", "deny_member_delete");
        deny.put("effect", "deny");
        deny.putArray("actionKeys").add("delete");
        deny.putArray("subjectSelectors").addObject().put("kind", "space_role").put("key", "owner");
        deny.putObject("dataScope").put("kind", "all");
        deny.put("priority", 999);

        assertThat(service.decide(
            configuration(model), subject(Set.of("owner"), Set.of()), SPACE, ITEM, "delete"
        ).allowed()).isFalse();
        assertThat(service.decide(
            configuration(model), subject(Set.of(), Set.of("enterprise_admin")), SPACE, ITEM, "view"
        ).allowed()).isFalse();
    }

    @Test
    void creatorCanEditButUnknownActionsAndOversizedBatchesFailClosed() {
        RuntimeConfiguration configuration = configuration(presets.modelFor("task"));
        assertThat(service.decide(
            configuration, subject(Set.of(), Set.of(), Set.of("creator")), SPACE, ITEM, "edit"
        ).allowed()).isTrue();
        assertThatThrownBy(() -> service.decide(
            configuration, subject(Set.of("member"), Set.of()), SPACE, ITEM, "unknown"
        )).hasMessageContaining("Permission action");
        var input = new WorkItemPermissionDecisionService.DecisionInput(
            configuration, subject(Set.of("member"), Set.of()), SPACE, ITEM, "view"
        );
        assertThatThrownBy(() -> service.decideBatch(java.util.Collections.nCopies(201, input)))
            .hasMessageContaining("1 to 200");
    }

    @Test
    void legacyBoundSnapshotsKeepTheFrozenSpaceRoleCeilingWithoutLivePolicyFallback() {
        var snapshot = mapper.createObjectNode();
        snapshot.put("snapshotSchemaVersion", 4);
        RuntimeConfiguration legacy = new RuntimeConfiguration(
            VERSION, UUID.randomUUID(), 1, 4, "b".repeat(64), snapshot
        );
        assertThat(service.decide(
            legacy, subject(Set.of("guest"), Set.of()), SPACE, ITEM, "view"
        ).allowed()).isTrue();
        assertThat(service.decide(
            legacy, subject(Set.of("guest"), Set.of()), SPACE, ITEM, "edit"
        ).allowed()).isFalse();
        assertThat(service.decide(
            legacy, subject(Set.of(), Set.of("enterprise_admin")), SPACE, ITEM, "view"
        ).allowed()).isFalse();
    }

    private RuntimeConfiguration configuration(com.fasterxml.jackson.databind.JsonNode model) {
        var snapshot = mapper.createObjectNode();
        snapshot.put("snapshotSchemaVersion", 5);
        snapshot.set("permissionModel", model);
        return new RuntimeConfiguration(VERSION, UUID.randomUUID(), 1, 5, "a".repeat(64), snapshot);
    }

    private SubjectContext subject(Set<String> spaceRoles, Set<String> enterpriseRoles) {
        return subject(spaceRoles, enterpriseRoles, Set.of());
    }

    private SubjectContext subject(
        Set<String> spaceRoles,
        Set<String> enterpriseRoles,
        Set<String> workItemRoles
    ) {
        return new SubjectContext(
            WORKSPACE, USER, 1, enterpriseRoles, spaceRoles, workItemRoles, Set.of()
        );
    }
}
