package com.colla.platform.modules.permission.contract;

import com.colla.platform.shared.auth.CurrentUser;

/**
 * Project-administration authorization boundary.
 */
public interface ProjectAuthorization {

    void requireCreateProjects(CurrentUser user);

    void requireManageProjects(CurrentUser user);
}
