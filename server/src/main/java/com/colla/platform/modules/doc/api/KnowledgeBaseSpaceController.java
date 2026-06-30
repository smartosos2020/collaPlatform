package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.application.KnowledgeBaseSpaceService;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseBulkGovernanceResult;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseDiscovery;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseGovernanceDashboard;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSubscription;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
    private final KnowledgeBaseSpaceService knowledgeBaseSpaceService;

    public KnowledgeBaseSpaceController(KnowledgeBaseSpaceService knowledgeBaseSpaceService) {
        this.knowledgeBaseSpaceService = knowledgeBaseSpaceService;
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
