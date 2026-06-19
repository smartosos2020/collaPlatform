package com.colla.platform.modules.permission.domain;

import java.util.UUID;

public final class PermissionModels {
    private PermissionModels() {
    }

    public record PermissionDecision(
        UUID workspaceId,
        String objectType,
        UUID objectId,
        String action,
        boolean allowed,
        String reason
    ) {
    }

    public record PermissionExplanation(
        String objectType,
        UUID objectId,
        String action,
        boolean allowed,
        String accessState,
        String reason,
        String currentLevel,
        String requiredLevel,
        String source
    ) {
    }
}
