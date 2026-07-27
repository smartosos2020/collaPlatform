package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemViewModels.ColumnSpec;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ExportJob;
import com.colla.platform.modules.project.domain.WorkItemViewModels.PreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewMode;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewPreference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
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
public class JdbcWorkItemViewRepository implements WorkItemViewRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemViewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ViewPreference> findPreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select view_key, view_mode, density, columns_json, aggregate_version, updated_at
                      from project_work_item_view_preferences
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                    """,
                (resultSet, rowNumber) -> new ViewPreference(
                    resultSet.getString("view_key"),
                    ViewMode.valueOf(resultSet.getString("view_mode")),
                    resultSet.getString("density"),
                    columns(resultSet.getString("columns_json")),
                    resultSet.getLong("aggregate_version"),
                    resultSet.getTimestamp("updated_at").toInstant()
                ),
                workspaceId,
                spaceId,
                userId,
                viewKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public ViewPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        PreferenceCommand command
    ) {
        String requestHash = sha256(json(List.of(
            viewKey,
            command.expectedVersion(),
            command.mode().name(),
            command.density(),
            command.columns()
        )));
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
                select request_hash, response_json
                  from project_work_item_view_commands
                 where workspace_id=? and space_id=? and user_id=?
                   and operation='save_preference' and request_id=?
                   and status='completed'
                """,
            workspaceId,
            spaceId,
            userId,
            command.requestId()
        );
        if (!replay.isEmpty()) {
            if (!requestHash.equals(replay.getFirst().get("request_hash"))) {
                throw failure(
                    "VIEW_REQUEST_CONFLICT",
                    "View preference request ID was reused with different input"
                );
            }
            return preference(String.valueOf(replay.getFirst().get("response_json")));
        }
        Optional<ViewPreference> current = findPreference(workspaceId, spaceId, userId, viewKey);
        if (current.isPresent() && current.get().version() != command.expectedVersion()
            || current.isEmpty() && command.expectedVersion() != 0) {
            throw failure("VIEW_PREFERENCE_VERSION_CONFLICT", "View preference changed; refresh and retry");
        }
        int changed;
        if (current.isEmpty()) {
            changed = jdbcTemplate.update(
                """
                    insert into project_work_item_view_preferences (
                        workspace_id, space_id, user_id, view_key, view_mode, density,
                        columns_json, aggregate_version, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?::jsonb, 1, now(), now())
                    on conflict do nothing
                    """,
                workspaceId,
                spaceId,
                userId,
                viewKey,
                command.mode().name(),
                command.density(),
                json(command.columns())
            );
        } else {
            changed = jdbcTemplate.update(
                """
                    update project_work_item_view_preferences
                       set view_mode=?, density=?, columns_json=?::jsonb,
                           aggregate_version=aggregate_version+1, updated_at=now()
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                       and aggregate_version=?
                    """,
                command.mode().name(),
                command.density(),
                json(command.columns()),
                workspaceId,
                spaceId,
                userId,
                viewKey,
                command.expectedVersion()
            );
        }
        if (changed != 1) {
            throw failure("VIEW_PREFERENCE_VERSION_CONFLICT", "View preference changed; refresh and retry");
        }
        ViewPreference result = findPreference(workspaceId, spaceId, userId, viewKey).orElseThrow();
        jdbcTemplate.update(
            """
                insert into project_work_item_view_commands (
                    id, workspace_id, space_id, user_id, view_key, operation,
                    request_id, request_hash, expected_version, status, response_version,
                    response_json, created_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'completed', ?, ?::jsonb, now(), now())
                on conflict (workspace_id, space_id, user_id, operation, request_id) do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            spaceId,
            userId,
            viewKey,
            "save_preference",
            command.requestId(),
            requestHash,
            command.expectedVersion(),
            result.version(),
            json(result)
        );
        return result;
    }

    @Override
    public ExportRecord createOrFindExport(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String requestId,
        String requestHash,
        JsonNode query,
        JsonNode columns,
        Instant expiresAt
    ) {
        try {
            jdbcTemplate.update(
                """
                    insert into project_work_item_export_jobs (
                        id, workspace_id, space_id, owner_user_id, request_id, request_hash,
                        query_schema_version, query_json, columns_json, status, row_limit,
                        created_at, ready_at, expires_at
                    ) values (?, ?, ?, ?, ?, ?, 1, ?::jsonb, ?::jsonb, 'ready', 200, now(), now(), ?)
                    """,
                id,
                workspaceId,
                spaceId,
                userId,
                requestId,
                requestHash,
                query.toString(),
                columns.toString(),
                Timestamp.from(expiresAt)
            );
        } catch (DuplicateKeyException ignored) {
            // Stable request IDs replay the existing job after hash verification below.
        }
        ExportRecord record = jdbcTemplate.queryForObject(
            """
                select id, owner_user_id, request_hash, query_json, columns_json, status,
                       row_limit, expires_at
                  from project_work_item_export_jobs
                 where workspace_id=? and space_id=? and owner_user_id=? and request_id=?
                """,
            (resultSet, rowNumber) -> export(resultSet, spaceId),
            workspaceId,
            spaceId,
            userId,
            requestId
        );
        if (record == null || !record.requestHash().equals(requestHash)) {
            throw failure("EXPORT_REQUEST_CONFLICT", "Export request ID was reused with different input");
        }
        return record;
    }

    @Override
    public Optional<ExportRecord> findExport(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        UUID exportId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, owner_user_id, request_hash, query_json, columns_json, status,
                           row_limit, expires_at
                      from project_work_item_export_jobs
                     where workspace_id=? and space_id=? and owner_user_id=? and id=?
                    """,
                (resultSet, rowNumber) -> export(resultSet, spaceId),
                workspaceId,
                spaceId,
                userId,
                exportId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ExportRecord export(java.sql.ResultSet resultSet, UUID spaceId)
        throws java.sql.SQLException {
        UUID id = resultSet.getObject("id", UUID.class);
        return new ExportRecord(
            new ExportJob(
                id,
                resultSet.getString("status"),
                resultSet.getInt("row_limit"),
                resultSet.getTimestamp("expires_at").toInstant(),
                "/api/project-spaces/" + spaceId + "/work-item-views/exports/" + id + "/download"
            ),
            read(resultSet.getString("query_json")),
            read(resultSet.getString("columns_json")),
            resultSet.getString("request_hash"),
            resultSet.getObject("owner_user_id", UUID.class)
        );
    }

    private List<ColumnSpec> columns(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored view columns are invalid", exception);
        }
    }

    private ViewPreference preference(String value) {
        try {
            return objectMapper.readValue(value, ViewPreference.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored view preference response is invalid", exception);
        }
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored export input is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_VIEW_CONFIGURATION", "View configuration is invalid", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
