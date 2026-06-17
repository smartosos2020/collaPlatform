package com.colla.platform.modules.doc.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        Instant updatedAt
    ) {
    }

    public record DocumentDetail(
        DocumentSummary document,
        String content,
        List<DocumentBlock> blocks,
        List<DocumentRelation> relations,
        List<DocumentPermission> permissions,
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
        Instant updatedAt
    ) {
    }

    public record DocumentBlockDraft(String blockType, String content, int sortOrder) {
    }

    public record DocumentVersion(
        UUID id,
        UUID documentId,
        int versionNo,
        String title,
        String content,
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

    public record DocumentDiffLine(String type, int oldLineNo, int newLineNo, String content) {
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
        UUID userId,
        String username,
        String displayName,
        String permissionLevel,
        Instant createdAt
    ) {
    }

    public record DocumentComment(
        UUID id,
        UUID documentId,
        UUID blockId,
        UUID authorId,
        String authorName,
        String content,
        boolean resolved,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolvedByName,
        Instant createdAt
    ) {
    }
}
