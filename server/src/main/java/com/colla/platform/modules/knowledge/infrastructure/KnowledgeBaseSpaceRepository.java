package com.colla.platform.modules.knowledge.infrastructure;

import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBaseSpaceRepository {
    void backfillLegacySpaces(UUID workspaceId);

    boolean codeExists(UUID workspaceId, String code, UUID excludeId);

    UUID createSpace(
        UUID workspaceId,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String visibility,
        UUID rootItemId,
        UUID homeItemId,
        UUID ownerId,
        String defaultPermissionLevel,
        UUID actorId
    );

    void updateSpace(
        UUID workspaceId,
        UUID spaceId,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String visibility,
        UUID homeItemId,
        String defaultPermissionLevel,
        UUID actorId
    );

    void updateStatus(UUID workspaceId, UUID spaceId, String status, UUID actorId);

    List<KnowledgeBaseSpaceSummary> listSpaces(UUID workspaceId, boolean includeArchived);

    Optional<KnowledgeBaseSpaceSummary> findSpace(UUID workspaceId, UUID spaceId);

    Optional<KnowledgeBaseSpaceSummary> findSpaceByRootItemId(UUID workspaceId, UUID rootItemId);

    Optional<KnowledgeBaseSpaceSummary> findSpaceByItemId(UUID workspaceId, UUID itemId);

    Optional<UUID> findDisabledRootForDocument(UUID workspaceId, UUID itemId);

    boolean isSubscribed(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId);

    void subscribe(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId);

    void unsubscribe(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId);

    List<UUID> listSubscribedItemIds(UUID workspaceId, UUID subscriberId, UUID rootItemId, int limit);

    List<UUID> listSubscriberIdsForDocument(UUID workspaceId, UUID itemId);
}
