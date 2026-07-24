package com.colla.platform.modules.im.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.im.domain.ImModels.ConversationDetail;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.im.domain.ImModels.MessagePage;
import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import com.colla.platform.modules.im.domain.ImModels.UnreadState;
import com.colla.platform.modules.im.infrastructure.ImRepository;
import com.colla.platform.modules.platform.application.InternalLinkService;
import com.colla.platform.modules.platform.domain.PlatformModels.ParsedInternalLink;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.websocket.WebSocketMessageSender;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_.-]{2,64})");
    private static final Pattern LINK_PATTERN = Pattern.compile("(colla://\\S+|https?://\\S+|/im\\?\\S*messageId=[0-9a-fA-F-]{36}\\S*|/(?:issues|docs|bases|approvals)/[0-9a-fA-F-]{36}(?:/\\S*)?)");

    private final ImRepository imRepository;
    private final InternalLinkService internalLinkService;
    private final TransactionalOutbox eventRepository;
    private final WebSocketMessageSender webSocketMessageSender;
    private final PlatformObjectRepository objectRepository;

    public ImService(
        ImRepository imRepository,
        InternalLinkService internalLinkService,
        TransactionalOutbox eventRepository,
        WebSocketMessageSender webSocketMessageSender,
        PlatformObjectRepository objectRepository
    ) {
        this.imRepository = imRepository;
        this.internalLinkService = internalLinkService;
        this.eventRepository = eventRepository;
        this.webSocketMessageSender = webSocketMessageSender;
        this.objectRepository = objectRepository;
    }

    public List<ConversationSummary> listConversations(CurrentUser currentUser) {
        return imRepository.listConversations(currentUser.workspaceId(), currentUser.id());
    }

    @Transactional
    public ConversationDetail createConversation(CurrentUser currentUser, String conversationType, String title, List<UUID> memberIds) {
        String type = normalizeConversationType(conversationType);
        List<UUID> normalizedMembers = new ArrayList<>(new LinkedHashSet<>(memberIds == null ? List.of() : memberIds));
        if (!normalizedMembers.contains(currentUser.id())) {
            normalizedMembers.add(currentUser.id());
        }
        if ("direct".equals(type) && normalizedMembers.size() != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct conversation requires exactly two members");
        }
        if (normalizedMembers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation requires members");
        }

        UUID conversationId = imRepository.createConversation(
            currentUser.workspaceId(),
            type,
            title == null || title.isBlank() ? defaultTitle(type) : title.trim(),
            currentUser.id(),
            currentUser.id()
        );
        for (UUID memberId : normalizedMembers) {
            imRepository.addMember(
                currentUser.workspaceId(),
                conversationId,
                memberId,
                currentUser.id().equals(memberId) ? "owner" : "member"
            );
        }
        return getConversation(currentUser, conversationId);
    }

    public ConversationDetail getConversation(CurrentUser currentUser, UUID conversationId) {
        return imRepository.findConversation(currentUser.workspaceId(), conversationId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    @Transactional
    public ConversationDetail addMembers(CurrentUser currentUser, UUID conversationId, List<UUID> memberIds) {
        ConversationDetail conversation = getConversation(currentUser, conversationId);
        List<UUID> normalizedMemberIds = new ArrayList<>(new LinkedHashSet<>(memberIds == null ? List.of() : memberIds));
        if (normalizedMemberIds.isEmpty()) {
            return conversation;
        }
        if ("direct".equals(conversation.conversationType())) {
            imRepository.updateConversationType(currentUser.workspaceId(), conversationId, "group");
        }
        for (UUID memberId : normalizedMemberIds) {
            imRepository.addMember(currentUser.workspaceId(), conversationId, memberId, "member");
        }
        ConversationDetail updated = getConversation(currentUser, conversationId);
        pushConversationEvents(currentUser, conversationId, updated);
        return updated;
    }

    @Transactional
    public ConversationDetail removeMember(CurrentUser currentUser, UUID conversationId, UUID memberId) {
        requireOwner(currentUser, conversationId);
        if (currentUser.id().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use leave conversation to remove yourself");
        }
        ConversationDetail before = getConversation(currentUser, conversationId);
        imRepository.removeMember(currentUser.workspaceId(), conversationId, memberId);
        ConversationDetail updated = getConversation(currentUser, conversationId);
        pushConversationEvents(currentUser, conversationId, updated, before.members().stream().map(member -> member.userId()).toList());
        return updated;
    }

    @Transactional
    public void leaveConversation(CurrentUser currentUser, UUID conversationId) {
        ConversationDetail conversation = getConversation(currentUser, conversationId);
        if ("direct".equals(conversation.conversationType()) || "system".equals(conversation.conversationType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation cannot be left");
        }
        imRepository.removeMember(currentUser.workspaceId(), conversationId, currentUser.id());
        pushConversationEvents(currentUser, conversationId, conversation, List.of(currentUser.id()));
    }

    @Transactional
    public void closeDirectConversation(CurrentUser currentUser, UUID conversationId) {
        ConversationDetail conversation = getConversation(currentUser, conversationId);
        if (!"direct".equals(conversation.conversationType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only direct conversation can be closed");
        }
        imRepository.removeMember(currentUser.workspaceId(), conversationId, currentUser.id());
        pushConversationEvents(currentUser, conversationId, conversation, List.of(currentUser.id()));
    }

    @Transactional
    public ConversationDetail setConversationMuted(CurrentUser currentUser, UUID conversationId, boolean muted) {
        requireMember(currentUser, conversationId);
        imRepository.setConversationMuted(currentUser.workspaceId(), conversationId, currentUser.id(), muted);
        ConversationDetail conversation = getConversation(currentUser, conversationId);
        pushConversationPreferenceEvent(currentUser, conversationId, conversation);
        return conversation;
    }

    @Transactional
    public ConversationDetail setConversationPinned(CurrentUser currentUser, UUID conversationId, boolean pinned) {
        requireMember(currentUser, conversationId);
        imRepository.setConversationPinned(currentUser.workspaceId(), conversationId, currentUser.id(), pinned);
        ConversationDetail conversation = getConversation(currentUser, conversationId);
        pushConversationPreferenceEvent(currentUser, conversationId, conversation);
        return conversation;
    }

    public MessagePage listMessages(CurrentUser currentUser, UUID conversationId, UUID beforeId, Long afterSeq, int limit) {
        requireMember(currentUser, conversationId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        if (afterSeq != null) {
            return new MessagePage(
                imRepository.listMessagesAfterSeq(currentUser.workspaceId(), conversationId, currentUser.id(), afterSeq, boundedLimit),
                null
            );
        }
        List<MessageSummary> messages = imRepository.listMessages(
            currentUser.workspaceId(),
            conversationId,
            currentUser.id(),
            beforeId,
            boundedLimit + 1
        );
        UUID nextCursor = messages.size() > boundedLimit ? messages.get(boundedLimit).id() : null;
        List<MessageSummary> items = messages.stream().limit(boundedLimit).toList();
        return new MessagePage(items, nextCursor);
    }

    public MessagePage listMessageContext(CurrentUser currentUser, UUID conversationId, UUID messageId, int limit) {
        requireMember(currentUser, conversationId);
        requireExistingMessage(currentUser, conversationId, messageId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<MessageSummary> messages = imRepository.listMessageContext(
            currentUser.workspaceId(),
            conversationId,
            currentUser.id(),
            messageId,
            boundedLimit + 1
        );
        UUID nextCursor = messages.size() > boundedLimit ? messages.get(boundedLimit).id() : null;
        List<MessageSummary> items = messages.stream().limit(boundedLimit).toList();
        return new MessagePage(items, nextCursor);
    }

    public MessageSummary getMessage(CurrentUser currentUser, UUID conversationId, UUID messageId) {
        requireMember(currentUser, conversationId);
        return requireExistingMessage(currentUser, conversationId, messageId);
    }

    public MessagePage searchMessages(CurrentUser currentUser, UUID conversationId, String query, String targetType, int limit) {
        requireMember(currentUser, conversationId);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        return new MessagePage(
            imRepository.searchMessages(
                currentUser.workspaceId(),
                conversationId,
                currentUser.id(),
                query,
                normalizeSearchTargetType(targetType),
                boundedLimit
            ),
            null
        );
    }

    @Transactional
    public MessageSummary sendMessage(
        CurrentUser currentUser,
        UUID conversationId,
        String clientMessageId,
        String messageType,
        String content
    ) {
        requireMember(currentUser, conversationId);
        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }
        String normalizedType = messageType == null || messageType.isBlank() ? "text" : messageType.toLowerCase(Locale.ROOT);
        String normalizedClientId = clientMessageId == null || clientMessageId.isBlank()
            ? UUID.randomUUID().toString()
            : clientMessageId.trim();

        MessageSummary existing = imRepository.findMessageByClientId(
            currentUser.workspaceId(),
            conversationId,
            currentUser.id(),
            normalizedClientId
        ).orElse(null);
        if (existing != null) {
            return existing;
        }

        MessageSummary inserted = imRepository.insertMessage(
            currentUser.workspaceId(),
            conversationId,
            currentUser.id(),
            normalizedClientId,
            normalizedType,
            trimmedContent
        );

        Set<UUID> mentionedUserIds = resolveMentions(currentUser, conversationId, trimmedContent);
        for (UUID mentionedUserId : mentionedUserIds) {
            imRepository.addMention(currentUser.workspaceId(), conversationId, inserted.id(), mentionedUserId);
            appendMentionEvents(currentUser, conversationId, inserted.id(), mentionedUserId);
        }
        resolveLinks(currentUser, conversationId, inserted.id(), trimmedContent);
        imRepository.updateConversationLastMessage(currentUser.workspaceId(), conversationId, inserted.id());

        MessageSummary message = imRepository.findMessageForUser(currentUser.workspaceId(), conversationId, inserted.id(), currentUser.id()).orElse(inserted);
        registerMessageObject(currentUser.workspaceId(), conversationId, message);
        appendMessageCreatedEvent(currentUser, conversationId, message);
        pushMessageEvents(currentUser, conversationId, message, "message.created");
        return message;
    }

    @Transactional
    public MessageSummary editMessage(CurrentUser currentUser, UUID conversationId, UUID messageId, String content) {
        requireMember(currentUser, conversationId);
        MessageSummary message = requireExistingMessage(currentUser, conversationId, messageId);
        if (!message.senderId().equals(currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only message sender can edit");
        }
        if (message.revokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revoked message cannot be edited");
        }
        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }
        imRepository.editMessage(currentUser.workspaceId(), conversationId, messageId, currentUser.id(), trimmedContent);
        MessageSummary updated = requireExistingMessage(currentUser, conversationId, messageId);
        appendMessageMutationEvent(currentUser, "message.edited", conversationId, updated);
        pushMessageEvents(currentUser, conversationId, updated, "message.edited");
        return updated;
    }

    @Transactional
    public MessageSummary revokeMessage(CurrentUser currentUser, UUID conversationId, UUID messageId) {
        requireMember(currentUser, conversationId);
        MessageSummary message = requireExistingMessage(currentUser, conversationId, messageId);
        if (!message.senderId().equals(currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only message sender can revoke");
        }
        imRepository.revokeMessage(currentUser.workspaceId(), conversationId, messageId, currentUser.id());
        MessageSummary updated = requireExistingMessage(currentUser, conversationId, messageId);
        appendMessageMutationEvent(currentUser, "message.revoked", conversationId, updated);
        pushMessageEvents(currentUser, conversationId, updated, "message.revoked");
        return updated;
    }

    @Transactional
    public MessageSummary pinMessage(CurrentUser currentUser, UUID conversationId, UUID messageId, boolean pinned) {
        requireMember(currentUser, conversationId);
        requireExistingMessage(currentUser, conversationId, messageId);
        imRepository.setPinned(currentUser.workspaceId(), conversationId, messageId, currentUser.id(), pinned);
        MessageSummary updated = requireExistingMessage(currentUser, conversationId, messageId);
        appendMessageMutationEvent(currentUser, pinned ? "message.pinned" : "message.unpinned", conversationId, updated);
        pushMessageEvents(currentUser, conversationId, updated, pinned ? "message.pinned" : "message.unpinned");
        return updated;
    }

    @Transactional
    public MessageSummary toggleReaction(CurrentUser currentUser, UUID conversationId, UUID messageId, String emoji) {
        requireMember(currentUser, conversationId);
        requireExistingMessage(currentUser, conversationId, messageId);
        String normalizedEmoji = emoji == null ? "" : emoji.trim();
        if (normalizedEmoji.isBlank() || normalizedEmoji.length() > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid emoji");
        }
        imRepository.toggleReaction(currentUser.workspaceId(), conversationId, messageId, currentUser.id(), normalizedEmoji);
        MessageSummary updated = requireExistingMessage(currentUser, conversationId, messageId);
        appendMessageMutationEvent(currentUser, "message.reaction.toggled", conversationId, updated);
        pushMessageEvents(currentUser, conversationId, updated, "message.reaction.toggled");
        return updated;
    }

    @Transactional
    public UnreadState markRead(CurrentUser currentUser, UUID conversationId, UUID messageId) {
        requireMember(currentUser, conversationId);
        if (messageId != null) {
            requireExistingMessage(currentUser, conversationId, messageId);
        }
        imRepository.markRead(currentUser.workspaceId(), conversationId, currentUser.id(), messageId);
        UnreadState state = unreadState(currentUser, conversationId);
        runAfterCommit(() -> {
            webSocketMessageSender.sendToUser(
                currentUser.id(),
                "conversation.read",
                currentUser.workspaceId(),
                "conversation",
                conversationId,
                Map.of("conversationId", conversationId, "unread", state)
            );
            webSocketMessageSender.sendToUser(
                currentUser.id(),
                "unread.changed",
                currentUser.workspaceId(),
                "conversation",
                conversationId,
                Map.of("conversationId", conversationId, "unread", state)
            );
        });
        return state;
    }

    public UnreadState unreadState(CurrentUser currentUser, UUID conversationId) {
        return new UnreadState(
            conversationId,
            imRepository.unreadCount(currentUser.workspaceId(), conversationId, currentUser.id()),
            imRepository.totalUnreadCount(currentUser.workspaceId(), currentUser.id())
        );
    }

    private Set<UUID> resolveMentions(CurrentUser currentUser, UUID conversationId, String content) {
        Matcher matcher = MENTION_PATTERN.matcher(content);
        List<String> usernames = new ArrayList<>();
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        Set<UUID> memberIds = new LinkedHashSet<>(imRepository.listMemberIds(currentUser.workspaceId(), conversationId));
        Set<UUID> mentionedUserIds = new LinkedHashSet<>(imRepository.findActiveUserIdsByUsernames(currentUser.workspaceId(), usernames));
        mentionedUserIds.retainAll(memberIds);
        mentionedUserIds.remove(currentUser.id());
        return mentionedUserIds;
    }

    private void resolveLinks(CurrentUser currentUser, UUID conversationId, UUID messageId, String content) {
        Matcher matcher = LINK_PATTERN.matcher(content);
        Set<String> links = new LinkedHashSet<>();
        while (matcher.find()) {
            links.add(stripTrailingPunctuation(matcher.group(1)));
        }
        for (String link : links) {
            ParsedInternalLink parsed = internalLinkService.resolve(currentUser, link);
            if (parsed.summary() == null) {
                continue;
            }
            imRepository.addLink(
                currentUser.workspaceId(),
                conversationId,
                messageId,
                link,
                parsed.objectType(),
                parsed.objectId(),
                parsed.webPath(),
                parsed.deepLink(),
                parsed.summary()
            );
        }
    }

    /**
     * Defers a WebSocket push until after the surrounding transaction commits.
     * Without this, the frontend's event-driven refetch can race ahead of the
     * commit and cache a stale (pre-commit) result, leaving the UI one message
     * behind until the next event arrives.
     */
    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void pushMessageEvents(CurrentUser currentUser, UUID conversationId, MessageSummary message, String eventType) {
        ConversationDetail conversation = getConversation(currentUser, conversationId);
        runAfterCommit(() -> {
            for (UUID memberId : imRepository.listMemberIds(currentUser.workspaceId(), conversationId)) {
                webSocketMessageSender.sendToUser(
                    memberId,
                    eventType,
                    currentUser.workspaceId(),
                    "message",
                    message.id(),
                    Map.of("conversationId", conversationId, "message", message)
                );
                webSocketMessageSender.sendToUser(
                    memberId,
                    "conversation.updated",
                    currentUser.workspaceId(),
                    "conversation",
                    conversationId,
                    Map.of("conversationId", conversationId, "conversation", conversation)
                );
                webSocketMessageSender.sendToUser(
                    memberId,
                    "unread.changed",
                    currentUser.workspaceId(),
                    "conversation",
                    conversationId,
                    Map.of(
                        "conversationId",
                        conversationId,
                        "unreadCount",
                        imRepository.unreadCount(currentUser.workspaceId(), conversationId, memberId),
                        "totalUnreadCount",
                        imRepository.totalUnreadCount(currentUser.workspaceId(), memberId)
                    )
                );
            }
        });
    }

    private void pushConversationEvents(CurrentUser currentUser, UUID conversationId, ConversationDetail conversation) {
        pushConversationEvents(currentUser, conversationId, conversation, List.of());
    }

    private void pushConversationPreferenceEvent(CurrentUser currentUser, UUID conversationId, ConversationDetail conversation) {
        runAfterCommit(() ->
            webSocketMessageSender.sendToUser(
                currentUser.id(),
                "conversation.updated",
                currentUser.workspaceId(),
                "conversation",
                conversationId,
                Map.of("conversationId", conversationId, "conversation", conversation)
            )
        );
    }

    private void pushConversationEvents(
        CurrentUser currentUser,
        UUID conversationId,
        ConversationDetail conversation,
        List<UUID> extraRecipientIds
    ) {
        Set<UUID> recipientIds = new LinkedHashSet<>(imRepository.listMemberIds(currentUser.workspaceId(), conversationId));
        recipientIds.addAll(extraRecipientIds);
        conversation.members().stream().map(member -> member.userId()).forEach(recipientIds::add);
        runAfterCommit(() -> {
            for (UUID memberId : recipientIds) {
                webSocketMessageSender.sendToUser(
                    memberId,
                    "conversation.updated",
                    currentUser.workspaceId(),
                    "conversation",
                    conversationId,
                    Map.of("conversationId", conversationId, "conversation", conversation)
                );
                webSocketMessageSender.sendToUser(
                    memberId,
                    "unread.changed",
                    currentUser.workspaceId(),
                    "conversation",
                    conversationId,
                    Map.of(
                        "conversationId",
                        conversationId,
                        "unreadCount",
                        imRepository.unreadCount(currentUser.workspaceId(), conversationId, memberId),
                        "totalUnreadCount",
                        imRepository.totalUnreadCount(currentUser.workspaceId(), memberId)
                    )
                );
            }
        });
    }

    private void appendMessageCreatedEvent(CurrentUser currentUser, UUID conversationId, MessageSummary message) {
        eventRepository.append(
            currentUser.workspaceId(),
            "message.created",
            "message",
            message.id(),
            currentUser.id(),
            Map.of("conversationId", conversationId.toString(), "messageId", message.id().toString()),
            "message.created:" + message.id()
        );
    }

    private void registerMessageObject(UUID workspaceId, UUID conversationId, MessageSummary message) {
        objectRepository.upsertObjectLink(
            workspaceId,
            "message",
            message.id(),
            "/im?conversationId=" + conversationId + "&messageId=" + message.id(),
            "colla://message/" + message.id(),
            messageTitle(message)
        );
    }

    private String messageTitle(MessageSummary message) {
        String content = message.content() == null ? "" : message.content().replaceAll("\\s+", " ").trim();
        if (content.isBlank()) {
            return message.senderName() + " 的消息";
        }
        String preview = content.length() <= 72 ? content : content.substring(0, 72) + "...";
        return message.senderName() + ": " + preview;
    }

    private void appendMessageMutationEvent(CurrentUser currentUser, String eventType, UUID conversationId, MessageSummary message) {
        eventRepository.append(
            currentUser.workspaceId(),
            eventType,
            "message",
            message.id(),
            currentUser.id(),
            Map.of("conversationId", conversationId.toString(), "messageId", message.id().toString()),
            eventType + ":" + message.id() + ":" + UUID.randomUUID()
        );
    }

    private MessageSummary requireExistingMessage(CurrentUser currentUser, UUID conversationId, UUID messageId) {
        return imRepository.findMessageForUser(currentUser.workspaceId(), conversationId, messageId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    }

    private void appendMentionEvents(CurrentUser currentUser, UUID conversationId, UUID messageId, UUID mentionedUserId) {
        eventRepository.append(
            currentUser.workspaceId(),
            "message.mentioned",
            "message",
            messageId,
            currentUser.id(),
            Map.of(
                "conversationId",
                conversationId.toString(),
                "messageId",
                messageId.toString(),
                "mentionedUserId",
                mentionedUserId.toString()
            ),
            "message.mentioned:" + messageId + ":" + mentionedUserId
        );
        eventRepository.append(
            currentUser.workspaceId(),
            "notification.created",
            "message",
            messageId,
            currentUser.id(),
            Map.of(
                "recipientId",
                mentionedUserId.toString(),
                "notificationType",
                "mention",
                "title",
                currentUser.displayName() + " 在消息中提到了你",
                "body",
                "点击查看会话消息",
                "targetType",
                "message",
                "targetId",
                messageId.toString(),
                "webPath",
                "/im?conversationId=" + conversationId,
                "dedupeKey",
                "mention:" + messageId + ":" + mentionedUserId
            ),
            "notification.mention:" + messageId + ":" + mentionedUserId
        );
    }

    private void requireMember(CurrentUser currentUser, UUID conversationId) {
        if (!imRepository.isMember(currentUser.workspaceId(), conversationId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation access denied");
        }
    }

    private void requireOwner(CurrentUser currentUser, UUID conversationId) {
        if (!imRepository.isOwner(currentUser.workspaceId(), conversationId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation owner required");
        }
    }

    private String normalizeConversationType(String conversationType) {
        if (conversationType == null || conversationType.isBlank()) {
            return "group";
        }
        String type = conversationType.toLowerCase(Locale.ROOT);
        if (!List.of("direct", "group", "project", "system").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation type");
        }
        return type;
    }

    private String normalizeSearchTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return null;
        }
        String type = targetType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("issue", "knowledge_content", "base", "base_table", "base_record", "message", "approval", "file").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message search target type");
        }
        return type;
    }

    private String defaultTitle(String type) {
        return switch (type) {
            case "direct" -> "单聊";
            case "project" -> "项目群";
            case "system" -> "系统通知";
            default -> "新群聊";
        };
    }

    private String stripTrailingPunctuation(String value) {
        return value.replaceAll("[，。；;,.!！?？)）]+$", "");
    }
}
