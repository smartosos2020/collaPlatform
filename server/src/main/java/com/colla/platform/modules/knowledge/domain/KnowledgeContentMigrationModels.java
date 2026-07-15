package com.colla.platform.modules.knowledge.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public final class KnowledgeContentMigrationModels {
    private KnowledgeContentMigrationModels() {
    }

    public record KnowledgeContentMigrationBatchPlan(
        UUID batchId,
        UUID workspaceId,
        String idempotencyKey,
        String mode,
        String status,
        int itemCount,
        int plannedCount,
        int failureCount,
        String sourceChecksum,
        String targetChecksum,
        List<KnowledgeContentMigrationItemPlan> items
    ) {
    }

    public record KnowledgeContentMigrationItemPlan(
        UUID itemId,
        String sourceChecksum,
        String targetChecksum,
        String status,
        String failureCode,
        String failureMessage,
        JsonNode targetDocument
    ) {
    }
}
