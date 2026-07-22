package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationBatchListItemView;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationBatchView;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationConfirmationRequest;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.MigrationVerificationReportView;
import com.colla.platform.modules.project.api.ProjectMigrationDtos.ProjectLegacyProfileView;
import com.colla.platform.modules.project.application.ProjectLegacyProfileService;
import com.colla.platform.modules.project.application.ProjectSpaceMigrationService;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

    public AdminProjectMigrationController(
        ProjectLegacyProfileService projectLegacyProfileService,
        ProjectSpaceMigrationService projectSpaceMigrationService
    ) {
        this.projectLegacyProfileService = projectLegacyProfileService;
        this.projectSpaceMigrationService = projectSpaceMigrationService;
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
}
