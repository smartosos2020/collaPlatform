package com.colla.platform.modules.approval.infrastructure;

import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalActionLog;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalCountBucket;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalFlowNode;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalFormSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalInstanceSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalTaskSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcApprovalRepository implements ApprovalRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcApprovalRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ApprovalFormSummary> listForms(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select id, form_key, name, description, category, schema_json, enabled, created_at, updated_at
                from approval_forms
                where workspace_id = ? and archived_at is null
                order by category, name
                """,
            this::mapForm,
            workspaceId
        );
    }

    @Override
    public Optional<ApprovalFormSummary> findForm(UUID workspaceId, UUID formId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, form_key, name, description, category, schema_json, enabled, created_at, updated_at
                    from approval_forms
                    where workspace_id = ? and id = ? and archived_at is null
                    """,
                this::mapForm,
                workspaceId,
                formId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<ApprovalFlowNode> listFlowNodes(UUID workspaceId, UUID formId) {
        return jdbcTemplate.query(
            """
                select id, form_id, node_order, name, approver_type, approver_value
                from approval_flow_nodes
                where workspace_id = ? and form_id = ?
                order by node_order
                """,
            this::mapNode,
            workspaceId,
            formId
        );
    }

    @Override
    public ApprovalInstanceSummary createInstance(UUID workspaceId, UUID formId, String formKey, String title, UUID applicantId, Map<String, Object> payload) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into approval_instances
                    (id, workspace_id, form_id, form_key, title, applicant_id, status, current_node_order,
                     payload_json, submitted_at, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, 'pending', 0, ?::jsonb, now(), now(), ?, now())
                """,
            id,
            workspaceId,
            formId,
            formKey,
            title,
            applicantId,
            writeJson(payload),
            applicantId
        );
        return findInstance(workspaceId, id).orElseThrow();
    }

    @Override
    public Optional<ApprovalInstanceSummary> findInstance(UUID workspaceId, UUID instanceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(instanceSelect() + " where i.workspace_id = ? and i.id = ?", this::mapInstance, workspaceId, instanceId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, Object> findPayload(UUID workspaceId, UUID instanceId) {
        String payload = jdbcTemplate.queryForObject(
            "select payload_json from approval_instances where workspace_id = ? and id = ?",
            String.class,
            workspaceId,
            instanceId
        );
        return readJson(payload);
    }

    @Override
    public List<ApprovalInstanceSummary> listMyInstances(UUID workspaceId, UUID userId, int limit) {
        return jdbcTemplate.query(
            instanceSelect() + """
                 where i.workspace_id = ?
                   and (
                       i.applicant_id = ?
                       or exists (
                           select 1
                           from approval_tasks t
                           where t.workspace_id = i.workspace_id
                             and t.instance_id = i.id
                             and t.assignee_id = ?
                       )
                   )
                 order by i.updated_at desc
                 limit ?
                """,
            this::mapInstance,
            workspaceId,
            userId,
            userId,
            bounded(limit)
        );
    }

    @Override
    public List<ApprovalTaskSummary> listTodos(UUID workspaceId, UUID userId, int limit) {
        return jdbcTemplate.query(
            """
                select t.id, t.instance_id, i.title instance_title, f.name form_name, applicant.display_name applicant_name,
                       t.node_order, t.assignee_id, assignee.display_name assignee_name,
                       t.status, t.comment, t.acted_at, t.transferred_to, transferred.display_name transferred_to_name,
                       t.created_at, t.updated_at
                from approval_tasks t
                join approval_instances i on i.id = t.instance_id and i.workspace_id = t.workspace_id
                join approval_forms f on f.id = i.form_id
                left join users applicant on applicant.id = i.applicant_id
                left join users assignee on assignee.id = t.assignee_id
                left join users transferred on transferred.id = t.transferred_to
                where t.workspace_id = ? and t.assignee_id = ? and t.status = 'pending' and i.status = 'pending'
                order by t.created_at desc
                limit ?
                """,
            this::mapTask,
            workspaceId,
            userId,
            bounded(limit)
        );
    }

    @Override
    public List<ApprovalTaskSummary> listTasks(UUID workspaceId, UUID instanceId) {
        return jdbcTemplate.query(taskSelect() + " where t.workspace_id = ? and t.instance_id = ? order by t.node_order, t.created_at", this::mapTask, workspaceId, instanceId);
    }

    @Override
    public List<ApprovalActionLog> listActions(UUID workspaceId, UUID instanceId) {
        return jdbcTemplate.query(
            """
                select l.id, l.instance_id, l.actor_id, u.display_name actor_name, l.action, l.from_status,
                       l.to_status, l.comment, l.metadata, l.created_at
                from approval_action_logs l
                left join users u on u.id = l.actor_id
                where l.workspace_id = ? and l.instance_id = ?
                order by l.created_at desc
                """,
            this::mapAction,
            workspaceId,
            instanceId
        );
    }

    @Override
    public Optional<ApprovalTaskSummary> findPendingTask(UUID workspaceId, UUID instanceId, UUID assigneeId, UUID taskId) {
        String taskFilter = taskId == null ? "" : " and t.id = ?";
        Object[] args = taskId == null
            ? new Object[] { workspaceId, instanceId, assigneeId }
            : new Object[] { workspaceId, instanceId, assigneeId, taskId };
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                taskSelect() + " where t.workspace_id = ? and t.instance_id = ? and t.assignee_id = ? and t.status = 'pending'" + taskFilter + " order by t.created_at limit 1",
                this::mapTask,
                args
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<ApprovalTaskSummary> listPendingTasks(UUID workspaceId, UUID instanceId) {
        return jdbcTemplate.query(taskSelect() + " where t.workspace_id = ? and t.instance_id = ? and t.status = 'pending' order by t.created_at", this::mapTask, workspaceId, instanceId);
    }

    @Override
    public void createTask(UUID workspaceId, UUID instanceId, int nodeOrder, UUID assigneeId) {
        jdbcTemplate.update(
            """
                insert into approval_tasks
                    (id, workspace_id, instance_id, node_order, assignee_id, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'pending', now(), now())
                """,
            UUID.randomUUID(),
            workspaceId,
            instanceId,
            nodeOrder,
            assigneeId
        );
    }

    @Override
    public void markTask(UUID workspaceId, UUID taskId, String status, String comment) {
        jdbcTemplate.update(
            """
                update approval_tasks
                set status = ?, comment = ?, acted_at = now(), updated_at = now()
                where workspace_id = ? and id = ? and status = 'pending'
                """,
            status,
            comment,
            workspaceId,
            taskId
        );
    }

    @Override
    public void cancelPendingTasks(UUID workspaceId, UUID instanceId, UUID exceptTaskId) {
        if (exceptTaskId == null) {
            jdbcTemplate.update(
                """
                    update approval_tasks
                    set status = 'canceled', updated_at = now()
                    where workspace_id = ? and instance_id = ? and status = 'pending'
                    """,
                workspaceId,
                instanceId
            );
            return;
        }
        jdbcTemplate.update(
            """
                update approval_tasks
                set status = 'canceled', updated_at = now()
                where workspace_id = ? and instance_id = ? and status = 'pending' and id <> ?
                """,
            workspaceId,
            instanceId,
            exceptTaskId
        );
    }

    @Override
    public void transferTask(UUID workspaceId, UUID taskId, UUID targetAssigneeId, String comment) {
        jdbcTemplate.update(
            """
                update approval_tasks
                set assignee_id = ?, transferred_to = ?, comment = ?, updated_at = now()
                where workspace_id = ? and id = ? and status = 'pending'
                """,
            targetAssigneeId,
            targetAssigneeId,
            comment,
            workspaceId,
            taskId
        );
    }

    @Override
    public void updateInstanceStatus(UUID workspaceId, UUID instanceId, String status, int currentNodeOrder, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update approval_instances
                set status = ?,
                    current_node_order = ?,
                    updated_by = ?,
                    updated_at = now(),
                    completed_at = case when ? in ('approved', 'rejected') then now() else completed_at end,
                    withdrawn_at = case when ? = 'withdrawn' then now() else withdrawn_at end
                where workspace_id = ? and id = ?
                """,
            status,
            currentNodeOrder,
            updatedBy,
            status,
            status,
            workspaceId,
            instanceId
        );
    }

    @Override
    public void addAction(UUID workspaceId, UUID instanceId, UUID actorId, String action, String fromStatus, String toStatus, String comment, Map<String, Object> metadata) {
        jdbcTemplate.update(
            """
                insert into approval_action_logs
                    (id, workspace_id, instance_id, actor_id, action, from_status, to_status, comment, metadata, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            instanceId,
            actorId,
            action,
            fromStatus,
            toStatus,
            comment,
            writeJson(metadata == null ? Map.of() : metadata)
        );
    }

    @Override
    public List<UUID> resolveApprovers(UUID workspaceId, UUID applicantId, ApprovalFlowNode node) {
        LinkedHashSet<UUID> approvers = new LinkedHashSet<>();
        if ("applicant".equals(node.approverType())) {
            approvers.add(applicantId);
        } else if ("user".equals(node.approverType()) && node.approverValue() != null) {
            UUID userId = UUID.fromString(node.approverValue());
            if (isActiveUser(workspaceId, userId)) {
                approvers.add(userId);
            }
        } else if ("role".equals(node.approverType()) && node.approverValue() != null) {
            approvers.addAll(jdbcTemplate.queryForList(
                """
                    select u.id
                    from users u
                    join user_roles ur on ur.workspace_id = u.workspace_id and ur.user_id = u.id
                    join roles r on r.id = ur.role_id
                    where u.workspace_id = ? and u.status = 'active' and u.deleted_at is null and r.code = ?
                    order by u.created_at
                    """,
                UUID.class,
                workspaceId,
                node.approverValue()
            ));
        }
        if (approvers.isEmpty()) {
            approvers.add(applicantId);
        }
        return List.copyOf(approvers);
    }

    @Override
    public boolean hasPendingTaskAtNode(UUID workspaceId, UUID instanceId, int nodeOrder) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from approval_tasks
                where workspace_id = ? and instance_id = ? and node_order = ? and status = 'pending'
                """,
            Integer.class,
            workspaceId,
            instanceId,
            nodeOrder
        );
        return count != null && count > 0;
    }

    @Override
    public boolean isParticipant(UUID workspaceId, UUID instanceId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from approval_instances i
                where i.workspace_id = ? and i.id = ?
                  and (
                      i.applicant_id = ?
                      or exists (
                          select 1 from approval_tasks t
                          where t.workspace_id = i.workspace_id and t.instance_id = i.id and t.assignee_id = ?
                      )
                  )
                """,
            Integer.class,
            workspaceId,
            instanceId,
            userId,
            userId
        );
        return count != null && count > 0;
    }

    @Override
    public long pendingTodoCount(UUID workspaceId, UUID userId) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from approval_tasks t
                join approval_instances i on i.id = t.instance_id and i.workspace_id = t.workspace_id
                where t.workspace_id = ? and t.assignee_id = ? and t.status = 'pending' and i.status = 'pending'
                """,
            Long.class,
            workspaceId,
            userId
        );
        return count == null ? 0 : count;
    }

    @Override
    public long submittedStatusCount(UUID workspaceId, UUID userId, String status) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from approval_instances where workspace_id = ? and applicant_id = ? and status = ?",
            Long.class,
            workspaceId,
            userId,
            status
        );
        return count == null ? 0 : count;
    }

    @Override
    public List<ApprovalCountBucket> countByForm(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select i.form_key key, f.name label, count(*) total
                from approval_instances i
                join approval_forms f on f.id = i.form_id
                where i.workspace_id = ?
                  and (
                      i.applicant_id = ?
                      or exists (
                          select 1 from approval_tasks t
                          where t.workspace_id = i.workspace_id and t.instance_id = i.id and t.assignee_id = ?
                      )
                  )
                group by i.form_key, f.name
                order by total desc, f.name
                """,
            (rs, rowNum) -> new ApprovalCountBucket(rs.getString("key"), rs.getString("label"), rs.getLong("total")),
            workspaceId,
            userId,
            userId
        );
    }

    @Override
    public List<ApprovalCountBucket> countByStatus(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select i.status key, i.status label, count(*) total
                from approval_instances i
                where i.workspace_id = ?
                  and (
                      i.applicant_id = ?
                      or exists (
                          select 1 from approval_tasks t
                          where t.workspace_id = i.workspace_id and t.instance_id = i.id and t.assignee_id = ?
                      )
                  )
                group by i.status
                order by total desc, i.status
                """,
            (rs, rowNum) -> new ApprovalCountBucket(rs.getString("key"), rs.getString("label"), rs.getLong("total")),
            workspaceId,
            userId,
            userId
        );
    }

    private ApprovalFormSummary mapForm(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalFormSummary(
            rs.getObject("id", UUID.class),
            rs.getString("form_key"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("category"),
            readJson(rs.getString("schema_json")),
            rs.getBoolean("enabled"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private ApprovalFlowNode mapNode(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalFlowNode(
            rs.getObject("id", UUID.class),
            rs.getObject("form_id", UUID.class),
            rs.getInt("node_order"),
            rs.getString("name"),
            rs.getString("approver_type"),
            rs.getString("approver_value")
        );
    }

    private ApprovalInstanceSummary mapInstance(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalInstanceSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("form_id", UUID.class),
            rs.getString("form_key"),
            rs.getString("form_name"),
            rs.getString("title"),
            rs.getObject("applicant_id", UUID.class),
            rs.getString("applicant_name"),
            rs.getString("status"),
            rs.getInt("current_node_order"),
            rs.getTimestamp("submitted_at").toInstant(),
            nullableInstant(rs, "completed_at"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private ApprovalTaskSummary mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalTaskSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("instance_id", UUID.class),
            rs.getString("instance_title"),
            rs.getString("form_name"),
            rs.getString("applicant_name"),
            rs.getInt("node_order"),
            rs.getObject("assignee_id", UUID.class),
            rs.getString("assignee_name"),
            rs.getString("status"),
            rs.getString("comment"),
            nullableInstant(rs, "acted_at"),
            rs.getObject("transferred_to", UUID.class),
            rs.getString("transferred_to_name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private ApprovalActionLog mapAction(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalActionLog(
            rs.getObject("id", UUID.class),
            rs.getObject("instance_id", UUID.class),
            rs.getObject("actor_id", UUID.class),
            rs.getString("actor_name"),
            rs.getString("action"),
            rs.getString("from_status"),
            rs.getString("to_status"),
            rs.getString("comment"),
            readJson(rs.getString("metadata")),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private String instanceSelect() {
        return """
            select i.id, i.form_id, i.form_key, f.name form_name, i.title, i.applicant_id,
                   applicant.display_name applicant_name, i.status, i.current_node_order,
                   i.submitted_at, i.completed_at, i.created_at, i.updated_at
            from approval_instances i
            join approval_forms f on f.id = i.form_id
            left join users applicant on applicant.id = i.applicant_id
            """;
    }

    private String taskSelect() {
        return """
            select t.id, t.instance_id, i.title instance_title, f.name form_name, applicant.display_name applicant_name,
                   t.node_order, t.assignee_id, assignee.display_name assignee_name,
                   t.status, t.comment, t.acted_at, t.transferred_to, transferred.display_name transferred_to_name,
                   t.created_at, t.updated_at
            from approval_tasks t
            join approval_instances i on i.id = t.instance_id and i.workspace_id = t.workspace_id
            join approval_forms f on f.id = i.form_id
            left join users applicant on applicant.id = i.applicant_id
            left join users assignee on assignee.id = t.assignee_id
            left join users transferred on transferred.id = t.transferred_to
            """;
    }

    private boolean isActiveUser(UUID workspaceId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from users where workspace_id = ? and id = ? and status = 'active' and deleted_at is null",
            Integer.class,
            workspaceId,
            userId
        );
        return count != null && count > 0;
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private int bounded(int limit) {
        return Math.min(Math.max(limit, 1), 50);
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid approval json", exception);
        }
    }

    private Map<String, Object> readJson(String value) {
        try {
            return objectMapper.readValue(value == null ? "{}" : value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid approval json", exception);
        }
    }
}
