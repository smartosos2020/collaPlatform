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

    public void requireManageUsers(CurrentUser user) {
        if (!canManageUsers(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User management permission required");
        }
    }
}
