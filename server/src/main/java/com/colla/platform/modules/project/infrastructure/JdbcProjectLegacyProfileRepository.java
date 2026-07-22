package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.DuplicateOwnerItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.Findings;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.IllegalRoleItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ImDriftDirection;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ImDriftItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.MissingConversationItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.MissingConversationReason;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.MissingOwnerItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.OrphanMemberItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.OrphanMemberReason;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ProjectTotals;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.RoleDistribution;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.SharedConversationItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectLegacyProfileRepository implements ProjectLegacyProfileRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectLegacyProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProjectTotals summarizeTotals(UUID workspaceId) {
        return jdbcTemplate.queryForObject(
            """
                select
                    (select count(*) from projects p
                      where p.workspace_id = ? and p.archived_at is null) active_projects,
                    (select count(*) from projects p
                      where p.workspace_id = ? and p.archived_at is not null) archived_projects,
                    (select count(*) from project_members pm
                      where pm.workspace_id = ? and pm.archived_at is null) active_members,
                    (select count(*) from project_members pm
                      where pm.workspace_id = ? and pm.archived_at is not null) archived_members
                """,
            (resultSet, rowNumber) -> new ProjectTotals(
                resultSet.getLong("active_projects"),
                resultSet.getLong("archived_projects"),
                resultSet.getLong("active_members"),
                resultSet.getLong("archived_members")
            ),
            workspaceId,
            workspaceId,
            workspaceId,
            workspaceId
        );
    }

    @Override
    public RoleDistribution summarizeRoleDistribution(UUID workspaceId) {
        return jdbcTemplate.queryForObject(
            """
                select count(*) filter (where pm.project_role = 'owner') owner_count,
                       count(*) filter (where pm.project_role = 'member') member_count,
                       count(*) filter (where pm.project_role = 'viewer') viewer_count,
                       count(*) filter (where pm.project_role not in ('owner', 'member', 'viewer')) other_count
                  from project_members pm
                 where pm.workspace_id = ? and pm.archived_at is null
                """,
            (resultSet, rowNumber) -> new RoleDistribution(
                resultSet.getLong("owner_count"),
                resultSet.getLong("member_count"),
                resultSet.getLong("viewer_count"),
                resultSet.getLong("other_count")
            ),
            workspaceId
        );
    }

    @Override
    public Findings<OrphanMemberItem> findOrphanMembers(UUID workspaceId, int limit) {
        return toFindings(jdbcTemplate.query(
            """
                select pm.project_id, p.project_key, pm.user_id,
                       case
                           when u.id is null then 'USER_MISSING'
                           when u.deleted_at is not null then 'USER_DELETED'
                           when u.status = 'disabled' then 'USER_DISABLED'
                           else 'WORKSPACE_MISMATCH'
                       end reason,
                       count(*) over () total_count
                  from project_members pm
                  join projects p on p.id = pm.project_id and p.archived_at is null
                  left join users u on u.id = pm.user_id
                 where pm.workspace_id = ?
                   and pm.archived_at is null
                   and (u.id is null or u.deleted_at is not null or u.status = 'disabled'
                        or u.workspace_id <> pm.workspace_id)
                 order by pm.project_id, pm.user_id
                 limit ?
                """,
            (resultSet, rowNumber) -> counted(resultSet, new OrphanMemberItem(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("project_key"),
                resultSet.getObject("user_id", UUID.class),
                OrphanMemberReason.valueOf(resultSet.getString("reason"))
            )),
            workspaceId,
            limit
        ));
    }

    @Override
    public Findings<IllegalRoleItem> findIllegalRoles(UUID workspaceId, int limit) {
        return toFindings(jdbcTemplate.query(
            """
                select pm.project_id, p.project_key, pm.user_id, pm.project_role,
                       count(*) over () total_count
                  from project_members pm
                  join projects p on p.id = pm.project_id and p.archived_at is null
                 where pm.workspace_id = ?
                   and pm.archived_at is null
                   and pm.project_role not in ('owner', 'member', 'viewer')
                 order by pm.project_id, pm.user_id
                 limit ?
                """,
            (resultSet, rowNumber) -> counted(resultSet, new IllegalRoleItem(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("project_key"),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("project_role")
            )),
            workspaceId,
            limit
        ));
    }

    @Override
    public Findings<DuplicateOwnerItem> findDuplicateOwners(UUID workspaceId, int limit) {
        return toFindings(jdbcTemplate.query(
            """
                with active_owners as (
                    select pm.project_id, pm.user_id
                      from project_members pm
                      join projects p on p.id = pm.project_id and p.archived_at is null
                     where pm.workspace_id = ?
                       and pm.archived_at is null
                       and pm.project_role = 'owner'
                ),
                duplicates as (
                    select project_id, array_agg(user_id order by user_id) user_ids
                      from active_owners
                     group by project_id
                    having count(*) > 1
                )
                select d.project_id, p.project_key, d.user_ids,
                       count(*) over () total_count
                  from duplicates d
                  join projects p on p.id = d.project_id
                 order by d.project_id
                 limit ?
                """,
            (resultSet, rowNumber) -> counted(resultSet, new DuplicateOwnerItem(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("project_key"),
                uuidList(resultSet, "user_ids")
            )),
            workspaceId,
            limit
        ));
    }

    @Override
    public Findings<SharedConversationItem> findSharedConversations(UUID workspaceId, int limit) {
        return toFindings(jdbcTemplate.query(
            """
                with bound_projects as (
                    select p.id, p.project_key, p.conversation_id
                      from projects p
                     where p.workspace_id = ?
                       and p.archived_at is null
                       and p.conversation_id is not null
                ),
                shared as (
                    select conversation_id,
                           array_agg(id order by id) project_ids,
                           array_agg(project_key order by id) project_keys
                      from bound_projects
                     group by conversation_id
                    having count(*) > 1
                )
                select s.conversation_id, s.project_ids, s.project_keys,
                       count(*) over () total_count
                  from shared s
                 order by s.conversation_id
                 limit ?
                """,
            (resultSet, rowNumber) -> counted(resultSet, new SharedConversationItem(
                resultSet.getObject("conversation_id", UUID.class),
                uuidList(resultSet, "project_ids"),
                stringList(resultSet, "project_keys")
            )),
            workspaceId,
            limit
        ));
    }

    @Override
    public Findings<MissingOwnerItem> findProjectsWithoutOwner(UUID workspaceId, int limit) {
        return toFindings(jdbcTemplate.query(
            """
                select p.id project_id, p.project_key,
                       count(*) over () total_count
                  from projects p
                 where p.workspace_id = ?
                   and p.archived_at is null
                   and not exists (
                       select 1
                         from project_members pm
                        where pm.project_id = p.id
                          and pm.archived_at is null
                          and pm.project_role = 'owner'
                   )
                 order by p.id
                 limit ?
                """,
            (resultSet, rowNumber) -> counted(resultSet, new MissingOwnerItem(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("project_key")
            )),
            workspaceId,
            limit
        ));
    }

    @Override
    public Findings<ImDriftItem> findImDrifts(UUID workspaceId, int limit) {
        return toFindings(jdbcTemplate.query(
            """
                select drift.project_id, drift.project_key, drift.conversation_id, drift.direction, drift.user_id,
                       count(*) over () total_count
                  from (
                      select p.id project_id, p.project_key, p.conversation_id,
                             'PROJECT_MEMBER_NOT_IN_GROUP' direction, pm.user_id
                        from projects p
                        join conversations c
                          on c.id = p.conversation_id and c.archived_at is null and c.conversation_type = 'project'
                        join project_members pm
                          on pm.project_id = p.id and pm.archived_at is null
                        left join conversation_members cm
                          on cm.conversation_id = p.conversation_id and cm.user_id = pm.user_id
                         and cm.archived_at is null
                       where p.workspace_id = ?
                         and p.archived_at is null
                         and p.conversation_id is not null
                         and cm.id is null
                      union all
                      select p.id, p.project_key, p.conversation_id,
                             'GROUP_MEMBER_NOT_IN_PROJECT', cm.user_id
                        from projects p
                        join conversations c
                          on c.id = p.conversation_id and c.archived_at is null and c.conversation_type = 'project'
                        join conversation_members cm
                          on cm.conversation_id = p.conversation_id and cm.archived_at is null
                        left join project_members pm
                          on pm.project_id = p.id and pm.user_id = cm.user_id and pm.archived_at is null
                       where p.workspace_id = ?
                         and p.archived_at is null
                         and p.conversation_id is not null
                         and pm.id is null
                  ) drift
                 order by drift.project_id, drift.direction, drift.user_id
                 limit ?
                """,
            (resultSet, rowNumber) -> counted(resultSet, new ImDriftItem(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("project_key"),
                resultSet.getObject("conversation_id", UUID.class),
                ImDriftDirection.valueOf(resultSet.getString("direction")),
                resultSet.getObject("user_id", UUID.class)
            )),
            workspaceId,
            workspaceId,
            limit
        ));
    }

    @Override
    public Findings<MissingConversationItem> findMissingConversations(UUID workspaceId, int limit) {
        return toFindings(jdbcTemplate.query(
            """
                select p.id project_id, p.project_key, p.conversation_id,
                       case
                           when p.conversation_id is null then 'NO_CONVERSATION_ID'
                           when c.id is null then 'CONVERSATION_NOT_FOUND'
                           when c.archived_at is not null then 'CONVERSATION_ARCHIVED'
                           else 'CONVERSATION_TYPE_MISMATCH'
                       end reason,
                       count(*) over () total_count
                  from projects p
                  left join conversations c on c.id = p.conversation_id
                 where p.workspace_id = ?
                   and p.archived_at is null
                   and (p.conversation_id is null or c.id is null or c.archived_at is not null
                        or c.conversation_type <> 'project')
                 order by p.id
                 limit ?
                """,
            (resultSet, rowNumber) -> counted(resultSet, new MissingConversationItem(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("project_key"),
                resultSet.getObject("conversation_id", UUID.class),
                MissingConversationReason.valueOf(resultSet.getString("reason"))
            )),
            workspaceId,
            limit
        ));
    }

    private <T> CountedRow<T> counted(ResultSet resultSet, T row) throws SQLException {
        return new CountedRow<>(resultSet.getLong("total_count"), row);
    }

    private <T> Findings<T> toFindings(List<CountedRow<T>> rows) {
        long totalCount = rows.isEmpty() ? 0 : rows.get(0).totalCount();
        return new Findings<>(totalCount, rows.stream().map(CountedRow::row).toList());
    }

    private List<UUID> uuidList(ResultSet resultSet, String column) throws SQLException {
        return List.of((UUID[]) resultSet.getArray(column).getArray());
    }

    private List<String> stringList(ResultSet resultSet, String column) throws SQLException {
        return List.of((String[]) resultSet.getArray(column).getArray());
    }

    private record CountedRow<T>(long totalCount, T row) {
    }
}
