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
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseMarkdownImportItem;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseMarkdownImportResult;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeContext;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeReviewReminderResult;
import com.colla.platform.modules.doc.infrastructure.DocumentRepository;
import com.colla.platform.modules.doc.infrastructure.KnowledgeBaseSpaceRepository;
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
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    /*
     * Historical naming boundary: Document* services operate the knowledge-content
     * editor substrate stored in the documents table. Product-level space and content
     * lifecycle semantics must be exposed through KnowledgeBaseSpaceService.
     */
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_.-]{2,64})");
    private static final Set<String> BLOCK_TYPES = Set.of(
        "paragraph",
        "heading",
        "list",
        "bullet_list",
        "bulleted_list",
        "ordered_list",
        "task",
        "todo",
        "task_item",
        "quote",
        "code",
        "code_block",
        "table",
        "image",
        "file",
        "embed",
        "embed_object",
        "base_view",
        "issue_embed",
        "message_embed",
        "file_embed",
        "link",
        "link_card",
        "legacy_html",
        "divider",
        "callout",
        "toc"
    );
    private static final Set<String> EMBED_BLOCK_TYPES = Set.of("embed", "embed_object", "base_view", "issue_embed", "message_embed", "file_embed", "link", "link_card");
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseSpaceRepository knowledgeBaseSpaceRepository;
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
        KnowledgeBaseSpaceRepository knowledgeBaseSpaceRepository,
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
        this.knowledgeBaseSpaceRepository = knowledgeBaseSpaceRepository;
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
            new DocumentAcceptanceScenario("meeting-notes", "会议纪要", "多人编辑纪要、评论行动项、从纪要生成任务", "ready", "协同编辑、评论线程、内容选区转事项已可用"),
            new DocumentAcceptanceScenario("requirements", "需求说明", "模板创建、版本命名、评审评论、关联项目事项", "ready", "模板、命名版本、评论和事项关系已可用"),
            new DocumentAcceptanceScenario("project-plan", "项目计划", "内容页嵌入 Base 视图和项目事项，任务回看知识片段", "ready", "Base view、issue embed、关联知识片段已可用"),
            new DocumentAcceptanceScenario("retro", "项目复盘", "多人补充复盘内容、提及成员、冻结命名版本", "ready", "协同、@mention 通知和命名版本已可用"),
            new DocumentAcceptanceScenario("knowledge-base", "知识库", "空间/文件夹组织知识条目、默认权限和分享链接", "ready", "space/folder/tree、知识库默认权限和分享链接已可用"),
            new DocumentAcceptanceScenario("base-kanban", "Base 看板说明", "内容页内嵌 Base 视图并展示权限态、筛选和排序", "ready", "M48 Base view 摘要已可用"),
            new DocumentAcceptanceScenario("incident", "问题排查", "从消息沉淀知识内容、从选区创建 BUG/任务、保留来源上下文", "ready", "消息转知识内容、内容选区转事项已可用"),
            new DocumentAcceptanceScenario("approval-brief", "审批说明", "知识内容关联审批对象并展示状态卡片", "ready", "approval 对象关系和平台对象卡已可用"),
            new DocumentAcceptanceScenario("file-brief", "文件说明", "内容页内上传文件卡、预览/下载/替换", "ready", "文件卡和内容上下文替换已可用"),
            new DocumentAcceptanceScenario("workbench", "跨模块工作台", "汇总消息、任务、Base、审批、文件并可反向回看", "ready", "知识内容关系、项目/Base 反向关系和对象卡已可用")
        );
        List<DocumentAcceptanceGate> gates = List.of(
            new DocumentAcceptanceGate("concurrent-editing", "3-5 人同时编辑", "trial-ready", "自动化覆盖双客户端协同；真人 3-5 人试运行需按验收清单执行"),
            new DocumentAcceptanceGate("permission-sharing", "权限分享试运行", "ready", "owner/manage/edit/comment/view、分享链接和权限申请已可用"),
            new DocumentAcceptanceGate("comment-notification", "评论提及通知闭环", "ready", "选区评论、回复、resolve/reopen 和 @mention 通知已可用"),
            new DocumentAcceptanceGate("message-to-doc", "从消息沉淀知识内容", "ready", "IM 消息 convert-to-document 兼容 API 已可用"),
            new DocumentAcceptanceGate("doc-to-task", "从内容生成任务", "ready", "docs/{id}/issues/from-selection 兼容 API 已可用"),
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
            knowledgeBaseSpaceRepository.findDisabledRootForDocument(currentUser.workspaceId(), parentId)
                .ifPresent(rootDocumentId -> {
                    PermissionDecision decision = permissionDecisionService.decide(currentUser, "document", rootDocumentId, "manage");
                    if (!decision.allowed()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge base is disabled");
                    }
                });
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
            documentRepository.listComments(currentUser.workspaceId(), documentId),
            knowledgeContext(currentUser, documentId)
        );
    }

    public String exportMarkdown(CurrentUser currentUser, UUID documentId) {
        requireView(currentUser, documentId);
        List<DocumentBlock> blocks = documentRepository.listBlocks(currentUser.workspaceId(), documentId);
        if (!blocks.isEmpty()) {
            return exportMarkdownFromBlocks(blocks);
        }
        return documentRepository.findContent(currentUser.workspaceId(), documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    public String exportHtml(CurrentUser currentUser, UUID documentId) {
        DocumentSummary document = requireView(currentUser, documentId);
        List<DocumentBlock> blocks = documentRepository.listBlocks(currentUser.workspaceId(), documentId);
        String content = blocks.isEmpty()
            ? documentRepository.findContent(currentUser.workspaceId(), documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"))
            : exportMarkdownFromBlocks(blocks);
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>")
            .append(escapeHtml(document.title()))
            .append("</title></head><body><article class=\"doc-export\">\n");
        html.append("<h1>").append(escapeHtml(document.title())).append("</h1>\n");
        if (!blocks.isEmpty()) {
            for (DocumentBlock block : blocks) {
                html.append(exportHtmlBlock(block));
            }
        } else {
            html.append(exportHtmlFromMarkdown(content));
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
        notifyKnowledgeSubscribers(currentUser, documentId, normalizedTitle, nextVersionNo);
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

    public List<DocumentTemplate> listTemplates(CurrentUser currentUser, UUID knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            requireKnowledgeBaseView(currentUser, knowledgeBaseId);
        }
        return documentRepository.listTemplates(currentUser.workspaceId(), knowledgeBaseId);
    }

    @Transactional
    public DocumentTemplate createTemplate(
        CurrentUser currentUser,
        UUID knowledgeBaseId,
        String title,
        String description,
        String category,
        String content
    ) {
        if (knowledgeBaseId == null) {
            if (!currentUser.hasRole("admin")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace template requires admin role");
            }
        } else {
            requireKnowledgeBaseManage(currentUser, knowledgeBaseId);
        }
        UUID templateId = documentRepository.createTemplate(
            currentUser.workspaceId(),
            knowledgeBaseId,
            normalizeTitle(title),
            normalizeNullableText(description, 512),
            normalizeTemplateCategory(category),
            content == null ? "" : content,
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "document.template.created",
            knowledgeBaseId == null ? "workspace" : "knowledge_base",
            knowledgeBaseId == null ? currentUser.workspaceId() : knowledgeBaseId,
            Map.of("templateId", templateId.toString(), "scopeType", knowledgeBaseId == null ? "workspace" : "knowledge_base")
        );
        return documentRepository.findTemplate(currentUser.workspaceId(), templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Document template was not created"));
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
    public DocumentDetail updateKnowledgeMetadata(
        CurrentUser currentUser,
        UUID documentId,
        UUID maintainerId,
        List<String> tags,
        String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt
    ) {
        DocumentSummary before = requireEdit(currentUser, documentId);
        String normalizedStatus = normalizeKnowledgeStatus(knowledgeStatus);
        Instant normalizedVerifiedAt = "verified".equals(normalizedStatus) && verifiedAt == null ? Instant.now() : verifiedAt;
        List<String> normalizedTags = normalizeTags(tags);
        documentRepository.updateKnowledgeMetadata(
            currentUser.workspaceId(),
            documentId,
            maintainerId,
            normalizedTags,
            normalizeNullableText(category, 64),
            normalizedStatus,
            reviewDueAt,
            normalizedVerifiedAt,
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "document.knowledge_metadata.updated",
            "document",
            documentId,
            Map.of(
                "previousStatus", before.knowledgeStatus(),
                "knowledgeStatus", normalizedStatus,
                "tagCount", Integer.toString(normalizedTags.size())
            )
        );
        return getDocument(currentUser, documentId);
    }

    @Transactional
    public KnowledgeBaseMarkdownImportResult importKnowledgeBaseMarkdownBatch(
        CurrentUser currentUser,
        UUID spaceId,
        UUID parentId,
        List<KnowledgeBaseMarkdownImportItem> items
    ) {
        KnowledgeBaseSpaceSummary space = requireKnowledgeBaseManage(currentUser, spaceId);
        UUID targetParentId = parentId == null ? space.homeDocumentId() : parentId;
        if (!targetParentId.equals(space.rootDocumentId())
            && !documentRepository.isDescendant(currentUser.workspaceId(), space.rootDocumentId(), targetParentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import target is outside the knowledge base");
        }
        List<KnowledgeBaseMarkdownImportItem> normalizedItems = items == null ? List.of() : items.stream()
            .filter(item -> item != null && item.title() != null && !item.title().isBlank())
            .limit(50)
            .toList();
        if (normalizedItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Markdown import requires at least one item");
        }
        List<DocumentSummary> imported = new ArrayList<>();
        for (KnowledgeBaseMarkdownImportItem item : normalizedItems) {
            DocumentDetail detail = createDocument(
                currentUser,
                targetParentId,
                item.title(),
                "markdown",
                item.content(),
                null,
                null,
                "view",
                false
            );
            DocumentDetail enriched = updateKnowledgeMetadata(
                currentUser,
                detail.document().id(),
                currentUser.id(),
                item.tags(),
                item.category(),
                "draft",
                null,
                null
            );
            imported.add(enriched.document());
        }
        auditService.log(
            currentUser,
            "knowledge_base.markdown_batch.imported",
            "knowledge_base",
            spaceId,
            Map.of("importedCount", Integer.toString(imported.size()), "parentId", targetParentId.toString())
        );
        return new KnowledgeBaseMarkdownImportResult(spaceId, imported.size(), imported);
    }

    public String exportKnowledgeBaseMarkdown(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireKnowledgeBaseView(currentUser, spaceId);
        List<DocumentSummary> documents = documentRepository.listKnowledgeBaseDocuments(currentUser.workspaceId(), space.rootDocumentId()).stream()
            .filter(document -> "markdown".equals(document.docType()))
            .toList();
        StringBuilder export = new StringBuilder();
        export.append("# ").append(space.name()).append("\n\n");
        export.append("> 导出时间：").append(Instant.now()).append("\n\n");
        for (DocumentSummary document : documents) {
            List<DocumentBlock> blocks = documentRepository.listBlocks(currentUser.workspaceId(), document.id());
            String content = blocks.isEmpty()
                ? documentRepository.findContent(currentUser.workspaceId(), document.id()).orElse("")
                : exportMarkdownFromBlocks(blocks);
            export.append("\n---\n\n");
            export.append("# ").append(document.title()).append("\n\n");
            if (document.category() != null || !document.tags().isEmpty() || document.reviewDueAt() != null) {
                export.append("<!-- category: ").append(document.category() == null ? "" : document.category()).append(" -->\n");
                export.append("<!-- tags: ").append(String.join(",", document.tags())).append(" -->\n");
                export.append("<!-- knowledgeStatus: ").append(document.knowledgeStatus()).append(" -->\n");
                if (document.reviewDueAt() != null) {
                    export.append("<!-- reviewDueAt: ").append(document.reviewDueAt()).append(" -->\n");
                }
                export.append("\n");
            }
            export.append(content).append("\n");
        }
        return export.toString();
    }

    @Transactional
    public KnowledgeReviewReminderResult runKnowledgeReviewReminders(CurrentUser currentUser, LocalDate beforeDate, int limit) {
        if (!currentUser.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge review reminder requires admin role");
        }
        LocalDate cutoff = beforeDate == null ? LocalDate.now().plusDays(7) : beforeDate;
        List<DocumentSummary> dueDocuments = documentRepository.listDueForReview(currentUser.workspaceId(), cutoff, limit <= 0 ? 50 : limit);
        int notified = 0;
        for (DocumentSummary document : dueDocuments) {
            if (document.maintainerId() == null) {
                continue;
            }
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "document",
                document.id(),
                currentUser.id(),
                Map.of(
                    "recipientId", document.maintainerId().toString(),
                    "notificationType", "knowledge_review_due",
                    "title", "知识内容需要复核",
                    "body", document.title() + " 复核日期：" + document.reviewDueAt(),
                    "targetType", "document",
                    "targetId", document.id().toString(),
                    "webPath", knowledgeDocumentWebPath(currentUser.workspaceId(), document.id()),
                    "dedupeKey", "knowledge.review_due:" + document.id() + ":" + document.reviewDueAt()
                ),
                "notification.knowledge.review_due:" + document.id() + ":" + document.reviewDueAt()
            );
            notified += documentRepository.markReviewReminderSent(currentUser.workspaceId(), document.id(), currentUser.id());
        }
        auditService.log(
            currentUser,
            "knowledge.review_reminders.sent",
            "workspace",
            currentUser.workspaceId(),
            Map.of("scannedCount", Integer.toString(dueDocuments.size()), "notifiedCount", Integer.toString(notified))
        );
        return new KnowledgeReviewReminderResult(dueDocuments.size(), notified);
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

    @Transactional
    public DocumentDetail importHtml(CurrentUser currentUser, UUID documentId, String title, String html) {
        DocumentSummary before = requireEdit(currentUser, documentId);
        String normalizedHtml = html == null ? "" : html;
        String normalizedTitle = title == null || title.isBlank() ? before.title() : title.trim();
        List<DocumentBlockDraft> importedBlocks = normalizeBlocks(blocksFromHtml(normalizedHtml));
        String normalizedContent = contentFromBlocks(importedBlocks);
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
            "HTML 导入",
            "import",
            "从 HTML 导入并转换为块",
            before.currentVersionNo(),
            blockSnapshot(importedBlocks)
        );
        documentRepository.replaceBlocks(currentUser.workspaceId(), documentId, importedBlocks, currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), documentId, normalizedContent);
        registerDocumentObject(currentUser.workspaceId(), documentId, normalizedTitle);
        eventRepository.append(
            currentUser.workspaceId(),
            "document.html.imported",
            "document",
            documentId,
            currentUser.id(),
            Map.of("documentId", documentId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "document.html.imported:" + documentId + ":" + nextVersionNo
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
        List<DocumentBlock> previousBlocks = documentRepository.listBlocks(currentUser.workspaceId(), documentId);
        List<DocumentBlockDraft> normalizedBlocks = normalizeBlocks(blocks);
        String normalizedContent = contentFromBlocks(normalizedBlocks);
        String versionSummary = blockChangeSummary(previousBlocks, normalizedBlocks);
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
            versionSummary,
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

    @Transactional
    public DocumentDetail insertBlock(CurrentUser currentUser, UUID documentId, int baseVersionNo, DocumentBlockDraft block, Integer afterSortOrder) {
        List<DocumentBlockDraft> current = documentRepository.listBlocks(currentUser.workspaceId(), documentId).stream()
            .map(this::draftFromStoredBlock)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int insertAt = afterSortOrder == null ? current.size() : Math.min(Math.max(afterSortOrder + 1, 0), current.size());
        current.add(insertAt, block == null ? new DocumentBlockDraft("paragraph", "", insertAt) : block);
        return saveBlocks(currentUser, documentId, baseVersionNo, current);
    }

    @Transactional
    public DocumentDetail updateBlock(CurrentUser currentUser, UUID documentId, UUID blockId, int baseVersionNo, DocumentBlockDraft patch) {
        List<DocumentBlockDraft> current = documentRepository.listBlocks(currentUser.workspaceId(), documentId).stream()
            .map(this::draftFromStoredBlock)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean found = false;
        for (int index = 0; index < current.size(); index++) {
            DocumentBlockDraft existing = current.get(index);
            if (blockId.equals(existing.id())) {
                current.set(index, mergeBlockDraft(existing, patch));
                found = true;
                break;
            }
        }
        if (!found) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document block not found");
        }
        return saveBlocks(currentUser, documentId, baseVersionNo, current);
    }

    @Transactional
    public DocumentDetail reorderBlocks(CurrentUser currentUser, UUID documentId, int baseVersionNo, List<UUID> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block order is required");
        }
        List<DocumentBlockDraft> current = documentRepository.listBlocks(currentUser.workspaceId(), documentId).stream()
            .map(this::draftFromStoredBlock)
            .toList();
        Map<UUID, DocumentBlockDraft> byId = new HashMap<>();
        for (DocumentBlockDraft block : current) {
            byId.put(block.id(), block);
        }
        List<DocumentBlockDraft> reordered = new ArrayList<>();
        for (UUID blockId : blockIds) {
            DocumentBlockDraft block = byId.remove(blockId);
            if (block == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block order contains unknown block id");
            }
            reordered.add(block);
        }
        reordered.addAll(byId.values());
        return saveBlocks(currentUser, documentId, baseVersionNo, reordered);
    }

    @Transactional
    public DocumentDetail deleteBlock(CurrentUser currentUser, UUID documentId, UUID blockId, int baseVersionNo) {
        List<DocumentBlockDraft> remaining = documentRepository.listBlocks(currentUser.workspaceId(), documentId).stream()
            .filter(block -> !block.id().equals(blockId))
            .map(this::draftFromStoredBlock)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (remaining.size() == documentRepository.listBlocks(currentUser.workspaceId(), documentId).size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document block not found");
        }
        return saveBlocks(currentUser, documentId, baseVersionNo, remaining);
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
        documentRepository.upsertSubjectPermission(
            currentUser.workspaceId(),
            documentId,
            normalizedSubjectType,
            subjectId,
            normalizedPermission,
            currentUser.id()
        );
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only root directories can be configured as knowledge bases");
        }
        java.util.Optional<KnowledgeBaseSpaceSummary> registeredSpace = knowledgeBaseSpaceRepository.findSpaceByRootDocumentId(
            currentUser.workspaceId(),
            documentId
        );
        if (registeredSpace.isPresent()) {
            auditService.log(
                currentUser,
                "document.knowledge_base.deprecated_noop",
                "document",
                documentId,
                Map.of("spaceId", registeredSpace.get().id().toString())
            );
            return getDocument(currentUser, documentId);
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
                    "title", currentUser.displayName() + " 申请访问知识内容「" + document.title() + "」",
                    "body", normalizedReason == null || normalizedReason.isBlank()
                        ? "申请权限：" + normalizedPermission
                        : "申请权限：" + normalizedPermission + "；原因：" + normalizedReason,
                    "targetType", "document",
                    "targetId", documentId.toString(),
                    "webPath", knowledgeDocumentWebPath(currentUser.workspaceId(), documentId),
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

    private KnowledgeBaseSpaceSummary requireKnowledgeBaseView(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = knowledgeBaseSpaceRepository.findSpace(currentUser.workspaceId(), spaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found"));
        if (currentUser.hasRole("admin")) {
            return space;
        }
        PermissionDecision decision = permissionDecisionService.decide(currentUser, "knowledge_base", spaceId, "view");
        if (decision.allowed()) {
            return space;
        }
        requireView(currentUser, space.rootDocumentId());
        return space;
    }

    private KnowledgeBaseSpaceSummary requireKnowledgeBaseManage(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = knowledgeBaseSpaceRepository.findSpace(currentUser.workspaceId(), spaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found"));
        if (currentUser.hasRole("admin")) {
            return space;
        }
        PermissionDecision decision = permissionDecisionService.decide(currentUser, "knowledge_base", spaceId, "manage");
        if (decision.allowed()) {
            return space;
        }
        requireManage(currentUser, space.rootDocumentId());
        return space;
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
                    block.parentId(),
                    block.blockType(),
                    block.content(),
                    block.sortOrder(),
                    block.schemaVersion(),
                    block.attrs(),
                    block.richContent(),
                    block.plainText(),
                    block.anchorId(),
                    block.blockVersion(),
                    block.createdBy(),
                    block.createdAt(),
                    block.updatedBy(),
                    block.updatedAt(),
                    summary,
                    metadata
                );
            })
            .toList();
    }

    private Map<String, Object> parseStructuredBlockMetadata(DocumentBlock block) {
        Map<String, Object> metadata = new HashMap<>();
        if ("table".equals(block.blockType()) || EMBED_BLOCK_TYPES.contains(block.blockType())) {
            metadata.putAll(parseJsonObject(block.content()).orElseGet(() -> Map.of("parseError", "invalid_json")));
        }
        metadata.putAll(block.attrs() == null ? Map.of() : block.attrs());
        metadata.put("schemaVersion", block.schemaVersion());
        if (block.parentId() != null) {
            metadata.put("parentId", block.parentId().toString());
        }
        if (block.anchorId() != null && !block.anchorId().isBlank()) {
            metadata.put("anchorId", block.anchorId());
        }
        return metadata;
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

    private DocumentBlockDraft draftFromStoredBlock(DocumentBlock block) {
        return new DocumentBlockDraft(
            block.id(),
            block.parentId(),
            block.blockType(),
            block.content(),
            block.sortOrder(),
            block.schemaVersion(),
            block.attrs(),
            block.richContent(),
            block.plainText(),
            block.anchorId(),
            false
        );
    }

    private DocumentBlockDraft mergeBlockDraft(DocumentBlockDraft existing, DocumentBlockDraft patch) {
        if (patch == null) {
            return existing;
        }
        return new DocumentBlockDraft(
            existing.id(),
            patch.parentId() == null ? existing.parentId() : patch.parentId(),
            patch.blockType() == null ? existing.blockType() : patch.blockType(),
            patch.content() == null ? existing.content() : patch.content(),
            existing.sortOrder(),
            patch.schemaVersion() == null ? existing.schemaVersion() : patch.schemaVersion(),
            patch.attrs() == null || patch.attrs().isEmpty() ? existing.attrs() : patch.attrs(),
            patch.richContent() == null || patch.richContent().isEmpty() ? existing.richContent() : patch.richContent(),
            patch.plainText() == null ? existing.plainText() : patch.plainText(),
            patch.anchorId() == null ? existing.anchorId() : patch.anchorId(),
            false
        );
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
            knowledgeDocumentWebPath(workspaceId, documentId),
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
        KnowledgeContext knowledgeContext = knowledgeContext(currentUser, documentId);
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
                    "title", currentUser.displayName() + " 在「" + knowledgeLocationTitle(document, knowledgeContext) + "」评论中提到了你",
                    "body", normalizedContent,
                    "targetType", "document",
                    "targetId", documentId.toString(),
                    "webPath", commentWebPath(documentId, threadId, knowledgeContext),
                    "dedupeKey", "document.comment.mention:" + commentId + ":" + mentionedUserId
                ),
                "notification.document.comment.mention:" + commentId + ":" + mentionedUserId
            );
        }
    }

    public KnowledgeContext knowledgeContext(CurrentUser currentUser, UUID documentId) {
        return knowledgeBaseSpaceRepository.findSpaceByDocumentId(currentUser.workspaceId(), documentId)
            .map(space -> {
                List<DocumentPathItem> path = documentPath(currentUser, documentId);
                String pathText = path.stream()
                    .map(DocumentPathItem::title)
                    .reduce((left, right) -> left + " / " + right)
                    .orElse("");
                return new KnowledgeContext(
                    space.id(),
                    space.name(),
                    space.code(),
                    space.rootDocumentId(),
                    space.homeDocumentId(),
                    path,
                    pathText,
                    "/knowledge-bases/" + space.id() + "?docId=" + documentId
                );
            })
            .orElse(null);
    }

    private String knowledgeLocationTitle(DocumentSummary document, KnowledgeContext knowledgeContext) {
        if (knowledgeContext == null || knowledgeContext.pathText() == null || knowledgeContext.pathText().isBlank()) {
            return document.title();
        }
        return knowledgeContext.spaceName() + " / " + knowledgeContext.pathText();
    }

    private String commentWebPath(UUID documentId, UUID threadId, KnowledgeContext knowledgeContext) {
        String basePath = knowledgeContext == null ? "/docs/" + documentId : knowledgeContext.webPath();
        return basePath + (basePath.contains("?") ? "&" : "?") + "commentId=" + threadId;
    }

    private String knowledgeDocumentWebPath(UUID workspaceId, UUID documentId) {
        return knowledgeBaseSpaceRepository.findSpaceByDocumentId(workspaceId, documentId)
            .map(space -> "/knowledge-bases/" + space.id() + "?docId=" + documentId)
            .orElse("/docs/" + documentId);
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
            document.targetSummary()
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
        if (!List.of("markdown", "folder", "space", "object_ref", "external_link").contains(type)) {
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

    private void notifyKnowledgeSubscribers(CurrentUser currentUser, UUID documentId, String title, int versionNo) {
        List<UUID> subscriberIds = knowledgeBaseSpaceRepository.listSubscriberIdsForDocument(currentUser.workspaceId(), documentId).stream()
            .filter(subscriberId -> !subscriberId.equals(currentUser.id()))
            .distinct()
            .toList();
        for (UUID subscriberId : subscriberIds) {
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "document",
                documentId,
                currentUser.id(),
                Map.of(
                    "recipientId", subscriberId.toString(),
                    "notificationType", "knowledge_subscription_updated",
                    "title", "关注的知识已更新",
                    "body", title + " 已更新到 v" + versionNo,
                    "targetType", "document",
                    "targetId", documentId.toString(),
                    "webPath", knowledgeDocumentWebPath(currentUser.workspaceId(), documentId),
                    "dedupeKey", "knowledge.subscription.updated:" + documentId + ":" + versionNo + ":" + subscriberId
                ),
                "notification.knowledge.subscription_updated:" + documentId + ":" + versionNo + ":" + subscriberId
            );
        }
    }

    private String normalizeKnowledgeStatus(String knowledgeStatus) {
        String normalized = knowledgeStatus == null || knowledgeStatus.isBlank()
            ? "draft"
            : knowledgeStatus.trim().toLowerCase(Locale.ROOT);
        if (!List.of("draft", "verified", "needs_review", "outdated", "archived").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid knowledge status");
        }
        return normalized;
    }

    private String normalizeTemplateCategory(String category) {
        String normalized = category == null || category.isBlank() ? "knowledge" : category.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .map(tag -> tag.trim())
            .map(tag -> tag.length() <= 32 ? tag : tag.substring(0, 32))
            .distinct()
            .limit(20)
            .toList();
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

    private String blockChangeSummary(List<DocumentBlock> previousBlocks, List<DocumentBlockDraft> nextBlocks) {
        Map<UUID, DocumentBlock> previousById = new HashMap<>();
        for (DocumentBlock block : previousBlocks) {
            previousById.put(block.id(), block);
        }
        int added = 0;
        int deleted = 0;
        int modified = 0;
        int moved = 0;
        int typeChanged = 0;
        Set<UUID> seen = new LinkedHashSet<>();
        for (DocumentBlockDraft block : nextBlocks) {
            if (block.id() == null || !previousById.containsKey(block.id())) {
                added++;
                continue;
            }
            seen.add(block.id());
            DocumentBlock previous = previousById.get(block.id());
            if (!Objects.equals(previous.blockType(), block.blockType())) {
                typeChanged++;
            }
            if (previous.sortOrder() != (block.sortOrder() == null ? previous.sortOrder() : block.sortOrder())) {
                moved++;
            }
            if (!Objects.equals(previous.content(), block.content())
                || !Objects.equals(previous.plainText(), block.plainText())
                || !Objects.equals(previous.attrs(), block.attrs())
                || !Objects.equals(previous.richContent(), block.richContent())
                || !Objects.equals(previous.parentId(), block.parentId())) {
                modified++;
            }
        }
        for (DocumentBlock block : previousBlocks) {
            if (!seen.contains(block.id())) {
                deleted++;
            }
        }
        return "块变更：新增 " + added
            + "，删除 " + deleted
            + "，修改 " + modified
            + "，移动 " + moved
            + "，类型转换 " + typeChanged;
    }

    private String exportMarkdownFromBlocks(List<DocumentBlock> blocks) {
        return blocks.stream()
            .sorted(Comparator.comparingInt(DocumentBlock::sortOrder))
            .map(this::exportMarkdownBlock)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private String exportMarkdownBlock(DocumentBlock block) {
        return switch (block.blockType()) {
            case "heading" -> "# " + safeExportText(block.content());
            case "list", "bullet_list", "bulleted_list" -> "- " + safeExportText(block.content());
            case "ordered_list" -> "1. " + safeExportText(block.content());
            case "task", "todo", "task_item" -> "- [ ] " + safeExportText(block.content());
            case "quote" -> "> " + safeExportText(block.content());
            case "code", "code_block" -> "```\n" + (block.content() == null ? "" : block.content()) + "\n```";
            case "table" -> exportTableMarkdown(block.content());
            case "image", "file", "embed_object", "base_view", "issue_embed", "message_embed", "file_embed", "link_card" -> exportEmbedMarkdown(block);
            case "divider" -> "---";
            case "legacy_html" -> "<!-- legacy_html retained; review before final rich-text removal -->\n" + (block.content() == null ? "" : block.content());
            default -> safeExportText(block.content());
        };
    }

    private String exportTableMarkdown(String content) {
        Map<String, Object> table = parseJsonObject(content).orElse(Map.of());
        Object columns = table.get("columns");
        if (columns instanceof List<?> columnList && !columnList.isEmpty()) {
            String header = columnList.stream().map(String::valueOf).map(this::safeExportText).reduce((left, right) -> left + " | " + right).orElse("");
            String divider = columnList.stream().map(column -> "---").reduce((left, right) -> left + " | " + right).orElse("---");
            return "| " + header + " |\n| " + divider + " |\n<!-- table rows are preserved in block JSON when re-importing from the system export. -->";
        }
        return "[table] " + (content == null ? "{}" : content);
    }

    private String exportEmbedMarkdown(DocumentBlock block) {
        Map<String, Object> data = parseJsonObject(block.content()).orElse(Map.of());
        String objectType = normalizeEmbedObjectType(block.blockType(), asString(data.getOrDefault("objectType", data.get("targetType"))));
        String objectId = asString(data.getOrDefault("objectId", data.get("targetId")));
        if (objectType.isBlank() || objectId.isBlank()) {
            return "[Unsupported embedded object]";
        }
        String directive = switch (block.blockType()) {
            case "file", "file_embed" -> "file-card";
            case "link_card" -> "link-card";
            default -> "object-card";
        };
        return "::" + directive + "{objectType=\"" + escapeDirectiveValue(objectType)
            + "\" objectId=\"" + escapeDirectiveValue(objectId)
            + "\" fallback=\"Embedded " + escapeDirectiveValue(objectType) + "\"}";
    }

    private String exportHtmlBlock(DocumentBlock block) {
        return switch (block.blockType()) {
            case "heading" -> "<h1>" + escapeHtml(block.content()) + "</h1>\n";
            case "list", "bullet_list", "bulleted_list" -> "<p>&bull; " + escapeHtml(block.content()) + "</p>\n";
            case "ordered_list" -> "<p>1. " + escapeHtml(block.content()) + "</p>\n";
            case "task", "todo", "task_item" -> "<p><input type=\"checkbox\" disabled> " + escapeHtml(block.content()) + "</p>\n";
            case "quote" -> "<blockquote>" + escapeHtml(block.content()) + "</blockquote>\n";
            case "code", "code_block" -> "<pre><code>" + escapeHtml(block.content()) + "</code></pre>\n";
            case "divider" -> "<hr>\n";
            case "table" -> "<pre data-block-type=\"table\">" + escapeHtml(block.content()) + "</pre>\n";
            case "image", "file", "embed_object", "base_view", "issue_embed", "message_embed", "file_embed", "link_card" ->
                "<p class=\"doc-export-embed\">" + escapeHtml(exportEmbedMarkdown(block)) + "</p>\n";
            case "legacy_html" -> "<div data-block-type=\"legacy_html\">" + (block.content() == null ? "" : block.content()) + "</div>\n";
            default -> "<p>" + escapeHtml(block.content()) + "</p>\n";
        };
    }

    private String exportHtmlFromMarkdown(String content) {
        StringBuilder html = new StringBuilder();
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
        return html.toString();
    }

    private String safeExportText(String value) {
        return value == null ? "" : value.replace("\r", "").trim();
    }

    private String escapeDirectiveValue(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
            String blockType = inferBlockType(trimmed);
            String blockContent = normalizeBlockContent(trimmed);
            blocks.add(new DocumentBlockDraft(
                null,
                null,
                blockType,
                blockContent,
                blocks.size(),
                2,
                defaultAttrs(blockType, blockContent),
                defaultRichContent(blockType, blockContent),
                plainTextForBlock(blockType, blockContent),
                null,
                false
            ));
        }
        if (blocks.isEmpty()) {
            blocks.add(defaultBlockDraft("paragraph", "", 0));
        }
        return blocks;
    }

    List<DocumentBlockDraft> blocksFromHtml(String html) {
        String value = html == null ? "" : html.trim();
        if (value.isBlank()) {
            return List.of(defaultBlockDraft("paragraph", "", 0));
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("<script") || lower.contains("<style")) {
            return List.of(defaultBlockDraft("legacy_html", "<!-- unsafe HTML omitted during import -->", 0));
        }
        if (Pattern.compile("(?is)<(table|iframe|video|svg|canvas|form)\\b").matcher(value).find()) {
            return List.of(defaultBlockDraft("legacy_html", value, 0));
        }
        List<DocumentBlockDraft> blocks = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?is)<(h[1-6]|p|li|blockquote|pre)[^>]*>(.*?)</\\1>|<hr\\b[^>]*>").matcher(value);
        while (matcher.find()) {
            String tag = matcher.group(1) == null ? "hr" : matcher.group(1).toLowerCase(Locale.ROOT);
            String body = matcher.group(2) == null ? "" : htmlToPlainText(matcher.group(2));
            String blockType = switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> "heading";
                case "li" -> "bullet_list";
                case "blockquote" -> "quote";
                case "pre" -> "code_block";
                case "hr" -> "divider";
                default -> "paragraph";
            };
            blocks.add(new DocumentBlockDraft(blockType, "divider".equals(blockType) ? "" : body, blocks.size()));
        }
        if (blocks.isEmpty()) {
            String plainText = htmlToPlainText(value);
            return plainText.isBlank() ? List.of(defaultBlockDraft("legacy_html", value, 0)) : blocksFromContent(plainText);
        }
        return blocks;
    }

    private DocumentBlockDraft defaultBlockDraft(String blockType, String content, int sortOrder) {
        return new DocumentBlockDraft(
            null,
            null,
            blockType,
            content,
            sortOrder,
            2,
            defaultAttrs(blockType, content),
            defaultRichContent(blockType, content),
            plainTextForBlock(blockType, content),
            null,
            false
        );
    }

    private String htmlToPlainText(String html) {
        return html
            .replaceAll("(?is)<br\\s*/?>", "\n")
            .replaceAll("(?is)<[^>]+>", "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim();
    }

    private List<DocumentBlockDraft> normalizeBlocks(List<DocumentBlockDraft> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of(new DocumentBlockDraft("paragraph", "", 0));
        }
        List<DocumentBlockDraft> normalized = new ArrayList<>();
        for (DocumentBlockDraft block : blocks) {
            if (Boolean.TRUE.equals(block.deleted())) {
                continue;
            }
            String blockType = normalizeBlockType(block.blockType());
            String content = normalizeDraftBlockContent(blockType, block.content());
            int sortOrder = normalized.size();
            String plainText = normalizePlainText(blockType, content, block.plainText());
            normalized.add(new DocumentBlockDraft(
                block.id(),
                block.parentId(),
                blockType,
                content,
                sortOrder,
                block.schemaVersion() == null ? 2 : Math.max(1, block.schemaVersion()),
                normalizeJsonMap(block.attrs(), defaultAttrs(blockType, content)),
                normalizeJsonMap(block.richContent(), defaultRichContent(blockType, content)),
                plainText,
                normalizeAnchorId(block.anchorId(), block.id(), sortOrder),
                false
            ));
        }
        return normalized.isEmpty() ? List.of(new DocumentBlockDraft("paragraph", "", 0)) : normalized;
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
        return blockType.equals("code") || blockType.equals("code_block") || blockType.equals("legacy_html")
            ? content == null ? "" : content
            : value;
    }

    private String contentFromBlocks(List<DocumentBlockDraft> blocks) {
        return blocks.stream()
            .map(block -> switch (block.blockType()) {
                case "heading" -> "# " + block.content();
                case "list", "bullet_list", "bulleted_list" -> "- " + block.content();
                case "ordered_list" -> "1. " + block.content();
                case "task", "todo", "task_item" -> "- [ ] " + block.content();
                case "quote" -> "> " + block.content();
                case "code", "code_block" -> "```\n" + block.content() + "\n```";
                case "table" -> "[table] " + block.content();
                case "image", "file", "embed", "embed_object", "base_view", "issue_embed", "message_embed", "file_embed", "link", "link_card" -> "[" + block.blockType() + "] " + block.content();
                case "divider" -> "---";
                case "legacy_html" -> block.content();
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
        Matcher enhancedBlock = Pattern.compile("^\\[(table|image|file|embed|embed_object|base_view|issue_embed|message_embed|file_embed|link|link_card|legacy_html)]\\s+.*$").matcher(line);
        if (enhancedBlock.matches()) {
            return enhancedBlock.group(1);
        }
        return "paragraph";
    }

    private String normalizeBlockContent(String line) {
        return line
            .replaceFirst("^\\[(table|image|file|embed|embed_object|base_view|issue_embed|message_embed|file_embed|link|link_card|legacy_html)]\\s+", "")
            .replaceFirst("^#{1,6}\\s*", "")
            .replaceFirst("^- \\[[ xX]\\]\\s*", "")
            .replaceFirst("^-\\s+", "")
            .replaceFirst("^>\\s*", "");
    }

    private String normalizeBlockType(String blockType) {
        String type = blockType == null ? "" : blockType.toLowerCase();
        if ("bulleted_list".equals(type)) {
            type = "bullet_list";
        } else if ("numbered_list".equals(type)) {
            type = "ordered_list";
        } else if ("todo".equals(type)) {
            type = "task_item";
        } else if ("code".equals(type)) {
            type = "code_block";
        } else if ("embed".equals(type)) {
            type = "embed_object";
        } else if ("link".equals(type)) {
            type = "link_card";
        }
        if (!BLOCK_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document block type");
        }
        return type;
    }

    private Map<String, Object> normalizeJsonMap(Map<String, Object> value, Map<String, Object> fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return new HashMap<>(value);
    }

    private String normalizePlainText(String blockType, String content, String plainText) {
        String value = plainText == null ? "" : plainText.trim();
        return value.isBlank() ? plainTextForBlock(blockType, content) : value;
    }

    private String plainTextForBlock(String blockType, String content) {
        if ("table".equals(blockType) || EMBED_BLOCK_TYPES.contains(blockType)) {
            return parseJsonObject(content).map(map -> String.join(" ", map.values().stream().map(String::valueOf).toList())).orElse("");
        }
        if ("divider".equals(blockType)) {
            return "";
        }
        return content == null ? "" : content.trim();
    }

    private Map<String, Object> defaultAttrs(String blockType, String content) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("type", blockType);
        if ("heading".equals(blockType)) {
            attrs.put("level", 1);
        }
        return attrs;
    }

    private Map<String, Object> defaultRichContent(String blockType, String content) {
        Map<String, Object> rich = new HashMap<>();
        rich.put("type", blockType);
        if ("table".equals(blockType) || EMBED_BLOCK_TYPES.contains(blockType)) {
            rich.put("data", parseJsonObject(content).orElse(Map.of("raw", content == null ? "" : content)));
        } else {
            rich.put("text", content == null ? "" : content);
        }
        return rich;
    }

    private String normalizeAnchorId(String anchorId, UUID blockId, int sortOrder) {
        String value = anchorId == null ? "" : anchorId.trim();
        if (!value.isBlank()) {
            return value.length() > 128 ? value.substring(0, 128) : value;
        }
        return blockId == null ? "block-" + sortOrder : "block-" + blockId;
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
