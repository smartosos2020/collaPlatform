package com.colla.platform.modules.knowledge.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCollaborationState;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCollaborationHealth;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.websocket.WebSocketEventPayload;
import com.colla.platform.shared.websocket.CollaborationMessageHandler;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
@ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class KnowledgeContentCollaborationService implements CollaborationMessageHandler, KnowledgeCollaborationHealthQuery {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SNAPSHOT_ENCODING = "snapshot-v1";
    private static final long PRESENCE_TTL_SECONDS = 120;

    private final KnowledgeContentService contentService;
    private final KnowledgeContentRepository contentRepository;
    private final ObjectMapper objectMapper;
    private final Map<ContentRoomKey, ContentRoom> rooms = new ConcurrentHashMap<>();
    private final Map<ContentRoomKey, DirtySnapshot> dirtySnapshots = new ConcurrentHashMap<>();

    public KnowledgeContentCollaborationService(
        KnowledgeContentService contentService,
        KnowledgeContentRepository contentRepository,
        ObjectMapper objectMapper
    ) {
        this.contentService = contentService;
        this.contentRepository = contentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String type) {
        return type != null && type.startsWith("knowledge.content.");
    }

    public KnowledgeContentCollaborationHealth health(CurrentUser currentUser, UUID itemId) {
        requireItemId(itemId);
        contentService.requireView(currentUser, itemId);
        ContentRoomKey key = new ContentRoomKey(currentUser.workspaceId(), itemId);
        ContentRoom room = rooms.get(key);
        KnowledgeContentCollaborationState state = contentRepository.findCollaborationState(currentUser.workspaceId(), itemId).orElse(null);
        long serverClock = room == null ? state == null ? 0 : state.serverClock() : room.clock.get();
        String stateVector = room == null ? state == null ? Long.toString(serverClock) : state.stateVector() : room.stateVector;
        return new KnowledgeContentCollaborationHealth(
            itemId,
            serverClock,
            room == null ? 0 : room.presences().size(),
            dirtySnapshots.containsKey(key),
            stateVector,
            state == null ? null : state.lastSavedAt(),
            state == null ? null : state.updatedAt()
        );
    }

    @Override
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
        UUID itemId = parseUuid(asString(command.get("itemId")));
        if (itemId == null) {
            itemId = parseUuid(asString(payload(command).get("itemId")));
        }
        if (itemId == null) {
            itemId = parseUuid(asString(command.get("itemId")));
        }
        try {
            switch (type) {
                case "knowledge.content.join" -> join(currentUser, session, requestId, itemId, payload(command));
                case "knowledge.content.leave" -> leave(currentUser, session, requestId, itemId);
                case "knowledge.content.awareness.update" -> awareness(currentUser, session, requestId, itemId, payload(command));
                case "knowledge.content.update" -> update(currentUser, session, requestId, itemId, payload(command));
                case "knowledge.content.snapshot.request" -> snapshot(currentUser, session, requestId, itemId);
                default -> sendError(session, requestId, currentUser.workspaceId(), itemId, "Unsupported knowledge content command");
            }
        } catch (ResponseStatusException exception) {
            sendError(session, requestId, currentUser.workspaceId(), itemId, exception.getReason());
        } catch (RuntimeException exception) {
            sendError(session, requestId, currentUser.workspaceId(), itemId, "Knowledge content collaboration command failed");
        }
    }

    @Override
    public void disconnect(WebSocketSession session, CurrentUser currentUser) {
        List<ContentRoomKey> affectedRooms = new ArrayList<>();
        for (Map.Entry<ContentRoomKey, ContentRoom> entry : rooms.entrySet()) {
            if (entry.getValue().remove(session)) {
                affectedRooms.add(entry.getKey());
            }
        }
        for (ContentRoomKey key : affectedRooms) {
            ContentRoom room = rooms.get(key);
            if (room == null) {
                continue;
            }
            broadcastAwareness(room, key, null);
            if (room.isEmpty()) {
                rooms.remove(key, room);
            }
        }
    }

    private void join(CurrentUser currentUser, WebSocketSession session, String requestId, UUID itemId, Map<String, Object> payload) {
        requireItemId(itemId);
        KnowledgeBaseItem document = contentService.requireView(currentUser, itemId);
        ContentRoomKey key = new ContentRoomKey(currentUser.workspaceId(), itemId);
        ContentRoom room = room(key, document);
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

    private void leave(CurrentUser currentUser, WebSocketSession session, String requestId, UUID itemId) {
        requireItemId(itemId);
        ContentRoomKey key = new ContentRoomKey(currentUser.workspaceId(), itemId);
        ContentRoom room = rooms.get(key);
        if (room == null) {
            return;
        }
        room.remove(session);
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("itemId", itemId.toString());
        send(session, "knowledge.content.leave", key, payload);
        broadcastAwareness(room, key, null);
        if (room.isEmpty()) {
            rooms.remove(key, room);
        }
    }

    private void awareness(CurrentUser currentUser, WebSocketSession session, String requestId, UUID itemId, Map<String, Object> payload) {
        requireItemId(itemId);
        contentService.requireView(currentUser, itemId);
        ContentRoomKey key = new ContentRoomKey(currentUser.workspaceId(), itemId);
        ContentRoom room = room(key, null);
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

    private void update(CurrentUser currentUser, WebSocketSession session, String requestId, UUID itemId, Map<String, Object> payload) {
        requireItemId(itemId);
        KnowledgeBaseItem document = contentService.requireEdit(currentUser, itemId);
        ContentRoomKey key = new ContentRoomKey(currentUser.workspaceId(), itemId);
        ContentRoom room = room(key, document);
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
        snapshotPayload.put("blocks", contentService.blocksFromContent(content));

        contentRepository.upsertCollaborationState(
            key.workspaceId(),
            key.itemId(),
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
        eventPayload.put("itemId", itemId.toString());
        eventPayload.put("encoding", SNAPSHOT_ENCODING);
        eventPayload.put("title", title);
        eventPayload.put("content", content);
        eventPayload.put("serverClock", serverClock);
        eventPayload.put("stateVector", stateVector);
        eventPayload.put("clientId", clientId);
        eventPayload.put("localSeq", localSeq);
        eventPayload.put("baseServerClock", baseServerClock);
        eventPayload.put("onlineUsers", onlineUsers(room));
        broadcast(room, "knowledge.content.update", key, eventPayload, null);
    }

    private void snapshot(CurrentUser currentUser, WebSocketSession session, String requestId, UUID itemId) {
        requireItemId(itemId);
        KnowledgeBaseItem document = contentService.requireView(currentUser, itemId);
        ContentRoomKey key = new ContentRoomKey(currentUser.workspaceId(), itemId);
        ContentRoom room = room(key, document);
        sendSnapshot(session, requestId, key, room);
    }

    @Transactional
    public void flushDirtySnapshots() {
        for (Map.Entry<ContentRoomKey, DirtySnapshot> entry : List.copyOf(dirtySnapshots.entrySet())) {
            ContentRoomKey key = entry.getKey();
            DirtySnapshot snapshot = entry.getValue();
            if (!dirtySnapshots.remove(key, snapshot)) {
                continue;
            }
            contentRepository.updateContentSnapshot(key.workspaceId(), key.itemId(), snapshot.title(), snapshot.content(), snapshot.actorId());
            contentRepository.replaceBlocks(key.workspaceId(), key.itemId(), contentService.blocksFromContent(snapshot.content()), snapshot.actorId());
            contentService.reanchorSelectionComments(key.workspaceId(), key.itemId(), snapshot.content());
            contentRepository.markCollaborationStateSaved(key.workspaceId(), key.itemId(), snapshot.serverClock());
            ContentRoom room = rooms.get(key);
            if (room != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("itemId", key.itemId().toString());
                payload.put("serverClock", snapshot.serverClock());
                payload.put("stateVector", Long.toString(snapshot.serverClock()));
                payload.put("clientId", snapshot.clientId());
                payload.put("localSeq", snapshot.localSeq());
                payload.put("savedAt", Instant.now().toString());
                broadcast(room, "knowledge.content.saved", key, payload, null);
            }
        }
    }

    public void pruneInactiveRooms() {
        Instant cutoff = Instant.now().minusSeconds(PRESENCE_TTL_SECONDS);
        for (Map.Entry<ContentRoomKey, ContentRoom> entry : List.copyOf(rooms.entrySet())) {
            ContentRoomKey key = entry.getKey();
            ContentRoom room = entry.getValue();
            if (room.prune(cutoff)) {
                broadcastAwareness(room, key, null);
            }
            if (room.isEmpty()) {
                rooms.remove(key, room);
            }
        }
    }

    private ContentRoom room(ContentRoomKey key, KnowledgeBaseItem document) {
        return rooms.computeIfAbsent(key, ignored -> {
            KnowledgeBaseItem summary = document == null
                ? contentRepository.findItem(key.workspaceId(), key.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"))
                : document;
            KnowledgeContentCollaborationState state = contentRepository.findCollaborationState(key.workspaceId(), key.itemId()).orElse(null);
            String activeContent = contentRepository.findContent(key.workspaceId(), key.itemId()).orElse("");
            String content = state == null || state.snapshotContent() == null || state.snapshotContent().isBlank()
                ? activeContent
                : state.snapshotContent();
            long serverClock = state == null ? summary.currentVersionNo() : state.serverClock();
            String stateVector = state == null ? Long.toString(serverClock) : state.stateVector();
            return new ContentRoom(summary.title(), content, stateVector, serverClock);
        });
    }

    private void sendSnapshot(WebSocketSession session, String requestId, ContentRoomKey key, ContentRoom room) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("itemId", key.itemId().toString());
        payload.put("encoding", SNAPSHOT_ENCODING);
        payload.put("title", room.title);
        payload.put("content", room.content);
        payload.put("serverClock", room.clock.get());
        payload.put("stateVector", room.stateVector);
        payload.put("onlineUsers", onlineUsers(room));
        send(session, "knowledge.content.snapshot", key, payload);
    }

    private void broadcastAwareness(ContentRoom room, ContentRoomKey key, String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("itemId", key.itemId().toString());
        payload.put("serverClock", room.clock.get());
        payload.put("stateVector", room.stateVector);
        payload.put("onlineUsers", onlineUsers(room));
        broadcast(room, "knowledge.content.awareness.update", key, payload, null);
    }

    private List<Map<String, Object>> onlineUsers(ContentRoom room) {
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

    private void broadcast(ContentRoom room, String type, ContentRoomKey key, Map<String, Object> payload, WebSocketSession except) {
        for (WebSocketSession target : room.sessions()) {
            if (target == except) {
                continue;
            }
            send(target, type, key, payload);
        }
    }

    private void send(WebSocketSession session, String type, ContentRoomKey key, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        WebSocketEventPayload event = WebSocketEventPayload.of(type, key.workspaceId(), "knowledge_content", key.itemId(), payload);
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        } catch (Exception ignored) {
            // REST fetch and snapshot.request recover missed realtime frames after reconnect.
        }
    }

    private void sendError(WebSocketSession session, String requestId, UUID workspaceId, UUID itemId, String message) {
        if (!session.isOpen()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("itemId", itemId == null ? "" : itemId.toString());
        payload.put("message", message == null || message.isBlank() ? "Knowledge content collaboration error" : message);
        WebSocketEventPayload event = WebSocketEventPayload.of("knowledge.content.error", workspaceId, "knowledge_content", itemId, payload);
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

    private void requireItemId(UUID itemId) {
        if (itemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge content item id is required");
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

    private record ContentRoomKey(UUID workspaceId, UUID itemId) {
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

    private static final class ContentRoom {
        private final Map<String, WebSocketSession> sessionsById = new ConcurrentHashMap<>();
        private final Map<String, Presence> presencesBySessionId = new ConcurrentHashMap<>();
        private final AtomicLong clock;
        private volatile String title;
        private volatile String content;
        private volatile String stateVector;

        private ContentRoom(String title, String content, String stateVector, long serverClock) {
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





