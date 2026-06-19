package com.colla.platform.modules.doc.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colla.platform.modules.base.application.BaseService;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDiffLine;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPathItem;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTreeNode;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersionDiff;
import com.colla.platform.modules.doc.infrastructure.DocumentRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.modules.permission.application.PermissionDecisionService;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_.-]{2,64})");
    private static final Set<String> BLOCK_TYPES = Set.of(
        "paragraph",
        "heading",
        "list",
        "task",
        "quote",
        "code",
        "table",
        "embed",
        "base_view",
        "issue_embed",
        "message_embed",
        "file_embed",
        "link"
    );
    private static final Set<String> EMBED_BLOCK_TYPES = Set.of("embed", "base_view", "issue_embed", "message_embed", "file_embed", "link");
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    private final DocumentRepository documentRepository;
    private final PlatformObjectRepository objectRepository;
    private final DomainEventRepository eventRepository;
    private final ProjectRepository projectRepository;
    private final FileRepository fileRepository;
    private final BaseService baseService;
    private final AuditService auditService;
    private final PermissionDecisionService permissionDecisionService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<PlatformObjectResolverRegistry> objectResolverRegistryProvider;

    public DocumentService(
        DocumentRepository documentRepository,
        PlatformObjectRepository objectRepository,
        DomainEventRepository eventRepository,
        ProjectRepository projectRepository,
        FileRepository fileRepository,
        BaseService baseService,
        AuditService auditService,
        PermissionDecisionService permissionDecisionService,
        ObjectMapper objectMapper,
        ObjectProvider<PlatformObjectResolverRegistry> objectResolverRegistryProvider
    ) {
        this.documentRepository = documentRepository;
        this.objectRepository = objectRepository;
        this.eventRepository = eventRepository;
        this.projectRepository = projectRepository;
        this.fileRepository = fileRepository;
        this.baseService = baseService;
        this.auditService = auditService;
        this.permissionDecisionService = permissionDecisionService;
        this.objectMapper = objectMapper;
        this.objectResolverRegistryProvider = objectResolverRegistryProvider;
    }

    public List<DocumentSummary> listDocuments(CurrentUser currentUser, boolean includeArchived) {
        return documentRepository.listDocuments(currentUser.workspaceId(), currentUser.id(), includeArchived);
    }

    public List<DocumentTreeNode> listDocumentTree(CurrentUser currentUser, boolean includeArchived) {
        List<DocumentSummary> documents = documentRepository.listDocuments(currentUser.workspaceId(), currentUser.id(), includeArchived);
        return buildTree(documents);
    }

    public List<DocumentPathItem> documentPath(CurrentUser currentUser, UUID documentId) {
        requireView(currentUser, documentId);
        Map<UUID, DocumentSummary> byId = new HashMap<>();
        for (DocumentSummary document : documentRepository.listDocuments(currentUser.workspaceId(), currentUser.id(), true)) {
            byId.put(document.id(), document);
        }
        List<DocumentPathItem> reversed = new ArrayList<>();
        DocumentSummary current = byId.get(documentId);
        while (current != null) {
            reversed.add(new DocumentPathItem(current.id(), current.title(), current.docType(), current.permissionLevel()));
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        List<DocumentPathItem> path = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    @Transactional
    public DocumentDetail createDocument(CurrentUser currentUser, UUID parentId, String title, String docType, String content) {
        if (parentId != null) {
            requireEdit(currentUser, parentId);
        }
        String normalizedTitle = normalizeTitle(title);
        String normalizedDocType = normalizeDocType(docType);
        String normalizedContent = content == null ? "" : content;
        int nextSortOrder = nextSortOrder(currentUser, parentId);
        UUID documentId = documentRepository.createDocument(
            currentUser.workspaceId(),
            parentId,
            normalizedTitle,
            normalizedDocType,
            normalizedContent,
            nextSortOrder,
            currentUser.id()
        );
        documentRepository.copyParentPermissions(currentUser.workspaceId(), documentId, parentId, currentUser.id());
        documentRepository.upsertPermission(currentUser.workspaceId(), documentId, currentUser.id(), "manage", currentUser.id());
        documentRepository.addVersion(currentUser.workspaceId(), documentId, 1, normalizedTitle, normalizedContent, currentUser.id());
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, blocksFromContent(normalizedContent), currentUser.id());
        registerDocumentObject(currentUser.workspaceId(), documentId, normalizedTitle);
        eventRepository.append(
            currentUser.workspaceId(),
            "document.created",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString()),
            "document.created:" + documentId
        );
        return getDocument(currentUser, documentId);
    }

    public DocumentDetail getDocument(CurrentUser currentUser, UUID documentId) {
        DocumentSummary summary = requireView(currentUser, documentId);
        String content = documentRepository.findContent(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return new DocumentDetail(
            summary,
            content,
            hydrateBlocks(currentUser, documentRepository.listBlocks(currentUser.workspaceId(), documentId)),
            documentRepository.listRelations(currentUser.workspaceId(), documentId),
            documentRepository.listPermissions(currentUser.workspaceId(), documentId),
            documentRepository.listComments(currentUser.workspaceId(), documentId)
        );
    }

    @Transactional
    public DocumentDetail saveDocument(CurrentUser currentUser, UUID documentId, int baseVersionNo, String title, String content) {
        DocumentSummary before = requireEdit(currentUser, documentId);
        if (baseVersionNo != before.currentVersionNo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document version conflict");
        }
        String normalizedTitle = title == null || title.isBlank() ? before.title() : title.trim();
        String normalizedContent = content == null ? "" : content;
        int nextVersionNo = before.currentVersionNo() + 1;
        documentRepository.updateDocument(
            currentUser.workspaceId(),
            documentId,
            before.parentId(),
            normalizedTitle,
            normalizedContent,
            nextVersionNo,
            currentUser.id()
        );
        documentRepository.addVersion(currentUser.workspaceId(), documentId, nextVersionNo, normalizedTitle, normalizedContent, currentUser.id());
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, blocksFromContent(normalizedContent), currentUser.id());
        registerDocumentObject(currentUser.workspaceId(), documentId, normalizedTitle);
        eventRepository.append(
            currentUser.workspaceId(),
            "document.updated",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "document.updated:" + documentId + ":" + nextVersionNo
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail moveDocument(CurrentUser currentUser, UUID documentId, UUID parentId, Integer sortOrder) {
        DocumentSummary document = requireManage(currentUser, documentId);
        if (parentId != null) {
            requireEdit(currentUser, parentId);
            if (documentId.equals(parentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document cannot be moved under itself");
            }
            if (documentRepository.isDescendant(currentUser.workspaceId(), documentId, parentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document cannot be moved under its descendant");
            }
        }
        int nextSortOrder = sortOrder == null ? document.sortOrder() : sortOrder;
        documentRepository.moveDocument(currentUser.workspaceId(), document.id(), parentId, nextSortOrder, currentUser.id());
        documentRepository.copyParentPermissions(currentUser.workspaceId(), document.id(), parentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.moved",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "parentId", parentId == null ? "" : parentId.toString(), "sortOrder", Integer.toString(nextSortOrder)),
            "document.moved:" + documentId + ":" + System.nanoTime()
        );
        auditService.log(
            currentUser,
            "document.moved",
            "document",
            documentId,
            Map.of("parentId", parentId == null ? "" : parentId.toString(), "sortOrder", Integer.toString(nextSortOrder))
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail archiveDocument(CurrentUser currentUser, UUID documentId) {
        requireManage(currentUser, documentId);
        documentRepository.archiveDocumentTree(currentUser.workspaceId(), documentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.archived",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString()),
            "document.archived:" + documentId + ":" + System.nanoTime()
        );
        auditService.log(currentUser, "document.archived", "document", documentId, Map.of("documentId", documentId.toString()));
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail restoreDocument(CurrentUser currentUser, UUID documentId) {
        requireManage(currentUser, documentId);
        documentRepository.restoreDocumentTree(currentUser.workspaceId(), documentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.restored",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString()),
            "document.restored:" + documentId + ":" + System.nanoTime()
        );
        auditService.log(currentUser, "document.restored", "document", documentId, Map.of("documentId", documentId.toString()));
        return getDocument(currentUser, documentId);
    }

    public List<DocumentVersion> listVersions(CurrentUser currentUser, UUID documentId) {
        requireView(currentUser, documentId);
        return documentRepository.listVersions(currentUser.workspaceId(), documentId);
    }

    public List<DocumentBlock> listBlocks(CurrentUser currentUser, UUID documentId) {
        requireView(currentUser, documentId);
        return hydrateBlocks(currentUser, documentRepository.listBlocks(currentUser.workspaceId(), documentId));
    }

    @Transactional
    public DocumentDetail saveBlocks(CurrentUser currentUser, UUID documentId, int baseVersionNo, List<DocumentBlockDraft> blocks) {
        DocumentSummary before = requireEdit(currentUser, documentId);
        if (baseVersionNo != before.currentVersionNo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document version conflict");
        }
        List<DocumentBlockDraft> normalizedBlocks = normalizeBlocks(blocks);
        String normalizedContent = contentFromBlocks(normalizedBlocks);
        int nextVersionNo = before.currentVersionNo() + 1;
        documentRepository.updateDocument(
            currentUser.workspaceId(),
            documentId,
            before.parentId(),
            before.title(),
            normalizedContent,
            nextVersionNo,
            currentUser.id()
        );
        documentRepository.addVersion(currentUser.workspaceId(), documentId, nextVersionNo, before.title(), normalizedContent, currentUser.id());
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, normalizedBlocks, currentUser.id());
        registerDocumentObject(currentUser.workspaceId(), documentId, before.title());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.blocks.updated",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "document.blocks.updated:" + documentId + ":" + nextVersionNo
        );
        return getDocument(currentUser, documentId);
    }

    public DocumentVersionDiff diffVersions(CurrentUser currentUser, UUID documentId, int fromVersionNo, int toVersionNo) {
        requireView(currentUser, documentId);
        DocumentVersion from = documentRepository.findVersion(currentUser.workspaceId(), documentId, fromVersionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document from version not found"));
        DocumentVersion to = documentRepository.findVersion(currentUser.workspaceId(), documentId, toVersionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document to version not found"));
        return new DocumentVersionDiff(documentId, fromVersionNo, toVersionNo, diffLines(from.content(), to.content()));
    }

    @Transactional
    public DocumentDetail restoreVersion(CurrentUser currentUser, UUID documentId, int versionNo) {
        DocumentSummary current = requireEdit(currentUser, documentId);
        DocumentVersion version = documentRepository.findVersion(currentUser.workspaceId(), documentId, versionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document version not found"));
        int nextVersionNo = current.currentVersionNo() + 1;
        documentRepository.updateDocument(
            currentUser.workspaceId(),
            documentId,
            current.parentId(),
            version.title(),
            version.content(),
            nextVersionNo,
            currentUser.id()
        );
        documentRepository.addVersion(currentUser.workspaceId(), documentId, nextVersionNo, version.title(), version.content(), currentUser.id());
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, blocksFromContent(version.content()), currentUser.id());
        registerDocumentObject(currentUser.workspaceId(), documentId, version.title());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.version.restored",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "fromVersionNo", Integer.toString(versionNo), "versionNo", Integer.toString(nextVersionNo)),
            "document.version.restored:" + documentId + ":" + nextVersionNo
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail grantPermission(CurrentUser currentUser, UUID documentId, UUID userId, String permissionLevel) {
        requireManage(currentUser, documentId);
        String normalizedPermission = normalizePermission(permissionLevel);
        documentRepository.upsertPermission(currentUser.workspaceId(), documentId, userId, normalizedPermission, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.permission.granted",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "userId", userId.toString(), "permissionLevel", normalizedPermission),
            "document.permission.granted:" + documentId + ":" + userId
        );
        auditService.log(
            currentUser,
            "permission.granted",
            "document",
            documentId,
            Map.of("userId", userId.toString(), "permissionLevel", normalizedPermission)
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail addRelation(CurrentUser currentUser, UUID documentId, String targetType, UUID targetId) {
        requireEdit(currentUser, documentId);
        String type = normalizeTargetType(targetType);
        validateRelationTarget(currentUser, type, targetId);
        documentRepository.addRelation(currentUser.workspaceId(), documentId, type, targetId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.relation.added",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "targetType", type, "targetId", targetId.toString()),
            "document.relation.added:" + documentId + ":" + type + ":" + targetId
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail addComment(CurrentUser currentUser, UUID documentId, UUID blockId, String content) {
        DocumentSummary document = requireEdit(currentUser, documentId);
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required");
        }
        if (blockId != null && documentRepository.listBlocks(currentUser.workspaceId(), documentId).stream().noneMatch(block -> block.id().equals(blockId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment block is not part of this document");
        }
        String normalizedContent = content.trim();
        UUID commentId = documentRepository.addComment(currentUser.workspaceId(), documentId, blockId, currentUser.id(), normalizedContent);
        for (UUID mentionedUserId : resolveMentions(currentUser, normalizedContent)) {
            if (mentionedUserId.equals(currentUser.id()) || documentRepository.findPermissionLevel(currentUser.workspaceId(), documentId, mentionedUserId).isEmpty()) {
                continue;
            }
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "document",
                documentId,
                currentUser.id(),
                Map.of(
                    "recipientId", mentionedUserId.toString(),
                    "notificationType", "document_comment_mention",
                    "title", currentUser.displayName() + " 在文档「" + document.title() + "」评论中提到了你",
                    "body", normalizedContent,
                    "targetType", "document",
                    "targetId", documentId.toString(),
                    "webPath", "/docs/" + documentId + "?commentId=" + commentId,
                    "dedupeKey", "document.comment.mention:" + commentId + ":" + mentionedUserId
                ),
                "notification.document.comment.mention:" + commentId + ":" + mentionedUserId
            );
        }
        auditService.log(
            currentUser,
            "document.comment.added",
            "document",
            documentId,
            Map.of("commentId", commentId.toString(), "blockId", blockId == null ? "" : blockId.toString())
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail resolveComment(CurrentUser currentUser, UUID documentId, UUID commentId) {
        requireEdit(currentUser, documentId);
        documentRepository.resolveComment(currentUser.workspaceId(), documentId, commentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.comment.resolved",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "commentId", commentId.toString()),
            "document.comment.resolved:" + commentId
        );
        auditService.log(
            currentUser,
            "document.comment.resolved",
            "document",
            documentId,
            Map.of("commentId", commentId.toString())
        );
        return getDocument(currentUser, documentId);
    }

    DocumentSummary requireView(CurrentUser currentUser, UUID documentId) {
        return requirePermission(currentUser, documentId, "view");
    }

    private DocumentSummary requireEdit(CurrentUser currentUser, UUID documentId) {
        return requirePermission(currentUser, documentId, "edit");
    }

    private DocumentSummary requireManage(CurrentUser currentUser, UUID documentId) {
        return requirePermission(currentUser, documentId, "manage");
    }

    private DocumentSummary requirePermission(CurrentUser currentUser, UUID documentId, String requiredLevel) {
        DocumentSummary document = documentRepository.findDocument(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        String permission = documentRepository.findPermissionLevel(currentUser.workspaceId(), documentId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Document access denied"));
        if (!permissionDecisionService.hasLevel(permission, requiredLevel)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document access denied");
        }
        return withPermission(document, permission);
    }

    private void validateRelationTarget(CurrentUser currentUser, String targetType, UUID targetId) {
        if ("issue".equals(targetType)) {
            IssueSummary issue = projectRepository.findIssue(currentUser.workspaceId(), targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
            if (!projectRepository.isProjectMember(currentUser.workspaceId(), issue.projectId(), currentUser.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Issue access denied");
            }
            return;
        }
        if ("file".equals(targetType)) {
            fileRepository.find(currentUser.workspaceId(), targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
            return;
        }
        if ("base".equals(targetType)) {
            baseService.requireView(currentUser, targetId);
            return;
        }
        if ("base_table".equals(targetType)) {
            baseService.requireTableView(currentUser, targetId);
            return;
        }
        if ("base_record".equals(targetType)) {
            baseService.getRecord(currentUser, targetId);
        }
    }

    private List<DocumentBlock> hydrateBlocks(CurrentUser currentUser, List<DocumentBlock> blocks) {
        return blocks.stream()
            .map(block -> {
                Map<String, Object> metadata = parseStructuredBlockMetadata(block);
                PlatformObjectSummary summary = resolveEmbedSummary(currentUser, block.blockType(), metadata);
                return new DocumentBlock(
                    block.id(),
                    block.documentId(),
                    block.blockType(),
                    block.content(),
                    block.sortOrder(),
                    block.createdAt(),
                    block.updatedAt(),
                    summary,
                    metadata
                );
            })
            .toList();
    }

    private Map<String, Object> parseStructuredBlockMetadata(DocumentBlock block) {
        if (!"table".equals(block.blockType()) && !EMBED_BLOCK_TYPES.contains(block.blockType())) {
            return Map.of();
        }
        return parseJsonObject(block.content()).orElseGet(() -> Map.of("parseError", "invalid_json"));
    }

    private PlatformObjectSummary resolveEmbedSummary(CurrentUser currentUser, String blockType, Map<String, Object> metadata) {
        if (!EMBED_BLOCK_TYPES.contains(blockType)) {
            return null;
        }
        String objectType = normalizeEmbedObjectType(blockType, asString(metadata.getOrDefault("objectType", metadata.get("targetType"))));
        UUID objectId = parseUuid(asString(metadata.getOrDefault("objectId", metadata.get("targetId"))));
        if (objectType.isBlank() || objectId == null) {
            return PlatformObjectSummary.unavailable(objectType.isBlank() ? "invalid" : objectType, new UUID(0L, 0L), ObjectAccessState.invalid);
        }
        return objectResolverRegistryProvider.getObject().resolve(currentUser, objectType, objectId);
    }

    private String normalizeEmbedObjectType(String blockType, String objectType) {
        if ("base_view".equals(blockType) && objectType.isBlank()) {
            return "base_table";
        }
        if ("issue_embed".equals(blockType)) {
            return "issue";
        }
        if ("message_embed".equals(blockType)) {
            return "message";
        }
        if ("file_embed".equals(blockType)) {
            return "file";
        }
        return objectType;
    }

    private void registerDocumentObject(UUID workspaceId, UUID documentId, String title) {
        objectRepository.upsertObjectLink(
            workspaceId,
            "document",
            documentId,
            "/docs/" + documentId,
            "colla://document/" + documentId,
            title
        );
    }

    private Set<UUID> resolveMentions(CurrentUser currentUser, String content) {
        Matcher matcher = MENTION_PATTERN.matcher(content);
        List<String> usernames = new ArrayList<>();
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        return new LinkedHashSet<>(documentRepository.findActiveUserIdsByUsernames(currentUser.workspaceId(), usernames));
    }

    private List<DocumentDiffLine> diffLines(String oldContent, String newContent) {
        String[] oldLines = splitLines(oldContent);
        String[] newLines = splitLines(newContent);
        int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
        for (int i = oldLines.length - 1; i >= 0; i--) {
            for (int j = newLines.length - 1; j >= 0; j--) {
                lcs[i][j] = oldLines[i].equals(newLines[j])
                    ? lcs[i + 1][j + 1] + 1
                    : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<DocumentDiffLine> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < oldLines.length || j < newLines.length) {
            if (i < oldLines.length && j < newLines.length && oldLines[i].equals(newLines[j])) {
                lines.add(new DocumentDiffLine("context", i + 1, j + 1, oldLines[i]));
                i++;
                j++;
            } else if (j < newLines.length && (i == oldLines.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                lines.add(new DocumentDiffLine("added", 0, j + 1, newLines[j]));
                j++;
            } else {
                lines.add(new DocumentDiffLine("removed", i + 1, 0, oldLines[i]));
                i++;
            }
        }
        return lines;
    }

    private String[] splitLines(String content) {
        return (content == null ? "" : content).split("\\R", -1);
    }

    private List<DocumentTreeNode> buildTree(List<DocumentSummary> documents) {
        Map<UUID, List<DocumentSummary>> byParent = new HashMap<>();
        Set<UUID> visibleIds = new LinkedHashSet<>();
        for (DocumentSummary document : documents) {
            visibleIds.add(document.id());
            byParent.computeIfAbsent(document.parentId(), ignored -> new ArrayList<>()).add(document);
        }
        for (List<DocumentSummary> siblings : byParent.values()) {
            siblings.sort(documentOrder());
        }
        List<DocumentSummary> roots = documents.stream()
            .filter(document -> document.parentId() == null || !visibleIds.contains(document.parentId()))
            .sorted(documentOrder())
            .toList();
        Set<UUID> visited = new LinkedHashSet<>();
        List<DocumentTreeNode> nodes = new ArrayList<>();
        for (DocumentSummary root : roots) {
            nodes.add(buildTreeNode(root, "", 0, byParent, visited));
        }
        return nodes;
    }

    private DocumentTreeNode buildTreeNode(
        DocumentSummary document,
        String parentPath,
        int depth,
        Map<UUID, List<DocumentSummary>> byParent,
        Set<UUID> visited
    ) {
        visited.add(document.id());
        String path = parentPath.isBlank() ? document.title() : parentPath + " / " + document.title();
        List<DocumentTreeNode> children = new ArrayList<>();
        for (DocumentSummary child : byParent.getOrDefault(document.id(), List.of())) {
            if (!visited.contains(child.id())) {
                children.add(buildTreeNode(child, path, depth + 1, byParent, visited));
            }
        }
        return new DocumentTreeNode(document, path, depth, children.size(), !children.isEmpty(), children);
    }

    private Comparator<DocumentSummary> documentOrder() {
        return Comparator
            .comparingInt(DocumentSummary::sortOrder)
            .thenComparing(document -> document.title().toLowerCase())
            .thenComparing(DocumentSummary::updatedAt, Comparator.reverseOrder());
    }

    private int nextSortOrder(CurrentUser currentUser, UUID parentId) {
        return documentRepository.listDocuments(currentUser.workspaceId(), currentUser.id(), true).stream()
            .filter(document -> parentId == null ? document.parentId() == null : parentId.equals(document.parentId()))
            .mapToInt(DocumentSummary::sortOrder)
            .max()
            .orElse(0) + 10;
    }

    private DocumentSummary withPermission(DocumentSummary document, String permissionLevel) {
        return new DocumentSummary(
            document.id(),
            document.parentId(),
            document.title(),
            document.docType(),
            document.currentVersionNo(),
            permissionLevel,
            document.createdBy(),
            document.createdByName(),
            document.createdAt(),
            document.updatedBy(),
            document.updatedByName(),
            document.updatedAt(),
            document.sortOrder(),
            document.archived()
        );
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document title is required");
        }
        return title.trim();
    }

    private String normalizeDocType(String docType) {
        String type = docType == null || docType.isBlank() ? "markdown" : docType.toLowerCase();
        if (!List.of("markdown", "folder", "space").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document type");
        }
        return type;
    }

    private String normalizePermission(String permissionLevel) {
        String level = permissionLevel == null ? "" : permissionLevel.toLowerCase();
        if (!List.of("view", "edit", "manage").contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document permission");
        }
        return level;
    }

    private String normalizeTargetType(String targetType) {
        String type = targetType == null ? "" : targetType.toLowerCase();
        if (!List.of("issue", "base", "base_table", "base_record", "file").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid relation target type");
        }
        return type;
    }

    private List<DocumentBlockDraft> blocksFromContent(String content) {
        String[] lines = splitLines(content);
        List<DocumentBlockDraft> blocks = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            blocks.add(new DocumentBlockDraft(inferBlockType(trimmed), normalizeBlockContent(trimmed), blocks.size()));
        }
        if (blocks.isEmpty()) {
            blocks.add(new DocumentBlockDraft("paragraph", "", 0));
        }
        return blocks;
    }

    private List<DocumentBlockDraft> normalizeBlocks(List<DocumentBlockDraft> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of(new DocumentBlockDraft("paragraph", "", 0));
        }
        List<DocumentBlockDraft> normalized = new ArrayList<>();
        for (DocumentBlockDraft block : blocks) {
            String blockType = normalizeBlockType(block.blockType());
            String content = normalizeDraftBlockContent(blockType, block.content());
            normalized.add(new DocumentBlockDraft(blockType, content, normalized.size()));
        }
        return normalized;
    }

    private String normalizeDraftBlockContent(String blockType, String content) {
        String value = content == null ? "" : content.trim();
        if ("table".equals(blockType)) {
            if (value.isBlank()) {
                return "{\"columns\":[\"列 1\",\"列 2\"],\"rows\":[[\"\",\"\"]]}";
            }
            Map<String, Object> table = parseJsonObject(value)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid table block content"));
            if (!table.containsKey("columns") || !table.containsKey("rows")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table block requires columns and rows");
            }
            return writeJson(table);
        }
        if (EMBED_BLOCK_TYPES.contains(blockType)) {
            Map<String, Object> embed = parseJsonObject(value)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid embed block content"));
            String objectType = normalizeEmbedObjectType(blockType, asString(embed.getOrDefault("objectType", embed.get("targetType"))));
            UUID objectId = parseUuid(asString(embed.getOrDefault("objectId", embed.get("targetId"))));
            if (objectType.isBlank() || objectId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Embed block requires objectType and objectId");
            }
            Map<String, Object> normalized = new HashMap<>(embed);
            normalized.put("objectType", objectType);
            normalized.put("objectId", objectId.toString());
            normalized.remove("targetType");
            normalized.remove("targetId");
            return writeJson(normalized);
        }
        return blockType.equals("code") ? content == null ? "" : content : value;
    }

    private String contentFromBlocks(List<DocumentBlockDraft> blocks) {
        return blocks.stream()
            .map(block -> switch (block.blockType()) {
                case "heading" -> "# " + block.content();
                case "list" -> "- " + block.content();
                case "task" -> "- [ ] " + block.content();
                case "quote" -> "> " + block.content();
                case "code" -> "```\n" + block.content() + "\n```";
                case "table" -> "[table] " + block.content();
                case "embed", "base_view", "issue_embed", "message_embed", "file_embed", "link" -> "[" + block.blockType() + "] " + block.content();
                default -> block.content();
            })
            .toList()
            .stream()
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private String inferBlockType(String line) {
        if (line.startsWith("#")) {
            return "heading";
        }
        String lowerLine = line.toLowerCase();
        if (line.startsWith("- [ ]") || lowerLine.startsWith("- [x]")) {
            return "task";
        }
        if (line.startsWith("- ")) {
            return "list";
        }
        if (line.startsWith(">")) {
            return "quote";
        }
        if (line.startsWith("```")) {
            return "code";
        }
        Matcher enhancedBlock = Pattern.compile("^\\[(table|embed|base_view|issue_embed|message_embed|file_embed|link)]\\s+.*$").matcher(line);
        if (enhancedBlock.matches()) {
            return enhancedBlock.group(1);
        }
        return "paragraph";
    }

    private String normalizeBlockContent(String line) {
        return line
            .replaceFirst("^\\[(table|embed|base_view|issue_embed|message_embed|file_embed|link)]\\s+", "")
            .replaceFirst("^#{1,6}\\s*", "")
            .replaceFirst("^- \\[[ xX]\\]\\s*", "")
            .replaceFirst("^-\\s+", "")
            .replaceFirst("^>\\s*", "");
    }

    private String normalizeBlockType(String blockType) {
        String type = blockType == null ? "" : blockType.toLowerCase();
        if (!BLOCK_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document block type");
        }
        return type;
    }

    private java.util.Optional<Map<String, Object>> parseJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(content, STRING_OBJECT_MAP);
            return java.util.Optional.of(parsed);
        } catch (JsonProcessingException exception) {
            return java.util.Optional.empty();
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid block content");
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
