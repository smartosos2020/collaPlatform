package com.colla.platform.modules.doc.infrastructure;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentComment;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCommentAnchor;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCollaborationState;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPermission;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentShareLink;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentRelation;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTemplate;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    UUID createDocument(
        UUID workspaceId,
        UUID parentId,
        String title,
        String docType,
        String content,
        int sortOrder,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        boolean knowledgeBase,
        UUID createdBy
    );

    void updateDocument(UUID workspaceId, UUID documentId, UUID parentId, String title, String content, int nextVersionNo, UUID updatedBy);

    void updateDocumentSnapshot(UUID workspaceId, UUID documentId, String title, String content, UUID updatedBy);

    void moveDocument(UUID workspaceId, UUID documentId, UUID parentId, int sortOrder, UUID updatedBy);

    void archiveDocumentTree(UUID workspaceId, UUID documentId, UUID updatedBy);

    void restoreDocumentTree(UUID workspaceId, UUID documentId, UUID updatedBy);

    boolean isDescendant(UUID workspaceId, UUID documentId, UUID candidateParentId);

    void copyParentPermissions(UUID workspaceId, UUID documentId, UUID parentId, UUID actorId);

    default void addVersion(UUID workspaceId, UUID documentId, int versionNo, String title, String content, UUID createdBy) {
        addVersion(workspaceId, documentId, versionNo, title, content, createdBy, null, "manual_checkpoint", null, null, null);
    }

    void addVersion(
        UUID workspaceId,
        UUID documentId,
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

    void replaceBlocks(UUID workspaceId, UUID documentId, List<DocumentBlockDraft> blocks, UUID actorId);

    List<DocumentBlock> listBlocks(UUID workspaceId, UUID documentId);

    List<DocumentSummary> listDocuments(UUID workspaceId, UUID userId, boolean includeArchived);

    Optional<DocumentSummary> findDocument(UUID workspaceId, UUID documentId);

    Optional<String> findContent(UUID workspaceId, UUID documentId);

    Optional<DocumentCollaborationState> findCollaborationState(UUID workspaceId, UUID documentId);

    void upsertCollaborationState(
        UUID workspaceId,
        UUID documentId,
        String stateVector,
        String snapshotContent,
        String snapshotPayload,
        long serverClock,
        String lastClientId,
        UUID updatedBy
    );

    void markCollaborationStateSaved(UUID workspaceId, UUID documentId, long serverClock);

    List<DocumentVersion> listVersions(UUID workspaceId, UUID documentId);

    Optional<DocumentVersion> findVersion(UUID workspaceId, UUID documentId, int versionNo);

    List<DocumentTemplate> listTemplates(UUID workspaceId, UUID knowledgeBaseId);

    Optional<DocumentTemplate> findTemplate(UUID workspaceId, UUID templateId);

    UUID createTemplate(
        UUID workspaceId,
        UUID knowledgeBaseId,
        String title,
        String description,
        String category,
        String content,
        UUID actorId
    );

    void updateKnowledgeMetadata(
        UUID workspaceId,
        UUID documentId,
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
        UUID documentId,
        String nodeKind,
        String targetObjectType,
        UUID targetObjectId,
        String targetRoute,
        String displayMode,
        String targetTitleStrategy,
        String entryAlias,
        UUID actorId
    );

    List<DocumentSummary> listKnowledgeBaseDocuments(UUID workspaceId, UUID rootDocumentId);

    List<DocumentSummary> listDueForReview(UUID workspaceId, LocalDate beforeDate, int limit);

    int markReviewReminderSent(UUID workspaceId, UUID documentId, UUID actorId);

    void upsertPermission(UUID workspaceId, UUID documentId, UUID userId, String permissionLevel, UUID actorId);

    void upsertSubjectPermission(
        UUID workspaceId,
        UUID documentId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        UUID actorId
    );

    Optional<String> findPermissionLevel(UUID workspaceId, UUID documentId, UUID userId);

    boolean isShareLinkAccess(UUID workspaceId, UUID documentId, UUID userId);

    List<DocumentPermission> listPermissions(UUID workspaceId, UUID documentId);

    List<DocumentShareLink> listShareLinks(UUID workspaceId, UUID documentId);

    DocumentShareLink upsertShareLink(
        UUID workspaceId,
        UUID documentId,
        String token,
        String scope,
        String permissionLevel,
        boolean enabled,
        Instant expiresAt,
        UUID actorId
    );

    Optional<DocumentShareLink> setShareLinkEnabled(UUID workspaceId, UUID documentId, boolean enabled, UUID actorId);

    void updateKnowledgeBaseSettings(
        UUID workspaceId,
        UUID documentId,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        boolean knowledgeBase,
        UUID actorId
    );

    List<UUID> findDocumentManagerUserIds(UUID workspaceId, UUID documentId);

    void addRelation(UUID workspaceId, UUID documentId, String targetType, UUID targetId, UUID createdBy);

    List<DocumentRelation> listRelations(UUID workspaceId, UUID documentId);

    Optional<DocumentComment> findComment(UUID workspaceId, UUID documentId, UUID commentId);

    UUID addComment(UUID workspaceId, UUID documentId, UUID authorId, String content, DocumentCommentAnchor anchor);

    UUID addCommentReply(UUID workspaceId, UUID documentId, UUID threadId, UUID parentCommentId, UUID authorId, String content);

    void updateCommentThreadSelectionAnchor(UUID workspaceId, UUID documentId, UUID threadId, int anchorStart, int anchorEnd);

    void resolveCommentThread(UUID workspaceId, UUID documentId, UUID commentId, UUID resolvedBy);

    void reopenCommentThread(UUID workspaceId, UUID documentId, UUID commentId, UUID reopenedBy);

    List<DocumentComment> listComments(UUID workspaceId, UUID documentId);

    List<UUID> findActiveUserIdsByUsernames(UUID workspaceId, List<String> usernames);
}
