package com.colla.platform.modules.knowledge.api;

import com.colla.platform.modules.knowledge.application.KnowledgeBaseSpaceService;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeBaseDiscoveryView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeBaseGovernanceView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeBaseSpaceDetailView;
import com.colla.platform.modules.knowledge.api.KnowledgeApiDtos.KnowledgeBaseSpaceView;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseBulkGovernanceResult;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSubscription;
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
    /* Product boundary: knowledge-base spaces and content lifecycle APIs live here. */
    private final KnowledgeBaseSpaceService knowledgeBaseSpaceService;
    public KnowledgeBaseSpaceController(KnowledgeBaseSpaceService knowledgeBaseSpaceService) {
        this.knowledgeBaseSpaceService = knowledgeBaseSpaceService;
    }

    @GetMapping
    public List<KnowledgeBaseSpaceView> listSpaces(
        @RequestParam(defaultValue = "false") boolean includeArchived,
        Authentication authentication
    ) {
        return knowledgeBaseSpaceService.listSpaces(currentUser(authentication), includeArchived).stream()
            .map(KnowledgeApiDtos::space)
            .toList();
    }

    @PostMapping
    public KnowledgeBaseSpaceDetailView createSpace(
        @Valid @RequestBody CreateKnowledgeBaseSpaceRequest request,
        Authentication authentication
    ) {
        return KnowledgeApiDtos.spaceDetail(knowledgeBaseSpaceService.createSpace(
            currentUser(authentication),
            request.name(),
            request.code(),
            request.description(),
            request.icon(),
            request.coverUrl(),
            request.visibility(),
            request.defaultPermissionLevel()
        ));
    }

    @GetMapping("/{spaceId}")
    public KnowledgeBaseSpaceDetailView getSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return KnowledgeApiDtos.spaceDetail(knowledgeBaseSpaceService.getSpace(currentUser(authentication), spaceId));
    }

    @GetMapping("/{spaceId}/discovery")
    public KnowledgeBaseDiscoveryView discovery(@PathVariable UUID spaceId, Authentication authentication) {
        return KnowledgeApiDtos.discovery(knowledgeBaseSpaceService.discovery(currentUser(authentication), spaceId));
    }

    @GetMapping("/{spaceId}/governance")
    public KnowledgeBaseGovernanceView governance(@PathVariable UUID spaceId, Authentication authentication) {
        return KnowledgeApiDtos.governance(knowledgeBaseSpaceService.governance(currentUser(authentication), spaceId));
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
            request.itemIds(),
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
    public KnowledgeBaseSpaceDetailView updateSpace(
        @PathVariable UUID spaceId,
        @Valid @RequestBody UpdateKnowledgeBaseSpaceRequest request,
        Authentication authentication
    ) {
        return KnowledgeApiDtos.spaceDetail(knowledgeBaseSpaceService.updateSpace(
            currentUser(authentication),
            spaceId,
            request.name(),
            request.code(),
            request.description(),
            request.icon(),
            request.coverUrl(),
            request.visibility(),
            request.homeItemId(),
            request.defaultPermissionLevel()
        ));
    }

    @PostMapping("/{spaceId}/disable")
    public KnowledgeBaseSpaceDetailView disableSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return KnowledgeApiDtos.spaceDetail(knowledgeBaseSpaceService.disableSpace(currentUser(authentication), spaceId));
    }

    @PostMapping("/{spaceId}/restore")
    public KnowledgeBaseSpaceDetailView restoreSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return KnowledgeApiDtos.spaceDetail(knowledgeBaseSpaceService.restoreSpace(currentUser(authentication), spaceId));
    }

    @PostMapping("/{spaceId}/archive")
    public KnowledgeBaseSpaceDetailView archiveSpace(@PathVariable UUID spaceId, Authentication authentication) {
        return KnowledgeApiDtos.spaceDetail(knowledgeBaseSpaceService.archiveSpace(currentUser(authentication), spaceId));
    }

    @DeleteMapping("/{spaceId}")
    public void deleteSpace(@PathVariable UUID spaceId, Authentication authentication) {
        knowledgeBaseSpaceService.deleteSpace(currentUser(authentication), spaceId);
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
        UUID homeItemId,
        String defaultPermissionLevel
    ) {
    }

    public record KnowledgeSubscriptionRequest(String targetType, UUID targetId) {
    }

    public record KnowledgeBaseBulkGovernanceRequest(
        List<UUID> itemIds,
        UUID maintainerId,
        List<String> tags,
        boolean replaceTags,
        boolean archive,
        boolean requestReview,
        LocalDate reviewDueAt
    ) {
    }
}
