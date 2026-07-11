package com.colla.platform.modules.knowledge.api;

import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeBaseItemTreeNodeView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeBaseItemView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeBaseMarkdownImportView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentDetailView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeContentTemplateView;
import com.colla.platform.modules.knowledge.application.KnowledgeBaseItemService;
import com.colla.platform.modules.knowledge.application.KnowledgeContentService;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseMarkdownImportItem;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-bases/{spaceId}")
public class KnowledgeBaseItemController {
    private final KnowledgeBaseItemService itemService;
    private final KnowledgeContentService contentService;

    public KnowledgeBaseItemController(KnowledgeBaseItemService itemService, KnowledgeContentService contentService) {
        this.itemService = itemService;
        this.contentService = contentService;
    }

    @GetMapping("/items")
    public List<KnowledgeBaseItemView> listItems(@PathVariable UUID spaceId,
                                                 @RequestParam(defaultValue = "false") boolean includeArchived,
                                                 Authentication authentication) {
        return itemService.listItems(currentUser(authentication), spaceId, includeArchived).stream()
            .map(KnowledgeApiDtos::item).toList();
    }

    @GetMapping("/items/tree")
    public List<KnowledgeBaseItemTreeNodeView> itemTree(@PathVariable UUID spaceId,
                                                        @RequestParam(defaultValue = "false") boolean includeArchived,
                                                        Authentication authentication) {
        return itemService.itemTree(currentUser(authentication), spaceId, includeArchived).stream()
            .map(KnowledgeApiDtos::treeNode).toList();
    }

    @PostMapping("/items")
    public KnowledgeContentDetailView createItem(@PathVariable UUID spaceId,
                                                  @Valid @RequestBody CreateKnowledgeBaseItemRequest request,
                                                  Authentication authentication) {
        return KnowledgeApiDtos.detail(itemService.createItem(currentUser(authentication), spaceId, request.parentId(),
            request.title(), request.contentType(), request.content(), request.targetObjectType(), request.targetObjectId(),
            request.targetRoute(), request.displayMode(), request.targetTitleStrategy(), request.entryAlias()));
    }

    @PostMapping("/items/from-template")
    public KnowledgeContentDetailView createItemFromTemplate(@PathVariable UUID spaceId,
                                                              @Valid @RequestBody CreateKnowledgeBaseItemFromTemplateRequest request,
                                                              Authentication authentication) {
        return KnowledgeApiDtos.detail(itemService.createItemFromTemplate(currentUser(authentication), spaceId,
            request.templateId(), request.parentId(), request.title()));
    }

    @PostMapping("/items/{itemId}/move")
    public KnowledgeContentDetailView moveItem(@PathVariable UUID spaceId, @PathVariable UUID itemId,
                                                @RequestBody MoveKnowledgeBaseItemRequest request,
                                                Authentication authentication) {
        return KnowledgeApiDtos.detail(itemService.moveItem(currentUser(authentication), spaceId, itemId,
            request.parentId(), request.sortOrder()));
    }

    @PostMapping("/items/{itemId}/archive")
    public KnowledgeContentDetailView archiveItem(@PathVariable UUID spaceId, @PathVariable UUID itemId,
                                                   Authentication authentication) {
        return KnowledgeApiDtos.detail(itemService.archiveItem(currentUser(authentication), spaceId, itemId));
    }

    @PostMapping("/items/{itemId}/restore")
    public KnowledgeContentDetailView restoreItem(@PathVariable UUID spaceId, @PathVariable UUID itemId,
                                                   Authentication authentication) {
        return KnowledgeApiDtos.detail(itemService.restoreItem(currentUser(authentication), spaceId, itemId));
    }

    @GetMapping("/templates")
    public List<KnowledgeContentTemplateView> templates(@PathVariable UUID spaceId, Authentication authentication) {
        return itemService.listTemplates(currentUser(authentication), spaceId).stream().map(KnowledgeApiDtos::template).toList();
    }

    @PostMapping("/templates")
    public KnowledgeContentTemplateView createTemplate(@PathVariable UUID spaceId,
                                                        @Valid @RequestBody CreateKnowledgeBaseTemplateRequest request,
                                                        Authentication authentication) {
        return KnowledgeApiDtos.template(contentService.createTemplate(currentUser(authentication), spaceId,
            request.title(), request.description(), request.category(), request.content()));
    }

    @PostMapping("/import/markdown-batch")
    public KnowledgeBaseMarkdownImportView importMarkdownBatch(@PathVariable UUID spaceId,
                                                                @Valid @RequestBody ImportKnowledgeBaseMarkdownBatchRequest request,
                                                                Authentication authentication) {
        return KnowledgeApiDtos.importResult(contentService.importKnowledgeBaseMarkdownBatch(currentUser(authentication), spaceId,
            request.parentId(), request.items()));
    }

    @GetMapping(value = "/export/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportMarkdown(@PathVariable UUID spaceId, Authentication authentication) {
        return ResponseEntity.ok().contentType(MediaType.valueOf("text/markdown;charset=UTF-8"))
            .body(contentService.exportKnowledgeBaseMarkdown(currentUser(authentication), spaceId));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateKnowledgeBaseItemRequest(UUID parentId, @NotBlank @Size(max = 255) String title,
        String contentType, String content, @Size(max = 64) String targetObjectType,
        UUID targetObjectId, @Size(max = 1024) String targetRoute, @Size(max = 32) String displayMode,
        @Size(max = 32) String targetTitleStrategy, @Size(max = 255) String entryAlias) { }

    public record CreateKnowledgeBaseItemFromTemplateRequest(@NotNull UUID templateId, UUID parentId,
        @Size(max = 255) String title) { }

    public record MoveKnowledgeBaseItemRequest(UUID parentId, Integer sortOrder) { }

    public record CreateKnowledgeBaseTemplateRequest(@NotBlank @Size(max = 128) String title,
        @Size(max = 512) String description, @Size(max = 64) String category, String content) { }

    public record ImportKnowledgeBaseMarkdownBatchRequest(UUID parentId,
        @NotNull List<KnowledgeBaseMarkdownImportItem> items) { }
}
