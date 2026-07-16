package com.colla.platform.modules.knowledge.domain;

import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeContentModels {
    private KnowledgeContentModels() {
    }

    public record KnowledgeContentPathItem(UUID id, String title, String contentType, String permissionLevel) {
    }

    public record KnowledgeContentContext(
        UUID spaceId, String spaceName, String spaceCode, UUID rootItemId, UUID homeItemId,
        List<KnowledgeContentPathItem> path, String pathText, String webPath
    ) {
    }

    public record KnowledgeContent(
        KnowledgeBaseItem item, String content, List<KnowledgeContentBlock> blocks,
        List<KnowledgeContentRelation> relations, List<KnowledgeContentPermission> permissions,
        List<KnowledgeContentShareLink> shareLinks, List<KnowledgeContentComment> comments,
        KnowledgeContentContext context
    ) {
    }

    public record KnowledgeContentBlock(
        UUID id, UUID itemId, UUID parentId, String blockType, String content, int sortOrder,
        int schemaVersion, Map<String, Object> attrs, Map<String, Object> richContent, String plainText,
        String anchorId, int blockVersion, UUID createdBy, Instant createdAt, UUID updatedBy,
        Instant updatedAt, PlatformObjectSummary embedSummary, Map<String, Object> metadata
    ) {
    }

    public record KnowledgeContentBlockDraft(
        UUID id, UUID parentId, String blockType, String content, Integer sortOrder, Integer schemaVersion,
        Map<String, Object> attrs, Map<String, Object> richContent, String plainText, String anchorId, Boolean deleted
    ) {
        public KnowledgeContentBlockDraft(String blockType, String content, int sortOrder) {
            this(null, null, blockType, content, sortOrder, 2, Map.of(), Map.of(), null, null, false);
        }
    }

    public record KnowledgeContentVersion(
        UUID id, UUID itemId, int versionNo, String versionName, String versionType, String title,
        String content, String summary, Integer sourceVersionNo, String blockSnapshot,
        UUID createdBy, String createdByName, Instant createdAt
    ) {
    }

    public record KnowledgeContentVersionDiff(
        UUID itemId, int fromVersionNo, int toVersionNo, List<KnowledgeContentDiffLine> lines
    ) {
    }

    public record KnowledgeContentDiffLine(
        String type, int oldLineNo, int newLineNo, String content, String scope, Integer blockIndex, String blockType, UUID blockId
    ) {
    }

    public record KnowledgeContentTemplate(
        UUID id, String title, String description, String category, String content, boolean builtIn,
        String scopeType, UUID knowledgeBaseId, String knowledgeBaseName, int versionNo,
        UUID supersedesTemplateId, Instant createdAt
    ) {
    }

    public record KnowledgeContentMetadataUpdate(
        UUID maintainerId, List<String> tags, String category, String knowledgeStatus,
        LocalDate reviewDueAt, Instant verifiedAt
    ) {
    }

    public record KnowledgeContentRelation(
        UUID id, UUID itemId, String targetType, UUID targetId, String title, String webPath, Instant createdAt
    ) {
    }

    public record KnowledgeContentTransferReport(
        String direction, String format, int blockCount, int attachmentCount, int objectReferenceCount,
        List<String> convertedFeatures, List<String> degradedFeatures, String fingerprint, boolean safeToApply
    ) {
    }

    public record KnowledgeContentPermission(
        UUID id, UUID itemId, String subjectType, UUID subjectId, UUID userId, String username,
        String displayName, String subjectName, String subjectDetail, String permissionLevel,
        String sourceType, UUID sourceItemId, String sourceTitle, Instant createdAt
    ) {
    }

    public record KnowledgeContentShareLink(
        UUID id, UUID itemId, String token, String scope, String permissionLevel, boolean enabled,
        Instant expiresAt, UUID createdBy, String createdByName, Instant createdAt, UUID updatedBy,
        String updatedByName, Instant updatedAt, UUID knowledgeBaseId, String knowledgeBaseName,
        String knowledgeBaseCode
    ) {
    }

    public record KnowledgeContentPermissionRequest(
        UUID requestId, UUID itemId, String requestedPermissionLevel, int notifiedCount, String status
    ) {
    }

    public record KnowledgeContentComment(
        UUID id, UUID threadId, UUID parentCommentId, UUID itemId, UUID blockId, UUID authorId,
        String authorName, String content, String anchorType, Integer anchorStart, Integer anchorEnd,
        String anchorText, String anchorPrefix, String anchorSuffix, Integer anchorVersionNo,
        String anchorState, String anchorInvalidReason, Instant anchorUpdatedAt, boolean root,
        boolean resolved, Instant resolvedAt, UUID resolvedBy, String resolvedByName, Instant reopenedAt,
        UUID reopenedBy, String reopenedByName, Instant createdAt, List<KnowledgeContentComment> replies
    ) {
    }

    public record KnowledgeContentCommentAnchor(
        String anchorType, UUID blockId, Integer anchorStart, Integer anchorEnd, String anchorText,
        String anchorPrefix, String anchorSuffix, Integer anchorVersionNo
    ) {
    }

    public record KnowledgeContentCollaborationState(
        UUID itemId, String stateVector, String snapshotContent, String snapshotPayload, long serverClock,
        String lastClientId, UUID updatedBy, Instant lastSavedAt, Instant updatedAt
    ) {
    }

    public record KnowledgeCollaborationTicket(
        String url, String documentName, String ticket, String clientId, String protocolVersion,
        int schemaVersion, String permissionLevel, boolean canView, boolean canEdit, Instant expiresAt
    ) {
    }

    public record KnowledgeCollaborationAuthorization(
        UUID workspaceId, UUID itemId, UUID userId, UUID deviceId, String username, String displayName,
        String clientId, String color, String permissionLevel, boolean canView, boolean canEdit, Instant expiresAt
    ) {
    }

    public record KnowledgeCollaborationTicketRecord(
        UUID id, String tokenHash, UUID workspaceId, UUID itemId, UUID userId, UUID deviceId,
        String clientId, Instant expiresAt, Instant revokedAt
    ) {
    }

    public record KnowledgeCollaborationBinaryState(
        byte[] snapshot, byte[] stateVector, int schemaVersion, long snapshotSequence, String snapshotHash,
        String canonicalSnapshot, Instant updatedAt
    ) {
    }

    public record KnowledgeCollaborationStoredUpdate(
        long sequence, byte[] payload, String updateId, UUID actorId, String clientId, Instant createdAt
    ) {
    }

    public record KnowledgeContentPerformance(
        UUID itemId, int blockCount, int embedCount, int commentCount, int contentLength,
        int lineCount, long snapshotBytes, int budgetTier, int initialRenderBlocks,
        int loadBudgetMs, int inputBudgetMs, int saveBudgetMs, int searchBudgetMs,
        int collaborationBudgetMs, boolean largeContent, String recommendedMode
    ) {
    }

    public record KnowledgeContentDiagnostics(
        UUID itemId, int versionNo, int blockCount, long snapshotBytes, int permissionCount,
        int objectReferenceCount, int unavailableObjectCount, boolean searchProjectionReady,
        boolean shareLinkEnabled, long collaborationServerClock, boolean collaborationDirty,
        Instant collaborationLastSavedAt, Instant generatedAt, boolean redacted
    ) {
    }

    public record KnowledgeContentMigrationPreview(
        UUID itemId, int currentVersionNo, int contentBlockCount, int storedBlockCount,
        int contentLength, boolean blockProjectionCurrent, boolean rollbackAvailable, String migrationMode
    ) {
    }

    public record KnowledgeContentCollaborationHealth(
        UUID itemId, long serverClock, int activeUsers, boolean dirty, String stateVector,
        Instant lastSavedAt, Instant updatedAt
    ) {
    }

    public record KnowledgeContentAcceptanceScenario(
        String key, String title, String workflow, String status, String evidence
    ) {
    }

    public record KnowledgeContentAcceptanceGate(String key, String label, String status, String evidence) {
    }

    public record KnowledgeContentAcceptanceReport(
        String version, String status, List<KnowledgeContentAcceptanceScenario> scenarios,
        List<KnowledgeContentAcceptanceGate> gates, int openP0, int openP1, boolean frozen,
        String frozenCriteria
    ) {
    }
}
