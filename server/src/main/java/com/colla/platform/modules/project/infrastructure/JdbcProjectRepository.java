package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectModels.IssueActivity;
import com.colla.platform.modules.project.domain.ProjectModels.IssueAttachment;
import com.colla.platform.modules.project.domain.ProjectModels.IssueComment;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.domain.ProjectModels.CountBucket;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectMember;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectStats;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectSummary;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectRepository implements ProjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID createProject(UUID workspaceId, String projectKey, String name, String description, UUID conversationId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into projects
                    (id, workspace_id, project_key, name, description, status, conversation_id,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, 'active', ?, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            projectKey,
            name,
            description,
            conversationId,
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public void updateProjectConversation(UUID workspaceId, UUID projectId, UUID conversationId) {
        jdbcTemplate.update(
            "update projects set conversation_id = ?, updated_at = now() where workspace_id = ? and id = ?",
            conversationId,
            workspaceId,
            projectId
        );
    }

    @Override
    public void addProjectMember(UUID workspaceId, UUID projectId, UUID userId, String projectRole, UUID createdBy) {
        jdbcTemplate.update(
            """
                insert into project_members
                    (id, workspace_id, project_id, user_id, project_role, joined_at, created_by)
                values (?, ?, ?, ?, ?, now(), ?)
                on conflict (project_id, user_id)
                do update set project_role = excluded.project_role, archived_at = null
                """,
            UUID.randomUUID(),
            workspaceId,
            projectId,
            userId,
            projectRole,
            createdBy
        );
    }

    @Override
    public boolean isProjectMember(UUID workspaceId, UUID projectId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from project_members
                where workspace_id = ? and project_id = ? and user_id = ? and archived_at is null
                """,
            Integer.class,
            workspaceId,
            projectId,
            userId
        );
        return count != null && count > 0;
    }

    @Override
    public boolean canEditProject(UUID workspaceId, UUID projectId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from project_members
                where workspace_id = ? and project_id = ? and user_id = ? and project_role in ('owner', 'member') and archived_at is null
                """,
            Integer.class,
            workspaceId,
            projectId,
            userId
        );
        return count != null && count > 0;
    }

    @Override
    public List<ProjectSummary> listProjects(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select p.id, p.project_key, p.name, p.description, p.status, p.conversation_id, p.created_at, p.updated_at,
                       (select count(*) from project_members pm2 where pm2.project_id = p.id and pm2.archived_at is null) member_count,
                       (select count(*) from issues i where i.project_id = p.id and i.deleted_at is null and i.status <> 'closed') open_issue_count
                from projects p
                join project_members pm on pm.project_id = p.id
                where p.workspace_id = ? and pm.user_id = ? and pm.archived_at is null and p.archived_at is null
                order by p.updated_at desc
                """,
            this::mapProjectSummary,
            workspaceId,
            userId
        );
    }

    @Override
    public Optional<ProjectDetail> findProject(UUID workspaceId, UUID projectId, UUID userId) {
        if (!isProjectMember(workspaceId, projectId, userId)) {
            return Optional.empty();
        }
        return findProjectById(workspaceId, projectId)
            .map(summary -> new ProjectDetail(
                summary.id(),
                summary.projectKey(),
                summary.name(),
                summary.description(),
                summary.status(),
                summary.conversationId(),
                listProjectMembers(workspaceId, projectId),
                summary.openIssueCount(),
                summary.createdAt(),
                summary.updatedAt()
            ));
    }

    @Override
    public Optional<ProjectSummary> findProjectById(UUID workspaceId, UUID projectId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select p.id, p.project_key, p.name, p.description, p.status, p.conversation_id, p.created_at, p.updated_at,
                           (select count(*) from project_members pm2 where pm2.project_id = p.id and pm2.archived_at is null) member_count,
                           (select count(*) from issues i where i.project_id = p.id and i.deleted_at is null and i.status <> 'closed') open_issue_count
                    from projects p
                    where p.workspace_id = ? and p.id = ? and p.archived_at is null
                    """,
                this::mapProjectSummary,
                workspaceId,
                projectId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<ProjectMember> listProjectMembers(UUID workspaceId, UUID projectId) {
        return jdbcTemplate.query(
            """
                select u.id user_id, u.username, u.display_name, pm.project_role, pm.joined_at
                from project_members pm
                join users u on u.id = pm.user_id
                where pm.workspace_id = ? and pm.project_id = ? and pm.archived_at is null and u.deleted_at is null
                order by pm.joined_at
                """,
            (rs, rowNum) -> new ProjectMember(
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("project_role"),
                rs.getTimestamp("joined_at").toInstant()
            ),
            workspaceId,
            projectId
        );
    }

    @Override
    public List<UUID> listProjectMemberIds(UUID workspaceId, UUID projectId) {
        return jdbcTemplate.queryForList(
            """
                select user_id
                from project_members
                where workspace_id = ? and project_id = ? and archived_at is null
                """,
            UUID.class,
            workspaceId,
            projectId
        );
    }

    @Override
    public List<UUID> findActiveProjectUserIdsByUsernames(UUID workspaceId, UUID projectId, List<String> usernames) {
        if (usernames.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", usernames.stream().map(item -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        args.add(workspaceId);
        args.addAll(usernames);
        return jdbcTemplate.queryForList(
            """
                select u.id
                from users u
                join project_members pm on pm.user_id = u.id and pm.project_id = ?
                where u.workspace_id = ? and u.status = 'active' and u.deleted_at is null and pm.archived_at is null
                  and u.username in (%s)
                """.formatted(placeholders),
            UUID.class,
            args.toArray()
        );
    }

    @Override
    public int nextIssueNumber(UUID projectId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) + 1 from issues where project_id = ?", Integer.class, projectId);
        return count == null ? 1 : count;
    }

    @Override
    public UUID createIssue(
        UUID workspaceId,
        UUID projectId,
        String issueKey,
        String issueType,
        String title,
        String description,
        String priority,
        UUID assigneeId,
        UUID reporterId,
        LocalDate dueAt,
        UUID createdBy
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into issues
                    (id, workspace_id, project_id, issue_key, issue_type, title, description, priority,
                     status, assignee_id, reporter_id, due_at, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'open', ?, ?, ?, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            projectId,
            issueKey,
            issueType,
            title,
            description,
            priority,
            assigneeId,
            reporterId,
            dueAt == null ? null : Date.valueOf(dueAt),
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public List<IssueSummary> listIssues(UUID workspaceId, UUID projectId, UUID userId, String status, String issueType) {
        if (!isProjectMember(workspaceId, projectId, userId)) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        args.add(projectId);
        StringBuilder sql = new StringBuilder("""
            select i.id, i.project_id, p.project_key, i.issue_key, i.issue_type, i.title, i.description, i.priority,
                   i.status, i.assignee_id, au.display_name assignee_name, i.reporter_id, ru.display_name reporter_name,
                   i.due_at, i.created_at, i.updated_at
            from issues i
            join projects p on p.id = i.project_id
            join users ru on ru.id = i.reporter_id
            left join users au on au.id = i.assignee_id
            where i.workspace_id = ? and i.project_id = ? and i.deleted_at is null
            """);
        if (status != null && !status.isBlank()) {
            sql.append(" and i.status = ?");
            args.add(status);
        }
        if (issueType != null && !issueType.isBlank()) {
            sql.append(" and i.issue_type = ?");
            args.add(issueType);
        }
        sql.append(" order by i.updated_at desc");
        return jdbcTemplate.query(sql.toString(), this::mapIssue, args.toArray());
    }

    @Override
    public List<IssueSummary> listMyIssues(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select i.id, i.project_id, p.project_key, i.issue_key, i.issue_type, i.title, i.description, i.priority,
                       i.status, i.assignee_id, au.display_name assignee_name, i.reporter_id, ru.display_name reporter_name,
                       i.due_at, i.created_at, i.updated_at
                from issues i
                join projects p on p.id = i.project_id
                join project_members pm on pm.project_id = p.id and pm.user_id = ?
                join users ru on ru.id = i.reporter_id
                left join users au on au.id = i.assignee_id
                where i.workspace_id = ? and i.assignee_id = ? and i.deleted_at is null and pm.archived_at is null
                order by i.updated_at desc
                """,
            this::mapIssue,
            userId,
            workspaceId,
            userId
        );
    }

    @Override
    public ProjectStats projectStats(UUID workspaceId, UUID projectId) {
        List<CountBucket> byStatus = jdbcTemplate.query(
            """
                select status key, status label, count(*) issue_count
                from issues
                where workspace_id = ? and project_id = ? and deleted_at is null
                group by status
                order by status
                """,
            this::mapBucket,
            workspaceId,
            projectId
        );
        List<CountBucket> byAssignee = jdbcTemplate.query(
            """
                select coalesce(i.assignee_id::text, 'unassigned') key,
                       coalesce(u.display_name, '未指派') label,
                       count(*) issue_count
                from issues i
                left join users u on u.id = i.assignee_id
                where i.workspace_id = ? and i.project_id = ? and i.deleted_at is null
                group by key, label
                order by issue_count desc, label
                """,
            this::mapBucket,
            workspaceId,
            projectId
        );
        List<CountBucket> byIteration = jdbcTemplate.query(
            """
                select coalesce(i.iteration_id::text, 'none') key,
                       coalesce(it.name, '未规划') label,
                       count(*) issue_count
                from issues i
                left join iterations it on it.id = i.iteration_id
                where i.workspace_id = ? and i.project_id = ? and i.deleted_at is null
                group by key, label
                order by issue_count desc, label
                """,
            this::mapBucket,
            workspaceId,
            projectId
        );
        Integer overdueCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from issues
                where workspace_id = ? and project_id = ? and deleted_at is null
                  and status not in ('resolved', 'closed') and due_at is not null and due_at < current_date
                """,
            Integer.class,
            workspaceId,
            projectId
        );
        return new ProjectStats(projectId, byStatus, byAssignee, byIteration, overdueCount == null ? 0 : overdueCount);
    }

    @Override
    public Optional<IssueSummary> findIssue(UUID workspaceId, UUID issueId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select i.id, i.project_id, p.project_key, i.issue_key, i.issue_type, i.title, i.description, i.priority,
                           i.status, i.assignee_id, au.display_name assignee_name, i.reporter_id, ru.display_name reporter_name,
                           i.due_at, i.created_at, i.updated_at
                    from issues i
                    join projects p on p.id = i.project_id
                    join users ru on ru.id = i.reporter_id
                    left join users au on au.id = i.assignee_id
                    where i.workspace_id = ? and i.id = ? and i.deleted_at is null
                    """,
                this::mapIssue,
                workspaceId,
                issueId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void updateIssue(UUID workspaceId, UUID issueId, String title, String description, String priority, UUID assigneeId, LocalDate dueAt, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update issues
                set title = ?, description = ?, priority = ?, assignee_id = ?, due_at = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            title,
            description,
            priority,
            assigneeId,
            dueAt == null ? null : Date.valueOf(dueAt),
            updatedBy,
            workspaceId,
            issueId
        );
    }

    @Override
    public void transitionIssue(UUID workspaceId, UUID issueId, String status, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update issues
                set status = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            status,
            updatedBy,
            workspaceId,
            issueId
        );
    }

    @Override
    public UUID addComment(UUID workspaceId, UUID issueId, UUID authorId, String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into issue_comments (id, workspace_id, issue_id, author_id, content, created_at)
                values (?, ?, ?, ?, ?, now())
                """,
            id,
            workspaceId,
            issueId,
            authorId,
            content
        );
        return id;
    }

    @Override
    public List<IssueComment> listComments(UUID workspaceId, UUID issueId) {
        return jdbcTemplate.query(
            """
                select c.id, c.issue_id, c.author_id, u.display_name author_name, c.content, c.created_at
                from issue_comments c
                join users u on u.id = c.author_id
                where c.workspace_id = ? and c.issue_id = ? and c.deleted_at is null
                order by c.created_at
                """,
            (rs, rowNum) -> new IssueComment(
                rs.getObject("id", UUID.class),
                rs.getObject("issue_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            issueId
        );
    }

    @Override
    public void addAttachment(UUID workspaceId, UUID issueId, UUID fileId, UUID createdBy) {
        jdbcTemplate.update(
            """
                insert into issue_attachments (id, workspace_id, issue_id, file_id, created_by, created_at)
                values (?, ?, ?, ?, ?, now())
                on conflict (workspace_id, issue_id, file_id) do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            issueId,
            fileId,
            createdBy
        );
    }

    @Override
    public List<IssueAttachment> listAttachments(UUID workspaceId, UUID issueId) {
        return jdbcTemplate.query(
            """
                select a.id, a.issue_id, a.file_id, f.original_name, a.created_by, a.created_at
                from issue_attachments a
                join files f on f.id = a.file_id
                where a.workspace_id = ? and a.issue_id = ?
                order by a.created_at
                """,
            (rs, rowNum) -> new IssueAttachment(
                rs.getObject("id", UUID.class),
                rs.getObject("issue_id", UUID.class),
                rs.getObject("file_id", UUID.class),
                rs.getString("original_name"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            issueId
        );
    }

    @Override
    public void addActivity(UUID workspaceId, UUID issueId, UUID actorId, String action, String fromValue, String toValue) {
        jdbcTemplate.update(
            """
                insert into issue_activity_logs
                    (id, workspace_id, issue_id, actor_id, action, from_value, to_value, metadata, created_at)
                values (?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            issueId,
            actorId,
            action,
            fromValue,
            toValue
        );
    }

    @Override
    public List<IssueActivity> listActivities(UUID workspaceId, UUID issueId) {
        return jdbcTemplate.query(
            """
                select l.id, l.issue_id, l.actor_id, u.display_name actor_name, l.action, l.from_value, l.to_value, l.created_at
                from issue_activity_logs l
                left join users u on u.id = l.actor_id
                where l.workspace_id = ? and l.issue_id = ?
                order by l.activity_seq desc
                """,
            (rs, rowNum) -> new IssueActivity(
                rs.getObject("id", UUID.class),
                rs.getObject("issue_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_name"),
                rs.getString("action"),
                rs.getString("from_value"),
                rs.getString("to_value"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            issueId
        );
    }

    private ProjectSummary mapProjectSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectSummary(
            rs.getObject("id", UUID.class),
            rs.getString("project_key"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getObject("conversation_id", UUID.class),
            rs.getInt("member_count"),
            rs.getInt("open_issue_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private IssueSummary mapIssue(ResultSet rs, int rowNum) throws SQLException {
        Date dueAt = rs.getDate("due_at");
        return new IssueSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getString("project_key"),
            rs.getString("issue_key"),
            rs.getString("issue_type"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("priority"),
            rs.getString("status"),
            rs.getObject("assignee_id", UUID.class),
            rs.getString("assignee_name"),
            rs.getObject("reporter_id", UUID.class),
            rs.getString("reporter_name"),
            dueAt == null ? null : dueAt.toLocalDate(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private CountBucket mapBucket(ResultSet rs, int rowNum) throws SQLException {
        return new CountBucket(rs.getString("key"), rs.getString("label"), rs.getLong("issue_count"));
    }
}
