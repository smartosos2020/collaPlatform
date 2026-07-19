package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceInvitation;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcProjectSpaceMembershipRepository implements ProjectSpaceMembershipRepository {
    private static final String MEMBER_SELECT = """
        select m.id, m.space_id, m.user_id, u.username, u.display_name, u.avatar_file_id, u.email,
               u.status user_status, m.status member_status, r.role_key, m.joined_at, m.removed_at, m.updated_at
          from project_space_members m
          join users u on u.workspace_id = m.workspace_id and u.id = m.user_id
          left join project_space_role_assignments r on r.workspace_id = m.workspace_id
               and r.space_id = m.space_id and r.member_id = m.id and r.revoked_at is null
        """;
    private static final String INVITATION_SELECT = """
        select i.id, i.space_id, i.invitee_user_id, u.display_name invitee_display_name,
               coalesce(i.invitee_email, u.email) invitee_email, i.role_key, i.status, i.expires_at,
               i.invited_by, i.invited_at, i.responded_at, i.revoked_by, i.revoked_at,
               i.version, i.updated_at, i.last_sent_at
          from project_space_invitations i
          left join users u on u.workspace_id = i.workspace_id and u.id = i.invitee_user_id
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectSpaceMembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String lockSpaceStatus(UUID workspaceId, UUID spaceId) {
        try {
            return jdbcTemplate.queryForObject(
                "select status from project_spaces where workspace_id = ? and id = ? for update",
                String.class,
                workspaceId,
                spaceId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project space not found");
        }
    }

    @Override
    public List<ProjectSpaceMember> listMembers(UUID workspaceId, UUID spaceId) {
        return jdbcTemplate.query(
            MEMBER_SELECT + " where m.workspace_id = ? and m.space_id = ? order by m.status, m.joined_at, m.id",
            this::mapMember,
            workspaceId,
            spaceId
        );
    }

    @Override
    public Optional<ProjectSpaceMember> findMember(UUID workspaceId, UUID spaceId, UUID memberId) {
        return optionalMember(
            MEMBER_SELECT + " where m.workspace_id = ? and m.space_id = ? and m.id = ?",
            workspaceId, spaceId, memberId
        );
    }

    @Override
    public Optional<ProjectSpaceMember> findMemberByUser(UUID workspaceId, UUID spaceId, UUID userId) {
        return optionalMember(
            MEMBER_SELECT + " where m.workspace_id = ? and m.space_id = ? and m.user_id = ?",
            workspaceId, spaceId, userId
        );
    }

    @Override
    public long countActiveOwners(UUID workspaceId, UUID spaceId) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                  from project_space_members m
                  join project_space_role_assignments r on r.workspace_id = m.workspace_id
                       and r.space_id = m.space_id and r.member_id = m.id and r.revoked_at is null
                  join users u on u.workspace_id = m.workspace_id and u.id = m.user_id and u.status = 'active'
                 where m.workspace_id = ? and m.space_id = ? and m.status = 'active' and r.role_key = 'owner'
                """,
            Long.class,
            workspaceId,
            spaceId
        );
        return count == null ? 0 : count;
    }

    @Override
    public UUID createMember(UUID workspaceId, UUID spaceId, UUID userId, String roleKey, UUID actorId) {
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId, workspaceId, spaceId, userId, actorId, actorId
        );
        insertRole(workspaceId, spaceId, memberId, roleKey, actorId);
        return memberId;
    }

    @Override
    public void reactivateMember(UUID workspaceId, UUID spaceId, UUID memberId, String roleKey, UUID actorId) {
        jdbcTemplate.update(
            """
                update project_space_members
                   set status = 'active', joined_at = now(), removed_at = null,
                       updated_by = ?, updated_at = now()
                 where workspace_id = ? and space_id = ? and id = ? and status = 'removed'
                """,
            actorId, workspaceId, spaceId, memberId
        );
        insertRole(workspaceId, spaceId, memberId, roleKey, actorId);
    }

    @Override
    public void changeRole(UUID workspaceId, UUID spaceId, UUID memberId, String roleKey, UUID actorId) {
        jdbcTemplate.update(
            """
                update project_space_role_assignments
                   set role_key = ?, assigned_by = ?, assigned_at = now()
                 where workspace_id = ? and space_id = ? and member_id = ? and revoked_at is null
                """,
            roleKey, actorId, workspaceId, spaceId, memberId
        );
        jdbcTemplate.update(
            "update project_space_members set updated_by = ?, updated_at = now() where workspace_id = ? and space_id = ? and id = ?",
            actorId, workspaceId, spaceId, memberId
        );
    }

    @Override
    public void removeMember(UUID workspaceId, UUID spaceId, UUID memberId, UUID actorId) {
        jdbcTemplate.update(
            """
                update project_space_role_assignments
                   set revoked_by = ?, revoked_at = now()
                 where workspace_id = ? and space_id = ? and member_id = ? and revoked_at is null
                """,
            actorId, workspaceId, spaceId, memberId
        );
        jdbcTemplate.update(
            """
                update project_space_members
                   set status = 'removed', removed_at = now(), updated_by = ?, updated_at = now()
                 where workspace_id = ? and space_id = ? and id = ? and status = 'active'
                """,
            actorId, workspaceId, spaceId, memberId
        );
    }

    @Override
    public void transferOwner(UUID workspaceId, UUID spaceId, UUID ownerMemberId, UUID targetMemberId, UUID actorId) {
        changeRole(workspaceId, spaceId, targetMemberId, "owner", actorId);
        changeRole(workspaceId, spaceId, ownerMemberId, "admin", actorId);
    }

    @Override
    public List<UUID> listSoleOwnerSpaceIds(UUID workspaceId, UUID userId) {
        return jdbcTemplate.queryForList(
            """
                select m.space_id
                  from project_space_members m
                  join project_space_role_assignments r on r.workspace_id = m.workspace_id
                       and r.space_id = m.space_id and r.member_id = m.id and r.revoked_at is null
                 where m.workspace_id = ? and m.user_id = ? and m.status = 'active' and r.role_key = 'owner'
                   and 1 = (
                       select count(*)
                         from project_space_members owners
                         join project_space_role_assignments owner_roles on owner_roles.workspace_id = owners.workspace_id
                              and owner_roles.space_id = owners.space_id and owner_roles.member_id = owners.id
                              and owner_roles.revoked_at is null and owner_roles.role_key = 'owner'
                         join users owner_users on owner_users.workspace_id = owners.workspace_id
                              and owner_users.id = owners.user_id and owner_users.status = 'active'
                        where owners.workspace_id = m.workspace_id and owners.space_id = m.space_id
                          and owners.status = 'active'
                   )
                """,
            UUID.class,
            workspaceId,
            userId
        );
    }

    @Override
    public List<ProjectSpaceInvitation> listInvitations(UUID workspaceId, UUID spaceId) {
        return jdbcTemplate.query(
            INVITATION_SELECT + " where i.workspace_id = ? and i.space_id = ? order by i.updated_at desc, i.id",
            this::mapInvitation,
            workspaceId,
            spaceId
        );
    }

    @Override
    public Optional<ProjectSpaceInvitation> findInvitation(UUID workspaceId, UUID invitationId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                INVITATION_SELECT + " where i.workspace_id = ? and i.id = ?",
                this::mapInvitation,
                workspaceId,
                invitationId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ProjectSpaceInvitation> findPendingInvitationByUser(UUID workspaceId, UUID spaceId, UUID userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                INVITATION_SELECT + " where i.workspace_id = ? and i.space_id = ? and i.invitee_user_id = ? and i.status = 'pending'",
                this::mapInvitation,
                workspaceId,
                spaceId,
                userId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public ProjectSpaceInvitation createInvitation(
        UUID workspaceId,
        UUID spaceId,
        UUID inviteeUserId,
        String roleKey,
        String tokenHash,
        Instant expiresAt,
        UUID actorId,
        String requestId
    ) {
        UUID invitationId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_invitations
                    (id, workspace_id, space_id, invitee_user_id, role_key, token_hash, status,
                     expires_at, invited_by, invited_at, updated_at, last_sent_at, last_request_id)
                values (?, ?, ?, ?, ?, ?, 'pending', ?, ?, now(), now(), now(), ?)
                """,
            invitationId, workspaceId, spaceId, inviteeUserId, roleKey, tokenHash,
            Timestamp.from(expiresAt), actorId, requestId
        );
        return findInvitation(workspaceId, invitationId).orElseThrow();
    }

    @Override
    public boolean resendInvitation(UUID workspaceId, UUID invitationId, String tokenHash, Instant expiresAt, String requestId) {
        return jdbcTemplate.update(
            """
                update project_space_invitations
                   set token_hash = ?, expires_at = ?, version = version + 1,
                       updated_at = now(), last_sent_at = now(), last_request_id = ?
                 where workspace_id = ? and id = ? and status = 'pending'
                   and last_request_id is distinct from ?
                """,
            tokenHash, Timestamp.from(expiresAt), requestId, workspaceId, invitationId, requestId
        ) > 0;
    }

    @Override
    public void updateInvitationStatus(UUID workspaceId, UUID invitationId, String status, UUID actorId) {
        jdbcTemplate.update(
            """
                update project_space_invitations
                   set status = ?, responded_at = case when ? in ('accepted', 'rejected') then now() else responded_at end,
                       revoked_by = case when ? = 'revoked' then ? else revoked_by end,
                       revoked_at = case when ? = 'revoked' then now() else revoked_at end,
                       version = version + 1, updated_at = now()
                 where workspace_id = ? and id = ? and status = 'pending'
                """,
            status, status, status, actorId, status, workspaceId, invitationId
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireInvitations(UUID workspaceId, UUID spaceId) {
        return jdbcTemplate.update(
            """
                update project_space_invitations
                   set status = 'expired', version = version + 1, updated_at = now()
                 where workspace_id = ? and space_id = ? and status = 'pending' and expires_at <= now()
                """,
            workspaceId, spaceId
        );
    }

    private Optional<ProjectSpaceMember> optionalMember(String sql, Object... arguments) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapMember, arguments));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private void insertRole(UUID workspaceId, UUID spaceId, UUID memberId, String roleKey, UUID actorId) {
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments
                    (id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at)
                values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, memberId, roleKey, actorId
        );
    }

    private ProjectSpaceMember mapMember(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectSpaceMember(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("username"),
            resultSet.getString("display_name"),
            resultSet.getObject("avatar_file_id", UUID.class),
            resultSet.getString("email"),
            resultSet.getString("user_status"),
            resultSet.getString("member_status"),
            resultSet.getString("role_key"),
            resultSet.getTimestamp("joined_at").toInstant(),
            instant(resultSet.getTimestamp("removed_at")),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private ProjectSpaceInvitation mapInvitation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectSpaceInvitation(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("invitee_user_id", UUID.class),
            resultSet.getString("invitee_display_name"),
            resultSet.getString("invitee_email"),
            resultSet.getString("role_key"),
            resultSet.getString("status"),
            resultSet.getTimestamp("expires_at").toInstant(),
            resultSet.getObject("invited_by", UUID.class),
            resultSet.getTimestamp("invited_at").toInstant(),
            instant(resultSet.getTimestamp("responded_at")),
            resultSet.getObject("revoked_by", UUID.class),
            instant(resultSet.getTimestamp("revoked_at")),
            resultSet.getLong("version"),
            resultSet.getTimestamp("updated_at").toInstant(),
            resultSet.getTimestamp("last_sent_at").toInstant()
        );
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
