package com.colla.platform.modules.permission.application;

import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionActionCategory;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionService {
    public boolean canAccessAdmin(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("admin.access");
    }

    public boolean canManageUsers(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("user.manage");
    }

    public boolean canViewOrganization(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("org.view") || canManageOrganization(user);
    }

    public boolean canManageOrganization(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("org.manage");
    }

    public boolean canViewUserGroups(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("usergroup.view") || canManageUserGroups(user);
    }

    public boolean canManageUserGroups(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("usergroup.manage");
    }

    public boolean canViewRoles(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("role.view") || canManageRoles(user);
    }

    public boolean canManageRoles(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("role.manage");
    }

    public boolean canInspectPermissions(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("permission.inspect") || canViewRoles(user);
    }

    public boolean canCreateProjects(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("project.create");
    }

    public boolean canManageProjects(CurrentUser user) {
        return user.hasRole("admin") || user.hasPermission("project.manage");
    }

    public void requireManageUsers(CurrentUser user) {
        if (!canManageUsers(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User management permission required");
        }
    }

    public void requireViewOrganization(CurrentUser user) {
        if (!canViewOrganization(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization view permission required");
        }
    }

    public void requireManageOrganization(CurrentUser user) {
        if (!canManageOrganization(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization management permission required");
        }
    }

    public void requireViewUserGroups(CurrentUser user) {
        if (!canViewUserGroups(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User group view permission required");
        }
    }

    public void requireManageUserGroups(CurrentUser user) {
        if (!canManageUserGroups(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User group management permission required");
        }
    }

    public void requireViewRoles(CurrentUser user) {
        if (!canViewRoles(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role view permission required");
        }
    }

    public void requireManageRoles(CurrentUser user) {
        if (!canManageRoles(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role management permission required");
        }
    }

    public void requireInspectPermissions(CurrentUser user) {
        if (!canInspectPermissions(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission inspect permission required");
        }
    }

    public void requireCreateProjects(CurrentUser user) {
        if (!canCreateProjects(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project creation permission required");
        }
    }

    public void requireManageProjects(CurrentUser user) {
        if (!canManageProjects(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project governance permission required");
        }
    }

    public PermissionActionCategory categorizeAction(String objectType, String action) {
        String normalizedObjectType = normalize(objectType);
        String normalizedAction = normalize(action);
        if (ListHolder.SUPER_ADMIN_ACTIONS.contains(normalizedAction)) {
            return PermissionActionCategory.super_admin;
        }
        if (normalizedObjectType.startsWith("admin") || ListHolder.ADMIN_OBJECTS.contains(normalizedObjectType)
            || ListHolder.ADMIN_ACTIONS.contains(normalizedAction)) {
            return PermissionActionCategory.admin_management;
        }
        if (ListHolder.SPACE_OBJECTS.contains(normalizedObjectType) || ListHolder.SPACE_ACTIONS.contains(normalizedAction)) {
            return PermissionActionCategory.space_management;
        }
        if (ListHolder.OBJECT_MANAGEMENT_ACTIONS.contains(normalizedAction)) {
            return PermissionActionCategory.object_management;
        }
        return PermissionActionCategory.user_action;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ListHolder {
        private static final java.util.Set<String> SUPER_ADMIN_ACTIONS = java.util.Set.of("super_admin", "impersonate", "security_override");
        private static final java.util.Set<String> ADMIN_ACTIONS = java.util.Set.of("admin", "audit", "inspect", "govern", "configure");
        private static final java.util.Set<String> ADMIN_OBJECTS = java.util.Set.of("user", "role", "department", "user_group", "permission", "audit_log");
        private static final java.util.Set<String> SPACE_OBJECTS = java.util.Set.of("knowledge_base", "workspace", "space");
        private static final java.util.Set<String> SPACE_ACTIONS = java.util.Set.of("archive_space", "manage_space", "space_permission");
        private static final java.util.Set<String> OBJECT_MANAGEMENT_ACTIONS = java.util.Set.of("manage", "share", "permission", "delete", "archive", "restore");
    }
}
