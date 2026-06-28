package com.colla.platform.modules.doc.application;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCollaborationState;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCollaborationHealth;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.infrastructure.DocumentRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.websocket.WebSocketEventPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class DocumentCollaborationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SNAPSHOT_ENCODING = "snapshot-v1";
    private static final long PRESENCE_TTL_SECONDS = 120;

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final Map<DocumentRoomKey, DocumentRoom> rooms = new ConcurrentHashMap<>();
    private final Map<DocumentRoomKey, DirtySnapshot> dirtySnapshots = new ConcurrentHashMap<>();

    public DocumentCollaborationService(
        DocumentService documentService,
        DocumentRepository documentRepository,
        ObjectMapper objectMapper
    ) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
    }

    public boolean supports(String type) {
        return type != null && type.startsWith("document.");
    }

    public DocumentCollaborationHealth health(CurrentUser currentUser, UUID documentId) {
        requireDocumentId(documentId);
        documentService.requireView(currentUser, documentId);
        DocumentRoomKey key = new DocumentRoomKey(currentUser.workspaceId(), documentId);
        DocumentRoom room = rooms.get(key);
        DocumentCollaborationState state = documentRepository.findCollaborationState(currentUser.workspaceId(), documentId).orElse(null);
        long serverClock = room == null ? state == null ? 0 : state.serverClock() : room.clock.get();
        String stateVector = room == null ? state == null ? Long.toString(serverClock) : state.stateVector() : room.stateVector;
        return new DocumentCollaborationHealth(
            documentId,
            serverClock,
            room == null ? 0 : room.presences().size(),
            dirtySnapshots.containsKey(key),
            stateVector,
            state == null ? null : state.lastSavedAt(),
            state == null ? null : state.updatedAt()
        );
    }

    public void handle(CurrentUser currentUser, WebSocketSession session, String rawPayload) {
        Map<String, Object> command;
        try {
            command = objectMapper.readValue(rawPayload, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            sendError(session, null, null, null, "Invalid WebSocket command");
            return;
        }
        String type = asString(command.get("type"));
        String requestId = asString(command.get("requestId"));
        UUID documentId = parseUuid(asString(command.get("documentId")));
        if (documentId == null) {
            documentId = parseUuid(asString(payload(command).get("documentId")));
        }
        try {
            switch (type) {
                case "document.join" -> join(currentUser, session, requestId, documentId, payload(command));
                case "document.leave" -> leave(currentUser, session, requestId, documentId);
                case "document.awareness.update" -> awareness(currentUser, session, requestId, documentId, payload(command));
                case "document.update" -> update(currentUser, session, requestId, documentId, payload(command));
                case "document.snapshot.request" -> snapshot(currentUser, session, requestId, documentId);
                default -> sendError(session, requestId, currentUser.workspaceId(), documentId, "Unsupported document command");
            }
        } catch (ResponseStatusException exception) {
            sendError(session, requestId, currentUser.workspaceId(), documentId, exception.getReason());
        } catch (RuntimeException exception) {
            sendError(session, requestId, currentUser.workspaceId(), documentId, "Document collaboration command failed");
        }
    }

    public void disconnect(WebSocketSession session, CurrentUser currentUser) {
        List<DocumentRoomKey> affectedRooms = new ArrayList<>();
        for (Map.Entry<DocumentRoomKey, DocumentRoom> entry : rooms.entrySet()) {
            if (entry.getValue().remove(session)) {
                affectedRooms.add(entry.getKey());
            }
        }
        for (DocumentRoomKey key : affectedRooms) {
            DocumentRoom room = rooms.get(key);
            if (room == null) {
                continue;
            }
            broadcastAwareness(room, key, null);
            if (room.isEmpty()) {
                rooms.remove(key, room);
            }
        }
    }

    private void join(CurrentUser currentUser, WebSocketSession session, String requestId, UUID documentId, Map<String, Object> payload) {
        requireDocumentId(documentId);
        DocumentSummary document = documentService.requireView(currentUser, documentId);
        DocumentRoomKey key = new DocumentRoomKey(currentUser.workspaceId(), documentId);
        DocumentRoom room = room(key, document);
        String clientId = normalizeClientId(asString(payload.get("clientId")), session);
        Presence presence = new Presence(
            currentUser.id(),
            currentUser.username(),
            currentUser.displayName(),
            clientId,
            colorFor(currentUser.id()),
            cursor(payload),
            asBoolean(payload.get("editing")),
            Instant.now()
        );
        room.add(session, presence);
        sendSnapshot(session, requestId, key, room);
        broadcastAwareness(room, key, null);
    }

    private void leave(CurrentUser currentUser, WebSocketSession session, String requestId, UUID documentId) {
        requireDocumentId(documentId);
        DocumentRoomKey key = new DocumentRoomKey(currentUser.workspaceId(), documentId);
        DocumentRoom room = rooms.get(key);
        if (room == null) {
            return;
        }
        room.remove(session);
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("documentId", documentId.toString());
        send(session, "document.leave", key, payload);
        broadcastAwareness(room, key, null);
        if (room.isEmpty()) {
            rooms.remove(key, room);
        }
    }

    private void awareness(CurrentUser currentUser, WebSocketSession session, String requestId, UUID documentId, Map<String, Object> payload) {
        requireDocumentId(documentId);
        documentService.requireView(currentUser, documentId);
        DocumentRoomKey key = new DocumentRoomKey(currentUser.workspaceId(), documentId);
        DocumentRoom room = room(key, null);
        String clientId = normalizeClientId(asString(payload.get("clientId")), session);
        Presence current = room.presence(session);
        Presence next = new Presence(
            currentUser.id(),
            currentUser.username(),
            currentUser.displayName(),
            clientId,
            current == null ? colorFor(currentUser.id()) : current.color(),
            cursor(payload),
            asBoolean(payload.get("editing")),
            Instant.now()
        );
        room.add(session, next);
        broadcastAwareness(room, key, requestId);
    }

    private void update(CurrentUser currentUser, WebSocketSession session, String requestId, UUID documentId, Map<String, Object> payload) {
        requireDocumentId(documentId);
        DocumentSummary document = documentService.requireEdit(currentUser, documentId);
        DocumentRoomKey key = new DocumentRoomKey(currentUser.workspaceId(), documentId);
        DocumentRoom room = room(key, document);
        String content = asString(payload.get("content"));
        String title = asString(payload.get("title"));
        if (title.isBlank()) {
            title = room.title.isBlank() ? document.title() : room.title;
        }
        String clientId = normalizeClientId(asString(payload.get("clientId")), session);
        long localSeq = asLong(payload.get("localSeq"), 0L);
        long baseServerClock = asLong(payload.get("baseServerClock"), 0L);
        long serverClock = room.clock.incrementAndGet();
        String stateVector = Long.toString(serverClock);
        room.title = title;
        room.content = content;
        room.stateVector = stateVector;
        Presence current = room.presence(session);
        room.add(session, new Presence(
            currentUser.id(),
            currentUser.username(),
            currentUser.displayName(),
            clientId,
            current == null ? colorFor(currentUser.id()) : current.color(),
            cursor(payload),
            true,
            Instant.now()
        ));

        Map<String, Object> snapshotPayload = new HashMap<>();
        snapshotPayload.put("encoding", SNAPSHOT_ENCODING);
        snapshotPayload.put("clientId", clientId);
        snapshotPayload.put("localSeq", localSeq);
        snapshotPayload.put("baseServerClock", baseServerClock);
        snapshotPayload.put("title", title);
        snapshotPayload.put("contentLength", content.length());

        documentRepository.upsertCollaborationState(
            key.workspaceId(),
            key.documentId(),
            stateVector,
            content,
            writeJson(snapshotPayload),
            serverClock,
            clientId,
            currentUser.id()
        );
        dirtySnapshots.put(key, new DirtySnapshot(title, content, serverClock, clientId, localSeq, currentUser.id(), Instant.now()));

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("requestId", requestId);
        eventPayload.put("documentId", documentId.toString());
        eventPayload.put("encoding", SNAPSHOT_ENCODING);
        eventPayload.put("title", title);
        eventPayload.put("content", content);
        eventPayload.put("serverClock", serverClock);
        eventPayload.put("stateVector", stateVector);
        eventPayload.put("clientId", clientId);
        eventPayload.put("localSeq", localSeq);
        eventPayload.put("baseServerClock", baseServerClock);
        eventPayload.put("onlineUsers", onlineUsers(room));
        broadcast(room, "document.update", key, eventPayload, null);
    }

    private void snapshot(CurrentUser currentUser, WebSocketSession session, String requestId, UUID documentId) {
        requireDocumentId(documentId);
        DocumentSummary document = documentService.requireView(currentUser, documentId);
        DocumentRoomKey key = new DocumentRoomKey(currentUser.workspaceId(), documentId);
        DocumentRoom room = room(key, document);
        sendSnapshot(session, requestId, key, room);
    }

    @Scheduled(fixedDelayString = "${colla.docs.collaboration.autosave-delay-ms:1000}")
    @Transactional
    public void flushDirtySnapshots() {
        for (Map.Entry<DocumentRoomKey, DirtySnapshot> entry : List.copyOf(dirtySnapshots.entrySet())) {
            DocumentRoomKey key = entry.getKey();
            DirtySnapshot snapshot = entry.getValue();
            if (!dirtySnapshots.remove(key, snapshot)) {
                continue;
            }
            documentRepository.updateDocumentSnapshot(key.workspaceId(), key.documentId(), snapshot.title(), snapshot.content(), snapshot.actorId());
            documentRepository.replaceBlocks(key.workspaceId(), key.documentId(), documentService.blocksFromContent(snapshot.content()), snapshot.actorId());
            documentService.reanchorSelectionComments(key.workspaceId(), key.documentId(), snapshot.content());
            documentRepository.markCollaborationStateSaved(key.workspaceId(), key.documentId(), snapshot.serverClock());
            DocumentRoom room = rooms.get(key);
            if (room != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("documentId", key.documentId().toString());
                payload.put("serverClock", snapshot.serverClock());
                payload.put("stateVector", Long.toString(snapshot.serverClock()));
                payload.put("clientId", snapshot.clientId());
                payload.put("localSeq", snapshot.localSeq());
                payload.put("savedAt", Instant.now().toString());
                broadcast(room, "document.saved", key, payload, null);
            }
        }
    }

    @Scheduled(fixedDelayString = "${colla.docs.collaboration.presence-cleanup-delay-ms:30000}")
    public void pruneInactiveRooms() {
        Instant cutoff = Instant.now().minusSeconds(PRESENCE_TTL_SECONDS);
        for (Map.Entry<DocumentRoomKey, DocumentRoom> entry : List.copyOf(rooms.entrySet())) {
            DocumentRoomKey key = entry.getKey();
            DocumentRoom room = entry.getValue();
            if (room.prune(cutoff)) {
                broadcastAwareness(room, key, null);
            }
            if (room.isEmpty()) {
                rooms.remove(key, room);
            }
        }
    }

    private DocumentRoom room(DocumentRoomKey key, DocumentSummary document) {
        return rooms.computeIfAbsent(key, ignored -> {
            DocumentSummary summary = document == null
                ? documentRepository.findDocument(key.workspaceId(), key.documentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"))
                : document;
            DocumentCollaborationState state = documentRepository.findCollaborationState(key.workspaceId(), key.documentId()).orElse(null);
            String content = state == null
                ? documentRepository.findContent(key.workspaceId(), key.documentId()).orElse("")
                : state.snapshotContent();
            long serverClock = state == null ? summary.currentVersionNo() : state.serverClock();
            String stateVector = state == null ? Long.toString(serverClock) : state.stateVector();
            return new DocumentRoom(summary.title(), content, stateVector, serverClock);
        });
    }

    private void sendSnapshot(WebSocketSession session, String requestId, DocumentRoomKey key, DocumentRoom room) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("documentId", key.documentId().toString());
        payload.put("encoding", SNAPSHOT_ENCODING);
        payload.put("title", room.title);
        payload.put("content", room.content);
        payload.put("serverClock", room.clock.get());
        payload.put("stateVector", room.stateVector);
        payload.put("onlineUsers", onlineUsers(room));
        send(session, "document.snapshot", key, payload);
    }

    private void broadcastAwareness(DocumentRoom room, DocumentRoomKey key, String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("documentId", key.documentId().toString());
        payload.put("serverClock", room.clock.get());
        payload.put("stateVector", room.stateVector);
        payload.put("onlineUsers", onlineUsers(room));
        broadcast(room, "document.awareness.update", key, payload, null);
    }

    private List<Map<String, Object>> onlineUsers(DocumentRoom room) {
        return room.presences().stream()
            .map(presence -> {
                Map<String, Object> user = new HashMap<>();
                user.put("userId", presence.userId().toString());
                user.put("username", presence.username());
                user.put("displayName", presence.displayName());
                user.put("clientId", presence.clientId());
                user.put("color", presence.color());
                user.put("cursor", presence.cursor());
                user.put("editing", presence.editing());
                user.put("seenAt", presence.seenAt().toString());
                return user;
            })
            .toList();
    }

    private void broadcast(DocumentRoom room, String type, DocumentRoomKey key, Map<String, Object> payload, WebSocketSession except) {
        for (WebSocketSession target : room.sessions()) {
            if (target == except) {
                continue;
            }
            send(target, type, key, payload);
        }
    }

    private void send(WebSocketSession session, String type, DocumentRoomKey key, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        WebSocketEventPayload event = WebSocketEventPayload.of(type, key.workspaceId(), "document", key.documentId(), payload);
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        } catch (Exception ignored) {
            // REST fetch and snapshot.request recover missed realtime frames after reconnect.
        }
    }

    private void sendError(WebSocketSession session, String requestId, UUID workspaceId, UUID documentId, String message) {
        if (!session.isOpen()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("documentId", documentId == null ? "" : documentId.toString());
        payload.put("message", message == null || message.isBlank() ? "Document collaboration error" : message);
        WebSocketEventPayload event = WebSocketEventPayload.of("document.error", workspaceId, "document", documentId, payload);
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        } catch (Exception ignored) {
            // The client falls back to REST when the socket is unhealthy.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> command) {
        Object value = command.get("payload");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cursor(Map<String, Object> payload) {
        Object value = payload.get("cursor");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private void requireDocumentId(UUID documentId) {
        if (documentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document id is required");
        }
    }

    private String normalizeClientId(String clientId, WebSocketSession session) {
        return clientId == null || clientId.isBlank() ? "session:" + session.getId() : clientId;
    }

    private String colorFor(UUID userId) {
        String[] colors = { "#1677ff", "#13a8a8", "#d46b08", "#722ed1", "#eb2f96", "#389e0d", "#fa8c16", "#2f54eb" };
        return colors[Math.floorMod(userId.hashCode(), colors.length)];
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(asString(value));
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(asString(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid collaboration snapshot");
        }
    }

    private record DocumentRoomKey(UUID workspaceId, UUID documentId) {
    }

    private record Presence(
        UUID userId,
        String username,
        String displayName,
        String clientId,
        String color,
        Map<String, Object> cursor,
        boolean editing,
        Instant seenAt
    ) {
    }

    private record DirtySnapshot(String title, String content, long serverClock, String clientId, long localSeq, UUID actorId, Instant updatedAt) {
    }

    private static final class DocumentRoom {
        private final Map<String, WebSocketSession> sessionsById = new ConcurrentHashMap<>();
        private final Map<String, Presence> presencesBySessionId = new ConcurrentHashMap<>();
        private final AtomicLong clock;
        private volatile String title;
        private volatile String content;
        private volatile String stateVector;

        private DocumentRoom(String title, String content, String stateVector, long serverClock) {
            this.title = title;
            this.content = content;
            this.stateVector = stateVector;
            this.clock = new AtomicLong(serverClock);
        }

        private void add(WebSocketSession session, Presence presence) {
            sessionsById.put(session.getId(), session);
            presencesBySessionId.put(session.getId(), presence);
        }

        private boolean remove(WebSocketSession session) {
            WebSocketSession removed = sessionsById.remove(session.getId());
            presencesBySessionId.remove(session.getId());
            return removed != null;
        }

        private Presence presence(WebSocketSession session) {
            return presencesBySessionId.get(session.getId());
        }

        private List<WebSocketSession> sessions() {
            return List.copyOf(sessionsById.values());
        }

        private List<Presence> presences() {
            return List.copyOf(presencesBySessionId.values());
        }

        private boolean isEmpty() {
            return sessionsById.isEmpty();
        }

        private boolean prune(Instant cutoff) {
            boolean changed = false;
            for (Map.Entry<String, WebSocketSession> entry : List.copyOf(sessionsById.entrySet())) {
                Presence presence = presencesBySessionId.get(entry.getKey());
                if (!entry.getValue().isOpen() || presence == null || presence.seenAt().isBefore(cutoff)) {
                    sessionsById.remove(entry.getKey());
                    presencesBySessionId.remove(entry.getKey());
                    changed = true;
                }
            }
            return changed;
        }
    }
}
