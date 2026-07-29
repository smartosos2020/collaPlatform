package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LegacyExitAuditModels {
    private LegacyExitAuditModels() {
    }

    public record LegacySurface(
        String key,
        String layer,
        String owner,
        String accessMode,
        boolean userVisible,
        String removalStage,
        String evidence
    ) {
    }

    public record LegacyAuditObservation(
        Map<String, Long> totals,
        String sourceFingerprint
    ) {
    }

    public record LegacyAuditFinding(
        UUID id,
        String key,
        String category,
        String severity,
        String status,
        long affectedCount,
        Map<String, Object> safeDetail,
        Instant recordedAt
    ) {
    }

    public record RemovalDecision(
        UUID id,
        UUID snapshotId,
        String surfaceKey,
        String decision,
        String reason,
        String requestId,
        String requestHash,
        UUID decidedBy,
        Instant decidedAt,
        boolean replayed
    ) {
        public RemovalDecision replayedCopy() {
            return new RemovalDecision(
                id, snapshotId, surfaceKey, decision, reason, requestId, requestHash,
                decidedBy, decidedAt, true
            );
        }
    }

    public record LegacyAuditSnapshot(
        UUID id,
        UUID workspaceId,
        String inventoryVersion,
        String status,
        String sourceFingerprint,
        Map<String, Long> totals,
        List<LegacySurface> surfaces,
        List<LegacyAuditFinding> findings,
        List<RemovalDecision> decisions,
        UUID generatedBy,
        Instant generatedAt
    ) {
    }
}
