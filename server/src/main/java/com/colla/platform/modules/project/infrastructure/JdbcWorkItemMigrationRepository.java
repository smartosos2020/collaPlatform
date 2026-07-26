package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemMigrationModels.LegacyMapTarget;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationBatch;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationFailure;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationPlan;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationPlanUnit;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationUnit;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationVerification;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.TypeBinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkItemMigrationRepository implements WorkItemMigrationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemMigrationRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<UUID> listLegacyProjectIds(UUID workspaceId) {
        return jdbc.query(
            "select id from projects where workspace_id=? order by id",
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            workspaceId
        );
    }

    @Override
    public Optional<UUID> findActiveSpace(UUID workspaceId, UUID legacyProjectId) {
        return jdbc.query("""
            select space_id from project_legacy_space_maps
             where workspace_id=? and legacy_project_id=? and mapping_status='active'
            """, (rs, rowNum) -> rs.getObject(1, UUID.class), workspaceId, legacyProjectId)
            .stream().findFirst();
    }

    @Override
    public JsonNode loadManifest(UUID workspaceId, UUID legacyProjectId) {
        return jdbc.query("""
            select jsonb_build_object(
              'schemaVersion', 1,
              'project', jsonb_build_object(
                'id',p.id,'projectKey',p.project_key,'name',p.name,'description',p.description,
                'status',p.status,'createdBy',p.created_by,'createdAt',p.created_at,
                'updatedBy',coalesce(p.updated_by,p.created_by),'updatedAt',p.updated_at,
                'archivedAt',p.archived_at
              ),
              'members', coalesce((
                select jsonb_agg(jsonb_build_object(
                  'id',m.id,'userId',m.user_id,'role',m.project_role,'joinedAt',m.joined_at,
                  'createdBy',m.created_by,'archivedAt',m.archived_at
                ) order by m.id)
                from project_members m
                where m.workspace_id=p.workspace_id and m.project_id=p.id
              ), '[]'::jsonb),
              'issues', coalesce((
                select jsonb_agg(jsonb_build_object(
                  'id',i.id,'issueKey',i.issue_key,'issueType',i.issue_type,'title',i.title,
                  'description',i.description,'priority',i.priority,'status',i.status,
                  'assigneeId',i.assignee_id,'reporterId',i.reporter_id,'dueAt',i.due_at,
                  'createdBy',i.created_by,'createdAt',i.created_at,
                  'updatedBy',coalesce(i.updated_by,i.created_by),'updatedAt',i.updated_at,
                  'deletedAt',i.deleted_at,
                  'comments',coalesce((
                    select jsonb_agg(jsonb_build_object(
                      'id',c.id,'authorId',c.author_id,'content',c.content,
                      'createdAt',c.created_at,'updatedAt',c.updated_at,'deletedAt',c.deleted_at
                    ) order by c.id)
                    from issue_comments c
                    where c.workspace_id=i.workspace_id and c.issue_id=i.id
                  ),'[]'::jsonb),
                  'attachments',coalesce((
                    select jsonb_agg(jsonb_build_object(
                      'id',a.id,'fileId',a.file_id,'createdBy',a.created_by,'createdAt',a.created_at
                    ) order by a.id)
                    from issue_attachments a
                    where a.workspace_id=i.workspace_id and a.issue_id=i.id
                  ),'[]'::jsonb),
                  'activities',coalesce((
                    select jsonb_agg(jsonb_build_object(
                      'id',l.id,'actorId',l.actor_id,'action',l.action,
                      'fromValue',l.from_value,'toValue',l.to_value,
                      'metadata',coalesce(l.metadata,'{}'::jsonb),'createdAt',l.created_at
                    ) order by l.id)
                    from issue_activity_logs l
                    where l.workspace_id=i.workspace_id and l.issue_id=i.id
                  ),'[]'::jsonb),
                  'relations',coalesce((
                    select jsonb_agg(jsonb_build_object(
                      'id',r.id,'targetType',r.target_type,'targetId',r.target_id,
                      'createdBy',r.created_by,'createdAt',r.created_at,'deletedAt',r.deleted_at
                    ) order by r.id)
                    from issue_relations r
                    where r.workspace_id=i.workspace_id and r.issue_id=i.id
                  ),'[]'::jsonb)
                ) order by i.id)
                from issues i
                where i.workspace_id=p.workspace_id and i.project_id=p.id
              ), '[]'::jsonb)
            ) manifest
            from projects p where p.workspace_id=? and p.id=?
            """, (rs, rowNum) -> json(rs.getString("manifest")), workspaceId, legacyProjectId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("LEGACY_PROJECT_NOT_FOUND"));
    }

    @Override
    public Optional<TypeBinding> findTypeBinding(UUID workspaceId, UUID spaceId, String typeKey) {
        return jdbc.query("""
            select t.id type_id,t.current_version_id,v.config_hash,t.type_key
              from project_work_item_types t
              join project_work_item_type_versions v
                on v.workspace_id=t.workspace_id and v.space_id=t.space_id
               and v.type_definition_id=t.id and v.id=t.current_version_id
             where t.workspace_id=? and t.space_id=? and t.type_key=?
               and t.status='active' and v.status in ('published','superseded')
            """, (rs, rowNum) -> new TypeBinding(
                rs.getObject("type_id", UUID.class),
                rs.getObject("current_version_id", UUID.class),
                rs.getString("type_key"),
                rs.getString("config_hash")
            ), workspaceId, spaceId, typeKey).stream().findFirst();
    }

    @Override
    @Transactional
    public UUID insertPlan(
        UUID workspaceId,
        MigrationPlan plan,
        boolean dryRun,
        int throttleMillis,
        UUID actorId
    ) {
        UUID batchId = UUID.randomUUID();
        String status = dryRun ? "completed" : "planned";
        JsonNode planPayload = objectMapper.valueToTree(Map.of(
            "schemaVersion", 1,
            "dryRun", dryRun,
            "unitCount", plan.units().size(),
            "preflightFailureCount", plan.failures().size()
        ));
        jdbc.update("""
            insert into project_work_item_migration_batches(
              id,workspace_id,status,source_watermark,source_fingerprint,
              manifest_fingerprint,initiated_by,initiated_at,finished_at,
              plan_payload,plan_fingerprint,throttle_millis
            ) values (?,?,?,?,?,?,?,now(),?,?::jsonb,?,?)
            """, batchId, workspaceId, status, timestamp(plan.sourceWatermark()), plan.sourceFingerprint(),
            plan.planFingerprint(), actorId, dryRun ? Timestamp.from(Instant.now()) : null,
            text(planPayload), plan.planFingerprint(), throttleMillis);
        for (MigrationPlanUnit unit : plan.units()) {
            UUID unitId = UUID.randomUUID();
            jdbc.update("""
                insert into project_work_item_migration_units(
                  id,workspace_id,batch_id,legacy_project_id,space_id,status,
                  attempt,source_fingerprint
                ) values (?,?,?,?,?,'planned',0,?)
                """, unitId, workspaceId, batchId, unit.legacyProjectId(), unit.spaceId(),
                unit.sourceFingerprint());
            jdbc.update("""
                insert into project_work_item_migration_manifests(
                  id,workspace_id,batch_id,unit_id,manifest_version,
                  source_watermark,source_fingerprint,payload,recorded_at
                ) values (?,?,?,?,1,?,?,?::jsonb,now())
                """, UUID.randomUUID(), workspaceId, batchId, unitId, timestamp(plan.sourceWatermark()),
                unit.sourceFingerprint(), text(unit.manifest()));
        }
        for (MigrationFailure failure : plan.failures()) {
            appendFailure(
                workspaceId, batchId, null, failure.failureCode(), failure.sourceType(),
                failure.sourceId(), objectMapper.valueToTree(failure.safeDetail())
            );
        }
        return batchId;
    }

    @Override
    public Optional<MigrationBatch> findBatch(UUID workspaceId, UUID batchId) {
        return jdbc.query("""
            select * from project_work_item_migration_batches
             where workspace_id=? and id=?
            """, this::mapBatch, workspaceId, batchId).stream().findFirst()
            .map(batch -> withChildren(batch, workspaceId));
    }

    @Override
    public List<MigrationBatch> listBatches(UUID workspaceId) {
        return jdbc.query("""
            select * from project_work_item_migration_batches
             where workspace_id=? order by initiated_at desc,id desc
            """, this::mapBatch, workspaceId).stream()
            .map(batch -> withChildren(batch, workspaceId))
            .toList();
    }

    @Override
    public List<MigrationUnit> listUnits(UUID workspaceId, UUID batchId) {
        return jdbc.query("""
            select * from project_work_item_migration_units
             where workspace_id=? and batch_id=? order by id
            """, this::mapUnit, workspaceId, batchId);
    }

    @Override
    public List<MigrationFailure> listFailures(UUID workspaceId, UUID batchId) {
        return jdbc.query("""
            select * from project_work_item_migration_failures
             where workspace_id=? and batch_id=? order by recorded_at,id
            """, this::mapFailure, workspaceId, batchId);
    }

    @Override
    public Optional<MigrationPlanUnit> loadUnitManifest(
        UUID workspaceId,
        UUID batchId,
        UUID unitId
    ) {
        return jdbc.query("""
            select u.legacy_project_id,u.space_id,u.source_fingerprint,m.payload,
              1
              + jsonb_array_length(m.payload->'source'->'members')
              + jsonb_array_length(m.payload->'source'->'issues')
              + coalesce((
                  select sum(
                    jsonb_array_length(issue->'comments')
                    + jsonb_array_length(issue->'attachments')
                    + jsonb_array_length(issue->'activities')
                  ) from jsonb_array_elements(m.payload->'source'->'issues') issue
                ),0) object_count
              from project_work_item_migration_units u
              join project_work_item_migration_manifests m
                on m.workspace_id=u.workspace_id and m.unit_id=u.id and m.manifest_version=1
             where u.workspace_id=? and u.batch_id=? and u.id=?
            """, (rs, rowNum) -> new MigrationPlanUnit(
                rs.getObject("legacy_project_id", UUID.class),
                rs.getObject("space_id", UUID.class),
                rs.getString("source_fingerprint"),
                json(rs.getString("payload")),
                rs.getInt("object_count")
            ), workspaceId, batchId, unitId).stream().findFirst();
    }

    @Override
    public Lease acquireLease(UUID workspaceId, UUID batchId, String owner, Instant staleBefore) {
        return jdbc.query("""
            update project_work_item_migration_batches
               set lease_owner=?,lease_token=gen_random_uuid(),fence_version=fence_version+1,
                   heartbeat_at=now(),version=version+1
             where workspace_id=? and id=?
               and status in ('planned','running','paused','failed')
               and (lease_token is null or heartbeat_at < ?)
            returning lease_token,fence_version
            """, (rs, rowNum) -> new Lease(
                rs.getObject("lease_token", UUID.class), rs.getLong("fence_version")
            ), owner, workspaceId, batchId, timestamp(staleBefore)).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("MIGRATION_LEASE_UNAVAILABLE"));
    }

    @Override
    public void releaseLease(UUID workspaceId, UUID batchId, UUID token, long fenceVersion) {
        jdbc.update("""
            update project_work_item_migration_batches
               set lease_owner=null,lease_token=null,heartbeat_at=null
             where workspace_id=? and id=? and lease_token=? and fence_version=?
            """, workspaceId, batchId, token, fenceVersion);
    }

    @Override
    public boolean heartbeat(UUID workspaceId, UUID batchId, UUID token, long fenceVersion) {
        return jdbc.update("""
            update project_work_item_migration_batches set heartbeat_at=now()
             where workspace_id=? and id=? and lease_token=? and fence_version=?
            """, workspaceId, batchId, token, fenceVersion) == 1;
    }

    @Override
    public void changeBatchStatus(
        UUID workspaceId,
        UUID batchId,
        String expectedStatus,
        String status,
        String pausedReason,
        UUID token,
        long fenceVersion
    ) {
        if ("running".equals(status) && Set.of("failed", "paused").contains(expectedStatus)) {
            jdbc.update("""
                update project_work_item_migration_units set status='paused'
                 where workspace_id=? and batch_id=? and status='failed'
                """, workspaceId, batchId);
        }
        int changed = jdbc.update("""
            update project_work_item_migration_batches
               set status=?,paused_reason=?,version=version+1,
                   finished_at=case when ? in ('completed','failed','rolled_back') then now() else null end
             where workspace_id=? and id=? and status=? and lease_token=? and fence_version=?
            """, status, pausedReason, status, workspaceId, batchId, expectedStatus, token, fenceVersion);
        if (changed != 1) {
            throw new IllegalStateException("MIGRATION_FENCE_CONFLICT");
        }
    }

    @Override
    public void requestPause(UUID workspaceId, UUID batchId, String reason) {
        if (jdbc.update("""
            update project_work_item_migration_batches
               set status='paused',paused_reason=?,version=version+1
             where workspace_id=? and id=? and status in ('planned','running','failed')
            """, reason, workspaceId, batchId) != 1) {
            throw new IllegalStateException("MIGRATION_NOT_PAUSABLE");
        }
        jdbc.update("""
            update project_work_item_migration_units set status='paused'
             where workspace_id=? and batch_id=? and status='planned'
            """, workspaceId, batchId);
    }

    @Override
    public Optional<MigrationUnit> claimNextUnit(
        UUID workspaceId,
        UUID batchId,
        UUID token,
        long fenceVersion
    ) {
        return jdbc.query("""
            with owned_batch as (
              select id from project_work_item_migration_batches
               where workspace_id=? and id=? and lease_token=? and fence_version=?
            ), candidate as (
              select u.id from project_work_item_migration_units u
              join owned_batch b on b.id=u.batch_id
              where u.workspace_id=? and u.status in ('planned','paused')
              order by u.id for update skip locked limit 1
            )
            update project_work_item_migration_units u
               set status='running',attempt=attempt+1,started_at=coalesce(started_at,now()),
                   finished_at=null,last_error_code=null,fence_version=?
              from candidate c where u.id=c.id
            returning u.*
            """, this::mapUnit, workspaceId, batchId, token, fenceVersion, workspaceId, fenceVersion)
            .stream().findFirst();
    }

    @Override
    public void completeUnit(UUID workspaceId, UUID unitId, long fenceVersion, int migratedObjects) {
        if (jdbc.update("""
            update project_work_item_migration_units
               set status='completed',migrated_objects=?,finished_at=now(),last_error_code=null
             where workspace_id=? and id=? and status='running' and fence_version=?
            """, migratedObjects, workspaceId, unitId, fenceVersion) != 1) {
            throw new IllegalStateException("MIGRATION_UNIT_FENCE_CONFLICT");
        }
    }

    @Override
    public void failUnit(UUID workspaceId, UUID unitId, long fenceVersion, String errorCode) {
        if (jdbc.update("""
            update project_work_item_migration_units
               set status='failed',last_error_code=?,finished_at=now()
             where workspace_id=? and id=? and status='running' and fence_version=?
            """, errorCode, workspaceId, unitId, fenceVersion) != 1) {
            throw new IllegalStateException("MIGRATION_UNIT_FENCE_CONFLICT");
        }
    }

    @Override
    public void appendFailure(
        UUID workspaceId,
        UUID batchId,
        UUID unitId,
        String code,
        String sourceType,
        UUID sourceId,
        JsonNode safeDetail
    ) {
        jdbc.update("""
            insert into project_work_item_migration_failures(
              id,workspace_id,batch_id,unit_id,failure_code,source_type,
              source_id,safe_detail,recorded_at
            ) values (?,?,?,?,?,?,?,?::jsonb,now())
            """, UUID.randomUUID(), workspaceId, batchId, unitId, code, sourceType,
            sourceId, text(safeDetail));
    }

    @Override
    public Optional<LegacyMapTarget> findActiveMap(
        UUID workspaceId,
        String sourceType,
        UUID sourceId
    ) {
        return jdbc.query("""
            select id,work_item_id,status,batch_id,unit_id
              from project_legacy_work_item_maps
             where workspace_id=? and source_type=? and source_id=? and status='active'
            """, (rs, rowNum) -> new LegacyMapTarget(
                rs.getObject("id", UUID.class),
                rs.getObject("work_item_id", UUID.class),
                rs.getString("status"),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("unit_id", UUID.class)
            ), workspaceId, sourceType, sourceId).stream().findFirst();
    }

    @Override
    public long nextNumber(UUID workspaceId, UUID spaceId, UUID typeId) {
        Long value = jdbc.queryForObject("""
            insert into project_work_item_counters(
              workspace_id,space_id,type_definition_id,next_number
            ) values (?,?,?,2)
            on conflict (workspace_id,space_id,type_definition_id)
            do update set next_number=project_work_item_counters.next_number+1
            returning next_number-1
            """, Long.class, workspaceId, spaceId, typeId);
        if (value == null) {
            throw new IllegalStateException("MIGRATION_NUMBER_ALLOCATION_FAILED");
        }
        return value;
    }

    @Override
    public void insertMigratedWorkItem(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        TypeBinding binding,
        long itemNumber,
        String displayKey,
        String title,
        JsonNode fieldValues,
        String status,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Instant archivedAt
    ) {
        jdbc.update("""
            insert into project_work_items(
              id,workspace_id,space_id,type_definition_id,type_version_id,config_hash,
              item_number,display_key,title,field_values,status,version,
              created_by,created_at,updated_by,updated_at,archived_at
            ) values (?,?,?,?,?,?,?,?,?,?::jsonb,?,0,?,?,?,?,?)
            """, id, workspaceId, spaceId, binding.typeId(), binding.versionId(),
            binding.configHash(), itemNumber, displayKey, title, text(fieldValues), status,
            createdBy, timestamp(createdAt), updatedBy, timestamp(updatedAt), timestamp(archivedAt));
    }

    @Override
    public void insertMap(
        UUID workspaceId,
        UUID batchId,
        UUID unitId,
        String sourceType,
        UUID sourceId,
        UUID sourceProjectId,
        UUID spaceId,
        UUID workItemId,
        String identityDecision,
        String sourceFingerprint
    ) {
        jdbc.update("""
            insert into project_legacy_work_item_maps(
              id,workspace_id,batch_id,unit_id,source_type,source_id,source_project_id,
              space_id,work_item_id,identity_decision,source_fingerprint,status,mapped_at
            ) values (?,?,?,?,?,?,?,?,?,?,?,'active',now())
            """, UUID.randomUUID(), workspaceId, batchId, unitId, sourceType, sourceId,
            sourceProjectId, spaceId, workItemId, identityDecision, sourceFingerprint);
    }

    @Override
    public void upsertParticipant(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId,
        String role,
        UUID actorId,
        Instant occurredAt
    ) {
        jdbc.update("""
            insert into project_work_item_participants(
              id,workspace_id,space_id,work_item_id,user_id,participant_role,
              created_by,created_at,updated_by,updated_at
            ) values (?,?,?,?,?,?,?,?,?,?)
            on conflict (workspace_id,space_id,work_item_id,user_id)
            do update set participant_role=excluded.participant_role,
                          updated_by=excluded.updated_by,updated_at=excluded.updated_at
            """, id, workspaceId, spaceId, workItemId, userId, role,
            actorId, timestamp(occurredAt), actorId, timestamp(occurredAt));
    }

    @Override
    public void insertComment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID authorId,
        String content,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        jdbc.update("""
            insert into project_work_item_comments(
              id,workspace_id,space_id,work_item_id,author_id,content,
              version,created_at,updated_at,deleted_at
            ) values (?,?,?,?,?,?,0,?,?,?)
            """, id, workspaceId, spaceId, workItemId, authorId, content,
            timestamp(createdAt), timestamp(updatedAt), timestamp(deletedAt));
    }

    @Override
    public void insertAttachment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID fileId,
        UUID createdBy,
        Instant createdAt
    ) {
        jdbc.update("""
            insert into project_work_item_attachments(
              id,workspace_id,space_id,work_item_id,file_id,created_by,created_at
            ) values (?,?,?,?,?,?,?)
            """, id, workspaceId, spaceId, workItemId, fileId, createdBy, timestamp(createdAt));
    }

    @Override
    public void insertActivity(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String activityType,
        UUID actorId,
        JsonNode payload,
        Instant occurredAt
    ) {
        Long sequence = jdbc.queryForObject("""
            select coalesce(max(sequence_number),0)+1
              from project_work_item_activities
             where workspace_id=? and space_id=? and work_item_id=?
            """, Long.class, workspaceId, spaceId, workItemId);
        jdbc.update("""
            insert into project_work_item_activities(
              id,workspace_id,space_id,work_item_id,sequence_number,
              activity_type,actor_id,public_payload,occurred_at
            ) values (?,?,?,?,?,?,?,?::jsonb,?)
            """, id, workspaceId, spaceId, workItemId, sequence, activityType,
            actorId, text(payload), timestamp(occurredAt));
    }

    @Override
    public void insertProvenance(
        UUID workspaceId,
        UUID batchId,
        UUID unitId,
        String sourceType,
        UUID sourceId,
        UUID sourceProjectId,
        String checksum,
        String targetType,
        UUID targetId,
        JsonNode safePayload
    ) {
        jdbc.update("""
            insert into project_work_item_migration_provenance(
              id,workspace_id,batch_id,unit_id,source_type,source_id,source_project_id,
              source_checksum,target_type,target_id,safe_payload,recorded_at
            ) values (?,?,?,?,?,?,?,?,?,?,?::jsonb,now())
            """, UUID.randomUUID(), workspaceId, batchId, unitId, sourceType, sourceId,
            sourceProjectId, checksum, targetType, targetId, text(safePayload));
    }

    @Override
    public VerificationObservation observeBatch(UUID workspaceId, UUID batchId) {
        return jdbc.queryForObject("""
            with expected as (
              select coalesce(sum(
                1 + jsonb_array_length(m.payload->'source'->'members')
                  + jsonb_array_length(m.payload->'source'->'issues')
                  + coalesce((
                      select sum(
                        jsonb_array_length(i->'comments')
                        + jsonb_array_length(i->'attachments')
                        + jsonb_array_length(i->'activities')
                      ) from jsonb_array_elements(m.payload->'source'->'issues') i
                    ),0)
              ),0) source_count
              from project_work_item_migration_manifests m
              where m.workspace_id=? and m.batch_id=? and m.manifest_version=1
            ), observed as (
              select
                count(*) filter (where lm.status='active') active_maps,
                count(wi.id) filter (where lm.status='active') target_items
              from project_legacy_work_item_maps lm
              left join project_work_items wi
                on wi.workspace_id=lm.workspace_id and wi.space_id=lm.space_id
               and wi.id=lm.work_item_id
              where lm.workspace_id=? and lm.batch_id=?
            ), provenance as (
              select count(*) rows,
                count(*) filter (where target_type='comment') comments,
                count(*) filter (where target_type='attachment') attachments,
                md5(coalesce(string_agg(
                  source_type||':'||source_id||':'||source_checksum,',' order by source_type,source_id
                ),'')) fingerprint
              from project_work_item_migration_provenance
              where workspace_id=? and batch_id=?
            ), unit_mismatch as (
              select count(*) mismatches from project_work_item_migration_units
               where workspace_id=? and batch_id=? and status<>'completed'
            )
            select p.fingerprint,e.source_count,o.active_maps,o.target_items,p.rows,
                   p.comments,p.attachments,
                   u.mismatches
                     + case when o.active_maps<>o.target_items then 1 else 0 end
                     + case when p.rows<>e.source_count then 1 else 0 end mismatches
              from expected e,observed o,provenance p,unit_mismatch u
            """, this::mapObservation,
            workspaceId, batchId, workspaceId, batchId, workspaceId, batchId,
            workspaceId, batchId);
    }

    @Override
    public VerificationObservation observeWorkspace(UUID workspaceId) {
        return jdbc.queryForObject("""
            with expected as (
              select
                (select count(*) from projects where workspace_id=?)
                +(select count(*) from issues where workspace_id=?)
                +(select count(*) from project_members where workspace_id=?)
                +(select count(*) from issue_comments where workspace_id=?)
                +(select count(*) from issue_attachments where workspace_id=?)
                +(select count(*) from issue_activity_logs where workspace_id=?) source_count
            ), observed as (
              select count(*) active_maps,count(wi.id) target_items
                from project_legacy_work_item_maps lm
                left join project_work_items wi
                  on wi.workspace_id=lm.workspace_id and wi.space_id=lm.space_id
                 and wi.id=lm.work_item_id
               where lm.workspace_id=? and lm.status='active'
            ), provenance as (
              select count(*) rows,
                count(*) filter (where target_type='comment') comments,
                count(*) filter (where target_type='attachment') attachments,
                md5(coalesce(string_agg(
                  source_type||':'||source_id||':'||source_checksum,',' order by source_type,source_id
                ),'')) fingerprint
              from project_work_item_migration_provenance p
              where p.workspace_id=?
                and exists (
                  select 1 from project_legacy_work_item_maps lm
                   where lm.workspace_id=p.workspace_id and lm.batch_id=p.batch_id
                     and lm.status='active'
                )
            )
            select p.fingerprint,e.source_count,o.active_maps,o.target_items,p.rows,
                   p.comments,p.attachments,
                   case when o.active_maps<>o.target_items or p.rows<>e.source_count
                        then 1 else 0 end mismatches
              from expected e,observed o,provenance p
            """, this::mapObservation,
            workspaceId, workspaceId, workspaceId, workspaceId, workspaceId, workspaceId,
            workspaceId, workspaceId);
    }

    @Override
    public MigrationVerification appendVerification(
        UUID workspaceId,
        UUID batchId,
        String scope,
        String status,
        String manifestFingerprint,
        VerificationObservation observation,
        UUID actorId
    ) {
        UUID id = UUID.randomUUID();
        JsonNode summary = objectMapper.valueToTree(Map.of(
            "expectedSources", observation.expectedSources(),
            "activeMaps", observation.activeMaps(),
            "targetItems", observation.targetItems(),
            "provenanceRows", observation.provenanceRows(),
            "comments", observation.comments(),
            "attachments", observation.attachments(),
            "mismatches", observation.mismatches()
        ));
        jdbc.update("""
            insert into project_work_item_migration_verifications(
              id,workspace_id,batch_id,verification_scope,status,manifest_fingerprint,
              observed_fingerprint,safe_summary,verified_by,verified_at
            ) values (?,?,?,?,?,?,?,?::jsonb,?,now())
            """, id, workspaceId, batchId, scope, status, manifestFingerprint,
            observation.fingerprint(), text(summary), actorId);
        return new MigrationVerification(
            id, workspaceId, batchId, scope, "matched".equals(status), manifestFingerprint,
            observation.fingerprint(), objectMapper.convertValue(summary, new TypeReference<>() {}),
            Instant.now()
        );
    }

    @Override
    public boolean hasCanonicalWrites(UUID workspaceId, UUID batchId) {
        Integer count = jdbc.queryForObject("""
            select count(*) from project_work_item_cutovers c
             where c.workspace_id=? and c.read_stage in ('canonical_write','complete')
               and exists (
                 select 1 from project_legacy_work_item_maps m
                  where m.workspace_id=c.workspace_id and m.batch_id=? and m.status='active'
                    and (c.space_id is null or c.space_id=m.space_id)
               )
            """, Integer.class, workspaceId, batchId);
        return count != null && count > 0;
    }

    @Override
    public List<UUID> listActiveTargets(UUID workspaceId, UUID batchId) {
        return jdbc.query("""
            select work_item_id from project_legacy_work_item_maps
             where workspace_id=? and batch_id=? and status='active'
            """, (rs, rowNum) -> rs.getObject(1, UUID.class), workspaceId, batchId);
    }

    @Override
    @Transactional
    public int rollbackBatch(UUID workspaceId, UUID batchId, UUID actorId) {
        jdbc.execute("set local colla.project_space_cleanup = 'on'");
        int maps = jdbc.update("""
            update project_legacy_work_item_maps
               set status='rolled_back',rolled_back_at=now()
             where workspace_id=? and batch_id=? and status='active'
            """, workspaceId, batchId);
        jdbc.update("""
            delete from project_work_items wi
             where wi.workspace_id=? and exists (
               select 1 from project_legacy_work_item_maps lm
                where lm.workspace_id=wi.workspace_id and lm.work_item_id=wi.id
                  and lm.batch_id=? and lm.status='rolled_back'
             )
            """, workspaceId, batchId);
        jdbc.update("""
            update project_work_item_migration_units
               set status='rolled_back',finished_at=now()
             where workspace_id=? and batch_id=? and status in ('completed','failed','paused','planned')
            """, workspaceId, batchId);
        jdbc.update("""
            update project_work_item_migration_batches
               set status='rolled_back',finished_at=now(),version=version+1,
                   lease_owner=null,lease_token=null,heartbeat_at=null
             where workspace_id=? and id=?
            """, workspaceId, batchId);
        return maps;
    }

    @Override
    public void enableKillSwitch(UUID workspaceId, UUID actorId) {
        jdbc.update("""
            update project_work_item_cutovers
               set kill_switch_enabled=true,changed_by=?,changed_at=now(),version=version+1
             where workspace_id=?
            """, actorId, workspaceId);
    }

    private MigrationBatch withChildren(MigrationBatch batch, UUID workspaceId) {
        return new MigrationBatch(
            batch.id(), batch.workspaceId(), batch.status(), batch.sourceWatermark(),
            batch.sourceFingerprint(), batch.manifestFingerprint(), batch.plan(),
            batch.planFingerprint(), batch.version(), batch.leaseOwner(), batch.leaseToken(),
            batch.fenceVersion(), batch.heartbeatAt(), batch.throttleMillis(),
            batch.pausedReason(), batch.initiatedBy(), batch.initiatedAt(), batch.finishedAt(),
            listUnits(workspaceId, batch.id()), listFailures(workspaceId, batch.id())
        );
    }

    private MigrationBatch mapBatch(ResultSet rs, int rowNum) throws SQLException {
        return new MigrationBatch(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getString("status"),
            instant(rs, "source_watermark"),
            rs.getString("source_fingerprint"),
            rs.getString("manifest_fingerprint"),
            json(rs.getString("plan_payload")),
            rs.getString("plan_fingerprint"),
            rs.getLong("version"),
            rs.getString("lease_owner"),
            rs.getObject("lease_token", UUID.class),
            rs.getLong("fence_version"),
            instant(rs, "heartbeat_at"),
            rs.getInt("throttle_millis"),
            rs.getString("paused_reason"),
            rs.getObject("initiated_by", UUID.class),
            instant(rs, "initiated_at"),
            instant(rs, "finished_at"),
            List.of(),
            List.of()
        );
    }

    private MigrationUnit mapUnit(ResultSet rs, int rowNum) throws SQLException {
        return new MigrationUnit(
            rs.getObject("id", UUID.class),
            rs.getObject("batch_id", UUID.class),
            rs.getObject("legacy_project_id", UUID.class),
            rs.getObject("space_id", UUID.class),
            rs.getString("status"),
            rs.getInt("attempt"),
            rs.getString("source_fingerprint"),
            rs.getLong("fence_version"),
            rs.getString("last_error_code"),
            rs.getInt("migrated_objects"),
            instant(rs, "started_at"),
            instant(rs, "finished_at")
        );
    }

    private MigrationFailure mapFailure(ResultSet rs, int rowNum) throws SQLException {
        JsonNode detail = json(rs.getString("safe_detail"));
        return new MigrationFailure(
            rs.getObject("id", UUID.class),
            rs.getObject("batch_id", UUID.class),
            rs.getObject("unit_id", UUID.class),
            rs.getString("failure_code"),
            rs.getString("source_type"),
            rs.getObject("source_id", UUID.class),
            objectMapper.convertValue(detail, new TypeReference<>() {}),
            instant(rs, "recorded_at")
        );
    }

    private VerificationObservation mapObservation(ResultSet rs, int rowNum) throws SQLException {
        return new VerificationObservation(
            rs.getString("fingerprint"),
            rs.getLong("source_count"),
            rs.getLong("active_maps"),
            rs.getLong("target_items"),
            rs.getLong("rows"),
            rs.getLong("comments"),
            rs.getLong("attachments"),
            rs.getLong("mismatches")
        );
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid migration JSON", exception);
        }
    }

    private String text(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode migration JSON", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
