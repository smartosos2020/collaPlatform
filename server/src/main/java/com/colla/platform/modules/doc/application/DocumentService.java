package com.colla.platform.modules.doc.application;

import com.colla.platform.modules.base.application.BaseService;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDiffLine;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersionDiff;
import com.colla.platform.modules.doc.infrastructure.DocumentRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.modules.permission.application.PermissionDecisionService;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_.-]{2,64})");

    private final DocumentRepository documentRepository;
    private final PlatformObjectRepository objectRepository;
    private final DomainEventRepository eventRepository;
    private final ProjectRepository projectRepository;
    private final FileRepository fileRepository;
    private final BaseService baseService;
    private final AuditService auditService;
    private final PermissionDecisionService permissionDecisionService;

    public DocumentService(
        DocumentRepository documentRepository,
        PlatformObjectRepository objectRepository,
        DomainEventRepository eventRepository,
        ProjectRepository projectRepository,
        FileRepository fileRepository,
        BaseService baseService,
        AuditService auditService,
        PermissionDecisionService permissionDecisionService
    ) {
        this.documentRepository = documentRepository;
        this.objectRepository = objectRepository;
        this.eventRepository = eventRepository;
        this.projectRepository = projectRepository;
        this.fileRepository = fileRepository;
        this.baseService = baseService;
        this.auditService = auditService;
        this.permissionDecisionService = permissionDecisionService;
    }

    public List<DocumentSummary> listDocuments(CurrentUser currentUser) {
        return documentRepository.listDocuments(currentUser.workspaceId(), currentUser.id());
    }

    @Transactional
    public DocumentDetail createDocument(CurrentUser currentUser, UUID parentId, String title, String content) {
        if (parentId != null) {
            requireEdit(currentUser, parentId);
        }
        String normalizedTitle = normalizeTitle(title);
        String normalizedContent = content == null ? "" : content;
        UUID documentId = documentRepository.createDocument(currentUser.workspaceId(), parentId, normalizedTitle, normalizedContent, currentUser.id());
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
            documentRepository.listBlocks(currentUser.workspaceId(), documentId),
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
    public DocumentDetail moveDocument(CurrentUser currentUser, UUID documentId, UUID parentId) {
        DocumentSummary document = requireManage(currentUser, documentId);
        if (parentId != null) {
            requireEdit(currentUser, parentId);
            if (documentId.equals(parentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document cannot be moved under itself");
            }
        }
        documentRepository.moveDocument(currentUser.workspaceId(), document.id(), parentId, currentUser.id());
        return getDocument(currentUser, documentId);
    }

    public List<DocumentVersion> listVersions(CurrentUser currentUser, UUID documentId) {
        requireView(currentUser, documentId);
        return documentRepository.listVersions(currentUser.workspaceId(), documentId);
    }

    public List<DocumentBlock> listBlocks(CurrentUser currentUser, UUID documentId) {
        requireView(currentUser, documentId);
        return documentRepository.listBlocks(currentUser.workspaceId(), documentId);
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
            document.updatedAt()
        );
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document title is required");
        }
        return title.trim();
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
            String content = block.content() == null ? "" : block.content();
            normalized.add(new DocumentBlockDraft(blockType, content, normalized.size()));
        }
        return normalized;
    }

    private String contentFromBlocks(List<DocumentBlockDraft> blocks) {
        return blocks.stream()
            .map(block -> switch (block.blockType()) {
                case "heading" -> "# " + block.content();
                case "list" -> "- " + block.content();
                case "task" -> "- [ ] " + block.content();
                case "quote" -> "> " + block.content();
                case "code" -> "```\n" + block.content() + "\n```";
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
        return "paragraph";
    }

    private String normalizeBlockContent(String line) {
        return line
            .replaceFirst("^#{1,6}\\s*", "")
            .replaceFirst("^- \\[[ xX]\\]\\s*", "")
            .replaceFirst("^-\\s+", "")
            .replaceFirst("^>\\s*", "");
    }

    private String normalizeBlockType(String blockType) {
        String type = blockType == null ? "" : blockType.toLowerCase();
        if (!List.of("paragraph", "heading", "list", "task", "quote", "code").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document block type");
        }
        return type;
    }
}
