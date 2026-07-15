package com.colla.platform.modules.knowledge.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeContentCanonicalModels {
    private KnowledgeContentCanonicalModels() {
    }

    public static final int CURRENT_SCHEMA_VERSION = 3;

    public record KnowledgeContentCanonicalDocument(
        JsonNode document,
        int schemaVersion,
        String checksum,
        List<KnowledgeContentCanonicalBlock> blocks,
        String plainText,
        String markdown,
        List<KnowledgeContentCanonicalEmbed> embeds,
        List<KnowledgeContentSchemaIssue> issues
    ) {
    }

    public record KnowledgeContentCanonicalBlock(
        UUID blockId,
        UUID parentId,
        int sortOrder,
        String blockType,
        JsonNode node,
        String plainText,
        String markdown,
        Map<String, Object> embedMetadata
    ) {
    }

    public record KnowledgeContentCanonicalEmbed(
        UUID blockId,
        String objectType,
        UUID objectId,
        String targetRoute,
        String title
    ) {
    }

    public record KnowledgeContentSchemaIssue(String code, String path, String message, String severity) {
        public boolean isError() {
            return "error".equals(severity);
        }
    }

    public record KnowledgeContentMigrationPlan(
        UUID itemId,
        int sourceSchemaVersion,
        int targetSchemaVersion,
        String sourceChecksum,
        String targetChecksum,
        int sourceBlockCount,
        int targetBlockCount,
        boolean changed,
        boolean safeToApply,
        String migrationMode,
        List<KnowledgeContentSchemaIssue> issues,
        KnowledgeContentCanonicalDocument canonicalDocument
    ) {
    }
}
