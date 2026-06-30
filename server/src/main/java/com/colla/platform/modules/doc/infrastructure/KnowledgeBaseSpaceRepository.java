package com.colla.platform.modules.doc.infrastructure;

import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceSummary;
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
        UUID rootDocumentId,
        UUID homeDocumentId,
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
        UUID homeDocumentId,
        String defaultPermissionLevel,
        UUID actorId
    );

    void updateStatus(UUID workspaceId, UUID spaceId, String status, UUID actorId);

    List<KnowledgeBaseSpaceSummary> listSpaces(UUID workspaceId, boolean includeArchived);

    Optional<KnowledgeBaseSpaceSummary> findSpace(UUID workspaceId, UUID spaceId);

    Optional<KnowledgeBaseSpaceSummary> findSpaceByRootDocumentId(UUID workspaceId, UUID rootDocumentId);

    Optional<KnowledgeBaseSpaceSummary> findSpaceByDocumentId(UUID workspaceId, UUID documentId);

    Optional<UUID> findDisabledRootForDocument(UUID workspaceId, UUID documentId);

    boolean isSubscribed(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId);

    void subscribe(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId);

    void unsubscribe(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId);

    List<UUID> listSubscribedDocumentIds(UUID workspaceId, UUID subscriberId, UUID rootDocumentId, int limit);

    List<UUID> listSubscriberIdsForDocument(UUID workspaceId, UUID documentId);
}
