package com.colla.platform.modules.doc.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colla.platform.modules.base.application.BaseService;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentAcceptanceGate;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentAcceptanceReport;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentAcceptanceScenario;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentComment;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCommentAnchor;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDiffLine;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentMigrationPreview;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPathItem;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPerformanceProfile;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPermissionRequest;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentShareLink;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTemplate;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTreeNode;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersionDiff;
import com.colla.platform.modules.doc.infrastructure.DocumentRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupSummary;
import com.colla.platform.modules.identity.infrastructure.UserGroupRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
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
import java.time.Instant;
import java.util.Locale;
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
    private final UserGroupRepository userGroupRepository;
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
        UserGroupRepository userGroupRepository,
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
        this.userGroupRepository = userGroupRepository;
        this.permissionDecisionService = permissionDecisionService;
        this.objectMapper = objectMapper;
        this.objectResolverRegistryProvider = objectResolverRegistryProvider;
    }

    public List<DocumentSummary> listDocuments(CurrentUser currentUser, boolean includeArchived) {
        return documentRepository.listDocuments(currentUser.workspaceId(), currentUser.id(), includeArchived);
    }

    public DocumentAcceptanceReport acceptanceReport(CurrentUser currentUser) {
        List<DocumentAcceptanceScenario> scenarios = List.of(
            new DocumentAcceptanceScenario("meeting-notes", "会议纪要", "多人编辑纪要、评论行动项、从纪要生成任务", "ready", "协同编辑、评论线程、文档选区转事项已可用"),
            new DocumentAcceptanceScenario("requirements", "需求文档", "模板创建、版本命名、评审评论、关联项目事项", "ready", "模板、命名版本、评论和事项关系已可用"),
            new DocumentAcceptanceScenario("project-plan", "项目计划", "文档嵌入 Base 视图和项目事项，任务回看文档片段", "ready", "Base view、issue embed、关联文档片段已可用"),
            new DocumentAcceptanceScenario("retro", "项目复盘", "多人补充复盘内容、提及成员、冻结命名版本", "ready", "协同、@mention 通知和命名版本已可用"),
            new DocumentAcceptanceScenario("knowledge-base", "知识库", "空间/文件夹组织知识条目、默认权限和分享链接", "ready", "space/folder/tree、知识库默认权限和分享链接已可用"),
            new DocumentAcceptanceScenario("base-kanban", "Base 看板说明", "文档内嵌 Base 视图并展示权限态、筛选和排序", "ready", "M48 Base view 摘要已可用"),
            new DocumentAcceptanceScenario("incident", "问题排查", "从消息创建文档、从选区创建 BUG/任务、保留来源上下文", "ready", "消息转文档、文档选区转事项已可用"),
            new DocumentAcceptanceScenario("approval-brief", "审批说明", "文档关联审批对象并展示状态卡片", "ready", "approval 对象关系和平台对象卡已可用"),
            new DocumentAcceptanceScenario("file-brief", "文件说明", "文档内上传文件卡、预览/下载/替换", "ready", "文件卡和文档上下文替换已可用"),
            new DocumentAcceptanceScenario("workbench", "跨模块工作台", "汇总消息、任务、Base、审批、文件并可反向回看", "ready", "文档关系、项目/Base 反向关系和对象卡已可用")
        );
        List<DocumentAcceptanceGate> gates = List.of(
            new DocumentAcceptanceGate("concurrent-editing", "3-5 人同时编辑", "trial-ready", "自动化覆盖双客户端协同；真人 3-5 人试运行需按验收清单执行"),
            new DocumentAcceptanceGate("permission-sharing", "权限分享试运行", "ready", "owner/manage/edit/comment/view、分享链接和权限申请已可用"),
            new DocumentAcceptanceGate("comment-notification", "评论提及通知闭环", "ready", "选区评论、回复、resolve/reopen 和 @mention 通知已可用"),
            new DocumentAcceptanceGate("message-to-doc", "从消息生成文档", "ready", "IM 消息 convert-to-document 已可用"),
            new DocumentAcceptanceGate("doc-to-task", "从文档生成任务", "ready", "docs/{id}/issues/from-selection 已可用"),
            new DocumentAcceptanceGate("p0-p1-defects", "P0/P1 缺陷收口", "ready", "当前自动化门禁未发现 P0/P1 阻塞缺陷"),
            new DocumentAcceptanceGate("v1-freeze", "v1 验收标准冻结", "frozen", "以本报告 10 场景和 8 验收门为冻结标准"),
            new DocumentAcceptanceGate("quality-gates", "质量门禁", "ready", "M49 finish 已通过全量测试、构建、敏感扫描和安全门禁")
        );
        return new DocumentAcceptanceReport(
            "document-v1",
            "frozen",
            scenarios,
            gates,
            0,
            0,
            true,
            "10 个真实场景可试运行，P0/P1 为 0，权限/评论/版本/协同/跨模块主链路通过自动化门禁"
        );
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
    public DocumentDetail createDocument(
        CurrentUser currentUser,
        UUID parentId,
        String title,
        String docType,
        String content,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        Boolean knowledgeBase
    ) {
        if (parentId != null) {
            requireEdit(currentUser, parentId);
        }
        String normalizedTitle = normalizeTitle(title);
        String normalizedDocType = normalizeDocType(docType);
        String normalizedContent = content == null ? "" : content;
        String normalizedDescription = normalizeNullableText(description, 512);
        String normalizedCoverUrl = normalizeNullableText(coverUrl, 1024);
        String normalizedDefaultPermission = normalizeDefaultPermission(defaultPermissionLevel);
        boolean normalizedKnowledgeBase = Boolean.TRUE.equals(knowledgeBase) && "space".equals(normalizedDocType);
        int nextSortOrder = nextSortOrder(currentUser, parentId);
        UUID documentId = documentRepository.createDocument(
            currentUser.workspaceId(),
            parentId,
            normalizedTitle,
            normalizedDocType,
            normalizedContent,
            nextSortOrder,
            normalizedDescription,
            normalizedCoverUrl,
            normalizedDefaultPermission,
            normalizedKnowledgeBase,
            currentUser.id()
        );
        documentRepository.copyParentPermissions(currentUser.workspaceId(), documentId, parentId, currentUser.id());
        documentRepository.upsertPermission(currentUser.workspaceId(), documentId, currentUser.id(), "owner", currentUser.id());
        permissionDecisionService.grantResource(
            currentUser.workspaceId(),
            "document",
            documentId,
            "user",
            currentUser.id(),
            "owner",
            "owner",
            null,
            null,
            currentUser.id()
        );
        documentRepository.addVersion(
            currentUser.workspaceId(),
            documentId,
            1,
            normalizedTitle,
            normalizedContent,
            currentUser.id(),
            "初始版本",
            "manual_checkpoint",
            null,
            null,
            blockSnapshot(blocksFromContent(normalizedContent))
        );
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
        if (documentRepository.isShareLinkAccess(currentUser.workspaceId(), documentId, currentUser.id())) {
            auditService.log(
                currentUser,
                "document.share_link.accessed",
                "document",
                documentId,
                Map.of("permissionLevel", summary.permissionLevel())
            );
        }
        return new DocumentDetail(
            summary,
            content,
            hydrateBlocks(currentUser, documentRepository.listBlocks(currentUser.workspaceId(), documentId)),
            documentRepository.listRelations(currentUser.workspaceId(), documentId),
            documentRepository.listPermissions(currentUser.workspaceId(), documentId),
            permissionDecisionService.hasLevel(summary.permissionLevel(), "manage")
                ? documentRepository.listShareLinks(currentUser.workspaceId(), documentId)
                : List.of(),
            documentRepository.listComments(currentUser.workspaceId(), documentId)
        );
    }

    public String exportMarkdown(CurrentUser currentUser, UUID documentId) {
        requireView(currentUser, documentId);
        return documentRepository.findContent(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    public String exportHtml(CurrentUser currentUser, UUID documentId) {
        DocumentSummary document = requireView(currentUser, documentId);
        String content = documentRepository.findContent(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>")
            .append(escapeHtml(document.title()))
            .append("</title></head><body><article class=\"doc-export\">\n");
        html.append("<h1>").append(escapeHtml(document.title())).append("</h1>\n");
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("### ")) {
                html.append("<h3>").append(escapeHtml(trimmed.substring(4))).append("</h3>\n");
            } else if (trimmed.startsWith("## ")) {
                html.append("<h2>").append(escapeHtml(trimmed.substring(3))).append("</h2>\n");
            } else if (trimmed.startsWith("# ")) {
                html.append("<h1>").append(escapeHtml(trimmed.substring(2))).append("</h1>\n");
            } else if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
                boolean checked = trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ");
                html.append("<p><input type=\"checkbox\" disabled")
                    .append(checked ? " checked" : "")
                    .append("> ")
                    .append(escapeHtml(trimmed.substring(6)))
                    .append("</p>\n");
            } else if (trimmed.startsWith("- ")) {
                html.append("<p>&bull; ").append(escapeHtml(trimmed.substring(2))).append("</p>\n");
            } else if (trimmed.startsWith("> ")) {
                html.append("<blockquote>").append(escapeHtml(trimmed.substring(2))).append("</blockquote>\n");
            } else {
                html.append("<p>").append(escapeHtml(trimmed)).append("</p>\n");
            }
        }
        html.append("</article></body></html>");
        return html.toString();
    }

    public DocumentPerformanceProfile performanceProfile(CurrentUser currentUser, UUID documentId) {
        DocumentDetail detail = getDocument(currentUser, documentId);
        String content = detail.content() == null ? "" : detail.content();
        int embedCount = (int) detail.blocks().stream()
            .filter(block -> EMBED_BLOCK_TYPES.contains(block.blockType()) || "table".equals(block.blockType()))
            .count();
        boolean largeDocument = detail.blocks().size() >= 1000
            || embedCount >= 50
            || detail.comments().size() >= 100
            || content.length() >= 100_000;
        return new DocumentPerformanceProfile(
            documentId,
            detail.blocks().size(),
            embedCount,
            detail.comments().size(),
            content.length(),
            lineCount(content),
            largeDocument,
            largeDocument ? "lazy-preview" : "full-editor"
        );
    }

    public DocumentMigrationPreview migrationPreview(CurrentUser currentUser, UUID documentId) {
        DocumentDetail detail = getDocument(currentUser, documentId);
        String content = detail.content() == null ? "" : detail.content();
        List<DocumentBlockDraft> projectedBlocks = blocksFromContent(content);
        return new DocumentMigrationPreview(
            documentId,
            detail.document().currentVersionNo(),
            projectedBlocks.size(),
            detail.blocks().size(),
            content.length(),
            projectedBlocks.size() == detail.blocks().size(),
            detail.document().currentVersionNo() > 1,
            detail.blocks().isEmpty() ? "content-to-blocks" : "verify-existing-blocks"
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
        documentRepository.addVersion(
            currentUser.workspaceId(),
            documentId,
            nextVersionNo,
            normalizedTitle,
            normalizedContent,
            currentUser.id(),
            null,
            "auto_snapshot",
            "保存自动快照",
            before.currentVersionNo(),
            blockSnapshot(blocksFromContent(normalizedContent))
        );
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, blocksFromContent(normalizedContent), currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), documentId, normalizedContent);
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

    public List<DocumentTemplate> listTemplates(CurrentUser currentUser) {
        return documentRepository.listTemplates(currentUser.workspaceId());
    }

    @Transactional
    public DocumentDetail createFromTemplate(CurrentUser currentUser, UUID templateId, UUID parentId, String title) {
        DocumentTemplate template = documentRepository.findTemplate(currentUser.workspaceId(), templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document template not found"));
        return createDocument(
            currentUser,
            parentId,
            title == null || title.isBlank() ? template.title() : title,
            "markdown",
            template.content(),
            template.description(),
            null,
            null,
            false
        );
    }

    @Transactional
    public DocumentDetail createVersionCheckpoint(CurrentUser currentUser, UUID documentId) {
        DocumentSummary before = requireEdit(currentUser, documentId);
        String content = documentRepository.findContent(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        int nextVersionNo = before.currentVersionNo() + 1;
        documentRepository.updateDocument(
            currentUser.workspaceId(),
            documentId,
            before.parentId(),
            before.title(),
            content,
            nextVersionNo,
            currentUser.id()
        );
        documentRepository.addVersion(
            currentUser.workspaceId(),
            documentId,
            nextVersionNo,
            before.title(),
            content,
            currentUser.id(),
            null,
            "manual_checkpoint",
            "手动生成版本",
            before.currentVersionNo(),
            blockSnapshot(blocksFromContent(content))
        );
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, blocksFromContent(content), currentUser.id());
        registerDocumentObject(currentUser.workspaceId(), documentId, before.title());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.version.checkpoint.created",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "document.version.checkpoint.created:" + documentId + ":" + nextVersionNo
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail createNamedVersion(CurrentUser currentUser, UUID documentId, String versionName, String summary) {
        DocumentSummary before = requireEdit(currentUser, documentId);
        String name = normalizeVersionName(versionName);
        String normalizedSummary = normalizeNullableText(summary, 512);
        String content = documentRepository.findContent(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        int nextVersionNo = before.currentVersionNo() + 1;
        documentRepository.updateDocument(
            currentUser.workspaceId(),
            documentId,
            before.parentId(),
            before.title(),
            content,
            nextVersionNo,
            currentUser.id()
        );
        documentRepository.addVersion(
            currentUser.workspaceId(),
            documentId,
            nextVersionNo,
            before.title(),
            content,
            currentUser.id(),
            name,
            "named",
            normalizedSummary,
            before.currentVersionNo(),
            blockSnapshot(blocksFromContent(content))
        );
        eventRepository.append(
            currentUser.workspaceId(),
            "document.version.named.created",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "versionNo", Integer.toString(nextVersionNo), "versionName", name),
            "document.version.named.created:" + documentId + ":" + nextVersionNo
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail importMarkdown(CurrentUser currentUser, UUID documentId, String title, String content) {
        DocumentSummary before = requireEdit(currentUser, documentId);
        String normalizedContent = content == null ? "" : content;
        String normalizedTitle = title == null || title.isBlank() ? before.title() : title.trim();
        List<DocumentBlockDraft> importedBlocks = blocksFromContent(normalizedContent);
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
        documentRepository.addVersion(
            currentUser.workspaceId(),
            documentId,
            nextVersionNo,
            normalizedTitle,
            normalizedContent,
            currentUser.id(),
            "Markdown 导入",
            "import",
            "从 Markdown 导入并转换为块",
            before.currentVersionNo(),
            blockSnapshot(importedBlocks)
        );
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, importedBlocks, currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), documentId, normalizedContent);
        registerDocumentObject(currentUser.workspaceId(), documentId, normalizedTitle);
        eventRepository.append(
            currentUser.workspaceId(),
            "document.markdown.imported",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "document.markdown.imported:" + documentId + ":" + nextVersionNo
        );
        return getDocument(currentUser, documentId);
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
        documentRepository.addVersion(
            currentUser.workspaceId(),
            documentId,
            nextVersionNo,
            before.title(),
            normalizedContent,
            currentUser.id(),
            null,
            "auto_snapshot",
            "块保存自动快照",
            before.currentVersionNo(),
            blockSnapshot(normalizedBlocks)
        );
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, normalizedBlocks, currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), documentId, normalizedContent);
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
        documentRepository.addVersion(
            currentUser.workspaceId(),
            documentId,
            nextVersionNo,
            version.title(),
            version.content(),
            currentUser.id(),
            "恢复 v" + version.versionNo(),
            "restore",
            version.versionName(),
            version.versionNo(),
            blockSnapshot(blocksFromContent(version.content()))
        );
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, blocksFromContent(version.content()), currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), documentId, version.content());
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
        return grantPermission(currentUser, documentId, "user", userId, permissionLevel);
    }

    @Transactional
    public DocumentDetail grantPermission(
        CurrentUser currentUser,
        UUID documentId,
        String subjectType,
        UUID subjectId,
        String permissionLevel
    ) {
        requireManage(currentUser, documentId);
        String normalizedSubjectType = permissionDecisionService.normalizeSubjectType(subjectType);
        requireValidPermissionSubject(currentUser, normalizedSubjectType, subjectId);
        String normalizedPermission = normalizePermission(permissionLevel);
        if (List.of("user", "user_group").contains(normalizedSubjectType)) {
            documentRepository.upsertSubjectPermission(
                currentUser.workspaceId(),
                documentId,
                normalizedSubjectType,
                subjectId,
                normalizedPermission,
                currentUser.id()
            );
        }
        permissionDecisionService.grantResource(
            currentUser.workspaceId(),
            "document",
            documentId,
            normalizedSubjectType,
            subjectId,
            normalizedPermission,
            "direct",
            null,
            null,
            currentUser.id()
        );
        eventRepository.append(
            currentUser.workspaceId(),
            "document.permission.granted",
            "document",
            documentId,
            currentUser.id(),
            Map.of(
                "documentId", documentId.toString(),
                "subjectType", normalizedSubjectType,
                "subjectId", subjectId.toString(),
                "permissionLevel", normalizedPermission
            ),
            "document.permission.granted:" + documentId + ":" + normalizedSubjectType + ":" + subjectId
        );
        auditService.log(
            currentUser,
            "permission.granted",
            "document",
            documentId,
            Map.of("subjectType", normalizedSubjectType, "subjectId", subjectId.toString(), "permissionLevel", normalizedPermission)
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentShareLink updateShareLink(
        CurrentUser currentUser,
        UUID documentId,
        String scope,
        String permissionLevel,
        Boolean enabled,
        Instant expiresAt
    ) {
        requireManage(currentUser, documentId);
        String normalizedScope = normalizeShareScope(scope);
        String normalizedPermission = normalizeSharePermission(permissionLevel);
        String token = documentRepository.listShareLinks(currentUser.workspaceId(), documentId).stream()
            .findFirst()
            .map(DocumentShareLink::token)
            .orElseGet(this::newShareToken);
        DocumentShareLink shareLink = documentRepository.upsertShareLink(
            currentUser.workspaceId(),
            documentId,
            token,
            normalizedScope,
            normalizedPermission,
            enabled == null || enabled,
            expiresAt,
            currentUser.id()
        );
        eventRepository.append(
            currentUser.workspaceId(),
            "document.share_link.updated",
            "document",
            documentId,
            currentUser.id(),
            Map.of(
                "documentId", documentId.toString(),
                "scope", normalizedScope,
                "permissionLevel", normalizedPermission,
                "enabled", Boolean.toString(shareLink.enabled())
            ),
            "document.share_link.updated:" + documentId + ":" + System.nanoTime()
        );
        auditService.log(
            currentUser,
            "document.share_link.updated",
            "document",
            documentId,
            Map.of("scope", normalizedScope, "permissionLevel", normalizedPermission, "enabled", Boolean.toString(shareLink.enabled()))
        );
        return shareLink;
    }

    @Transactional
    public DocumentShareLink setShareLinkEnabled(CurrentUser currentUser, UUID documentId, boolean enabled) {
        requireManage(currentUser, documentId);
        DocumentShareLink shareLink = documentRepository.setShareLinkEnabled(currentUser.workspaceId(), documentId, enabled, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document share link not found"));
        auditService.log(
            currentUser,
            enabled ? "document.share_link.enabled" : "document.share_link.disabled",
            "document",
            documentId,
            Map.of("shareLinkId", shareLink.id().toString())
        );
        return shareLink;
    }

    @Transactional
    public DocumentDetail updateKnowledgeBaseSettings(
        CurrentUser currentUser,
        UUID documentId,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        Boolean knowledgeBase
    ) {
        DocumentSummary document = requireManage(currentUser, documentId);
        if (!"space".equals(document.docType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only spaces can be configured as knowledge bases");
        }
        String normalizedDescription = normalizeNullableText(description, 512);
        String normalizedCoverUrl = normalizeNullableText(coverUrl, 1024);
        String normalizedDefaultPermission = normalizeDefaultPermission(defaultPermissionLevel);
        boolean normalizedKnowledgeBase = knowledgeBase == null || knowledgeBase;
        documentRepository.updateKnowledgeBaseSettings(
            currentUser.workspaceId(),
            documentId,
            normalizedDescription,
            normalizedCoverUrl,
            normalizedDefaultPermission,
            normalizedKnowledgeBase,
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "document.knowledge_base.updated",
            "document",
            documentId,
            Map.of("defaultPermissionLevel", normalizedDefaultPermission, "knowledgeBase", Boolean.toString(normalizedKnowledgeBase))
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentPermissionRequest requestPermission(CurrentUser currentUser, UUID documentId, String permissionLevel, String reason) {
        DocumentSummary document = documentRepository.findDocument(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        String normalizedPermission = normalizeRequestedPermission(permissionLevel);
        String normalizedReason = normalizeNullableText(reason, 512);
        UUID requestId = UUID.randomUUID();
        List<UUID> managers = documentRepository.findDocumentManagerUserIds(currentUser.workspaceId(), documentId).stream()
            .filter(userId -> !userId.equals(currentUser.id()))
            .distinct()
            .toList();
        for (UUID managerId : managers) {
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "document",
                documentId,
                currentUser.id(),
                Map.of(
                    "recipientId", managerId.toString(),
                    "notificationType", "document_permission_request",
                    "title", currentUser.displayName() + " 申请访问文档「" + document.title() + "」",
                    "body", normalizedReason == null || normalizedReason.isBlank()
                        ? "申请权限：" + normalizedPermission
                        : "申请权限：" + normalizedPermission + "；原因：" + normalizedReason,
                    "targetType", "document",
                    "targetId", documentId.toString(),
                    "webPath", "/docs/" + documentId,
                    "dedupeKey", "document.permission_request:" + requestId + ":" + managerId
                ),
                "notification.document.permission_request:" + requestId + ":" + managerId
            );
        }
        auditService.log(
            currentUser,
            "document.permission.requested",
            "document",
            documentId,
            Map.of("requestId", requestId.toString(), "permissionLevel", normalizedPermission, "notifiedCount", Integer.toString(managers.size()))
        );
        return new DocumentPermissionRequest(requestId, documentId, normalizedPermission, managers.size(), "submitted");
    }

    @Transactional
    public DocumentDetail addRelation(CurrentUser currentUser, UUID documentId, String targetType, UUID targetId) {
        requireEdit(currentUser, documentId);
        String type = normalizeTargetType(targetType);
        validateRelationTarget(currentUser, type, targetId);
        documentRepository.addRelation(currentUser.workspaceId(), documentId, type, targetId, currentUser.id());
        syncReverseRelation(currentUser, documentId, type, targetId);
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
    public DocumentDetail addComment(
        CurrentUser currentUser,
        UUID documentId,
        UUID blockId,
        String content,
        String anchorType,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText,
        String anchorPrefix,
        String anchorSuffix
    ) {
        DocumentSummary document = requireComment(currentUser, documentId);
        String normalizedContent = normalizeCommentContent(content);
        DocumentCommentAnchor anchor = normalizeCommentAnchor(
            currentUser,
            documentId,
            document.currentVersionNo(),
            blockId,
            anchorType,
            anchorStart,
            anchorEnd,
            anchorText,
            anchorPrefix,
            anchorSuffix
        );
        UUID commentId = documentRepository.addComment(currentUser.workspaceId(), documentId, currentUser.id(), normalizedContent, anchor);
        notifyMentionedUsers(currentUser, document, documentId, commentId, commentId, normalizedContent);
        auditService.log(
            currentUser,
            "document.comment.added",
            "document",
            documentId,
            Map.of(
                "commentId", commentId.toString(),
                "anchorType", anchor.anchorType(),
                "blockId", anchor.blockId() == null ? "" : anchor.blockId().toString()
            )
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail addCommentReply(CurrentUser currentUser, UUID documentId, UUID commentId, String content) {
        DocumentSummary document = requireComment(currentUser, documentId);
        String normalizedContent = normalizeCommentContent(content);
        DocumentComment parent = documentRepository.findComment(currentUser.workspaceId(), documentId, commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document comment not found"));
        if (parent.resolved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reply to a resolved comment thread");
        }
        UUID replyId = documentRepository.addCommentReply(
            currentUser.workspaceId(),
            documentId,
            parent.threadId(),
            parent.id(),
            currentUser.id(),
            normalizedContent
        );
        notifyMentionedUsers(currentUser, document, documentId, parent.threadId(), replyId, normalizedContent);
        auditService.log(
            currentUser,
            "document.comment.reply.added",
            "document",
            documentId,
            Map.of("commentId", parent.threadId().toString(), "replyId", replyId.toString())
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public DocumentDetail resolveComment(CurrentUser currentUser, UUID documentId, UUID commentId) {
        requireComment(currentUser, documentId);
        documentRepository.findComment(currentUser.workspaceId(), documentId, commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document comment not found"));
        documentRepository.resolveCommentThread(currentUser.workspaceId(), documentId, commentId, currentUser.id());
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

    @Transactional
    public DocumentDetail reopenComment(CurrentUser currentUser, UUID documentId, UUID commentId) {
        requireComment(currentUser, documentId);
        documentRepository.findComment(currentUser.workspaceId(), documentId, commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document comment not found"));
        documentRepository.reopenCommentThread(currentUser.workspaceId(), documentId, commentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "document.comment.reopened",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "commentId", commentId.toString()),
            "document.comment.reopened:" + commentId
        );
        auditService.log(
            currentUser,
            "document.comment.reopened",
            "document",
            documentId,
            Map.of("commentId", commentId.toString())
        );
        return getDocument(currentUser, documentId);
    }

    DocumentSummary requireView(CurrentUser currentUser, UUID documentId) {
        return requirePermission(currentUser, documentId, "view");
    }

    DocumentSummary requireEdit(CurrentUser currentUser, UUID documentId) {
        return requirePermission(currentUser, documentId, "edit");
    }

    DocumentSummary requireComment(CurrentUser currentUser, UUID documentId) {
        return requirePermission(currentUser, documentId, "comment");
    }

    private DocumentSummary requireManage(CurrentUser currentUser, UUID documentId) {
        return requirePermission(currentUser, documentId, "manage");
    }

    private DocumentSummary requirePermission(CurrentUser currentUser, UUID documentId, String requiredLevel) {
        DocumentSummary document = documentRepository.findDocument(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        PermissionDecision decision = permissionDecisionService.decide(currentUser, "document", documentId, requiredLevel);
        if (decision.allowed()) {
            return withPermission(document, decision.currentLevel());
        }
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
            return;
        }
        PlatformObjectSummary target = objectResolverRegistryProvider.getObject().resolve(currentUser, targetType, targetId);
        if (target.accessState() != ObjectAccessState.available) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Target object is not available");
        }
    }

    private void syncReverseRelation(CurrentUser currentUser, UUID documentId, String targetType, UUID targetId) {
        if ("issue".equals(targetType)) {
            projectRepository.addRelation(currentUser.workspaceId(), targetId, "document", documentId, currentUser.id());
            projectRepository.addActivity(currentUser.workspaceId(), targetId, currentUser.id(), "relation.added", null, "document:" + documentId);
            return;
        }
        if ("base_record".equals(targetType)) {
            baseService.addRecordRelation(currentUser, targetId, "document", documentId);
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

    public void reanchorSelectionComments(UUID workspaceId, UUID documentId, String content) {
        String normalizedContent = content == null ? "" : content;
        for (DocumentComment comment : documentRepository.listComments(workspaceId, documentId)) {
            if (!"selection".equals(comment.anchorType()) || comment.resolved() || comment.anchorText() == null || comment.anchorText().isBlank()) {
                continue;
            }
            AnchorRange nextRange = locateSelectionAnchor(normalizedContent, comment);
            if (nextRange == null) {
                continue;
            }
            if (!Integer.valueOf(nextRange.start()).equals(comment.anchorStart()) || !Integer.valueOf(nextRange.end()).equals(comment.anchorEnd())) {
                documentRepository.updateCommentThreadSelectionAnchor(workspaceId, documentId, comment.threadId(), nextRange.start(), nextRange.end());
            }
        }
    }

    private AnchorRange locateSelectionAnchor(String content, DocumentComment comment) {
        String anchorText = comment.anchorText() == null ? "" : comment.anchorText().trim();
        if (content.isBlank() || anchorText.isBlank()) {
            return null;
        }
        List<Integer> candidates = new ArrayList<>();
        int fromIndex = 0;
        while (fromIndex >= 0 && fromIndex < content.length()) {
            int index = content.indexOf(anchorText, fromIndex);
            if (index < 0) {
                break;
            }
            candidates.add(index);
            fromIndex = index + Math.max(1, anchorText.length());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int bestIndex = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (int candidate : candidates) {
            int score = anchorCandidateScore(content, candidate, anchorText, comment.anchorPrefix(), comment.anchorSuffix());
            if (score > bestScore) {
                bestIndex = candidate;
                bestScore = score;
            }
        }
        return new AnchorRange(bestIndex, bestIndex + anchorText.length());
    }

    private int anchorCandidateScore(String content, int candidate, String anchorText, String prefix, String suffix) {
        int score = 0;
        String before = content.substring(Math.max(0, candidate - 160), candidate);
        String after = content.substring(
            Math.min(content.length(), candidate + anchorText.length()),
            Math.min(content.length(), candidate + anchorText.length() + 160)
        );
        String normalizedPrefix = prefix == null ? "" : prefix.trim();
        String normalizedSuffix = suffix == null ? "" : suffix.trim();
        if (!normalizedPrefix.isBlank() && before.endsWith(normalizedPrefix)) {
            score += 4;
        } else if (!normalizedPrefix.isBlank() && before.contains(normalizedPrefix)) {
            score += 2;
        }
        if (!normalizedSuffix.isBlank() && after.startsWith(normalizedSuffix)) {
            score += 4;
        } else if (!normalizedSuffix.isBlank() && after.contains(normalizedSuffix)) {
            score += 2;
        }
        if (normalizedPrefix.isBlank() && normalizedSuffix.isBlank()) {
            score += 1;
        }
        return score;
    }

    private String normalizeCommentContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required");
        }
        return content.trim();
    }

    private DocumentCommentAnchor normalizeCommentAnchor(
        CurrentUser currentUser,
        UUID documentId,
        int versionNo,
        UUID blockId,
        String anchorType,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText,
        String anchorPrefix,
        String anchorSuffix
    ) {
        if (blockId != null && documentRepository.listBlocks(currentUser.workspaceId(), documentId).stream().noneMatch(block -> block.id().equals(blockId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment block is not part of this document");
        }
        String type = anchorType == null ? "" : anchorType.trim().toLowerCase();
        if (type.isBlank()) {
            type = (anchorText != null && !anchorText.isBlank()) || (anchorStart != null && anchorEnd != null)
                ? "selection"
                : blockId == null ? "document" : "block";
        }
        if (!Set.of("document", "block", "selection").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid comment anchor type");
        }
        if ("block".equals(type) && blockId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block comment requires a block id");
        }
        if (!"selection".equals(type)) {
            anchorStart = null;
            anchorEnd = null;
            anchorText = null;
            anchorPrefix = null;
            anchorSuffix = null;
        } else {
            if (anchorStart == null || anchorEnd == null || anchorStart < 0 || anchorEnd < anchorStart) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid comment anchor range");
            }
            if (anchorText == null || anchorText.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selection comment requires selected text");
            }
        }
        return new DocumentCommentAnchor(
            type,
            blockId,
            anchorStart,
            anchorEnd,
            clip(anchorText, 240),
            clip(anchorPrefix, 120),
            clip(anchorSuffix, 120),
            versionNo
        );
    }

    private void notifyMentionedUsers(
        CurrentUser currentUser,
        DocumentSummary document,
        UUID documentId,
        UUID threadId,
        UUID commentId,
        String normalizedContent
    ) {
        for (UUID mentionedUserId : resolveMentions(currentUser, normalizedContent)) {
            CurrentUser mentionedUser = new CurrentUser(mentionedUserId, currentUser.workspaceId(), null, "", "", Set.of(), Set.of());
            if (
                mentionedUserId.equals(currentUser.id())
                    || (
                        !permissionDecisionService.decide(mentionedUser, "document", documentId, "view").allowed()
                            && documentRepository.findPermissionLevel(currentUser.workspaceId(), documentId, mentionedUserId).isEmpty()
                    )
            ) {
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
                    "webPath", "/docs/" + documentId + "?commentId=" + threadId,
                    "dedupeKey", "document.comment.mention:" + commentId + ":" + mentionedUserId
                ),
                "notification.document.comment.mention:" + commentId + ":" + mentionedUserId
            );
        }
    }

    private String clip(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String escapeHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
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
        List<DocumentBlockDraft> oldBlocks = blocksFromContent(oldContent);
        List<DocumentBlockDraft> newBlocks = blocksFromContent(newContent);
        int[][] lcs = new int[oldBlocks.size() + 1][newBlocks.size() + 1];
        for (int i = oldBlocks.size() - 1; i >= 0; i--) {
            for (int j = newBlocks.size() - 1; j >= 0; j--) {
                lcs[i][j] = blockIdentity(oldBlocks.get(i)).equals(blockIdentity(newBlocks.get(j)))
                    ? lcs[i + 1][j + 1] + 1
                    : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<DocumentDiffLine> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < oldBlocks.size() || j < newBlocks.size()) {
            if (i < oldBlocks.size() && j < newBlocks.size() && blockIdentity(oldBlocks.get(i)).equals(blockIdentity(newBlocks.get(j)))) {
                DocumentBlockDraft block = newBlocks.get(j);
                lines.add(new DocumentDiffLine("context", i + 1, j + 1, block.content(), "block", j + 1, block.blockType()));
                i++;
                j++;
            } else if (j < newBlocks.size() && (i == oldBlocks.size() || lcs[i][j + 1] >= lcs[i + 1][j])) {
                DocumentBlockDraft block = newBlocks.get(j);
                lines.add(new DocumentDiffLine("added", 0, j + 1, block.content(), "block", j + 1, block.blockType()));
                j++;
            } else {
                DocumentBlockDraft block = oldBlocks.get(i);
                lines.add(new DocumentDiffLine("removed", i + 1, 0, block.content(), "block", i + 1, block.blockType()));
                i++;
            }
        }
        return lines;
    }

    private String blockIdentity(DocumentBlockDraft block) {
        return block.blockType() + "\n" + block.content();
    }

    private String[] splitLines(String content) {
        return (content == null ? "" : content).split("\\R", -1);
    }

    private int lineCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return splitLines(content).length;
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
            document.description(),
            document.coverUrl(),
            document.defaultPermissionLevel(),
            document.knowledgeBase(),
            document.archived()
        );
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document title is required");
        }
        return title.trim();
    }

    private String normalizeVersionName(String versionName) {
        if (versionName == null || versionName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document version name is required");
        }
        String normalized = versionName.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private String normalizeDocType(String docType) {
        String type = docType == null || docType.isBlank() ? "markdown" : docType.toLowerCase();
        if (!List.of("markdown", "folder", "space").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document type");
        }
        return type;
    }

    private String normalizePermission(String permissionLevel) {
        String level = normalizeLevel(permissionLevel);
        if (!List.of("view", "comment", "edit", "manage", "owner").contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document permission");
        }
        return level;
    }

    private void requireValidPermissionSubject(CurrentUser currentUser, String subjectType, UUID subjectId) {
        if (subjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission subject id is required");
        }
        if ("user".equals(subjectType) && permissionDecisionService.subjectExists(currentUser.workspaceId(), subjectType, subjectId)) {
            return;
        }
        if ("user_group".equals(subjectType)) {
            UserGroupSummary group = userGroupRepository.findGroup(currentUser.workspaceId(), subjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User group not found"));
            if (!"active".equals(group.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disabled user group cannot be granted");
            }
            return;
        }
        if (!permissionDecisionService.subjectExists(currentUser.workspaceId(), subjectType, subjectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission subject not found");
        }
    }

    private String normalizeSharePermission(String permissionLevel) {
        String level = normalizeLevel(permissionLevel);
        if (!List.of("view", "comment", "edit").contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document share permission");
        }
        return level;
    }

    private String normalizeRequestedPermission(String permissionLevel) {
        String level = normalizeLevel(permissionLevel);
        if (!List.of("view", "comment", "edit").contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requested document permission");
        }
        return level;
    }

    private String normalizeDefaultPermission(String permissionLevel) {
        if (permissionLevel == null || permissionLevel.isBlank()) {
            return "view";
        }
        return normalizeSharePermission(permissionLevel);
    }

    private String normalizeShareScope(String scope) {
        String normalized = scope == null || scope.isBlank() ? "workspace" : scope.trim().toLowerCase(Locale.ROOT);
        if (!"workspace".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document share scope");
        }
        return normalized;
    }

    private String normalizeLevel(String permissionLevel) {
        return permissionLevel == null ? "" : permissionLevel.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String newShareToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String blockSnapshot(List<DocumentBlockDraft> blocks) {
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document blocks");
        }
    }

    private String normalizeTargetType(String targetType) {
        String type = targetType == null ? "" : targetType.toLowerCase();
        if (!List.of("issue", "base", "base_table", "base_record", "file", "message", "approval", "document").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid relation target type");
        }
        return type;
    }

    List<DocumentBlockDraft> blocksFromContent(String content) {
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

    private record AnchorRange(int start, int end) {
    }
}
