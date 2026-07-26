package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemConfigurationTemplateService;
import com.colla.platform.modules.project.application.WorkItemConfigurationTemplateService.InstallationDetail;
import com.colla.platform.modules.project.application.WorkItemConfigurationTemplateService.TemplateCommandResult;
import com.colla.platform.modules.project.application.WorkItemConfigurationTemplateService.TemplateSummary;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateUpgradePreview;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/configuration")
public class WorkItemConfigurationTemplateController {
    private final WorkItemConfigurationTemplateService service;

    public WorkItemConfigurationTemplateController(WorkItemConfigurationTemplateService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    public List<TemplateSummary> catalog(@PathVariable UUID spaceId, Authentication authentication) {
        return service.catalog(currentUser(authentication), spaceId);
    }

    @PostMapping("/types/{typeId}/versions/{versionId}:create-template")
    public TemplateSummary create(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID versionId,
        @Valid @RequestBody CreateTemplateRequest request,
        Authentication authentication
    ) {
        return service.createWorkspaceTemplate(
            currentUser(authentication),
            spaceId,
            typeId,
            versionId,
            request.templateKey(),
            request.name(),
            request.description()
        );
    }

    @PostMapping("/types/{typeId}/versions/{versionId}:update-template/{templateId}")
    public TemplateSummary addVersion(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID versionId,
        @PathVariable UUID templateId,
        Authentication authentication
    ) {
        return service.addWorkspaceTemplateVersion(
            currentUser(authentication), spaceId, typeId, templateId, versionId
        );
    }

    @PostMapping("/templates/{templateId}:withdraw")
    public TemplateSummary withdraw(
        @PathVariable UUID spaceId,
        @PathVariable UUID templateId,
        Authentication authentication
    ) {
        return service.withdraw(currentUser(authentication), spaceId, templateId);
    }

    @GetMapping("/types/{typeId}/template-installation")
    public InstallationDetail installation(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        Authentication authentication
    ) {
        return service.installation(currentUser(authentication), spaceId, typeId);
    }

    @PostMapping("/types/{typeId}/template-installation")
    public TemplateCommandResult install(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody InstallTemplateRequest request,
        Authentication authentication
    ) {
        return service.install(
            currentUser(authentication),
            spaceId,
            typeId,
            request.templateId(),
            request.templateVersionId(),
            request.expectedDraftAggregateVersion(),
            requestId()
        );
    }

    @PostMapping("/types/{typeId}/template-installation:preview-upgrade")
    public TemplateUpgradePreview preview(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody UpgradePreviewRequest request,
        Authentication authentication
    ) {
        return service.previewUpgrade(
            currentUser(authentication),
            spaceId,
            typeId,
            request.targetTemplateVersionId(),
            request.resolutions()
        );
    }

    @PostMapping("/types/{typeId}/template-installation:apply-upgrade")
    public TemplateCommandResult upgrade(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody ApplyUpgradeRequest request,
        Authentication authentication
    ) {
        return service.applyUpgrade(
            currentUser(authentication),
            spaceId,
            typeId,
            request.targetTemplateVersionId(),
            request.expectedDraftAggregateVersion(),
            request.expectedInstallationAggregateVersion(),
            request.resolutions(),
            requestId()
        );
    }

    @PostMapping("/types/{typeId}/template-installation:detach")
    public TemplateCommandResult detach(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody DetachRequest request,
        Authentication authentication
    ) {
        return service.detach(
            currentUser(authentication),
            spaceId,
            typeId,
            request.expectedInstallationAggregateVersion(),
            requestId()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record CreateTemplateRequest(
        @NotBlank @Size(max = 96) String templateKey,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 2000) String description
    ) {
    }

    public record InstallTemplateRequest(
        @NotNull UUID templateId,
        UUID templateVersionId,
        @PositiveOrZero long expectedDraftAggregateVersion
    ) {
    }

    public record UpgradePreviewRequest(
        UUID targetTemplateVersionId,
        Map<String, String> resolutions
    ) {
    }

    public record ApplyUpgradeRequest(
        @NotNull UUID targetTemplateVersionId,
        @PositiveOrZero long expectedDraftAggregateVersion,
        @PositiveOrZero long expectedInstallationAggregateVersion,
        Map<String, String> resolutions
    ) {
    }

    public record DetachRequest(@PositiveOrZero long expectedInstallationAggregateVersion) {
    }
}
