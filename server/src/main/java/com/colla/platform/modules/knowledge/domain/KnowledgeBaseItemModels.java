package com.colla.platform.modules.knowledge.domain;

import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class KnowledgeBaseItemModels {
    private KnowledgeBaseItemModels() {
    }

    public record KnowledgeBaseItem(
        UUID id, UUID parentId, String title, String contentType, int currentVersionNo, String permissionLevel,
        UUID createdBy, String createdByName, Instant createdAt, UUID updatedBy, String updatedByName, Instant updatedAt,
        int sortOrder, String description, String coverUrl, String defaultPermissionLevel, boolean knowledgeBase,
        boolean archived, UUID maintainerId, String maintainerName, List<String> tags, String category,
        String knowledgeStatus, LocalDate reviewDueAt, Instant verifiedAt, String itemKind, String targetObjectType,
        UUID targetObjectId, String targetRoute, String displayMode, String targetTitleStrategy, String entryAlias,
        PlatformObjectSummary targetSummary
    ) {
    }

    public record KnowledgeBaseItemTreeNode(
        KnowledgeBaseItem item, String path, int depth, int childCount, boolean hasChildren,
        List<KnowledgeBaseItemTreeNode> children
    ) {
    }

    public record KnowledgeBaseMarkdownImportItem(String title, String content, String category, List<String> tags) {
    }

    public record KnowledgeBaseMarkdownImportResult(UUID spaceId, int importedCount, List<KnowledgeBaseItem> items) {
    }

    public record KnowledgeReviewReminderResult(int scannedCount, int notifiedCount) {
    }

    public record KnowledgeBaseSpaceSummary(
        UUID id, String name, String code, String description, String icon, String coverUrl, String status,
        String visibility, UUID rootItemId, UUID homeItemId, UUID ownerId, String ownerName,
        String defaultPermissionLevel, Instant createdAt, Instant updatedAt, long itemCount
    ) {
    }

    public record KnowledgeBaseSpaceDetail(
        KnowledgeBaseSpaceSummary space, KnowledgeBaseItem rootItem, KnowledgeBaseItem homeItem
    ) {
    }

    public record KnowledgeBaseSubscription(String targetType, UUID targetId, boolean subscribed) {
    }

    public record KnowledgeBaseDiscovery(
        UUID spaceId, List<KnowledgeBaseItem> recentAccessed, List<KnowledgeBaseItem> favorites,
        List<KnowledgeBaseItem> maintainedByMe, List<KnowledgeBaseItem> dueForReview,
        List<KnowledgeBaseItem> popular, List<KnowledgeBaseItem> recommended,
        List<KnowledgeBaseItem> subscribedItems, boolean spaceSubscribed
    ) {
    }

    public record KnowledgeBaseHealthMetrics(
        long itemCount, long activeItemCount, long outdatedItemCount, long unmaintainedItemCount,
        long ownerlessItemCount, long highRiskPermissionCount, long blockCoverageGapCount,
        long emptyBlockCount, long invalidEmbedBlockCount, double blockCoveragePercent
    ) {
    }

    public record KnowledgeBaseGovernanceRisk(
        String id, String ruleCode, String severity, String resourceType, UUID resourceId, String title,
        String subjectType, UUID subjectId, String subjectName, String permissionLevel, String reason, String actionPath
    ) {
    }

    public record KnowledgeBaseAccessItemStat(
        KnowledgeBaseItem item, long visitorCount, long accessCount, Instant lastAccessedAt
    ) {
    }

    public record KnowledgeBaseSearchTermStat(String query, long count, Instant lastSearchedAt) {
    }

    public record KnowledgeBaseAccessStats(
        long visitorCount, long accessCount, List<KnowledgeBaseAccessItemStat> popularItems,
        List<KnowledgeBaseAccessItemStat> lowAccessItems, List<KnowledgeBaseSearchTermStat> noResultTerms
    ) {
    }

    public record KnowledgeBaseGovernanceDashboard(
        UUID spaceId, KnowledgeBaseHealthMetrics health, List<KnowledgeBaseGovernanceRisk> risks,
        KnowledgeBaseAccessStats accessStats
    ) {
    }

    public record KnowledgeBaseBulkGovernanceResult(int updatedCount, int archivedCount, int reviewRequestedCount) {
    }
}
