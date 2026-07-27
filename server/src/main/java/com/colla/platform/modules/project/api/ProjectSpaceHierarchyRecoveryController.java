package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemHierarchyApiDtos.ConsistencyReportResponse;
import com.colla.platform.modules.project.api.WorkItemHierarchyApiDtos.HierarchyRebuildBatchResponse;
import com.colla.platform.modules.project.application.WorkItemHierarchyService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/hierarchy-rebuilds")
public class ProjectSpaceHierarchyRecoveryController {
    private final WorkItemHierarchyService service;

    public ProjectSpaceHierarchyRecoveryController(WorkItemHierarchyService service) {
        this.service = service;
    }

    @PostMapping(":scan")
    public ConsistencyReportResponse scan(
        @PathVariable UUID spaceId,
        @Valid @RequestBody ScanRequest request,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.consistency(service.scan(
            currentUser(authentication),
            spaceId,
            request.relationKey()
        ));
    }

    @PostMapping
    public HierarchyRebuildBatchResponse rebuild(
        @PathVariable UUID spaceId,
        @Valid @RequestBody RebuildRequest request,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.batch(service.rebuild(
            currentUser(authentication),
            spaceId,
            request.relationKey(),
            request.dryRun(),
            request.confirmation(),
            RequestBoundaryContext.current().requestId()
        ));
    }

    @PostMapping("/{batchId}:resume")
    public HierarchyRebuildBatchResponse resume(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        @Valid @RequestBody ResumeRequest request,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.batch(service.resume(
            currentUser(authentication),
            spaceId,
            batchId,
            request.confirmation()
        ));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record ScanRequest(@NotBlank String relationKey) {
    }

    public record RebuildRequest(
        @NotBlank String relationKey,
        boolean dryRun,
        String confirmation
    ) {
    }

    public record ResumeRequest(@NotBlank String confirmation) {
    }
}
