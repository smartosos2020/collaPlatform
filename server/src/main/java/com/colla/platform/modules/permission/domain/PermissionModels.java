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
}
