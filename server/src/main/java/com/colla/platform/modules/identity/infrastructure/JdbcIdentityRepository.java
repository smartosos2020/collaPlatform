package com.colla.platform.modules.identity.infrastructure;

import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.domain.AuthModels.MemberDepartment;
import com.colla.platform.modules.identity.domain.AuthModels.MemberSummary;
import com.colla.platform.modules.identity.domain.AuthModels.DeviceSummary;
import com.colla.platform.modules.identity.domain.AuthModels.PushTokenSummary;
import com.colla.platform.shared.auth.ClientType;
import com.colla.platform.shared.auth.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIdentityRepository implements IdentityRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findUserByUsername(String username) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, username, password_hash, display_name, avatar_file_id, email, status
                    from users
                    where username = ? and deleted_at is null
                    """,
                this::mapUser,
                username
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserAccount> findUserById(UUID userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, username, password_hash, display_name, avatar_file_id, email, status
                    from users
                    where id = ? and deleted_at is null
                    """,
                this::mapUser,
                userId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CurrentUser> findCurrentUser(UUID userId, UUID deviceId) {
        return findUserById(userId)
            .filter(user -> "active".equals(user.status()))
            .filter(user -> isDeviceActive(user.workspaceId(), user.id(), deviceId))
            .map(user -> new CurrentUser(
                user.id(),
                user.workspaceId(),
                deviceId,
                user.username(),
                user.displayName(),
                findRoleCodes(user.workspaceId(), user.id()),
                findPermissionCodes(user.workspaceId(), user.id())
            ));
    }

    @Override
    public UUID upsertDevice(
        UUID workspaceId,
        UUID userId,
        ClientType deviceType,
        String deviceFingerprint,
        String deviceName,
        String appVersion
    ) {
        UUID id = findDeviceId(workspaceId, userId, deviceFingerprint).orElse(UUID.randomUUID());
        int updated = jdbcTemplate.update(
            """
                update user_devices
                set device_type = ?, device_name = ?, app_version = ?, last_active_at = now(), revoked_at = null
                where id = ?
                """,
            deviceType.name().toLowerCase(),
            deviceName,
            appVersion,
            id
        );
        if (updated == 0) {
            jdbcTemplate.update(
                """
                    insert into user_devices
                        (id, workspace_id, user_id, device_type, device_name, device_fingerprint, app_version, last_active_at, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, now(), now())
                    """,
                id,
                workspaceId,
                userId,
                deviceType.name().toLowerCase(),
                deviceName,
                deviceFingerprint,
                appVersion
            );
        }
        return id;
    }

    @Override
    public UUID createSession(
        UUID workspaceId,
        UUID userId,
        UUID deviceId,
        String refreshTokenHash,
        String userAgent,
        String ipAddress,
        Instant expiresAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into sessions
                    (id, workspace_id, user_id, device_id, refresh_token_hash, user_agent, ip_address, expires_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            id,
            workspaceId,
            userId,
            deviceId,
            refreshTokenHash,
            userAgent,
            ipAddress,
            Timestamp.from(expiresAt)
        );
        return id;
    }

    @Override
    public Optional<SessionRecord> findActiveSessionByRefreshTokenHash(String refreshTokenHash) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, user_id, device_id, expires_at, revoked_at
                    from sessions
                    where refresh_token_hash = ?
                    """,
                (rs, rowNum) -> new SessionRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("workspace_id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("device_id", UUID.class),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
                ),
                refreshTokenHash
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void revokeSession(UUID sessionId, Instant revokedAt) {
        jdbcTemplate.update("update sessions set revoked_at = ? where id = ?", Timestamp.from(revokedAt), sessionId);
    }

    @Override
    public boolean isDeviceActive(UUID workspaceId, UUID userId, UUID deviceId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from user_devices
                where workspace_id = ? and user_id = ? and id = ? and revoked_at is null
                """,
            Integer.class,
            workspaceId,
            userId,
            deviceId
        );
        return count != null && count > 0;
    }

    @Override
    public void updateLastLoginAt(UUID userId, Instant at) {
        jdbcTemplate.update("update users set last_login_at = ?, updated_at = now() where id = ?", Timestamp.from(at), userId);
    }

    @Override
    public boolean hasAnyUser() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from users where deleted_at is null", Integer.class);
        return count != null && count > 0;
    }

    @Override
    public UUID createUser(UUID workspaceId, String username, String passwordHash, String displayName, String email, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into users
                    (id, workspace_id, username, password_hash, display_name, email, status, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, 'active', ?, now(), ?, now())
                """,
            id,
            workspaceId,
            username,
            passwordHash,
            displayName,
            email,
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public void assignRole(UUID workspaceId, UUID userId, String roleCode, UUID createdBy) {
        UUID roleId = jdbcTemplate.queryForObject(
            "select id from roles where workspace_id = ? and code = ?",
            UUID.class,
            workspaceId,
            roleCode
        );
        Integer existing = jdbcTemplate.queryForObject(
            "select count(*) from user_roles where workspace_id = ? and user_id = ? and role_id = ?",
            Integer.class,
            workspaceId,
            userId,
            roleId
        );
        if (existing == null || existing == 0) {
            jdbcTemplate.update(
                "insert into user_roles (id, workspace_id, user_id, role_id, created_by, created_at) values (?, ?, ?, ?, ?, now())",
                UUID.randomUUID(),
                workspaceId,
                userId,
                roleId,
                createdBy
            );
        }
    }

    @Override
    public List<MemberSummary> listMembers(UUID workspaceId, UUID departmentId) {
        if (departmentId == null) {
            return jdbcTemplate.query(
                """
                    select id, username, display_name, avatar_file_id, email, status, last_login_at, created_at
                    from users
                    where workspace_id = ? and deleted_at is null
                    order by created_at desc
                    """,
                (rs, rowNum) -> mapMemberSummary(workspaceId, rs),
                workspaceId
            );
        }
        return jdbcTemplate.query(
            """
                select id, username, display_name, avatar_file_id, email, status, last_login_at, created_at
                from users
                where workspace_id = ? and deleted_at is null
                  and exists (
                      select 1
                      from department_members dm
                      where dm.workspace_id = users.workspace_id
                        and dm.user_id = users.id
                        and dm.department_id = ?
                        and dm.ended_at is null
                  )
                order by created_at desc
                """,
            (rs, rowNum) -> mapMemberSummary(workspaceId, rs),
            workspaceId,
            departmentId
        );
    }

    @Override
    public void updateUserStatus(UUID workspaceId, UUID userId, String status, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update users
                set status = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            status,
            updatedBy,
            workspaceId,
            userId
        );
    }

    @Override
    public void updatePassword(UUID workspaceId, UUID userId, String passwordHash, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update users
                set password_hash = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            passwordHash,
            updatedBy,
            workspaceId,
            userId
        );
    }

    @Override
    public void updateProfile(UUID workspaceId, UUID userId, String displayName, String email, UUID avatarFileId, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update users
                set display_name = ?, email = ?, avatar_file_id = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            displayName,
            email,
            avatarFileId,
            updatedBy,
            workspaceId,
            userId
        );
    }

    @Override
    public void updateAvatarFileId(UUID workspaceId, UUID userId, UUID avatarFileId, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update users
                set avatar_file_id = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            avatarFileId,
            updatedBy,
            workspaceId,
            userId
        );
    }

    @Override
    public Optional<UUID> findDefaultWorkspaceId() {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "select id from workspaces where slug = 'default'",
                UUID.class
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<DeviceSummary> listDevices(UUID workspaceId, UUID userId, UUID currentDeviceId) {
        return jdbcTemplate.query(
            """
                select d.id, d.device_type, d.device_name, d.device_fingerprint, d.app_version,
                       d.last_active_at, d.created_at, d.revoked_at,
                       coalesce((
                           select count(*)
                           from sessions s
                           where s.workspace_id = d.workspace_id
                             and s.device_id = d.id
                             and s.revoked_at is null
                             and s.expires_at > now()
                       ), 0) active_session_count,
                       coalesce((
                           select count(*)
                           from push_tokens pt
                           where pt.workspace_id = d.workspace_id
                             and pt.device_id = d.id
                             and pt.enabled = true
                             and pt.revoked_at is null
                       ), 0) enabled_push_token_count
                from user_devices d
                where d.workspace_id = ? and d.user_id = ?
                order by d.last_active_at desc nulls last, d.created_at desc
                """,
            (rs, rowNum) -> mapDevice(rs, currentDeviceId),
            workspaceId,
            userId
        );
    }

    @Override
    public Optional<DeviceSummary> findDevice(UUID workspaceId, UUID deviceId, UUID currentDeviceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select d.id, d.device_type, d.device_name, d.device_fingerprint, d.app_version,
                           d.last_active_at, d.created_at, d.revoked_at,
                           coalesce((
                               select count(*)
                               from sessions s
                               where s.workspace_id = d.workspace_id
                                 and s.device_id = d.id
                                 and s.revoked_at is null
                                 and s.expires_at > now()
                           ), 0) active_session_count,
                           coalesce((
                               select count(*)
                               from push_tokens pt
                               where pt.workspace_id = d.workspace_id
                                 and pt.device_id = d.id
                                 and pt.enabled = true
                                 and pt.revoked_at is null
                           ), 0) enabled_push_token_count
                    from user_devices d
                    where d.workspace_id = ? and d.id = ?
                    """,
                (rs, rowNum) -> mapDevice(rs, currentDeviceId),
                workspaceId,
                deviceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void revokeDevice(UUID workspaceId, UUID deviceId, Instant revokedAt) {
        jdbcTemplate.update(
            "update user_devices set revoked_at = ? where workspace_id = ? and id = ?",
            Timestamp.from(revokedAt),
            workspaceId,
            deviceId
        );
        jdbcTemplate.update(
            "update push_tokens set enabled = false, revoked_at = ?, updated_at = now() where workspace_id = ? and device_id = ? and revoked_at is null",
            Timestamp.from(revokedAt),
            workspaceId,
            deviceId
        );
    }

    @Override
    public void revokeDeviceSessions(UUID workspaceId, UUID deviceId, Instant revokedAt) {
        jdbcTemplate.update(
            "update sessions set revoked_at = ? where workspace_id = ? and device_id = ? and revoked_at is null",
            Timestamp.from(revokedAt),
            workspaceId,
            deviceId
        );
    }

    @Override
    public PushTokenSummary upsertPushToken(UUID workspaceId, UUID userId, UUID deviceId, String provider, String tokenHash) {
        Optional<PushTokenSummary> existing = findPushToken(workspaceId, deviceId, provider, tokenHash);
        UUID id = existing.map(PushTokenSummary::id).orElse(UUID.randomUUID());
        int updated = jdbcTemplate.update(
            """
                update push_tokens
                set enabled = true, token_encrypted = ?, updated_at = now(), revoked_at = null
                where id = ?
                """,
            tokenHash,
            id
        );
        if (updated == 0) {
            jdbcTemplate.update(
                """
                    insert into push_tokens
                        (id, workspace_id, user_id, device_id, provider, token_hash, token_encrypted, enabled, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, true, now(), now())
                    """,
                id,
                workspaceId,
                userId,
                deviceId,
                provider,
                tokenHash,
                tokenHash
            );
        }
        return findPushTokenById(id).orElseThrow();
    }

    @Override
    public List<PushTokenSummary> listPushTokens(UUID workspaceId, UUID userId, UUID deviceId) {
        return jdbcTemplate.query(
            """
                select id, device_id, provider, enabled, created_at, updated_at, revoked_at
                from push_tokens
                where workspace_id = ? and user_id = ? and device_id = ?
                order by updated_at desc
                """,
            this::mapPushToken,
            workspaceId,
            userId,
            deviceId
        );
    }

    private UserAccount mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserAccount(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getObject("avatar_file_id", UUID.class),
            rs.getString("email"),
            rs.getString("status")
        );
    }

    private DeviceSummary mapDevice(ResultSet rs, UUID currentDeviceId) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new DeviceSummary(
            id,
            ClientType.fromValue(rs.getString("device_type")),
            rs.getString("device_name"),
            rs.getString("device_fingerprint"),
            rs.getString("app_version"),
            rs.getTimestamp("last_active_at") == null ? null : rs.getTimestamp("last_active_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
            rs.getInt("active_session_count"),
            rs.getInt("enabled_push_token_count"),
            id.equals(currentDeviceId)
        );
    }

    private PushTokenSummary mapPushToken(ResultSet rs, int rowNum) throws SQLException {
        return new PushTokenSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("device_id", UUID.class),
            rs.getString("provider"),
            rs.getBoolean("enabled"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
        );
    }

    private MemberSummary mapMemberSummary(UUID workspaceId, ResultSet rs) throws SQLException {
        UUID userId = rs.getObject("id", UUID.class);
        return new MemberSummary(
            userId,
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getObject("avatar_file_id", UUID.class),
            rs.getString("email"),
            rs.getString("status"),
            rs.getTimestamp("last_login_at") == null ? null : rs.getTimestamp("last_login_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            findRoleCodes(workspaceId, userId),
            findMemberDepartments(workspaceId, userId)
        );
    }

    private Optional<PushTokenSummary> findPushToken(UUID workspaceId, UUID deviceId, String provider, String tokenHash) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, device_id, provider, enabled, created_at, updated_at, revoked_at
                    from push_tokens
                    where workspace_id = ? and device_id = ? and provider = ? and token_hash = ?
                    """,
                this::mapPushToken,
                workspaceId,
                deviceId,
                provider,
                tokenHash
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<PushTokenSummary> findPushTokenById(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, device_id, provider, enabled, created_at, updated_at, revoked_at
                    from push_tokens
                    where id = ?
                    """,
                this::mapPushToken,
                id
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<UUID> findDeviceId(UUID workspaceId, UUID userId, String deviceFingerprint) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id
                    from user_devices
                    where workspace_id = ? and user_id = ? and device_fingerprint = ?
                    """,
                UUID.class,
                workspaceId,
                userId,
                deviceFingerprint
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Set<String> findRoleCodes(UUID workspaceId, UUID userId) {
        return new HashSet<>(jdbcTemplate.queryForList(
            """
                with effective_roles as (
                    select ur.role_id
                    from user_roles ur
                    where ur.workspace_id = ? and ur.user_id = ?
                    union
                    select ra.role_id
                    from role_assignments ra
                    where ra.workspace_id = ?
                      and ra.subject_type = 'user'
                      and ra.subject_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                    union
                    select ra.role_id
                    from role_assignments ra
                    join departments d on d.id = ra.subject_id and d.deleted_at is null and d.status = 'active'
                    join department_members dm on dm.department_id = d.id and dm.ended_at is null
                    where ra.workspace_id = ?
                      and ra.subject_type = 'department'
                      and dm.user_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                    union
                    select ra.role_id
                    from role_assignments ra
                    join user_groups ug on ug.id = ra.subject_id and ug.deleted_at is null and ug.status = 'active'
                    join user_group_members ugm on ugm.group_id = ug.id and ugm.subject_type = 'user' and ugm.removed_at is null
                    where ra.workspace_id = ?
                      and ra.subject_type = 'user_group'
                      and ugm.subject_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                    union
                    select ra.role_id
                    from role_assignments ra
                    join user_groups ug on ug.id = ra.subject_id and ug.deleted_at is null and ug.status = 'active'
                    join user_group_members ugm on ugm.group_id = ug.id and ugm.subject_type = 'department' and ugm.removed_at is null
                    join departments d on d.id = ugm.subject_id and d.deleted_at is null and d.status = 'active'
                    join department_members dm on dm.department_id = d.id and dm.ended_at is null
                    where ra.workspace_id = ?
                      and ra.subject_type = 'user_group'
                      and dm.user_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                )
                select distinct r.code
                from effective_roles er
                join roles r on r.id = er.role_id
                where r.workspace_id = ? and r.status = 'active'
                """,
            String.class,
            workspaceId,
            userId,
            workspaceId,
            userId,
            workspaceId,
            userId,
            workspaceId,
            userId,
            workspaceId,
            userId,
            workspaceId
        ));
    }

    private Set<String> findPermissionCodes(UUID workspaceId, UUID userId) {
        return new HashSet<>(jdbcTemplate.queryForList(
            """
                with effective_roles as (
                    select ur.role_id
                    from user_roles ur
                    where ur.workspace_id = ? and ur.user_id = ?
                    union
                    select ra.role_id
                    from role_assignments ra
                    where ra.workspace_id = ?
                      and ra.subject_type = 'user'
                      and ra.subject_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                    union
                    select ra.role_id
                    from role_assignments ra
                    join departments d on d.id = ra.subject_id and d.deleted_at is null and d.status = 'active'
                    join department_members dm on dm.department_id = d.id and dm.ended_at is null
                    where ra.workspace_id = ?
                      and ra.subject_type = 'department'
                      and dm.user_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                    union
                    select ra.role_id
                    from role_assignments ra
                    join user_groups ug on ug.id = ra.subject_id and ug.deleted_at is null and ug.status = 'active'
                    join user_group_members ugm on ugm.group_id = ug.id and ugm.subject_type = 'user' and ugm.removed_at is null
                    where ra.workspace_id = ?
                      and ra.subject_type = 'user_group'
                      and ugm.subject_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                    union
                    select ra.role_id
                    from role_assignments ra
                    join user_groups ug on ug.id = ra.subject_id and ug.deleted_at is null and ug.status = 'active'
                    join user_group_members ugm on ugm.group_id = ug.id and ugm.subject_type = 'department' and ugm.removed_at is null
                    join departments d on d.id = ugm.subject_id and d.deleted_at is null and d.status = 'active'
                    join department_members dm on dm.department_id = d.id and dm.ended_at is null
                    where ra.workspace_id = ?
                      and ra.subject_type = 'user_group'
                      and dm.user_id = ?
                      and ra.status = 'active'
                      and ra.effective_at <= now()
                      and (ra.expires_at is null or ra.expires_at > now())
                )
                select distinct p.code
                from effective_roles er
                join roles r on r.id = er.role_id and r.workspace_id = ? and r.status = 'active'
                join role_permissions rp on rp.role_id = er.role_id
                join permissions p on p.id = rp.permission_id
                """,
            String.class,
            workspaceId,
            userId,
            workspaceId,
            userId,
            workspaceId,
            userId,
            workspaceId,
            userId,
            workspaceId,
            userId
            ,
            workspaceId
        ));
    }

    private List<MemberDepartment> findMemberDepartments(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select d.id department_id, d.code department_code, d.name department_name, dm.relation_type
                from department_members dm
                join departments d on d.id = dm.department_id
                where dm.workspace_id = ?
                  and dm.user_id = ?
                  and dm.ended_at is null
                  and d.deleted_at is null
                order by case dm.relation_type when 'primary' then 0 else 1 end, d.depth, d.sort_order, d.name
                """,
            (rs, rowNum) -> new MemberDepartment(
                rs.getObject("department_id", UUID.class),
                rs.getString("department_code"),
                rs.getString("department_name"),
                rs.getString("relation_type")
            ),
            workspaceId,
            userId
        );
    }
}
