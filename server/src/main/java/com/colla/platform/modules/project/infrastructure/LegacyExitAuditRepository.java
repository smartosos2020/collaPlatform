package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditFinding;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditObservation;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditSnapshot;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacySurface;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.RemovalDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegacyExitAuditRepository {
    LegacyAuditObservation observe(UUID workspaceId);

    LegacyAuditSnapshot insertSnapshot(
        UUID workspaceId,
        String inventoryVersion,
        LegacyAuditObservation observation,
        List<LegacySurface> surfaces,
        List<LegacyAuditFinding> findings,
        UUID actorId
    );

    Optional<LegacyAuditSnapshot> findSnapshot(UUID workspaceId, UUID snapshotId);

    List<LegacyAuditSnapshot> listSnapshots(UUID workspaceId, int limit);

    Optional<RemovalDecision> findDecisionByRequest(UUID workspaceId, String requestId);

    RemovalDecision insertDecision(
        UUID workspaceId,
        UUID snapshotId,
        String surfaceKey,
        String decision,
        String reason,
        String requestId,
        String requestHash,
        UUID actorId
    );
}
