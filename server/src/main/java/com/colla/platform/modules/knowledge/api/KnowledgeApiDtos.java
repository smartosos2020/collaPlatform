package com.colla.platform.modules.knowledge.api;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlock;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCollaborationHealth;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentComment;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentDiffLine;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentMigrationPreview;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPathItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPerformance;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPermission;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPermissionRequest;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentRelation;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentShareLink;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentTemplate;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItemTreeNode;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentVersion;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentVersionDiff;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseMarkdownImportResult;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseAccessItemStat;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseDiscovery;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseGovernanceDashboard;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseGovernanceRisk;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseHealthMetrics;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSearchTermStat;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeApiDtos {
    private KnowledgeApiDtos() {
    }

    static KnowledgeBaseSpaceView space(KnowledgeBaseSpaceSummary source) {
        return new KnowledgeBaseSpaceView(
            source.id(),
            source.name(),
            source.code(),
            source.description(),
            source.icon(),
            source.coverUrl(),
            source.status(),
            source.visibility(),
            source.rootItemId(),
            source.homeItemId(),
            source.ownerId(),
            source.ownerName(),
            source.defaultPermissionLevel(),
            source.createdAt(),
            source.updatedAt(),
            source.itemCount(),
            new KnowledgeBaseNavigation(source.rootItemId(), source.homeItemId(), "/knowledge-bases/" + source.id()),
            permission(source.defaultPermissionLevel()),
            List.of("open", "search", "subscribe", "create_item")
        );
    }

    static KnowledgeBaseSpaceDetailView spaceDetail(KnowledgeBaseSpaceDetail source) {
        return new KnowledgeBaseSpaceDetailView(space(source.space()), item(source.rootItem()), item(source.homeItem()));
    }

    static KnowledgeBaseItemView item(KnowledgeBaseItem source) {
        return new KnowledgeBaseItemView(
            source.id(),
            source.parentId(),
            source.title(),
            source.contentType(),
            source.itemKind(),
            source.currentVersionNo(),
            source.permissionLevel(),
            source.createdBy(),
            source.createdByName(),
            source.createdAt(),
            source.updatedBy(),
            source.updatedByName(),
            source.updatedAt(),
            source.sortOrder(),
            source.description(),
            source.coverUrl(),
            source.defaultPermissionLevel(),
            source.archived(),
            source.maintainerId(),
            source.maintainerName(),
            source.tags(),
            source.category(),
            source.knowledgeStatus(),
            source.reviewDueAt(),
            source.verifiedAt(),
            source.targetObjectType(),
            source.targetObjectId(),
            source.targetRoute(),
            source.displayMode(),
            source.targetTitleStrategy(),
            source.entryAlias(),
            source.targetSummary(),
            permission(source.permissionLevel()),
            itemActions(source)
        );
    }

    static KnowledgeContentDetailView detail(KnowledgeContent source) {
        return new KnowledgeContentDetailView(
            item(source.item()),
            source.content(),
            source.blocks().stream().map(KnowledgeApiDtos::block).toList(),
            source.relations().stream().map(KnowledgeApiDtos::relation).toList(),
            source.permissions().stream().map(KnowledgeApiDtos::permission).toList(),
            source.shareLinks().stream().map(KnowledgeApiDtos::shareLink).toList(),
            source.comments().stream().map(KnowledgeApiDtos::comment).toList(),
            new KnowledgeContentContextView(
                source.context().spaceId(),
                source.context().spaceName(),
                source.context().spaceCode(),
                source.context().rootItemId(),
                source.context().homeItemId(),
                source.context().path().stream().map(KnowledgeApiDtos::pathItem).toList(),
                source.context().pathText(),
                "/knowledge-bases/" + source.context().spaceId() + "/items/" + source.item().id()
            )
        );
    }

    static KnowledgeBaseItemTreeNodeView treeNode(KnowledgeBaseItemTreeNode source) {
        return new KnowledgeBaseItemTreeNodeView(
            item(source.item()),
            source.path(),
            source.depth(),
            source.childCount(),
            source.hasChildren(),
            source.children().stream().map(KnowledgeApiDtos::treeNode).toList()
        );
    }

    static KnowledgeContentBlockView block(KnowledgeContentBlock source) {
        return new KnowledgeContentBlockView(
            source.id(),
            source.itemId(),
            source.parentId(),
            source.blockType(),
            source.content(),
            source.sortOrder(),
            source.schemaVersion(),
            source.attrs(),
            source.richContent(),
            source.plainText(),
            source.anchorId(),
            source.blockVersion(),
            source.createdBy(),
            source.createdAt(),
            source.updatedBy(),
            source.updatedAt(),
            source.embedSummary(),
            source.metadata()
        );
    }

    static com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft toBlockDraft(KnowledgeContentBlockDraft source) {
        return new com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft(
            source.id(),
            source.parentId(),
            source.blockType(),
            source.content(),
            source.sortOrder(),
            source.schemaVersion(),
            source.attrs(),
            source.richContent(),
            source.plainText(),
            source.anchorId(),
            source.deleted()
        );
    }

    static KnowledgeContentVersionView version(KnowledgeContentVersion source) {
        return new KnowledgeContentVersionView(
            source.id(),
            source.itemId(),
            source.versionNo(),
            source.versionName(),
            source.versionType(),
            source.title(),
            source.content(),
            source.summary(),
            source.sourceVersionNo(),
            source.blockSnapshot(),
            source.createdBy(),
            source.createdByName(),
            source.createdAt()
        );
    }

    static KnowledgeContentVersionDiffView versionDiff(KnowledgeContentVersionDiff source) {
        return new KnowledgeContentVersionDiffView(
            source.itemId(),
            source.fromVersionNo(),
            source.toVersionNo(),
            source.lines().stream().map(KnowledgeApiDtos::diffLine).toList()
        );
    }

    private static KnowledgeContentDiffLineView diffLine(KnowledgeContentDiffLine source) {
        return new KnowledgeContentDiffLineView(
            source.type(), source.oldLineNo(), source.newLineNo(), source.content(), source.scope(), source.blockIndex(), source.blockType()
        );
    }

    static KnowledgeContentTemplateView template(KnowledgeContentTemplate source) {
        return new KnowledgeContentTemplateView(
            source.id(),
            source.title(),
            source.description(),
            source.category(),
            source.content(),
            source.builtIn(),
            source.scopeType(),
            source.knowledgeBaseId(),
            source.knowledgeBaseName(),
            source.createdAt()
        );
    }

    static KnowledgeBaseMarkdownImportView importResult(KnowledgeBaseMarkdownImportResult source) {
        return new KnowledgeBaseMarkdownImportView(
            source.spaceId(),
            source.importedCount(),
            source.items().stream().map(KnowledgeApiDtos::item).toList()
        );
    }

    static KnowledgeBaseDiscoveryView discovery(KnowledgeBaseDiscovery source) {
        return new KnowledgeBaseDiscoveryView(
            source.spaceId(),
            source.recentAccessed().stream().map(KnowledgeApiDtos::item).toList(),
            source.favorites().stream().map(KnowledgeApiDtos::item).toList(),
            source.maintainedByMe().stream().map(KnowledgeApiDtos::item).toList(),
            source.dueForReview().stream().map(KnowledgeApiDtos::item).toList(),
            source.popular().stream().map(KnowledgeApiDtos::item).toList(),
            source.recommended().stream().map(KnowledgeApiDtos::item).toList(),
            source.subscribedItems().stream().map(KnowledgeApiDtos::item).toList(),
            source.spaceSubscribed()
        );
    }

    static KnowledgeBaseGovernanceView governance(KnowledgeBaseGovernanceDashboard source) {
        KnowledgeBaseHealthMetrics health = source.health();
        return new KnowledgeBaseGovernanceView(
            source.spaceId(),
            new KnowledgeBaseHealthView(
                health.itemCount(),
                health.activeItemCount(),
                health.outdatedItemCount(),
                health.unmaintainedItemCount(),
                health.ownerlessItemCount(),
                health.highRiskPermissionCount(),
                health.blockCoverageGapCount(),
                health.emptyBlockCount(),
                health.invalidEmbedBlockCount(),
                health.blockCoveragePercent()
            ),
            source.risks().stream().map(KnowledgeApiDtos::governanceRisk).toList(),
            new KnowledgeBaseAccessStatsView(
                source.accessStats().visitorCount(),
                source.accessStats().accessCount(),
                source.accessStats().popularItems().stream().map(KnowledgeApiDtos::accessItem).toList(),
                source.accessStats().lowAccessItems().stream().map(KnowledgeApiDtos::accessItem).toList(),
                source.accessStats().noResultTerms().stream().map(KnowledgeApiDtos::searchTerm).toList()
            )
        );
    }

    private static KnowledgeBaseGovernanceRiskView governanceRisk(KnowledgeBaseGovernanceRisk source) {
        return new KnowledgeBaseGovernanceRiskView(
            source.id(),
            source.ruleCode(),
            source.severity(),
            source.resourceType(),
            source.resourceId(),
            source.title(),
            source.subjectType(),
            source.subjectId(),
            source.subjectName(),
            source.permissionLevel(),
            source.reason(),
            source.actionPath()
        );
    }

    private static KnowledgeBaseAccessItemView accessItem(KnowledgeBaseAccessItemStat source) {
        return new KnowledgeBaseAccessItemView(item(source.item()), source.visitorCount(), source.accessCount(), source.lastAccessedAt());
    }

    private static KnowledgeBaseSearchTermView searchTerm(KnowledgeBaseSearchTermStat source) {
        return new KnowledgeBaseSearchTermView(source.query(), source.count(), source.lastSearchedAt());
    }

    static KnowledgeContentPermissionRequestView permissionRequest(KnowledgeContentPermissionRequest source) {
        return new KnowledgeContentPermissionRequestView(
            source.requestId(), source.itemId(), source.requestedPermissionLevel(), source.notifiedCount(), source.status()
        );
    }

    static KnowledgeContentPerformanceView performance(KnowledgeContentPerformance source) {
        return new KnowledgeContentPerformanceView(
            source.itemId(),
            source.blockCount(),
            source.embedCount(),
            source.commentCount(),
            source.contentLength(),
            source.lineCount(),
            source.largeContent(),
            source.recommendedMode()
        );
    }

    static KnowledgeContentMigrationPreviewView migrationPreview(KnowledgeContentMigrationPreview source) {
        return new KnowledgeContentMigrationPreviewView(
            source.itemId(),
            source.currentVersionNo(),
            source.contentBlockCount(),
            source.storedBlockCount(),
            source.contentLength(),
            source.blockProjectionCurrent(),
            source.rollbackAvailable(),
            source.migrationMode()
        );
    }

    static KnowledgeContentCollaborationHealthView collaborationHealth(KnowledgeContentCollaborationHealth source) {
        return new KnowledgeContentCollaborationHealthView(
            source.itemId(),
            source.serverClock(),
            source.activeUsers(),
            source.dirty(),
            source.stateVector(),
            source.lastSavedAt(),
            source.updatedAt()
        );
    }

    private static KnowledgeContentPathItemView pathItem(KnowledgeContentPathItem source) {
        return new KnowledgeContentPathItemView(source.id(), source.title(), source.contentType(), source.permissionLevel());
    }

    private static KnowledgeContentRelationView relation(KnowledgeContentRelation source) {
        return new KnowledgeContentRelationView(
            source.id(), source.itemId(), source.targetType(), source.targetId(), source.title(), source.webPath(), source.createdAt()
        );
    }

    private static KnowledgeContentPermissionView permission(KnowledgeContentPermission source) {
        return new KnowledgeContentPermissionView(
            source.id(),
            source.itemId(),
            source.subjectType(),
            source.subjectId(),
            source.userId(),
            source.username(),
            source.displayName(),
            source.subjectName(),
            source.subjectDetail(),
            source.permissionLevel(),
            source.sourceType(),
            source.sourceItemId(),
            source.sourceTitle(),
            source.createdAt()
        );
    }

    static KnowledgeContentShareLinkView shareLink(KnowledgeContentShareLink source) {
        return new KnowledgeContentShareLinkView(
            source.id(),
            source.itemId(),
            source.token(),
            source.scope(),
            source.permissionLevel(),
            source.enabled(),
            source.expiresAt(),
            source.createdBy(),
            source.createdByName(),
            source.createdAt(),
            source.updatedBy(),
            source.updatedByName(),
            source.updatedAt(),
            source.knowledgeBaseId(),
            source.knowledgeBaseName(),
            source.knowledgeBaseCode()
        );
    }

    private static KnowledgeContentCommentView comment(KnowledgeContentComment source) {
        return new KnowledgeContentCommentView(
            source.id(),
            source.threadId(),
            source.parentCommentId(),
            source.itemId(),
            source.blockId(),
            source.authorId(),
            source.authorName(),
            source.content(),
            source.anchorType(),
            source.anchorStart(),
            source.anchorEnd(),
            source.anchorText(),
            source.anchorPrefix(),
            source.anchorSuffix(),
            source.anchorVersionNo(),
            source.root(),
            source.resolved(),
            source.resolvedAt(),
            source.resolvedBy(),
            source.resolvedByName(),
            source.reopenedAt(),
            source.reopenedBy(),
            source.reopenedByName(),
            source.createdAt(),
            source.replies().stream().map(KnowledgeApiDtos::comment).toList()
        );
    }

    private static KnowledgeCollaborationPermission permission(String level) {
        return new KnowledgeCollaborationPermission(level, permissionText(level), canEdit(level));
    }

    private static List<String> itemActions(KnowledgeBaseItem source) {
        if (source.archived()) {
            return List.of("open", "restore");
        }
        return canEdit(source.permissionLevel())
            ? List.of("open", "edit", "comment", "share", "move", "archive")
            : List.of("open", "comment", "request_permission");
    }

    private static boolean canEdit(String permissionLevel) {
        return List.of("edit", "manage", "owner").contains(permissionLevel);
    }

    private static String permissionText(String permissionLevel) {
        return switch (permissionLevel == null ? "" : permissionLevel) {
            case "owner", "manage" -> "可管理";
            case "edit" -> "可编辑";
            case "comment" -> "可评论";
            case "view" -> "可查看";
            default -> "可申请权限";
        };
    }

    public record KnowledgeBaseSpaceView(
        UUID id,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String status,
        String visibility,
        UUID rootItemId,
        UUID homeItemId,
        UUID ownerId,
        String ownerName,
        String defaultPermissionLevel,
        Instant createdAt,
        Instant updatedAt,
        long itemCount,
        KnowledgeBaseNavigation navigation,
        KnowledgeCollaborationPermission collaborationPermission,
        List<String> availableActions
    ) {
    }

    public record KnowledgeBaseSpaceDetailView(KnowledgeBaseSpaceView space, KnowledgeBaseItemView rootItem, KnowledgeBaseItemView homeItem) {
    }

    public record KnowledgeBaseNavigation(UUID rootItemId, UUID homeItemId, String webPath) {
    }

    public record KnowledgeBaseItemView(
        UUID id,
        UUID parentId,
        String title,
        String contentType,
        String itemKind,
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
        boolean archived,
        UUID maintainerId,
        String maintainerName,
        List<String> tags,
        String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt,
        String targetObjectType,
        UUID targetObjectId,
        String targetRoute,
        String displayMode,
        String targetTitleStrategy,
        String entryAlias,
        PlatformObjectSummary targetSummary,
        KnowledgeCollaborationPermission collaborationPermission,
        List<String> availableActions
    ) {
    }

    public record KnowledgeContentDetailView(
        KnowledgeBaseItemView item,
        String content,
        List<KnowledgeContentBlockView> blocks,
        List<KnowledgeContentRelationView> relations,
        List<KnowledgeContentPermissionView> permissions,
        List<KnowledgeContentShareLinkView> shareLinks,
        List<KnowledgeContentCommentView> comments,
        KnowledgeContentContextView context
    ) {
    }

    public record KnowledgeBaseItemTreeNodeView(
        KnowledgeBaseItemView item,
        String path,
        int depth,
        int childCount,
        boolean hasChildren,
        List<KnowledgeBaseItemTreeNodeView> children
    ) {
    }

    public record KnowledgeContentBlockView(
        UUID id,
        UUID itemId,
        UUID parentId,
        String blockType,
        String content,
        int sortOrder,
        int schemaVersion,
        Map<String, Object> attrs,
        Map<String, Object> richContent,
        String plainText,
        String anchorId,
        int blockVersion,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        PlatformObjectSummary embedSummary,
        Map<String, Object> metadata
    ) {
    }

    public record KnowledgeContentBlockDraft(
        UUID id,
        UUID parentId,
        String blockType,
        String content,
        Integer sortOrder,
        Integer schemaVersion,
        Map<String, Object> attrs,
        Map<String, Object> richContent,
        String plainText,
        String anchorId,
        Boolean deleted
    ) {
    }

    public record KnowledgeContentVersionView(
        UUID id,
        UUID itemId,
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

    public record KnowledgeContentVersionDiffView(
        UUID itemId,
        int fromVersionNo,
        int toVersionNo,
        List<KnowledgeContentDiffLineView> lines
    ) {
    }

    public record KnowledgeContentDiffLineView(
        String type,
        int oldLineNo,
        int newLineNo,
        String content,
        String scope,
        Integer blockIndex,
        String blockType
    ) {
    }

    public record KnowledgeContentTemplateView(
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

    public record KnowledgeBaseMarkdownImportView(UUID spaceId, int importedCount, List<KnowledgeBaseItemView> items) {
    }

    public record KnowledgeBaseDiscoveryView(
        UUID spaceId,
        List<KnowledgeBaseItemView> recentAccessed,
        List<KnowledgeBaseItemView> favorites,
        List<KnowledgeBaseItemView> maintainedByMe,
        List<KnowledgeBaseItemView> dueForReview,
        List<KnowledgeBaseItemView> popular,
        List<KnowledgeBaseItemView> recommended,
        List<KnowledgeBaseItemView> subscribedItems,
        boolean spaceSubscribed
    ) {
    }

    public record KnowledgeBaseGovernanceView(
        UUID spaceId,
        KnowledgeBaseHealthView health,
        List<KnowledgeBaseGovernanceRiskView> risks,
        KnowledgeBaseAccessStatsView accessStats
    ) {
    }

    public record KnowledgeBaseHealthView(
        long itemCount,
        long activeItemCount,
        long outdatedItemCount,
        long unmaintainedItemCount,
        long ownerlessItemCount,
        long highRiskPermissionCount,
        long blockCoverageGapCount,
        long emptyBlockCount,
        long invalidEmbedBlockCount,
        double blockCoveragePercent
    ) {
    }

    public record KnowledgeBaseGovernanceRiskView(
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

    public record KnowledgeBaseAccessStatsView(
        long visitorCount,
        long accessCount,
        List<KnowledgeBaseAccessItemView> popularItems,
        List<KnowledgeBaseAccessItemView> lowAccessItems,
        List<KnowledgeBaseSearchTermView> noResultTerms
    ) {
    }

    public record KnowledgeBaseAccessItemView(
        KnowledgeBaseItemView item,
        long visitorCount,
        long accessCount,
        Instant lastAccessedAt
    ) {
    }

    public record KnowledgeBaseSearchTermView(String query, long count, Instant lastSearchedAt) {
    }

    public record KnowledgeContentPathItemView(UUID id, String title, String contentType, String permissionLevel) {
    }

    public record KnowledgeContentRelationView(
        UUID id,
        UUID itemId,
        String targetType,
        UUID targetId,
        String title,
        String webPath,
        Instant createdAt
    ) {
    }

    public record KnowledgeContentPermissionView(
        UUID id,
        UUID itemId,
        String subjectType,
        UUID subjectId,
        UUID userId,
        String username,
        String displayName,
        String subjectName,
        String subjectDetail,
        String permissionLevel,
        String sourceType,
        UUID sourceItemId,
        String sourceTitle,
        Instant createdAt
    ) {
    }

    public record KnowledgeContentShareLinkView(
        UUID id,
        UUID itemId,
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

    public record KnowledgeContentPermissionRequestView(
        UUID requestId,
        UUID itemId,
        String requestedPermissionLevel,
        int notifiedCount,
        String status
    ) {
    }

    public record KnowledgeContentCommentView(
        UUID id,
        UUID threadId,
        UUID parentCommentId,
        UUID itemId,
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
        List<KnowledgeContentCommentView> replies
    ) {
    }

    public record KnowledgeContentContextView(
        UUID spaceId,
        String spaceName,
        String spaceCode,
        UUID rootItemId,
        UUID homeItemId,
        List<KnowledgeContentPathItemView> path,
        String pathText,
        String webPath
    ) {
    }

    public record KnowledgeContentPerformanceView(
        UUID itemId,
        int blockCount,
        int embedCount,
        int commentCount,
        int contentLength,
        int lineCount,
        boolean largeContent,
        String recommendedMode
    ) {
    }

    public record KnowledgeContentMigrationPreviewView(
        UUID itemId,
        int currentVersionNo,
        int contentBlockCount,
        int storedBlockCount,
        int contentLength,
        boolean blockProjectionCurrent,
        boolean rollbackAvailable,
        String migrationMode
    ) {
    }

    public record KnowledgeContentCollaborationHealthView(
        UUID itemId,
        long serverClock,
        int activeUsers,
        boolean dirty,
        String stateVector,
        Instant lastSavedAt,
        Instant updatedAt
    ) {
    }

    public record KnowledgeCollaborationPermission(String level, String displayText, boolean canEdit) {
    }
}
