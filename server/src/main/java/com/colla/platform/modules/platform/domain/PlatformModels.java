package com.colla.platform.modules.platform.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class PlatformModels {
    private PlatformModels() {
    }

    public enum ObjectAccessState {
        available,
        forbidden,
        deleted,
        not_found,
        invalid
    }

    public record PlatformObjectSummary(
        String objectType,
        UUID objectId,
        ObjectAccessState accessState,
        String title,
        String subtitle,
        String status,
        String webPath,
        String deepLink,
        Map<String, Object> metadata
    ) {
        public static PlatformObjectSummary unavailable(String objectType, UUID objectId, ObjectAccessState state) {
            return new PlatformObjectSummary(objectType, objectId, state, null, null, null, null, null, Map.of());
        }
    }

    public record ObjectLinkRecord(
        UUID id,
        UUID workspaceId,
        String objectType,
        UUID objectId,
        String webPath,
        String deepLink,
        String titleSnapshot,
        Instant deletedAt
    ) {
    }

    public record ParsedInternalLink(
        boolean resolved,
        String source,
        String objectType,
        UUID objectId,
        String webPath,
        String deepLink,
        PlatformObjectSummary summary
    ) {
        public static ParsedInternalLink unresolved(String source) {
            return new ParsedInternalLink(false, source, null, null, null, null, null);
        }
    }

    public record PlatformObjectReference(
        String objectType,
        UUID objectId,
        String webPath,
        String deepLink,
        String titleSnapshot,
        Instant touchedAt
    ) {
    }

    public record PlatformObjectNavigation(
        PlatformObjectSummary summary,
        String webPath,
        String deepLink,
        String mobileFallbackPath
    ) {
    }
}
