package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.application.KnowledgeBaseSpaceService;
import com.colla.platform.modules.doc.api.AdminKnowledgeBaseDtos.AdminKnowledgeBaseDetailView;
import com.colla.platform.modules.doc.api.AdminKnowledgeBaseDtos.AdminKnowledgeBaseGovernanceView;
import com.colla.platform.modules.doc.api.AdminKnowledgeBaseDtos.AdminKnowledgeBaseSpaceView;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseBulkGovernanceResult;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/knowledge-bases")
public class AdminKnowledgeBaseController {
    private final KnowledgeBaseSpaceService knowledgeBaseSpaceService;

    public AdminKnowledgeBaseController(KnowledgeBaseSpaceService knowledgeBaseSpaceService) {
        this.knowledgeBaseSpaceService = knowledgeBaseSpaceService;
    }

    @GetMapping
    public List<AdminKnowledgeBaseSpaceView> list(
        @RequestParam(defaultValue = "true") boolean includeArchived,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.listSpaces(currentUser(authentication), includeArchived).stream()
            .map(AdminKnowledgeBaseDtos::space)
            .toList();
    }

    @GetMapping("/{spaceId}")
    public AdminKnowledgeBaseDetailView detail(@PathVariable UUID spaceId, Authentication authentication) {
        return AdminKnowledgeBaseDtos.detail(knowledgeBaseSpaceService.getSpace(currentUser(authentication), spaceId));
    }

    @PatchMapping("/{spaceId}")
    public AdminKnowledgeBaseDetailView update(
        @PathVariable UUID spaceId,
        @Valid @RequestBody UpdateAdminKnowledgeBaseRequest request,
        Authentication authentication
    ) {
        return AdminKnowledgeBaseDtos.detail(knowledgeBaseSpaceService.updateSpace(
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
        ));
    }

    @PostMapping("/{spaceId}/disable")
    public AdminKnowledgeBaseDetailView disable(@PathVariable UUID spaceId, Authentication authentication) {
        return AdminKnowledgeBaseDtos.detail(knowledgeBaseSpaceService.disableSpace(currentUser(authentication), spaceId));
    }

    @PostMapping("/{spaceId}/restore")
    public AdminKnowledgeBaseDetailView restore(@PathVariable UUID spaceId, Authentication authentication) {
        return AdminKnowledgeBaseDtos.detail(knowledgeBaseSpaceService.restoreSpace(currentUser(authentication), spaceId));
    }

    @PostMapping("/{spaceId}/archive")
    public AdminKnowledgeBaseDetailView archive(@PathVariable UUID spaceId, Authentication authentication) {
        return AdminKnowledgeBaseDtos.detail(knowledgeBaseSpaceService.archiveSpace(currentUser(authentication), spaceId));
    }

    @DeleteMapping("/{spaceId}")
    public AdminKnowledgeBaseDetailView delete(@PathVariable UUID spaceId, Authentication authentication) {
        return AdminKnowledgeBaseDtos.detail(knowledgeBaseSpaceService.archiveSpace(currentUser(authentication), spaceId));
    }

    @GetMapping("/{spaceId}/governance")
    public AdminKnowledgeBaseGovernanceView governance(@PathVariable UUID spaceId, Authentication authentication) {
        CurrentUser currentUser = currentUser(authentication);
        KnowledgeBaseSpaceDetail detail = knowledgeBaseSpaceService.getSpace(currentUser, spaceId);
        return AdminKnowledgeBaseDtos.governance(detail.space(), knowledgeBaseSpaceService.governance(currentUser, spaceId));
    }

    @PostMapping("/{spaceId}/governance/bulk")
    public KnowledgeBaseBulkGovernanceResult bulkGovernance(
        @PathVariable UUID spaceId,
        @Valid @RequestBody AdminKnowledgeBaseBulkGovernanceRequest request,
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
    public ResponseEntity<String> exportGovernance(@PathVariable UUID spaceId, Authentication authentication) {
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
            .body(knowledgeBaseSpaceService.exportGovernanceCsv(currentUser(authentication), spaceId));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record UpdateAdminKnowledgeBaseRequest(
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

    public record AdminKnowledgeBaseBulkGovernanceRequest(
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
