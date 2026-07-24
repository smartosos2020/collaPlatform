package com.colla.platform.modules.knowledge.api;

import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentBlockDraft;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentBlockView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentCanonicalMigrationPreviewView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentCollaborationHealthView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeCollaborationTicketView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentDetailView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentMigrationPreviewView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentPathItemView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentPerformanceView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentPermissionRequestView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentShareLinkView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentVersionDiffView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentVersionView;
import com.colla.platform.modules.knowledge.application.KnowledgeCollaborationHealthQuery;
import com.colla.platform.modules.knowledge.application.KnowledgeCollaborationGatewayService;
import com.colla.platform.modules.knowledge.application.KnowledgeContentCanonicalService;
import com.colla.platform.modules.knowledge.application.KnowledgeContentCrossModuleService;
import com.colla.platform.modules.knowledge.application.KnowledgeContentService;
import com.colla.platform.modules.knowledge.application.KnowledgeBaseSpaceService;
import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.modules.permission.application.ResourcePermissionManagementService;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-bases/{spaceId}/items/{itemId}")
public class KnowledgeContentController {
    private final KnowledgeCollaborationHealthQuery collaborationService;
    private final KnowledgeCollaborationGatewayService collaborationGatewayService;
    private final KnowledgeContentCanonicalService canonicalService;
    private final KnowledgeContentCrossModuleService crossModuleService;
    private final KnowledgeContentService contentService;
    private final KnowledgeBaseSpaceService knowledgeBaseSpaceService;
    private final ResourcePermissionManagementService resourcePermissionManagementService;

    public KnowledgeContentController(
        KnowledgeCollaborationHealthQuery collaborationService,
        KnowledgeCollaborationGatewayService collaborationGatewayService,
        KnowledgeContentCanonicalService canonicalService,
        KnowledgeContentCrossModuleService crossModuleService,
        KnowledgeContentService contentService,
        KnowledgeBaseSpaceService knowledgeBaseSpaceService,
        ResourcePermissionManagementService resourcePermissionManagementService
    ) {
        this.collaborationService = collaborationService;
        this.collaborationGatewayService = collaborationGatewayService;
        this.canonicalService = canonicalService;
        this.crossModuleService = crossModuleService;
        this.contentService = contentService;
        this.knowledgeBaseSpaceService = knowledgeBaseSpaceService;
        this.resourcePermissionManagementService = resourcePermissionManagementService;
    }

    @GetMapping
    public KnowledgeContentDetailView detail(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        return KnowledgeApiDtos.detail(knowledgeBaseSpaceService.getItem(currentUser(authentication), spaceId, itemId));
    }

