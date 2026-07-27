package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemRelationModels.Direction;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationEndpoint;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationProjection;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.WorkItemRelation;
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
public class JdbcWorkItemRelationRepository implements WorkItemRelationRepository {
    private static final String RELATION_COLUMNS = """
        r.id, r.workspace_id, r.space_id, r.relation_key, r.relation_kind, r.direction,
        r.definition_type_id, r.definition_version_id, r.definition_config_hash,
        r.source_work_item_id, r.target_work_item_id, r.status, r.version,
        r.created_by, r.created_at, r.updated_by, r.updated_at, r.withdrawn_by, r.withdrawn_at
        """;
    private static final String PROJECTION_SELECT = """
        select %s,
               source.type_definition_id source_type_definition_id,
               source.type_version_id source_type_version_id,
               source_type.type_key source_type_key,
               source.display_key source_display_key, source.title source_title,
               source.status source_status, source.version source_version,
               target.type_definition_id target_type_definition_id,
               target.type_version_id target_type_version_id,
               target_type.type_key target_type_key,
               target.display_key target_display_key, target.title target_title,
               target.status target_status, target.version target_version,
               definition.value->>'forwardName' relation_forward_name,
               definition.value->>'reverseName' relation_reverse_name
          from project_work_item_relations r
          join project_work_items source
            on source.workspace_id=r.workspace_id and source.space_id=r.space_id
           and source.id=r.source_work_item_id
          join project_work_item_types source_type
            on source_type.workspace_id=source.workspace_id and source_type.space_id=source.space_id
           and source_type.id=source.type_definition_id
          join project_work_items target
            on target.workspace_id=r.workspace_id and target.space_id=r.space_id
           and target.id=r.target_work_item_id
          join project_work_item_types target_type
            on target_type.workspace_id=target.workspace_id and target_type.space_id=target.space_id
           and target_type.id=target.type_definition_id
          join project_work_item_type_versions definition_version
            on definition_version.workspace_id=r.workspace_id
           and definition_version.space_id=r.space_id
           and definition_version.type_definition_id=r.definition_type_id
           and definition_version.id=r.definition_version_id
          join lateral jsonb_array_elements(
               coalesce(definition_version.config->'relationDefinitions', '[]'::jsonb)
          ) definition(value)
            on definition.value->>'relationKey'=r.relation_key
        """.formatted(RELATION_COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemRelationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void acquireGraphLock(UUID workspaceId, UUID spaceId, String relationKey) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            statement -> statement.setString(
                1, workspaceId + ":" + spaceId + ":" + relationKey
            ),
            resultSet -> null
        );
    }

    @Override
    public void insert(NewRelation relation) {
        jdbcTemplate.update(
            """
                insert into project_work_item_relations (
                    id, workspace_id, space_id, relation_key, relation_kind, direction,
                    definition_type_id, definition_version_id, definition_config_hash,
                    source_work_item_id, target_work_item_id, status, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', 0, ?, now(), ?, now())
                """,
            relation.id(),
            relation.workspaceId(),
            relation.spaceId(),
            relation.relationKey(),
            relation.kind().name(),
            relation.direction().name(),
            relation.definitionTypeId(),
            relation.definitionVersionId(),
            relation.definitionConfigHash(),
            relation.sourceWorkItemId(),
            relation.targetWorkItemId(),
            relation.actorId(),
            relation.actorId()
        );
    }

    @Override
    public Optional<WorkItemRelation> find(
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        boolean lock
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "select " + RELATION_COLUMNS
                    + " from project_work_item_relations r"
                    + " where r.workspace_id=? and r.space_id=? and r.id=?"
                    + (lock ? " for update of r" : ""),
                this::mapRelation,
                workspaceId,
                spaceId,
                relationId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RelationProjection> findProjection(
        UUID workspaceId,
        UUID spaceId,
        UUID relationId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                PROJECTION_SELECT + " where r.workspace_id=? and r.space_id=? and r.id=?",
                this::mapProjection,
                workspaceId,
                spaceId,
                relationId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkItemRelation> findActiveEdge(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "select " + RELATION_COLUMNS
                    + " from project_work_item_relations r"
                    + " where r.workspace_id=? and r.space_id=? and r.relation_key=?"
                    + " and r.source_work_item_id=? and r.target_work_item_id=?"
                    + " and r.status='active'",
                this::mapRelation,
                workspaceId,
                spaceId,
                relationKey,
                sourceWorkItemId,
                targetWorkItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<RelationProjection> list(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String relationKey,
        UUID cursor,
        int limit
    ) {
        StringBuilder sql = new StringBuilder(PROJECTION_SELECT)
            .append(" where r.workspace_id=? and r.space_id=? and r.status='active'")
            .append(" and (r.source_work_item_id=? or r.target_work_item_id=?)");
        List<Object> parameters = new ArrayList<>(
            List.of(workspaceId, spaceId, workItemId, workItemId)
        );
        if (relationKey != null && !relationKey.isBlank()) {
            sql.append(" and r.relation_key=?");
            parameters.add(relationKey);
        }
        if (cursor != null) {
            sql.append(" and r.id>?");
            parameters.add(cursor);
        }
        sql.append(" order by r.id limit ?");
        parameters.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapProjection, parameters.toArray());
    }

    @Override
    public List<WorkItemRelation> listActiveTouching(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId
    ) {
        return jdbcTemplate.query(
            "select " + RELATION_COLUMNS
                + " from project_work_item_relations r"
                + " where r.workspace_id=? and r.space_id=? and r.status='active'"
                + " and (r.source_work_item_id=? or r.target_work_item_id=?)"
                + " order by r.relation_key, r.id for update of r",
            this::mapRelation,
            workspaceId,
            spaceId,
            workItemId,
            workItemId
        );
    }

    @Override
    public long countActiveOutgoing(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID sourceWorkItemId
    ) {
        return count(
            """
                select count(*) from project_work_item_relations
                 where workspace_id=? and space_id=? and relation_key=?
                   and source_work_item_id=? and status='active'
                """,
            workspaceId,
            spaceId,
            relationKey,
            sourceWorkItemId
        );
    }

    @Override
    public long countActiveIncoming(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID targetWorkItemId
    ) {
        return count(
            """
                select count(*) from project_work_item_relations
                 where workspace_id=? and space_id=? and relation_key=?
                   and target_work_item_id=? and status='active'
                """,
            workspaceId,
            spaceId,
            relationKey,
            targetWorkItemId
        );
    }

    @Override
    public boolean pathExists(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID startWorkItemId,
        UUID soughtWorkItemId,
        int maxDepth
    ) {
        Boolean result = jdbcTemplate.queryForObject(
            """
                with recursive reachable(work_item_id, depth, visited) as (
                    select r.target_work_item_id, 1, array[r.source_work_item_id, r.target_work_item_id]
                      from project_work_item_relations r
                     where r.workspace_id=? and r.space_id=? and r.relation_key=?
                       and r.source_work_item_id=? and r.status='active'
                    union all
                    select r.target_work_item_id, reachable.depth + 1,
                           reachable.visited || r.target_work_item_id
                      from reachable
                      join project_work_item_relations r
                        on r.workspace_id=? and r.space_id=? and r.relation_key=?
                       and r.source_work_item_id=reachable.work_item_id
                       and r.status='active'
                     where reachable.depth < ?
                       and not r.target_work_item_id = any(reachable.visited)
                )
                select exists(select 1 from reachable where work_item_id=?)
                """,
            Boolean.class,
            workspaceId,
            spaceId,
            relationKey,
            startWorkItemId,
            workspaceId,
            spaceId,
            relationKey,
            maxDepth,
            soughtWorkItemId
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public List<ImpactEdge> listImpact(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID focusWorkItemId,
        String direction,
        int maxDepth,
        int limit
    ) {
        boolean upstream = "upstream".equals(direction);
        String firstJoin = upstream
            ? "r.target_work_item_id=?"
            : "r.source_work_item_id=?";
        String recursiveJoin = upstream
            ? "r.target_work_item_id=walk.next_id"
            : "r.source_work_item_id=walk.next_id";
        String nextId = upstream ? "r.source_work_item_id" : "r.target_work_item_id";
        String sql = """
            with recursive walk(
                relation_id, source_work_item_id, target_work_item_id,
                next_id, depth, visited
            ) as (
                select r.id, r.source_work_item_id, r.target_work_item_id,
                       %s, 1, array[r.source_work_item_id, r.target_work_item_id]
                  from project_work_item_relations r
                 where r.workspace_id=? and r.space_id=? and r.relation_key=?
                   and r.status='active' and r.relation_kind in ('dependency', 'blocking')
                   and %s
                union all
                select r.id, r.source_work_item_id, r.target_work_item_id,
                       %s, walk.depth + 1, walk.visited || %s
                  from walk
                  join project_work_item_relations r
                    on r.workspace_id=? and r.space_id=? and r.relation_key=?
                   and r.status='active' and r.relation_kind in ('dependency', 'blocking')
                   and %s
                 where walk.depth < ?
                   and not %s = any(walk.visited)
            )
            select relation_id, source_work_item_id, target_work_item_id, min(depth) depth
              from walk
             group by relation_id, source_work_item_id, target_work_item_id
             order by min(depth), relation_id
             limit ?
            """.formatted(
                nextId, firstJoin, nextId, nextId, recursiveJoin, nextId
            );
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) -> new ImpactEdge(
                resultSet.getObject("relation_id", UUID.class),
                resultSet.getObject("source_work_item_id", UUID.class),
                resultSet.getObject("target_work_item_id", UUID.class),
                resultSet.getInt("depth")
            ),
            workspaceId,
            spaceId,
            relationKey,
            focusWorkItemId,
            workspaceId,
            spaceId,
            relationKey,
            maxDepth,
            limit
        );
    }

    @Override
    public int withdraw(
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        long expectedVersion,
        UUID actorId,
        String reasonHash
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_relations
                   set status='withdrawn', version=version+1, updated_by=?, updated_at=now(),
                       withdrawn_by=?, withdrawn_at=now(), withdrawal_reason_hash=?
                 where workspace_id=? and space_id=? and id=? and status='active' and version=?
                """,
            actorId,
            actorId,
            reasonHash,
            workspaceId,
            spaceId,
            relationId,
            expectedVersion
        );
    }

    @Override
    public int restore(
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        long expectedVersion,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_relations
                   set status='active', version=version+1, updated_by=?, updated_at=now(),
                       withdrawn_by=null, withdrawn_at=null, withdrawal_reason_hash=null
                 where workspace_id=? and space_id=? and id=? and status='withdrawn' and version=?
                """,
            actorId,
            workspaceId,
            spaceId,
            relationId,
            expectedVersion
        );
    }

    @Override
    public boolean tryStartCommand(CommandStart command) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_relation_commands (
                    id, workspace_id, space_id, relation_id, operation, request_id,
                    request_hash, status, response_schema_version, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'pending', 1, ?, now())
                on conflict (workspace_id, space_id, operation, request_id) do nothing
                """,
            command.id(),
            command.workspaceId(),
            command.spaceId(),
            command.relationId(),
            command.operation(),
            command.requestId(),
            command.requestHash(),
            command.actorId()
        ) == 1;
    }

    @Override
    public Optional<CommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, space_id, relation_id, operation, request_id,
                           request_hash, status, response_relation_id, response_relation_version,
                           response_payload, created_by
                      from project_work_item_relation_commands
                     where workspace_id=? and space_id=? and operation=? and request_id=?
                    """,
                (resultSet, rowNumber) -> new CommandReceipt(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("workspace_id", UUID.class),
                    resultSet.getObject("space_id", UUID.class),
                    resultSet.getObject("relation_id", UUID.class),
                    resultSet.getString("operation"),
                    resultSet.getString("request_id"),
                    resultSet.getString("request_hash"),
                    resultSet.getString("status"),
                    resultSet.getObject("response_relation_id", UUID.class),
                    nullableLong(resultSet, "response_relation_version"),
                    json(resultSet.getString("response_payload")),
                    resultSet.getObject("created_by", UUID.class)
                ),
                workspaceId,
                spaceId,
                operation,
                requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void completeCommand(
        UUID commandId,
        UUID relationId,
        long relationVersion,
        JsonNode response
    ) {
        if (jdbcTemplate.update(
            """
                update project_work_item_relation_commands
                   set status='completed', response_relation_id=?,
                       response_relation_version=?, response_payload=?::jsonb, completed_at=now()
                 where id=? and status='pending'
                """,
            relationId,
            relationVersion,
            response.toString(),
            commandId
        ) != 1) {
            throw new IllegalStateException("Relation command receipt was not pending");
        }
    }

    @Override
    public void appendHistory(HistoryAppend history) {
        jdbcTemplate.update(
            """
                insert into project_work_item_relation_history (
                    id, workspace_id, space_id, relation_id, relation_version, event_kind,
                    relation_key, source_work_item_id, target_work_item_id,
                    definition_type_id, definition_version_id, definition_config_hash,
                    command_id, safe_metadata, occurred_by, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
                """,
            history.id(),
            history.workspaceId(),
            history.spaceId(),
            history.relationId(),
            history.relationVersion(),
            history.eventKind(),
            history.relationKey(),
            history.sourceWorkItemId(),
            history.targetWorkItemId(),
            history.definitionTypeId(),
            history.definitionVersionId(),
            history.definitionConfigHash(),
            history.commandId(),
            history.safeMetadata().toString(),
            history.actorId()
        );
    }

    private long count(String sql, Object... parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return value == null ? 0 : value;
    }

    private WorkItemRelation mapRelation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkItemRelation(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getString("relation_key"),
            RelationKind.parse(resultSet.getString("relation_kind")),
            Direction.parse(resultSet.getString("direction")),
            resultSet.getObject("definition_type_id", UUID.class),
            resultSet.getObject("definition_version_id", UUID.class),
            resultSet.getString("definition_config_hash"),
            resultSet.getObject("source_work_item_id", UUID.class),
            resultSet.getObject("target_work_item_id", UUID.class),
            resultSet.getString("status"),
            resultSet.getLong("version"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("updated_by", UUID.class),
            resultSet.getTimestamp("updated_at").toInstant(),
            resultSet.getObject("withdrawn_by", UUID.class),
            instant(resultSet.getTimestamp("withdrawn_at"))
        );
    }

    private RelationProjection mapProjection(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RelationProjection(
            mapRelation(resultSet, rowNumber),
            endpoint(resultSet, "source"),
            endpoint(resultSet, "target"),
            resultSet.getString("relation_forward_name"),
            resultSet.getString("relation_reverse_name")
        );
    }

    private RelationEndpoint endpoint(ResultSet resultSet, String prefix) throws SQLException {
        return new RelationEndpoint(
            resultSet.getObject(prefix + "_work_item_id", UUID.class),
            resultSet.getObject(prefix + "_type_definition_id", UUID.class),
            resultSet.getObject(prefix + "_type_version_id", UUID.class),
            resultSet.getString(prefix + "_type_key"),
            resultSet.getString(prefix + "_display_key"),
            resultSet.getString(prefix + "_title"),
            resultSet.getString(prefix + "_status"),
            resultSet.getLong(prefix + "_version")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private JsonNode json(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored relation JSON is invalid", exception);
        }
    }

    private java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
