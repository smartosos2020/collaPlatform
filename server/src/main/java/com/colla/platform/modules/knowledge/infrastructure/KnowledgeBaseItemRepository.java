package com.colla.platform.modules.knowledge.infrastructure;

import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for knowledge-base directory items and their governance metadata. */
public interface KnowledgeBaseItemRepository {
    Optional<KnowledgeBaseItem> findItem(UUID workspaceId, UUID itemId);

    List<KnowledgeBaseItem> listKnowledgeBaseItems(UUID workspaceId, UUID rootItemId);

    List<KnowledgeBaseItem> listObjectReferences(UUID workspaceId, String targetObjectType, UUID targetObjectId);

    void updateKnowledgeNodeMetadata(UUID workspaceId, UUID itemId, String itemKind,
        String targetObjectType, UUID targetObjectId, String targetRoute, String displayMode,
        String targetTitleStrategy, String entryAlias, UUID actorId);

    void updateKnowledgeMetadata(UUID workspaceId, UUID itemId, UUID maintainerId, List<String> tags,
        String category, String knowledgeStatus, LocalDate reviewDueAt, Instant verifiedAt, UUID actorId);
}

