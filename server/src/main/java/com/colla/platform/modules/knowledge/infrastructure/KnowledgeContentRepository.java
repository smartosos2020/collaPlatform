package com.colla.platform.modules.knowledge.infrastructure;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentComment;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCommentAnchor;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlock;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCollaborationState;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationBinaryState;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationStoredUpdate;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeCollaborationTicketRecord;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPermission;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentShareLink;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentRelation;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentTemplate;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeContentRepository {
    UUID createItem(
        UUID workspaceId,
        UUID parentId,
        String title,
        String contentType,
        String content,
        int sortOrder,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        boolean knowledgeBase,
        UUID createdBy
    );

    void updateContent(UUID workspaceId, UUID itemId, UUID parentId, String title, String content, int nextVersionNo, UUID updatedBy);

    void updateContentSnapshot(UUID workspaceId, UUID itemId, String title, String content, UUID updatedBy);

    void moveItem(UUID workspaceId, UUID itemId, UUID parentId, int sortOrder, UUID updatedBy);

    void archiveItemTree(UUID workspaceId, UUID itemId, UUID updatedBy);

    void restoreItemTree(UUID workspaceId, UUID itemId, UUID updatedBy);

    boolean isDescendant(UUID workspaceId, UUID itemId, UUID candidateParentId);

    void copyParentPermissions(UUID workspaceId, UUID itemId, UUID parentId, UUID actorId);

    default void addVersion(UUID workspaceId, UUID itemId, int versionNo, String title, String content, UUID createdBy) {
        addVersion(workspaceId, itemId, versionNo, title, content, createdBy, null, "manual_checkpoint", null, null, null);
    }

    void addVersion(
        UUID workspaceId,
        UUID itemId,
        int versionNo,
        String title,
        String content,
        UUID createdBy,
        String versionName,
        String versionType,
        String summary,
        Integer sourceVersionNo,
        String blockSnapshot
    );

    void replaceBlocks(UUID workspaceId, UUID itemId, List<KnowledgeContentBlockDraft> blocks, UUID actorId);

    List<KnowledgeContentBlock> listBlocks(UUID workspaceId, UUID itemId);

    List<KnowledgeBaseItem> listItems(UUID workspaceId, UUID userId, boolean includeArchived);

    Optional<KnowledgeBaseItem> findItem(UUID workspaceId, UUID itemId);

    Optional<String> findContent(UUID workspaceId, UUID itemId);

    Optional<KnowledgeContentCollaborationState> findCollaborationState(UUID workspaceId, UUID itemId);

    void upsertCollaborationState(
        UUID workspaceId,
        UUID itemId,
        String stateVector,
        String snapshotContent,
        String snapshotPayload,
        long serverClock,
        String lastClientId,
        UUID updatedBy
    );

    void markCollaborationStateSaved(UUID workspaceId, UUID itemId, long serverClock);

    void createCollaborationTicket(
        String tokenHash, UUID workspaceId, UUID itemId, UUID userId, UUID deviceId,
        String clientId, Instant expiresAt
    );

    Optional<KnowledgeCollaborationTicketRecord> findActiveCollaborationTicket(String tokenHash);

    Optional<KnowledgeCollaborationBinaryState> findCollaborationBinaryState(UUID workspaceId, UUID itemId);

    List<KnowledgeCollaborationStoredUpdate> listCollaborationUpdatesAfter(UUID workspaceId, UUID itemId, long sequence);

    long appendCollaborationUpdate(
        UUID workspaceId, UUID itemId, String updateId, byte[] payload, UUID actorId,
        String clientId, int schemaVersion
    );

    Optional<UUID> findLatestCollaborationActor(UUID workspaceId, UUID itemId);

    int compactCollaborationUpdates(UUID workspaceId, UUID itemId, int retainedUpdates);

    int purgeExpiredCollaborationTickets(Instant cutoff);

    void deleteCollaborationState(UUID workspaceId, UUID itemId);

    void storeCollaborationSnapshot(
        UUID workspaceId, UUID itemId, byte[] snapshot, byte[] stateVector, String snapshotHash,
        int schemaVersion, String canonicalSnapshot, String clientId, UUID actorId
    );

    boolean markCollaborationAuditCheckpoint(UUID workspaceId, UUID itemId, Instant cutoff);

    List<KnowledgeContentVersion> listVersions(UUID workspaceId, UUID itemId);

    Optional<KnowledgeContentVersion> findVersion(UUID workspaceId, UUID itemId, int versionNo);

    List<KnowledgeContentTemplate> listTemplates(UUID workspaceId, UUID knowledgeBaseId);

    Optional<KnowledgeContentTemplate> findTemplate(UUID workspaceId, UUID templateId);

    UUID createTemplate(
        UUID workspaceId,
        UUID knowledgeBaseId,
        String title,
        String description,
        String category,
        String content,
        UUID actorId
    );

    UUID upgradeTemplate(UUID workspaceId, UUID templateId, String content, UUID actorId);

    void updateKnowledgeMetadata(
        UUID workspaceId,
        UUID itemId,
        UUID maintainerId,
        List<String> tags,
        String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt,
        UUID actorId
    );

    void updateKnowledgeNodeMetadata(
        UUID workspaceId,
        UUID itemId,
        String nodeKind,
        String targetObjectType,
        UUID targetObjectId,
        String targetRoute,
        String displayMode,
        String targetTitleStrategy,
        String entryAlias,
        UUID actorId
    );

    List<KnowledgeBaseItem> listKnowledgeBaseItems(UUID workspaceId, UUID rootItemId);

    List<KnowledgeBaseItem> listDueForReview(UUID workspaceId, LocalDate beforeDate, int limit);

    int markReviewReminderSent(UUID workspaceId, UUID itemId, UUID actorId);

    void upsertPermission(UUID workspaceId, UUID itemId, UUID userId, String permissionLevel, UUID actorId);

    void upsertSubjectPermission(
        UUID workspaceId,
        UUID itemId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        UUID actorId
    );

    Optional<String> findPermissionLevel(UUID workspaceId, UUID itemId, UUID userId);

    boolean isShareLinkAccess(UUID workspaceId, UUID itemId, UUID userId);

    List<KnowledgeContentPermission> listPermissions(UUID workspaceId, UUID itemId);

    List<KnowledgeContentShareLink> listShareLinks(UUID workspaceId, UUID itemId);

    KnowledgeContentShareLink upsertShareLink(
        UUID workspaceId,
        UUID itemId,
        String token,
        String scope,
        String permissionLevel,
        boolean enabled,
        Instant expiresAt,
        UUID actorId
    );

    Optional<KnowledgeContentShareLink> setShareLinkEnabled(UUID workspaceId, UUID itemId, boolean enabled, UUID actorId);

    void updateKnowledgeBaseSettings(
        UUID workspaceId,
        UUID itemId,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        boolean knowledgeBase,
        UUID actorId
    );

    List<UUID> findItemManagerUserIds(UUID workspaceId, UUID itemId);

    void addRelation(UUID workspaceId, UUID itemId, String targetType, UUID targetId, UUID createdBy);

    void removeRelation(UUID workspaceId, UUID itemId, String targetType, UUID targetId);

    List<KnowledgeContentRelation> listRelations(UUID workspaceId, UUID itemId);

    Optional<KnowledgeContentComment> findComment(UUID workspaceId, UUID itemId, UUID commentId);

    UUID addComment(UUID workspaceId, UUID itemId, UUID authorId, String content, KnowledgeContentCommentAnchor anchor);

    UUID addCommentReply(UUID workspaceId, UUID itemId, UUID threadId, UUID parentCommentId, UUID authorId, String content);

    void updateCommentThreadSelectionAnchor(UUID workspaceId, UUID itemId, UUID threadId, int anchorStart, int anchorEnd);

    void updateCommentThreadAnchorState(UUID workspaceId, UUID itemId, UUID threadId, String anchorState, String reason);

    void updateCommentThreadsAnchorStateByBlock(UUID workspaceId, UUID itemId, UUID blockId, String anchorState, String reason);

    void resolveCommentThread(UUID workspaceId, UUID itemId, UUID commentId, UUID resolvedBy);

    void reopenCommentThread(UUID workspaceId, UUID itemId, UUID commentId, UUID reopenedBy);

    List<KnowledgeContentComment> listComments(UUID workspaceId, UUID itemId);

    List<UUID> findActiveUserIdsByUsernames(UUID workspaceId, List<String> usernames);
}

