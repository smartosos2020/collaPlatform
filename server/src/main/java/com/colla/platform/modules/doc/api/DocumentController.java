package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.application.DocumentCrossModuleService;
import com.colla.platform.modules.doc.application.DocumentCollaborationService;
import com.colla.platform.modules.doc.application.DocumentService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentAcceptanceReport;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentCollaborationHealth;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPathItem;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentMigrationPreview;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPerformanceProfile;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPermissionRequest;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentShareLink;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTemplate;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTreeNode;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersionDiff;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseMarkdownImportItem;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseMarkdownImportResult;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeReviewReminderResult;
import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentCollaborationService documentCollaborationService;
    private final DocumentCrossModuleService documentCrossModuleService;
    private final DocumentService documentService;

    public DocumentController(
        DocumentCollaborationService documentCollaborationService,
        DocumentCrossModuleService documentCrossModuleService,
        DocumentService documentService
    ) {
        this.documentCollaborationService = documentCollaborationService;
        this.documentCrossModuleService = documentCrossModuleService;
        this.documentService = documentService;
    }

    @GetMapping("/docs")
    public List<DocumentSummary> listDocuments(
        @RequestParam(defaultValue = "false") boolean includeArchived,
        Authentication authentication
    ) {
        return documentService.listDocuments(currentUser(authentication), includeArchived);
    }

    @GetMapping("/docs/tree")
    public List<DocumentTreeNode> documentTree(
        @RequestParam(defaultValue = "false") boolean includeArchived,
        Authentication authentication
    ) {
        return documentService.listDocumentTree(currentUser(authentication), includeArchived);
    }

    @GetMapping("/docs/templates")
    public List<DocumentTemplate> templates(@RequestParam(required = false) UUID knowledgeBaseId, Authentication authentication) {
        return documentService.listTemplates(currentUser(authentication), knowledgeBaseId);
    }

    @PostMapping("/docs/templates")
    public DocumentTemplate createTemplate(
        @Valid @RequestBody CreateDocumentTemplateRequest request,
        Authentication authentication
    ) {
        return documentService.createTemplate(
            currentUser(authentication),
            request.knowledgeBaseId(),
            request.title(),
            request.description(),
            request.category(),
            request.content()
        );
    }

    @GetMapping("/docs/acceptance/v1")
    public DocumentAcceptanceReport acceptanceReport(Authentication authentication) {
        return documentService.acceptanceReport(currentUser(authentication));
    }

    @PostMapping("/docs")
    public DocumentDetail createDocument(@Valid @RequestBody CreateDocumentRequest request, Authentication authentication) {
        return documentService.createDocument(
            currentUser(authentication),
            request.parentId(),
            request.title(),
            request.docType(),
            request.content(),
            request.description(),
            request.coverUrl(),
            request.defaultPermissionLevel(),
            request.knowledgeBase()
        );
    }

    @PostMapping("/docs/from-template")
    public DocumentDetail createDocumentFromTemplate(
        @Valid @RequestBody CreateDocumentFromTemplateRequest request,
        Authentication authentication
    ) {
        return documentService.createFromTemplate(currentUser(authentication), request.templateId(), request.parentId(), request.title());
    }

    @GetMapping("/docs/{documentId}")
    public DocumentDetail document(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.getDocument(currentUser(authentication), documentId);
    }

    @GetMapping("/docs/{documentId}/performance")
    public DocumentPerformanceProfile performanceProfile(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.performanceProfile(currentUser(authentication), documentId);
    }

    @GetMapping("/docs/{documentId}/migration-preview")
    public DocumentMigrationPreview migrationPreview(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.migrationPreview(currentUser(authentication), documentId);
    }

    @GetMapping("/docs/{documentId}/collaboration/health")
    public DocumentCollaborationHealth collaborationHealth(@PathVariable UUID documentId, Authentication authentication) {
        return documentCollaborationService.health(currentUser(authentication), documentId);
    }

    @GetMapping("/docs/{documentId}/path")
    public List<DocumentPathItem> documentPath(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.documentPath(currentUser(authentication), documentId);
    }

    @PatchMapping("/docs/{documentId}")
    public DocumentDetail saveDocument(
        @PathVariable UUID documentId,
        @Valid @RequestBody SaveDocumentRequest request,
        Authentication authentication
    ) {
        return documentService.saveDocument(
            currentUser(authentication),
            documentId,
            request.baseVersionNo(),
            request.title(),
            request.content()
        );
    }

    @PatchMapping("/docs/{documentId}/knowledge-metadata")
    public DocumentDetail updateKnowledgeMetadata(
        @PathVariable UUID documentId,
        @Valid @RequestBody UpdateKnowledgeMetadataRequest request,
        Authentication authentication
    ) {
        return documentService.updateKnowledgeMetadata(
            currentUser(authentication),
            documentId,
            request.maintainerId(),
            request.tags(),
            request.category(),
            request.knowledgeStatus(),
            request.reviewDueAt(),
            request.verifiedAt()
        );
    }

    @GetMapping("/docs/{documentId}/blocks")
    public List<DocumentBlock> blocks(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.listBlocks(currentUser(authentication), documentId);
    }

    @PatchMapping("/docs/{documentId}/blocks")
    public DocumentDetail saveBlocks(
        @PathVariable UUID documentId,
        @Valid @RequestBody SaveDocumentBlocksRequest request,
        Authentication authentication
    ) {
        return documentService.saveBlocks(currentUser(authentication), documentId, request.baseVersionNo(), request.blocks());
    }

    @PostMapping("/docs/{documentId}/move")
    public DocumentDetail moveDocument(
        @PathVariable UUID documentId,
        @RequestBody MoveDocumentRequest request,
        Authentication authentication
    ) {
        return documentService.moveDocument(currentUser(authentication), documentId, request.parentId(), request.sortOrder());
    }

    @PostMapping("/docs/{documentId}/archive")
    public DocumentDetail archiveDocument(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.archiveDocument(currentUser(authentication), documentId);
    }

    @PostMapping("/docs/{documentId}/restore")
    public DocumentDetail restoreDocument(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.restoreDocument(currentUser(authentication), documentId);
    }

    @GetMapping("/docs/{documentId}/versions")
    public List<DocumentVersion> versions(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.listVersions(currentUser(authentication), documentId);
    }

    @PostMapping("/docs/{documentId}/versions/checkpoint")
    public DocumentDetail createVersionCheckpoint(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.createVersionCheckpoint(currentUser(authentication), documentId);
    }

    @PostMapping("/docs/{documentId}/versions/named")
    public DocumentDetail createNamedVersion(
        @PathVariable UUID documentId,
        @Valid @RequestBody CreateNamedVersionRequest request,
        Authentication authentication
    ) {
        return documentService.createNamedVersion(currentUser(authentication), documentId, request.versionName(), request.summary());
    }

    @PostMapping("/docs/{documentId}/import/markdown")
    public DocumentDetail importMarkdown(
        @PathVariable UUID documentId,
        @Valid @RequestBody ImportMarkdownRequest request,
        Authentication authentication
    ) {
        return documentService.importMarkdown(currentUser(authentication), documentId, request.title(), request.content());
    }

    @GetMapping(value = "/docs/{documentId}/export/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportMarkdown(@PathVariable UUID documentId, Authentication authentication) {
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/markdown;charset=UTF-8"))
            .body(documentService.exportMarkdown(currentUser(authentication), documentId));
    }

    @GetMapping(value = "/docs/{documentId}/export/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportHtml(@PathVariable UUID documentId, Authentication authentication) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(documentService.exportHtml(currentUser(authentication), documentId));
    }

    @PostMapping("/knowledge-bases/{spaceId}/import/markdown-batch")
    public KnowledgeBaseMarkdownImportResult importKnowledgeBaseMarkdownBatch(
        @PathVariable UUID spaceId,
        @Valid @RequestBody ImportKnowledgeBaseMarkdownBatchRequest request,
        Authentication authentication
    ) {
        return documentService.importKnowledgeBaseMarkdownBatch(currentUser(authentication), spaceId, request.parentId(), request.items());
    }

    @GetMapping(value = "/knowledge-bases/{spaceId}/export/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportKnowledgeBaseMarkdown(@PathVariable UUID spaceId, Authentication authentication) {
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/markdown;charset=UTF-8"))
            .body(documentService.exportKnowledgeBaseMarkdown(currentUser(authentication), spaceId));
    }

    @PostMapping("/docs/knowledge-review-reminders/run")
    public KnowledgeReviewReminderResult runKnowledgeReviewReminders(
        @RequestBody(required = false) RunKnowledgeReviewRemindersRequest request,
        Authentication authentication
    ) {
        RunKnowledgeReviewRemindersRequest normalized = request == null ? new RunKnowledgeReviewRemindersRequest(null, 50) : request;
        return documentService.runKnowledgeReviewReminders(currentUser(authentication), normalized.beforeDate(), normalized.limit());
    }

    @GetMapping("/docs/{documentId}/versions/diff")
    public DocumentVersionDiff diffVersions(
        @PathVariable UUID documentId,
        @RequestParam int fromVersionNo,
        @RequestParam int toVersionNo,
        Authentication authentication
    ) {
        return documentService.diffVersions(currentUser(authentication), documentId, fromVersionNo, toVersionNo);
    }

    @PostMapping("/docs/{documentId}/versions/{versionNo}/restore")
    public DocumentDetail restoreVersion(
        @PathVariable UUID documentId,
        @PathVariable int versionNo,
        Authentication authentication
    ) {
        return documentService.restoreVersion(currentUser(authentication), documentId, versionNo);
    }

    @PostMapping("/docs/{documentId}/permissions")
    public DocumentDetail grantPermission(
        @PathVariable UUID documentId,
        @Valid @RequestBody GrantDocumentPermissionRequest request,
        Authentication authentication
    ) {
        UUID subjectId = request.subjectId() == null ? request.userId() : request.subjectId();
        String subjectType = request.subjectType() == null || request.subjectType().isBlank() ? "user" : request.subjectType();
        return documentService.grantPermission(currentUser(authentication), documentId, subjectType, subjectId, request.permissionLevel());
    }

    @PostMapping("/docs/{documentId}/share-link")
    public DocumentShareLink updateShareLink(
        @PathVariable UUID documentId,
        @Valid @RequestBody UpdateDocumentShareLinkRequest request,
        Authentication authentication
    ) {
        return documentService.updateShareLink(
            currentUser(authentication),
            documentId,
            request.scope(),
            request.permissionLevel(),
            request.enabled(),
            request.expiresAt()
        );
    }

    @PostMapping("/docs/{documentId}/share-link/enable")
    public DocumentShareLink enableShareLink(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.setShareLinkEnabled(currentUser(authentication), documentId, true);
    }

    @PostMapping("/docs/{documentId}/share-link/disable")
    public DocumentShareLink disableShareLink(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.setShareLinkEnabled(currentUser(authentication), documentId, false);
    }

    @PostMapping("/docs/{documentId}/knowledge-base")
    public DocumentDetail updateKnowledgeBase(
        @PathVariable UUID documentId,
        @Valid @RequestBody UpdateKnowledgeBaseRequest request,
        Authentication authentication
    ) {
        return documentService.updateKnowledgeBaseSettings(
            currentUser(authentication),
            documentId,
            request.description(),
            request.coverUrl(),
            request.defaultPermissionLevel(),
            request.knowledgeBase()
        );
    }

    @PostMapping("/docs/{documentId}/permission-requests")
    public DocumentPermissionRequest requestPermission(
        @PathVariable UUID documentId,
        @Valid @RequestBody RequestDocumentPermissionRequest request,
        Authentication authentication
    ) {
        return documentService.requestPermission(currentUser(authentication), documentId, request.permissionLevel(), request.reason());
    }

    @PostMapping("/docs/{documentId}/relations")
    public DocumentDetail addRelation(
        @PathVariable UUID documentId,
        @Valid @RequestBody AddDocumentRelationRequest request,
        Authentication authentication
    ) {
        return documentService.addRelation(currentUser(authentication), documentId, request.targetType(), request.targetId());
    }

    @PostMapping("/docs/{documentId}/issues/from-selection")
    public IssueDetail createIssueFromSelection(
        @PathVariable UUID documentId,
        @Valid @RequestBody CreateIssueFromDocumentSelectionRequest request,
        Authentication authentication
    ) {
        return documentCrossModuleService.createIssueFromSelection(
            currentUser(authentication),
            documentId,
            request.projectId(),
            request.issueType(),
            request.title(),
            request.description(),
            request.priority(),
            request.assigneeId(),
            request.dueAt(),
            request.anchorStart(),
            request.anchorEnd(),
            request.anchorText()
        );
    }

    @PostMapping("/docs/{documentId}/comments")
    public DocumentDetail addComment(
        @PathVariable UUID documentId,
        @Valid @RequestBody AddDocumentCommentRequest request,
        Authentication authentication
    ) {
        return documentService.addComment(
            currentUser(authentication),
            documentId,
            request.blockId(),
            request.content(),
            request.anchorType(),
            request.anchorStart(),
            request.anchorEnd(),
            request.anchorText(),
            request.anchorPrefix(),
            request.anchorSuffix()
        );
    }

    @PostMapping("/docs/{documentId}/comments/{commentId}/replies")
    public DocumentDetail addCommentReply(
        @PathVariable UUID documentId,
        @PathVariable UUID commentId,
        @Valid @RequestBody AddDocumentCommentReplyRequest request,
        Authentication authentication
    ) {
        return documentService.addCommentReply(currentUser(authentication), documentId, commentId, request.content());
    }

    @PostMapping("/docs/{documentId}/comments/{commentId}/resolve")
    public DocumentDetail resolveComment(
        @PathVariable UUID documentId,
        @PathVariable UUID commentId,
        Authentication authentication
    ) {
        return documentService.resolveComment(currentUser(authentication), documentId, commentId);
    }

    @PostMapping("/docs/{documentId}/comments/{commentId}/reopen")
    public DocumentDetail reopenComment(
        @PathVariable UUID documentId,
        @PathVariable UUID commentId,
        Authentication authentication
    ) {
        return documentService.reopenComment(currentUser(authentication), documentId, commentId);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateDocumentRequest(
        UUID parentId,
        @NotBlank @Size(max = 255) String title,
        String docType,
        String content,
        @Size(max = 512) String description,
        @Size(max = 1024) String coverUrl,
        String defaultPermissionLevel,
        Boolean knowledgeBase
    ) {
    }

    public record CreateDocumentFromTemplateRequest(@NotNull UUID templateId, UUID parentId, @Size(max = 255) String title) {
    }

    public record CreateDocumentTemplateRequest(
        UUID knowledgeBaseId,
        @NotBlank @Size(max = 128) String title,
        @Size(max = 512) String description,
        @Size(max = 64) String category,
        String content
    ) {
    }

    public record SaveDocumentRequest(int baseVersionNo, @Size(max = 255) String title, String content) {
    }

    public record UpdateKnowledgeMetadataRequest(
        UUID maintainerId,
        List<String> tags,
        @Size(max = 64) String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt
    ) {
    }

    public record SaveDocumentBlocksRequest(int baseVersionNo, @NotNull List<DocumentBlockDraft> blocks) {
    }

    public record CreateNamedVersionRequest(@NotBlank @Size(max = 128) String versionName, @Size(max = 512) String summary) {
    }

    public record ImportMarkdownRequest(@Size(max = 255) String title, String content) {
    }

    public record ImportKnowledgeBaseMarkdownBatchRequest(
        UUID parentId,
        @NotNull List<KnowledgeBaseMarkdownImportItem> items
    ) {
    }

    public record RunKnowledgeReviewRemindersRequest(LocalDate beforeDate, int limit) {
    }

    public record MoveDocumentRequest(UUID parentId, Integer sortOrder) {
    }

    public record GrantDocumentPermissionRequest(UUID userId, String subjectType, UUID subjectId, @NotBlank String permissionLevel) {
    }

    public record UpdateDocumentShareLinkRequest(
        String scope,
        @NotBlank String permissionLevel,
        Boolean enabled,
        Instant expiresAt
    ) {
    }

    public record UpdateKnowledgeBaseRequest(
        @Size(max = 512) String description,
        @Size(max = 1024) String coverUrl,
        String defaultPermissionLevel,
        Boolean knowledgeBase
    ) {
    }

    public record RequestDocumentPermissionRequest(@NotBlank String permissionLevel, @Size(max = 512) String reason) {
    }

    public record AddDocumentRelationRequest(@NotBlank String targetType, @NotNull UUID targetId) {
    }

    public record CreateIssueFromDocumentSelectionRequest(
        @NotNull UUID projectId,
        String issueType,
        @Size(max = 255) String title,
        String description,
        String priority,
        UUID assigneeId,
        LocalDate dueAt,
        Integer anchorStart,
        Integer anchorEnd,
        @Size(max = 2000) String anchorText
    ) {
    }

    public record AddDocumentCommentRequest(
        UUID blockId,
        String anchorType,
        Integer anchorStart,
        Integer anchorEnd,
        String anchorText,
        String anchorPrefix,
        String anchorSuffix,
        @NotBlank String content
    ) {
    }

    public record AddDocumentCommentReplyRequest(@NotBlank String content) {
    }
}
