package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationBatchListItemView;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationBatchView;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationConfirmationRequest;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationVerificationReportView;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.ProjectLegacyProfileView;
import com.colla.platform.modules.project.application.ProjectLegacyProfileService;
import com.colla.platform.modules.project.application.ProjectSpaceMigrationService;
import com.colla.platform.modules.project.application.WorkItemMigrationService;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationBatch;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationExecution;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationFailure;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationVerification;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/project-migrations")
public class AdminProjectMigrationController {
    private static final String EXECUTE_CONFIRMATION = "EXECUTE";
    private static final String ROLLBACK_CONFIRMATION = "ROLLBACK";

    private final ProjectLegacyProfileService projectLegacyProfileService;
    private final ProjectSpaceMigrationService projectSpaceMigrationService;
    private final WorkItemMigrationService workItemMigrationService;

    public AdminProjectMigrationController(
        ProjectLegacyProfileService projectLegacyProfileService,
        ProjectSpaceMigrationService projectSpaceMigrationService,
        WorkItemMigrationService workItemMigrationService
    ) {
        this.projectLegacyProfileService = projectLegacyProfileService;
        this.projectSpaceMigrationService = projectSpaceMigrationService;
        this.workItemMigrationService = workItemMigrationService;
    }

    @GetMapping("/profile")
    public ProjectLegacyProfileView profile(Authentication authentication) {
        return ProjectMigrationDtos.profile(projectLegacyProfileService.generateProfile(currentUser(authentication)));
    }

    @GetMapping("/batches")
    public List<MigrationBatchListItemView> batches(Authentication authentication) {
        return projectSpaceMigrationService.listBatches(currentUser(authentication)).stream()
            .map(ProjectMigrationDtos::batchListItem)
            .toList();
    }

    @GetMapping("/batches/{batchId}")
    public MigrationBatchView batch(@PathVariable UUID batchId, Authentication authentication) {
        return ProjectMigrationDtos.batch(
            projectSpaceMigrationService.getBatch(currentUser(authentication), batchId)
        );
    }

    @PostMapping("/spaces:dry-run")
    public MigrationBatchView dryRun(Authentication authentication) {
        return ProjectMigrationDtos.batch(projectSpaceMigrationService.dryRun(currentUser(authentication)));
    }

    @PostMapping("/spaces:execute")
    public MigrationBatchView execute(
        @RequestBody(required = false) MigrationConfirmationRequest request,
        Authentication authentication
    ) {
        requireConfirmation(request, EXECUTE_CONFIRMATION);
        return ProjectMigrationDtos.batch(projectSpaceMigrationService.execute(currentUser(authentication)));
    }

    @PostMapping("/batches/{batchId}:resume")
    public MigrationBatchView resume(@PathVariable UUID batchId, Authentication authentication) {
        return ProjectMigrationDtos.batch(
            projectSpaceMigrationService.resume(currentUser(authentication), batchId)
        );
    }

    @PostMapping("/batches/{batchId}:verify")
    public MigrationVerificationReportView verify(@PathVariable UUID batchId, Authentication authentication) {
        return ProjectMigrationDtos.verificationReport(
            projectSpaceMigrationService.verify(currentUser(authentication), batchId)
        );
    }

    @PostMapping("/workspaces:verify-convergence")
    public MigrationVerificationReportView verifyWorkspaceConvergence(Authentication authentication) {
        return ProjectMigrationDtos.verificationReport(
            projectSpaceMigrationService.verifyWorkspaceConvergence(currentUser(authentication))
        );
    }

    @PostMapping("/batches/{batchId}:rollback")
    public MigrationBatchView rollback(
        @PathVariable UUID batchId,
        @RequestBody(required = false) MigrationConfirmationRequest request,
        Authentication authentication
    ) {
        requireConfirmation(request, ROLLBACK_CONFIRMATION);
        return ProjectMigrationDtos.batch(
            projectSpaceMigrationService.rollback(currentUser(authentication), batchId)
        );
    }

    @GetMapping("/work-items/batches")
    public List<MigrationBatch> workItemBatches(Authentication authentication) {
        return workItemMigrationService.list(currentUser(authentication));
    }

    @GetMapping("/work-items/batches/{batchId}")
    public MigrationBatch workItemBatch(
        @PathVariable UUID batchId,
        Authentication authentication
    ) {
        return workItemMigrationService.get(currentUser(authentication), batchId);
    }

    @GetMapping("/work-items/batches/{batchId}/failures")
    public ResponseEntity<List<MigrationFailure>> workItemFailures(
        @PathVariable UUID batchId,
        Authentication authentication
    ) {
        List<MigrationFailure> failures =
            workItemMigrationService.get(currentUser(authentication), batchId).failures();
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"work-item-migration-" + batchId + "-failures.json\""
            )
            .body(failures);
    }

    @PostMapping("/work-items:plan")
    public MigrationBatch planWorkItems(
        @Valid @RequestBody(required = false) WorkItemPlanRequest request,
        Authentication authentication
    ) {
        WorkItemPlanRequest input = request == null
            ? new WorkItemPlanRequest(false, 0, Set.of())
            : request;
        return workItemMigrationService.plan(
            currentUser(authentication), input.dryRun(), input.throttleMillis(), input.projectIds()
        );
    }

    @PostMapping("/work-items/batches/{batchId}:execute")
    public MigrationExecution executeWorkItems(
        @PathVariable UUID batchId,
        @Valid @RequestBody WorkItemExecuteRequest request,
        Authentication authentication
    ) {
        requireConfirmation(
            new MigrationConfirmationRequest(request.confirmation()), EXECUTE_CONFIRMATION
        );
        return workItemMigrationService.execute(
            currentUser(authentication), batchId, request.workerId()
        );
    }

    @PostMapping("/work-items/batches/{batchId}:pause")
    public MigrationBatch pauseWorkItems(
        @PathVariable UUID batchId,
        @RequestBody(required = false) WorkItemPauseRequest request,
        Authentication authentication
    ) {
        return workItemMigrationService.pause(
            currentUser(authentication), batchId, request == null ? null : request.reason()
        );
    }

    @PostMapping("/work-items/batches/{batchId}:verify")
    public MigrationVerification verifyWorkItems(
        @PathVariable UUID batchId,
        Authentication authentication
    ) {
        return workItemMigrationService.verifyBatch(currentUser(authentication), batchId);
    }

    @PostMapping("/work-items:verify-convergence")
    public MigrationVerification verifyWorkItemConvergence(Authentication authentication) {
        return workItemMigrationService.verifyConvergence(currentUser(authentication));
    }

    @PostMapping("/work-items/batches/{batchId}:rollback")
    public MigrationBatch rollbackWorkItems(
        @PathVariable UUID batchId,
        @RequestBody(required = false) MigrationConfirmationRequest request,
        Authentication authentication
    ) {
        requireConfirmation(request, ROLLBACK_CONFIRMATION);
        return workItemMigrationService.rollback(currentUser(authentication), batchId, true);
    }

    private void requireConfirmation(MigrationConfirmationRequest request, String expected) {
        String confirmation = request == null ? null : request.confirmation();
        if (!expected.equals(confirmation)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "High-risk operation requires the request body to contain \"confirmation\": \"" + expected + "\""
            );
        }
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record WorkItemPlanRequest(
        boolean dryRun,
        @Min(0) @Max(60000) int throttleMillis,
        Set<UUID> projectIds
    ) {
        public WorkItemPlanRequest {
            projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
        }
    }

    public record WorkItemExecuteRequest(String confirmation, String workerId) {
    }

    public record WorkItemPauseRequest(String reason) {
    }
}
