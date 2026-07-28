package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.MetricSemanticModels.Dimension;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricDefinition;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricExpression;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricVersion;
import com.colla.platform.modules.project.domain.MetricSemanticModels.Window;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMetricSemanticRepository implements MetricSemanticRepository {
    private static final String SELECT = """
        select d.id,d.metric_key,d.name,d.description,d.unit,d.status,d.row_version,
               d.draft_expression,d.draft_window,d.updated_at,
               v.id version_id,v.version_number,v.definition_hash,
               v.expression version_expression,v.window_definition version_window,
               v.published_at,v.published_by
          from project_metric_definitions d
          left join project_metric_versions v
            on v.workspace_id=d.workspace_id and v.space_id=d.space_id
           and v.id=d.current_version_id
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcMetricSemanticRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Dimension> dimensions(UUID workspaceId, UUID spaceId) {
        return jdbc.query("""
            select dimension_key,version,label,value_type,source_contract,cardinality_limit
              from project_metric_dimensions
             where workspace_id=? and space_id=? and status='active'
             order by dimension_key
            """, (result, row) -> new Dimension(
                result.getString("dimension_key"), result.getInt("version"),
                result.getString("label"), result.getString("value_type"),
                result.getString("source_contract"), result.getInt("cardinality_limit")
            ), workspaceId, spaceId);
    }

    @Override
    public List<MetricDefinition> list(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(SELECT + """
             where d.workspace_id=? and d.space_id=? and d.status <> 'archived'
             order by d.updated_at desc,d.id
             limit ?
            """, this::mapDefinition, workspaceId, spaceId, limit);
    }

    @Override
    public Optional<MetricDefinition> find(
        UUID workspaceId, UUID spaceId, UUID metricId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(SELECT + """
                 where d.workspace_id=? and d.space_id=? and d.id=?
                """, this::mapDefinition, workspaceId, spaceId, metricId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId,
        String operation, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                select request_hash,response_payload::text
                  from project_metric_commands
                 where workspace_id=? and space_id=? and actor_id=?
                   and operation=? and request_id=?
                """, (result, row) -> new CommandRecord(
                    result.getString(1), result.getString(2)
                ), workspaceId, spaceId, actorId, operation, requestId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public MetricDefinition save(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID metricId,
        String metricKey, String name, String description, String unit,
        MetricExpression expression, Window window, long expectedVersion,
        String requestId, String requestHash
    ) {
        UUID id = metricId == null ? UUID.randomUUID() : metricId;
        try {
            if (metricId == null) {
                jdbc.update("""
                    insert into project_metric_definitions(
                      id,workspace_id,space_id,metric_key,name,description,unit,status,
                      row_version,draft_expression,draft_window,created_by,updated_by
                    ) values (?,?,?,?,?,?,?,'draft',1,?::jsonb,?::jsonb,?,?)
                    """, id, workspaceId, spaceId, metricKey, name, description, unit,
                    write(expression), write(window), actorId, actorId);
            } else {
                int changed = jdbc.update("""
                    update project_metric_definitions
                       set metric_key=?,name=?,description=?,unit=?,
                           draft_expression=?::jsonb,draft_window=?::jsonb,
                           row_version=row_version+1,updated_by=?,updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                       and row_version=? and status in ('draft','active','disabled')
                    """, metricKey, name, description, unit, write(expression), write(window),
                    actorId, workspaceId, spaceId, id, expectedVersion);
                if (changed != 1) throw versionConflict();
            }
        } catch (DuplicateKeyException exception) {
            throw failure("METRIC_KEY_CONFLICT", "Metric key already exists");
        }
        MetricDefinition result = find(workspaceId, spaceId, id).orElseThrow();
        storeCommand(workspaceId, spaceId, actorId, id, "save_metric",
            requestId, requestHash, result);
        return result;
    }

    @Override
    @Transactional
    public MetricVersion publish(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID metricId,
        long expectedVersion, String definitionHash,
        String requestId, String requestHash
    ) {
        MetricDefinition definition = lock(workspaceId, spaceId, metricId);
        if (definition.version() != expectedVersion
            || "archived".equals(definition.status())) {
            throw versionConflict();
        }
        int number = jdbc.queryForObject("""
            select coalesce(max(version_number),0)+1
              from project_metric_versions
             where workspace_id=? and space_id=? and metric_id=?
            """, Integer.class, workspaceId, spaceId, metricId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
            insert into project_metric_versions(
              id,workspace_id,space_id,metric_id,version_number,schema_version,
              definition_hash,expression,window_definition,published_by
            ) values (?,?,?,?,?,1,?,?::jsonb,?::jsonb,?)
            """, versionId, workspaceId, spaceId, metricId, number, definitionHash,
            write(definition.draftExpression()), write(definition.draftWindow()), actorId);
        int changed = jdbc.update("""
            update project_metric_definitions
               set current_version_id=?,status='active',row_version=row_version+1,
                   updated_by=?,updated_at=now()
             where workspace_id=? and space_id=? and id=? and row_version=?
            """, versionId, actorId, workspaceId, spaceId, metricId, expectedVersion);
        if (changed != 1) throw versionConflict();
        MetricVersion result = find(workspaceId, spaceId, metricId)
            .orElseThrow().publishedVersion();
        storeCommand(workspaceId, spaceId, actorId, metricId, "publish_metric",
            requestId, requestHash, result);
        return result;
    }

    @Override
    @Transactional
    public MetricDefinition lifecycle(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID metricId,
        String action, long expectedVersion, String requestId, String requestHash
    ) {
        String status = switch (action) {
            case "disable" -> "disabled";
            case "revise" -> "draft";
            case "archive" -> "archived";
            default -> throw failure("METRIC_COMMAND_INVALID", "Metric lifecycle action is invalid");
        };
        int changed = jdbc.update("""
            update project_metric_definitions
               set status=?,row_version=row_version+1,updated_by=?,updated_at=now()
             where workspace_id=? and space_id=? and id=? and row_version=?
               and status <> 'archived'
            """, status, actorId, workspaceId, spaceId, metricId, expectedVersion);
        if (changed != 1) throw versionConflict();
        MetricDefinition result = find(workspaceId, spaceId, metricId).orElseThrow();
        storeCommand(workspaceId, spaceId, actorId, metricId, action + "_metric",
            requestId, requestHash, result);
        return result;
    }

    private MetricDefinition lock(UUID workspaceId, UUID spaceId, UUID metricId) {
        try {
            return jdbc.queryForObject(SELECT + """
                 where d.workspace_id=? and d.space_id=? and d.id=?
                 for update of d
                """, this::mapDefinition, workspaceId, spaceId, metricId);
        } catch (EmptyResultDataAccessException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Metric is not available");
        }
    }

    private void storeCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID metricId,
        String operation, String requestId, String requestHash, Object result
    ) {
        jdbc.update("""
            insert into project_metric_commands(
              id,workspace_id,space_id,actor_id,metric_id,operation,
              request_id,request_hash,response_payload,status
            ) values (?,?,?,?,?,?,?,?,?::jsonb,'completed')
            """, UUID.randomUUID(), workspaceId, spaceId, actorId, metricId,
            operation, requestId, requestHash, write(result));
    }

    private MetricDefinition mapDefinition(ResultSet result, int row) throws SQLException {
        MetricVersion version = result.getObject("version_id") == null ? null
            : new MetricVersion(
                result.getObject("version_id", UUID.class),
                result.getObject("id", UUID.class),
                result.getInt("version_number"),
                result.getString("definition_hash"),
                read(result.getString("version_expression"), MetricExpression.class),
                read(result.getString("version_window"), Window.class),
                result.getTimestamp("published_at").toInstant(),
                result.getObject("published_by", UUID.class)
            );
        return new MetricDefinition(
            result.getObject("id", UUID.class), result.getString("metric_key"),
            result.getString("name"), result.getString("description"),
            result.getString("unit"), result.getString("status"),
            result.getLong("row_version"),
            read(result.getString("draft_expression"), MetricExpression.class),
            read(result.getString("draft_window"), Window.class),
            version, result.getTimestamp("updated_at").toInstant()
        );
    }

    private WorkItemRuntimeException versionConflict() {
        return failure("METRIC_VERSION_CONFLICT", "Metric changed; refresh before retrying");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
