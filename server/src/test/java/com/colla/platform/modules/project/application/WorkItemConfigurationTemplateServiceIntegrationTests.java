package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.WorkItemConfigurationException;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class WorkItemConfigurationTemplateServiceIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WorkItemConfigurationTemplateService service;

    @Autowired
    private WorkItemConfigurationSnapshotCanonicalizer canonicalizer;

    @Autowired
    private WorkItemFieldConfigurationService fieldConfigurationService;

    @Autowired
    private WorkItemLayoutConfigurationService layoutConfigurationService;

    @MockitoSpyBean
    private WorkItemConfigurationAuthorStateHydrator authorStateHydrator;

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
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_field_definitions
                 where workspace_id=? and space_id=? and type_definition_id=?
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
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

    @Test
    void hydratesTemplateFieldsOptionsAndLayoutsBeforeLaterLiveEditingRefreshesTheDraft() throws Exception {
        Fixture fixture = fixture("hydrate", "owner");
        UUID sourceVersionId = insertHydrationSourceVersion(fixture);
        initializeLegacyLayouts(fixture);
        var template = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            sourceVersionId,
            "hydration_" + fixture.suffix(),
            "Hydration",
            "Hydration template"
        );

        String requestId = "template-hydrate-" + UUID.randomUUID();
        var installed = service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            template.currentVersion().id(),
            2,
            requestId
        );

        UUID fieldId = jdbcTemplate.queryForObject(
            """
                select id
                  from project_work_item_field_definitions
                 where workspace_id=? and space_id=? and type_definition_id=? and field_key='severity'
                """,
            UUID.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        );
        assertEquals(2, jdbcTemplate.queryForObject(
            "select count(*) from project_work_item_field_options where field_definition_id=?",
            Integer.class,
            fieldId
        ));
        assertEquals(4, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_layout_nodes
                 where workspace_id=? and space_id=? and type_definition_id=? and status='active'
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_layout_nodes
                 where workspace_id=? and space_id=? and type_definition_id=? and status='removed'
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
        assertEquals(fieldId.toString(), jdbcTemplate.queryForObject(
            """
                select snapshot->'fields'->0->>'id'
                  from project_work_item_configuration_drafts
                 where id=?
                """,
            String.class,
            fixture.draftId()
        ));

        long fieldVersion = jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_field_definitions where id=?",
            Long.class,
            fieldId
        );
        assertTrue(service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            template.currentVersion().id(),
            2,
            requestId
        ).replayed());
        assertEquals(fieldVersion, jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_field_definitions where id=?",
            Long.class,
            fieldId
        ));
        var repeated = service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            template.currentVersion().id(),
            installed.draft().aggregateVersion(),
            "template-hydrate-repeat-" + UUID.randomUUID()
        );
        assertEquals(installed.draft().aggregateVersion() + 1, repeated.draft().aggregateVersion());
        assertEquals(repeated.draft().aggregateVersion(), jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_configuration_drafts where id=?",
            Long.class,
            fixture.draftId()
        ));

        var liveField = fieldConfigurationService.detail(
            fixture.user(), fixture.spaceId(), fixture.typeId(), fieldId
        );
        fieldConfigurationService.update(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            fieldId,
            "Severity updated",
            "Edited after template installation",
            liveField.definition().config(),
            liveField.definition().aggregateVersion(),
            "template-live-edit-" + UUID.randomUUID()
        );

        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select jsonb_array_length(snapshot->'fields')
                  from project_work_item_configuration_drafts
                 where id=?
                """,
            Integer.class,
            fixture.draftId()
        ));
        assertEquals("Severity updated", jdbcTemplate.queryForObject(
            """
                select snapshot->'fields'->0->>'name'
                  from project_work_item_configuration_drafts
                 where id=?
                """,
            String.class,
            fixture.draftId()
        ));
        assertEquals(4, jdbcTemplate.queryForObject(
            """
                select (
                    select coalesce(sum(jsonb_array_length(layout->'nodes')), 0)
                      from jsonb_array_elements(snapshot->'layouts') layout
                )
                  from project_work_item_configuration_drafts
                 where id=?
                """,
            Integer.class,
            fixture.draftId()
        ));
        assertNotEquals(installed.draft().configHash(), jdbcTemplate.queryForObject(
            "select config_hash from project_work_item_configuration_drafts where id=?",
            String.class,
            fixture.draftId()
        ));
    }

    @Test
    void rollsBackTemplateInstallationWhenExistingAuthorStateCannotBeDiscarded() throws Exception {
        Fixture fixture = fixture("hydrate-conflict", "owner");
        fieldConfigurationService.create(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            "local_only",
            "Local only",
            "",
            "text",
            objectMapper.createObjectNode(),
            0,
            "template-local-field-" + UUID.randomUUID()
        );
        var template = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            insertHydrationSourceVersion(fixture),
            "conflict_" + fixture.suffix(),
            "Conflict",
            "Conflict template"
        );
        long draftVersion = jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_configuration_drafts where id=?",
            Long.class,
            fixture.draftId()
        );
        String draftHash = jdbcTemplate.queryForObject(
            "select config_hash from project_work_item_configuration_drafts where id=?",
            String.class,
            fixture.draftId()
        );

        WorkItemConfigurationException conflict = assertThrows(
            WorkItemConfigurationException.class,
            () -> service.install(
                fixture.user(),
                fixture.spaceId(),
                fixture.typeId(),
                template.id(),
                template.currentVersion().id(),
                draftVersion,
                "template-author-conflict-" + UUID.randomUUID()
            )
        );
        assertEquals("TEMPLATE_AUTHOR_STATE_CONFLICT", conflict.code());
        assertEquals(draftVersion, jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_configuration_drafts where id=?",
            Long.class,
            fixture.draftId()
        ));
        assertEquals(draftHash, jdbcTemplate.queryForObject(
            "select config_hash from project_work_item_configuration_drafts where id=?",
            String.class,
            fixture.draftId()
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_configuration_template_installations
                 where workspace_id=? and space_id=? and type_definition_id=?
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_field_definitions
                 where workspace_id=? and space_id=? and type_definition_id=? and field_key='local_only'
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
    }

    @Test
    void treatsEmptyTemplateFieldsNodesAndPoliciesAsNonDestructiveComponents() throws Exception {
        Fixture fixture = fixture("hydrate-empty", "owner");
        fieldConfigurationService.create(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            "local_field",
            "Local field",
            "",
            "text",
            objectMapper.createObjectNode(),
            0,
            "template-empty-local-field-" + UUID.randomUUID()
        );
        initializeLegacyLayouts(fixture);
        var template = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            fixture.versionId(),
            "empty_" + fixture.suffix(),
            "Empty components",
            "Empty components preserve local author state"
        );

        var installed = service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            template.currentVersion().id(),
            3,
            "template-empty-install-" + UUID.randomUUID()
        );

        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_field_definitions
                 where workspace_id=? and space_id=? and type_definition_id=? and field_key='local_field'
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_layout_nodes
                 where workspace_id=? and space_id=? and type_definition_id=? and status='active'
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select jsonb_array_length(snapshot->'fields') from project_work_item_configuration_drafts where id=?",
            Integer.class,
            fixture.draftId()
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select (
                    select coalesce(sum(jsonb_array_length(layout->'nodes')), 0)
                      from jsonb_array_elements(snapshot->'layouts') layout
                )
                  from project_work_item_configuration_drafts
                 where id=?
                """,
            Integer.class,
            fixture.draftId()
        ));
        assertEquals(installed.draft().configHash(), jdbcTemplate.queryForObject(
            "select config_hash from project_work_item_configuration_drafts where id=?",
            String.class,
            fixture.draftId()
        ));
    }

    @Test
    void acceptsOneLayoutTemplateAndPreservesTheMissingLayoutKind() throws Exception {
        Fixture fixture = fixture("hydrate-one-layout", "owner");
        initializeLegacyLayouts(fixture);
        UUID sourceVersionId = insertSingleLayoutSourceVersion(fixture);
        var template = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            sourceVersionId,
            "one_layout_" + fixture.suffix(),
            "One layout",
            "Only create layout has an opinion"
        );

        service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            template.currentVersion().id(),
            2,
            "template-one-layout-install-" + UUID.randomUUID()
        );

        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_layout_nodes node
                  join project_work_item_layouts layout on layout.id=node.layout_id
                 where node.workspace_id=? and node.space_id=? and node.type_definition_id=?
                   and layout.layout_kind='create' and node.node_key='create_replacement'
                   and node.status='active'
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_layout_nodes node
                  join project_work_item_layouts layout on layout.id=node.layout_id
                 where node.workspace_id=? and node.space_id=? and node.type_definition_id=?
                   and layout.layout_kind='detail' and node.node_key='detail_legacy'
                   and node.status='active'
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            "select jsonb_array_length(snapshot->'layouts') from project_work_item_configuration_drafts where id=?",
            Integer.class,
            fixture.draftId()
        ));
    }

    @Test
    void reportsStableConflictForDisabledLayoutInsteadOfLeakingUniqueConstraint() throws Exception {
        Fixture fixture = fixture("hydrate-disabled", "owner");
        initializeLegacyLayouts(fixture);
        jdbcTemplate.update(
            """
                update project_work_item_layouts
                   set status='disabled', updated_by=?, updated_at=now(),
                       aggregate_version=aggregate_version+1
                 where workspace_id=? and space_id=? and type_definition_id=? and layout_kind='create'
                """,
            fixture.user().id(),
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        );
        var template = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            fixture.versionId(),
            "disabled_" + fixture.suffix(),
            "Disabled layout",
            "Disabled layout conflict"
        );

        WorkItemConfigurationException conflict = assertThrows(
            WorkItemConfigurationException.class,
            () -> service.install(
                fixture.user(),
                fixture.spaceId(),
                fixture.typeId(),
                template.id(),
                template.currentVersion().id(),
                2,
                "template-disabled-layout-" + UUID.randomUUID()
            )
        );
        assertEquals("TEMPLATE_AUTHOR_STATE_CONFLICT", conflict.code());
        assertEquals(2, jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_configuration_drafts where id=?",
            Long.class,
            fixture.draftId()
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_configuration_template_installations
                 where workspace_id=? and space_id=? and type_definition_id=?
                """,
            Integer.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
    }

    @Test
    void templateInstallAndOrdinaryFieldEditShareAuthorStateBeforeDraftLockOrder() throws Exception {
        Fixture fixture = fixture("hydrate-lock-order", "owner");
        UUID sourceVersionId = insertHydrationSourceVersion(fixture);
        var template = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            sourceVersionId,
            "lock_order_" + fixture.suffix(),
            "Lock order",
            "Template and ordinary editing lock order"
        );
        var installed = service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            template.currentVersion().id(),
            0,
            "template-lock-order-initial-" + UUID.randomUUID()
        );
        UUID fieldId = jdbcTemplate.queryForObject(
            """
                select id
                  from project_work_item_field_definitions
                 where workspace_id=? and space_id=? and type_definition_id=? and field_key='severity'
                """,
            UUID.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        );
        var field = fieldConfigurationService.detail(
            fixture.user(), fixture.spaceId(), fixture.typeId(), fieldId
        ).definition();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var templateFuture = executor.submit(() -> {
                start.await();
                try {
                    service.install(
                        fixture.user(),
                        fixture.spaceId(),
                        fixture.typeId(),
                        template.id(),
                        template.currentVersion().id(),
                        installed.draft().aggregateVersion(),
                        "template-lock-order-repeat-" + UUID.randomUUID()
                    );
                    return "success";
                } catch (WorkItemConfigurationException exception) {
                    return "configuration:" + exception.code();
                } catch (RuntimeException exception) {
                    return "raw:" + exception.getClass().getName();
                }
            });
            var fieldFuture = executor.submit(() -> {
                start.await();
                try {
                    fieldConfigurationService.update(
                        fixture.user(),
                        fixture.spaceId(),
                        fixture.typeId(),
                        fieldId,
                        "Severity concurrently edited",
                        "",
                        field.config(),
                        field.aggregateVersion(),
                        "template-lock-order-field-" + UUID.randomUUID()
                    );
                    return "success";
                } catch (WorkItemFieldException exception) {
                    return "field:" + exception.code();
                } catch (RuntimeException exception) {
                    return "raw:" + exception.getClass().getName();
                }
            });
            start.countDown();
            String templateOutcome = templateFuture.get(15, TimeUnit.SECONDS);
            String fieldOutcome = fieldFuture.get(15, TimeUnit.SECONDS);
            assertEquals(1, java.util.stream.Stream.of(templateOutcome, fieldOutcome)
                .filter("success"::equals)
                .count());
            assertFalse(templateOutcome.startsWith("raw:"), templateOutcome);
            assertFalse(fieldOutcome.startsWith("raw:"), fieldOutcome);
            assertTrue(
                java.util.stream.Stream.of(templateOutcome, fieldOutcome)
                    .filter(outcome -> !"success".equals(outcome))
                    .allMatch(outcome ->
                        outcome.endsWith("DRAFT_VERSION_CONFLICT")
                            || outcome.endsWith("TEMPLATE_AUTHOR_STATE_VERSION_CONFLICT")
                            || outcome.endsWith("FIELD_VERSION_CONFLICT")
                    ),
                templateOutcome + ", " + fieldOutcome
            );
        } finally {
            executor.shutdownNow();
        }
        assertEquals(
            jdbcTemplate.queryForObject(
                "select name from project_work_item_field_definitions where id=?",
                String.class,
                fieldId
            ),
            jdbcTemplate.queryForObject(
                """
                    select field->>'name'
                      from project_work_item_configuration_drafts draft,
                           jsonb_array_elements(draft.snapshot->'fields') field
                     where draft.id=? and field->>'fieldKey'='severity'
                    """,
                String.class,
                fixture.draftId()
            )
        );
    }

    @Test
    void installsNonEmptyTemplateAcrossSpacesWithTargetBoundPermanentIds() throws Exception {
        Fixture source = fixture("hydrate-cross-space-source", "owner");
        Fixture target = fixture("hydrate-cross-space-target", "owner");
        UUID sourceVersionId = insertHydrationSourceVersion(source);
        JsonNode sourceSnapshot = objectMapper.readTree(jdbcTemplate.queryForObject(
            "select config::text from project_work_item_type_versions where id=?",
            String.class,
            sourceVersionId
        ));
        var template = service.createWorkspaceTemplate(
            source.user(),
            source.spaceId(),
            source.typeId(),
            sourceVersionId,
            "cross_space_" + source.suffix(),
            "Cross-space",
            "Cross-space non-empty author state"
        );

        var installed = service.install(
            target.user(),
            target.spaceId(),
            target.typeId(),
            template.id(),
            template.currentVersion().id(),
            0,
            "template-cross-space-install-" + UUID.randomUUID()
        );

        JsonNode targetSnapshot = objectMapper.readTree(jdbcTemplate.queryForObject(
            "select snapshot::text from project_work_item_configuration_drafts where id=?",
            String.class,
            target.draftId()
        ));
        Set<String> sourceIds = authorObjectIds(sourceSnapshot);
        Set<String> targetIds = authorObjectIds(targetSnapshot);
        assertFalse(sourceIds.isEmpty());
        assertEquals(sourceIds.size(), targetIds.size());
        assertTrue(java.util.Collections.disjoint(sourceIds, targetIds));
        assertEquals(target.typeId().toString(), targetSnapshot.path("typeDefinition").path("id").asText());
        assertEquals(installed.draft().configHash(), jdbcTemplate.queryForObject(
            "select config_hash from project_work_item_configuration_drafts where id=?",
            String.class,
            target.draftId()
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_field_definitions
                 where workspace_id=? and space_id=? and type_definition_id=? and field_key='severity'
                """,
            Integer.class,
            WORKSPACE_ID,
            target.spaceId(),
            target.typeId()
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_work_item_layouts
                 where workspace_id=? and space_id=? and type_definition_id=? and status='active'
                """,
            Integer.class,
            WORKSPACE_ID,
            target.spaceId(),
            target.typeId()
        ));
    }

    @Test
    void installAndUpgradeAcquireAuthorStateBeforeDraftAndInstallation() throws Exception {
        Fixture fixture = fixture("hydrate-template-lock-order", "owner");
        UUID firstSourceVersionId = insertHydrationSourceVersion(fixture, 2, "Severity");
        var template = service.createWorkspaceTemplate(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            firstSourceVersionId,
            "template_lock_order_" + fixture.suffix(),
            "Template lock order",
            "Install and upgrade use one lock order"
        );
        var installed = service.install(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            template.currentVersion().id(),
            0,
            "template-lock-order-base-" + UUID.randomUUID()
        );
        UUID nextSourceVersionId = insertHydrationSourceVersion(fixture, 3, "Severity next");
        var updatedTemplate = service.addWorkspaceTemplateVersion(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            template.id(),
            nextSourceVersionId
        );

        CountDownLatch installHydrated = new CountDownLatch(1);
        CountDownLatch upgradeEnteredHydrator = new CountDownLatch(1);
        WorkItemConfigurationAuthorStateHydrator hydratorTarget =
            AopTestUtils.getUltimateTargetObject(authorStateHydrator);
        doAnswer(invocation -> {
            String threadName = Thread.currentThread().getName();
            if ("template-install-lock-order".equals(threadName)) {
                Object result = invocation.callRealMethod();
                installHydrated.countDown();
                if (!upgradeEnteredHydrator.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Upgrade did not reach author-state hydration");
                }
                return result;
            }
            if ("template-upgrade-lock-order".equals(threadName)) {
                upgradeEnteredHydrator.countDown();
            }
            return invocation.callRealMethod();
        }).when(hydratorTarget).hydrate(any(), any(), any(), any(), any());

        var installExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "template-install-lock-order")
        );
        var upgradeExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "template-upgrade-lock-order")
        );
        try {
            var installFuture = installExecutor.submit(() -> {
                try {
                    service.install(
                        fixture.user(),
                        fixture.spaceId(),
                        fixture.typeId(),
                        template.id(),
                        template.currentVersion().id(),
                        installed.draft().aggregateVersion(),
                        "template-lock-order-repeat-" + UUID.randomUUID()
                    );
                    return "success";
                } catch (WorkItemConfigurationException exception) {
                    return "configuration:" + exception.code();
                } catch (RuntimeException exception) {
                    return "raw:" + exception.getClass().getName();
                }
            });
            assertTrue(installHydrated.await(10, TimeUnit.SECONDS));
            var upgradeFuture = upgradeExecutor.submit(() -> {
                try {
                    service.applyUpgrade(
                        fixture.user(),
                        fixture.spaceId(),
                        fixture.typeId(),
                        updatedTemplate.currentVersion().id(),
                        installed.draft().aggregateVersion(),
                        installed.installation().aggregateVersion(),
                        java.util.Map.of(),
                        "template-lock-order-upgrade-" + UUID.randomUUID()
                    );
                    return "success";
                } catch (WorkItemConfigurationException exception) {
                    return "configuration:" + exception.code();
                } catch (RuntimeException exception) {
                    return "raw:" + exception.getClass().getName();
                }
            });

            String installOutcome = installFuture.get(15, TimeUnit.SECONDS);
            String upgradeOutcome = upgradeFuture.get(15, TimeUnit.SECONDS);
            assertEquals("success", installOutcome);
            assertTrue(
                Set.of(
                    "configuration:DRAFT_VERSION_CONFLICT",
                    "configuration:TEMPLATE_AUTHOR_STATE_VERSION_CONFLICT",
                    "configuration:TEMPLATE_INSTALLATION_VERSION_CONFLICT"
                ).contains(upgradeOutcome),
                upgradeOutcome
            );
            assertFalse(upgradeOutcome.startsWith("raw:"), upgradeOutcome);
        } finally {
            installExecutor.shutdownNow();
            upgradeExecutor.shutdownNow();
        }

        assertEquals(
            jdbcTemplate.queryForObject(
                "select name from project_work_item_field_definitions where workspace_id=? and space_id=? "
                    + "and type_definition_id=? and field_key='severity'",
                String.class,
                WORKSPACE_ID,
                fixture.spaceId(),
                fixture.typeId()
            ),
            jdbcTemplate.queryForObject(
                """
                    select field->>'name'
                      from project_work_item_configuration_drafts draft,
                           jsonb_array_elements(draft.snapshot->'fields') field
                     where draft.id=? and field->>'fieldKey'='severity'
                    """,
                String.class,
                fixture.draftId()
            )
        );
        assertEquals(template.currentVersion().id(), jdbcTemplate.queryForObject(
            """
                select upstream_version_id
                  from project_work_item_configuration_template_installations
                 where workspace_id=? and space_id=? and type_definition_id=?
                """,
            UUID.class,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId()
        ));
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

    private UUID insertHydrationSourceVersion(Fixture fixture) {
        return insertHydrationSourceVersion(fixture, 2, "Severity");
    }

    private UUID insertHydrationSourceVersion(Fixture fixture, int versionNumber, String fieldName) {
        ObjectNode snapshot = (ObjectNode) fixture.sourceSnapshot().deepCopy();
        ObjectNode field = snapshot.withArray("fields").addObject();
        field.put("id", UUID.randomUUID().toString());
        field.put("fieldKey", "severity");
        field.put("name", fieldName);
        field.put("description", "Requirement severity");
        field.put("fieldType", "single_select");
        ObjectNode config = field.putObject("config");
        config.put("schemaVersion", 1);
        config.put("required", false);
        config.putNull("defaultValue");
        config.putArray("validationRules");
        config.putObject("typeConfig");
        field.put("sortOrder", 10);
        field.put("status", "active");
        field.put("system", false);
        var options = field.putArray("options");
        ObjectNode high = options.addObject();
        high.put("id", UUID.randomUUID().toString());
        high.put("optionKey", "high");
        high.put("name", "High");
        high.put("color", "#FF4D4F");
        high.put("sortOrder", 10);
        high.put("status", "active");
        ObjectNode normal = options.addObject();
        normal.put("id", UUID.randomUUID().toString());
        normal.put("optionKey", "normal");
        normal.put("name", "Normal");
        normal.put("color", "#1677FF");
        normal.put("sortOrder", 20);
        normal.put("status", "active");

        for (JsonNode layout : snapshot.withArray("layouts")) {
            String kind = layout.path("layoutKind").asText();
            ObjectNode section = ((ObjectNode) layout).withArray("nodes").addObject();
            section.put("id", UUID.randomUUID().toString());
            section.put("nodeKey", kind + "_main");
            section.put("nodeType", "section");
            section.putNull("parentKey");
            section.putNull("fieldKey");
            section.put("sortOrder", 0);
            section.putObject("config");
            section.putObject("visibilityCondition").put("schemaVersion", 1);
            ObjectNode fieldNode = ((ObjectNode) layout).withArray("nodes").addObject();
            fieldNode.put("id", UUID.randomUUID().toString());
            fieldNode.put("nodeKey", kind + "_severity");
            fieldNode.put("nodeType", "field");
            fieldNode.put("parentKey", kind + "_main");
            fieldNode.put("fieldKey", "severity");
            fieldNode.put("sortOrder", 0);
            fieldNode.putObject("config");
            fieldNode.putObject("visibilityCondition").put("schemaVersion", 1);
        }
        var canonical = canonicalizer.canonicalize(snapshot);
        UUID versionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_work_item_type_versions (
                    id, workspace_id, space_id, type_definition_id, version_number,
                    config_hash, status, config, created_by, created_at, published_by,
                    published_at, snapshot_schema_version
                ) values (?, ?, ?, ?, ?, ?, 'superseded', ?::jsonb, ?, now(), ?, now(), 1)
                """,
            versionId,
            WORKSPACE_ID,
            fixture.spaceId(),
            fixture.typeId(),
            versionNumber,
            canonical.configHash(),
            canonical.payload().toString(),
            fixture.user().id(),
            fixture.user().id()
        );
        return versionId;
    }

    private Set<String> authorObjectIds(JsonNode snapshot) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (JsonNode field : snapshot.path("fields")) {
            result.add(field.path("id").asText());
            for (JsonNode option : field.path("options")) {
                result.add(option.path("id").asText());
            }
        }
        for (JsonNode layout : snapshot.path("layouts")) {
            result.add(layout.path("id").asText());
            for (JsonNode node : layout.path("nodes")) {
                result.add(node.path("id").asText());
            }
            for (JsonNode policy : layout.path("policies")) {
                result.add(policy.path("id").asText());
            }
        }
        result.remove("");
        return Set.copyOf(result);
    }

    private UUID insertSingleLayoutSourceVersion(Fixture fixture) {
        ObjectNode snapshot = (ObjectNode) fixture.sourceSnapshot().deepCopy();
        var layouts = snapshot.putArray("layouts");
        ObjectNode create = layouts.addObject();
        create.put("id", UUID.randomUUID().toString());
        create.put("layoutKind", "create");
        create.put("status", "active");
        ObjectNode replacement = create.putArray("nodes").addObject();
        replacement.put("id", UUID.randomUUID().toString());
        replacement.put("nodeKey", "create_replacement");
        replacement.put("nodeType", "section");
        replacement.putNull("parentKey");
        replacement.putNull("fieldKey");
        replacement.put("sortOrder", 0);
        replacement.putObject("config");
        replacement.putObject("visibilityCondition").put("schemaVersion", 1);
        create.putArray("policies");
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

    private void initializeLegacyLayouts(Fixture fixture) {
        for (String kind : java.util.List.of("create", "detail")) {
            ObjectNode visibility = objectMapper.createObjectNode();
            visibility.put("schemaVersion", 1);
            layoutConfigurationService.save(
                fixture.user(),
                fixture.spaceId(),
                fixture.typeId(),
                kind,
                java.util.List.of(new LayoutNode(
                    UUID.randomUUID(),
                    null,
                    kind + "_legacy",
                    "section",
                    null,
                    null,
                    0,
                    objectMapper.createObjectNode(),
                    visibility
                )),
                java.util.List.of(),
                0,
                "template-legacy-layout-" + kind + "-" + UUID.randomUUID()
            );
        }
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
