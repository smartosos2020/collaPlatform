package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CrossSpaceGrantModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_GRANTS = 100;
    public static final int MAX_TYPE_SCOPES = 32;
    public static final int MAX_INSTANCE_SCOPES = 100;

    private CrossSpaceGrantModels() {
    }

    public record SaveGrantCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        UUID grantId,
        UUID targetSpaceId,
        String name,
        JsonNode scope
    ) {
    }

    public record GrantLifecycleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action,
        String party,
        String reason
    ) {
    }

    public record CrossSpaceGrant(
        UUID id,
        UUID sourceSpaceId,
        UUID targetSpaceId,
        String name,
        String status,
        int currentVersion,
        boolean sourceConfirmed,
        boolean targetConfirmed,
        UUID sourceConfirmedBy,
        UUID targetConfirmedBy,
        JsonNode scope,
        String scopeHash,
        UUID updatedBy,
        Instant updatedAt,
        Instant revokedAt,
        Instant archivedAt
    ) {
    }

    public record GrantVersion(
        int versionNumber,
        JsonNode scope,
        String scopeHash,
        UUID createdBy,
        Instant createdAt
    ) {
    }

    public record GrantHistory(
        CrossSpaceGrant grant,
        List<GrantVersion> versions
    ) {
    }

    public record GrantFoundation(
        int schemaVersion,
        List<String> directions,
        List<String> operations,
        List<CrossSpaceGrant> grants,
        boolean truncated
    ) {
    }
}
