package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardColumn;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardOrder;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreference;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreferenceCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkItemBoardRepository implements WorkItemBoardRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemBoardRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<BoardPreference> findPreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select view_key, column_field, swimlane_field, columns_json,
                           aggregate_version, updated_at
                      from project_work_item_board_preferences
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                    """,
                (resultSet, rowNumber) -> new BoardPreference(
                    resultSet.getString("view_key"),
                    resultSet.getString("column_field"),
                    resultSet.getString("swimlane_field"),
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
    public BoardPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        BoardPreferenceCommand command
    ) {
        String requestHash = sha256(json(List.of(
            viewKey,
            command.expectedVersion(),
            command.columnField(),
            command.swimlaneField() == null ? "" : command.swimlaneField(),
            command.columns()
        )));
        Optional<CommandRecord> replay = findCommand(
            workspaceId, spaceId, userId, "save_preference", command.requestId()
        );
        if (replay.isPresent()) {
            if (!requestHash.equals(replay.get().requestHash())) {
                throw failure("BOARD_REQUEST_CONFLICT", "Board request ID was reused with different input");
            }
            if (!"completed".equals(replay.get().status())) {
                throw failure("BOARD_REQUEST_IN_PROGRESS", "Board request is already in progress");
            }
            return preference(replay.get().responseJson());
        }
        Optional<BoardPreference> current = findPreference(workspaceId, spaceId, userId, viewKey);
        if (current.isPresent() && current.get().version() != command.expectedVersion()
            || current.isEmpty() && command.expectedVersion() != 0) {
            throw failure("BOARD_PREFERENCE_VERSION_CONFLICT", "Board preference changed; refresh and retry");
        }
        int changed;
        if (current.isEmpty()) {
            changed = jdbcTemplate.update(
                """
                    insert into project_work_item_board_preferences (
                        workspace_id, space_id, user_id, view_key, schema_version,
                        column_field, swimlane_field, columns_json, aggregate_version,
                        created_at, updated_at
                    ) values (?, ?, ?, ?, 1, ?, ?, ?::jsonb, 1, now(), now())
                    on conflict do nothing
                    """,
                workspaceId,
                spaceId,
                userId,
                viewKey,
                command.columnField(),
                command.swimlaneField(),
                json(command.columns())
            );
        } else {
            changed = jdbcTemplate.update(
                """
                    update project_work_item_board_preferences
                       set column_field=?, swimlane_field=?, columns_json=?::jsonb,
                           aggregate_version=aggregate_version+1, updated_at=now()
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                       and aggregate_version=?
                    """,
                command.columnField(),
                command.swimlaneField(),
                json(command.columns()),
                workspaceId,
                spaceId,
                userId,
                viewKey,
                command.expectedVersion()
            );
        }
        if (changed != 1) {
            throw failure("BOARD_PREFERENCE_VERSION_CONFLICT", "Board preference changed; refresh and retry");
        }
        BoardPreference result = findPreference(workspaceId, spaceId, userId, viewKey).orElseThrow();
        jdbcTemplate.update(
            """
                insert into project_work_item_board_commands (
                    id, workspace_id, space_id, user_id, view_key, work_item_id,
                    operation, request_id, request_hash, expected_version, status,
                    response_json, created_at, completed_at
                ) values (?, ?, ?, ?, ?, null, 'save_preference', ?, ?, ?, 'completed',
                          ?::jsonb, now(), now())
                """,
            UUID.randomUUID(),
            workspaceId,
            spaceId,
            userId,
            viewKey,
            command.requestId(),
            requestHash,
            command.expectedVersion(),
            json(result)
        );
        return result;
    }

    @Override
    public List<BoardOrder> listOrders(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<UUID> workItemIds
    ) {
        if (workItemIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(workItemIds.size(), "?"));
        List<Object> parameters = new ArrayList<>(List.of(workspaceId, spaceId, userId, viewKey));
        parameters.addAll(workItemIds);
        return jdbcTemplate.query(
            """
                select work_item_id, column_key, swimlane_key, rank,
                       source_work_item_version, aggregate_version, updated_at
                  from project_work_item_board_orders
                 where workspace_id=? and space_id=? and user_id=? and view_key=?
                   and work_item_id in (%s)
                """.formatted(placeholders),
            (resultSet, rowNumber) -> new BoardOrder(
                resultSet.getObject("work_item_id", UUID.class),
                resultSet.getString("column_key"),
                resultSet.getString("swimlane_key"),
                resultSet.getLong("rank"),
                resultSet.getLong("source_work_item_version"),
                resultSet.getLong("aggregate_version"),
                resultSet.getTimestamp("updated_at").toInstant()
            ),
            parameters.toArray()
        );
    }

    @Override
    public BoardOrder reserveOrder(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId,
        String columnKey,
        String swimlaneKey,
        long rank,
        long expectedOrderVersion,
        long sourceWorkItemVersion
    ) {
        int changed;
        if (expectedOrderVersion == 0) {
            changed = jdbcTemplate.update(
                """
                    insert into project_work_item_board_orders (
                        workspace_id, space_id, user_id, view_key, work_item_id,
                        column_key, swimlane_key, rank, source_work_item_version,
                        aggregate_version, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now())
                    on conflict do nothing
                    """,
                workspaceId, spaceId, userId, viewKey, workItemId,
                columnKey, swimlaneKey, rank, sourceWorkItemVersion
            );
        } else {
            changed = jdbcTemplate.update(
                """
                    update project_work_item_board_orders
                       set column_key=?, swimlane_key=?, rank=?, source_work_item_version=?,
                           aggregate_version=aggregate_version+1, updated_at=now()
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                       and work_item_id=? and aggregate_version=?
                    """,
                columnKey, swimlaneKey, rank, sourceWorkItemVersion,
                workspaceId, spaceId, userId, viewKey, workItemId, expectedOrderVersion
            );
        }
        if (changed != 1) {
            throw failure("BOARD_ORDER_VERSION_CONFLICT", "Board order changed; refresh and retry");
        }
        return requireOrder(workspaceId, spaceId, userId, viewKey, workItemId);
    }

    @Override
    public BoardOrder alignOrderSourceVersion(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId,
        long expectedOrderVersion,
        long sourceWorkItemVersion
    ) {
        int changed = jdbcTemplate.update(
            """
                update project_work_item_board_orders
                   set source_work_item_version=?, updated_at=now()
                 where workspace_id=? and space_id=? and user_id=? and view_key=?
                   and work_item_id=? and aggregate_version=?
                """,
            sourceWorkItemVersion,
            workspaceId,
            spaceId,
            userId,
            viewKey,
            workItemId,
            expectedOrderVersion
        );
        if (changed != 1) {
            throw failure("BOARD_ORDER_VERSION_CONFLICT", "Board order changed; refresh and retry");
        }
        return requireOrder(workspaceId, spaceId, userId, viewKey, workItemId);
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, request_hash, status, response_json
                      from project_work_item_board_commands
                     where workspace_id=? and space_id=? and user_id=?
                       and operation=? and request_id=?
                    """,
                (resultSet, rowNumber) -> new CommandRecord(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("request_hash"),
                    resultSet.getString("status"),
                    resultSet.getString("response_json")
                ),
                workspaceId, spaceId, userId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public CommandRecord beginCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId,
        String operation,
        String requestId,
        String requestHash,
        long expectedVersion
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                    insert into project_work_item_board_commands (
                        id, workspace_id, space_id, user_id, view_key, work_item_id,
                        operation, request_id, request_hash, expected_version, status,
                        created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', now())
                    on conflict (workspace_id, space_id, user_id, operation, request_id) do nothing
                """,
            id, workspaceId, spaceId, userId, viewKey, workItemId,
            operation, requestId, requestHash, expectedVersion
        );
        return findCommand(workspaceId, spaceId, userId, operation, requestId).orElseThrow();
    }

    @Override
    public void completeCommand(UUID commandId, String responseJson) {
        int changed = jdbcTemplate.update(
            """
                update project_work_item_board_commands
                   set status='completed', response_json=?::jsonb, completed_at=now()
                 where id=? and status='pending'
                """,
            responseJson,
            commandId
        );
        if (changed != 1) {
            throw failure("BOARD_REQUEST_CONFLICT", "Board command could not be completed");
        }
    }

    @Override
    public void recordRender(
        UUID workspaceId,
        UUID spaceId,
        String viewKey,
        int columnCount,
        int laneCount,
        int cardCount
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_board_projection_stats (
                    workspace_id, space_id, view_key, render_count,
                    last_column_count, last_lane_count, last_card_count, updated_at
                ) values (?, ?, ?, 1, ?, ?, ?, now())
                on conflict (workspace_id, space_id, view_key) do update
                    set render_count=project_work_item_board_projection_stats.render_count+1,
                        last_column_count=excluded.last_column_count,
                        last_lane_count=excluded.last_lane_count,
                        last_card_count=excluded.last_card_count,
                        updated_at=excluded.updated_at
                """,
            workspaceId, spaceId, viewKey, columnCount, laneCount, cardCount
        );
    }

    private BoardOrder requireOrder(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId
    ) {
        return listOrders(workspaceId, spaceId, userId, viewKey, List.of(workItemId))
            .stream().findFirst().orElseThrow();
    }

    private List<BoardColumn> columns(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored board columns are invalid", exception);
        }
    }

    private BoardPreference preference(String value) {
        try {
            return objectMapper.readValue(value, BoardPreference.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored board preference response is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board configuration is invalid", exception);
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
