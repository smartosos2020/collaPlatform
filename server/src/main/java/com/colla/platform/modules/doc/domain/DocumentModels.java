package com.colla.platform.modules.doc.domain;

import java.time.Instant;
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
        boolean archived
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
        List<DocumentComment> comments
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
        Instant createdAt
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
        Instant updatedAt
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
}
