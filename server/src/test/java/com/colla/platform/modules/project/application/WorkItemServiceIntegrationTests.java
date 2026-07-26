package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.ReadStage;
import com.colla.platform.modules.project.application.WorkItemCompatibilityService.LegacyWriteClosedException;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class WorkItemServiceIntegrationTests {
    private static final UUID WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WorkItemService service;

    @Autowired
    private WorkItemCompatibilityService compatibilityService;

    @Autowired
    private WorkItemConfigurationSnapshotCanonicalizer snapshotCanonicalizer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void createBindsSnapshotAppliesDefaultsAndReplaysTheExactReceipt() throws Exception {
        Fixture fixture = fixture("create");
        JsonNode values = objectMapper.readTree("""
            {"secret":"visible-to-member"}
            """);

        WorkItemView created = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "First item",
            values,
            "create-exact"
        );
        WorkItemView replayed = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "First item",
            values,
            "create-exact"
        );

        assertThat(replayed).isEqualTo(created);
        assertThat(created.item().typeVersionId()).isEqualTo(fixture.versionId());
        assertThat(created.item().configHash()).isEqualTo(fixture.configHash());
        assertThat(created.item().displayKey()).isEqualTo("TASK-1");
        assertThat(created.fieldValues().path("priority").asText()).isEqualTo("normal");
        assertThat(created.fieldValues().path("secret").asText()).isEqualTo("visible-to-member");
        assertThat(created.availableActions()).containsExactly("view", "edit", "archive");
        assertThat(count("project_work_items", fixture.spaceId())).isEqualTo(1);
        assertThat(count("project_work_item_commands", fixture.spaceId())).isEqualTo(1);
        assertThat(countWhere(
            "audit_logs",
            "workspace_id=? and target_type='work_item' and target_id=?",
            WORKSPACE_ID,
            created.item().id()
        )).isEqualTo(1);
        assertThat(countWhere(
            "domain_events",
            "workspace_id=? and event_type='work_item.changed' and aggregate_id=?",
            WORKSPACE_ID,
            created.item().id()
        )).isEqualTo(1);
        assertThat(countWhere(
            "object_links",
            "workspace_id=? and object_type='work_item' and object_id=? and deleted_at is null",
            WORKSPACE_ID,
            created.item().id()
        )).isEqualTo(1);
    }

    @Test
    void updateArchiveAndRestoreHonorOptimisticVersionAndRollbackFailedReceipts() throws Exception {
        Fixture fixture = fixture("lifecycle");
        WorkItemView created = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Lifecycle",
            objectMapper.readTree("{}"),
            "lifecycle-create"
        );

        assertThatThrownBy(() -> service.update(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            "Stale",
            objectMapper.readTree("{\"priority\":\"high\"}"),
            9,
            "lifecycle-stale"
        )).isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("WORK_ITEM_VERSION_CONFLICT");
        assertThat(countWhere(
            "project_work_item_commands",
            "workspace_id=? and operation='update' and request_id='lifecycle-stale'",
            WORKSPACE_ID
        )).isZero();

        WorkItemView updated = service.update(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            "Updated",
            objectMapper.readTree("{\"priority\":\"high\"}"),
            0,
            "lifecycle-update"
        );
        WorkItemView archived = service.transition(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            "archived",
            1,
            "lifecycle-archive"
        );
        WorkItemView restored = service.transition(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            "active",
            2,
            "lifecycle-restore"
        );

        assertThat(updated.item().version()).isEqualTo(1);
        assertThat(archived.item().status()).isEqualTo("archived");
        assertThat(archived.availableActions()).containsExactly("view", "restore");
        assertThat(restored.item().status()).isEqualTo("active");
        assertThat(restored.item().version()).isEqualTo(3);
        assertThat(countWhere(
            "domain_events",
            "workspace_id=? and event_type='work_item.changed' and aggregate_id=?",
            WORKSPACE_ID,
            created.item().id()
        )).isEqualTo(4);
    }

    @Test
    void runtimeFormsCommentsAndAttachmentsRemainVersionedAndReplaySafe() throws Exception {
        Fixture fixture = fixture("collaboration");
        var form = service.createForm(fixture.owner(), fixture.spaceId(), fixture.typeId());
        assertThat(form.typeVersionId()).isEqualTo(fixture.versionId());
        assertThat(form.runtime().path("configHash").asText()).isEqualTo(fixture.configHash());
        assertThat(form.runtime().path("layoutKind").asText()).isEqualTo("create");
        assertThat(form.runtime().path("accessProjection").path("priority").path("mode").asText())
            .isEqualTo("write");

        WorkItemView created = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Collaborative item",
            objectMapper.readTree("{}"),
            "collaboration-create"
        );
        assertThat(created.runtime().path("typeVersionId").asText())
            .isEqualTo(fixture.versionId().toString());
        assertThat(created.runtime().path("layoutKind").asText()).isEqualTo("detail");

        var commented = service.addComment(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            "First canonical comment",
            0,
            "comment-exact"
        );
        var replayedComment = service.addComment(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            "First canonical comment",
            0,
            "comment-exact"
        );
        assertThat(replayedComment).isEqualTo(commented);
        assertThat(commented.workItemVersion()).isEqualTo(1);
        assertThat(commented.comments()).singleElement()
            .satisfies(comment -> {
                assertThat(comment.content()).isEqualTo("First canonical comment");
                assertThat(comment.authorDisplayName()).isEqualTo(fixture.owner().displayName());
            });

        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into files(
                  id,workspace_id,object_key,original_name,content_type,size_bytes,
                  status,uploaded_by,created_at,completed_at
                ) values (?,?,?,?,?,?,'completed',?,now(),now())
                """,
            fileId,
            WORKSPACE_ID,
            WORKSPACE_ID + "/" + fileId + "/acceptance.txt",
            "acceptance.txt",
            "text/plain",
            17,
            fixture.owner().id()
        );
        var attached = service.addAttachment(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            fileId,
            1,
            "attachment-exact"
        );
        var replayedAttachment = service.addAttachment(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            fileId,
            1,
            "attachment-exact"
        );
        assertThat(replayedAttachment).isEqualTo(attached);
        assertThat(attached.workItemVersion()).isEqualTo(2);
        assertThat(attached.attachments()).singleElement()
            .satisfies(value -> {
                assertThat(value.fileName()).isEqualTo("acceptance.txt");
                assertThat(value.sizeBytes()).isEqualTo(17);
            });
        assertThat(count("project_work_item_comments", fixture.spaceId())).isEqualTo(1);
        assertThat(count("project_work_item_attachments", fixture.spaceId())).isEqualTo(1);
        assertThat(countWhere(
            "file_usages",
            "workspace_id=? and file_id=? and target_type='work_item' and target_id=?",
            WORKSPACE_ID,
            fileId,
            created.item().id()
        )).isEqualTo(1);
        assertThat(countWhere(
            "project_work_item_commands",
            "workspace_id=? and operation in ('comment_add','attachment_add')",
            WORKSPACE_ID
        )).isEqualTo(2);
        assertThat(service.listComments(fixture.owner(), fixture.spaceId(), created.item().id()))
            .hasSize(1);
        assertThat(service.listAttachments(fixture.owner(), fixture.spaceId(), created.item().id()))
            .hasSize(1);
    }

    @Test
    void guestReadsHideProtectedFieldsAndCannotWrite() throws Exception {
        Fixture fixture = fixture("guest");
        CurrentUser guest = addMember(fixture, "guest");
        WorkItemView created = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Protected",
            objectMapper.readTree("{\"secret\":\"do-not-disclose\"}"),
            "guest-create"
        );

        WorkItemView visible = service.get(guest, fixture.spaceId(), created.item().id());

        assertThat(visible.fieldValues().has("secret")).isFalse();
        assertThat(visible.fieldValues().path("priority").asText()).isEqualTo("normal");
        assertThat(visible.runtime().path("accessProjection").has("secret")).isFalse();
        assertThat(visible.runtime().path("snapshot").path("fields").toString())
            .doesNotContain("secret");
        assertThat(visible.runtime().path("snapshot").path("layouts").toString())
            .doesNotContain("secret");
        assertThat(visible.availableActions()).containsExactly("view");
        assertThatThrownBy(() -> service.create(
            guest,
            fixture.spaceId(),
            fixture.typeId(),
            "Forbidden",
            objectMapper.readTree("{}"),
            "guest-forbidden"
        )).isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("FORBIDDEN");
        assertThat(countWhere(
            "project_work_item_commands",
            "workspace_id=? and request_id='guest-forbidden'",
            WORKSPACE_ID
        )).isZero();
    }

    @Test
    void compositeScopeAndMembershipPreventCrossSpaceEnumeration() throws Exception {
        Fixture first = fixture("scope-one");
        Fixture second = fixture("scope-two");
        WorkItemView created = service.create(
            first.owner(),
            first.spaceId(),
            first.typeId(),
            "Scoped",
            objectMapper.readTree("{}"),
            "scope-create"
        );

        assertThatThrownBy(() -> service.get(
            second.owner(),
            second.spaceId(),
            created.item().id()
        )).isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("NOT_FOUND_OR_HIDDEN");
        assertThatThrownBy(() -> service.get(
            second.owner(),
            first.spaceId(),
            created.item().id()
        )).isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("NOT_FOUND_OR_HIDDEN");
        assertThat(service.list(first.owner(), first.spaceId(), null, null, 10).items())
            .extracting(view -> view.item().id())
            .containsExactly(created.item().id());
        assertThat(service.list(second.owner(), second.spaceId(), null, null, 10).items())
            .isEmpty();
    }

    @Test
    void reusedRequestIdWithDifferentPayloadIsRejectedWithoutExtraSideEffects() throws Exception {
        Fixture fixture = fixture("idempotency");
        service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Original",
            objectMapper.readTree("{}"),
            "same-key"
        );

        assertThatThrownBy(() -> service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Changed",
            objectMapper.readTree("{}"),
            "same-key"
        )).isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(count("project_work_items", fixture.spaceId())).isEqualTo(1);
        assertThat(count("project_work_item_commands", fixture.spaceId())).isEqualTo(1);
    }

    @Test
    void sixIdentityMatrixUsesSpaceMembershipInsteadOfEnterpriseRoleShortcuts() throws Exception {
        Fixture fixture = fixture("identities");
        CurrentUser admin = addMember(fixture, "admin");
        CurrentUser member = addMember(fixture, "member");
        CurrentUser guest = addMember(fixture, "guest");
        Fixture outsideFixture = fixture("outside");
        CurrentUser enterpriseAdmin = new CurrentUser(
            outsideFixture.owner().id(),
            WORKSPACE_ID,
            UUID.randomUUID(),
            outsideFixture.owner().username(),
            outsideFixture.owner().displayName(),
            Set.of("admin"),
            Set.of("project.manage")
        );
        WorkItemView created = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Identity matrix",
            objectMapper.readTree("{}"),
            "identity-create"
        );

        assertThat(service.get(fixture.owner(), fixture.spaceId(), created.item().id()).availableActions())
            .contains("edit", "archive");
        assertThat(service.get(admin, fixture.spaceId(), created.item().id()).availableActions())
            .contains("edit", "archive");
        assertThat(service.get(member, fixture.spaceId(), created.item().id()).availableActions())
            .contains("edit", "archive");
        assertThat(service.get(guest, fixture.spaceId(), created.item().id()).availableActions())
            .containsExactly("view");
        assertHidden(() -> service.get(
            outsideFixture.owner(),
            fixture.spaceId(),
            created.item().id()
        ));
        assertHidden(() -> service.get(
            enterpriseAdmin,
            fixture.spaceId(),
            created.item().id()
        ));
    }

    @Test
    void projectionsParticipantsActivitiesAndQueryRemainTransactionallyAligned() throws Exception {
        Fixture fixture = fixture("m2-runtime");
        CurrentUser assignee = addMember(fixture, "member");
        WorkItemView created = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Projected",
            objectMapper.readTree("{\"priority\":\"normal\",\"secret\":\"classified\"}"),
            "m2-create"
        );

        assertThat(countWhere(
            "project_work_item_field_projections",
            "workspace_id=? and space_id=? and work_item_id=?",
            WORKSPACE_ID,
            fixture.spaceId(),
            created.item().id()
        )).isEqualTo(2);
        assertThat(service.listParticipants(
            fixture.owner(), fixture.spaceId(), created.item().id()
        )).singleElement().satisfies(participant -> {
            assertThat(participant.userId()).isEqualTo(fixture.owner().id());
            assertThat(participant.role()).isEqualTo("owner");
        });
        assertThat(service.listActivities(
            fixture.owner(), fixture.spaceId(), created.item().id(), null, 20
        ).items()).extracting(activity -> activity.type()).containsExactly("created");

        var assigned = service.changeParticipant(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            assignee.id(),
            "assignee",
            false,
            0,
            "m2-assignee"
        );
        var replayed = service.changeParticipant(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            assignee.id(),
            "assignee",
            false,
            0,
            "m2-assignee"
        );
        assertThat(replayed).isEqualTo(assigned);
        assertThat(assigned.workItemVersion()).isEqualTo(1);
        assertThat(assigned.participants()).extracting(participant -> participant.role())
            .containsExactly("owner", "assignee");

        var ownerRemoved = service.changeParticipant(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            fixture.owner().id(),
            "owner",
            true,
            1,
            "m2-owner-remove"
        );
        assertThat(ownerRemoved.workItemVersion()).isEqualTo(2);
        assertThatThrownBy(() -> service.changeParticipant(
            fixture.owner(),
            fixture.spaceId(),
            created.item().id(),
            assignee.id(),
            "assignee",
            true,
            2,
            "m2-last-responsible"
        )).isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("LAST_RESPONSIBLE_PARTICIPANT");
        assertThat(countWhere(
            "project_work_item_commands",
            "workspace_id=? and request_id='m2-last-responsible'",
            WORKSPACE_ID
        )).isZero();

        assertThat(service.query(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "priority",
            "eq",
            objectMapper.readTree("\"normal\""),
            "desc",
            20
        ).items()).extracting(view -> view.item().id()).containsExactly(created.item().id());
        assertThatThrownBy(() -> service.query(
            addMember(fixture, "guest"),
            fixture.spaceId(),
            fixture.typeId(),
            "secret",
            "eq",
            objectMapper.readTree("\"classified\""),
            "desc",
            20
        )).isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("NOT_FOUND_OR_HIDDEN");

        assertThat(service.listActivities(
            fixture.owner(), fixture.spaceId(), created.item().id(), null, 20
        ).items()).extracting(activity -> activity.type())
            .containsExactly("participant_removed", "participant_changed", "created");
        assertThat(jdbcTemplate.queryForList(
            """
                select payload::text from domain_events
                 where workspace_id=? and event_type='work_item.changed' and aggregate_id=?
                """,
            String.class,
            WORKSPACE_ID,
            created.item().id()
        )).allSatisfy(payload -> assertThat(payload)
            .doesNotContain("classified", "Projected", "fieldValues", "participants"));
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update project_work_item_activities set activity_type='forged' where work_item_id=?",
            created.item().id()
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void projectionRebuildRepairsDerivedRowsWithoutChangingAuthority() throws Exception {
        Fixture fixture = fixture("m2-rebuild");
        WorkItemView created = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Rebuild",
            objectMapper.readTree("{\"priority\":\"normal\"}"),
            "m2-rebuild-create"
        );
        jdbcTemplate.update(
            """
                delete from project_work_item_field_projections
                 where workspace_id=? and space_id=? and work_item_id=?
                """,
            WORKSPACE_ID,
            fixture.spaceId(),
            created.item().id()
        );

        assertThat(service.rebuildFieldProjections(
            fixture.owner(), fixture.spaceId(), created.item().id()
        )).isEqualTo(1);
        assertThat(service.get(
            fixture.owner(), fixture.spaceId(), created.item().id()
        ).fieldValues()).isEqualTo(created.fieldValues());
        assertThat(countWhere(
            "project_work_item_field_projections",
            "workspace_id=? and space_id=? and work_item_id=?",
            WORKSPACE_ID,
            fixture.spaceId(),
            created.item().id()
        )).isEqualTo(1);
    }

    @Test
    void representativeProjectionQueryUsesTheBoundedTypedIndex() throws Exception {
        Fixture fixture = fixture("m2-plan");
        service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Plan anchor",
            objectMapper.readTree("{\"priority\":\"normal\"}"),
            "m2-plan-create"
        );
        for (int index = 0; index < 250; index++) {
            UUID itemId = UUID.randomUUID();
            long itemNumber = index + 2L;
            jdbcTemplate.update(
                """
                    insert into project_work_items (
                        id, workspace_id, space_id, type_definition_id, type_version_id,
                        config_hash, item_number, display_key, title, field_values, status,
                        version, created_by, created_at, updated_by, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, '{"priority":"normal"}'::jsonb,
                              'active', 0, ?, now(), ?, now())
                    """,
                itemId,
                WORKSPACE_ID,
                fixture.spaceId(),
                fixture.typeId(),
                fixture.versionId(),
                fixture.configHash(),
                itemNumber,
                "TASK-" + itemNumber,
                "Plan row " + index,
                fixture.owner().id(),
                fixture.owner().id()
            );
            jdbcTemplate.update(
                """
                    insert into project_work_item_field_projections (
                        workspace_id, space_id, work_item_id, field_key, field_type,
                        config_hash, canonical_hash, canonical_value, text_value,
                        filterable, sortable, index_capability, updated_at
                    ) values (?, ?, ?, 'priority', 'text', ?, ?, '"normal"'::jsonb, 'normal',
                              true, true, 'text', now())
                    """,
                WORKSPACE_ID,
                fixture.spaceId(),
                itemId,
                fixture.configHash(),
                "b".repeat(64)
            );
        }

        String plan = transactionTemplate.execute(status -> {
            jdbcTemplate.execute("set local enable_seqscan = off");
            return String.join("\n", jdbcTemplate.query(
                """
                    explain (analyze, buffers, format text)
                    select wi.id
                      from project_work_items wi
                      join project_work_item_field_projections fp
                        on fp.workspace_id = wi.workspace_id
                       and fp.space_id = wi.space_id
                       and fp.work_item_id = wi.id
                     where wi.workspace_id = ? and wi.space_id = ? and wi.type_definition_id = ?
                       and fp.field_key = 'priority' and fp.filterable = true
                       and fp.text_value = 'normal' and fp.config_hash = ?
                     order by fp.text_value desc nulls last, wi.id desc
                     limit 50
                    """,
                (resultSet, rowNumber) -> resultSet.getString(1),
                WORKSPACE_ID,
                fixture.spaceId(),
                fixture.typeId(),
                fixture.configHash()
            ));
        });

        assertThat(plan).contains("idx_project_work_item_field_projection_text");
        assertThat(service.query(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "priority",
            "eq",
            objectMapper.readTree("\"normal\""),
            "desc",
            50
        ).items()).hasSize(50);
    }

    @Test
    void compatibilityRoutingUsesExplicitMapShadowEvidenceAndKillSwitch() throws Exception {
        Fixture fixture = fixture("compat");
        WorkItemView canonical = service.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Canonical title",
            objectMapper.readTree("{\"priority\":\"high\"}"),
            "compat-create"
        );
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        insertLegacyIssueFixture(fixture, projectId, issueId);
        jdbcTemplate.update("""
            insert into project_work_item_migration_batches(
              id,workspace_id,status,source_watermark,source_fingerprint,
              manifest_fingerprint,initiated_by,initiated_at
            ) values (?,?,'completed',now(),'source','manifest',?,now())
            """, batchId, WORKSPACE_ID, fixture.owner().id());
        jdbcTemplate.update("""
            insert into project_work_item_migration_units(
              id,workspace_id,batch_id,legacy_project_id,space_id,status,
              source_fingerprint,finished_at
            ) values (?,?,?,?,?,'completed','source',now())
            """, unitId, WORKSPACE_ID, batchId, projectId, fixture.spaceId());
        jdbcTemplate.update("""
            insert into project_legacy_work_item_maps(
              id,workspace_id,batch_id,unit_id,source_type,source_id,
              source_project_id,space_id,work_item_id,identity_decision,
              source_fingerprint,status,mapped_at
            ) values (?,?,?,?, 'issue', ?,?,?,?,'uuid_conflict_remapped','source','active',now())
            """, UUID.randomUUID(), WORKSPACE_ID, batchId, unitId, issueId,
            projectId, fixture.spaceId(), canonical.item().id());

        var legacy = compatibilityService.resolveLegacyIssue(fixture.owner(), issueId);
        assertThat(legacy.source()).isEqualTo("legacy");
        assertThat(legacy.canonicalLocation()).endsWith(canonical.item().id().toString());

        var shadow = compatibilityService.changeCutover(
            admin(fixture), fixture.spaceId(), ReadStage.SHADOW, true, false, 0
        );
        assertThat(shadow.version()).isEqualTo(1);
        assertThat(compatibilityService.resolveLegacyIssue(fixture.owner(), issueId).source())
            .isEqualTo("legacy");
        assertThat(countWhere(
            "project_work_item_shadow_samples",
            "workspace_id=? and source_id=?",
            WORKSPACE_ID,
            issueId
        )).isEqualTo(1);

        var canonicalRead = compatibilityService.changeCutover(
            admin(fixture), fixture.spaceId(), ReadStage.CANONICAL_READ, true, false, 1
        );
        assertThat(canonicalRead.version()).isEqualTo(2);
        assertThat(compatibilityService.resolveLegacyIssue(fixture.owner(), issueId).source())
            .isEqualTo("canonical");

        compatibilityService.changeCutover(
            admin(fixture), fixture.spaceId(), ReadStage.CANONICAL_READ, true, true, 2
        );
        assertThat(compatibilityService.resolveLegacyIssue(fixture.owner(), issueId).source())
            .isEqualTo("legacy");
    }

    @Test
    void compatibilityProfileAndWriteClosureFailClosedWithoutLeakingAcrossMembership() throws Exception {
        Fixture fixture = fixture("compat-boundary");
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        insertLegacyIssueFixture(fixture, projectId, issueId);

        var profile = compatibilityService.profile(admin(fixture));
        assertThat(profile.projects()).isPositive();
        assertThat(profile.issues()).isPositive();
        assertThat(profile.sourceFingerprint()).isNotBlank();

        compatibilityService.changeCutover(
            admin(fixture),
            fixture.spaceId(),
            ReadStage.CANONICAL_WRITE,
            false,
            false,
            0
        );
        assertThatThrownBy(() -> compatibilityService.requireLegacyIssueWrite(fixture.owner(), issueId))
            .isInstanceOf(LegacyWriteClosedException.class)
            .extracting(exception -> ((LegacyWriteClosedException) exception).canonicalLocation())
            .isEqualTo("/issues/" + issueId);

        CurrentUser outsider = new CurrentUser(
            UUID.randomUUID(), WORKSPACE_ID, UUID.randomUUID(), "outsider", "Outsider",
            Set.of("member"), Set.of()
        );
        assertHidden(() -> compatibilityService.resolveLegacyIssue(outsider, issueId));
    }

    private void insertLegacyIssueFixture(Fixture fixture, UUID projectId, UUID issueId) {
        jdbcTemplate.update("""
            insert into projects(
              id,workspace_id,project_key,name,status,created_by,created_at,updated_at
            ) values (?,?,?,'Legacy project','active',?,now(),now())
            """, projectId, WORKSPACE_ID,
            "LEG-" + projectId.toString().replace("-", "").substring(0, 8),
            fixture.owner().id());
        jdbcTemplate.update("""
            insert into project_members(
              id,workspace_id,project_id,user_id,project_role,joined_at,created_by
            ) values (?,?,?,?, 'owner',now(),?)
            """, UUID.randomUUID(), WORKSPACE_ID, projectId, fixture.owner().id(), fixture.owner().id());
        jdbcTemplate.update("""
            insert into project_legacy_space_maps(
              id,workspace_id,legacy_project_id,space_id,mapping_version,mapping_status,
              source_checksum,mapped_by,mapped_at
            ) values (?,?,?,?,1,'active','source',?,now())
            """, UUID.randomUUID(), WORKSPACE_ID, projectId, fixture.spaceId(), fixture.owner().id());
        jdbcTemplate.update("""
            insert into issues(
              id,workspace_id,project_id,issue_key,issue_type,title,description,
              priority,status,reporter_id,created_by,created_at,updated_at
            ) values (?,?,?,?, 'task','Legacy title','Legacy description',
                      'medium','open',?,?,now(),now())
            """, issueId, WORKSPACE_ID, projectId,
            "LEG-" + issueId.toString().replace("-", "").substring(0, 8),
            fixture.owner().id(), fixture.owner().id());
    }

    private CurrentUser admin(Fixture fixture) {
        return new CurrentUser(
            fixture.owner().id(),
            WORKSPACE_ID,
            fixture.owner().deviceId(),
            fixture.owner().username(),
            fixture.owner().displayName(),
            Set.of("admin"),
            Set.of("project.manage")
        );
    }

    private Fixture fixture(String label) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JsonNode snapshot = objectMapper.readTree("""
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
              "fields":[
                {
                  "id":"%s",
                  "fieldKey":"priority",
                  "name":"Priority",
                  "fieldType":"text",
                  "config":{"schemaVersion":1,"required":false,"defaultValue":"normal","validationRules":[]},
                  "sortOrder":0,
                  "status":"active",
                  "system":false,
                  "options":[]
                },
                {
                  "id":"%s",
                  "fieldKey":"secret",
                  "name":"Secret",
                  "fieldType":"text",
                  "config":{"schemaVersion":1,"required":false,"validationRules":[]},
                  "sortOrder":1,
                  "status":"active",
                  "system":false,
                  "options":[]
                }
              ],
              "layouts":[
                {
                  "id":"%s",
                  "layoutKind":"create",
                  "status":"active",
                  "nodes":[],
                  "policies":[]
                },
                {
                  "id":"%s",
                  "layoutKind":"detail",
                  "status":"active",
                  "nodes":[],
                  "policies":[
                    {
                      "id":"%s",
                      "fieldKey":"secret",
                      "policyKey":"default",
                      "policy":{
                        "schemaVersion":1,
                        "default":{"mode":"write","required":false},
                        "rules":[
                          {
                            "ruleKey":"guest_hidden",
                            "roles":["guest"],
                            "mode":"hidden",
                            "required":false
                          }
                        ]
                      }
                    }
                  ]
                }
              ]
            }
            """.formatted(
                typeId,
                WORKSPACE_ID,
                spaceId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
            ));
        var canonical = snapshotCanonicalizer.canonicalize(snapshot);

        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId,
            WORKSPACE_ID,
            "s07_" + label + "_" + suffix,
            "S07 " + label
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
            "s07_" + label + "_" + suffix,
            "S07 " + label,
            userId,
            userId
        );
        jdbcTemplate.update(
            """
                insert into project_space_members (
                    id, workspace_id, space_id, user_id, status, joined_at,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId,
            WORKSPACE_ID,
            spaceId,
            userId,
            userId,
            userId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments (
                    id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at
                ) values (?, ?, ?, ?, 'owner', ?, now())
                """,
            UUID.randomUUID(),
            WORKSPACE_ID,
            spaceId,
            memberId,
            userId
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
                typeId,
                WORKSPACE_ID,
                spaceId,
                versionId,
                userId,
                userId
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

        return new Fixture(
            new CurrentUser(
                userId,
                WORKSPACE_ID,
                UUID.randomUUID(),
                "s07_" + label + "_" + suffix,
                "S07 " + label,
                Set.of("member"),
                Set.of()
            ),
            spaceId,
            typeId,
            versionId,
            canonical.configHash()
        );
    }

    private CurrentUser addMember(Fixture fixture, String role) {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId,
            WORKSPACE_ID,
            "s07_" + role + "_" + suffix,
            "S07 " + role
        );
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
            fixture.owner().id(),
            fixture.owner().id()
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
            fixture.owner().id()
        );
        return new CurrentUser(
            userId,
            WORKSPACE_ID,
            UUID.randomUUID(),
            "s07_" + role + "_" + suffix,
            "S07 " + role,
            Set.of("member"),
            Set.of()
        );
    }

    private int count(String table, UUID spaceId) {
        return countWhere(table, "workspace_id=? and space_id=?", WORKSPACE_ID, spaceId);
    }

    private int countWhere(String table, String predicate, Object... arguments) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where " + predicate,
            Integer.class,
            arguments
        );
    }

    private void assertHidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("NOT_FOUND_OR_HIDDEN");
    }

    private record Fixture(
        CurrentUser owner,
        UUID spaceId,
        UUID typeId,
        UUID versionId,
        String configHash
    ) {
    }
}
