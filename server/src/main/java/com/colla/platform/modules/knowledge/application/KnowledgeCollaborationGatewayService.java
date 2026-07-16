package com.colla.platform.modules.knowledge.application;

import com.colla.platform.config.KnowledgeCollaborationProperties;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentCanonicalDocument;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationAuthorization;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationBinaryState;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationStoredUpdate;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationTicket;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationTicketRecord;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeCollaborationGatewayService {
    public static final String PROTOCOL_VERSION = "colla-yjs-v1";
    public static final int SCHEMA_VERSION = KnowledgeContentCanonicalModels.CURRENT_SCHEMA_VERSION;
    private static final Set<String> EDIT_LEVELS = Set.of("owner", "manage", "edit");

    private final KnowledgeCollaborationProperties properties;
    private final KnowledgeContentService contentService;
    private final KnowledgeContentCanonicalService canonicalService;
    private final KnowledgeContentSchemaService schemaService;
    private final KnowledgeContentRepository repository;
    private final IdentityRepository identityRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public KnowledgeCollaborationGatewayService(
        KnowledgeCollaborationProperties properties,
        KnowledgeContentService contentService,
        KnowledgeContentCanonicalService canonicalService,
        KnowledgeContentSchemaService schemaService,
        KnowledgeContentRepository repository,
        IdentityRepository identityRepository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.contentService = contentService;
        this.canonicalService = canonicalService;
        this.schemaService = schemaService;
        this.repository = repository;
        this.identityRepository = identityRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeCollaborationTicket issue(CurrentUser user, UUID itemId) {
        var item = contentService.requireView(user, itemId);
        repository.purgeExpiredCollaborationTickets(Instant.now().minus(properties.getExpiredTicketRetention()));
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String clientId = "web:" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(properties.getTicketTtl());
        repository.createCollaborationTicket(
            sha256(token), user.workspaceId(), itemId, user.id(), user.deviceId(), clientId, expiresAt
        );
        return new KnowledgeCollaborationTicket(
            properties.getPublicUrl(), documentName(user.workspaceId(), itemId), token, clientId,
            PROTOCOL_VERSION, SCHEMA_VERSION, item.permissionLevel(), true,
            EDIT_LEVELS.contains(item.permissionLevel()), expiresAt
        );
    }

    public KnowledgeCollaborationAuthorization authenticate(String ticket, String requestedDocumentName) {
        KnowledgeCollaborationTicketRecord record = repository.findActiveCollaborationTicket(sha256(ticket))
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, "COLLAB_AUTH_EXPIRED", "Collaboration ticket expired or invalid"));
        if (!documentName(record.workspaceId(), record.itemId()).equals(requestedDocumentName)) {
            throw failure(HttpStatus.FORBIDDEN, "COLLAB_FORBIDDEN", "Collaboration ticket does not match document");
        }
        CurrentUser user = identityRepository.findCurrentUser(record.userId(), record.deviceId())
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, "COLLAB_AUTH_EXPIRED", "User session is no longer active"));
        if (!record.workspaceId().equals(user.workspaceId())) {
            throw failure(HttpStatus.FORBIDDEN, "COLLAB_FORBIDDEN", "Workspace mismatch");
        }
        var item = contentService.requireView(user, record.itemId());
        boolean canEdit = EDIT_LEVELS.contains(item.permissionLevel());
        return new KnowledgeCollaborationAuthorization(
            record.workspaceId(), record.itemId(), user.id(), user.deviceId(), user.username(), user.displayName(),
            record.clientId(), colorFor(user.id()), item.permissionLevel(), true, canEdit, record.expiresAt()
        );
    }

    public CollaborationLoad load(String ticket, String documentName) {
        KnowledgeCollaborationAuthorization authorization = authenticate(ticket, documentName);
        KnowledgeCollaborationBinaryState state = repository.findCollaborationBinaryState(
            authorization.workspaceId(), authorization.itemId()
        ).orElse(null);
        long snapshotSequence = state == null ? 0 : state.snapshotSequence();
        List<KnowledgeCollaborationStoredUpdate> updates = repository.listCollaborationUpdatesAfter(
            authorization.workspaceId(), authorization.itemId(), snapshotSequence
        );
        var detail = contentService.getContent(asCurrentUser(authorization), authorization.itemId());
        JsonNode canonical = state != null && state.canonicalSnapshot() != null
            ? readJson(state.canonicalSnapshot())
            : canonicalService.plan(authorization.itemId(), detail).canonicalDocument().document();
        return new CollaborationLoad(
            detail.item().title(), encode(state == null ? null : state.snapshot()),
            encode(state == null ? null : state.stateVector()), state == null ? SCHEMA_VERSION : state.schemaVersion(),
            snapshotSequence, updates.stream().map(update -> new CollaborationUpdate(
                update.sequence(), update.updateId(), encode(update.payload()), update.actorId(), update.clientId(), update.createdAt()
            )).toList(), canonical
        );
    }

    @Transactional
    public CollaborationUpdateAck appendUpdate(
        String ticket,
        String documentName,
        String encodedUpdate,
        String clientId,
        String updateId,
        int schemaVersion
    ) {
        KnowledgeCollaborationAuthorization authorization = requireEditable(ticket, documentName);
        requireSchema(schemaVersion);
        byte[] update = decode(encodedUpdate, properties.getMaxUpdateBytes());
        if (!sha256(update).equals(updateId)) {
            throw failure(HttpStatus.BAD_REQUEST, "COLLAB_INVALID_UPDATE", "Update id does not match payload");
        }
        long sequence = repository.appendCollaborationUpdate(
            authorization.workspaceId(), authorization.itemId(), updateId, update, authorization.userId(),
            normalizeClientId(clientId, authorization.clientId()), schemaVersion
        );
        return new CollaborationUpdateAck(sequence, updateId, true);
    }

    @Transactional
    public CollaborationSnapshotAck storeSnapshot(
        String ticket,
        String documentName,
        String encodedSnapshot,
        String encodedStateVector,
        JsonNode canonicalDocument,
        int schemaVersion,
        String clientId
    ) {
        KnowledgeCollaborationAuthorization authorization = requireEditable(ticket, documentName);
        return persistSnapshot(
            authorization.workspaceId(), authorization.itemId(), asCurrentUser(authorization), encodedSnapshot,
            encodedStateVector, canonicalDocument, schemaVersion,
            normalizeClientId(clientId, authorization.clientId()), null
        );
    }

    @Transactional
    public CollaborationSnapshotAck storeSnapshotFromNode(
        String documentName,
        String encodedSnapshot,
        String encodedStateVector,
        JsonNode canonicalDocument,
        int schemaVersion,
        String clientId,
        String nodeId
    ) {
        DocumentKey key = parseDocumentName(documentName);
        UUID actorId = repository.findLatestCollaborationActor(key.workspaceId(), key.itemId())
            .orElseThrow(() -> failure(HttpStatus.CONFLICT, "COLLAB_SNAPSHOT_NO_ACTOR", "No persisted collaboration update can own this snapshot"));
        var account = identityRepository.findUserById(actorId)
            .filter(user -> key.workspaceId().equals(user.workspaceId()) && "active".equals(user.status()))
            .orElseThrow(() -> failure(HttpStatus.CONFLICT, "COLLAB_SNAPSHOT_ACTOR_UNAVAILABLE", "Snapshot actor is no longer available"));
        CurrentUser actor = new CurrentUser(
            account.id(), account.workspaceId(), null, account.username(), account.displayName(), Set.of(), Set.of()
        );
        return persistSnapshot(
            key.workspaceId(), key.itemId(), actor, encodedSnapshot, encodedStateVector, canonicalDocument,
            schemaVersion, normalizeClientId(clientId, "node:" + normalizeNodeId(nodeId)), normalizeNodeId(nodeId)
        );
    }

    private CollaborationSnapshotAck persistSnapshot(
        UUID workspaceId,
        UUID itemId,
        CurrentUser actor,
        String encodedSnapshot,
        String encodedStateVector,
        JsonNode canonicalDocument,
        int schemaVersion,
        String clientId,
        String nodeId
    ) {
        requireSchema(schemaVersion);
        byte[] snapshot = decode(encodedSnapshot, properties.getMaxUpdateBytes() * 20L);
        byte[] stateVector = decode(encodedStateVector, properties.getMaxUpdateBytes());
        KnowledgeContentCanonicalDocument canonical = schemaService.normalizeDocument(canonicalDocument, itemId);
        if (canonical.issues().stream().anyMatch(issue -> issue.isError())) {
            throw failure(HttpStatus.BAD_REQUEST, "COLLAB_SCHEMA_INVALID", "Canonical projection contains schema errors");
        }
        String title = canonicalDocument.path("collaborationTitle").asText(null);
        if (title == null || title.isBlank()) {
            title = repository.findItem(workspaceId, itemId)
                .map(com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem::title)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "COLLAB_DOCUMENT_NOT_FOUND", "Collaboration document no longer exists"));
        }
        repository.updateContentSnapshot(
            workspaceId, itemId, title, canonical.markdown(), actor.id()
        );
        repository.replaceBlocks(
            workspaceId, itemId, schemaService.toBlockDrafts(canonical), actor.id()
        );
        contentService.reanchorSelectionComments(workspaceId, itemId, canonical.markdown());
        repository.storeCollaborationSnapshot(
            workspaceId, itemId, snapshot, stateVector, sha256(snapshot), schemaVersion,
            writeJson(canonical.document()), clientId, actor.id()
        );
        int compactedUpdates = repository.compactCollaborationUpdates(workspaceId, itemId, properties.getRetainedUpdates());
        if (repository.markCollaborationAuditCheckpoint(
            workspaceId, itemId, Instant.now().minus(1, ChronoUnit.MINUTES)
        )) {
            Map<String, Object> context = new HashMap<>();
            context.put("protocolVersion", PROTOCOL_VERSION);
            context.put("schemaVersion", schemaVersion);
            context.put("clientId", clientId);
            context.put("snapshotHash", sha256(snapshot));
            context.put("compactedUpdates", compactedUpdates);
            if (nodeId != null) context.put("nodeId", nodeId);
            auditService.log(actor, "knowledge.content.collaboration.checkpoint", "knowledge_content", itemId, context);
        }
        return new CollaborationSnapshotAck(
            sha256(snapshot), stateVector.length, canonical.checksum(), compactedUpdates, nodeId, Instant.now()
        );
    }

    public boolean validInternalSecret(String supplied) {
        byte[] expected = properties.getInternalSecret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private KnowledgeCollaborationAuthorization requireEditable(String ticket, String documentName) {
        KnowledgeCollaborationAuthorization authorization = authenticate(ticket, documentName);
        if (!authorization.canEdit()) {
            throw failure(HttpStatus.FORBIDDEN, "COLLAB_READ_ONLY", "Collaboration session is read only");
        }
        return authorization;
    }

    private CurrentUser asCurrentUser(KnowledgeCollaborationAuthorization authorization) {
        return identityRepository.findCurrentUser(authorization.userId(), authorization.deviceId())
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, "COLLAB_AUTH_EXPIRED", "User session is no longer active"));
    }

    private void requireSchema(int schemaVersion) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw failure(HttpStatus.CONFLICT, "COLLAB_SCHEMA_MISMATCH", "Unsupported collaboration schema version");
        }
    }

    private byte[] decode(String value, long maxBytes) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value == null ? "" : value);
            if (decoded.length == 0) {
                throw failure(HttpStatus.BAD_REQUEST, "COLLAB_INVALID_UPDATE", "Collaboration binary payload is empty");
            }
            if (decoded.length > maxBytes) {
                throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "COLLAB_UPDATE_TOO_LARGE", "Collaboration binary payload is too large");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw failure(HttpStatus.BAD_REQUEST, "COLLAB_INVALID_UPDATE", "Collaboration payload is not valid base64");
        }
    }

    private String encode(byte[] value) {
        return value == null || value.length == 0 ? "" : Base64.getEncoder().encodeToString(value);
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw failure(HttpStatus.CONFLICT, "COLLAB_DOCUMENT_CORRUPT", "Stored collaboration projection is invalid");
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure(HttpStatus.BAD_REQUEST, "COLLAB_SCHEMA_INVALID", "Canonical projection is not serializable");
        }
    }

    private String normalizeClientId(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) return fallback;
        return candidate.length() > 128 ? candidate.substring(0, 128) : candidate;
    }

    private String normalizeNodeId(String value) {
        if (value == null || value.isBlank()) {
            throw failure(HttpStatus.BAD_REQUEST, "COLLAB_NODE_REQUIRED", "Collaboration node id is required");
        }
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private DocumentKey parseDocumentName(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", -1);
        if (parts.length != 3 || !"knowledge".equals(parts[0])) {
            throw failure(HttpStatus.BAD_REQUEST, "COLLAB_INVALID_DOCUMENT", "Invalid collaboration document name");
        }
        try {
            return new DocumentKey(UUID.fromString(parts[1]), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException exception) {
            throw failure(HttpStatus.BAD_REQUEST, "COLLAB_INVALID_DOCUMENT", "Invalid collaboration document name");
        }
    }

    private String documentName(UUID workspaceId, UUID itemId) {
        return "knowledge:" + workspaceId + ":" + itemId;
    }

    private String colorFor(UUID userId) {
        String[] colors = {"#5b5bd6", "#16a085", "#d97706", "#dc2626", "#2563eb", "#9333ea"};
        return colors[Math.floorMod(userId.hashCode(), colors.length)];
    }

    private String sha256(String value) { return sha256(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8)); }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private CollaborationFailure failure(HttpStatus status, String code, String message) {
        return new CollaborationFailure(status, code, message);
    }

    public record CollaborationLoad(
        String title, String snapshot, String stateVector, int schemaVersion, long snapshotSequence,
        List<CollaborationUpdate> updates, JsonNode canonicalDocument
    ) {}
    public record CollaborationUpdate(long sequence, String updateId, String update, UUID actorId, String clientId, Instant createdAt) {}
    public record CollaborationUpdateAck(long sequence, String updateId, boolean accepted) {}
    private record DocumentKey(UUID workspaceId, UUID itemId) {}
    public record CollaborationSnapshotAck(
        String snapshotHash, int stateVectorBytes, String canonicalChecksum, int compactedUpdates, String nodeId, Instant savedAt
    ) {}

    public static final class CollaborationFailure extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        public CollaborationFailure(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
        public HttpStatus status() { return status; }
        public String code() { return code; }
    }
}
