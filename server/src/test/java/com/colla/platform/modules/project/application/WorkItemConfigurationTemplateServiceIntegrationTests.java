package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.WorkItemConfigurationException;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class WorkItemConfigurationTemplateServiceIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WorkItemConfigurationTemplateService service;

    @Autowired
    private WorkItemConfigurationSnapshotCanonicalizer canonicalizer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void installsCopiesUpgradesMergesDetachesAndReplaysExactly() throws Exception {
        Fixture fixture = fixture("lifecycle", "owner");
        var workspaceTemplate = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            fixture.versionId(),
            "delivery_" + fixture.suffix(),
            "Delivery",
            "Delivery template"
        );
        String installRequest = "template-install-" + UUID.randomUUID();
        var installed = service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            workspaceTemplate.id(),
            workspaceTemplate.currentVersion().id(),
            0,
            installRequest
        );
        assertFalse(installed.replayed());
        assertEquals("attached", installed.installation().status());
        assertEquals(fixture.typeId().toString(), jdbcTemplate.queryForObject(
            "select snapshot->'typeDefinition'->>'id' from project_work_item_configuration_drafts where id=?",
            String.class,
            fixture.draftId()
        ));
        assertNotEquals(
            workspaceTemplate.currentVersion().configHash(),
            installed.draft().configHash(),
            "cross-space installation must rebind target identity"
        );
        var replay = service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            workspaceTemplate.id(),
            workspaceTemplate.currentVersion().id(),
            0,
            installRequest
        );
        assertTrue(replay.replayed());

        addLocalOwnerField(fixture);
        UUID upstreamSourceVersionId = insertUpstreamVersion(fixture);
        var updatedTemplate = service.addWorkspaceTemplateVersion(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            workspaceTemplate.id(),
            upstreamSourceVersionId
        );
        var preview = service.previewUpgrade(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            updatedTemplate.currentVersion().id(),
            java.util.Map.of()
        );
        assertTrue(preview.upgradeAvailable());
        assertTrue(preview.conflicts().isEmpty());
        assertEquals(2, preview.mergedSnapshot().path("fields").size());

        String upgradeRequest = "template-upgrade-" + UUID.randomUUID();
        var upgraded = service.applyUpgrade(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            updatedTemplate.currentVersion().id(),
            2,
            installed.installation().aggregateVersion(),
            java.util.Map.of(),
            upgradeRequest
        );
        assertEquals(updatedTemplate.currentVersion().id(), upgraded.installation().upstreamVersionId());
        assertEquals(2, jdbcTemplate.queryForObject(
            "select jsonb_array_length(snapshot->'fields') from project_work_item_configuration_drafts where id=?",
            Integer.class,
            fixture.draftId()
        ));

        String hashBeforeDetach = upgraded.draft().configHash();
        String detachRequest = "template-detach-" + UUID.randomUUID();
        var detached = service.detach(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            upgraded.installation().aggregateVersion(),
            detachRequest
        );
        assertEquals("detached", detached.installation().status());
        assertEquals(hashBeforeDetach, detached.draft().configHash());
        assertTrue(service.detach(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            upgraded.installation().aggregateVersion(),
            detachRequest
        ).replayed());
        WorkItemConfigurationException reused = assertThrows(
            WorkItemConfigurationException.class,
            () -> service.detach(
                fixture.user(),
                fixture.spaceId(),
                fixture.typeId(),
                upgraded.installation().aggregateVersion() + 1,
                detachRequest
            )
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.code());
    }

    @Test
    void enforcesSixIdentityDisclosureBoundaryForTemplateCatalog() throws Exception {
        Fixture owner = fixture("security", "owner");
        CurrentUser member = addIdentity(owner, "member", true);
        CurrentUser guest = addIdentity(owner, "guest", true);
        CurrentUser nonMember = addIdentity(owner, "member", false);
        CurrentUser enterpriseAdmin = addIdentity(owner, "member", false);

        assertFalse(service.catalog(owner.user(), owner.spaceId()).isEmpty());
        assertEquals("FORBIDDEN", assertThrows(
            WorkItemConfigurationException.class,
            () -> service.catalog(member, owner.spaceId())
        ).code());
        assertEquals("FORBIDDEN", assertThrows(
            WorkItemConfigurationException.class,
            () -> service.catalog(guest, owner.spaceId())
        ).code());
        assertEquals("NOT_FOUND_OR_HIDDEN", assertThrows(
            WorkItemConfigurationException.class,
            () -> service.catalog(nonMember, owner.spaceId())
        ).code());
        assertEquals("NOT_FOUND_OR_HIDDEN", assertThrows(
            WorkItemConfigurationException.class,
            () -> service.catalog(enterpriseAdmin, owner.spaceId())
        ).code());
    }

    private void addLocalOwnerField(Fixture fixture) throws Exception {
        ObjectNode snapshot = (ObjectNode) objectMapper.readTree(jdbcTemplate.queryForObject(
            "select snapshot::text from project_work_item_configuration_drafts where id=?",
            String.class,
            fixture.draftId()
        ));
        ObjectNode field = snapshot.withArray("fields").addObject();
        field.put("id", UUID.randomUUID().toString());
        field.put("fieldKey", "owner");
        field.put("name", "Owner");
        field.put("description", "");
        field.put("fieldType", "text");
        field.putObject("config");
        field.put("sortOrder", 20);
        field.put("status", "active");
        field.put("system", false);
        field.putArray("options");
        var canonical = canonicalizer.canonicalize(snapshot);
        jdbcTemplate.update(
            """
                update project_work_item_configuration_drafts
                   set snapshot=?::jsonb, config_hash=?, aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where id=?
                """,
            canonical.payload().toString(),
            canonical.configHash(),
            fixture.user().id(),
            fixture.draftId()
        );
    }

    private UUID insertUpstreamVersion(Fixture fixture) throws Exception {
        ObjectNode snapshot = (ObjectNode) fixture.sourceSnapshot().deepCopy();
        ObjectNode field = snapshot.withArray("fields").addObject();
        field.put("id", UUID.randomUUID().toString());
        field.put("fieldKey", "priority");
        field.put("name", "Priority");
        field.put("description", "");
        field.put("fieldType", "text");
        field.putObject("config");
        field.put("sortOrder", 10);
        field.put("status", "active");
        field.put("system", false);
        field.putArray("options");
        var canonical = canonicalizer.canonicalize(snapshot);
        UUID versionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_work_item_type_versions (
                    id, workspace_id, space_id, type_definition_id, version_number,
                    config_hash, status, config, created_by, created_at, published_by,
                    published_at, snapshot_schema_version
                ) values (?, ?, ?, ?, 2, ?, 'superseded', ?::jsonb, ?, now(), ?, now(), 1)
                """,
            versionId,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId(),
            canonical.configHash(),
            canonical.payload().toString(),
            fixture.user().id(),
            fixture.user().id()
        );
        return versionId;
    }

    private CurrentUser addIdentity(Fixture fixture, String role, boolean join) {
        UUID userId = UUID.randomUUID();
        String username = "s06m3_" + role + "_" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId,
            WORKSPACE_ID,
            username,
            username
        );
        if (join) {
            UUID memberId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                    insert into project_space_members (
                        id, workspace_id, space_id, user_id, status, joined_at,
                        created_by, created_at, updated_by, updated_at
                    ) values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                    """,
                memberId,
                WORKSPACE_ID,
                fixture.spaceId(),
                userId,
                fixture.user().id(),
                fixture.user().id()
            );
            jdbcTemplate.update(
                """
                    insert into project_space_role_assignments (
                        id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at
                    ) values (?, ?, ?, ?, ?, ?, now())
                    """,
                UUID.randomUUID(),
                WORKSPACE_ID,
                fixture.spaceId(),
                memberId,
                role,
                fixture.user().id()
            );
        }
        return new CurrentUser(
            userId,
            WORKSPACE_ID,
            UUID.randomUUID(),
            username,
            username,
            Set.of("enterprise_admin".equals(role) ? "admin" : "member"),
            Set.of()
        );
    }

    private Fixture fixture(String label, String role) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        ObjectNode snapshot = (ObjectNode) objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{
                "id":"%s",
                "workspaceId":"%s",
                "spaceId":"%s",
                "typeKey":"task",
                "name":"Task",
                "icon":"",
                "description":"",
                "sortOrder":0,
                "status":"active",
                "system":false
              },
              "fields":[],
              "layouts":[
                {"id":"%s","layoutKind":"create","status":"active","nodes":[],"policies":[]},
                {"id":"%s","layoutKind":"detail","status":"active","nodes":[],"policies":[]}
              ]
            }
            """.formatted(
                typeId,
                WORKSPACE_ID,
                spaceId,
                UUID.randomUUID(),
                UUID.randomUUID()
            ));
        var canonical = canonicalizer.canonicalize(snapshot);
        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId,
            WORKSPACE_ID,
            "s06m3_" + label + "_" + suffix,
            "S06 M3 " + label
        );
        jdbcTemplate.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, status, visibility, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', 'private', 0, ?, now(), ?, now())
                """,
            spaceId,
            WORKSPACE_ID,
            "s06m3_" + label + "_" + suffix,
            "S06 M3 " + label,
            userId,
            userId
        );
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_members (
                    id, workspace_id, space_id, user_id, status, joined_at,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId, WORKSPACE_ID, spaceId, userId, userId, userId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments (
                    id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at
                ) values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), WORKSPACE_ID, spaceId, memberId, role, userId
        );
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                """
                    insert into project_work_item_types (
                        id, workspace_id, space_id, type_key, name, icon, description,
                        sort_order, status, is_system, current_version_id, created_by,
                        created_at, updated_by, updated_at, aggregate_version
                    ) values (?, ?, ?, 'task', 'Task', '', '', 0, 'active', false, ?, ?, now(), ?, now(), 0)
                    """,
                typeId, WORKSPACE_ID, spaceId, versionId, userId, userId
            );
            jdbcTemplate.update(
                """
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, created_by, created_at, published_by,
                        published_at, snapshot_schema_version
                    ) values (?, ?, ?, ?, 1, ?, 'published', ?::jsonb, ?, now(), ?, now(), 1)
                    """,
                versionId,
                WORKSPACE_ID,
                spaceId,
                typeId,
                canonical.configHash(),
                canonical.payload().toString(),
                userId,
                userId
            );
        });
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_drafts (
                    id, workspace_id, space_id, type_definition_id, status,
                    snapshot_schema_version, config_hash, snapshot, diagnostics,
                    aggregate_version, lineage_kind, created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'valid', 1, ?, ?::jsonb, '[]'::jsonb, 0, 'live_edit', ?, now(), ?, now())
                """,
            draftId,
            WORKSPACE_ID,
            spaceId,
            typeId,
            canonical.configHash(),
            canonical.payload().toString(),
            userId,
            userId
        );
        CurrentUser user = new CurrentUser(
            userId,
            WORKSPACE_ID,
            UUID.randomUUID(),
            "s06m3_" + label + "_" + suffix,
            "S06 M3 " + label,
            Set.of("member"),
            Set.of()
        );
        return new Fixture(user, spaceId, typeId, versionId, draftId, suffix, canonical.payload());
    }

    private record Fixture(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID versionId,
        UUID draftId,
        String suffix,
        com.fasterxml.jackson.databind.JsonNode sourceSnapshot
    ) {
    }
}
