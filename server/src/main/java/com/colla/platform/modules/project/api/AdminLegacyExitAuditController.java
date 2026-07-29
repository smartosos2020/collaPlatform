package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.LegacyExitAuditService;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditSnapshot;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.RemovalDecision;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/project-migrations/legacy-audit")
public class AdminLegacyExitAuditController {
    private final LegacyExitAuditService service;

    public AdminLegacyExitAuditController(LegacyExitAuditService service) {
        this.service = service;
    }

    @GetMapping("/snapshots")
    public List<LegacyAuditSnapshot> snapshots(Authentication authentication) {
        return service.list(currentUser(authentication));
    }

    @PostMapping("/snapshots")
    public LegacyAuditSnapshot createSnapshot(Authentication authentication) {
        return service.createSnapshot(currentUser(authentication));
    }

    @GetMapping("/snapshots/{snapshotId}")
    public LegacyAuditSnapshot snapshot(
        @PathVariable UUID snapshotId,
        Authentication authentication
    ) {
        return service.get(currentUser(authentication), snapshotId);
    }

    @GetMapping("/snapshots/{snapshotId}/export")
    public ResponseEntity<LegacyAuditSnapshot> export(
        @PathVariable UUID snapshotId,
        Authentication authentication
    ) {
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"legacy-audit-" + snapshotId + ".json\""
            )
            .body(service.get(currentUser(authentication), snapshotId));
    }

    @PostMapping("/snapshots/{snapshotId}/decisions")
    public RemovalDecision decide(
        @PathVariable UUID snapshotId,
        @Valid @RequestBody RemovalDecisionRequest request,
        Authentication authentication
    ) {
        return service.decide(
            currentUser(authentication), snapshotId, request.surfaceKey(), request.decision(),
            request.reason(), request.requestId()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record RemovalDecisionRequest(
        @NotBlank String surfaceKey,
        @NotBlank String decision,
        @Size(min = 10, max = 1000) String reason,
        @Size(min = 8, max = 160) String requestId
    ) {
    }
}