    @PatchMapping
    public KnowledgeContentDetailView save(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody SaveKnowledgeContentRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.saveContent(user, itemId, request.baseVersionNo(), request.title(), request.content()));
    }

    @PatchMapping("/metadata")
    public KnowledgeContentDetailView updateMetadata(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody UpdateKnowledgeContentMetadataRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.updateKnowledgeMetadata(
            user,
            itemId,
            request.maintainerId(),
            request.tags(),
            request.category(),
            request.knowledgeStatus(),
            request.reviewDueAt(),
            request.verifiedAt()
        ));
    }

    @GetMapping("/blocks")
    public List<KnowledgeContentBlockView> blocks(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return contentService.listBlocks(user, itemId).stream().map(KnowledgeApiDtos::block).toList();
    }

    @PatchMapping("/blocks")
    public KnowledgeContentDetailView saveBlocks(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody SaveKnowledgeContentBlocksRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.saveBlocks(
            user,
            itemId,
            request.baseVersionNo(),
            request.title(),
            request.saveMode(),
            request.blocks().stream().map(KnowledgeApiDtos::toBlockDraft).toList()
        ));
    }

    @PostMapping("/blocks")
    public KnowledgeContentDetailView insertBlock(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody InsertKnowledgeContentBlockRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.insertBlock(
            user,
            itemId,
            request.baseVersionNo(),
            KnowledgeApiDtos.toBlockDraft(request.block()),
            request.afterSortOrder()
        ));
    }

    @PatchMapping("/blocks/{blockId}")
    public KnowledgeContentDetailView updateBlock(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @PathVariable UUID blockId,
        @Valid @RequestBody UpdateKnowledgeContentBlockRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.updateBlock(
            user,
            itemId,
            blockId,
            request.baseVersionNo(),
            KnowledgeApiDtos.toBlockDraft(request.block())
        ));
    }

    @PostMapping("/blocks/reorder")
    public KnowledgeContentDetailView reorderBlocks(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody ReorderKnowledgeContentBlocksRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.reorderBlocks(user, itemId, request.baseVersionNo(), request.blockIds()));
    }

    @DeleteMapping("/blocks/{blockId}")
    public KnowledgeContentDetailView deleteBlock(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @PathVariable UUID blockId,
        @RequestParam int baseVersionNo,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.deleteBlock(user, itemId, blockId, baseVersionNo));
    }

    @GetMapping("/versions")
    public List<KnowledgeContentVersionView> versions(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return contentService.listVersions(user, itemId).stream().map(KnowledgeApiDtos::version).toList();
    }

    @PostMapping("/versions/checkpoint")
    public KnowledgeContentDetailView createCheckpoint(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.createVersionCheckpoint(user, itemId));
    }

    @PostMapping("/versions/named")
    public KnowledgeContentDetailView createNamedVersion(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody CreateKnowledgeContentVersionRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.createNamedVersion(user, itemId, request.versionName(), request.summary()));
    }

    @GetMapping("/versions/diff")
    public KnowledgeContentVersionDiffView diffVersions(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @RequestParam int fromVersionNo,
        @RequestParam int toVersionNo,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.versionDiff(contentService.diffVersions(user, itemId, fromVersionNo, toVersionNo));
    }

    @PostMapping("/versions/{versionNo}/restore")
    public KnowledgeContentDetailView restoreVersion(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @PathVariable int versionNo,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.restoreVersion(user, itemId, versionNo));
    }

    @PostMapping("/import/markdown")
    public KnowledgeContentDetailView importMarkdown(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody ImportKnowledgeMarkdownRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.importMarkdown(user, itemId, request.title(), request.content()));
    }

    @PostMapping("/import/html")
    public KnowledgeContentDetailView importHtml(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody ImportKnowledgeHtmlRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.importHtml(user, itemId, request.title(), request.html()));
    }

    @PostMapping("/import/preview")
    public KnowledgeApiDtos.KnowledgeContentTransferReportView previewImport(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody PreviewKnowledgeContentTransferRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.transferReport(contentService.previewImport(user, itemId, request.format(), request.source()));
    }

    @GetMapping("/export/manifest")
    public KnowledgeApiDtos.KnowledgeContentTransferReportView exportManifest(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @RequestParam(defaultValue = "markdown") String format,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.transferReport(contentService.exportManifest(user, itemId, format));
    }

    @GetMapping(value = "/export/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportMarkdown(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/markdown;charset=UTF-8"))
            .body(contentService.exportMarkdown(user, itemId));
    }

    @GetMapping(value = "/export/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportHtml(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(contentService.exportHtml(user, itemId));
    }

    @PostMapping("/permissions")
    public KnowledgeContentDetailView grantPermission(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody GrantKnowledgeContentPermissionRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        UUID subjectId = request.subjectId() == null ? request.userId() : request.subjectId();
        String subjectType = request.subjectType() == null || request.subjectType().isBlank() ? "user" : request.subjectType();
        return KnowledgeApiDtos.detail(contentService.grantPermission(user, itemId, subjectType, subjectId, request.permissionLevel()));
    }

    @PostMapping("/permission-requests")
    public KnowledgeContentPermissionRequestView requestPermission(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody RequestKnowledgeContentPermissionRequest request,
        Authentication authentication
    ) {
        ResourcePermissionRequest submitted = resourcePermissionManagementService.requestPermission(
            currentUser(authentication),
            "knowledge_content",
            itemId,
            request.permissionLevel(),
            request.reason()
        );
        return new KnowledgeContentPermissionRequestView(
            submitted.id(),
            itemId,
            submitted.permissionLevel(),
            0,
            submitted.status()
        );
    }

    @PostMapping("/share-link")
    public KnowledgeContentShareLinkView updateShareLink(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody UpdateKnowledgeContentShareLinkRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.shareLink(contentService.updateShareLink(
            user, itemId, request.scope(), request.permissionLevel(), request.enabled(), request.expiresAt()
        ));
    }

    @PostMapping("/share-link/enable")
    public KnowledgeContentShareLinkView enableShareLink(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.shareLink(contentService.setShareLinkEnabled(user, itemId, true));
    }

    @PostMapping("/share-link/disable")
    public KnowledgeContentShareLinkView disableShareLink(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.shareLink(contentService.setShareLinkEnabled(user, itemId, false));
    }

    @PostMapping("/share-link/revoke")
    public KnowledgeContentShareLinkView revokeShareLink(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.shareLink(contentService.revokeShareLink(user, itemId));
    }

    @PostMapping("/relations")
    public KnowledgeContentDetailView addRelation(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody AddKnowledgeContentRelationRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.addRelation(user, itemId, request.targetType(), request.targetId()));
    }

    @DeleteMapping("/relations")
    public KnowledgeContentDetailView removeRelation(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @RequestParam String targetType,
        @RequestParam UUID targetId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.removeRelation(user, itemId, targetType, targetId));
    }

    @PostMapping("/issues/from-selection")
    public IssueDetail createIssueFromSelection(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody CreateIssueFromKnowledgeSelectionRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return crossModuleService.createIssueFromSelection(
            user,
            itemId,
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

    @PostMapping("/comments")
    public KnowledgeContentDetailView addComment(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @Valid @RequestBody AddKnowledgeContentCommentRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.addComment(
            user,
            itemId,
            request.blockId(),
            request.content(),
            request.anchorType(),
            request.anchorStart(),
            request.anchorEnd(),
            request.anchorText(),
            request.anchorPrefix(),
            request.anchorSuffix()
        ));
    }

    @PostMapping("/comments/{commentId}/replies")
    public KnowledgeContentDetailView addCommentReply(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @PathVariable UUID commentId,
        @Valid @RequestBody AddKnowledgeContentCommentReplyRequest request,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.addCommentReply(user, itemId, commentId, request.content()));
    }

    @PostMapping("/comments/{commentId}/resolve")
    public KnowledgeContentDetailView resolveComment(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @PathVariable UUID commentId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.resolveComment(user, itemId, commentId));
    }

    @PostMapping("/comments/{commentId}/reopen")
    public KnowledgeContentDetailView reopenComment(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        @PathVariable UUID commentId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.detail(contentService.reopenComment(user, itemId, commentId));
    }

    @GetMapping("/path")
    public List<KnowledgeContentPathItemView> path(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        KnowledgeContentDetailView detail = detail(spaceId, itemId, authentication);
        return detail.context().path();
    }

    @GetMapping("/performance")
    public KnowledgeContentPerformanceView performance(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.performance(contentService.performanceProfile(user, itemId));
    }

    @GetMapping("/diagnostics")
    public KnowledgeApiDtos.KnowledgeContentDiagnosticsView diagnostics(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.diagnostics(contentService.diagnostics(user, itemId));
    }

    @GetMapping("/migration-preview")
    public KnowledgeContentMigrationPreviewView migrationPreview(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.migrationPreview(contentService.migrationPreview(user, itemId));
    }

    @GetMapping("/migration/preview")
    public KnowledgeContentCanonicalMigrationPreviewView canonicalMigrationPreview(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.canonicalMigrationPreview(canonicalService.plan(itemId, contentService.getContent(user, itemId)));
    }

    @GetMapping("/collaboration/health")
    public KnowledgeContentCollaborationHealthView collaborationHealth(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.collaborationHealth(collaborationService.health(user, itemId));
    }

    @PostMapping("/collaboration/ticket")
    public KnowledgeCollaborationTicketView collaborationTicket(
        @PathVariable UUID spaceId,
        @PathVariable UUID itemId,
        Authentication authentication
    ) {
        CurrentUser user = itemUser(authentication, spaceId, itemId);
        return KnowledgeApiDtos.collaborationTicket(collaborationGatewayService.issue(user, itemId));
    }

    private CurrentUser itemUser(Authentication authentication, UUID spaceId, UUID itemId) {
        CurrentUser user = currentUser(authentication);
        knowledgeBaseSpaceService.requireItemAccess(user, spaceId, itemId);
        return user;
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record SaveKnowledgeContentRequest(int baseVersionNo, @Size(max = 255) String title, String content) {
    }

    public record UpdateKnowledgeContentMetadataRequest(
        UUID maintainerId,
        List<String> tags,
        @Size(max = 64) String category,
        String knowledgeStatus,
        LocalDate reviewDueAt,
        Instant verifiedAt
    ) {
    }

    public record SaveKnowledgeContentBlocksRequest(
        int baseVersionNo,
        @Size(max = 255) String title,
        String saveMode,
        @NotNull List<KnowledgeContentBlockDraft> blocks
    ) {
    }

    public record InsertKnowledgeContentBlockRequest(
        int baseVersionNo,
        @NotNull KnowledgeContentBlockDraft block,
        Integer afterSortOrder
    ) {
    }

    public record UpdateKnowledgeContentBlockRequest(int baseVersionNo, @NotNull KnowledgeContentBlockDraft block) {
    }

    public record ReorderKnowledgeContentBlocksRequest(int baseVersionNo, @NotNull List<UUID> blockIds) {
    }

    public record CreateKnowledgeContentVersionRequest(
        @NotBlank @Size(max = 128) String versionName,
        @Size(max = 512) String summary
    ) {
    }

    public record ImportKnowledgeMarkdownRequest(@Size(max = 255) String title, String content) {
    }

    public record ImportKnowledgeHtmlRequest(@Size(max = 255) String title, String html) {
    }

    public record PreviewKnowledgeContentTransferRequest(@NotBlank String format, @NotNull String source) {
    }

    public record GrantKnowledgeContentPermissionRequest(UUID userId, String subjectType, UUID subjectId, @NotBlank String permissionLevel) {
    }

    public record RequestKnowledgeContentPermissionRequest(@NotBlank String permissionLevel, @Size(max = 512) String reason) {
    }

    public record UpdateKnowledgeContentShareLinkRequest(
        String scope,
        @NotBlank String permissionLevel,
        Boolean enabled,
        Instant expiresAt
    ) {
    }

    public record AddKnowledgeContentRelationRequest(@NotBlank String targetType, @NotNull UUID targetId) {
    }

    public record CreateIssueFromKnowledgeSelectionRequest(
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

    public record AddKnowledgeContentCommentRequest(
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

    public record AddKnowledgeContentCommentReplyRequest(@NotBlank String content) {
    }
}

