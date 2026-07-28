package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.CrossSpaceGrant;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CreateLinkIntentCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CrossSpaceRelationPolicy;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntent;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntentCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.CrossSpaceRelationRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossSpaceRelationServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SOURCE_SPACE = UUID.randomUUID();
    private static final UUID TARGET_SPACE = UUID.randomUUID();
    private static final UUID SOURCE_USER = UUID.randomUUID();
    private static final UUID TARGET_USER = UUID.randomUUID();
    private static final UUID POLICY = UUID.randomUUID();
    private static final UUID GRANT = UUID.randomUUID();
    private static final UUID SOURCE_ITEM = UUID.randomUUID();
    private static final UUID TARGET_ITEM = UUID.randomUUID();
    private static final UUID SOURCE_TYPE = UUID.randomUUID();
    private static final UUID SOURCE_VERSION = UUID.randomUUID();
    private static final UUID TARGET_TYPE = UUID.randomUUID();
    private static final UUID TARGET_VERSION = UUID.randomUUID();
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsBoundedIntentThroughSourceRelateCapability() {
        Fixture fixture = fixture();
        CrossSpaceRelationPolicy policy = policy("active", 3);
        when(fixture.repository.findPolicy(WORKSPACE, POLICY, true))
            .thenReturn(Optional.of(policy));
        when(fixture.repository.findReceipt(
            WORKSPACE, SOURCE_USER, "intent_create", "intent-create-1"
        )).thenReturn(Optional.empty());
        when(fixture.workItems.find(WORKSPACE, SOURCE_SPACE, SOURCE_ITEM))
            .thenReturn(Optional.of(item(SOURCE_SPACE, SOURCE_ITEM, SOURCE_TYPE, SOURCE_VERSION)));
        when(fixture.workItems.find(WORKSPACE, TARGET_SPACE, TARGET_ITEM))
            .thenReturn(Optional.of(item(TARGET_SPACE, TARGET_ITEM, TARGET_TYPE, TARGET_VERSION)));
        LinkIntent expected = intent("requested", 1, null);
        when(fixture.repository.createIntent(
            eq(WORKSPACE), eq(SOURCE_USER), eq(policy),
            eq(SOURCE_ITEM), eq(0L), eq(TARGET_ITEM), eq(0L)
        )).thenReturn(expected);

        LinkIntent result = fixture.service.createIntent(
            sourceUser(), POLICY,
            new CreateLinkIntentCommand(
                1, "intent-create-1", 3, SOURCE_ITEM, 0, TARGET_ITEM, 0
            )
        );

        assertThat(result).isEqualTo(expected);
        verify(fixture.access).requireAction(
            any(), eq(SOURCE_SPACE), any(), eq("relate"), eq("blocks")
        );
        verify(fixture.repository).saveReceipt(
            eq(WORKSPACE), eq(SOURCE_USER), eq("intent_create"),
            eq("intent-create-1"), any(), any()
        );
    }

    @Test
    void rechecksGrantBeforeTargetAcceptanceAndNeverCreatesHalfEdge() {
        Fixture fixture = fixture();
        CrossSpaceRelationPolicy policy = policy("active", 3);
        LinkIntent intent = intent("requested", 1, null);
        when(fixture.repository.findIntent(WORKSPACE, intent.id(), true))
            .thenReturn(Optional.of(intent));
        when(fixture.repository.findPolicy(WORKSPACE, POLICY, true))
            .thenReturn(Optional.of(policy));
        when(fixture.repository.findReceipt(
            WORKSPACE, TARGET_USER, "intent_accept", "intent-accept-1"
        )).thenReturn(Optional.empty());
        when(fixture.grants.requireActiveGrant(any(), eq(GRANT), eq("relate")))
            .thenThrow(new WorkItemRuntimeException(
                "CROSS_SPACE_REFERENCE_FORBIDDEN", "forbidden"
            ));

        assertThatThrownBy(() -> fixture.service.intentLifecycle(
            targetUser(), intent.id(),
            new LinkIntentCommand(1, "intent-accept-1", 1, "accept", null)
        )).isInstanceOf(WorkItemRuntimeException.class);

        verify(fixture.canonical, never()).create(any());
        verify(fixture.repository, never()).completeIntent(
            any(), any(), any(Long.class), any(), any(), any(), any()
        );
    }

    @Test
    void endpointReferenceContractCannotExposeTitleStatusOrPath() {
        assertThat(
            java.util.Arrays.stream(
                com.colla.platform.modules.project.domain.CrossSpaceRelationModels
                    .EndpointReference.class.getRecordComponents()
            ).map(java.lang.reflect.RecordComponent::getName)
        ).doesNotContain("title", "status", "path", "relationCount");
    }

    private Fixture fixture() {
        CrossSpaceRelationRepository repository = mock(CrossSpaceRelationRepository.class);
        CrossSpaceRelationCommand canonical = mock(CrossSpaceRelationCommand.class);
        CrossSpaceGrantService grants = mock(CrossSpaceGrantService.class);
        WorkItemRepository workItems = mock(WorkItemRepository.class);
        PublishedSnapshotAdapter snapshots = mock(PublishedSnapshotAdapter.class);
        WorkItemRelationAccessDecisionService access =
            mock(WorkItemRelationAccessDecisionService.class);
        AuditLog audit = mock(AuditLog.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        CrossSpaceRelationService service = new CrossSpaceRelationService(
            repository, canonical, grants, workItems, snapshots,
            access, audit, outbox, JSON
        );
        when(grants.requireActiveGrant(any(), eq(GRANT), eq("relate")))
            .thenReturn(grant());
        return new Fixture(
            repository, canonical, grants, workItems, access, service
        );
    }

    private CrossSpaceRelationPolicy policy(String status, long version) {
        return new CrossSpaceRelationPolicy(
            POLICY, GRANT, SOURCE_SPACE, TARGET_SPACE, "blocks",
            "source_to_target",
            SOURCE_TYPE, SOURCE_VERSION, "a".repeat(64),
            TARGET_TYPE, TARGET_VERSION, "b".repeat(64),
            status, version, SOURCE_USER, TARGET_USER, SOURCE_USER, Instant.EPOCH
        );
    }

    private LinkIntent intent(String status, long version, UUID relationId) {
        return new LinkIntent(
            UUID.randomUUID(), POLICY, 3,
            SOURCE_SPACE, SOURCE_ITEM, 0,
            TARGET_SPACE, TARGET_ITEM, 0,
            status, version, SOURCE_USER,
            "linked".equals(status) ? TARGET_USER : null,
            relationId, Instant.EPOCH
        );
    }

    private CrossSpaceGrant grant() {
        return new CrossSpaceGrant(
            GRANT, SOURCE_SPACE, TARGET_SPACE, "grant", "active", 1,
            true, true, SOURCE_USER, TARGET_USER,
            JsonNodeFactory.instance.objectNode(), "hash",
            SOURCE_USER, Instant.EPOCH, null, null
        );
    }

    private WorkItem item(
        UUID spaceId, UUID itemId, UUID typeId, UUID versionId
    ) {
        return new WorkItem(
            itemId, WORKSPACE, spaceId, typeId, versionId, "task", "Task",
            typeId.equals(SOURCE_TYPE) ? "a".repeat(64) : "b".repeat(64),
            1, "TASK-1", "Hidden title", JsonNodeFactory.instance.objectNode(),
            "active", 0, SOURCE_USER, Instant.EPOCH, SOURCE_USER, Instant.EPOCH, null
        );
    }

    private CurrentUser sourceUser() {
        return user(SOURCE_USER);
    }

    private CurrentUser targetUser() {
        return user(TARGET_USER);
    }

    private CurrentUser user(UUID id) {
        return new CurrentUser(
            id, WORKSPACE, UUID.randomUUID(), "actor", "Actor", Set.of(), Set.of()
        );
    }

    private record Fixture(
        CrossSpaceRelationRepository repository,
        CrossSpaceRelationCommand canonical,
        CrossSpaceGrantService grants,
        WorkItemRepository workItems,
        WorkItemRelationAccessDecisionService access,
        CrossSpaceRelationService service
    ) {
    }
}
