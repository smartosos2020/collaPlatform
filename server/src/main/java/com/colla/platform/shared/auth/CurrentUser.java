package com.colla.platform.shared.auth;

import java.util.Set;
import java.util.UUID;

public record CurrentUser(
    UUID id,
    UUID workspaceId,
    UUID deviceId,
    String username,
    String displayName,
    Set<String> roles,
    Set<String> permissions
) {
    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode);
    }
}
