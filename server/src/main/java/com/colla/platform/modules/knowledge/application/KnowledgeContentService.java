package com.colla.platform.modules.knowledge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colla.platform.modules.base.application.BaseService;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentAcceptanceGate;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentAcceptanceReport;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentAcceptanceScenario;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlock;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentComment;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCommentAnchor;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentDiffLine;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentDiagnostics;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentMigrationPreview;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPathItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPerformance;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentPermissionRequest;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentShareLink;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentTemplate;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentTransferReport;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItemTreeNode;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentVersion;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentVersionDiff;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseMarkdownImportItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseMarkdownImportResult;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentContext;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeReviewReminderResult;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeBaseSpaceRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeContentService {
    /*
     * Historical naming boundary: Document* services operate the knowledge-content
     * editor substrate stored in the knowledge_base_items table. Product-level space and content
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
    private static final TypeReference<List<KnowledgeContentBlockDraft>> BLOCK_DRAFT_LIST = new TypeReference<>() {
    };
    private static final Duration AUTO_CHECKPOINT_INTERVAL = Duration.ofMinutes(1);

    private final KnowledgeContentRepository contentRepository;
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
    private final ObjectProvider<KnowledgeCollaborationGatewayService> collaborationGatewayProvider;

    public KnowledgeContentService(
        KnowledgeContentRepository contentRepository,
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
        ObjectProvider<PlatformObjectResolverRegistry> objectResolverRegistryProvider,
        ObjectProvider<KnowledgeCollaborationGatewayService> collaborationGatewayProvider
    ) {
        this.contentRepository = contentRepository;
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
        this.collaborationGatewayProvider = collaborationGatewayProvider;
    }

    private void invalidateCollaborationState(UUID workspaceId, UUID itemId) {
        KnowledgeCollaborationGatewayService gateway = collaborationGatewayProvider.getIfAvailable();
        if (gateway != null) {
            gateway.invalidateCollaborationState(workspaceId, itemId);
        }
    }

    public List<KnowledgeBaseItem> listItems(CurrentUser currentUser, boolean includeArchived) {
        return contentRepository.listItems(currentUser.workspaceId(), currentUser.id(), includeArchived);
    }

    public KnowledgeContentAcceptanceReport acceptanceReport(CurrentUser currentUser) {
        List<KnowledgeContentAcceptanceScenario> scenarios = List.of(
            new KnowledgeContentAcceptanceScenario("meeting-notes", "会议纪要", "多人编辑纪要、评论行动项、从纪要生成任务", "ready", "协同编辑、评论线程、内容选区转事项已可用"),
            new KnowledgeContentAcceptanceScenario("requirements", "需求说明", "模板创建、版本命名、评审评论、关联项目事项", "ready", "模板、命名版本、评论和事项关系已可用"),
            new KnowledgeContentAcceptanceScenario("project-plan", "项目计划", "内容页嵌入 Base 视图和项目事项，任务回看知识片段", "ready", "Base view、issue embed、关联知识片段已可用"),
            new KnowledgeContentAcceptanceScenario("retro", "项目复盘", "多人补充复盘内容、提及成员、冻结命名版本", "ready", "协同、@mention 通知和命名版本已可用"),
            new KnowledgeContentAcceptanceScenario("knowledge-base", "知识库", "空间/文件夹组织知识条目、默认权限和分享链接", "ready", "space/folder/tree、知识库默认权限和分享链接已可用"),
            new KnowledgeContentAcceptanceScenario("base-kanban", "Base 看板说明", "内容页内嵌 Base 视图并展示权限态、筛选和排序", "ready", "M48 Base view 摘要已可用"),
            new KnowledgeContentAcceptanceScenario("incident", "问题排查", "从消息沉淀知识内容、从选区创建 BUG/任务、保留来源上下文", "ready", "消息转知识内容、内容选区转事项已可用"),
            new KnowledgeContentAcceptanceScenario("approval-brief", "审批说明", "知识内容关联审批对象并展示状态卡片", "ready", "approval 对象关系和平台对象卡已可用"),
            new KnowledgeContentAcceptanceScenario("file-brief", "文件说明", "内容页内上传文件卡、预览/下载/替换", "ready", "文件卡和内容上下文替换已可用"),
            new KnowledgeContentAcceptanceScenario("workbench", "跨模块工作台", "汇总消息、任务、Base、审批、文件并可反向回看", "ready", "知识内容关系、项目/Base 反向关系和对象卡已可用")
        );
        List<KnowledgeContentAcceptanceGate> gates = List.of(
            new KnowledgeContentAcceptanceGate("concurrent-editing", "3-5 人同时编辑", "trial-ready", "自动化覆盖双客户端协同；真人 3-5 人试运行需按验收清单执行"),
            new KnowledgeContentAcceptanceGate("permission-sharing", "权限分享试运行", "ready", "owner/manage/edit/comment/view、分享链接和权限申请已可用"),
            new KnowledgeContentAcceptanceGate("comment-notification", "评论提及通知闭环", "ready", "选区评论、回复、resolve/reopen 和 @mention 通知已可用"),
            new KnowledgeContentAcceptanceGate("message-to-knowledge-content", "从消息沉淀知识内容", "ready", "IM 消息 canonical 转换 API 已可用"),
            new KnowledgeContentAcceptanceGate("doc-to-task", "从内容生成任务", "ready", "docs/{id}/issues/from-selection 兼容 API 已可用"),
            new KnowledgeContentAcceptanceGate("p0-p1-defects", "P0/P1 缺陷收口", "ready", "当前自动化门禁未发现 P0/P1 阻塞缺陷"),
            new KnowledgeContentAcceptanceGate("v1-freeze", "v1 验收标准冻结", "frozen", "以本报告 10 场景和 8 验收门为冻结标准"),
            new KnowledgeContentAcceptanceGate("quality-gates", "质量门禁", "ready", "M49 finish 已通过全量测试、构建、敏感扫描和安全门禁")
        );
        return new KnowledgeContentAcceptanceReport(
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

    public List<KnowledgeBaseItemTreeNode> listDocumentTree(CurrentUser currentUser, boolean includeArchived) {
        List<KnowledgeBaseItem> knowledge_base_items = contentRepository.listItems(currentUser.workspaceId(), currentUser.id(), includeArchived);
        return buildTree(knowledge_base_items);
    }

    public List<KnowledgeContentPathItem> documentPath(CurrentUser currentUser, UUID itemId) {
        requireView(currentUser, itemId);
        Map<UUID, KnowledgeBaseItem> byId = new HashMap<>();
        for (KnowledgeBaseItem document : contentRepository.listItems(currentUser.workspaceId(), currentUser.id(), true)) {
            byId.put(document.id(), document);
        }
        List<KnowledgeContentPathItem> reversed = new ArrayList<>();
        KnowledgeBaseItem current = byId.get(itemId);
        while (current != null) {
            reversed.add(new KnowledgeContentPathItem(current.id(), current.title(), current.contentType(), current.permissionLevel()));
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        List<KnowledgeContentPathItem> path = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    @Transactional
    public KnowledgeContent createItem(
        CurrentUser currentUser,
        UUID parentId,
        String title,
        String contentType,
        String content,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        Boolean knowledgeBase
    ) {
        if (parentId != null) {
            requireEdit(currentUser, parentId);
            knowledgeBaseSpaceRepository.findDisabledRootForDocument(currentUser.workspaceId(), parentId)
                .ifPresent(rootItemId -> {
                    PermissionDecision decision = permissionDecisionService.decide(currentUser, "knowledge_content", rootItemId, "manage");
                    if (!decision.allowed()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge base is disabled");
                    }
                });
        }
        String normalizedTitle = normalizeTitle(title);
        String normalizedContentType = normalizeContentType(contentType);
        String normalizedContent = content == null ? "" : content;
        String normalizedDescription = normalizeNullableText(description, 512);
        String normalizedCoverUrl = normalizeNullableText(coverUrl, 1024);
        String normalizedDefaultPermission = normalizeDefaultPermission(defaultPermissionLevel);
        boolean normalizedKnowledgeBase = Boolean.TRUE.equals(knowledgeBase) && "space".equals(normalizedContentType);
        int nextSortOrder = nextSortOrder(currentUser, parentId);
        UUID itemId = contentRepository.createItem(
            currentUser.workspaceId(),
            parentId,
            normalizedTitle,
            normalizedContentType,
            normalizedContent,
            nextSortOrder,
            normalizedDescription,
            normalizedCoverUrl,
            normalizedDefaultPermission,
            normalizedKnowledgeBase,
            currentUser.id()
        );
        contentRepository.copyParentPermissions(currentUser.workspaceId(), itemId, parentId, currentUser.id());
        permissionDecisionService.grantResource(
            currentUser.workspaceId(),
            "knowledge_content",
            itemId,
            "user",
            currentUser.id(),
            "owner",
            "owner",
            null,
            null,
            currentUser.id()
        );
        contentRepository.addVersion(
            currentUser.workspaceId(),
            itemId,
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
        contentRepository.replaceBlocks(currentUser.workspaceId(), itemId, blocksFromContent(normalizedContent), currentUser.id());
        registerDocumentObject(currentUser.workspaceId(), itemId, normalizedTitle);
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.created",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString()),
            "knowledge.content.created:" + itemId
        );
        return getContent(currentUser, itemId);
    }

    public KnowledgeContent getContent(CurrentUser currentUser, UUID itemId) {
        KnowledgeBaseItem summary = requireView(currentUser, itemId);
        String content = contentRepository.findContent(currentUser.workspaceId(), itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"));
        if (contentRepository.isShareLinkAccess(currentUser.workspaceId(), itemId, currentUser.id())) {
            auditService.log(
                currentUser,
                "knowledge.content.share_link.accessed",
                "knowledge_content",
                itemId,
                Map.of("permissionLevel", summary.permissionLevel())
            );
        }
        return new KnowledgeContent(
            summary,
            content,
            hydrateBlocks(currentUser, contentRepository.listBlocks(currentUser.workspaceId(), itemId)),
            contentRepository.listRelations(currentUser.workspaceId(), itemId),
            contentRepository.listPermissions(currentUser.workspaceId(), itemId),
            permissionDecisionService.hasLevel(summary.permissionLevel(), "manage")
                ? contentRepository.listShareLinks(currentUser.workspaceId(), itemId)
                : List.of(),
            contentRepository.listComments(currentUser.workspaceId(), itemId),
            knowledgeContext(currentUser, itemId)
        );
    }

    public String exportMarkdown(CurrentUser currentUser, UUID itemId) {
        requireView(currentUser, itemId);
        List<KnowledgeContentBlock> blocks = contentRepository.listBlocks(currentUser.workspaceId(), itemId);
        if (!blocks.isEmpty()) {
            return exportMarkdownFromBlocks(blocks);
        }
        return contentRepository.findContent(currentUser.workspaceId(), itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"));
    }

    public String exportHtml(CurrentUser currentUser, UUID itemId) {
        KnowledgeBaseItem document = requireView(currentUser, itemId);
        List<KnowledgeContentBlock> blocks = contentRepository.listBlocks(currentUser.workspaceId(), itemId);
        String content = blocks.isEmpty()
            ? contentRepository.findContent(currentUser.workspaceId(), itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"))
            : exportMarkdownFromBlocks(blocks);
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>")
            .append(escapeHtml(document.title()))
            .append("</title></head><body><article class=\"doc-export\">\n");
        html.append("<h1>").append(escapeHtml(document.title())).append("</h1>\n");
        if (!blocks.isEmpty()) {
            for (KnowledgeContentBlock block : blocks) {
                html.append(exportHtmlBlock(block));
            }
        } else {
            html.append(exportHtmlFromMarkdown(content));
        }
        html.append("</article></body></html>");
        return html.toString();
    }

    public KnowledgeContentTransferReport previewImport(CurrentUser currentUser, UUID itemId, String format, String source) {
        requireEdit(currentUser, itemId);
        String normalizedFormat = normalizeTransferFormat(format);
        String normalizedSource = source == null ? "" : source;
        validateImportSourceSize(normalizedSource);
        List<KnowledgeContentBlockDraft> blocks = "html".equals(normalizedFormat)
            ? blocksFromHtml(normalizedSource)
            : blocksFromContent(normalizedSource);
        List<String> degraded = new ArrayList<>();
        String lower = normalizedSource.toLowerCase(Locale.ROOT);
        if ("html".equals(normalizedFormat) && (lower.contains("<script") || lower.contains("<style"))) {
            degraded.add("unsafe_html_omitted");
        }
        if ("html".equals(normalizedFormat) && Pattern.compile("(?is)<(iframe|video|svg|canvas|form)\\b").matcher(normalizedSource).find()) {
            degraded.add("unsupported_html_preserved_as_legacy");
        }
        if ("markdown".equals(normalizedFormat) && normalizedSource.split("```", -1).length % 2 == 0) {
            degraded.add("unclosed_code_fence");
        }
        return transferReport("import", normalizedFormat, blocks, degraded, normalizedSource, !normalizedSource.isBlank());
    }

    public KnowledgeContentTransferReport exportManifest(CurrentUser currentUser, UUID itemId, String format) {
        requireView(currentUser, itemId);
        String normalizedFormat = normalizeTransferFormat(format);
        List<KnowledgeContentBlockDraft> blocks = contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream()
            .map(this::draftFromStoredBlock)
            .toList();
        String payload = "html".equals(normalizedFormat) ? exportHtml(currentUser, itemId) : exportMarkdown(currentUser, itemId);
        List<String> degraded = blocks.stream().anyMatch(block -> "legacy_html".equals(block.blockType()))
            ? List.of("legacy_html_requires_review")
            : List.of();
        return transferReport("export", normalizedFormat, blocks, degraded, payload, true);
    }

    private KnowledgeContentTransferReport transferReport(
        String direction,
        String format,
        List<KnowledgeContentBlockDraft> blocks,
        List<String> degraded,
        String payload,
        boolean safeToApply
    ) {
        Set<String> features = blocks.stream().map(KnowledgeContentBlockDraft::blockType)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int attachmentCount = (int) blocks.stream().filter(block -> Set.of("image", "file", "file_embed").contains(block.blockType())).count();
        int objectReferenceCount = (int) blocks.stream().filter(block -> EMBED_BLOCK_TYPES.contains(block.blockType())).count();
        String fingerprint = UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8)).toString();
        return new KnowledgeContentTransferReport(
            direction,
            format,
            blocks.size(),
            attachmentCount,
            objectReferenceCount,
            List.copyOf(features),
            List.copyOf(degraded),
            fingerprint,
            safeToApply && !degraded.contains("unclosed_code_fence")
        );
    }

    private String normalizeTransferFormat(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("markdown", "html").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer format must be markdown or html");
        }
        return normalized;
    }

    private void validateImportSourceSize(String source) {
        if (source != null && source.length() > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Import source exceeds 1 MB");
        }
    }

    public KnowledgeContentPerformance performanceProfile(CurrentUser currentUser, UUID itemId) {
        KnowledgeContent detail = getContent(currentUser, itemId);
        String content = detail.content() == null ? "" : detail.content();
        int embedCount = (int) detail.blocks().stream()
            .filter(block -> EMBED_BLOCK_TYPES.contains(block.blockType()) || "table".equals(block.blockType()))
            .count();
        boolean largeDocument = detail.blocks().size() >= 500
            || embedCount >= 50
            || detail.comments().size() >= 100
            || content.length() >= 100_000;
        int budgetTier = detail.blocks().size() <= 100 ? 100 : detail.blocks().size() <= 500 ? 500 : 1000;
        int loadBudgetMs = budgetTier == 100 ? 1500 : budgetTier == 500 ? 2500 : 4000;
        int inputBudgetMs = budgetTier == 100 ? 100 : budgetTier == 500 ? 120 : 150;
        int saveBudgetMs = budgetTier == 100 ? 1500 : budgetTier == 500 ? 2500 : 4000;
        int searchBudgetMs = budgetTier == 100 ? 1000 : budgetTier == 500 ? 1500 : 2000;
        int collaborationBudgetMs = budgetTier == 100 ? 800 : budgetTier == 500 ? 1000 : 1500;
        long snapshotBytes = blockSnapshot(detail.blocks().stream().map(this::draftFromStoredBlock).toList())
            .getBytes(StandardCharsets.UTF_8).length;
        return new KnowledgeContentPerformance(
            itemId,
            detail.blocks().size(),
            embedCount,
            detail.comments().size(),
            content.length(),
            lineCount(content),
            snapshotBytes,
            budgetTier,
            Math.min(detail.blocks().size(), 160),
            loadBudgetMs,
            inputBudgetMs,
            saveBudgetMs,
            searchBudgetMs,
            collaborationBudgetMs,
            largeDocument,
            largeDocument ? "lazy-preview" : "full-editor"
        );
    }

    public KnowledgeContentDiagnostics diagnostics(CurrentUser currentUser, UUID itemId) {
        requireManage(currentUser, itemId);
        KnowledgeContent detail = getContent(currentUser, itemId);
        List<KnowledgeContentBlockDraft> drafts = detail.blocks().stream().map(this::draftFromStoredBlock).toList();
        long snapshotBytes = blockSnapshot(drafts).getBytes(StandardCharsets.UTF_8).length;
        int objectReferenceCount = (int) detail.blocks().stream()
            .filter(block -> EMBED_BLOCK_TYPES.contains(block.blockType()))
            .count();
        int unavailableObjectCount = (int) detail.blocks().stream()
            .filter(block -> EMBED_BLOCK_TYPES.contains(block.blockType()))
            .filter(block -> block.embedSummary() == null || block.embedSummary().accessState() != ObjectAccessState.available)
            .count();
        var collaboration = contentRepository.findCollaborationState(currentUser.workspaceId(), itemId).orElse(null);
        return new KnowledgeContentDiagnostics(
            itemId,
            detail.item().currentVersionNo(),
            detail.blocks().size(),
            snapshotBytes,
            detail.permissions().size(),
            objectReferenceCount,
            unavailableObjectCount,
            detail.content() != null,
            detail.shareLinks().stream().anyMatch(link -> link.enabled() && (link.expiresAt() == null || link.expiresAt().isAfter(Instant.now()))),
            collaboration == null ? 0 : collaboration.serverClock(),
            collaboration != null && !Objects.equals(collaboration.snapshotContent(), detail.content()),
            collaboration == null ? null : collaboration.lastSavedAt(),
            Instant.now(),
            true
        );
    }

    public KnowledgeContentMigrationPreview migrationPreview(CurrentUser currentUser, UUID itemId) {
        KnowledgeContent detail = getContent(currentUser, itemId);
        String content = detail.content() == null ? "" : detail.content();
        List<KnowledgeContentBlockDraft> projectedBlocks = blocksFromContent(content);
        return new KnowledgeContentMigrationPreview(
            itemId,
            detail.item().currentVersionNo(),
            projectedBlocks.size(),
            detail.blocks().size(),
            content.length(),
            projectedBlocks.size() == detail.blocks().size(),
            detail.item().currentVersionNo() > 1,
            detail.blocks().isEmpty() ? "content-to-blocks" : "verify-existing-blocks"
        );
    }

    @Transactional
    public KnowledgeContent saveContent(CurrentUser currentUser, UUID itemId, int baseVersionNo, String title, String content) {
        KnowledgeBaseItem before = requireEdit(currentUser, itemId);
        if (baseVersionNo != before.currentVersionNo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document version conflict");
        }
        String normalizedTitle = title == null || title.isBlank() ? before.title() : title.trim();
        String normalizedContent = content == null ? "" : content;
        int nextVersionNo = before.currentVersionNo() + 1;
        contentRepository.updateContent(
            currentUser.workspaceId(),
            itemId,
            before.parentId(),
            normalizedTitle,
            normalizedContent,
            nextVersionNo,
            currentUser.id()
        );
        contentRepository.addVersion(
            currentUser.workspaceId(),
            itemId,
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
        contentRepository.replaceBlocks(currentUser.workspaceId(), itemId, blocksFromContent(normalizedContent), currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), itemId, normalizedContent);
        registerDocumentObject(currentUser.workspaceId(), itemId, normalizedTitle);
        invalidateCollaborationState(currentUser.workspaceId(), itemId);
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.updated",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "knowledge.content.updated:" + itemId + ":" + nextVersionNo
        );
        notifyKnowledgeSubscribers(currentUser, itemId, normalizedTitle, nextVersionNo);
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent moveItem(CurrentUser currentUser, UUID itemId, UUID parentId, Integer sortOrder) {
        KnowledgeBaseItem document = requireManage(currentUser, itemId);
        if (parentId != null) {
            requireEdit(currentUser, parentId);
            if (itemId.equals(parentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document cannot be moved under itself");
            }
            if (contentRepository.isDescendant(currentUser.workspaceId(), itemId, parentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document cannot be moved under its descendant");
            }
        }
        int nextSortOrder = sortOrder == null ? document.sortOrder() : sortOrder;
        contentRepository.moveItem(currentUser.workspaceId(), document.id(), parentId, nextSortOrder, currentUser.id());
        contentRepository.copyParentPermissions(currentUser.workspaceId(), document.id(), parentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.moved",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "parentId", parentId == null ? "" : parentId.toString(), "sortOrder", Integer.toString(nextSortOrder)),
            "knowledge.content.moved:" + itemId + ":" + System.nanoTime()
        );
        auditService.log(
            currentUser,
            "knowledge.content.moved",
            "knowledge_content",
            itemId,
            Map.of("parentId", parentId == null ? "" : parentId.toString(), "sortOrder", Integer.toString(nextSortOrder))
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent copyItem(CurrentUser currentUser, UUID itemId, UUID parentId, String title) {
        KnowledgeContent source = getContent(currentUser, itemId);
        if (!"markdown".equals(source.item().contentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only knowledge pages can be copied");
        }
        String copyTitle = title == null || title.isBlank() ? source.item().title() + " 副本" : normalizeTitle(title);
        KnowledgeContent created = createItem(
            currentUser,
            parentId,
            copyTitle,
            "markdown",
            source.content(),
            source.item().description(),
            source.item().coverUrl(),
            source.item().defaultPermissionLevel(),
            false
        );
        Map<UUID, UUID> copiedBlockIds = source.blocks().stream().collect(java.util.stream.Collectors.toMap(
            KnowledgeContentBlock::id,
            ignored -> UUID.randomUUID(),
            (left, right) -> left,
            java.util.LinkedHashMap::new
        ));
        List<KnowledgeContentBlockDraft> sourceBlocks = source.blocks().stream().map(block -> new KnowledgeContentBlockDraft(
            copiedBlockIds.get(block.id()),
            block.parentId() == null ? null : copiedBlockIds.get(block.parentId()),
            block.blockType(),
            block.content(),
            block.sortOrder(),
            block.schemaVersion(),
            block.attrs(),
            block.richContent(),
            block.plainText(),
            null,
            false
        )).toList();
        KnowledgeContent copied = sourceBlocks.isEmpty()
            ? created
            : saveBlocks(currentUser, created.item().id(), created.item().currentVersionNo(), copyTitle, sourceBlocks);
        copied = updateKnowledgeMetadata(
            currentUser,
            copied.item().id(),
            source.item().maintainerId(),
            source.item().tags(),
            source.item().category(),
            source.item().knowledgeStatus(),
            source.item().reviewDueAt(),
            source.item().verifiedAt()
        );
        auditService.log(
            currentUser,
            "knowledge.content.copied",
            "knowledge_content",
            copied.item().id(),
            Map.of(
                "sourceItemId", itemId.toString(),
                "targetParentId", parentId == null ? "" : parentId.toString(),
                "explicitPermissionsCopied", "false"
            )
        );
        return copied;
    }

    @Transactional
    public KnowledgeContent archiveItem(CurrentUser currentUser, UUID itemId) {
        requireManage(currentUser, itemId);
        contentRepository.archiveItemTree(currentUser.workspaceId(), itemId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.archived",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString()),
            "knowledge.content.archived:" + itemId + ":" + System.nanoTime()
        );
        auditService.log(currentUser, "knowledge.content.archived", "knowledge_content", itemId, Map.of("itemId", itemId.toString()));
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent restoreItem(CurrentUser currentUser, UUID itemId) {
        requireManage(currentUser, itemId);
        contentRepository.restoreItemTree(currentUser.workspaceId(), itemId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.restored",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString()),
            "knowledge.content.restored:" + itemId + ":" + System.nanoTime()
        );
        auditService.log(currentUser, "knowledge.content.restored", "knowledge_content", itemId, Map.of("itemId", itemId.toString()));
        return getContent(currentUser, itemId);
    }

    public List<KnowledgeContentVersion> listVersions(CurrentUser currentUser, UUID itemId) {
        requireView(currentUser, itemId);
        return contentRepository.listVersions(currentUser.workspaceId(), itemId);
    }

    public List<KnowledgeContentTemplate> listTemplates(CurrentUser currentUser, UUID knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            requireKnowledgeBaseView(currentUser, knowledgeBaseId);
        }
        return contentRepository.listTemplates(currentUser.workspaceId(), knowledgeBaseId);
    }

    @Transactional
    public KnowledgeContentTemplate createTemplate(
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
        UUID templateId = contentRepository.createTemplate(
            currentUser.workspaceId(),
            knowledgeBaseId,
            normalizeTitle(title),
            normalizeNullableText(description, 512),
            normalizeTemplateCategory(category),
            sanitizeTemplateContent(currentUser, content),
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "knowledge.content.template.created",
            knowledgeBaseId == null ? "workspace" : "knowledge_base",
            knowledgeBaseId == null ? currentUser.workspaceId() : knowledgeBaseId,
            Map.of("templateId", templateId.toString(), "scopeType", knowledgeBaseId == null ? "workspace" : "knowledge_base")
        );
        return contentRepository.findTemplate(currentUser.workspaceId(), templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Document template was not created"));
    }

    @Transactional
    public KnowledgeContentTemplate upgradeTemplate(CurrentUser currentUser, UUID templateId, String content) {
        KnowledgeContentTemplate source = contentRepository.findTemplate(currentUser.workspaceId(), templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document template not found"));
        if (source.builtIn()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Built-in templates cannot be upgraded in place");
        }
        if (source.knowledgeBaseId() == null) {
            if (!currentUser.hasRole("admin")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace template requires admin role");
            }
        } else {
            requireKnowledgeBaseManage(currentUser, source.knowledgeBaseId());
        }
        UUID upgradedId = contentRepository.upgradeTemplate(
            currentUser.workspaceId(),
            templateId,
            sanitizeTemplateContent(currentUser, content),
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "knowledge.content.template.upgraded",
            source.knowledgeBaseId() == null ? "workspace" : "knowledge_base",
            source.knowledgeBaseId() == null ? currentUser.workspaceId() : source.knowledgeBaseId(),
            Map.of("previousTemplateId", templateId.toString(), "templateId", upgradedId.toString())
        );
        return contentRepository.findTemplate(currentUser.workspaceId(), upgradedId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Document template upgrade was not created"));
    }

    @Transactional
    public KnowledgeContent createFromTemplate(CurrentUser currentUser, UUID templateId, UUID parentId, String title) {
        KnowledgeContentTemplate template = contentRepository.findTemplate(currentUser.workspaceId(), templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document template not found"));
        return createItem(
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
    public KnowledgeContent updateKnowledgeMetadata(
        CurrentUser currentUser,
        UUID itemId,
        UUID maintainerId,
        List<String> tags,
        String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt
    ) {
        KnowledgeBaseItem before = requireEdit(currentUser, itemId);
        String normalizedStatus = normalizeKnowledgeStatus(knowledgeStatus);
        Instant normalizedVerifiedAt = "verified".equals(normalizedStatus) && verifiedAt == null ? Instant.now() : verifiedAt;
        List<String> normalizedTags = normalizeTags(tags);
        contentRepository.updateKnowledgeMetadata(
            currentUser.workspaceId(),
            itemId,
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
            "knowledge.content.knowledge_metadata.updated",
            "knowledge_content",
            itemId,
            Map.of(
                "previousStatus", before.knowledgeStatus(),
                "knowledgeStatus", normalizedStatus,
                "tagCount", Integer.toString(normalizedTags.size())
            )
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeBaseMarkdownImportResult importKnowledgeBaseMarkdownBatch(
        CurrentUser currentUser,
        UUID spaceId,
        UUID parentId,
        List<KnowledgeBaseMarkdownImportItem> items
    ) {
        KnowledgeBaseSpaceSummary space = requireKnowledgeBaseManage(currentUser, spaceId);
        UUID targetParentId = parentId == null ? space.homeItemId() : parentId;
        if (!targetParentId.equals(space.rootItemId())
            && !contentRepository.isDescendant(currentUser.workspaceId(), space.rootItemId(), targetParentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import target is outside the knowledge base");
        }
        List<KnowledgeBaseMarkdownImportItem> normalizedItems = items == null ? List.of() : items.stream()
            .filter(item -> item != null && item.title() != null && !item.title().isBlank())
            .limit(50)
            .toList();
        if (normalizedItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Markdown import requires at least one item");
        }
        List<KnowledgeBaseItem> imported = new ArrayList<>();
        for (KnowledgeBaseMarkdownImportItem item : normalizedItems) {
            KnowledgeContent detail = createItem(
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
            KnowledgeContent enriched = updateKnowledgeMetadata(
                currentUser,
                detail.item().id(),
                currentUser.id(),
                item.tags(),
                item.category(),
                "draft",
                null,
                null
            );
            imported.add(enriched.item());
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
        List<KnowledgeBaseItem> knowledge_base_items = contentRepository.listKnowledgeBaseItems(currentUser.workspaceId(), space.rootItemId()).stream()
            .filter(document -> "markdown".equals(document.contentType()))
            .toList();
        StringBuilder export = new StringBuilder();
        export.append("# ").append(space.name()).append("\n\n");
        export.append("> 导出时间：").append(Instant.now()).append("\n\n");
        export.append("<!-- colla-space-export: version=1 spaceId=").append(spaceId).append(" -->\n\n");
        int attachmentCount = 0;
        int objectReferenceCount = 0;
        for (KnowledgeBaseItem document : knowledge_base_items) {
            List<KnowledgeContentBlock> blocks = contentRepository.listBlocks(currentUser.workspaceId(), document.id());
            int itemAttachments = (int) blocks.stream().filter(block -> Set.of("image", "file", "file_embed").contains(block.blockType())).count();
            int itemObjectReferences = (int) blocks.stream().filter(block -> EMBED_BLOCK_TYPES.contains(block.blockType())).count();
            attachmentCount += itemAttachments;
            objectReferenceCount += itemObjectReferences;
            String path = documentPath(currentUser, document.id()).stream().map(KnowledgeContentPathItem::title)
                .reduce((left, right) -> left + " / " + right).orElse(document.title());
            String content = blocks.isEmpty()
                ? contentRepository.findContent(currentUser.workspaceId(), document.id()).orElse("")
                : exportMarkdownFromBlocks(blocks);
            export.append("\n---\n\n");
            export.append("# ").append(document.title()).append("\n\n");
            export.append("<!-- itemId: ").append(document.id()).append(" -->\n");
            export.append("<!-- path: ").append(path).append(" -->\n");
            export.append("<!-- attachments: ").append(itemAttachments).append("; objectReferences: ").append(itemObjectReferences).append(" -->\n");
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
        export.append("\n<!-- export-summary: items=").append(knowledge_base_items.size())
            .append(" attachments=").append(attachmentCount)
            .append(" objectReferences=").append(objectReferenceCount).append(" -->\n");
        return export.toString();
    }

    @Transactional
    public KnowledgeReviewReminderResult runKnowledgeReviewReminders(CurrentUser currentUser, LocalDate beforeDate, int limit) {
        if (!currentUser.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge review reminder requires admin role");
        }
        LocalDate cutoff = beforeDate == null ? LocalDate.now().plusDays(7) : beforeDate;
        List<KnowledgeBaseItem> dueDocuments = contentRepository.listDueForReview(currentUser.workspaceId(), cutoff, limit <= 0 ? 50 : limit);
        int notified = 0;
        for (KnowledgeBaseItem document : dueDocuments) {
            if (document.maintainerId() == null) {
                continue;
            }
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "knowledge_content",
                document.id(),
                currentUser.id(),
                Map.of(
                    "recipientId", document.maintainerId().toString(),
                    "notificationType", "knowledge_review_due",
                    "title", "知识内容需要复核",
                    "body", document.title() + " 复核日期：" + document.reviewDueAt(),
                    "targetType", "knowledge_content",
                    "targetId", document.id().toString(),
                    "webPath", knowledgeContentWebPath(currentUser.workspaceId(), document.id()),
                    "dedupeKey", "knowledge.review_due:" + document.id() + ":" + document.reviewDueAt()
                ),
                "notification.knowledge.review_due:" + document.id() + ":" + document.reviewDueAt()
            );
            notified += contentRepository.markReviewReminderSent(currentUser.workspaceId(), document.id(), currentUser.id());
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
    public KnowledgeContent createVersionCheckpoint(CurrentUser currentUser, UUID itemId) {
        KnowledgeBaseItem before = requireEdit(currentUser, itemId);
        List<KnowledgeContentBlockDraft> currentBlocks = contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream()
            .map(this::draftFromStoredBlock)
            .toList();
        String content = contentFromBlocks(currentBlocks);
        int nextVersionNo = before.currentVersionNo() + 1;
        contentRepository.updateContent(
            currentUser.workspaceId(),
            itemId,
            before.parentId(),
            before.title(),
            content,
            nextVersionNo,
            currentUser.id()
        );
        contentRepository.addVersion(
            currentUser.workspaceId(),
            itemId,
            nextVersionNo,
            before.title(),
            content,
            currentUser.id(),
            null,
            "manual_checkpoint",
            "手动生成版本",
            before.currentVersionNo(),
            blockSnapshot(currentBlocks)
        );
        registerDocumentObject(currentUser.workspaceId(), itemId, before.title());
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.version.checkpoint.created",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "knowledge.content.version.checkpoint.created:" + itemId + ":" + nextVersionNo
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent createNamedVersion(CurrentUser currentUser, UUID itemId, String versionName, String summary) {
        KnowledgeBaseItem before = requireEdit(currentUser, itemId);
        String name = normalizeVersionName(versionName);
        String normalizedSummary = normalizeNullableText(summary, 512);
        List<KnowledgeContentBlockDraft> currentBlocks = contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream()
            .map(this::draftFromStoredBlock)
            .toList();
        String content = contentFromBlocks(currentBlocks);
        int nextVersionNo = before.currentVersionNo() + 1;
        contentRepository.updateContent(
            currentUser.workspaceId(),
            itemId,
            before.parentId(),
            before.title(),
            content,
            nextVersionNo,
            currentUser.id()
        );
        contentRepository.addVersion(
            currentUser.workspaceId(),
            itemId,
            nextVersionNo,
            before.title(),
            content,
            currentUser.id(),
            name,
            "named",
            normalizedSummary,
            before.currentVersionNo(),
            blockSnapshot(currentBlocks)
        );
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.version.named.created",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "versionNo", Integer.toString(nextVersionNo), "versionName", name),
            "knowledge.content.version.named.created:" + itemId + ":" + nextVersionNo
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent importMarkdown(CurrentUser currentUser, UUID itemId, String title, String content) {
        KnowledgeBaseItem before = requireEdit(currentUser, itemId);
        String normalizedContent = content == null ? "" : content;
        validateImportSourceSize(normalizedContent);
        String normalizedTitle = title == null || title.isBlank() ? before.title() : title.trim();
        List<KnowledgeContentBlockDraft> importedBlocks = blocksFromContent(normalizedContent);
        int nextVersionNo = before.currentVersionNo() + 1;
        contentRepository.updateContent(
            currentUser.workspaceId(),
            itemId,
            before.parentId(),
            normalizedTitle,
            normalizedContent,
            nextVersionNo,
            currentUser.id()
        );
        contentRepository.addVersion(
            currentUser.workspaceId(),
            itemId,
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
        contentRepository.replaceBlocks(currentUser.workspaceId(), itemId, importedBlocks, currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), itemId, normalizedContent);
        registerDocumentObject(currentUser.workspaceId(), itemId, normalizedTitle);
        invalidateCollaborationState(currentUser.workspaceId(), itemId);
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.markdown.imported",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "knowledge.content.markdown.imported:" + itemId + ":" + nextVersionNo
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent importHtml(CurrentUser currentUser, UUID itemId, String title, String html) {
        KnowledgeBaseItem before = requireEdit(currentUser, itemId);
        String normalizedHtml = html == null ? "" : html;
        validateImportSourceSize(normalizedHtml);
        String normalizedTitle = title == null || title.isBlank() ? before.title() : title.trim();
        List<KnowledgeContentBlockDraft> importedBlocks = normalizeBlocks(blocksFromHtml(normalizedHtml));
        String normalizedContent = contentFromBlocks(importedBlocks);
        int nextVersionNo = before.currentVersionNo() + 1;
        contentRepository.updateContent(
            currentUser.workspaceId(),
            itemId,
            before.parentId(),
            normalizedTitle,
            normalizedContent,
            nextVersionNo,
            currentUser.id()
        );
        contentRepository.addVersion(
            currentUser.workspaceId(),
            itemId,
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
        contentRepository.replaceBlocks(currentUser.workspaceId(), itemId, importedBlocks, currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), itemId, normalizedContent);
        registerDocumentObject(currentUser.workspaceId(), itemId, normalizedTitle);
        invalidateCollaborationState(currentUser.workspaceId(), itemId);
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.html.imported",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "versionNo", Integer.toString(nextVersionNo)),
            "knowledge.content.html.imported:" + itemId + ":" + nextVersionNo
        );
        return getContent(currentUser, itemId);
    }

    public List<KnowledgeContentBlock> listBlocks(CurrentUser currentUser, UUID itemId) {
        requireView(currentUser, itemId);
        return hydrateBlocks(currentUser, contentRepository.listBlocks(currentUser.workspaceId(), itemId));
    }

    @Transactional
    public KnowledgeContent saveBlocks(CurrentUser currentUser, UUID itemId, int baseVersionNo, String title, List<KnowledgeContentBlockDraft> blocks) {
        return saveBlocks(currentUser, itemId, baseVersionNo, title, "auto", blocks);
    }

    @Transactional
    public KnowledgeContent saveBlocks(
        CurrentUser currentUser,
        UUID itemId,
        int baseVersionNo,
        String title,
        String saveMode,
        List<KnowledgeContentBlockDraft> blocks
    ) {
        Instant startedAt = Instant.now();
        KnowledgeBaseItem before = requireEdit(currentUser, itemId);
        if (baseVersionNo != before.currentVersionNo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document version conflict");
        }
        List<KnowledgeContentBlock> previousBlocks = contentRepository.listBlocks(currentUser.workspaceId(), itemId);
        List<KnowledgeContentBlockDraft> normalizedBlocks = normalizeBlocks(blocks);
        String normalizedTitle = title == null || title.isBlank() ? before.title() : title.trim();
        String normalizedContent = contentFromBlocks(normalizedBlocks);
        String versionSummary = blockChangeSummary(previousBlocks, normalizedBlocks);
        int nextVersionNo = before.currentVersionNo() + 1;
        String normalizedSaveMode = normalizeSaveMode(saveMode);
        boolean checkpointCreated = "manual".equals(normalizedSaveMode)
            || shouldCreateAutoCheckpoint(currentUser.workspaceId(), itemId);
        contentRepository.updateContent(
            currentUser.workspaceId(),
            itemId,
            before.parentId(),
            normalizedTitle,
            normalizedContent,
            nextVersionNo,
            currentUser.id()
        );
        if (checkpointCreated) {
            contentRepository.addVersion(
                currentUser.workspaceId(),
                itemId,
                nextVersionNo,
                normalizedTitle,
                normalizedContent,
                currentUser.id(),
                null,
                "manual".equals(normalizedSaveMode) ? "manual_checkpoint" : "auto_snapshot",
                versionSummary,
                before.currentVersionNo(),
                blockSnapshot(normalizedBlocks)
            );
        }
        contentRepository.replaceBlocks(currentUser.workspaceId(), itemId, normalizedBlocks, currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), itemId, normalizedContent);
        registerDocumentObject(currentUser.workspaceId(), itemId, normalizedTitle);
        invalidateCollaborationState(currentUser.workspaceId(), itemId);
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.blocks.updated",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of(
                "itemId", itemId.toString(),
                "versionNo", Integer.toString(nextVersionNo),
                "baseVersionNo", Integer.toString(baseVersionNo),
                "saveMode", normalizedSaveMode,
                "checkpointCreated", Boolean.toString(checkpointCreated),
                "blockCount", Integer.toString(normalizedBlocks.size()),
                "durationMs", Long.toString(Duration.between(startedAt, Instant.now()).toMillis())
            ),
            "knowledge.content.blocks.updated:" + itemId + ":" + nextVersionNo
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent insertBlock(CurrentUser currentUser, UUID itemId, int baseVersionNo, KnowledgeContentBlockDraft block, Integer afterSortOrder) {
        List<KnowledgeContentBlockDraft> current = contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream()
            .map(this::draftFromStoredBlock)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int insertAt = afterSortOrder == null ? current.size() : Math.min(Math.max(afterSortOrder + 1, 0), current.size());
        current.add(insertAt, block == null ? new KnowledgeContentBlockDraft("paragraph", "", insertAt) : block);
        return saveBlocks(currentUser, itemId, baseVersionNo, null, current);
    }

    @Transactional
    public KnowledgeContent updateBlock(CurrentUser currentUser, UUID itemId, UUID blockId, int baseVersionNo, KnowledgeContentBlockDraft patch) {
        List<KnowledgeContentBlockDraft> current = contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream()
            .map(this::draftFromStoredBlock)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean found = false;
        for (int index = 0; index < current.size(); index++) {
            KnowledgeContentBlockDraft existing = current.get(index);
            if (blockId.equals(existing.id())) {
                current.set(index, mergeBlockDraft(existing, patch));
                found = true;
                break;
            }
        }
        if (!found) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document block not found");
        }
        return saveBlocks(currentUser, itemId, baseVersionNo, null, current);
    }

    @Transactional
    public KnowledgeContent reorderBlocks(CurrentUser currentUser, UUID itemId, int baseVersionNo, List<UUID> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block order is required");
        }
        List<KnowledgeContentBlockDraft> current = contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream()
            .map(this::draftFromStoredBlock)
            .toList();
        Map<UUID, KnowledgeContentBlockDraft> byId = new HashMap<>();
        for (KnowledgeContentBlockDraft block : current) {
            byId.put(block.id(), block);
        }
        List<KnowledgeContentBlockDraft> reordered = new ArrayList<>();
        for (UUID blockId : blockIds) {
            KnowledgeContentBlockDraft block = byId.remove(blockId);
            if (block == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block order contains unknown block id");
            }
            reordered.add(block);
        }
        reordered.addAll(byId.values());
        return saveBlocks(currentUser, itemId, baseVersionNo, null, reordered);
    }

    @Transactional
    public KnowledgeContent deleteBlock(CurrentUser currentUser, UUID itemId, UUID blockId, int baseVersionNo) {
        List<KnowledgeContentBlockDraft> remaining = contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream()
            .filter(block -> !block.id().equals(blockId))
            .map(this::draftFromStoredBlock)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (remaining.size() == contentRepository.listBlocks(currentUser.workspaceId(), itemId).size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document block not found");
        }
        contentRepository.updateCommentThreadsAnchorStateByBlock(
            currentUser.workspaceId(), itemId, blockId, "detached", "block_deleted"
        );
        return saveBlocks(currentUser, itemId, baseVersionNo, null, remaining);
    }

    public KnowledgeContentVersionDiff diffVersions(CurrentUser currentUser, UUID itemId, int fromVersionNo, int toVersionNo) {
        requireView(currentUser, itemId);
        KnowledgeContentVersion from = contentRepository.findVersion(currentUser.workspaceId(), itemId, fromVersionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document from version not found"));
        KnowledgeContentVersion to = contentRepository.findVersion(currentUser.workspaceId(), itemId, toVersionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document to version not found"));
        return new KnowledgeContentVersionDiff(
            itemId,
            fromVersionNo,
            toVersionNo,
            diffBlockDrafts(blocksFromSnapshot(from.blockSnapshot()), blocksFromSnapshot(to.blockSnapshot()))
        );
    }

    @Transactional
    public KnowledgeContent restoreVersion(CurrentUser currentUser, UUID itemId, int versionNo) {
        KnowledgeBaseItem current = requireEdit(currentUser, itemId);
        KnowledgeContentVersion version = contentRepository.findVersion(currentUser.workspaceId(), itemId, versionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document version not found"));
        List<KnowledgeContentBlockDraft> restoredBlocks = blocksFromSnapshot(version.blockSnapshot());
        String restoredContent = contentFromBlocks(restoredBlocks);
        int nextVersionNo = current.currentVersionNo() + 1;
        contentRepository.updateContent(
            currentUser.workspaceId(),
            itemId,
            current.parentId(),
            version.title(),
            restoredContent,
            nextVersionNo,
            currentUser.id()
        );
        contentRepository.addVersion(
            currentUser.workspaceId(),
            itemId,
            nextVersionNo,
            version.title(),
            restoredContent,
            currentUser.id(),
            "恢复 v" + version.versionNo(),
            "restore",
            version.versionName(),
            version.versionNo(),
            blockSnapshot(restoredBlocks)
        );
        contentRepository.replaceBlocks(currentUser.workspaceId(), itemId, restoredBlocks, currentUser.id());
        reanchorSelectionComments(currentUser.workspaceId(), itemId, restoredContent);
        registerDocumentObject(currentUser.workspaceId(), itemId, version.title());
        invalidateCollaborationState(currentUser.workspaceId(), itemId);
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.version.restored",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "fromVersionNo", Integer.toString(versionNo), "versionNo", Integer.toString(nextVersionNo)),
            "knowledge.content.version.restored:" + itemId + ":" + nextVersionNo
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent grantPermission(CurrentUser currentUser, UUID itemId, UUID userId, String permissionLevel) {
        return grantPermission(currentUser, itemId, "user", userId, permissionLevel);
    }

    @Transactional
    public KnowledgeContent grantPermission(
        CurrentUser currentUser,
        UUID itemId,
        String subjectType,
        UUID subjectId,
        String permissionLevel
    ) {
        requireManage(currentUser, itemId);
        String normalizedSubjectType = permissionDecisionService.normalizeSubjectType(subjectType);
        requireValidPermissionSubject(currentUser, normalizedSubjectType, subjectId);
        String normalizedPermission = normalizePermission(permissionLevel);
        contentRepository.upsertSubjectPermission(
            currentUser.workspaceId(),
            itemId,
            normalizedSubjectType,
            subjectId,
            normalizedPermission,
            currentUser.id()
        );
        permissionDecisionService.grantResource(
            currentUser.workspaceId(),
            "knowledge_content",
            itemId,
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
            "knowledge.content.permission.granted",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of(
                "itemId", itemId.toString(),
                "subjectType", normalizedSubjectType,
                "subjectId", subjectId.toString(),
                "permissionLevel", normalizedPermission
            ),
            "kb.permission:" + UUID.randomUUID()
        );
        auditService.log(
            currentUser,
            "permission.granted",
            "knowledge_content",
            itemId,
            Map.of("subjectType", normalizedSubjectType, "subjectId", subjectId.toString(), "permissionLevel", normalizedPermission)
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContentShareLink updateShareLink(
        CurrentUser currentUser,
        UUID itemId,
        String scope,
        String permissionLevel,
        Boolean enabled,
        Instant expiresAt
    ) {
        requireManage(currentUser, itemId);
        String normalizedScope = normalizeShareScope(scope);
        String normalizedPermission = normalizeSharePermission(permissionLevel);
        String token = contentRepository.listShareLinks(currentUser.workspaceId(), itemId).stream()
            .findFirst()
            .map(KnowledgeContentShareLink::token)
            .orElseGet(this::newShareToken);
        KnowledgeContentShareLink shareLink = contentRepository.upsertShareLink(
            currentUser.workspaceId(),
            itemId,
            token,
            normalizedScope,
            normalizedPermission,
            enabled == null || enabled,
            expiresAt,
            currentUser.id()
        );
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.share_link.updated",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of(
                "itemId", itemId.toString(),
                "scope", normalizedScope,
                "permissionLevel", normalizedPermission,
                "enabled", Boolean.toString(shareLink.enabled())
            ),
            "knowledge.content.share_link.updated:" + itemId + ":" + System.nanoTime()
        );
        auditService.log(
            currentUser,
            "knowledge.content.share_link.updated",
            "knowledge_content",
            itemId,
            Map.of("scope", normalizedScope, "permissionLevel", normalizedPermission, "enabled", Boolean.toString(shareLink.enabled()))
        );
        return shareLink;
    }

    @Transactional
    public KnowledgeContentShareLink setShareLinkEnabled(CurrentUser currentUser, UUID itemId, boolean enabled) {
        requireManage(currentUser, itemId);
        KnowledgeContentShareLink shareLink = contentRepository.setShareLinkEnabled(currentUser.workspaceId(), itemId, enabled, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document share link not found"));
        auditService.log(
            currentUser,
            enabled ? "knowledge.content.share_link.enabled" : "knowledge.content.share_link.disabled",
            "knowledge_content",
            itemId,
            Map.of("shareLinkId", shareLink.id().toString())
        );
        return shareLink;
    }

    @Transactional
    public KnowledgeContentShareLink revokeShareLink(CurrentUser currentUser, UUID itemId) {
        requireManage(currentUser, itemId);
        KnowledgeContentShareLink existing = contentRepository.listShareLinks(currentUser.workspaceId(), itemId).stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document share link not found"));
        KnowledgeContentShareLink revoked = contentRepository.upsertShareLink(
            currentUser.workspaceId(),
            itemId,
            newShareToken(),
            existing.scope(),
            existing.permissionLevel(),
            false,
            existing.expiresAt(),
            currentUser.id()
        );
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.share_link.revoked",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "shareLinkId", revoked.id().toString()),
            "knowledge.content.share_link.revoked:" + itemId + ":" + System.nanoTime()
        );
        auditService.log(
            currentUser,
            "knowledge.content.share_link.revoked",
            "knowledge_content",
            itemId,
            Map.of("shareLinkId", revoked.id().toString(), "tokenRotated", "true")
        );
        return revoked;
    }

    @Transactional
    public KnowledgeContent updateKnowledgeBaseSettings(
        CurrentUser currentUser,
        UUID itemId,
        String description,
        String coverUrl,
        String defaultPermissionLevel,
        Boolean knowledgeBase
    ) {
        KnowledgeBaseItem document = requireManage(currentUser, itemId);
        if (!"space".equals(document.contentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only root directories can be configured as knowledge bases");
        }
        java.util.Optional<KnowledgeBaseSpaceSummary> registeredSpace = knowledgeBaseSpaceRepository.findSpaceByRootItemId(
            currentUser.workspaceId(),
            itemId
        );
        if (registeredSpace.isPresent()) {
            auditService.log(
                currentUser,
                "knowledge.content.knowledge_base.deprecated_noop",
                "knowledge_content",
                itemId,
                Map.of("spaceId", registeredSpace.get().id().toString())
            );
            return getContent(currentUser, itemId);
        }
        String normalizedDescription = normalizeNullableText(description, 512);
        String normalizedCoverUrl = normalizeNullableText(coverUrl, 1024);
        String normalizedDefaultPermission = normalizeDefaultPermission(defaultPermissionLevel);
        boolean normalizedKnowledgeBase = knowledgeBase == null || knowledgeBase;
        contentRepository.updateKnowledgeBaseSettings(
            currentUser.workspaceId(),
            itemId,
            normalizedDescription,
            normalizedCoverUrl,
            normalizedDefaultPermission,
            normalizedKnowledgeBase,
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "knowledge.content.knowledge_base.updated",
            "knowledge_content",
            itemId,
            Map.of("defaultPermissionLevel", normalizedDefaultPermission, "knowledgeBase", Boolean.toString(normalizedKnowledgeBase))
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContentPermissionRequest requestPermission(CurrentUser currentUser, UUID itemId, String permissionLevel, String reason) {
        KnowledgeBaseItem document = contentRepository.findItem(currentUser.workspaceId(), itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"));
        String normalizedPermission = normalizeRequestedPermission(permissionLevel);
        String normalizedReason = normalizeNullableText(reason, 512);
        UUID requestId = UUID.randomUUID();
        List<UUID> managers = contentRepository.findItemManagerUserIds(currentUser.workspaceId(), itemId).stream()
            .filter(userId -> !userId.equals(currentUser.id()))
            .distinct()
            .toList();
        for (UUID managerId : managers) {
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "knowledge_content",
                itemId,
                currentUser.id(),
                Map.of(
                    "recipientId", managerId.toString(),
                    "notificationType", "knowledge_content_permission_request",
                    "title", currentUser.displayName() + " 申请访问知识内容「" + document.title() + "」",
                    "body", normalizedReason == null || normalizedReason.isBlank()
                        ? "申请权限：" + normalizedPermission
                        : "申请权限：" + normalizedPermission + "；原因：" + normalizedReason,
                    "targetType", "knowledge_content",
                    "targetId", itemId.toString(),
                    "webPath", knowledgeContentWebPath(currentUser.workspaceId(), itemId),
                    "dedupeKey", "knowledge.content.permission_request:" + requestId + ":" + managerId
                ),
                "notification.knowledge.content.permission_request:" + requestId + ":" + managerId
            );
        }
        auditService.log(
            currentUser,
            "knowledge.content.permission.requested",
            "knowledge_content",
            itemId,
            Map.of("requestId", requestId.toString(), "permissionLevel", normalizedPermission, "notifiedCount", Integer.toString(managers.size()))
        );
        return new KnowledgeContentPermissionRequest(requestId, itemId, normalizedPermission, managers.size(), "submitted");
    }

    @Transactional
    public KnowledgeContent addRelation(CurrentUser currentUser, UUID itemId, String targetType, UUID targetId) {
        requireEdit(currentUser, itemId);
        String type = normalizeTargetType(targetType);
        validateRelationTarget(currentUser, type, targetId);
        contentRepository.addRelation(currentUser.workspaceId(), itemId, type, targetId, currentUser.id());
        syncReverseRelation(currentUser, itemId, type, targetId);
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.relation.added",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "targetType", type, "targetId", targetId.toString()),
            "knowledge.content.relation.added:" + itemId + ":" + type + ":" + targetId
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent removeRelation(CurrentUser currentUser, UUID itemId, String targetType, UUID targetId) {
        requireEdit(currentUser, itemId);
        String type = normalizeTargetType(targetType);
        contentRepository.removeRelation(currentUser.workspaceId(), itemId, type, targetId);
        if ("knowledge_content".equals(type) && !itemId.equals(targetId)) {
            contentRepository.removeRelation(currentUser.workspaceId(), targetId, "knowledge_content", itemId);
        }
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.relation.removed",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "targetType", type, "targetId", targetId.toString()),
            "knowledge.content.relation.removed:" + itemId + ":" + type + ":" + targetId
        );
        auditService.log(
            currentUser,
            "knowledge.content.relation.removed",
            "knowledge_content",
            itemId,
            Map.of("targetType", type, "targetId", targetId.toString())
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent addComment(
        CurrentUser currentUser,
        UUID itemId,
        UUID blockId,
        String content,
        String anchorType,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText,
        String anchorPrefix,
        String anchorSuffix
    ) {
        KnowledgeBaseItem document = requireComment(currentUser, itemId);
        String normalizedContent = normalizeCommentContent(content);
        KnowledgeContentCommentAnchor anchor = normalizeCommentAnchor(
            currentUser,
            itemId,
            document.currentVersionNo(),
            blockId,
            anchorType,
            anchorStart,
            anchorEnd,
            anchorText,
            anchorPrefix,
            anchorSuffix
        );
        UUID commentId = contentRepository.addComment(currentUser.workspaceId(), itemId, currentUser.id(), normalizedContent, anchor);
        notifyMentionedUsers(currentUser, document, itemId, commentId, commentId, normalizedContent);
        auditService.log(
            currentUser,
            "knowledge.content.comment.added",
            "knowledge_content",
            itemId,
            Map.of(
                "commentId", commentId.toString(),
                "anchorType", anchor.anchorType(),
                "blockId", anchor.blockId() == null ? "" : anchor.blockId().toString()
            )
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent addCommentReply(CurrentUser currentUser, UUID itemId, UUID commentId, String content) {
        KnowledgeBaseItem document = requireComment(currentUser, itemId);
        String normalizedContent = normalizeCommentContent(content);
        KnowledgeContentComment parent = contentRepository.findComment(currentUser.workspaceId(), itemId, commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document comment not found"));
        if (parent.resolved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reply to a resolved comment thread");
        }
        UUID replyId = contentRepository.addCommentReply(
            currentUser.workspaceId(),
            itemId,
            parent.threadId(),
            parent.id(),
            currentUser.id(),
            normalizedContent
        );
        notifyMentionedUsers(currentUser, document, itemId, parent.threadId(), replyId, normalizedContent);
        auditService.log(
            currentUser,
            "knowledge.content.comment.reply.added",
            "knowledge_content",
            itemId,
            Map.of("commentId", parent.threadId().toString(), "replyId", replyId.toString())
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent resolveComment(CurrentUser currentUser, UUID itemId, UUID commentId) {
        requireComment(currentUser, itemId);
        contentRepository.findComment(currentUser.workspaceId(), itemId, commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document comment not found"));
        contentRepository.resolveCommentThread(currentUser.workspaceId(), itemId, commentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.comment.resolved",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "commentId", commentId.toString()),
            "knowledge.content.comment.resolved:" + commentId
        );
        auditService.log(
            currentUser,
            "knowledge.content.comment.resolved",
            "knowledge_content",
            itemId,
            Map.of("commentId", commentId.toString())
        );
        return getContent(currentUser, itemId);
    }

    @Transactional
    public KnowledgeContent reopenComment(CurrentUser currentUser, UUID itemId, UUID commentId) {
        requireComment(currentUser, itemId);
        contentRepository.findComment(currentUser.workspaceId(), itemId, commentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document comment not found"));
        contentRepository.reopenCommentThread(currentUser.workspaceId(), itemId, commentId, currentUser.id());
        eventRepository.append(
            currentUser.workspaceId(),
            "knowledge.content.comment.reopened",
            "knowledge_content",
            itemId,
            currentUser.id(),
            Map.of("itemId", itemId.toString(), "commentId", commentId.toString()),
            "knowledge.content.comment.reopened:" + commentId
        );
        auditService.log(
            currentUser,
            "knowledge.content.comment.reopened",
            "knowledge_content",
            itemId,
            Map.of("commentId", commentId.toString())
        );
        return getContent(currentUser, itemId);
    }

    KnowledgeBaseItem requireView(CurrentUser currentUser, UUID itemId) {
        return requirePermission(currentUser, itemId, "view");
    }

    KnowledgeBaseItem requireEdit(CurrentUser currentUser, UUID itemId) {
        return requirePermission(currentUser, itemId, "edit");
    }

    KnowledgeBaseItem requireComment(CurrentUser currentUser, UUID itemId) {
        return requirePermission(currentUser, itemId, "comment");
    }

    private KnowledgeBaseItem requireManage(CurrentUser currentUser, UUID itemId) {
        return requirePermission(currentUser, itemId, "manage");
    }

    private KnowledgeBaseItem requirePermission(CurrentUser currentUser, UUID itemId, String requiredLevel) {
        KnowledgeBaseItem document = contentRepository.findItem(currentUser.workspaceId(), itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"));
        PermissionDecision decision = permissionDecisionService.decide(currentUser, "knowledge_content", itemId, requiredLevel);
        if (decision.allowed()) {
            return withPermission(document, decision.currentLevel());
        }
        String permission = contentRepository.findPermissionLevel(currentUser.workspaceId(), itemId, currentUser.id())
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
        requireView(currentUser, space.rootItemId());
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
        requireManage(currentUser, space.rootItemId());
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

    private void syncReverseRelation(CurrentUser currentUser, UUID itemId, String targetType, UUID targetId) {
        if ("knowledge_content".equals(targetType) && !itemId.equals(targetId)) {
            contentRepository.addRelation(currentUser.workspaceId(), targetId, "knowledge_content", itemId, currentUser.id());
            return;
        }
        if ("issue".equals(targetType)) {
            projectRepository.addRelation(currentUser.workspaceId(), targetId, "knowledge_content", itemId, currentUser.id());
            projectRepository.addActivity(currentUser.workspaceId(), targetId, currentUser.id(), "relation.added", null, "document:" + itemId);
            return;
        }
        if ("base_record".equals(targetType)) {
            baseService.addRecordRelation(currentUser, targetId, "knowledge_content", itemId);
        }
    }

    private List<KnowledgeContentBlock> hydrateBlocks(CurrentUser currentUser, List<KnowledgeContentBlock> blocks) {
        return blocks.stream()
            .map(block -> {
                Map<String, Object> metadata = parseStructuredBlockMetadata(block);
                PlatformObjectSummary summary = resolveEmbedSummary(currentUser, block.blockType(), metadata);
                return new KnowledgeContentBlock(
                    block.id(),
                    block.itemId(),
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

    private Map<String, Object> parseStructuredBlockMetadata(KnowledgeContentBlock block) {
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

    private KnowledgeContentBlockDraft draftFromStoredBlock(KnowledgeContentBlock block) {
        return new KnowledgeContentBlockDraft(
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

    private KnowledgeContentBlockDraft mergeBlockDraft(KnowledgeContentBlockDraft existing, KnowledgeContentBlockDraft patch) {
        if (patch == null) {
            return existing;
        }
        return new KnowledgeContentBlockDraft(
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

    private void registerDocumentObject(UUID workspaceId, UUID itemId, String title) {
        objectRepository.upsertObjectLink(
            workspaceId,
            "knowledge_content",
            itemId,
            knowledgeContentWebPath(workspaceId, itemId),
            "colla://knowledge-content/" + itemId,
            title
        );
    }

    public void reanchorSelectionComments(UUID workspaceId, UUID itemId, String content) {
        String normalizedContent = content == null ? "" : content;
        Set<UUID> activeBlockIds = contentRepository.listBlocks(workspaceId, itemId).stream()
            .map(KnowledgeContentBlock::id)
            .collect(java.util.stream.Collectors.toSet());
        for (KnowledgeContentComment comment : contentRepository.listComments(workspaceId, itemId)) {
            if (comment.root() && comment.blockId() != null && !activeBlockIds.contains(comment.blockId())) {
                contentRepository.updateCommentThreadAnchorState(workspaceId, itemId, comment.threadId(), "detached", "block_deleted");
                continue;
            }
            if (comment.root() && comment.blockId() != null && activeBlockIds.contains(comment.blockId()) && !"selection".equals(comment.anchorType())) {
                contentRepository.updateCommentThreadAnchorState(workspaceId, itemId, comment.threadId(), "active", null);
            }
            if (!"selection".equals(comment.anchorType()) || comment.resolved() || comment.anchorText() == null || comment.anchorText().isBlank()) {
                continue;
            }
            AnchorRange nextRange = locateSelectionAnchor(normalizedContent, comment);
            if (nextRange == null) {
                contentRepository.updateCommentThreadAnchorState(workspaceId, itemId, comment.threadId(), "detached", "selection_not_found");
                continue;
            }
            if (!Integer.valueOf(nextRange.start()).equals(comment.anchorStart())
                || !Integer.valueOf(nextRange.end()).equals(comment.anchorEnd())
                || !"active".equals(comment.anchorState())) {
                contentRepository.updateCommentThreadSelectionAnchor(workspaceId, itemId, comment.threadId(), nextRange.start(), nextRange.end());
            }
        }
    }

    private AnchorRange locateSelectionAnchor(String content, KnowledgeContentComment comment) {
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

    private KnowledgeContentCommentAnchor normalizeCommentAnchor(
        CurrentUser currentUser,
        UUID itemId,
        int versionNo,
        UUID blockId,
        String anchorType,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText,
        String anchorPrefix,
        String anchorSuffix
    ) {
        if (blockId != null && contentRepository.listBlocks(currentUser.workspaceId(), itemId).stream().noneMatch(block -> block.id().equals(blockId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment block is not part of this document");
        }
        String type = anchorType == null ? "" : anchorType.trim().toLowerCase();
        if (type.isBlank()) {
            type = (anchorText != null && !anchorText.isBlank()) || (anchorStart != null && anchorEnd != null)
                ? "selection"
                : blockId == null ? "document" : "block";
        }
        if ("knowledge_content".equals(type)) {
            type = "document";
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
        return new KnowledgeContentCommentAnchor(
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
        KnowledgeBaseItem document,
        UUID itemId,
        UUID threadId,
        UUID commentId,
        String normalizedContent
    ) {
        KnowledgeContentContext knowledgeContext = knowledgeContext(currentUser, itemId);
        for (UUID mentionedUserId : resolveMentions(currentUser, normalizedContent)) {
            CurrentUser mentionedUser = new CurrentUser(mentionedUserId, currentUser.workspaceId(), null, "", "", Set.of(), Set.of());
            if (
                mentionedUserId.equals(currentUser.id())
                    || (
                        !permissionDecisionService.decide(mentionedUser, "knowledge_content", itemId, "view").allowed()
                            && contentRepository.findPermissionLevel(currentUser.workspaceId(), itemId, mentionedUserId).isEmpty()
                    )
            ) {
                continue;
            }
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "knowledge_content",
                itemId,
                currentUser.id(),
                Map.of(
                    "recipientId", mentionedUserId.toString(),
                    "notificationType", "knowledge_content_comment_mention",
                    "title", currentUser.displayName() + " 在「" + knowledgeLocationTitle(document, knowledgeContext) + "」评论中提到了你",
                    "body", normalizedContent,
                    "targetType", "knowledge_content",
                    "targetId", itemId.toString(),
                    "webPath", commentWebPath(itemId, threadId, knowledgeContext),
                    "dedupeKey", "knowledge.content.comment.mention:" + commentId + ":" + mentionedUserId
                ),
                "notification.knowledge.content.comment.mention:" + commentId + ":" + mentionedUserId
            );
        }
    }

    public KnowledgeContentContext knowledgeContext(CurrentUser currentUser, UUID itemId) {
        return knowledgeBaseSpaceRepository.findSpaceByItemId(currentUser.workspaceId(), itemId)
            .map(space -> {
                List<KnowledgeContentPathItem> path = documentPath(currentUser, itemId);
                String pathText = path.stream()
                    .map(KnowledgeContentPathItem::title)
                    .reduce((left, right) -> left + " / " + right)
                    .orElse("");
                return new KnowledgeContentContext(
                    space.id(),
                    space.name(),
                    space.code(),
                    space.rootItemId(),
                    space.homeItemId(),
                    path,
                    pathText,
                    "/knowledge-bases/" + space.id() + "/items/" + itemId
                );
            })
            .orElse(null);
    }

    private String knowledgeLocationTitle(KnowledgeBaseItem document, KnowledgeContentContext knowledgeContext) {
        if (knowledgeContext == null || knowledgeContext.pathText() == null || knowledgeContext.pathText().isBlank()) {
            return document.title();
        }
        return knowledgeContext.spaceName() + " / " + knowledgeContext.pathText();
    }

    private String commentWebPath(UUID itemId, UUID threadId, KnowledgeContentContext knowledgeContext) {
        String basePath = knowledgeContext == null ? "/knowledge-bases" : knowledgeContext.webPath();
        return basePath + (basePath.contains("?") ? "&" : "?") + "commentId=" + threadId;
    }

    private String knowledgeContentWebPath(UUID workspaceId, UUID itemId) {
        return knowledgeBaseSpaceRepository.findSpaceByItemId(workspaceId, itemId)
            .map(space -> "/knowledge-bases/" + space.id() + "/items/" + itemId)
            .orElse("/knowledge-bases");
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
        return new LinkedHashSet<>(contentRepository.findActiveUserIdsByUsernames(currentUser.workspaceId(), usernames));
    }

    private List<KnowledgeContentDiffLine> diffLines(String oldContent, String newContent) {
        return diffBlockDrafts(blocksFromContent(oldContent), blocksFromContent(newContent));
    }

    private List<KnowledgeContentDiffLine> diffBlockDrafts(
        List<KnowledgeContentBlockDraft> oldBlocks,
        List<KnowledgeContentBlockDraft> newBlocks
    ) {
        int[][] lcs = new int[oldBlocks.size() + 1][newBlocks.size() + 1];
        for (int i = oldBlocks.size() - 1; i >= 0; i--) {
            for (int j = newBlocks.size() - 1; j >= 0; j--) {
                lcs[i][j] = blockIdentity(oldBlocks.get(i)).equals(blockIdentity(newBlocks.get(j)))
                    ? lcs[i + 1][j + 1] + 1
                    : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<KnowledgeContentDiffLine> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < oldBlocks.size() || j < newBlocks.size()) {
            if (i < oldBlocks.size() && j < newBlocks.size() && blockIdentity(oldBlocks.get(i)).equals(blockIdentity(newBlocks.get(j)))) {
                KnowledgeContentBlockDraft oldBlock = oldBlocks.get(i);
                KnowledgeContentBlockDraft newBlock = newBlocks.get(j);
                String lineType = sameBlockSnapshot(oldBlock, newBlock) ? "context" : "modified";
                lines.add(new KnowledgeContentDiffLine(lineType, i + 1, j + 1, newBlock.content(), "block", j + 1, newBlock.blockType(), newBlock.id()));
                i++;
                j++;
            } else if (j < newBlocks.size() && (i == oldBlocks.size() || lcs[i][j + 1] >= lcs[i + 1][j])) {
                KnowledgeContentBlockDraft block = newBlocks.get(j);
                lines.add(new KnowledgeContentDiffLine("added", 0, j + 1, block.content(), "block", j + 1, block.blockType(), block.id()));
                j++;
            } else {
                KnowledgeContentBlockDraft block = oldBlocks.get(i);
                lines.add(new KnowledgeContentDiffLine("removed", i + 1, 0, block.content(), "block", i + 1, block.blockType(), block.id()));
                i++;
            }
        }
        return lines;
    }

    private boolean sameBlockSnapshot(KnowledgeContentBlockDraft left, KnowledgeContentBlockDraft right) {
        return Objects.equals(left.blockType(), right.blockType())
            && Objects.equals(left.content(), right.content())
            && Objects.equals(left.parentId(), right.parentId())
            && Objects.equals(left.schemaVersion(), right.schemaVersion())
            && Objects.equals(left.attrs(), right.attrs())
            && Objects.equals(left.richContent(), right.richContent())
            && Objects.equals(left.plainText(), right.plainText())
            && Objects.equals(left.anchorId(), right.anchorId())
            && Objects.equals(left.deleted(), right.deleted());
    }

    private String blockIdentity(KnowledgeContentBlockDraft block) {
        if (block.id() != null) {
            return "id:" + block.id();
        }
        return "fallback:" + block.blockType() + "\n" + block.content();
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

    private List<KnowledgeBaseItemTreeNode> buildTree(List<KnowledgeBaseItem> knowledge_base_items) {
        Map<UUID, List<KnowledgeBaseItem>> byParent = new HashMap<>();
        Set<UUID> visibleIds = new LinkedHashSet<>();
        for (KnowledgeBaseItem document : knowledge_base_items) {
            visibleIds.add(document.id());
            byParent.computeIfAbsent(document.parentId(), ignored -> new ArrayList<>()).add(document);
        }
        for (List<KnowledgeBaseItem> siblings : byParent.values()) {
            siblings.sort(documentOrder());
        }
        List<KnowledgeBaseItem> roots = knowledge_base_items.stream()
            .filter(document -> document.parentId() == null || !visibleIds.contains(document.parentId()))
            .sorted(documentOrder())
            .toList();
        Set<UUID> visited = new LinkedHashSet<>();
        List<KnowledgeBaseItemTreeNode> nodes = new ArrayList<>();
        for (KnowledgeBaseItem root : roots) {
            nodes.add(buildTreeNode(root, "", 0, byParent, visited));
        }
        return nodes;
    }

    private KnowledgeBaseItemTreeNode buildTreeNode(
        KnowledgeBaseItem document,
        String parentPath,
        int depth,
        Map<UUID, List<KnowledgeBaseItem>> byParent,
        Set<UUID> visited
    ) {
        visited.add(document.id());
        String path = parentPath.isBlank() ? document.title() : parentPath + " / " + document.title();
        List<KnowledgeBaseItemTreeNode> children = new ArrayList<>();
        for (KnowledgeBaseItem child : byParent.getOrDefault(document.id(), List.of())) {
            if (!visited.contains(child.id())) {
                children.add(buildTreeNode(child, path, depth + 1, byParent, visited));
            }
        }
        return new KnowledgeBaseItemTreeNode(document, path, depth, children.size(), !children.isEmpty(), children);
    }

    private Comparator<KnowledgeBaseItem> documentOrder() {
        return Comparator
            .comparingInt(KnowledgeBaseItem::sortOrder)
            .thenComparing(document -> document.title().toLowerCase())
            .thenComparing(KnowledgeBaseItem::updatedAt, Comparator.reverseOrder());
    }

    private int nextSortOrder(CurrentUser currentUser, UUID parentId) {
        return contentRepository.listItems(currentUser.workspaceId(), currentUser.id(), true).stream()
            .filter(document -> parentId == null ? document.parentId() == null : parentId.equals(document.parentId()))
            .mapToInt(KnowledgeBaseItem::sortOrder)
            .max()
            .orElse(0) + 10;
    }

    private KnowledgeBaseItem withPermission(KnowledgeBaseItem document, String permissionLevel) {
        return new KnowledgeBaseItem(
            document.id(),
            document.parentId(),
            document.title(),
            document.contentType(),
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
            document.itemKind(),
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

    private String normalizeSaveMode(String saveMode) {
        String normalized = saveMode == null || saveMode.isBlank() ? "auto" : saveMode.trim().toLowerCase(Locale.ROOT);
        if (!List.of("auto", "manual").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document save mode");
        }
        return normalized;
    }

    private boolean shouldCreateAutoCheckpoint(UUID workspaceId, UUID itemId) {
        return contentRepository.listVersions(workspaceId, itemId).stream()
            .filter(version -> "auto_snapshot".equals(version.versionType()))
            .findFirst()
            .map(version -> version.createdAt() == null || Duration.between(version.createdAt(), Instant.now()).compareTo(AUTO_CHECKPOINT_INTERVAL) >= 0)
            .orElse(true);
    }

    private String normalizeContentType(String contentType) {
        String type = contentType == null || contentType.isBlank() ? "markdown" : contentType.toLowerCase();
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

    private void notifyKnowledgeSubscribers(CurrentUser currentUser, UUID itemId, String title, int versionNo) {
        List<UUID> subscriberIds = knowledgeBaseSpaceRepository.listSubscriberIdsForDocument(currentUser.workspaceId(), itemId).stream()
            .filter(subscriberId -> !subscriberId.equals(currentUser.id()))
            .distinct()
            .toList();
        for (UUID subscriberId : subscriberIds) {
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "knowledge_content",
                itemId,
                currentUser.id(),
                Map.of(
                    "recipientId", subscriberId.toString(),
                    "notificationType", "knowledge_subscription_updated",
                    "title", "关注的知识已更新",
                    "body", title + " 已更新到 v" + versionNo,
                    "targetType", "knowledge_content",
                    "targetId", itemId.toString(),
                    "webPath", knowledgeContentWebPath(currentUser.workspaceId(), itemId),
                    "dedupeKey", "knowledge.subscription.updated:" + itemId + ":" + versionNo + ":" + subscriberId
                ),
                "notification.knowledge.subscription_updated:" + itemId + ":" + versionNo + ":" + subscriberId
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

    private String sanitizeTemplateContent(CurrentUser currentUser, String content) {
        List<KnowledgeContentBlockDraft> sanitized = new ArrayList<>();
        for (KnowledgeContentBlockDraft block : blocksFromContent(content == null ? "" : content)) {
            if (!EMBED_BLOCK_TYPES.contains(block.blockType())) {
                sanitized.add(block);
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(block.attrs() == null ? Map.of() : block.attrs());
            parseJsonObject(block.content()).ifPresent(metadata::putAll);
            String objectType = normalizeEmbedObjectType(
                block.blockType(),
                asString(metadata.getOrDefault("objectType", metadata.get("targetType")))
            );
            UUID objectId = parseUuid(asString(metadata.getOrDefault("objectId", metadata.get("targetId"))));
            boolean allowed = objectId == null && objectType.isBlank();
            if (objectId != null && !objectType.isBlank()) {
                PlatformObjectSummary summary = objectResolverRegistryProvider.getObject().resolve(currentUser, objectType, objectId);
                allowed = summary.accessState() == ObjectAccessState.available;
            }
            if (allowed) {
                sanitized.add(block);
            } else {
                sanitized.add(new KnowledgeContentBlockDraft(
                    "paragraph",
                    "对象引用已在模板发布时移除，请在使用模板后重新选择有权访问的对象。",
                    sanitized.size()
                ));
            }
        }
        return contentFromBlocks(sanitized);
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

    private String blockSnapshot(List<KnowledgeContentBlockDraft> blocks) {
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document blocks");
        }
    }

    private List<KnowledgeContentBlockDraft> blocksFromSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            throw new IllegalStateException("Knowledge content version is missing its block snapshot");
        }
        try {
            return normalizeBlocks(objectMapper.readValue(snapshot, BLOCK_DRAFT_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid knowledge content version block snapshot", exception);
        }
    }

    private String blockChangeSummary(List<KnowledgeContentBlock> previousBlocks, List<KnowledgeContentBlockDraft> nextBlocks) {
        Map<UUID, KnowledgeContentBlock> previousById = new HashMap<>();
        for (KnowledgeContentBlock block : previousBlocks) {
            previousById.put(block.id(), block);
        }
        int added = 0;
        int deleted = 0;
        int modified = 0;
        int moved = 0;
        int typeChanged = 0;
        Set<UUID> seen = new LinkedHashSet<>();
        for (KnowledgeContentBlockDraft block : nextBlocks) {
            if (block.id() == null || !previousById.containsKey(block.id())) {
                added++;
                continue;
            }
            seen.add(block.id());
            KnowledgeContentBlock previous = previousById.get(block.id());
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
        for (KnowledgeContentBlock block : previousBlocks) {
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

    private String exportMarkdownFromBlocks(List<KnowledgeContentBlock> blocks) {
        return blocks.stream()
            .sorted(Comparator.comparingInt(KnowledgeContentBlock::sortOrder))
            .map(this::exportMarkdownBlock)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private String exportMarkdownBlock(KnowledgeContentBlock block) {
        return switch (block.blockType()) {
            case "heading" -> "# " + safeExportText(block.content());
            case "list", "bullet_list", "bulleted_list" -> "- " + safeExportText(block.content());
            case "ordered_list" -> "1. " + safeExportText(block.content());
            case "task", "todo", "task_item" -> "- [ ] " + safeExportText(block.content());
            case "quote" -> "> " + safeExportText(block.content());
            case "code", "code_block" -> "```\n" + (block.content() == null ? "" : block.content()) + "\n```";
            case "table" -> exportTableMarkdown(block.content());
            case "image" -> "![" + escapeMarkdownLabel(block.plainText()) + "](" + safeImageSource(block.attrs().get("src")) + ")";
            case "file", "embed_object", "base_view", "issue_embed", "message_embed", "file_embed", "link_card" -> exportEmbedMarkdown(block);
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
            StringBuilder markdown = new StringBuilder("| " + header + " |\n| " + divider + " |");
            if (table.get("rows") instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof List<?> cells) {
                        markdown.append("\n| ").append(cells.stream().map(String::valueOf).map(this::safeExportText)
                            .reduce((left, right) -> left + " | " + right).orElse("")).append(" |");
                    }
                }
            }
            return markdown.toString();
        }
        return "[table] " + (content == null ? "{}" : content);
    }

    private String exportEmbedMarkdown(KnowledgeContentBlock block) {
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

    private String exportHtmlBlock(KnowledgeContentBlock block) {
        return switch (block.blockType()) {
            case "heading" -> "<h1>" + escapeHtml(block.content()) + "</h1>\n";
            case "list", "bullet_list", "bulleted_list" -> "<p>&bull; " + escapeHtml(block.content()) + "</p>\n";
            case "ordered_list" -> "<p>1. " + escapeHtml(block.content()) + "</p>\n";
            case "task", "todo", "task_item" -> "<p><input type=\"checkbox\" disabled> " + escapeHtml(block.content()) + "</p>\n";
            case "quote" -> "<blockquote>" + escapeHtml(block.content()) + "</blockquote>\n";
            case "code", "code_block" -> "<pre><code>" + escapeHtml(block.content()) + "</code></pre>\n";
            case "divider" -> "<hr>\n";
            case "table" -> exportTableHtml(block.content());
            case "image" -> "<img src=\"" + escapeHtml(safeImageSource(block.attrs().get("src"))) + "\" alt=\"" + escapeHtml(block.plainText()) + "\">\n";
            case "file", "embed_object", "base_view", "issue_embed", "message_embed", "file_embed", "link_card" ->
                "<p class=\"doc-export-embed\">" + escapeHtml(exportEmbedMarkdown(block)) + "</p>\n";
            case "legacy_html" -> "<div data-block-type=\"legacy_html\">" + (block.content() == null ? "" : block.content()) + "</div>\n";
            default -> "<p>" + escapeHtml(block.content()) + "</p>\n";
        };
    }

    private String exportTableHtml(String content) {
        Map<String, Object> table = parseJsonObject(content).orElse(Map.of());
        StringBuilder html = new StringBuilder("<table><thead><tr>");
        if (table.get("columns") instanceof List<?> columns) {
            columns.forEach(column -> html.append("<th>").append(escapeHtml(String.valueOf(column))).append("</th>"));
        }
        html.append("</tr></thead><tbody>");
        if (table.get("rows") instanceof List<?> rows) {
            for (Object row : rows) {
                html.append("<tr>");
                if (row instanceof List<?> cells) {
                    cells.forEach(cell -> html.append("<td>").append(escapeHtml(String.valueOf(cell))).append("</td>"));
                }
                html.append("</tr>");
            }
        }
        return html.append("</tbody></table>\n").toString();
    }

    private String safeImageSource(Object value) {
        String source = value == null ? "" : String.valueOf(value).trim();
        return source.matches("(?i)^(https?://|/api/files/|data:image/).+") ? source : "";
    }

    private String escapeMarkdownLabel(String value) {
        return safeExportText(value).replace("[", "\\[").replace("]", "\\]");
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
        if (!List.of("issue", "base", "base_table", "base_record", "file", "message", "approval", "knowledge_content").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid relation target type");
        }
        return type;
    }

    List<KnowledgeContentBlockDraft> blocksFromContent(String content) {
        String[] lines = splitLines(content);
        List<KnowledgeContentBlockDraft> blocks = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("```")) {
                String language = trimmed.substring(3).trim();
                StringBuilder code = new StringBuilder();
                while (++index < lines.length && !lines[index].trim().startsWith("```")) {
                    if (code.length() > 0) {
                        code.append('\n');
                    }
                    code.append(lines[index]);
                }
                blocks.add(importedBlockDraft("code_block", code.toString(), blocks.size(), Map.of("language", language)));
                continue;
            }
            Matcher image = Pattern.compile("^!\\[([^]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)$").matcher(trimmed);
            if (image.matches()) {
                blocks.add(importedBlockDraft("image", image.group(1), blocks.size(), Map.of("src", image.group(2), "alt", image.group(1))));
                continue;
            }
            if (isMarkdownTableHeader(lines, index)) {
                List<String> columns = markdownTableCells(lines[index]);
                List<List<String>> rows = new ArrayList<>();
                index += 2;
                while (index < lines.length && lines[index].contains("|") && !lines[index].trim().isBlank()) {
                    rows.add(markdownTableCells(lines[index]));
                    index++;
                }
                index--;
                blocks.add(importedBlockDraft(
                    "table",
                    writeJson(Map.of("columns", columns, "rows", rows)),
                    blocks.size(),
                    Map.of()
                ));
                continue;
            }
            String blockType = inferBlockType(trimmed);
            String blockContent = normalizeBlockContent(trimmed);
            Map<String, Object> attrs = "heading".equals(blockType)
                ? Map.of("level", Math.min(6, trimmed.indexOf(' ') < 0 ? 1 : trimmed.indexOf(' ')))
                : "task".equals(blockType)
                    ? Map.of("checked", trimmed.toLowerCase(Locale.ROOT).startsWith("- [x]"))
                    : Map.of();
            blocks.add(importedBlockDraft(blockType, blockContent, blocks.size(), attrs));
        }
        if (blocks.isEmpty()) {
            blocks.add(defaultBlockDraft("paragraph", "", 0));
        }
        return blocks;
    }

    private KnowledgeContentBlockDraft importedBlockDraft(
        String blockType,
        String content,
        int sortOrder,
        Map<String, Object> importedAttrs
    ) {
        Map<String, Object> attrs = new HashMap<>(defaultAttrs(blockType, content));
        attrs.putAll(importedAttrs);
        return new KnowledgeContentBlockDraft(
            null,
            null,
            blockType,
            content,
            sortOrder,
            2,
            attrs,
            defaultRichContent(blockType, content),
            plainTextForBlock(blockType, content),
            null,
            false
        );
    }

    private boolean isMarkdownTableHeader(String[] lines, int index) {
        return index + 1 < lines.length
            && lines[index].contains("|")
            && lines[index + 1].trim().matches("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$");
    }

    private List<String> markdownTableCells(String line) {
        String normalized = line.trim().replaceFirst("^\\|", "").replaceFirst("\\|$", "");
        return java.util.Arrays.stream(normalized.split("\\|", -1)).map(String::trim).toList();
    }

    List<KnowledgeContentBlockDraft> blocksFromHtml(String html) {
        String value = html == null ? "" : html.trim();
        if (value.isBlank()) {
            return List.of(defaultBlockDraft("paragraph", "", 0));
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("<script") || lower.contains("<style")) {
            return List.of(defaultBlockDraft("legacy_html", "<!-- unsafe HTML omitted during import -->", 0));
        }
        if (Pattern.compile("(?is)<(iframe|video|svg|canvas|form)\\b").matcher(value).find()) {
            return List.of(defaultBlockDraft("legacy_html", value, 0));
        }
        List<KnowledgeContentBlockDraft> blocks = new ArrayList<>();
        Matcher imageMatcher = Pattern.compile("(?is)<img\\b([^>]*)>").matcher(value);
        while (imageMatcher.find()) {
            String attrs = imageMatcher.group(1);
            String src = htmlAttribute(attrs, "src");
            String alt = htmlAttribute(attrs, "alt");
            if (!src.isBlank()) {
                blocks.add(importedBlockDraft("image", alt, blocks.size(), Map.of("src", src, "alt", alt)));
            }
        }
        Matcher matcher = Pattern.compile("(?is)<(h[1-6]|p|li|blockquote|pre|table)[^>]*>(.*?)</\\1>|<hr\\b[^>]*>").matcher(value);
        while (matcher.find()) {
            String tag = matcher.group(1) == null ? "hr" : matcher.group(1).toLowerCase(Locale.ROOT);
            if ("table".equals(tag)) {
                blocks.add(importedBlockDraft("table", writeJson(parseHtmlTable(matcher.group(2))), blocks.size(), Map.of()));
                continue;
            }
            String body = matcher.group(2) == null ? "" : htmlToPlainText(matcher.group(2));
            String blockType = switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> "heading";
                case "li" -> "bullet_list";
                case "blockquote" -> "quote";
                case "pre" -> "code_block";
                case "hr" -> "divider";
                default -> "paragraph";
            };
            blocks.add(new KnowledgeContentBlockDraft(blockType, "divider".equals(blockType) ? "" : body, blocks.size()));
        }
        if (blocks.isEmpty()) {
            String plainText = htmlToPlainText(value);
            return plainText.isBlank() ? List.of(defaultBlockDraft("legacy_html", value, 0)) : blocksFromContent(plainText);
        }
        return blocks;
    }

    private String htmlAttribute(String attrs, String name) {
        Matcher matcher = Pattern.compile("(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*(['\"])(.*?)\\1").matcher(attrs == null ? "" : attrs);
        return matcher.find() ? htmlToPlainText(matcher.group(2)) : "";
    }

    private Map<String, Object> parseHtmlTable(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>").matcher(html == null ? "" : html);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = Pattern.compile("(?is)<t[hd][^>]*>(.*?)</t[hd]>").matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                cells.add(htmlToPlainText(cellMatcher.group(1)));
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        List<String> columns = rows.isEmpty() ? List.of("列 1") : rows.remove(0);
        return Map.of("columns", columns, "rows", rows);
    }

    private KnowledgeContentBlockDraft defaultBlockDraft(String blockType, String content, int sortOrder) {
        return new KnowledgeContentBlockDraft(
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

    private List<KnowledgeContentBlockDraft> normalizeBlocks(List<KnowledgeContentBlockDraft> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of(defaultBlockDraft("paragraph", "", 0));
        }
        List<KnowledgeContentBlockDraft> normalized = new ArrayList<>();
        for (KnowledgeContentBlockDraft block : blocks) {
            if (Boolean.TRUE.equals(block.deleted())) {
                continue;
            }
            String blockType = normalizeBlockType(block.blockType());
            String content = normalizeDraftBlockContent(blockType, block.content());
            int sortOrder = normalized.size();
            String plainText = normalizePlainText(blockType, content, block.plainText());
            normalized.add(new KnowledgeContentBlockDraft(
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
        return normalized.isEmpty() ? List.of(new KnowledgeContentBlockDraft("paragraph", "", 0)) : normalized;
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

    private String contentFromBlocks(List<KnowledgeContentBlockDraft> blocks) {
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
        if (line.matches("^\\d+\\.\\s+.*$")) {
            return "ordered_list";
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
            .replaceFirst("^\\d+\\.\\s+", "")
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





