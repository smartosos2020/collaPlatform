package com.colla.platform.modules.im.domain;

import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ImModels {
    private ImModels() {
    }

    public record ConversationSummary(
        UUID id,
        String conversationType,
        String title,
        int memberCount,
        MessageSummary lastMessage,
        long unreadCount,
        Instant lastMessageAt,
        Instant createdAt
    ) {
    }

    public record ConversationDetail(
        UUID id,
        String conversationType,
        String title,
        int memberCount,
        List<ConversationMember> members,
        MessageSummary lastMessage,
        long unreadCount,
        Instant lastMessageAt,
        Instant createdAt
    ) {
    }

    public record ConversationMember(
        UUID userId,
        String username,
        String displayName,
        String memberRole,
        Instant joinedAt
    ) {
    }

    public record MessageSummary(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        String messageType,
        String content,
        String clientMessageId,
        Instant createdAt,
        Instant editedAt,
        Instant revokedAt,
        Instant pinnedAt,
        UUID pinnedBy,
        List<MessageMention> mentions,
        List<MessageLink> links,
        List<MessageReactionSummary> reactions
    ) {
    }

    public record MessageMention(UUID userId, String username, String displayName) {
    }

    public record MessageReactionSummary(String emoji, int count, boolean reactedByMe) {
    }

    public record MessageLink(
        UUID id,
        String sourceUrl,
        String targetType,
        UUID targetId,
        String webPath,
        String deepLink,
        PlatformObjectSummary summary
    ) {
    }

    public record MessagePage(List<MessageSummary> items, UUID nextCursor) {
    }

    public record UnreadState(UUID conversationId, long unreadCount, long totalUnreadCount) {
    }
}
