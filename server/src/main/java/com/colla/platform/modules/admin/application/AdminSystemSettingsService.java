package com.colla.platform.modules.admin.application;

import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenProperties;
import com.colla.platform.shared.auth.PasswordPolicy;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminSystemSettingsService {
    private final JdbcTemplate jdbcTemplate;
    private final PermissionService permissionService;
    private final JwtTokenProperties tokenProperties;
    private final PasswordPolicy passwordPolicy;

    public AdminSystemSettingsService(
        JdbcTemplate jdbcTemplate,
        PermissionService permissionService,
        JwtTokenProperties tokenProperties,
        PasswordPolicy passwordPolicy
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.tokenProperties = tokenProperties;
        this.passwordPolicy = passwordPolicy;
    }

    public AdminSystemSettingsView view(CurrentUser currentUser) {
        permissionService.requireViewOrganization(currentUser);
        WorkspaceInfo workspace = jdbcTemplate.queryForObject(
            """
                select id, name, slug, status, created_at, updated_at
                from workspaces
                where id = ?
                """,
            (rs, rowNum) -> new WorkspaceInfo(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            ),
            currentUser.workspaceId()
        );
        long memberCount = count("select count(*) from users where workspace_id = ? and deleted_at is null", currentUser.workspaceId());
        long activeSessionCount = count("select count(*) from sessions where workspace_id = ? and revoked_at is null and expires_at > now()", currentUser.workspaceId());
        long activeDeviceCount = count("select count(*) from user_devices where workspace_id = ? and revoked_at is null", currentUser.workspaceId());
        return new AdminSystemSettingsView(
            workspace,
            new SecurityPolicy(
                passwordPolicy.getMinLength(),
                passwordPolicy.isRequireLetter(),
                passwordPolicy.isRequireDigit(),
                tokenProperties.getAccessTokenTtlMinutes(),
                tokenProperties.getRefreshTokenTtlDays(),
                true,
                true
            ),
            new RuntimeInfo("colla-platform", "健康检查由 /api/health 提供", memberCount, activeSessionCount, activeDeviceCount)
        );
    }

    private long count(String sql, UUID workspaceId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, workspaceId);
        return value == null ? 0 : value;
    }

    public record AdminSystemSettingsView(WorkspaceInfo workspace, SecurityPolicy securityPolicy, RuntimeInfo runtime) {
    }

    public record WorkspaceInfo(UUID id, String name, String slug, String status, Instant createdAt, Instant updatedAt) {
    }

    public record SecurityPolicy(
        int passwordMinLength,
        boolean passwordRequireLetter,
        boolean passwordRequireDigit,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays,
        boolean requiredSecurityNotifications,
        boolean requiredSystemNotifications
    ) {
    }

    public record RuntimeInfo(String service, String healthEndpoint, long memberCount, long activeSessionCount, long activeDeviceCount) {
    }
}
