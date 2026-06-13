package com.colla.platform.modules.im.application;

import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.im.domain.ImModels.ConversationDetail;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.im.domain.ImModels.MessagePage;
import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import com.colla.platform.modules.im.domain.ImModels.UnreadState;
import com.colla.platform.modules.im.infrastructure.ImRepository;
import com.colla.platform.modules.platform.application.InternalLinkService;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.ParsedInternalLink;
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
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_.-]{2,64})");
    private static final Pattern LINK_PATTERN = Pattern.compile("(colla://\\S+|https?://\\S+|/(?:issues|docs|bases|approvals)/[0-9a-fA-F-]{36}(?:/\\S*)?)");

    private final ImRepository imRepository;
    private final InternalLinkService internalLinkService;
    private final DomainEventRepository eventRepository;
    private final WebSocketMessageSender webSocketMessageSender;

    public ImService(
        ImRepository imRepository,
        InternalLinkService internalLinkService,
        DomainEventRepository eventRepository,
        WebSocketMessageSender webSocketMessageSender
    ) {
        this.imRepository = imRepository;
        this.internalLinkService = internalLinkService;
        this.eventRepository = eventRepository;
        this.webSocketMessageSender = webSocketMessageSender;
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
        requireOwner(currentUser, conversationId);
        for (UUID memberId : new LinkedHashSet<>(memberIds == null ? List.of() : memberIds)) {
            imRepository.addMember(currentUser.workspaceId(), conversationId, memberId, "member");
        }
        return getConversation(currentUser, conversationId);
    }

    public MessagePage listMessages(CurrentUser currentUser, UUID conversationId, UUID beforeId, int limit) {
        requireMember(currentUser, conversationId);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
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
        appendMessageCreatedEvent(currentUser, conversationId, message);
        pushMessageEvents(currentUser, conversationId, message);
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
        pushMessageEvents(currentUser, conversationId, updated);
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
        pushMessageEvents(currentUser, conversationId, updated);
        return updated;
    }

    @Transactional
    public MessageSummary pinMessage(CurrentUser currentUser, UUID conversationId, UUID messageId, boolean pinned) {
        requireMember(currentUser, conversationId);
        requireExistingMessage(currentUser, conversationId, messageId);
        imRepository.setPinned(currentUser.workspaceId(), conversationId, messageId, currentUser.id(), pinned);
        MessageSummary updated = requireExistingMessage(currentUser, conversationId, messageId);
        appendMessageMutationEvent(currentUser, pinned ? "message.pinned" : "message.unpinned", conversationId, updated);
        pushMessageEvents(currentUser, conversationId, updated);
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
        pushMessageEvents(currentUser, conversationId, updated);
        return updated;
    }

    @Transactional
    public UnreadState markRead(CurrentUser currentUser, UUID conversationId, UUID messageId) {
        requireMember(currentUser, conversationId);
        imRepository.markRead(currentUser.workspaceId(), conversationId, currentUser.id(), messageId);
        UnreadState state = unreadState(currentUser, conversationId);
        webSocketMessageSender.sendToUser(
            currentUser.id(),
            "conversation.read",
            Map.of("conversationId", conversationId, "unread", state)
        );
        webSocketMessageSender.sendToUser(
            currentUser.id(),
            "unread.changed",
            Map.of("conversationId", conversationId, "unread", state)
        );
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

    private void pushMessageEvents(CurrentUser currentUser, UUID conversationId, MessageSummary message) {
        ConversationDetail conversation = getConversation(currentUser, conversationId);
        for (UUID memberId : imRepository.listMemberIds(currentUser.workspaceId(), conversationId)) {
            webSocketMessageSender.sendToUser(
                memberId,
                "message.created",
                Map.of("conversationId", conversationId, "message", message)
            );
            webSocketMessageSender.sendToUser(
                memberId,
                "conversation.updated",
                Map.of("conversationId", conversationId, "conversation", conversation)
            );
            webSocketMessageSender.sendToUser(
                memberId,
                "unread.changed",
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
