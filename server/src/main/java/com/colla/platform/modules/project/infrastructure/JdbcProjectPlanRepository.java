package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ProjectPlanModels.LinkInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.MilestoneInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PhaseInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanChange;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanLink;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanMilestone;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanPhase;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcProjectPlanRepository implements ProjectPlanRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProjectPlanRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PlanSummary> list(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            """
                select id, name, description, start_date, end_date, status,
                       aggregate_version, created_by, created_at, updated_by,
                       updated_at, archived_at
                  from project_plans
                 where workspace_id=? and space_id=?
                 order by case status when 'published' then 0 when 'draft' then 1 else 2 end,
                          updated_at desc, id
                 limit ?
                """,
            (rs, row) -> summary(rs),
            workspaceId, spaceId, limit
        );
    }

    @Override
    public Optional<ProjectPlan> find(
        UUID workspaceId, UUID spaceId, UUID planId, int changeLimit
    ) {
        try {
            PlanSummary plan = jdbc.queryForObject(
                """
                    select id, name, description, start_date, end_date, status,
                           aggregate_version, created_by, created_at, updated_by,
                           updated_at, archived_at
                      from project_plans
                     where workspace_id=? and space_id=? and id=?
                    """,
                (rs, row) -> summary(rs),
                workspaceId, spaceId, planId
            );
            List<PlanPhase> phases = jdbc.query(
                """
                    select id, phase_key, name, position, start_date, end_date, status
                      from project_plan_phases
                     where workspace_id=? and space_id=? and plan_id=?
                     order by position, id
                    """,
                (rs, row) -> new PlanPhase(
                    rs.getObject("id", UUID.class),
                    rs.getString("phase_key"),
                    rs.getString("name"),
                    rs.getInt("position"),
                    rs.getObject("start_date", LocalDate.class),
                    rs.getObject("end_date", LocalDate.class),
                    rs.getString("status")
                ),
                workspaceId, spaceId, planId
            );
            List<PlanMilestone> milestones = jdbc.query(
                """
                    select id, phase_id, milestone_key, name, position,
                           target_date, status, owner_user_id
                      from project_plan_milestones
                     where workspace_id=? and space_id=? and plan_id=?
                     order by position, id
                    """,
                (rs, row) -> new PlanMilestone(
                    rs.getObject("id", UUID.class),
                    rs.getObject("phase_id", UUID.class),
                    rs.getString("milestone_key"),
                    rs.getString("name"),
                    rs.getInt("position"),
                    rs.getObject("target_date", LocalDate.class),
                    rs.getString("status"),
                    rs.getObject("owner_user_id", UUID.class)
                ),
                workspaceId, spaceId, planId
            );
            List<PlanLink> links = jdbc.query(
                """
                    select id, milestone_id, work_item_id, source_work_item_version
                      from project_plan_links
                     where workspace_id=? and space_id=? and plan_id=?
                     order by milestone_id, work_item_id, id
                    """,
                (rs, row) -> new PlanLink(
                    rs.getObject("id", UUID.class),
                    rs.getObject("milestone_id", UUID.class),
                    rs.getObject("work_item_id", UUID.class),
                    rs.getLong("source_work_item_version")
                ),
                workspaceId, spaceId, planId
            );
            List<PlanChange> changes = jdbc.query(
                """
                    select change_sequence, operation, reason, actor_id,
                           plan_version, occurred_at
                      from project_plan_changes
                     where workspace_id=? and space_id=? and plan_id=?
                     order by change_sequence desc
                     limit ?
                    """,
                (rs, row) -> new PlanChange(
                    rs.getLong("change_sequence"),
                    rs.getString("operation"),
                    rs.getString("reason"),
                    rs.getObject("actor_id", UUID.class),
                    rs.getLong("plan_version"),
                    rs.getTimestamp("occurred_at").toInstant()
                ),
                workspaceId, spaceId, planId, changeLimit
            );
            return Optional.of(new ProjectPlan(
                plan, phases, milestones, links, changes, null
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select request_hash, response_json
                      from project_plan_commands
                     where workspace_id=? and space_id=? and actor_id=?
                       and operation=? and request_id=?
                    """,
                (rs, row) -> new CommandRecord(
                    rs.getString("request_hash"), rs.getString("response_json")
                ),
                workspaceId, spaceId, actorId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public ProjectPlan create(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String requestId,
        String requestHash,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links,
        Map<UUID, Long> workItemVersions
    ) {
        UUID planId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_plans(
                    id, workspace_id, space_id, name, description, start_date,
                    end_date, status, aggregate_version, created_by, created_at,
                    updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'draft', 1, ?, now(), ?, now())
                """,
            planId, workspaceId, spaceId, name, description,
            Date.valueOf(startDate), Date.valueOf(endDate), actorId, actorId
        );
        insertGraph(
            workspaceId, spaceId, actorId, planId,
            phases, milestones, links, workItemVersions
        );
        appendChange(workspaceId, spaceId, planId, "create", "", actorId, 1);
        ProjectPlan result = require(workspaceId, spaceId, planId);
        insertCommand(
            workspaceId, spaceId, actorId, planId, "create",
            requestId, requestHash, result
        );
        return result;
    }

    @Override
    @Transactional
    public ProjectPlan mutate(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID planId,
        String operation,
        String requestId,
        String requestHash,
        long expectedVersion,
        String reason,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links,
        Map<UUID, Long> workItemVersions
    ) {
        PlanSummary before = find(workspaceId, spaceId, planId, 1)
            .map(ProjectPlan::plan)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project plan is not available"));
        String targetStatus = status(operation, before.status());
        int changed;
        if ("update".equals(operation)) {
            changed = jdbc.update(
                """
                    update project_plans
                       set name=?, description=?, start_date=?, end_date=?,
                           aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                       and aggregate_version=? and status <> 'archived'
                    """,
                name, description, Date.valueOf(startDate), Date.valueOf(endDate),
                actorId, workspaceId, spaceId, planId, expectedVersion
            );
        } else {
            changed = jdbc.update(
                """
                    update project_plans
                       set status=?, archived_at=case when ?='archived' then now() else null end,
                           aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                       and aggregate_version=? and status=?
                    """,
                targetStatus, targetStatus, actorId, workspaceId, spaceId,
                planId, expectedVersion, before.status()
            );
        }
        if (changed != 1) {
            throw failure("PROJECT_PLAN_VERSION_CONFLICT", "Project plan changed; refresh and retry");
        }
        if ("update".equals(operation)) {
            jdbc.update(
                "delete from project_plan_links where workspace_id=? and space_id=? and plan_id=?",
                workspaceId, spaceId, planId
            );
            jdbc.update(
                "delete from project_plan_milestones where workspace_id=? and space_id=? and plan_id=?",
                workspaceId, spaceId, planId
            );
            jdbc.update(
                "delete from project_plan_phases where workspace_id=? and space_id=? and plan_id=?",
                workspaceId, spaceId, planId
            );
            insertGraph(
                workspaceId, spaceId, actorId, planId,
                phases, milestones, links, workItemVersions
            );
        }
        long nextVersion = expectedVersion + 1;
        appendChange(
            workspaceId, spaceId, planId, operation, reason, actorId, nextVersion
        );
        ProjectPlan result = require(workspaceId, spaceId, planId);
        insertCommand(
            workspaceId, spaceId, actorId, planId, operation,
            requestId, requestHash, result
        );
        return result;
    }

    private void insertGraph(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID planId,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links,
        Map<UUID, Long> workItemVersions
    ) {
        for (PhaseInput phase : phases) {
            jdbc.update(
                """
                    insert into project_plan_phases(
                        id, workspace_id, space_id, plan_id, phase_key, name,
                        position, start_date, end_date, status
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                phase.id(), workspaceId, spaceId, planId, phase.phaseKey(),
                phase.name(), phase.position(), Date.valueOf(phase.startDate()),
                Date.valueOf(phase.endDate()), phase.status()
            );
        }
        for (MilestoneInput milestone : milestones) {
            jdbc.update(
                """
                    insert into project_plan_milestones(
                        id, workspace_id, space_id, plan_id, phase_id,
                        milestone_key, name, position, target_date, status,
                        owner_user_id
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                milestone.id(), workspaceId, spaceId, planId, milestone.phaseId(),
                milestone.milestoneKey(), milestone.name(), milestone.position(),
                Date.valueOf(milestone.targetDate()), milestone.status(),
                milestone.ownerUserId()
            );
        }
        for (LinkInput link : links) {
            jdbc.update(
                """
                    insert into project_plan_links(
                        id, workspace_id, space_id, plan_id, milestone_id,
                        work_item_id, source_work_item_version, created_by, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
                    """,
                link.id(), workspaceId, spaceId, planId, link.milestoneId(),
                link.workItemId(), workItemVersions.get(link.workItemId()), actorId
            );
        }
    }

    private void appendChange(
        UUID workspaceId,
        UUID spaceId,
        UUID planId,
        String operation,
        String reason,
        UUID actorId,
        long planVersion
    ) {
        Long sequence = jdbc.queryForObject(
            """
                select coalesce(max(change_sequence), 0) + 1
                  from project_plan_changes
                 where workspace_id=? and space_id=? and plan_id=?
                """,
            Long.class, workspaceId, spaceId, planId
        );
        jdbc.update(
            """
                insert into project_plan_changes(
                    id, workspace_id, space_id, plan_id, change_sequence,
                    operation, reason, actor_id, plan_version, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, planId, sequence,
            operation, reason, actorId, planVersion
        );
    }

    private void insertCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID planId,
        String operation,
        String requestId,
        String requestHash,
        ProjectPlan result
    ) {
        jdbc.update(
            """
                insert into project_plan_commands(
                    id, workspace_id, space_id, actor_id, plan_id, operation,
                    request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, planId, operation,
            requestId, requestHash, json(result)
        );
    }

    private String status(String operation, String current) {
        return switch (operation) {
            case "publish" -> {
                if (!"draft".equals(current)) {
                    throw failure("PROJECT_PLAN_TRANSITION_INVALID", "Only draft plans can be published");
                }
                yield "published";
            }
            case "archive" -> {
                if ("archived".equals(current)) {
                    throw failure("PROJECT_PLAN_TRANSITION_INVALID", "Project plan is already archived");
                }
                yield "archived";
            }
            case "restore" -> {
                if (!"archived".equals(current)) {
                    throw failure("PROJECT_PLAN_TRANSITION_INVALID", "Only archived plans can be restored");
                }
                yield "draft";
            }
            case "update" -> current;
            default -> throw failure("PROJECT_PLAN_OPERATION_INVALID", "Project plan operation is invalid");
        };
    }

    private ProjectPlan require(UUID workspaceId, UUID spaceId, UUID planId) {
        return find(workspaceId, spaceId, planId, 100).orElseThrow();
    }

    private PlanSummary summary(ResultSet rs) throws SQLException {
        return new PlanSummary(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("description"),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class),
            rs.getString("status"),
            rs.getLong("aggregate_version"),
            rs.getObject("created_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("archived_at") == null
                ? null : rs.getTimestamp("archived_at").toInstant()
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize project plan receipt", exception);
        }
    }
}
