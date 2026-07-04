package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.application.DocumentService;
import com.colla.platform.modules.doc.application.KnowledgeBaseSpaceService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTemplate;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTreeNode;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseMarkdownImportItem;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseMarkdownImportResult;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseBulkGovernanceResult;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseDiscovery;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseGovernanceDashboard;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSubscription;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseSpaceController {
    /*
     * Product boundary: knowledge-base spaces and content lifecycle APIs live here.
     * The /api/docs controller remains the editor substrate and historical deep-link
     * compatibility layer, not a separate product module.
     */
    private final KnowledgeBaseSpaceService knowledgeBaseSpaceService;
    private final DocumentService documentService;

    public KnowledgeBaseSpaceController(KnowledgeBaseSpaceService knowledgeBaseSpaceService, DocumentService documentService) {
        this.knowledgeBaseSpaceService = knowledgeBaseSpaceService;
        this.documentService = documentService;
    }

    @GetMapping
    public List<KnowledgeBaseSpaceSummary> listSpaces(
        @RequestParam(defaultValue = "false") boolean includeArchived,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.listSpaces(currentUser(authentication), includeArchived);
    }

    @PostMapping
    public KnowledgeBaseSpaceDetail createSpace(
        @Valid @RequestBody CreateKnowledgeBaseSpaceRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.createSpace(
            currentUser(authentication),
            request.name(),
            request.code(),
            request.description(),
            request.icon(),
            request.coverUrl(),
            request.visibility(),
            request.defaultPermissionLevel()
        );
    }

    @GetMapping("/{spaceId}")
    public KnowledgeBaseSpaceDetail getSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.getSpace(currentUser(authentication), spaceId);
    }

    @GetMapping("/{spaceId}/items")
    public List<DocumentSummary> listItems(
        @PathVariable UUID spaceId,
        @RequestParam(defaultValue = "false") boolean includeArchived,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.listItems(currentUser(authentication), spaceId, includeArchived);
    }

    @GetMapping("/{spaceId}/items/tree")
    public List<DocumentTreeNode> itemTree(
        @PathVariable UUID spaceId,
        @RequestParam(defaultValue = "false") boolean includeArchived,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.itemTree(currentUser(authentication), spaceId, includeArchived);
    }

    @PostMapping("/{spaceId}/items")
    public DocumentDetail createItem(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateKnowledgeBaseItemRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.createItem(
            currentUser(authentication),
            spaceId,
            request.parentId(),
            request.title(),
            request.docType(),
            request.content()
        );
    }

    @PostMapping("/{spaceId}/items/from-template")
    public DocumentDetail createItemFromTemplate(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateKnowledgeBaseItemFromTemplateRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.createItemFromTemplate(
            currentUser(authentication),
            spaceId,
            request.templateId(),
            request.parentId(),
            request.title()
        );
    }

    @PostMapping("/{spaceId}/items/{documentId}/move")
    public DocumentDetail moveItem(
        @PathVariable UUID spaceId,
        @PathVariable UUID documentId,
        @RequestBody MoveKnowledgeBaseItemRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.moveItem(currentUser(authentication), spaceId, documentId, request.parentId(), request.sortOrder());
    }

    @PostMapping("/{spaceId}/items/{documentId}/archive")
    public DocumentDetail archiveItem(@PathVariable UUID spaceId, @PathVariable UUID documentId, Authentication authentication) {
        return knowledgeBaseSpaceService.archiveItem(currentUser(authentication), spaceId, documentId);
    }

    @PostMapping("/{spaceId}/items/{documentId}/restore")
    public DocumentDetail restoreItem(@PathVariable UUID spaceId, @PathVariable UUID documentId, Authentication authentication) {
        return knowledgeBaseSpaceService.restoreItem(currentUser(authentication), spaceId, documentId);
    }

    @GetMapping("/{spaceId}/templates")
    public List<DocumentTemplate> templates(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.listTemplates(currentUser(authentication), spaceId);
    }

    @PostMapping("/{spaceId}/templates")
    public DocumentTemplate createTemplate(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateKnowledgeBaseTemplateRequest request,
        Authentication authentication
    ) {
        return documentService.createTemplate(
            currentUser(authentication),
            spaceId,
            request.title(),
            request.description(),
            request.category(),
            request.content()
        );
    }

    @PostMapping("/{spaceId}/import/markdown-batch")
    public KnowledgeBaseMarkdownImportResult importMarkdownBatch(
        @PathVariable UUID spaceId,
        @Valid @RequestBody ImportKnowledgeBaseMarkdownBatchRequest request,
        Authentication authentication
    ) {
        return documentService.importKnowledgeBaseMarkdownBatch(currentUser(authentication), spaceId, request.parentId(), request.items());
    }

    @GetMapping(value = "/{spaceId}/export/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportMarkdown(@PathVariable UUID spaceId, Authentication authentication) {
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/markdown;charset=UTF-8"))
            .body(documentService.exportKnowledgeBaseMarkdown(currentUser(authentication), spaceId));
    }

    @GetMapping("/{spaceId}/discovery")
    public KnowledgeBaseDiscovery discovery(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.discovery(currentUser(authentication), spaceId);
    }

    @GetMapping("/{spaceId}/governance")
    public KnowledgeBaseGovernanceDashboard governance(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.governance(currentUser(authentication), spaceId);
    }

    @PostMapping("/{spaceId}/governance/bulk")
    public KnowledgeBaseBulkGovernanceResult bulkGovernance(
        @PathVariable UUID spaceId,
        @Valid @RequestBody KnowledgeBaseBulkGovernanceRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.bulkGovernance(
            currentUser(authentication),
            spaceId,
            request.documentIds(),
            request.maintainerId(),
            request.tags(),
            request.replaceTags(),
            request.archive(),
            request.requestReview(),
            request.reviewDueAt()
        );
    }

    @GetMapping(value = "/{spaceId}/governance/export", produces = "text/csv")
    public String exportGovernance(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.exportGovernanceCsv(currentUser(authentication), spaceId);
    }

    @PatchMapping("/{spaceId}")
    public KnowledgeBaseSpaceDetail updateSpace(
        @PathVariable UUID spaceId,
        @Valid @RequestBody UpdateKnowledgeBaseSpaceRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.updateSpace(
            currentUser(authentication),
            spaceId,
            request.name(),
            request.code(),
            request.description(),
            request.icon(),
            request.coverUrl(),
            request.visibility(),
            request.homeDocumentId(),
            request.defaultPermissionLevel()
        );
    }

    @PostMapping("/{spaceId}/disable")
    public KnowledgeBaseSpaceDetail disableSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.disableSpace(currentUser(authentication), spaceId);
    }

    @PostMapping("/{spaceId}/restore")
    public KnowledgeBaseSpaceDetail restoreSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.restoreSpace(currentUser(authentication), spaceId);
    }

    @PostMapping("/{spaceId}/archive")
    public KnowledgeBaseSpaceDetail archiveSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.archiveSpace(currentUser(authentication), spaceId);
    }

    @DeleteMapping("/{spaceId}")
    public KnowledgeBaseSpaceDetail deleteSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return knowledgeBaseSpaceService.archiveSpace(currentUser(authentication), spaceId);
    }

    @PostMapping("/{spaceId}/subscriptions")
    public KnowledgeBaseSubscription subscribe(
        @PathVariable UUID spaceId,
        @Valid @RequestBody KnowledgeSubscriptionRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.subscribe(currentUser(authentication), spaceId, request.targetType(), request.targetId());
    }

    @PostMapping("/{spaceId}/subscriptions/remove")
    public KnowledgeBaseSubscription unsubscribe(
        @PathVariable UUID spaceId,
        @Valid @RequestBody KnowledgeSubscriptionRequest request,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.unsubscribe(currentUser(authentication), spaceId, request.targetType(), request.targetId());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateKnowledgeBaseSpaceRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 64) String code,
        @Size(max = 512) String description,
        @Size(max = 64) String icon,
        @Size(max = 1024) String coverUrl,
        String visibility,
        String defaultPermissionLevel
    ) {
    }

    public record UpdateKnowledgeBaseSpaceRequest(
        @Size(max = 255) String name,
        @Size(max = 64) String code,
        @Size(max = 512) String description,
        @Size(max = 64) String icon,
        @Size(max = 1024) String coverUrl,
        String visibility,
        UUID homeDocumentId,
        String defaultPermissionLevel
    ) {
    }

    public record KnowledgeSubscriptionRequest(String targetType, UUID targetId) {
    }

    public record CreateKnowledgeBaseItemRequest(
        UUID parentId,
        @NotBlank @Size(max = 255) String title,
        String docType,
        String content
    ) {
    }

    public record CreateKnowledgeBaseItemFromTemplateRequest(@NotNull UUID templateId, UUID parentId, @Size(max = 255) String title) {
    }

    public record MoveKnowledgeBaseItemRequest(UUID parentId, Integer sortOrder) {
    }

    public record CreateKnowledgeBaseTemplateRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 512) String description,
        @Size(max = 64) String category,
        String content
    ) {
    }

    public record ImportKnowledgeBaseMarkdownBatchRequest(
        UUID parentId,
        @NotNull List<KnowledgeBaseMarkdownImportItem> items
    ) {
    }

    public record KnowledgeBaseBulkGovernanceRequest(
        List<UUID> documentIds,
        UUID maintainerId,
        List<String> tags,
        boolean replaceTags,
        boolean archive,
        boolean requestReview,
        LocalDate reviewDueAt
    ) {
    }
}
