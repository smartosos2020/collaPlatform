package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ResourceCapacityService;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.Allocation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityFoundation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityRule;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.MutateAllocationCommand;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.SaveCapacityRuleCommand;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/resource-planning/capacity")
public final class UserResourceCapacityController {
    private final ResourceCapacityService service;

    public UserResourceCapacityController(ResourceCapacityService service) {
        this.service = service;
    }

    @GetMapping
    public CapacityFoundation get(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.get((CurrentUser) authentication.getPrincipal(), spaceId);
    }

    @PostMapping("/allocations")
    public Allocation allocation(
        @PathVariable UUID spaceId,
        @RequestBody MutateAllocationCommand command,
        Authentication authentication
    ) {
        return service.mutate(
            (CurrentUser) authentication.getPrincipal(), spaceId, command
        );
    }

    @PostMapping("/rules")
    public CapacityRule rule(
        @PathVariable UUID spaceId,
        @RequestBody SaveCapacityRuleCommand command,
        Authentication authentication
    ) {
        return service.saveRule(
            (CurrentUser) authentication.getPrincipal(), spaceId, command
        );
    }
}
