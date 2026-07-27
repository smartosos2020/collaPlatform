package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Acceptance;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Deliverable;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableVersion;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MaterialInput;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MaterialReference;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.ReviewRound;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Signoff;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcProjectDeliveryRepository implements ProjectDeliveryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProjectDeliveryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DeliverableSummary> list(
        UUID workspaceId, UUID spaceId, int limit
    ) {
        return jdbc.query(
            """
                select id, title, summary, status, owner_user_id, due_date,
                       plan_id, milestone_id, register_entry_ids, current_version_id,
                       aggregate_version, created_by, created_at, updated_by, updated_at
                  from project_deliverables
                 where workspace_id=? and space_id=?
                 order by case status when 'accepted' then 1 when 'archived' then 2 else 0 end,
                          updated_at desc, id
                 limit ?
                """,
            (rs, row) -> summary(rs), workspaceId, spaceId, limit
        );
    }

    @Override
    public Optional<Deliverable> find(
        UUID workspaceId, UUID spaceId, UUID deliverableId
    ) {
        try {
            DeliverableSummary summary = jdbc.queryForObject(
                """
                    select id, title, summary, status, owner_user_id, due_date,
                           plan_id, milestone_id, register_entry_ids, current_version_id,
                           aggregate_version, created_by, created_at, updated_by, updated_at
                      from project_deliverables
                     where workspace_id=? and space_id=? and id=?
                    """,
                (rs, row) -> summary(rs), workspaceId, spaceId, deliverableId
            );
            List<DeliverableVersion> versions = jdbc.query(
                """
                    select id, version_sequence, version_label, version_note,
                           submitted_by, submitted_at
                      from project_deliverable_versions
                     where workspace_id=? and space_id=? and deliverable_id=?
                     order by version_sequence desc
                    """,
                (rs, row) -> new DeliverableVersion(
                    rs.getObject("id", UUID.class),
                    rs.getInt("version_sequence"),
                    rs.getString("version_label"),
                    rs.getString("version_note"),
                    rs.getObject("submitted_by", UUID.class),
                    rs.getTimestamp("submitted_at").toInstant(),
                    materials(workspaceId, spaceId, deliverableId,
                        rs.getObject("id", UUID.class))
                ),
                workspaceId, spaceId, deliverableId
            );
            List<ReviewRound> reviews = jdbc.query(
                """
                    select id, review_round, deliverable_version_id, review_items,
                           required_signer_ids, quorum, status, conclusion,
                           opened_at, closed_at
                      from project_deliverable_reviews
                     where workspace_id=? and space_id=? and deliverable_id=?
                     order by review_round desc
                    """,
                (rs, row) -> new ReviewRound(
                    rs.getObject("id", UUID.class),
                    rs.getInt("review_round"),
                    rs.getObject("deliverable_version_id", UUID.class),
                    strings(rs.getString("review_items")),
                    uuids(rs, "required_signer_ids"),
                    rs.getInt("quorum"),
                    rs.getString("status"),
                    rs.getString("conclusion"),
                    signoffs(
                        workspaceId, spaceId, deliverableId,
                        rs.getObject("id", UUID.class)
                    ),
                    rs.getTimestamp("opened_at").toInstant(),
                    rs.getTimestamp("closed_at") == null
                        ? null : rs.getTimestamp("closed_at").toInstant()
                ),
                workspaceId, spaceId, deliverableId
            );
            List<Acceptance> acceptances = jdbc.query(
                """
                    select acceptance_sequence, conclusion, comment, actor_id,
                           review_id, occurred_at
                      from project_deliverable_acceptances
                     where workspace_id=? and space_id=? and deliverable_id=?
                     order by acceptance_sequence desc
                    """,
                (rs, row) -> new Acceptance(
                    rs.getLong("acceptance_sequence"),
                    rs.getString("conclusion"),
                    rs.getString("comment"),
                    rs.getObject("actor_id", UUID.class),
                    rs.getObject("review_id", UUID.class),
                    rs.getTimestamp("occurred_at").toInstant()
                ),
                workspaceId, spaceId, deliverableId
            );
            return Optional.of(new Deliverable(
                summary, versions, reviews, acceptances, false
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
                      from project_deliverable_commands
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
    public Deliverable create(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId,
        String requestHash, String title, String summary, UUID ownerUserId,
        LocalDate dueDate, UUID planId, UUID milestoneId,
        List<UUID> registerEntryIds
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_deliverables(
                    id, workspace_id, space_id, title, summary, status,
                    owner_user_id, due_date, plan_id, milestone_id,
                    register_entry_ids, aggregate_version, created_by, created_at,
                    updated_by, updated_at
                ) values (?, ?, ?, ?, ?, 'draft', ?, ?, ?, ?, cast(? as uuid[]),
                          1, ?, now(), ?, now())
                """,
            id, workspaceId, spaceId, title, summary, ownerUserId, date(dueDate),
            planId, milestoneId, uuidArray(registerEntryIds), actorId, actorId
        );
        Deliverable result = require(workspaceId, spaceId, id);
        insertCommand(
            workspaceId, spaceId, actorId, id, "create",
            requestId, requestHash, result
        );
        return result;
    }

    @Override
    @Transactional
    public Deliverable mutate(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID deliverableId,
        String operation, String requestId, String requestHash, long expectedVersion,
        String reason, String title, String summary, UUID ownerUserId,
        LocalDate dueDate, String versionLabel, String versionNote,
        List<MaterialInput> materials, Map<UUID, Long> materialVersions,
        List<String> reviewItems, List<UUID> requiredSignerIds, int quorum,
        String conclusion, String comment
    ) {
        Deliverable before = require(workspaceId, spaceId, deliverableId);
        UUID currentVersion = before.deliverable().currentVersionId();
        if ("submit_version".equals(operation)) {
            currentVersion = insertVersion(
                workspaceId, spaceId, actorId, deliverableId,
                versionLabel, versionNote, materials, materialVersions,
                before.versions().size() + 1
            );
        } else if ("open_review".equals(operation)
            || "reopen_review".equals(operation)) {
            if (currentVersion == null) {
                throw failure(
                    "PROJECT_DELIVERABLE_VERSION_REQUIRED",
                    "A submitted version is required"
                );
            }
            insertReview(
                workspaceId, spaceId, actorId, deliverableId, currentVersion,
                reviewItems, requiredSignerIds, quorum, before.reviews().size() + 1
            );
        } else if ("sign".equals(operation) || "revoke_signoff".equals(operation)) {
            ReviewRound review = openReview(before);
            insertSignoff(
                workspaceId, spaceId, deliverableId, review.id(), actorId,
                "revoke_signoff".equals(operation) ? "revoke" : conclusion,
                comment
            );
        } else if ("close_review".equals(operation)) {
            ReviewRound review = openReview(before);
            String reviewResult = reviewResult(
                workspaceId, spaceId, deliverableId, review
            );
            jdbc.update(
                """
                    update project_deliverable_reviews
                       set status=?, conclusion=?, closed_at=now()
                     where workspace_id=? and space_id=? and deliverable_id=?
                       and id=? and status='open'
                    """,
                reviewResult, comment, workspaceId, spaceId, deliverableId, review.id()
            );
        } else if ("accept".equals(operation) || "reject".equals(operation)) {
            ReviewRound review = before.reviews().stream()
                .findFirst().orElseThrow(() -> failure(
                    "PROJECT_DELIVERABLE_REVIEW_REQUIRED", "A closed review is required"
                ));
            if ("open".equals(review.status())
                || ("accept".equals(operation) && !"approved".equals(review.status()))) {
                throw failure(
                    "PROJECT_DELIVERABLE_ACCEPTANCE_INVALID",
                    "Review result does not permit this acceptance"
                );
            }
            insertAcceptance(
                workspaceId, spaceId, deliverableId, review.id(), actorId,
                "accept".equals(operation) ? "accepted" : "rejected", comment
            );
        }
        String status = targetStatus(before.deliverable().status(), operation);
        int changed = jdbc.update(
            """
                update project_deliverables
                   set title=?, summary=?, status=?, owner_user_id=?, due_date=?,
                       current_version_id=?, aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=? and aggregate_version=?
                """,
            title, summary, status, ownerUserId, date(dueDate), currentVersion,
            actorId, workspaceId, spaceId, deliverableId, expectedVersion
        );
        if (changed != 1) {
            throw failure(
                "PROJECT_DELIVERABLE_VERSION_CONFLICT",
                "Project deliverable changed concurrently"
            );
        }
        Deliverable result = require(workspaceId, spaceId, deliverableId);
        insertCommand(
            workspaceId, spaceId, actorId, deliverableId, operation,
            requestId, requestHash, result
        );
        return result;
    }

    private UUID insertVersion(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID deliverableId,
        String label, String note, List<MaterialInput> materials,
        Map<UUID, Long> materialVersions, int sequence
    ) {
        UUID versionId = UUID.randomUUID();
        jdbc.update(
            """
                insert into project_deliverable_versions(
                    id, workspace_id, space_id, deliverable_id, version_sequence,
                    version_label, version_note, submitted_by, submitted_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            versionId, workspaceId, spaceId, deliverableId, sequence,
            label, note, actorId
        );
        for (int position = 0; position < materials.size(); position++) {
            MaterialInput material = materials.get(position);
            jdbc.update(
                """
                    insert into project_deliverable_materials(
                        id, workspace_id, space_id, deliverable_id, version_id,
                        source_type, source_id, source_version, external_uri, position
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                material.id(), workspaceId, spaceId, deliverableId, versionId,
                material.sourceType(), material.sourceId(),
                material.sourceId() == null ? null : materialVersions.get(material.sourceId()),
                material.externalUri(), position
            );
        }
        return versionId;
    }

    private void insertReview(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID deliverableId,
        UUID versionId, List<String> reviewItems, List<UUID> requiredSignerIds,
        int quorum, int round
    ) {
        jdbc.update(
            """
                insert into project_deliverable_reviews(
                    id, workspace_id, space_id, deliverable_id,
                    deliverable_version_id, review_round, review_items,
                    required_signer_ids, quorum, status, opened_by, opened_at
                ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as uuid[]),
                          ?, 'open', ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, deliverableId, versionId,
            round, json(reviewItems), uuidArray(requiredSignerIds), quorum, actorId
        );
    }

    private void insertSignoff(
        UUID workspaceId, UUID spaceId, UUID deliverableId, UUID reviewId,
        UUID signerId, String conclusion, String comment
    ) {
        jdbc.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            resultSet -> null,
            "project-deliverable-signoff:" + workspaceId + ":" + deliverableId + ":" + reviewId
        );
        jdbc.update(
            """
                insert into project_deliverable_signoffs(
                    id, workspace_id, space_id, deliverable_id, review_id,
                    signoff_sequence, signer_id, conclusion, comment, occurred_at
                ) values (?, ?, ?, ?, ?, (
                    select coalesce(max(signoff_sequence), 0) + 1
                      from project_deliverable_signoffs
                     where workspace_id=? and space_id=? and deliverable_id=? and review_id=?
                ), ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, deliverableId, reviewId,
            workspaceId, spaceId, deliverableId, reviewId,
            signerId, conclusion, comment
        );
    }

    private String reviewResult(
        UUID workspaceId, UUID spaceId, UUID deliverableId, ReviewRound review
    ) {
        Map<UUID, String> current = new LinkedHashMap<>();
        for (Signoff signoff : signoffs(
            workspaceId, spaceId, deliverableId, review.id()
        )) {
            current.putIfAbsent(
                signoff.signerId(), signoff.revoked() ? "revoke" : signoff.conclusion()
            );
        }
        if (current.values().stream().anyMatch("reject"::equals)) {
            return "rejected";
        }
        long approved = current.values().stream().filter("approve"::equals).count();
        if (approved < review.quorum()) {
            throw failure(
                "PROJECT_DELIVERABLE_QUORUM_NOT_MET", "Review quorum is not met"
            );
        }
        return "approved";
    }

    private void insertAcceptance(
        UUID workspaceId, UUID spaceId, UUID deliverableId, UUID reviewId,
        UUID actorId, String conclusion, String comment
    ) {
        jdbc.update(
            """
                insert into project_deliverable_acceptances(
                    id, workspace_id, space_id, deliverable_id,
                    acceptance_sequence, review_id, conclusion, comment,
                    actor_id, occurred_at
                ) values (?, ?, ?, ?, (
                    select coalesce(max(acceptance_sequence), 0) + 1
                      from project_deliverable_acceptances
                     where workspace_id=? and space_id=? and deliverable_id=?
                ), ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, deliverableId,
            workspaceId, spaceId, deliverableId, reviewId, conclusion, comment, actorId
        );
    }

    private List<MaterialReference> materials(
        UUID workspaceId, UUID spaceId, UUID deliverableId, UUID versionId
    ) {
        return jdbc.query(
            """
                select id, source_type, source_id, source_version, external_uri
                  from project_deliverable_materials
                 where workspace_id=? and space_id=? and deliverable_id=? and version_id=?
                 order by position, id
                """,
            (rs, row) -> new MaterialReference(
                rs.getObject("id", UUID.class),
                rs.getString("source_type"),
                rs.getObject("source_id", UUID.class),
                rs.getLong("source_version"),
                rs.getString("external_uri")
            ),
            workspaceId, spaceId, deliverableId, versionId
        );
    }

    private List<Signoff> signoffs(
        UUID workspaceId, UUID spaceId, UUID deliverableId, UUID reviewId
    ) {
        return jdbc.query(
            """
                select signoff_sequence, signer_id, conclusion, comment, occurred_at
                  from project_deliverable_signoffs
                 where workspace_id=? and space_id=? and deliverable_id=? and review_id=?
                 order by signoff_sequence desc
                """,
            (rs, row) -> new Signoff(
                rs.getLong("signoff_sequence"),
                rs.getObject("signer_id", UUID.class),
                rs.getString("conclusion"),
                rs.getString("comment"),
                "revoke".equals(rs.getString("conclusion")),
                rs.getTimestamp("occurred_at").toInstant()
            ),
            workspaceId, spaceId, deliverableId, reviewId
        );
    }

    private ReviewRound openReview(Deliverable deliverable) {
        return deliverable.reviews().stream()
            .filter(review -> "open".equals(review.status()))
            .findFirst()
            .orElseThrow(() -> failure(
                "PROJECT_DELIVERABLE_REVIEW_NOT_OPEN", "No review is open"
            ));
    }

    private String targetStatus(String current, String operation) {
        return switch (operation) {
            case "update", "sign", "revoke_signoff" -> current;
            case "submit_version" -> "submitted";
            case "withdraw_version" -> "withdrawn";
            case "open_review", "reopen_review" -> "reviewing";
            case "close_review" -> "reviewed";
            case "accept" -> "accepted";
            case "reject" -> "rejected";
            case "archive" -> "archived";
            case "restore" -> "draft";
            default -> throw failure(
                "PROJECT_DELIVERABLE_OPERATION_INVALID",
                "Deliverable operation is invalid"
            );
        };
    }

    private void insertCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID deliverableId,
        String operation, String requestId, String requestHash, Deliverable result
    ) {
        jdbc.update(
            """
                insert into project_deliverable_commands(
                    id, workspace_id, space_id, actor_id, deliverable_id,
                    operation, request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, deliverableId,
            operation, requestId, requestHash, json(result)
        );
    }

    private Deliverable require(UUID workspaceId, UUID spaceId, UUID id) {
        return find(workspaceId, spaceId, id)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Project deliverable is not available"
            ));
    }

    private DeliverableSummary summary(ResultSet rs) throws SQLException {
        return new DeliverableSummary(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("status"),
            rs.getObject("owner_user_id", UUID.class),
            rs.getObject("due_date", LocalDate.class),
            rs.getObject("plan_id", UUID.class),
            rs.getObject("milestone_id", UUID.class),
            uuids(rs, "register_entry_ids"),
            rs.getObject("current_version_id", UUID.class),
            rs.getLong("aggregate_version"),
            rs.getObject("created_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private List<UUID> uuids(ResultSet rs, String column) throws SQLException {
        Object[] values = (Object[]) rs.getArray(column).getArray();
        List<UUID> result = new ArrayList<>(values.length);
        for (Object value : values) {
            result.add(value instanceof UUID uuid ? uuid : UUID.fromString(value.toString()));
        }
        return List.copyOf(result);
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read review items", exception);
        }
    }

    private String uuidArray(List<UUID> values) {
        return "{" + values.stream().map(UUID::toString)
            .reduce((left, right) -> left + "," + right).orElse("") + "}";
    }

    private Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize deliverable value", exception);
        }
    }
}
