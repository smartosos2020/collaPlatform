package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTreeNode;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseDiscovery;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class UserKnowledgeDtos {
    private UserKnowledgeDtos() {
    }

    static UserKnowledgeSpaceView space(KnowledgeBaseSpaceSummary space) {
        return new UserKnowledgeSpaceView(
            space.id(),
            space.name(),
            space.code(),
            space.description(),
            space.icon(),
            space.coverUrl(),
            space.status(),
            space.visibility(),
            space.rootDocumentId(),
            space.homeDocumentId(),
            space.ownerId(),
            space.ownerName(),
            space.defaultPermissionLevel(),
            space.createdAt(),
            space.updatedAt(),
            space.documentCount(),
            new UserKnowledgeSpaceNavigation(space.rootDocumentId(), space.homeDocumentId(), "/knowledge-bases/" + space.id()),
            new UserCollaborationPermission(space.defaultPermissionLevel(), permissionText(space.defaultPermissionLevel()), canEdit(space.defaultPermissionLevel())),
            List.of("open", "search", "subscribe", "create_content")
        );
    }

    static UserKnowledgeSpaceDetailView spaceDetail(KnowledgeBaseSpaceDetail detail) {
        return new UserKnowledgeSpaceDetailView(
            space(detail.space()),
            content(detail.rootDocument()),
            content(detail.homeDocument())
        );
    }

    static UserKnowledgeContentView content(DocumentSummary document) {
        return new UserKnowledgeContentView(
            document.id(),
            document.parentId(),
            document.title(),
            document.docType(),
            document.currentVersionNo(),
            document.permissionLevel(),
            document.createdBy(),
            document.createdByName(),
            document.createdAt(),
            document.updatedBy(),
            document.updatedByName(),
            document.updatedAt(),
            document.sortOrder(),
            document.description(),
            document.coverUrl(),
            document.defaultPermissionLevel(),
            document.knowledgeBase(),
            document.archived(),
            document.maintainerId(),
            document.maintainerName(),
            document.tags(),
            document.category(),
            document.knowledgeStatus(),
            document.reviewDueAt(),
            document.verifiedAt(),
            document.nodeKind(),
            document.targetObjectType(),
            document.targetObjectId(),
            document.targetRoute(),
            document.displayMode(),
            document.targetTitleStrategy(),
            document.entryAlias(),
            document.targetSummary(),
            new UserKnowledgeContentEntry(document.nodeKind(), document.docType(), document.targetObjectType(), document.targetObjectId(), document.targetRoute()),
            new UserCollaborationPermission(document.permissionLevel(), permissionText(document.permissionLevel()), canEdit(document.permissionLevel())),
            contentActions(document)
        );
    }

    static UserKnowledgeContentDetailView contentDetail(DocumentDetail detail) {
        return new UserKnowledgeContentDetailView(
            content(detail.document()),
            detail.content(),
            detail.blocks(),
            detail.relations(),
            detail.permissions(),
            detail.shareLinks(),
            detail.comments(),
            detail.knowledgeContext()
        );
    }

    static UserKnowledgeTreeNodeView treeNode(DocumentTreeNode node) {
        return new UserKnowledgeTreeNodeView(
            content(node.document()),
            node.path(),
            node.depth(),
            node.childCount(),
            node.hasChildren(),
            node.children().stream().map(UserKnowledgeDtos::treeNode).toList()
        );
    }

    static UserKnowledgeDiscoveryView discovery(KnowledgeBaseDiscovery discovery) {
        return new UserKnowledgeDiscoveryView(
            discovery.spaceId(),
            discovery.recentAccessed().stream().map(UserKnowledgeDtos::content).toList(),
            discovery.favorites().stream().map(UserKnowledgeDtos::content).toList(),
            discovery.maintainedByMe().stream().map(UserKnowledgeDtos::content).toList(),
            discovery.dueForReview().stream().map(UserKnowledgeDtos::content).toList(),
            discovery.popular().stream().map(UserKnowledgeDtos::content).toList(),
            discovery.recommended().stream().map(UserKnowledgeDtos::content).toList(),
            discovery.subscribedDocuments().stream().map(UserKnowledgeDtos::content).toList(),
            discovery.spaceSubscribed()
        );
    }

    private static List<String> contentActions(DocumentSummary document) {
        if (document.archived()) {
            return List.of("open", "restore");
        }
        return canEdit(document.permissionLevel())
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

    record UserKnowledgeSpaceView(
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
        long documentCount,
        UserKnowledgeSpaceNavigation navigation,
        UserCollaborationPermission collaborationPermission,
        List<String> availableActions
    ) {
    }

    record UserKnowledgeSpaceDetailView(
        UserKnowledgeSpaceView space,
        UserKnowledgeContentView rootDocument,
        UserKnowledgeContentView homeDocument
    ) {
    }

    record UserKnowledgeContentView(
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
        Instant verifiedAt,
        String nodeKind,
        String targetObjectType,
        UUID targetObjectId,
        String targetRoute,
        String displayMode,
        String targetTitleStrategy,
        String entryAlias,
        PlatformObjectSummary targetSummary,
        UserKnowledgeContentEntry contentEntry,
        UserCollaborationPermission collaborationPermission,
        List<String> availableActions
    ) {
    }

    record UserKnowledgeContentDetailView(
        UserKnowledgeContentView document,
        String content,
        List<?> blocks,
        List<?> relations,
        List<?> permissions,
        List<?> shareLinks,
        List<?> comments,
        Object knowledgeContext
    ) {
    }

    record UserKnowledgeTreeNodeView(
        UserKnowledgeContentView document,
        String path,
        int depth,
        int childCount,
        boolean hasChildren,
        List<UserKnowledgeTreeNodeView> children
    ) {
    }

    record UserKnowledgeDiscoveryView(
        UUID spaceId,
        List<UserKnowledgeContentView> recentAccessed,
        List<UserKnowledgeContentView> favorites,
        List<UserKnowledgeContentView> maintainedByMe,
        List<UserKnowledgeContentView> dueForReview,
        List<UserKnowledgeContentView> popular,
        List<UserKnowledgeContentView> recommended,
        List<UserKnowledgeContentView> subscribedDocuments,
        boolean spaceSubscribed
    ) {
    }

    record UserKnowledgeSpaceNavigation(UUID rootDocumentId, UUID homeDocumentId, String webPath) {
    }

    record UserKnowledgeContentEntry(String nodeKind, String docType, String targetObjectType, UUID targetObjectId, String targetRoute) {
    }

    record UserCollaborationPermission(String level, String displayText, boolean canEdit) {
    }
}
