package com.colla.platform.modules.doc.infrastructure;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentComment;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPermission;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentRelation;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    UUID createDocument(UUID workspaceId, UUID parentId, String title, String content, UUID createdBy);

    void updateDocument(UUID workspaceId, UUID documentId, UUID parentId, String title, String content, int nextVersionNo, UUID updatedBy);

    void moveDocument(UUID workspaceId, UUID documentId, UUID parentId, UUID updatedBy);

    void addVersion(UUID workspaceId, UUID documentId, int versionNo, String title, String content, UUID createdBy);

    void replaceBlocks(UUID workspaceId, UUID documentId, List<DocumentBlockDraft> blocks, UUID actorId);

    List<DocumentBlock> listBlocks(UUID workspaceId, UUID documentId);

    List<DocumentSummary> listDocuments(UUID workspaceId, UUID userId);

    Optional<DocumentSummary> findDocument(UUID workspaceId, UUID documentId);

    Optional<String> findContent(UUID workspaceId, UUID documentId);

    List<DocumentVersion> listVersions(UUID workspaceId, UUID documentId);

    Optional<DocumentVersion> findVersion(UUID workspaceId, UUID documentId, int versionNo);

    void upsertPermission(UUID workspaceId, UUID documentId, UUID userId, String permissionLevel, UUID actorId);

    Optional<String> findPermissionLevel(UUID workspaceId, UUID documentId, UUID userId);

    List<DocumentPermission> listPermissions(UUID workspaceId, UUID documentId);

    void addRelation(UUID workspaceId, UUID documentId, String targetType, UUID targetId, UUID createdBy);

    List<DocumentRelation> listRelations(UUID workspaceId, UUID documentId);

    UUID addComment(UUID workspaceId, UUID documentId, UUID blockId, UUID authorId, String content);

    void resolveComment(UUID workspaceId, UUID documentId, UUID commentId, UUID resolvedBy);

    List<DocumentComment> listComments(UUID workspaceId, UUID documentId);

    List<UUID> findActiveUserIdsByUsernames(UUID workspaceId, List<String> usernames);
}
