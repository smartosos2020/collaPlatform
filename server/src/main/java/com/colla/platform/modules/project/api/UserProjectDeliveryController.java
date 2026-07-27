package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectDeliveryService;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Deliverable;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MutateCommand;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/deliverables")
public final class UserProjectDeliveryController {
    private final ProjectDeliveryService service;

    public UserProjectDeliveryController(ProjectDeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeliverableSummary> list(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.list(currentUser(authentication), spaceId);
    }

    @GetMapping("/{deliverableId}")
    public Deliverable get(
        @PathVariable UUID spaceId,
        @PathVariable UUID deliverableId,
        Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId, deliverableId);
    }

    @PostMapping
    public Deliverable create(
        @PathVariable UUID spaceId,
        @RequestBody CreateCommand command,
        Authentication authentication
    ) {
        return service.create(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/{deliverableId}:mutate")
    public Deliverable mutate(
        @PathVariable UUID spaceId,
        @PathVariable UUID deliverableId,
        @RequestBody MutateCommand command,
        Authentication authentication
    ) {
        return service.mutate(
            currentUser(authentication), spaceId, deliverableId, command
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
