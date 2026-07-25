package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemLayoutAccessProjectionService;
import com.colla.platform.modules.project.application.WorkItemLayoutAccessProjectionService.LayoutAccessProjection;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/types/{typeId}/layouts")
public class WorkItemLayoutAccessController {
    private final WorkItemLayoutAccessProjectionService projectionService;

    public WorkItemLayoutAccessController(WorkItemLayoutAccessProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/{layoutKind}/projection")
    public LayoutAccessProjection project(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable String layoutKind,
        Authentication authentication
    ) {
        return projectionService.project(
            (CurrentUser) authentication.getPrincipal(),
            spaceId,
            typeId,
            layoutKind
        );
    }
}
