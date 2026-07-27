package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemRelationMigrationService;
import com.colla.platform.modules.project.application.WorkItemRelationMigrationService.MigrationState;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}")
public class WorkItemRelationMigrationController {
    private final WorkItemRelationMigrationService service;

    public WorkItemRelationMigrationController(
        WorkItemRelationMigrationService service
    ) {
        this.service = service;
    }

    @PostMapping("/relation-migrations:plan")
    public MigrationState plan(
        @PathVariable UUID spaceId,
        @Valid @RequestBody PlanRequest request,
        Authentication authentication
    ) {
        return service.plan(
            user(authentication), spaceId, request.relationKey(), request.dryRun(),
            request.reason(), requestId()
        );
    }

    @GetMapping("/relation-migrations/{batchId}")
    public MigrationState get(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        Authentication authentication
    ) {
        return service.get(user(authentication), spaceId, batchId);
    }

    @PostMapping("/relation-migrations/{batchId}:execute")
    public MigrationState execute(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        @Valid @RequestBody MutationRequest request,
        Authentication authentication
    ) {
        return service.execute(
            user(authentication), spaceId, batchId, request.expectedVersion(),
            request.reason(), request.confirmation()
        );
    }

    @PostMapping("/relation-migrations/{batchId}:resume")
    public MigrationState resume(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        @Valid @RequestBody MutationRequest request,
        Authentication authentication
    ) {
        return service.resume(
            user(authentication), spaceId, batchId, request.expectedVersion(),
            request.reason(), request.confirmation()
        );
    }

    @PostMapping("/relation-migrations/{batchId}:verify")
    public MigrationState verify(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        @Valid @RequestBody VerifyRequest request,
        Authentication authentication
    ) {
        return service.verify(
            user(authentication), spaceId, batchId, request.expectedVersion()
        );
    }

    @PostMapping("/relation-migrations/{batchId}:rollback")
    public MigrationState rollback(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        @Valid @RequestBody MutationRequest request,
        Authentication authentication
    ) {
        return service.rollback(
            user(authentication), spaceId, batchId, request.expectedVersion(),
            request.reason(), request.confirmation()
        );
    }

    private CurrentUser user(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record PlanRequest(
        @NotBlank String relationKey,
        boolean dryRun,
        @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record MutationRequest(
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank String confirmation
    ) {
    }

    public record VerifyRequest(@PositiveOrZero long expectedVersion) {
    }
}
