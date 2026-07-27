package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivity;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemComment;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemParticipant;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.FieldProjection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemRepository implements WorkItemRepository {
    private static final String ITEM_SELECT = """
        select wi.id, wi.workspace_id, wi.space_id, wi.type_definition_id, wi.type_version_id,
               t.type_key, t.name type_name, wi.config_hash, wi.item_number, wi.display_key,
               wi.title, wi.field_values, wi.status, wi.version, wi.created_by, wi.created_at,
               wi.updated_by, wi.updated_at, wi.archived_at
          from project_work_items wi
          join project_work_item_types t
            on t.workspace_id = wi.workspace_id
           and t.space_id = wi.space_id
           and t.id = wi.type_definition_id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LockedType> lockCurrentType(UUID workspaceId, UUID spaceId, UUID typeId) {
        return findCurrentType(workspaceId, spaceId, typeId, true);
    }

    @Override
    public Optional<LockedType> findCurrentType(UUID workspaceId, UUID spaceId, UUID typeId) {
        return findCurrentType(workspaceId, spaceId, typeId, false);
    }

    private Optional<LockedType> findCurrentType(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        boolean lock
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select t.id, t.current_version_id, t.type_key, t.name, t.status type_status,
                           s.status space_status, v.config_hash
                      from project_work_item_types t
                      join project_spaces s
                        on s.workspace_id = t.workspace_id and s.id = t.space_id
                      join project_work_item_type_versions v
                        on v.workspace_id = t.workspace_id
                       and v.space_id = t.space_id
                       and v.type_definition_id = t.id
                       and v.id = t.current_version_id
                     where t.workspace_id = ? and t.space_id = ? and t.id = ?
                       and v.status in ('published', 'superseded')
                    """ + (lock ? " for update of t" : ""),
                (resultSet, rowNumber) -> new LockedType(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("current_version_id", UUID.class),
                    resultSet.getString("type_key"),
                    resultSet.getString("name"),
                    resultSet.getString("type_status"),
                    resultSet.getString("space_status"),
                    resultSet.getString("config_hash")
                ),
                workspaceId,
                spaceId,
                typeId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public long nextNumber(UUID workspaceId, UUID spaceId, UUID typeId) {
        Long value = jdbcTemplate.queryForObject(
            """
                insert into project_work_item_counters (workspace_id, space_id, type_definition_id, next_number)
                values (?, ?, ?, 2)
                on conflict (workspace_id, space_id, type_definition_id)
                do update set next_number = project_work_item_counters.next_number + 1
                returning next_number - 1
                """,
            Long.class,
            workspaceId,
            spaceId,
            typeId
        );
        if (value == null) {
            throw new IllegalStateException("Work item number was not allocated");
        }
        return value;
    }

    @Override
    public void insert(NewWorkItem item) {
        jdbcTemplate.update(
            """
                insert into project_work_items (
                    id, workspace_id, space_id, type_definition_id, type_version_id,
                    config_hash, item_number, display_key, title, field_values, status,
                    version, created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'active', 0, ?, now(), ?, now())
                """,
            item.id(),
            item.workspaceId(),
            item.spaceId(),
            item.typeDefinitionId(),
            item.typeVersionId(),
            item.configHash(),
            item.itemNumber(),
            item.displayKey(),
            item.title(),
            json(item.fieldValues()),
            item.actorId(),
            item.actorId()
        );
    }

    @Override
    public Optional<WorkItem> find(UUID workspaceId, UUID spaceId, UUID workItemId) {
        if (spaceId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                ITEM_SELECT + " where wi.workspace_id = ? and wi.space_id = ? and wi.id = ?",
                this::mapItem,
                workspaceId,
                spaceId,
                workItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkItem> lock(UUID workspaceId, UUID spaceId, UUID workItemId) {
        if (spaceId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                ITEM_SELECT + """
                     where wi.workspace_id = ? and wi.space_id = ? and wi.id = ?
                     for update of wi
                    """,
                this::mapItem,
                workspaceId,
                spaceId,
                workItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> findSpaceId(UUID workspaceId, UUID workItemId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "select space_id from project_work_items where workspace_id = ? and id = ?",
                UUID.class,
                workspaceId,
                workItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<WorkItem> list(UUID workspaceId, UUID spaceId, UUID typeId, UUID cursor, int limit) {
        if (cursor == null) {
            if (typeId == null) {
                return jdbcTemplate.query(
                    ITEM_SELECT + """
                         where wi.workspace_id = ? and wi.space_id = ?
                         order by wi.updated_at desc, wi.id desc
                         limit ?
                        """,
                    this::mapItem,
                    workspaceId,
                    spaceId,
                    limit
                );
            }
            return jdbcTemplate.query(
                ITEM_SELECT + """
                     where wi.workspace_id = ? and wi.space_id = ?
                       and wi.type_definition_id = ?
                     order by wi.updated_at desc, wi.id desc
                     limit ?
                    """,
                this::mapItem,
                workspaceId,
                spaceId,
                typeId,
                limit
            );
        }
        if (typeId == null) {
            return jdbcTemplate.query(
                ITEM_SELECT + """
                     where wi.workspace_id = ? and wi.space_id = ?
                       and (wi.updated_at, wi.id) < (
                           select anchor.updated_at, anchor.id
                             from project_work_items anchor
                            where anchor.workspace_id = ? and anchor.space_id = ? and anchor.id = ?
                       )
                     order by wi.updated_at desc, wi.id desc
                     limit ?
                    """,
                this::mapItem,
                workspaceId,
                spaceId,
                workspaceId,
                spaceId,
                cursor,
                limit
            );
        }
        return jdbcTemplate.query(
            ITEM_SELECT + """
                 where wi.workspace_id = ? and wi.space_id = ?
                   and wi.type_definition_id = ?
                   and (wi.updated_at, wi.id) < (
                       select anchor.updated_at, anchor.id
                         from project_work_items anchor
                        where anchor.workspace_id = ? and anchor.space_id = ? and anchor.id = ?
                   )
                 order by wi.updated_at desc, wi.id desc
                 limit ?
                """,
            this::mapItem,
            workspaceId,
            spaceId,
            typeId,
            workspaceId,
            spaceId,
            cursor,
            limit
        );
    }

    @Override
    public List<WorkItem> listForSearchRebuild(UUID workspaceId, UUID afterId, int limit) {
        return jdbcTemplate.query(
            ITEM_SELECT + """
                 join project_spaces ps
                   on ps.workspace_id = wi.workspace_id and ps.id = wi.space_id
                 where wi.workspace_id = ?
                   and wi.status <> 'archived'
                   and ps.status = 'active'
                   and (?::uuid is null or wi.id > ?)
                 order by wi.id
                 limit ?
                """,
            this::mapItem,
            workspaceId,
            afterId,
            afterId,
            limit
        );
    }

    @Override
    public List<WorkItem> searchRelationTargets(
        UUID workspaceId,
        UUID spaceId,
        List<String> typeKeys,
        String query,
        UUID cursor,
        int limit
    ) {
        if (typeKeys == null || typeKeys.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(
            ",", java.util.Collections.nCopies(typeKeys.size(), "?")
        );
        StringBuilder sql = new StringBuilder(ITEM_SELECT)
            .append(" where wi.workspace_id=? and wi.space_id=? and wi.status='active'")
            .append(" and t.status='active' and t.type_key in (")
            .append(placeholders)
            .append(")");
        List<Object> parameters = new ArrayList<>();
        parameters.add(workspaceId);
        parameters.add(spaceId);
        parameters.addAll(typeKeys);
        String normalized = query == null ? "" : query.trim();
        if (!normalized.isBlank()) {
            sql.append(" and (wi.display_key ilike ? escape '\\' or wi.title ilike ? escape '\\')");
            String pattern = "%" + like(normalized) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (cursor != null) {
            sql.append(" and wi.id>?");
            parameters.add(cursor);
        }
        sql.append(" order by wi.id limit ?");
        parameters.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapItem, parameters.toArray());
    }

    @Override
    public List<WorkItem> findAll(
        UUID workspaceId,
        UUID spaceId,
        List<UUID> workItemIds
    ) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(
            ",", java.util.Collections.nCopies(workItemIds.size(), "?")
        );
        List<Object> parameters = new ArrayList<>();
        parameters.add(workspaceId);
        parameters.add(spaceId);
        parameters.addAll(workItemIds);
        return jdbcTemplate.query(
            ITEM_SELECT
                + " where wi.workspace_id=? and wi.space_id=? and wi.id in ("
                + placeholders + ") order by wi.id",
            this::mapItem,
            parameters.toArray()
        );
    }

    @Override
    public int update(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String title,
        JsonNode fieldValues,
        UUID actorId,
        long expectedVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_items
                   set title = ?, field_values = ?::jsonb, updated_by = ?, updated_at = now(), version = version + 1
                 where workspace_id = ? and space_id = ? and id = ?
                   and status = 'active' and version = ?
                """,
            title,
            json(fieldValues),
            actorId,
            workspaceId,
            spaceId,
            workItemId,
            expectedVersion
        );
    }

    @Override
    public int transition(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String expectedStatus,
        String targetStatus,
        UUID actorId,
        long expectedVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_items
                   set status = ?, archived_at = case when ? = 'archived' then now() else null end,
                       updated_by = ?, updated_at = now(), version = version + 1
                 where workspace_id = ? and space_id = ? and id = ?
                   and status = ? and version = ?
                """,
            targetStatus,
            targetStatus,
            actorId,
            workspaceId,
            spaceId,
            workItemId,
            expectedStatus,
            expectedVersion
        );
    }

    @Override
    public int workflowUpdate(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        JsonNode fieldValues,
        UUID actorId,
        long expectedVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_items
                   set field_values = ?::jsonb, updated_by = ?, updated_at = now(), version = version + 1
                 where workspace_id = ? and space_id = ? and id = ?
                   and status = 'active' and version = ?
                """,
            json(fieldValues),
            actorId,
            workspaceId,
            spaceId,
            workItemId,
            expectedVersion
        );
    }

    @Override
    public int workflowBindingUpdate(
        UUID workspaceId, UUID spaceId, UUID workItemId, UUID expectedTypeVersionId,
        String expectedConfigHash, UUID targetTypeVersionId, String targetConfigHash,
        JsonNode fieldValues, UUID actorId, long expectedVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_items
                   set type_version_id=?, config_hash=?, field_values=?::jsonb,
                       updated_by=?, updated_at=now(), version=version+1
                 where workspace_id=? and space_id=? and id=?
                   and type_version_id=? and config_hash=? and version=?
                """,
            targetTypeVersionId, targetConfigHash, json(fieldValues), actorId,
            workspaceId, spaceId, workItemId, expectedTypeVersionId,
            expectedConfigHash, expectedVersion
        );
    }

    @Override
    public void replaceFieldProjections(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        List<FieldProjection> projections
    ) {
        jdbcTemplate.update(
            """
                delete from project_work_item_field_projections
                 where workspace_id = ? and space_id = ? and work_item_id = ?
                """,
            workspaceId,
            spaceId,
            workItemId
        );
        insertFieldProjections(workspaceId, spaceId, workItemId, projections);
    }

    @Override
    public int rebuildFieldProjections(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        List<FieldProjection> projections
    ) {
        replaceFieldProjections(workspaceId, spaceId, workItemId, projections);
        return projections.size();
    }

    @Override
    public List<WorkItemParticipant> listParticipants(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId
    ) {
        return jdbcTemplate.query(
            """
                select p.id, p.user_id, null::varchar display_name, p.participant_role,
                       p.created_by, p.created_at, p.updated_by, p.updated_at
                  from project_work_item_participants p
                 where p.workspace_id = ? and p.space_id = ? and p.work_item_id = ?
                 order by case p.participant_role
                              when 'owner' then 0 when 'assignee' then 1
                              when 'collaborator' then 2 else 3
                          end,
                          p.user_id
                """,
            this::mapParticipant,
            workspaceId,
            spaceId,
            workItemId
        );
    }

    @Override
    public Optional<WorkItemParticipant> findParticipant(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select p.id, p.user_id, null::varchar display_name, p.participant_role,
                           p.created_by, p.created_at, p.updated_by, p.updated_at
                      from project_work_item_participants p
                     where p.workspace_id = ? and p.space_id = ?
                       and p.work_item_id = ? and p.user_id = ?
                    """,
                this::mapParticipant,
                workspaceId,
                spaceId,
                workItemId,
                userId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void upsertParticipant(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId,
        String role,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_participants (
                    id, workspace_id, space_id, work_item_id, user_id, participant_role,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, now(), ?, now())
                on conflict (workspace_id, space_id, work_item_id, user_id)
                do update set participant_role = excluded.participant_role,
                              updated_by = excluded.updated_by,
                              updated_at = now()
                """,
            id,
            workspaceId,
            spaceId,
            workItemId,
            userId,
            role,
            actorId,
            actorId
        );
    }

    @Override
    public int removeParticipant(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId
    ) {
        return jdbcTemplate.update(
            """
                delete from project_work_item_participants
                 where workspace_id = ? and space_id = ? and work_item_id = ? and user_id = ?
                """,
            workspaceId,
            spaceId,
            workItemId,
            userId
        );
    }

    @Override
    public long countResponsibleParticipants(UUID workspaceId, UUID spaceId, UUID workItemId) {
        Long value = jdbcTemplate.queryForObject(
            """
                select count(*) from project_work_item_participants
                 where workspace_id = ? and space_id = ? and work_item_id = ?
                   and participant_role in ('owner', 'assignee')
                """,
            Long.class,
            workspaceId,
            spaceId,
            workItemId
        );
        return value == null ? 0L : value;
    }

    @Override
    public int touch(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID actorId,
        long expectedVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_items
                   set updated_by = ?, updated_at = now(), version = version + 1
                 where workspace_id = ? and space_id = ? and id = ?
                   and status = 'active' and version = ?
                """,
            actorId,
            workspaceId,
            spaceId,
            workItemId,
            expectedVersion
        );
    }

    @Override
    public void appendActivity(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String activityType,
        UUID actorId,
        JsonNode publicPayload
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_activities (
                    id, workspace_id, space_id, work_item_id, sequence_number,
                    activity_type, actor_id, public_payload, occurred_at
                )
                select ?, ?, ?, ?, coalesce(max(sequence_number), 0) + 1, ?, ?, ?::jsonb, now()
                  from project_work_item_activities
                 where workspace_id = ? and space_id = ? and work_item_id = ?
                """,
            id,
            workspaceId,
            spaceId,
            workItemId,
            activityType,
            actorId,
            json(publicPayload),
            workspaceId,
            spaceId,
            workItemId
        );
    }

    @Override
    public List<WorkItemActivity> listActivities(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        Long beforeSequence,
        int limit
    ) {
        if (beforeSequence == null) {
            return jdbcTemplate.query(
                """
                    select a.id, a.sequence_number, a.activity_type, a.actor_id,
                           null::varchar actor_display_name, a.public_payload, a.occurred_at
                      from project_work_item_activities a
                     where a.workspace_id = ? and a.space_id = ? and a.work_item_id = ?
                     order by a.sequence_number desc
                     limit ?
                    """,
                this::mapActivity,
                workspaceId,
                spaceId,
                workItemId,
                limit
            );
        }
        return jdbcTemplate.query(
            """
                select a.id, a.sequence_number, a.activity_type, a.actor_id,
                       null::varchar actor_display_name, a.public_payload, a.occurred_at
                  from project_work_item_activities a
                 where a.workspace_id = ? and a.space_id = ? and a.work_item_id = ?
                   and a.sequence_number < ?
                 order by a.sequence_number desc
                 limit ?
                """,
            this::mapActivity,
            workspaceId,
            spaceId,
            workItemId,
            beforeSequence,
            limit
        );
    }

    @Override
    public List<WorkItemComment> listComments(UUID workspaceId, UUID spaceId, UUID workItemId) {
        return jdbcTemplate.query(
            """
                select c.id, c.author_id, null::varchar author_display_name, c.content,
                       c.version, c.created_at, c.updated_at
                  from project_work_item_comments c
                 where c.workspace_id = ? and c.space_id = ? and c.work_item_id = ?
                   and c.deleted_at is null
                 order by c.created_at, c.id
                """,
            this::mapComment,
            workspaceId,
            spaceId,
            workItemId
        );
    }

    @Override
    public void insertComment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID authorId,
        String content
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_comments (
                    id, workspace_id, space_id, work_item_id, author_id, content,
                    version, created_at
                ) values (?, ?, ?, ?, ?, ?, 0, now())
                """,
            id,
            workspaceId,
            spaceId,
            workItemId,
            authorId,
            content
        );
    }

    @Override
    public List<AttachmentLink> listAttachments(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId
    ) {
        return jdbcTemplate.query(
            """
                select a.id, a.file_id, a.created_by, a.created_at
                  from project_work_item_attachments a
                 where a.workspace_id = ? and a.space_id = ? and a.work_item_id = ?
                   and a.deleted_at is null
                 order by a.created_at, a.id
                """,
            this::mapAttachment,
            workspaceId,
            spaceId,
            workItemId
        );
    }

    @Override
    public int insertAttachment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID fileId,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_attachments (
                    id, workspace_id, space_id, work_item_id, file_id, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, space_id, work_item_id, file_id) do nothing
                """,
            id,
            workspaceId,
            spaceId,
            workItemId,
            fileId,
            actorId
        );
    }

    @Override
    public List<WorkItem> queryByProjection(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String fieldKey,
        String operator,
        FieldProjection queryValue,
        String sortDirection,
        int limit
    ) {
        if (!"eq".equals(operator)) {
            throw new IllegalArgumentException("Only the controlled eq query template is implemented");
        }
        String column;
        Object value;
        if (queryValue.textValue() != null) {
            column = "text_value";
            value = queryValue.textValue();
        } else if (queryValue.numberValue() != null) {
            column = "number_value";
            value = queryValue.numberValue();
        } else if (queryValue.booleanValue() != null) {
            column = "boolean_value";
            value = queryValue.booleanValue();
        } else if (queryValue.dateValue() != null) {
            column = "date_value";
            value = queryValue.dateValue();
        } else if (queryValue.timestampValue() != null) {
            column = "timestamp_value";
            value = Timestamp.from(queryValue.timestampValue());
        } else {
            column = "canonical_hash";
            value = queryValue.canonicalHash();
        }
        String direction = "asc".equalsIgnoreCase(sortDirection) ? "asc" : "desc";
        String orderBy = "none".equalsIgnoreCase(sortDirection)
            ? "wi.updated_at desc, wi.id desc"
            : "fp." + column + " " + direction + " nulls last, wi.id " + direction;
        String sql = ITEM_SELECT + """
             join project_work_item_field_projections fp
               on fp.workspace_id = wi.workspace_id
              and fp.space_id = wi.space_id
              and fp.work_item_id = wi.id
             where wi.workspace_id = ? and wi.space_id = ? and wi.type_definition_id = ?
               and fp.field_key = ? and fp.filterable = true and fp.%s = ?
               and fp.config_hash = ?
             order by %s
             limit ?
            """.formatted(column, orderBy);
        return jdbcTemplate.query(
            sql,
            this::mapItem,
            workspaceId,
            spaceId,
            typeId,
            fieldKey,
            value,
            queryValue.configHash(),
            limit
        );
    }

    @Override
    public boolean tryStartCommand(CommandStart command) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_commands (
                    id, workspace_id, space_id, work_item_id, operation, request_id,
                    request_hash, status, response_schema_version, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'pending', 1, ?, now())
                on conflict do nothing
                """,
            command.id(),
            command.workspaceId(),
            command.spaceId(),
            command.workItemId(),
            command.operation(),
            command.requestId(),
            command.requestHash(),
            command.actorId()
        ) == 1;
    }

    @Override
    public Optional<CommandReceipt> findCommand(UUID workspaceId, String operation, String requestId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, space_id, work_item_id, operation, request_id,
                           request_hash, status, response_payload, created_by
                      from project_work_item_commands
                     where workspace_id = ? and operation = ? and request_id = ?
                    """,
                this::mapReceipt,
                workspaceId,
                operation,
                requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void completeCommand(UUID commandId, UUID workItemId, JsonNode response) {
        if (jdbcTemplate.update(
            """
                update project_work_item_commands
                   set work_item_id = coalesce(work_item_id, ?), status = 'completed',
                       response_payload = ?::jsonb, completed_at = now()
                 where id = ? and status = 'pending' and response_payload is null
                """,
            workItemId,
            json(response),
            commandId
        ) != 1) {
            throw new IllegalStateException("Work item command could not be completed");
        }
    }

    private WorkItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            Timestamp archivedAt = resultSet.getTimestamp("archived_at");
            return new WorkItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("workspace_id", UUID.class),
                resultSet.getObject("space_id", UUID.class),
                resultSet.getObject("type_definition_id", UUID.class),
                resultSet.getObject("type_version_id", UUID.class),
                resultSet.getString("type_key"),
                resultSet.getString("type_name"),
                resultSet.getString("config_hash"),
                resultSet.getLong("item_number"),
                resultSet.getString("display_key"),
                resultSet.getString("title"),
                objectMapper.readTree(resultSet.getString("field_values")),
                resultSet.getString("status"),
                resultSet.getLong("version"),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getObject("updated_by", UUID.class),
                resultSet.getTimestamp("updated_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant()
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid work item JSON stored", exception);
        }
    }

    private WorkItemParticipant mapParticipant(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkItemParticipant(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("display_name"),
            resultSet.getString("participant_role"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("updated_by", UUID.class),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private WorkItemActivity mapActivity(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkItemActivity(
            resultSet.getObject("id", UUID.class),
            resultSet.getLong("sequence_number"),
            resultSet.getString("activity_type"),
            resultSet.getObject("actor_id", UUID.class),
            resultSet.getString("actor_display_name"),
            parse(resultSet.getString("public_payload")),
            resultSet.getTimestamp("occurred_at").toInstant()
        );
    }

    private WorkItemComment mapComment(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new WorkItemComment(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("author_id", UUID.class),
            resultSet.getString("author_display_name"),
            resultSet.getString("content"),
            resultSet.getLong("version"),
            resultSet.getTimestamp("created_at").toInstant(),
            updatedAt == null ? null : updatedAt.toInstant()
        );
    }

    private AttachmentLink mapAttachment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AttachmentLink(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("file_id", UUID.class),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private void insertFieldProjections(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        List<FieldProjection> projections
    ) {
        for (FieldProjection projection : projections) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_field_projections (
                        workspace_id, space_id, work_item_id, field_key, field_type,
                        config_hash, canonical_hash, canonical_value, text_value, number_value,
                        boolean_value, date_value, timestamp_value, reference_values,
                        filterable, sortable, index_capability, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, now())
                    """,
                workspaceId,
                spaceId,
                workItemId,
                projection.fieldKey(),
                projection.fieldType(),
                projection.configHash(),
                projection.canonicalHash(),
                json(projection.canonicalValue()),
                projection.textValue(),
                projection.numberValue(),
                projection.booleanValue(),
                projection.dateValue(),
                projection.timestampValue() == null ? null : Timestamp.from(projection.timestampValue()),
                projection.referenceValues() == null ? null : json(projection.referenceValues()),
                projection.filterable(),
                projection.sortable(),
                projection.indexCapability()
            );
        }
    }

    private CommandReceipt mapReceipt(ResultSet resultSet, int rowNumber) throws SQLException {
        String response = resultSet.getString("response_payload");
        return new CommandReceipt(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("work_item_id", UUID.class),
            resultSet.getString("operation"),
            resultSet.getString("request_id"),
            resultSet.getString("request_hash"),
            resultSet.getString("status"),
            response == null ? null : parse(response),
            resultSet.getObject("created_by", UUID.class)
        );
    }

    private JsonNode parse(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid command response JSON stored", exception);
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid work item JSON", exception);
        }
    }

    private String like(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
