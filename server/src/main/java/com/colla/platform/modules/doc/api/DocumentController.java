package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.application.DocumentService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentPathItem;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentTreeNode;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersion;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentVersionDiff;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
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

    @PostMapping("/docs")
    public DocumentDetail createDocument(@Valid @RequestBody CreateDocumentRequest request, Authentication authentication) {
        return documentService.createDocument(currentUser(authentication), request.parentId(), request.title(), request.docType(), request.content());
    }

    @GetMapping("/docs/{documentId}")
    public DocumentDetail document(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.getDocument(currentUser(authentication), documentId);
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
        return documentService.grantPermission(currentUser(authentication), documentId, request.userId(), request.permissionLevel());
    }

    @PostMapping("/docs/{documentId}/relations")
    public DocumentDetail addRelation(
        @PathVariable UUID documentId,
        @Valid @RequestBody AddDocumentRelationRequest request,
        Authentication authentication
    ) {
        return documentService.addRelation(currentUser(authentication), documentId, request.targetType(), request.targetId());
    }

    @PostMapping("/docs/{documentId}/comments")
    public DocumentDetail addComment(
        @PathVariable UUID documentId,
        @Valid @RequestBody AddDocumentCommentRequest request,
        Authentication authentication
    ) {
        return documentService.addComment(currentUser(authentication), documentId, request.blockId(), request.content());
    }

    @PostMapping("/docs/{documentId}/comments/{commentId}/resolve")
    public DocumentDetail resolveComment(
        @PathVariable UUID documentId,
        @PathVariable UUID commentId,
        Authentication authentication
    ) {
        return documentService.resolveComment(currentUser(authentication), documentId, commentId);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateDocumentRequest(UUID parentId, @NotBlank @Size(max = 255) String title, String docType, String content) {
    }

    public record SaveDocumentRequest(int baseVersionNo, @Size(max = 255) String title, String content) {
    }

    public record SaveDocumentBlocksRequest(int baseVersionNo, @NotNull List<DocumentBlockDraft> blocks) {
    }

    public record MoveDocumentRequest(UUID parentId, Integer sortOrder) {
    }

    public record GrantDocumentPermissionRequest(@NotNull UUID userId, @NotBlank String permissionLevel) {
    }

    public record AddDocumentRelationRequest(@NotBlank String targetType, @NotNull UUID targetId) {
    }

    public record AddDocumentCommentRequest(UUID blockId, @NotBlank String content) {
    }
}
