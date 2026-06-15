package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.application.DocumentService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlock;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentBlockDraft;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
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
    public List<DocumentSummary> listDocuments(Authentication authentication) {
        return documentService.listDocuments(currentUser(authentication));
    }

    @PostMapping("/docs")
    public DocumentDetail createDocument(@Valid @RequestBody CreateDocumentRequest request, Authentication authentication) {
        return documentService.createDocument(currentUser(authentication), request.parentId(), request.title(), request.content());
    }

    @GetMapping("/docs/{documentId}")
    public DocumentDetail document(@PathVariable UUID documentId, Authentication authentication) {
        return documentService.getDocument(currentUser(authentication), documentId);
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
        return documentService.moveDocument(currentUser(authentication), documentId, request.parentId());
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
        return documentService.addComment(currentUser(authentication), documentId, request.content());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateDocumentRequest(UUID parentId, @NotBlank @Size(max = 255) String title, String content) {
    }

    public record SaveDocumentRequest(int baseVersionNo, @Size(max = 255) String title, String content) {
    }

    public record SaveDocumentBlocksRequest(int baseVersionNo, @NotNull List<DocumentBlockDraft> blocks) {
    }

    public record MoveDocumentRequest(UUID parentId) {
    }

    public record GrantDocumentPermissionRequest(@NotNull UUID userId, @NotBlank String permissionLevel) {
    }

    public record AddDocumentRelationRequest(@NotBlank String targetType, @NotNull UUID targetId) {
    }

    public record AddDocumentCommentRequest(@NotBlank String content) {
    }
}
