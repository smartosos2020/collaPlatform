package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.infrastructure.WorkItemRelationMigrationRepository.LegacyRelationUnit;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationMigrationRepository.MigrationBatch;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationMigrationRepository.MigrationUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemRelationMigrationRepository
    implements WorkItemRelationMigrationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemRelationMigrationRepository(
        JdbcTemplate jdbcTemplate, ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LegacyRelationUnit> inspectLegacy(UUID workspaceId, UUID spaceId) {
        return jdbcTemplate.query(
            """
            select gen_random_uuid() id, r.id source_relation_id, r.issue_id source_issue_id,
                   r.target_type, r.target_id,
                   md5(concat_ws(':', r.id, r.issue_id, r.target_type, r.target_id,
                                 r.created_by, r.created_at, r.deleted_at)) source_fingerprint,
                   case
                     when r.deleted_at is not null then 'deleted_source'
                     when r.target_type <> 'issue' then 'preserved_platform_reference'
                     when target_map.work_item_id is null then 'unresolved_target'
                     when target_map.space_id <> source_map.space_id then 'cross_space_target'
                     else 'canonical_work_item'
                   end classification,
                   source_map.work_item_id source_work_item_id,
                   case when target_map.space_id=source_map.space_id
                        then target_map.work_item_id end target_work_item_id
              from issue_relations r
              join project_legacy_work_item_maps source_map
                on source_map.workspace_id=r.workspace_id
               and source_map.source_type='issue'
               and source_map.source_id=r.issue_id
               and source_map.status='active'
              left join project_legacy_work_item_maps target_map
                on target_map.workspace_id=r.workspace_id
               and target_map.source_type='issue'
               and target_map.source_id=r.target_id
               and target_map.status='active'
             where r.workspace_id=? and source_map.space_id=?
             order by r.id
            """,
            (resultSet, rowNumber) -> new LegacyRelationUnit(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("source_relation_id", UUID.class),
                resultSet.getObject("source_issue_id", UUID.class),
                resultSet.getString("target_type"),
                resultSet.getObject("target_id", UUID.class),
                resultSet.getString("source_fingerprint"),
                resultSet.getString("classification"),
                resultSet.getObject("source_work_item_id", UUID.class),
                resultSet.getObject("target_work_item_id", UUID.class)
            ),
            workspaceId,
            spaceId
        );
    }

    @Override
    public void createBatch(MigrationBatch batch) {
        jdbcTemplate.update(
            """
            insert into project_work_item_relation_migration_batches(
                id, workspace_id, space_id, relation_key, request_id, manifest_hash,
                dry_run, status, version, total_count, canonical_count, preserved_count,
                completed_count, failed_count, reason_hash, initiated_by,
                initiated_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, 'planned', 0, ?, ?, ?, 0, 0, ?, ?, now(), now())
            """,
            batch.id(), batch.workspaceId(), batch.spaceId(), batch.relationKey(),
            batch.requestId(), batch.manifestHash(), batch.dryRun(), batch.totalCount(),
            batch.canonicalCount(), batch.preservedCount(), batch.reasonHash(),
            batch.initiatedBy()
        );
    }

    @Override
    public void insertUnits(UUID batchId, List<LegacyRelationUnit> units) {
        for (LegacyRelationUnit unit : units) {
            String status = "canonical_work_item".equals(unit.classification())
                ? "planned" : "preserved";
            jdbcTemplate.update(
                """
                insert into project_work_item_relation_migration_units(
                    id, workspace_id, space_id, batch_id, source_relation_id,
                    source_issue_id, target_type, target_id, source_fingerprint,
                    classification, source_work_item_id, target_work_item_id,
                    status, attempt, created_at, updated_at
                )
                select ?, b.workspace_id, b.space_id, b.id, ?, ?, ?, ?, ?, ?, ?, ?,
                       ?, 0, now(), now()
                  from project_work_item_relation_migration_batches b where b.id=?
                """,
                unit.id(), unit.sourceRelationId(), unit.sourceIssueId(), unit.targetType(),
                unit.targetId(), unit.sourceFingerprint(), unit.classification(),
                unit.sourceWorkItemId(), unit.targetWorkItemId(), status, batchId
            );
        }
    }

    @Override
    public Optional<MigrationBatch> findBatch(
        UUID workspaceId, UUID spaceId, UUID batchId, boolean lock
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                select id, workspace_id, space_id, relation_key, request_id, manifest_hash,
                       dry_run, status, version, total_count, canonical_count, preserved_count,
                       completed_count, failed_count, reason_hash, initiated_by,
                       initiated_at, updated_at, completed_at
                  from project_work_item_relation_migration_batches
                 where workspace_id=? and space_id=? and id=?
                """ + (lock ? " for update" : ""),
                (resultSet, rowNumber) -> new MigrationBatch(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("workspace_id", UUID.class),
                    resultSet.getObject("space_id", UUID.class),
                    resultSet.getString("relation_key"),
                    resultSet.getString("request_id"),
                    resultSet.getString("manifest_hash"),
                    resultSet.getBoolean("dry_run"),
                    resultSet.getString("status"),
                    resultSet.getLong("version"),
                    resultSet.getInt("total_count"),
                    resultSet.getInt("canonical_count"),
                    resultSet.getInt("preserved_count"),
                    resultSet.getInt("completed_count"),
                    resultSet.getInt("failed_count"),
                    resultSet.getString("reason_hash"),
                    resultSet.getObject("initiated_by", UUID.class),
                    resultSet.getTimestamp("initiated_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    instant(resultSet.getTimestamp("completed_at"))
                ),
                workspaceId, spaceId, batchId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MigrationBatch> findBatchByRequest(
        UUID workspaceId, UUID spaceId, String requestId
    ) {
        try {
            UUID id = jdbcTemplate.queryForObject(
                """
                select id from project_work_item_relation_migration_batches
                 where workspace_id=? and space_id=? and request_id=?
                """,
                UUID.class, workspaceId, spaceId, requestId
            );
            return id == null ? Optional.empty() : findBatch(workspaceId, spaceId, id, false);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<MigrationUnit> listUnits(
        UUID workspaceId, UUID spaceId, UUID batchId, List<String> statuses
    ) {
        StringBuilder sql = new StringBuilder("""
            select id, source_relation_id, source_issue_id, target_type, target_id,
                   source_fingerprint, classification, source_work_item_id,
                   target_work_item_id, relation_id, status, attempt, error_code
              from project_work_item_relation_migration_units
             where workspace_id=? and space_id=? and batch_id=?
            """);
        List<Object> parameters = new ArrayList<>(List.of(workspaceId, spaceId, batchId));
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" and status in (")
                .append(String.join(",", java.util.Collections.nCopies(statuses.size(), "?")))
                .append(")");
            parameters.addAll(statuses);
        }
        sql.append(" order by id");
        return jdbcTemplate.query(
            sql.toString(),
            (resultSet, rowNumber) -> new MigrationUnit(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("source_relation_id", UUID.class),
                resultSet.getObject("source_issue_id", UUID.class),
                resultSet.getString("target_type"),
                resultSet.getObject("target_id", UUID.class),
                resultSet.getString("source_fingerprint"),
                resultSet.getString("classification"),
                resultSet.getObject("source_work_item_id", UUID.class),
                resultSet.getObject("target_work_item_id", UUID.class),
                resultSet.getObject("relation_id", UUID.class),
                resultSet.getString("status"),
                resultSet.getInt("attempt"),
                resultSet.getString("error_code")
            ),
            parameters.toArray()
        );
    }

    @Override
    public int transitionBatch(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        long expectedVersion,
        List<String> expectedStatuses,
        String targetStatus
    ) {
        String placeholders = String.join(
            ",", java.util.Collections.nCopies(expectedStatuses.size(), "?")
        );
        List<Object> parameters = new ArrayList<>();
        parameters.add(targetStatus);
        parameters.add(targetStatus);
        parameters.add(workspaceId);
        parameters.add(spaceId);
        parameters.add(batchId);
        parameters.add(expectedVersion);
        parameters.addAll(expectedStatuses);
        return jdbcTemplate.update(
            """
            update project_work_item_relation_migration_batches
               set status=?, version=version+1, updated_at=now(),
                   completed_at=case when ? in ('completed','failed','verified','rolled_back')
                                     then now() else null end
             where workspace_id=? and space_id=? and id=? and version=?
               and status in (""" + placeholders + ")",
            parameters.toArray()
        );
    }

    @Override
    public void markUnitCompleted(UUID unitId, UUID relationId) {
        jdbcTemplate.update(
            """
            update project_work_item_relation_migration_units
               set relation_id=?, status='completed', attempt=attempt+1,
                   error_code=null, updated_at=now()
             where id=? and status in ('planned','failed')
            """,
            relationId, unitId
        );
    }

    @Override
    public void markUnitFailed(UUID unitId, String errorCode) {
        jdbcTemplate.update(
            """
            update project_work_item_relation_migration_units
               set status='failed', attempt=attempt+1, error_code=?, updated_at=now()
             where id=? and status in ('planned','failed')
            """,
            errorCode, unitId
        );
    }

    @Override
    public void markUnitVerified(UUID unitId) {
        jdbcTemplate.update(
            """
            update project_work_item_relation_migration_units
               set status='verified', updated_at=now()
             where id=? and status='completed'
            """,
            unitId
        );
    }

    @Override
    public void markUnitRolledBack(UUID unitId) {
        jdbcTemplate.update(
            """
            update project_work_item_relation_migration_units
               set status='rolled_back', updated_at=now()
             where id=? and status in ('completed','verified')
            """,
            unitId
        );
    }

    @Override
    public void refreshCounts(
        UUID workspaceId, UUID spaceId, UUID batchId, String status
    ) {
        jdbcTemplate.update(
            """
            update project_work_item_relation_migration_batches b
               set completed_count=(
                       select count(*) from project_work_item_relation_migration_units u
                        where u.batch_id=b.id and u.status in ('completed','verified','rolled_back')
                   ),
                   failed_count=(
                       select count(*) from project_work_item_relation_migration_units u
                        where u.batch_id=b.id and u.status='failed'
                   ),
                   status=?, version=version+1, updated_at=now(),
                   completed_at=case when ? in ('completed','failed','verified','rolled_back')
                                     then now() else null end
             where b.workspace_id=? and b.space_id=? and b.id=?
            """,
            status, status, workspaceId, spaceId, batchId
        );
    }

    @Override
    public List<UUID> verificationFailures(
        UUID workspaceId, UUID spaceId, UUID batchId
    ) {
        return jdbcTemplate.query(
            """
            select u.id
              from project_work_item_relation_migration_units u
              left join project_work_item_relations r
                on r.workspace_id=u.workspace_id and r.space_id=u.space_id
               and r.id=u.relation_id
             where u.workspace_id=? and u.space_id=? and u.batch_id=?
               and u.classification='canonical_work_item'
               and (
                   u.status not in ('completed','verified')
                   or r.id is null or r.status <> 'active'
                   or r.source_work_item_id <> u.source_work_item_id
                   or r.target_work_item_id <> u.target_work_item_id
               )
             order by u.id
            """,
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            workspaceId, spaceId, batchId
        );
    }

    @Override
    public void appendVerification(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        boolean passed,
        int checkedCount,
        List<UUID> failureUnitIds,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
            insert into project_work_item_relation_migration_verifications(
                id, workspace_id, space_id, batch_id, outcome, checked_count,
                failure_count, safe_failures, verified_by, verified_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
            """,
            UUID.randomUUID(), workspaceId, spaceId, batchId,
            passed ? "passed" : "failed", checkedCount, failureUnitIds.size(),
            json(failureUnitIds), actorId
        );
    }

    private java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid migration verification JSON", exception);
        }
    }
}
