package com.colla.platform.modules.im.contract;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimum project-to-IM collaboration contract.
 */
public interface ProjectMessaging {

    UUID createProjectConversation(UUID workspaceId, UUID actorId, String name, Collection<Member> members);

    void addMember(UUID workspaceId, UUID actorId, UUID conversationId, Member member);

    Optional<MessageSnapshot> findVisibleMessage(UUID workspaceId, UUID actorId, UUID conversationId, UUID messageId);

    void sendSystemMessage(UUID workspaceId, UUID actorId, UUID conversationId, String idempotencyKey, String content);

    record Member(UUID userId, String role) {
    }

    record MessageSnapshot(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        String senderName,
        String content,
        Instant revokedAt
    ) {
    }
}
