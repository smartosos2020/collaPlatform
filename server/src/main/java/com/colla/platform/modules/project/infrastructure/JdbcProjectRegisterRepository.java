package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ProjectRegisterModels.ReferenceInput;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterEntry;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterHistory;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterReference;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.ResponseInput;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.ResponsePlan;
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
public class JdbcProjectRegisterRepository implements ProjectRegisterRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProjectRegisterRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RegisterSummary> list(
        UUID workspaceId, UUID spaceId, String entryType, int limit
    ) {
        String filter = entryType == null ? "" : " and entry_type=?";
        Object[] arguments = entryType == null
            ? new Object[]{workspaceId, spaceId, limit}
            : new Object[]{workspaceId, spaceId, entryType, limit};
        return jdbc.query(
            """
                select id, entry_type, title, summary, status, owner_user_id,
                       due_date, probability, impact, decision_basis, change_impact,
                       supersedes_entry_id, verification, aggregate_version,
                       created_by, created_at, updated_by, updated_at
                  from project_register_entries
                 where workspace_id=? and space_id=?
                """ + filter + """
                 order by updated_at desc, id
                 limit ?
                """,
            (rs, row) -> summary(rs),
            arguments
        );
    }

    @Override
    public Optional<RegisterEntry> find(
        UUID workspaceId, UUID spaceId, UUID entryId, int historyLimit
    ) {
        try {
            RegisterSummary entry = jdbc.queryForObject(
                """
                    select id, entry_type, title, summary, status, owner_user_id,
                           due_date, probability, impact, decision_basis, change_impact,
                           supersedes_entry_id, verification, aggregate_version,
                           created_by, created_at, updated_by, updated_at
                      from project_register_entries
                     where workspace_id=? and space_id=? and id=?
                    """,
                (rs, row) -> summary(rs), workspaceId, spaceId, entryId
            );
            List<RegisterReference> references = jdbc.query(
                """
                    select id, source_type, source_id, source_version
                      from project_register_references
                     where workspace_id=? and space_id=? and entry_id=?
                     order by source_type, source_id, id
                    """,
                (rs, row) -> new RegisterReference(
                    rs.getObject("id", UUID.class),
                    rs.getString("source_type"),
                    rs.getObject("source_id", UUID.class),
                    rs.getLong("source_version")
                ),
                workspaceId, spaceId, entryId
            );
            List<ResponsePlan> responses = jdbc.query(
                """
                    select id, response_type, description, owner_user_id, due_date, status
                      from project_register_responses
                     where workspace_id=? and space_id=? and entry_id=?
                     order by position, id
                    """,
                (rs, row) -> new ResponsePlan(
                    rs.getObject("id", UUID.class),
                    rs.getString("response_type"),
                    rs.getString("description"),
                    rs.getObject("owner_user_id", UUID.class),
                    rs.getObject("due_date", LocalDate.class),
                    rs.getString("status")
                ),
                workspaceId, spaceId, entryId
            );
            List<RegisterHistory> history = jdbc.query(
                """
                    select history_sequence, operation, from_status, to_status,
                           reason, actor_id, entry_version, occurred_at
                      from project_register_history
                     where workspace_id=? and space_id=? and entry_id=?
                     order by history_sequence desc
                     limit ?
                    """,
                (rs, row) -> new RegisterHistory(
                    rs.getLong("history_sequence"),
                    rs.getString("operation"),
                    rs.getString("from_status"),
                    rs.getString("to_status"),
                    rs.getString("reason"),
                    rs.getObject("actor_id", UUID.class),
                    rs.getLong("entry_version"),
                    rs.getTimestamp("occurred_at").toInstant()
                ),
                workspaceId, spaceId, entryId, historyLimit
            );
            return Optional.of(new RegisterEntry(
                entry, references, responses, history, false
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select request_hash, response_json
                      from project_register_commands
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
    public RegisterEntry create(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId,
        String requestHash, String entryType, String title, String summary,
        UUID ownerUserId, LocalDate dueDate, Integer probability, Integer impact,
        String decisionBasis, String changeImpact, List<ReferenceInput> references,
        List<ResponseInput> responses, Map<UUID, Long> sourceVersions
    ) {
        UUID entryId = UUID.randomUUID();
        String status = initialStatus(entryType);
        jdbc.update(
            """
                insert into project_register_entries(
                    id, workspace_id, space_id, entry_type, title, summary, status,
                    owner_user_id, due_date, probability, impact, decision_basis,
                    change_impact, aggregate_version, created_by, created_at,
                    updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, now(), ?, now())
                """,
            entryId, workspaceId, spaceId, entryType, title, summary, status,
            ownerUserId, date(dueDate), probability, impact, decisionBasis,
            changeImpact, actorId, actorId
        );
        replaceChildren(
            workspaceId, spaceId, entryId, references, responses, sourceVersions
        );
        appendHistory(
            workspaceId, spaceId, entryId, "create", "", status, "",
            actorId, 1
        );
        RegisterEntry result = require(workspaceId, spaceId, entryId);
        insertCommand(
            workspaceId, spaceId, actorId, entryId, "create",
            requestId, requestHash, result
        );
        return result;
    }

    @Override
    @Transactional
    public RegisterEntry mutate(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID entryId,
        String operation, String requestId, String requestHash, long expectedVersion,
        String reason, String title, String summary, UUID ownerUserId,
        LocalDate dueDate, Integer probability, Integer impact,
        String decisionBasis, String changeImpact, UUID supersedesEntryId,
        String verification, List<ReferenceInput> references,
        List<ResponseInput> responses, Map<UUID, Long> sourceVersions
    ) {
        RegisterSummary before = find(workspaceId, spaceId, entryId, 1)
            .map(RegisterEntry::entry)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Project register entry is not available"
            ));
        String target = targetStatus(before.entryType(), before.status(), operation);
        int changed = jdbc.update(
            """
                update project_register_entries
                   set title=?, summary=?, status=?, owner_user_id=?, due_date=?,
                       probability=?, impact=?, decision_basis=?, change_impact=?,
                       supersedes_entry_id=?, verification=?,
                       aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=? and aggregate_version=?
                """,
            title, summary, target, ownerUserId, date(dueDate), probability, impact,
            decisionBasis, changeImpact, supersedesEntryId, verification, actorId,
            workspaceId, spaceId, entryId, expectedVersion
        );
        if (changed != 1) {
            throw failure(
                "PROJECT_REGISTER_VERSION_CONFLICT",
                "Project register entry changed concurrently"
            );
        }
        replaceChildren(
            workspaceId, spaceId, entryId, references, responses, sourceVersions
        );
        appendHistory(
            workspaceId, spaceId, entryId, operation, before.status(), target,
            reason, actorId, expectedVersion + 1
        );
        RegisterEntry result = require(workspaceId, spaceId, entryId);
        insertCommand(
            workspaceId, spaceId, actorId, entryId, operation,
            requestId, requestHash, result
        );
        return result;
    }

    private void replaceChildren(
        UUID workspaceId, UUID spaceId, UUID entryId,
        List<ReferenceInput> references, List<ResponseInput> responses,
        Map<UUID, Long> sourceVersions
    ) {
        jdbc.update(
            "delete from project_register_references where workspace_id=? and space_id=? and entry_id=?",
            workspaceId, spaceId, entryId
        );
        jdbc.update(
            "delete from project_register_responses where workspace_id=? and space_id=? and entry_id=?",
            workspaceId, spaceId, entryId
        );
        for (ReferenceInput reference : references) {
            jdbc.update(
                """
                    insert into project_register_references(
                        id, workspace_id, space_id, entry_id, source_type,
                        source_id, source_version, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, now())
                    """,
                reference.id(), workspaceId, spaceId, entryId,
                reference.sourceType(), reference.sourceId(),
                sourceVersions.get(reference.sourceId())
            );
        }
        for (int position = 0; position < responses.size(); position++) {
            ResponseInput response = responses.get(position);
            jdbc.update(
                """
                    insert into project_register_responses(
                        id, workspace_id, space_id, entry_id, response_type,
                        description, owner_user_id, due_date, status, position
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                response.id(), workspaceId, spaceId, entryId,
                response.responseType(), response.description(),
                response.ownerUserId(), date(response.dueDate()),
                response.status(), position
            );
        }
    }

    private void appendHistory(
        UUID workspaceId, UUID spaceId, UUID entryId, String operation,
        String fromStatus, String toStatus, String reason, UUID actorId, long version
    ) {
        jdbc.update(
            """
                insert into project_register_history(
                    id, workspace_id, space_id, entry_id, history_sequence,
                    operation, from_status, to_status, reason, actor_id,
                    entry_version, occurred_at
                ) values (?, ?, ?, ?, (
                    select coalesce(max(history_sequence), 0) + 1
                      from project_register_history
                     where workspace_id=? and space_id=? and entry_id=?
                ), ?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, entryId,
            workspaceId, spaceId, entryId, operation, fromStatus, toStatus,
            reason, actorId, version
        );
    }

    private void insertCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID entryId,
        String operation, String requestId, String requestHash, RegisterEntry result
    ) {
        jdbc.update(
            """
                insert into project_register_commands(
                    id, workspace_id, space_id, actor_id, entry_id,
                    operation, request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, entryId,
            operation, requestId, requestHash, json(result)
        );
    }

    private RegisterEntry require(UUID workspaceId, UUID spaceId, UUID entryId) {
        return find(workspaceId, spaceId, entryId, 100)
            .orElseThrow(() -> new IllegalStateException("Register entry disappeared"));
    }

    private RegisterSummary summary(ResultSet rs) throws SQLException {
        Integer probability = (Integer) rs.getObject("probability");
        Integer impact = (Integer) rs.getObject("impact");
        return new RegisterSummary(
            rs.getObject("id", UUID.class),
            rs.getString("entry_type"),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("status"),
            rs.getObject("owner_user_id", UUID.class),
            rs.getObject("due_date", LocalDate.class),
            probability,
            impact,
            probability == null || impact == null ? 0 : probability * impact,
            rs.getString("decision_basis"),
            rs.getString("change_impact"),
            rs.getObject("supersedes_entry_id", UUID.class),
            rs.getString("verification"),
            rs.getLong("aggregate_version"),
            rs.getObject("created_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String initialStatus(String type) {
        return switch (type) {
            case "risk" -> "identified";
            case "issue" -> "open";
            case "decision", "change" -> "proposed";
            default -> throw new IllegalArgumentException("Unknown register type");
        };
    }

    private String targetStatus(String type, String current, String operation) {
        if ("update".equals(operation)) {
            return current;
        }
        return switch (type + ":" + operation) {
            case "risk:assess" -> "assessed";
            case "risk:monitor", "risk:reopen" -> "monitoring";
            case "risk:close" -> "closed";
            case "issue:escalate" -> "escalated";
            case "issue:resolve" -> "resolved";
            case "issue:verify" -> "verified";
            case "issue:reopen" -> "open";
            case "decision:adopt" -> "adopted";
            case "decision:supersede" -> "superseded";
            case "decision:revoke" -> "revoked";
            case "change:analyze" -> "analyzed";
            case "change:approve" -> "approved";
            case "change:reject" -> "rejected";
            case "change:apply" -> "applied";
            case "change:reopen" -> "proposed";
            default -> throw failure(
                "PROJECT_REGISTER_TRANSITION_INVALID",
                "Project register transition is invalid"
            );
        };
    }

    private Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize register receipt", exception);
        }
    }
}
