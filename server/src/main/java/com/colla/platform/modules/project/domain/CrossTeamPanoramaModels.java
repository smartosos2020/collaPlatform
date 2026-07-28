package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CrossTeamPanoramaModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SLICES = 200;
    public static final int MAX_AUDIT = 200;

    private CrossTeamPanoramaModels() {
    }

    public record SavePreferenceCommand(
        int schemaVersion, String requestId, long expectedVersion,
        boolean compact, int windowDays
    ) {
    }

    public record PanoramaPreference(boolean compact, int windowDays, long version) {
    }

    public record CollaborationSlice(
        String kind, UUID identity, UUID sourceSpaceId, UUID targetSpaceId,
        String status, long version, String source, Instant observedAt
    ) {
    }

    public record CollaborationAuditEntry(
        String kind, UUID identity, String status, long version,
        String source, Instant occurredAt
    ) {
    }

    public record PanoramaHealth(
        String status, int grants, int relations, int syncRules,
        int openConflicts, boolean truncated, String diagnostic
    ) {
    }

    public record CrossTeamPanorama(
        int schemaVersion,
        PanoramaPreference preference,
        List<CollaborationSlice> slices,
        List<CollaborationAuditEntry> audit,
        PanoramaHealth health,
        Instant observedAt
    ) {
    }
}
