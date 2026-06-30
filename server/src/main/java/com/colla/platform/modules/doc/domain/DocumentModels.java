package com.colla.platform.modules.doc.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;

public final class DocumentModels {
    private DocumentModels() {
    }

    public record DocumentSummary(
        UUID id,
        UUID parentId,
        String title,
        String docType,
        int currentVersionNo,
        String permissionLevel,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        UUID updatedBy,
        String updatedByName,
        Instant updatedAt,
        int sortOrder,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        boolean knowledgeBase,
        boolean archived,
        UUID maintainerId,
        String maintainerName,
        List<String> tags,
        String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt
    ) {
    }

    public record DocumentTreeNode(
        DocumentSummary document,
        String path,
        int depth,
        int childCount,
        boolean hasChildren,
        List<DocumentTreeNode> children
    ) {
    }

    public record DocumentPathItem(
        UUID id,
        String title,
        String docType,
        String permissionLevel
    ) {
    }

    public record DocumentDetail(
        DocumentSummary document,
        String content,
        List<DocumentBlock> blocks,
        List<DocumentRelation> relations,
        List<DocumentPermission> permissions,
        List<DocumentShareLink> shareLinks,
        List<DocumentComment> comments,
        KnowledgeContext knowledgeContext
    ) {
    }

    public record KnowledgeContext(
        UUID spaceId,
        String spaceName,
        String spaceCode,
        UUID rootDocumentId,
        UUID homeDocumentId,
        List<DocumentPathItem> path,
        String pathText,
        String webPath
    ) {
    }

    public record DocumentBlock(
        UUID id,
        UUID documentId,
        String blockType,
        String content,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt,
        PlatformObjectSummary embedSummary,
        Map<String, Object> metadata
    ) {
    }

    public record DocumentBlockDraft(String blockType, String content, int sortOrder) {
    }

    public record DocumentVersion(
        UUID id,
        UUID documentId,
        int versionNo,
        String versionName,
        String versionType,
        String title,
        String content,
        String summary,
        Integer sourceVersionNo,
        String blockSnapshot,
        UUID createdBy,
        String createdByName,
        Instant createdAt
    ) {
    }

    public record DocumentVersionDiff(
        UUID documentId,
        int fromVersionNo,
        int toVersionNo,
        List<DocumentDiffLine> lines
    ) {
    }

    public record DocumentDiffLine(String type, int oldLineNo, int newLineNo, String content, String scope, Integer blockIndex, String blockType) {
    }

    public record DocumentTemplate(
        UUID id,
        String title,
        String description,
        String category,
        String content,
        boolean builtIn,
        String scopeType,
        UUID knowledgeBaseId,
        String knowledgeBaseName,
        Instant createdAt
    ) {
    }

    public record KnowledgeMetadataUpdate(
        UUID maintainerId,
        List<String> tags,
        String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt
    ) {
    }

    public record KnowledgeReviewReminderResult(
        int scannedCount,
        int notifiedCount
    ) {
    }

    public record KnowledgeBaseMarkdownImportItem(
        String title,
        String content,
        String category,
        List<String> tags
    ) {
    }

    public record KnowledgeBaseMarkdownImportResult(
        UUID spaceId,
        int importedCount,
        List<DocumentSummary> documents
    ) {
    }

    public record DocumentRelation(
        UUID id,
        UUID documentId,
        String targetType,
        UUID targetId,
        String title,
        String webPath,
        Instant createdAt
    ) {
    }

    public record DocumentPermission(
        UUID id,
        UUID documentId,
        String subjectType,
        UUID subjectId,
        UUID userId,
        String username,
        String displayName,
        String subjectName,
        String subjectDetail,
        String permissionLevel,
        String sourceType,
        UUID sourceDocumentId,
        String sourceTitle,
        Instant createdAt
    ) {
    }

    public record DocumentShareLink(
        UUID id,
        UUID documentId,
        String token,
        String scope,
        String permissionLevel,
        boolean enabled,
        Instant expiresAt,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        UUID updatedBy,
        String updatedByName,
        Instant updatedAt,
        UUID knowledgeBaseId,
        String knowledgeBaseName,
        String knowledgeBaseCode
    ) {
    }

    public record DocumentPermissionRequest(
        UUID requestId,
        UUID documentId,
        String requestedPermissionLevel,
        int notifiedCount,
        String status
    ) {
    }

    public record DocumentComment(
        UUID id,
        UUID threadId,
        UUID parentCommentId,
        UUID documentId,
        UUID blockId,
        UUID authorId,
        String authorName,
        String content,
        String anchorType,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText,
        String anchorPrefix,
        String anchorSuffix,
        Integer anchorVersionNo,
        boolean root,
        boolean resolved,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolvedByName,
        Instant reopenedAt,
        UUID reopenedBy,
        String reopenedByName,
        Instant createdAt,
        List<DocumentComment> replies
    ) {
    }

