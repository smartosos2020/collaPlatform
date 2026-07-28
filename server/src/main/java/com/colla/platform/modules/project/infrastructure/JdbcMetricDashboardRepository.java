package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.MetricDashboardModels.ChartDefinition;
import com.colla.platform.modules.project.domain.MetricDashboardModels.Dashboard;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardConfig;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardPreference;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMetricDashboardRepository implements MetricDashboardRepository {
    private static final String SELECT = """
        select d.id,d.dashboard_key,d.name,d.description,d.status,d.sharing_scope,
               d.row_version,d.draft_config,d.updated_at,
               v.id version_id,v.version_number,v.definition_hash,
               v.config version_config,v.published_at,v.published_by
          from project_dashboards d
          left join project_dashboard_versions v
            on v.workspace_id=d.workspace_id and v.space_id=d.space_id
           and v.id=d.current_version_id
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcMetricDashboardRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Dashboard> list(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(SELECT + """
             where d.workspace_id=? and d.space_id=? and d.status <> 'archived'
             order by d.updated_at desc,d.id
             limit ?
            """, this::mapDashboard, workspaceId, spaceId, limit);
    }

    @Override
    public Optional<Dashboard> find(
        UUID workspaceId,
        UUID spaceId,
        UUID dashboardId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(SELECT + """
                 where d.workspace_id=? and d.space_id=? and d.id=?
                """, this::mapDashboard, workspaceId, spaceId, dashboardId));
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
            return Optional.ofNullable(jdbc.queryForObject("""
                select request_hash,response_payload::text
                  from project_dashboard_commands
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
    public Dashboard save(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID dashboardId,
        String dashboardKey,
        String name,
        String description,
        DashboardConfig config,
        long expectedVersion,
        String requestId,
        String requestHash
    ) {
        try {
            if (expectedVersion == 0) {
                jdbc.update("""
                    insert into project_dashboards(
                      id,workspace_id,space_id,dashboard_key,name,description,status,
                      sharing_scope,row_version,draft_config,created_by,updated_by
                    ) values (?,?,?,?,?,?,'draft','private',1,?::jsonb,?,?)
                    """, dashboardId, workspaceId, spaceId, dashboardKey, name,
                    description, write(config), actorId, actorId);
            } else {
                int changed = jdbc.update("""
                    update project_dashboards
                       set dashboard_key=?,name=?,description=?,draft_config=?::jsonb,
                           row_version=row_version+1,updated_by=?,updated_at=now()
                     where workspace_id=? and space_id=? and id=? and row_version=?
                       and status <> 'archived'
                    """, dashboardKey, name, description, write(config), actorId,
                    workspaceId, spaceId, dashboardId, expectedVersion);
                if (changed != 1) throw versionConflict();
            }
            synchronizeDraft(workspaceId, spaceId, dashboardId, config);
        } catch (DuplicateKeyException exception) {
            throw failure("DASHBOARD_KEY_CONFLICT", "Dashboard or chart key already exists");
        }
        Dashboard result = find(workspaceId, spaceId, dashboardId).orElseThrow();
        storeCommand(workspaceId, spaceId, actorId, dashboardId, "save_dashboard",
            requestId, requestHash, result);
        return result;
    }

    private void synchronizeDraft(
        UUID workspaceId,
        UUID spaceId,
        UUID dashboardId,
        DashboardConfig config
    ) {
        jdbc.update("""
            delete from project_metric_data_source_bindings
             where workspace_id=? and space_id=? and dashboard_id=?
            """, workspaceId, spaceId, dashboardId);
        for (var binding : config.dataSources()) {
            UUID bindingId = UUID.nameUUIDFromBytes(
                (dashboardId + ":" + binding.bindingKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            jdbc.update("""
                insert into project_metric_data_source_bindings(
                  id,workspace_id,space_id,dashboard_id,binding_key,source_kind,
                  source_space_ids,saved_view_id,metric_id,metric_version,row_version
                ) values (?,?,?,?,?,?,?::jsonb,?,?,?,1)
                """, bindingId, workspaceId, spaceId, dashboardId, binding.bindingKey(),
                binding.kind(), write(binding.spaceIds()), binding.savedViewId(),
                binding.metricId(), binding.metricVersion());
        }
        jdbc.update("""
            update project_chart_definitions set status='archived',updated_at=now()
             where workspace_id=? and space_id=? and dashboard_id=?
            """, workspaceId, spaceId, dashboardId);
        for (ChartDefinition chart : config.charts()) {
            int changed = jdbc.update("""
                update project_chart_definitions
                   set name=?,visualization=?,draft_config=?::jsonb,status='draft',
                       row_version=row_version+1,updated_at=now()
                 where workspace_id=? and space_id=? and dashboard_id=? and id=?
                """, chart.name(), chart.visualization(), write(chart),
                workspaceId, spaceId, dashboardId, chart.id());
            if (changed == 0) {
                jdbc.update("""
                    insert into project_chart_definitions(
                      id,workspace_id,space_id,dashboard_id,chart_key,name,
                      visualization,draft_config,status,row_version
                    ) values (?,?,?,?,?,?,?,?::jsonb,'draft',1)
                    """, chart.id(), workspaceId, spaceId, dashboardId,
                    chart.chartKey(), chart.name(), chart.visualization(), write(chart));
            }
        }
    }

    @Override
    @Transactional
    public DashboardVersion publish(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID dashboardId,
        long expectedVersion,
        String definitionHash,
        String requestId,
        String requestHash
    ) {
        Dashboard dashboard = lock(workspaceId, spaceId, dashboardId);
        if (dashboard.version() != expectedVersion
            || "archived".equals(dashboard.status())) {
            throw versionConflict();
        }
        List<Map<String, Object>> refs = dashboard.draftConfig().charts().stream()
            .map(chart -> publishChart(workspaceId, spaceId, actorId, chart))
            .toList();
        int number = jdbc.queryForObject("""
            select coalesce(max(version_number),0)+1
              from project_dashboard_versions
             where workspace_id=? and space_id=? and dashboard_id=?
            """, Integer.class, workspaceId, spaceId, dashboardId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
            insert into project_dashboard_versions(
              id,workspace_id,space_id,dashboard_id,version_number,schema_version,
              definition_hash,config,chart_version_refs,published_by
            ) values (?,?,?,?,?,1,?,?::jsonb,?::jsonb,?)
            """, versionId, workspaceId, spaceId, dashboardId, number,
            definitionHash, write(dashboard.draftConfig()), write(refs), actorId);
        int changed = jdbc.update("""
            update project_dashboards
               set current_version_id=?,status='active',row_version=row_version+1,
                   updated_by=?,updated_at=now()
             where workspace_id=? and space_id=? and id=? and row_version=?
            """, versionId, actorId, workspaceId, spaceId, dashboardId, expectedVersion);
        if (changed != 1) throw versionConflict();
        DashboardVersion result = find(workspaceId, spaceId, dashboardId)
            .orElseThrow().publishedVersion();
        storeCommand(workspaceId, spaceId, actorId, dashboardId, "publish_dashboard",
            requestId, requestHash, result);
        return result;
    }

    private Map<String, Object> publishChart(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        ChartDefinition chart
    ) {
        int number = jdbc.queryForObject("""
            select coalesce(max(version_number),0)+1
              from project_chart_versions
             where workspace_id=? and space_id=? and chart_id=?
            """, Integer.class, workspaceId, spaceId, chart.id());
        String hash = sha256(write(chart));
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
            insert into project_chart_versions(
              id,workspace_id,space_id,chart_id,version_number,schema_version,
              definition_hash,config,published_by
            ) values (?,?,?,?,?,1,?,?::jsonb,?)
            """, versionId, workspaceId, spaceId, chart.id(), number,
            hash, write(chart), actorId);
        jdbc.update("""
            update project_chart_definitions
               set current_version_id=?,status='active',updated_at=now()
             where workspace_id=? and space_id=? and id=?
            """, versionId, workspaceId, spaceId, chart.id());
        return Map.of(
            "chartId", chart.id(),
            "chartKey", chart.chartKey(),
            "versionId", versionId,
            "versionNumber", number,
            "definitionHash", hash
        );
    }

    @Override
    @Transactional
    public Dashboard lifecycle(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID dashboardId,
        String action,
        long expectedVersion,
        String requestId,
        String requestHash
    ) {
        String status = switch (action) {
            case "disable" -> "disabled";
            case "revise" -> "draft";
            case "archive" -> "archived";
            default -> null;
        };
        String sharing = switch (action) {
            case "share" -> "space";
            case "unshare" -> "private";
            default -> null;
        };
        if (status == null && sharing == null) {
            throw failure("DASHBOARD_COMMAND_INVALID", "Dashboard lifecycle action is invalid");
        }
        int changed = status != null
            ? jdbc.update("""
                update project_dashboards
                   set status=?,row_version=row_version+1,updated_by=?,updated_at=now()
                 where workspace_id=? and space_id=? and id=? and row_version=?
                   and status <> 'archived'
                """, status, actorId, workspaceId, spaceId, dashboardId, expectedVersion)
            : jdbc.update("""
                update project_dashboards
                   set sharing_scope=?,row_version=row_version+1,updated_by=?,updated_at=now()
                 where workspace_id=? and space_id=? and id=? and row_version=?
                   and status='active'
                """, sharing, actorId, workspaceId, spaceId, dashboardId, expectedVersion);
        if (changed != 1) throw versionConflict();
        Dashboard result = find(workspaceId, spaceId, dashboardId).orElseThrow();
        storeCommand(workspaceId, spaceId, actorId, dashboardId, action + "_dashboard",
            requestId, requestHash, result);
        return result;
    }

    @Override
    public DashboardPreference preference(
        UUID workspaceId,
        UUID spaceId,
        UUID dashboardId,
        UUID userId
    ) {
        try {
            return jdbc.queryForObject("""
                select compact,filter_values::text,row_version
                  from project_dashboard_preferences
                 where workspace_id=? and space_id=? and dashboard_id=? and user_id=?
                """, (result, row) -> new DashboardPreference(
                    dashboardId,
                    result.getBoolean("compact"),
                    readMap(result.getString("filter_values")),
                    result.getLong("row_version")
                ), workspaceId, spaceId, dashboardId, userId);
        } catch (EmptyResultDataAccessException exception) {
            return new DashboardPreference(dashboardId, false, Map.of(), 0);
        }
    }

    @Override
    @Transactional
    public DashboardPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID dashboardId,
        UUID userId,
        boolean compact,
        Map<String, String> filterValues,
        long expectedVersion
    ) {
        int changed;
        if (expectedVersion == 0) {
            changed = jdbc.update("""
                insert into project_dashboard_preferences(
                  id,workspace_id,space_id,dashboard_id,user_id,compact,filter_values,row_version
                ) values (?,?,?,?,?,?,?::jsonb,1)
                on conflict (workspace_id,space_id,dashboard_id,user_id) do nothing
                """, UUID.randomUUID(), workspaceId, spaceId, dashboardId, userId,
                compact, write(filterValues));
        } else {
            changed = jdbc.update("""
                update project_dashboard_preferences
                   set compact=?,filter_values=?::jsonb,row_version=row_version+1,updated_at=now()
                 where workspace_id=? and space_id=? and dashboard_id=? and user_id=?
                   and row_version=?
                """, compact, write(filterValues), workspaceId, spaceId,
                dashboardId, userId, expectedVersion);
        }
        if (changed != 1) throw versionConflict();
        return preference(workspaceId, spaceId, dashboardId, userId);
    }

    private Dashboard lock(UUID workspaceId, UUID spaceId, UUID dashboardId) {
        try {
            return jdbc.queryForObject(SELECT + """
                 where d.workspace_id=? and d.space_id=? and d.id=?
                 for update of d
                """, this::mapDashboard, workspaceId, spaceId, dashboardId);
        } catch (EmptyResultDataAccessException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Dashboard is not available");
        }
    }

    private void storeCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID dashboardId,
        String operation,
        String requestId,
        String requestHash,
        Object result
    ) {
        jdbc.update("""
            insert into project_dashboard_commands(
              id,workspace_id,space_id,actor_id,dashboard_id,operation,
              request_id,request_hash,response_payload,status
            ) values (?,?,?,?,?,?,?,?,?::jsonb,'completed')
            """, UUID.randomUUID(), workspaceId, spaceId, actorId, dashboardId,
            operation, requestId, requestHash, write(result));
    }

    private Dashboard mapDashboard(ResultSet result, int row) throws SQLException {
        DashboardVersion version = result.getObject("version_id") == null ? null
            : new DashboardVersion(
                result.getObject("version_id", UUID.class),
                result.getObject("id", UUID.class),
                result.getInt("version_number"),
                result.getString("definition_hash"),
                read(result.getString("version_config"), DashboardConfig.class),
                result.getTimestamp("published_at").toInstant(),
                result.getObject("published_by", UUID.class)
            );
        return new Dashboard(
            result.getObject("id", UUID.class),
            result.getString("dashboard_key"),
            result.getString("name"),
            result.getString("description"),
            result.getString("status"),
            result.getString("sharing_scope"),
            result.getLong("row_version"),
            read(result.getString("draft_config"), DashboardConfig.class),
            version,
            result.getTimestamp("updated_at").toInstant()
        );
    }

    private RuntimeException versionConflict() {
        return failure("DASHBOARD_VERSION_CONFLICT", "Dashboard changed; refresh before retrying");
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

    private Map<String, String> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
