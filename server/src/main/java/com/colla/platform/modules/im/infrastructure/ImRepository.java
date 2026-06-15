package com.colla.platform.modules.im.infrastructure;

import com.colla.platform.modules.im.domain.ImModels.ConversationDetail;
import com.colla.platform.modules.im.domain.ImModels.ConversationMember;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.im.domain.ImModels.MessageLink;
import com.colla.platform.modules.im.domain.ImModels.MessageMention;
import com.colla.platform.modules.im.domain.ImModels.MessageReactionSummary;
import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImRepository {
    UUID createConversation(UUID workspaceId, String conversationType, String title, UUID ownerId, UUID createdBy);

    void addMember(UUID workspaceId, UUID conversationId, UUID userId, String memberRole);

    void removeMember(UUID workspaceId, UUID conversationId, UUID userId);

    void setConversationMuted(UUID workspaceId, UUID conversationId, UUID userId, boolean muted);

    void setConversationPinned(UUID workspaceId, UUID conversationId, UUID userId, boolean pinned);

    void updateConversationType(UUID workspaceId, UUID conversationId, String conversationType);

    boolean isMember(UUID workspaceId, UUID conversationId, UUID userId);

    boolean isOwner(UUID workspaceId, UUID conversationId, UUID userId);

    List<ConversationSummary> listConversations(UUID workspaceId, UUID userId);

    Optional<ConversationDetail> findConversation(UUID workspaceId, UUID conversationId, UUID userId);

    List<ConversationMember> listMembers(UUID workspaceId, UUID conversationId);

    List<UUID> listMemberIds(UUID workspaceId, UUID conversationId);

    List<UUID> findActiveUserIdsByUsernames(UUID workspaceId, List<String> usernames);

    MessageSummary insertMessage(UUID workspaceId, UUID conversationId, UUID senderId, String clientMessageId, String messageType, String content);

    Optional<MessageSummary> findMessage(UUID workspaceId, UUID conversationId, UUID messageId);

    Optional<MessageSummary> findMessageForUser(UUID workspaceId, UUID conversationId, UUID messageId, UUID userId);

    Optional<MessageSummary> findMessageForUser(UUID workspaceId, UUID messageId, UUID userId);

    List<MessageSummary> listMessages(UUID workspaceId, UUID conversationId, UUID userId, UUID beforeId, int limit);

    void editMessage(UUID workspaceId, UUID conversationId, UUID messageId, UUID senderId, String content);

    void revokeMessage(UUID workspaceId, UUID conversationId, UUID messageId, UUID senderId);

    void setPinned(UUID workspaceId, UUID conversationId, UUID messageId, UUID actorId, boolean pinned);

    void toggleReaction(UUID workspaceId, UUID conversationId, UUID messageId, UUID userId, String emoji);

    void updateConversationLastMessage(UUID workspaceId, UUID conversationId, UUID messageId);

    void addMention(UUID workspaceId, UUID conversationId, UUID messageId, UUID mentionedUserId);

    void addLink(
        UUID workspaceId,
        UUID conversationId,
        UUID messageId,
        String sourceUrl,
        String targetType,
        UUID targetId,
        String webPath,
        String deepLink,
        PlatformObjectSummary summary
    );

    List<MessageMention> listMentions(UUID workspaceId, UUID messageId);

    List<MessageLink> listLinks(UUID workspaceId, UUID messageId);

    List<MessageReactionSummary> listReactions(UUID workspaceId, UUID messageId, UUID currentUserId);

    void markRead(UUID workspaceId, UUID conversationId, UUID userId, UUID messageId);

    long unreadCount(UUID workspaceId, UUID conversationId, UUID userId);

    long totalUnreadCount(UUID workspaceId, UUID userId);
}
