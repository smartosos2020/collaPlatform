package com.colla.platform.modules.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.config.KnowledgeCollaborationProperties;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationTicketRecord;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeCollaborationGatewayServiceTests {
    private final KnowledgeContentService contentService = mock(KnowledgeContentService.class);
    private final KnowledgeContentCanonicalService canonicalService = mock(KnowledgeContentCanonicalService.class);
    private final KnowledgeContentRepository repository = mock(KnowledgeContentRepository.class);
    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KnowledgeCollaborationProperties properties = new KnowledgeCollaborationProperties();
    private KnowledgeCollaborationGatewayService service;
    private CurrentUser user;
    private UUID itemId;
    private String ticket;
    private String documentName;

    @BeforeEach
    void setUp() {
        properties.setMaxUpdateBytes(32);
        service = new KnowledgeCollaborationGatewayService(
            properties, contentService, canonicalService, new KnowledgeContentSchemaService(objectMapper),
            repository, identityRepository, auditService, objectMapper
        );
        UUID workspaceId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        ticket = "test-ticket";
        documentName = "knowledge:" + workspaceId + ":" + itemId;
        user = new CurrentUser(UUID.randomUUID(), workspaceId, UUID.randomUUID(), "editor", "Editor", Set.of("member"), Set.of());
        KnowledgeCollaborationTicketRecord ticketRecord = new KnowledgeCollaborationTicketRecord(
            UUID.randomUUID(), sha256(ticket.getBytes(StandardCharsets.UTF_8)), workspaceId, itemId,
            user.id(), user.deviceId(), "client:test", Instant.now().plusSeconds(300), null
        );
        when(repository.findActiveCollaborationTicket(ticketRecord.tokenHash())).thenReturn(Optional.of(ticketRecord));
        when(identityRepository.findCurrentUser(user.id(), user.deviceId())).thenReturn(Optional.of(user));
    }

    @Test
    void acceptsIdempotentUpdatesAndReturnsStableSequence() throws Exception {
        editableItem("edit");
        byte[] update = "merge-update".getBytes(StandardCharsets.UTF_8);
        String updateId = sha256(update);
        when(repository.appendCollaborationUpdate(any(), any(), eq(updateId), any(), any(), any(), eq(3))).thenReturn(42L);

        var first = service.appendUpdate(ticket, documentName, Base64.getEncoder().encodeToString(update), "client:test", updateId, 3);
        var duplicate = service.appendUpdate(ticket, documentName, Base64.getEncoder().encodeToString(update), "client:test", updateId, 3);

        assertThat(first.sequence()).isEqualTo(42L);
        assertThat(duplicate.sequence()).isEqualTo(42L);
        assertThat(duplicate.accepted()).isTrue();
    }

    @Test
    void rejectsReadOnlyAndOversizedUpdatesBeforePersistence() {
        editableItem("view");
        byte[] small = "small".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.appendUpdate(
            ticket, documentName, Base64.getEncoder().encodeToString(small), "client:test", sha256(small), 3
        )).isInstanceOf(KnowledgeCollaborationGatewayService.CollaborationFailure.class)
            .hasMessageContaining("read only");

        editableItem("edit");
        byte[] oversized = new byte[33];
        assertThatThrownBy(() -> service.appendUpdate(
            ticket, documentName, Base64.getEncoder().encodeToString(oversized), "client:test", sha256(oversized), 3
        )).isInstanceOf(KnowledgeCollaborationGatewayService.CollaborationFailure.class)
            .hasMessageContaining("too large");
        verify(repository, never()).appendCollaborationUpdate(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void persistsBinaryAndCanonicalProjectionWithoutAuditPerUpdate() {
        properties.setMaxUpdateBytes(1024);
        editableItem("edit");
        ObjectNode document = objectMapper.createObjectNode();
        document.put("type", "doc");
        document.put("schemaVersion", 3);
        document.put("collaborationTitle", "Shared title");
        ArrayNode content = document.putArray("content");
        ObjectNode paragraph = content.addObject();
        paragraph.put("type", "paragraph");
        paragraph.putObject("attrs").put("blockId", UUID.randomUUID().toString());
        paragraph.putArray("content").addObject().put("type", "text").put("text", "Shared body");
        byte[] snapshot = "binary-snapshot".getBytes(StandardCharsets.UTF_8);
        byte[] vector = "vector".getBytes(StandardCharsets.UTF_8);
        when(repository.markCollaborationAuditCheckpoint(any(), any(), any())).thenReturn(false);

        var ack = service.storeSnapshot(
            ticket, documentName, Base64.getEncoder().encodeToString(snapshot), Base64.getEncoder().encodeToString(vector),
            document, 3, "client:test"
        );

        assertThat(ack.snapshotHash()).isEqualTo(sha256(snapshot));
        verify(repository).updateContentSnapshot(user.workspaceId(), itemId, "Shared title", "Shared body", user.id());
        verify(repository).storeCollaborationSnapshot(
            eq(user.workspaceId()), eq(itemId), eq(snapshot), eq(vector), eq(sha256(snapshot)), eq(3),
            any(), eq("client:test"), eq(user.id())
        );
        verify(auditService, never()).log(any(CurrentUser.class), any(), any(), any(), any());
    }

    @Test
    void trustedNodeSnapshotUsesLatestPersistedActorAndCompactsTheUpdateTail() {
        properties.setMaxUpdateBytes(1024);
        properties.setRetainedUpdates(25);
        ObjectNode document = objectMapper.createObjectNode();
        document.put("type", "doc");
        document.put("schemaVersion", 3);
        document.put("collaborationTitle", "Node checkpoint");
        document.putArray("content");
        when(repository.findLatestCollaborationActor(user.workspaceId(), itemId)).thenReturn(Optional.of(user.id()));
        when(identityRepository.findUserById(user.id())).thenReturn(Optional.of(new UserAccount(
            user.id(), user.workspaceId(), user.username(), "hash", user.displayName(), null, "editor@colla.local", "active"
        )));
        when(repository.compactCollaborationUpdates(user.workspaceId(), itemId, 25)).thenReturn(17);

        var ack = service.storeSnapshotFromNode(
            documentName,
            Base64.getEncoder().encodeToString("node-snapshot".getBytes(StandardCharsets.UTF_8)),
            Base64.getEncoder().encodeToString("node-vector".getBytes(StandardCharsets.UTF_8)),
            document, 3, "client:test", "collaboration-a"
        );

        assertThat(ack.compactedUpdates()).isEqualTo(17);
        assertThat(ack.nodeId()).isEqualTo("collaboration-a");
        verify(repository).compactCollaborationUpdates(user.workspaceId(), itemId, 25);
        verify(repository).updateContentSnapshot(user.workspaceId(), itemId, "Node checkpoint", "", user.id());
    }

    @Test
    void recordsOneThrottledAuditCheckpointForPersistedCollaborationState() {
        properties.setMaxUpdateBytes(1024);
        editableItem("edit");
        ObjectNode document = objectMapper.createObjectNode();
        document.put("type", "doc");
        document.put("schemaVersion", 3);
        document.put("collaborationTitle", "Checkpoint title");
        document.putArray("content");
        when(repository.markCollaborationAuditCheckpoint(any(), any(), any())).thenReturn(true, false);

        for (int index = 0; index < 2; index += 1) {
            service.storeSnapshot(
                ticket, documentName,
                Base64.getEncoder().encodeToString(("snapshot-" + index).getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(("vector-" + index).getBytes(StandardCharsets.UTF_8)),
                document, 3, "client:test"
            );
        }

        verify(auditService, times(1)).log(
            any(CurrentUser.class), eq("knowledge.content.collaboration.checkpoint"),
            eq("knowledge_content"), eq(itemId), any()
        );
    }

    @Test
    void rejectsExpiredOrDisabledUserSessionsDuringPermissionRefresh() {
        editableItem("edit");
        when(identityRepository.findCurrentUser(user.id(), user.deviceId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(ticket, documentName))
            .isInstanceOf(KnowledgeCollaborationGatewayService.CollaborationFailure.class)
            .hasMessageContaining("no longer active");
    }

    private void editableItem(String permissionLevel) {
        KnowledgeBaseItem item = mock(KnowledgeBaseItem.class);
        when(item.permissionLevel()).thenReturn(permissionLevel);
        when(item.title()).thenReturn("Stored title");
        when(contentService.requireView(user, itemId)).thenReturn(item);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
