package com.colla.platform.modules.permission.application;

import com.colla.platform.shared.auth.CurrentUser;
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
}