    public record DocumentCommentAnchor(
        String anchorType,
        UUID blockId,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText,
        String anchorPrefix,
        String anchorSuffix,
        Integer anchorVersionNo
    ) {
    }

    public record DocumentCollaborationState(
        UUID documentId,
        String stateVector,
        String snapshotContent,
        String snapshotPayload,
        long serverClock,
        String lastClientId,
        UUID updatedBy,
        Instant lastSavedAt,
        Instant updatedAt
    ) {
    }

    public record DocumentPerformanceProfile(
        UUID documentId,
        int blockCount,
        int embedCount,
        int commentCount,
        int contentLength,
        int lineCount,
        boolean largeDocument,
        String recommendedMode
    ) {
    }

    public record DocumentMigrationPreview(
        UUID documentId,
        int currentVersionNo,
        int contentBlockCount,
        int storedBlockCount,
        int contentLength,
        boolean blockProjectionCurrent,
        boolean rollbackAvailable,
        String migrationMode
    ) {
    }

    public record DocumentCollaborationHealth(
        UUID documentId,
        long serverClock,
        int activeUsers,
        boolean dirty,
        String stateVector,
        Instant lastSavedAt,
        Instant updatedAt
    ) {
    }

    public record DocumentAcceptanceScenario(
        String key,
        String title,
        String workflow,
        String status,
        String evidence
    ) {
    }

    public record DocumentAcceptanceGate(
        String key,
        String label,
        String status,
        String evidence
    ) {
    }

    public record DocumentAcceptanceReport(
        String version,
        String status,
        List<DocumentAcceptanceScenario> scenarios,
        List<DocumentAcceptanceGate> gates,
        int openP0,
        int openP1,
        boolean frozen,
        String frozenCriteria
    ) {
    }

    public record KnowledgeBaseSpaceSummary(
        UUID id,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String status,
        String visibility,
        UUID rootDocumentId,
        UUID homeDocumentId,
        UUID ownerId,
        String ownerName,
        String defaultPermissionLevel,
        Instant createdAt,
        Instant updatedAt,
        long documentCount
    ) {
    }

    public record KnowledgeBaseSpaceDetail(
        KnowledgeBaseSpaceSummary space,
        DocumentSummary rootDocument,
        DocumentSummary homeDocument
    ) {
    }

    public record KnowledgeBaseSubscription(
        String targetType,
        UUID targetId,
        boolean subscribed
    ) {
    }

    public record KnowledgeBaseDiscovery(
        UUID spaceId,
        List<DocumentSummary> recentAccessed,
        List<DocumentSummary> favorites,
        List<DocumentSummary> maintainedByMe,
        List<DocumentSummary> dueForReview,
        List<DocumentSummary> popular,
        List<DocumentSummary> recommended,
        List<DocumentSummary> subscribedDocuments,
        boolean spaceSubscribed
    ) {
    }

    public record KnowledgeBaseHealthMetrics(
        long documentCount,
        long activeDocumentCount,
        long outdatedDocumentCount,
        long unmaintainedDocumentCount,
        long ownerlessDocumentCount,
        long highRiskPermissionCount
    ) {
    }

    public record KnowledgeBaseGovernanceRisk(
        String id,
        String ruleCode,
        String severity,
        String resourceType,
        UUID resourceId,
        String title,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String permissionLevel,
        String reason,
        String actionPath
    ) {
    }

    public record KnowledgeBaseAccessDocumentStat(
        DocumentSummary document,
        long visitorCount,
        long accessCount,
        Instant lastAccessedAt
    ) {
    }

    public record KnowledgeBaseSearchTermStat(
        String query,
        long count,
        Instant lastSearchedAt
    ) {
    }

    public record KnowledgeBaseAccessStats(
        long visitorCount,
        long accessCount,
        List<KnowledgeBaseAccessDocumentStat> popularDocuments,
        List<KnowledgeBaseAccessDocumentStat> lowAccessDocuments,
        List<KnowledgeBaseSearchTermStat> noResultTerms
    ) {
    }

    public record KnowledgeBaseGovernanceDashboard(
        UUID spaceId,
        KnowledgeBaseHealthMetrics health,
        List<KnowledgeBaseGovernanceRisk> risks,
        KnowledgeBaseAccessStats accessStats
    ) {
    }

    public record KnowledgeBaseBulkGovernanceResult(
        int updatedCount,
        int archivedCount,
        int reviewRequestedCount
    ) {
    }
}
