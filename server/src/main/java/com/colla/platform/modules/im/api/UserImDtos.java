package com.colla.platform.modules.im.api;

import com.colla.platform.modules.im.domain.ImModels.ConversationDetail;
import com.colla.platform.modules.im.domain.ImModels.ConversationMember;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.im.domain.ImModels.MessagePage;
import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class UserImDtos {
    private UserImDtos() {
    }

    static UserConversationView conversation(ConversationSummary conversation) {
        return new UserConversationView(
            conversation.id(),
            conversation.conversationType(),
            conversation.title(),
            conversation.memberCount(),
            conversation.muted(),
            conversation.pinnedAt(),
            conversation.lastMessage(),
            conversation.unreadCount(),
            conversation.lastMessageAt(),
            conversation.createdAt(),
            new UserConversationReminder(conversation.unreadCount(), conversation.muted(), conversation.pinnedAt() != null),
            List.of("open", conversation.muted() ? "unmute" : "mute", conversation.pinnedAt() == null ? "pin" : "unpin")
        );
    }

    static UserConversationDetailView conversationDetail(ConversationDetail detail) {
        return new UserConversationDetailView(
            detail.id(),
            detail.conversationType(),
            detail.title(),
            detail.memberCount(),
            detail.members(),
            detail.muted(),
            detail.pinnedAt(),
            detail.lastMessage(),
            detail.unreadCount(),
            detail.lastMessageAt(),
            detail.createdAt(),
            new UserConversationReminder(detail.unreadCount(), detail.muted(), detail.pinnedAt() != null),
            List.of("open", "send_message", "add_member", detail.muted() ? "unmute" : "mute")
        );
    }

    static UserMessageView message(MessageSummary message) {
        return new UserMessageView(
            message.id(),
            message.conversationId(),
            message.senderId(),
            message.senderName(),
            message.messageType(),
            message.content(),
            message.clientMessageId(),
            message.messageSeq(),
            message.createdAt(),
            message.editedAt(),
            message.revokedAt(),
            message.pinnedAt(),
            message.pinnedBy(),
            message.mentions(),
            message.links(),
            message.reactions(),
            List.of("reply", "react", "copy", message.revokedAt() == null ? "revoke" : "restore")
        );
    }

    static UserMessagePageView messagePage(MessagePage page) {
        return new UserMessagePageView(page.items().stream().map(UserImDtos::message).toList(), page.nextCursor());
    }

    record UserConversationView(
        UUID id,
        String conversationType,
        String title,
        int memberCount,
        boolean muted,
        Instant pinnedAt,
        MessageSummary lastMessage,
        long unreadCount,
        Instant lastMessageAt,
        Instant createdAt,
        UserConversationReminder reminder,
        List<String> availableActions
    ) {
    }

    record UserConversationDetailView(
        UUID id,
        String conversationType,
        String title,
        int memberCount,
        List<ConversationMember> members,
        boolean muted,
        Instant pinnedAt,
        MessageSummary lastMessage,
        long unreadCount,
        Instant lastMessageAt,
        Instant createdAt,
        UserConversationReminder reminder,
        List<String> availableActions
    ) {
    }

    record UserMessageView(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        String messageType,
        String content,
        String clientMessageId,
        long messageSeq,
        Instant createdAt,
        Instant editedAt,
        Instant revokedAt,
        Instant pinnedAt,
        UUID pinnedBy,
        List<?> mentions,
        List<?> links,
        List<?> reactions,
        List<String> availableActions
    ) {
    }

    record UserMessagePageView(List<UserMessageView> items, UUID nextCursor) {
    }

    record UserConversationReminder(long unreadCount, boolean muted, boolean pinned) {
    }
}
