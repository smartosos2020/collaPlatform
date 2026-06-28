package com.colla.platform.modules.identity.infrastructure;

import com.colla.platform.modules.identity.domain.UserGroupModels.ExpandedUserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserGroupRepository implements UserGroupRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UserGroupSummary> listGroups(UUID workspaceId, boolean activeOnly) {
        return jdbcTemplate.query(
            """
                select ug.id, ug.code, ug.name, ug.description, ug.group_type, ug.status, ug.created_at, ug.updated_at,
                       coalesce((
                           select count(*)
                           from user_group_members ugm
                           where ugm.workspace_id = ug.workspace_id
                             and ugm.group_id = ug.id
                             and ugm.removed_at is null
                       ), 0) direct_member_count,
                       coalesce((
                           select count(distinct expanded.user_id)
                           from (
                               select ugm.subject_id user_id
                               from user_group_members ugm
                               join users u on u.id = ugm.subject_id and u.deleted_at is null
                               where ugm.workspace_id = ug.workspace_id
                                 and ugm.group_id = ug.id
                                 and ugm.subject_type = 'user'
                                 and ugm.removed_at is null
                               union
                               select dm.user_id
                               from user_group_members ugm
                               join departments d on d.id = ugm.subject_id and d.deleted_at is null
                               join department_members dm on dm.department_id = d.id and dm.ended_at is null
                               join users u on u.id = dm.user_id and u.deleted_at is null
                               where ugm.workspace_id = ug.workspace_id
                                 and ugm.group_id = ug.id
                                 and ugm.subject_type = 'department'
                                 and ugm.removed_at is null
                           ) expanded
                       ), 0) expanded_member_count
                from user_groups ug
                where ug.workspace_id = ?
                  and ug.deleted_at is null
                  and (? = false or ug.status = 'active')
                order by ug.status, lower(ug.name), ug.created_at
                """,
            this::mapGroup,
            workspaceId,
            activeOnly
        );
    }

    @Override
    public Optional<UserGroupSummary> findGroup(UUID workspaceId, UUID groupId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select ug.id, ug.code, ug.name, ug.description, ug.group_type, ug.status, ug.created_at, ug.updated_at,
                           coalesce((
                               select count(*)
                               from user_group_members ugm
                               where ugm.workspace_id = ug.workspace_id
                                 and ugm.group_id = ug.id
                                 and ugm.removed_at is null
                           ), 0) direct_member_count,
                           coalesce((
                               select count(distinct expanded.user_id)
                               from (
                                   select ugm.subject_id user_id
                                   from user_group_members ugm
                                   join users u on u.id = ugm.subject_id and u.deleted_at is null
                                   where ugm.workspace_id = ug.workspace_id
                                     and ugm.group_id = ug.id
                                     and ugm.subject_type = 'user'
                                     and ugm.removed_at is null
                                   union
                                   select dm.user_id
                                   from user_group_members ugm
                                   join departments d on d.id = ugm.subject_id and d.deleted_at is null
                                   join department_members dm on dm.department_id = d.id and dm.ended_at is null
                                   join users u on u.id = dm.user_id and u.deleted_at is null
                                   where ugm.workspace_id = ug.workspace_id
                                     and ugm.group_id = ug.id
                                     and ugm.subject_type = 'department'
                                     and ugm.removed_at is null
                               ) expanded
                           ), 0) expanded_member_count
                    from user_groups ug
                    where ug.workspace_id = ? and ug.id = ? and ug.deleted_at is null
                    """,
                this::mapGroup,
                workspaceId,
                groupId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public UUID createGroup(UUID workspaceId, String code, String name, String description, String groupType, UUID actorId) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into user_groups
                    (id, workspace_id, code, name, description, group_type, status, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, 'active', ?, now(), ?, now())
                """,
            groupId,
            workspaceId,
            code,
            name,
            description,
            groupType,
            actorId,
            actorId
        );
        return groupId;
    }

    @Override
    public void updateGroup(UUID workspaceId, UUID groupId, String code, String name, String description, String groupType, UUID actorId) {
        jdbcTemplate.update(
            """
                update user_groups
                set code = ?, name = ?, description = ?, group_type = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            code,
            name,
            description,
            groupType,
            actorId,
            workspaceId,
            groupId
        );
    }

    @Override
    public void disableGroup(UUID workspaceId, UUID groupId, UUID actorId) {
        jdbcTemplate.update(
            """
                update user_groups
                set status = 'disabled', updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            actorId,
            workspaceId,
            groupId
        );
    }

    @Override
    public void deleteGroup(UUID workspaceId, UUID groupId) {
        jdbcTemplate.update(
            """
                update user_groups
                set deleted_at = now(), updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            workspaceId,
            groupId
        );
    }

    @Override
    public boolean hasActiveMembers(UUID workspaceId, UUID groupId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from user_group_members
                where workspace_id = ? and group_id = ? and removed_at is null
                """,
            Integer.class,
            workspaceId,
            groupId
        );
        return count != null && count > 0;
    }

    @Override
    public List<UserGroupMember> listMembers(UUID workspaceId, UUID groupId) {
        return jdbcTemplate.query(
            """
                select ugm.id, ugm.group_id, ugm.subject_type, ugm.subject_id,
                       case ugm.subject_type when 'user' then u.display_name else d.name end subject_name,
                       case ugm.subject_type when 'user' then u.username else d.code end subject_detail,
                       case ugm.subject_type when 'user' then u.status else d.status end subject_status,
                       ugm.created_at
                from user_group_members ugm
                left join users u on u.id = ugm.subject_id and ugm.subject_type = 'user' and u.deleted_at is null
                left join departments d on d.id = ugm.subject_id and ugm.subject_type = 'department' and d.deleted_at is null
                where ugm.workspace_id = ?
                  and ugm.group_id = ?
                  and ugm.removed_at is null
                order by ugm.subject_type, lower(coalesce(u.display_name, d.name)), ugm.created_at
                """,
            this::mapMember,
            workspaceId,
            groupId
        );
    }

    @Override
    public UUID addMember(UUID workspaceId, UUID groupId, String subjectType, UUID subjectId, UUID actorId) {
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into user_group_members
                    (id, workspace_id, group_id, subject_type, subject_id, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, group_id, subject_type, subject_id)
                where removed_at is null
                do nothing
                """,
            memberId,
            workspaceId,
            groupId,
            subjectType,
            subjectId,
            actorId
        );
        return memberId;
    }

    @Override
    public void removeMember(UUID workspaceId, UUID groupId, UUID memberId) {
        jdbcTemplate.update(
            """
                update user_group_members
                set removed_at = now()
                where workspace_id = ?
                  and group_id = ?
                  and id = ?
                  and removed_at is null
                """,
            workspaceId,
            groupId,
            memberId
        );
    }

    @Override
    public List<ExpandedUserGroupMember> listExpandedMembers(UUID workspaceId, UUID groupId) {
        return jdbcTemplate.query(
            """
                select distinct on (user_id)
                       user_id, username, display_name, email, status, source_type, source_id, source_name
                from (
                    select u.id user_id, u.username, u.display_name, u.email, u.status,
                           'user' source_type, u.id source_id, u.display_name source_name, 0 source_rank
                    from user_group_members ugm
                    join users u on u.id = ugm.subject_id
                    where ugm.workspace_id = ?
                      and ugm.group_id = ?
                      and ugm.subject_type = 'user'
                      and ugm.removed_at is null
                      and u.deleted_at is null
                    union all
                    select u.id user_id, u.username, u.display_name, u.email, u.status,
                           'department' source_type, d.id source_id, d.name source_name, 1 source_rank
                    from user_group_members ugm
                    join departments d on d.id = ugm.subject_id and d.deleted_at is null
                    join department_members dm on dm.department_id = d.id and dm.ended_at is null
                    join users u on u.id = dm.user_id and u.deleted_at is null
                    where ugm.workspace_id = ?
                      and ugm.group_id = ?
                      and ugm.subject_type = 'department'
                      and ugm.removed_at is null
                ) expanded
                order by user_id, source_rank, lower(display_name), username
                """,
            this::mapExpandedMember,
            workspaceId,
            groupId,
            workspaceId,
            groupId
        );
    }

    private UserGroupSummary mapGroup(ResultSet rs, int rowNum) throws SQLException {
        return new UserGroupSummary(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("group_type"),
            rs.getString("status"),
            rs.getInt("direct_member_count"),
            rs.getInt("expanded_member_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private UserGroupMember mapMember(ResultSet rs, int rowNum) throws SQLException {
        return new UserGroupMember(
            rs.getObject("id", UUID.class),
            rs.getObject("group_id", UUID.class),
            rs.getString("subject_type"),
            rs.getObject("subject_id", UUID.class),
            rs.getString("subject_name"),
            rs.getString("subject_detail"),
            rs.getString("subject_status"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private ExpandedUserGroupMember mapExpandedMember(ResultSet rs, int rowNum) throws SQLException {
        return new ExpandedUserGroupMember(
            rs.getObject("user_id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("email"),
            rs.getString("status"),
            rs.getString("source_type"),
            rs.getObject("source_id", UUID.class),
            rs.getString("source_name")
        );
    }
}
