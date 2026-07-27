package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ProjectDetailModels.DetailPreference;
import com.colla.platform.modules.project.domain.ProjectDetailModels.HealthStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcProjectDetailRepository implements ProjectDetailRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProjectDetailRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<DetailPreference> findPreference(
        UUID workspaceId, UUID spaceId, UUID actorId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select schema_version, visible_sections, compact,
                           aggregate_version, updated_at
                      from project_detail_preferences
                     where workspace_id=? and space_id=? and actor_id=?
                    """,
                (rs, row) -> new DetailPreference(
                    rs.getInt("schema_version"),
                    strings(rs.getString("visible_sections")),
                    rs.getBoolean("compact"),
                    rs.getLong("aggregate_version"),
                    rs.getTimestamp("updated_at").toInstant()
                ),
                workspaceId, spaceId, actorId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select request_hash, response_json
                      from project_detail_commands
                     where workspace_id=? and space_id=? and actor_id=?
                       and operation='save_preference' and request_id=?
                    """,
                (rs, row) -> new CommandRecord(
                    rs.getString("request_hash"), rs.getString("response_json")
                ),
                workspaceId, spaceId, actorId, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public DetailPreference savePreference(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId,
        String requestHash, long expectedVersion, List<String> visibleSections,
        boolean compact
    ) {
        int changed;
        if (expectedVersion == 0) {
            changed = jdbc.update(
                """
                    insert into project_detail_preferences(
                        id, workspace_id, space_id, actor_id, visible_sections,
                        compact, aggregate_version, updated_at
                    ) values (?, ?, ?, ?, cast(? as jsonb), ?, 1, now())
                    on conflict (workspace_id, space_id, actor_id) do nothing
                    """,
                UUID.randomUUID(), workspaceId, spaceId, actorId,
                json(visibleSections), compact
            );
        } else {
            changed = jdbc.update(
                """
                    update project_detail_preferences
                       set visible_sections=cast(? as jsonb), compact=?,
                           aggregate_version=aggregate_version+1, updated_at=now()
                     where workspace_id=? and space_id=? and actor_id=?
                       and aggregate_version=?
                    """,
                json(visibleSections), compact, workspaceId, spaceId, actorId,
                expectedVersion
            );
        }
        if (changed != 1) {
            throw failure(
                "PROJECT_DETAIL_VERSION_CONFLICT",
                "Project detail preference changed concurrently"
            );
        }
        DetailPreference result = findPreference(workspaceId, spaceId, actorId)
            .orElseThrow(() -> new IllegalStateException("Preference disappeared"));
        jdbc.update(
            """
                insert into project_detail_commands(
                    id, workspace_id, space_id, actor_id, operation,
                    request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, 'save_preference', ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId,
            requestId, requestHash, json(result)
        );
        return result;
    }

    @Override
    public void recordProjection(
        UUID workspaceId, UUID spaceId, UUID actorId,
        HealthStatus health, String sourceFingerprint
    ) {
        jdbc.update(
            """
                insert into project_health_projection_index(
                    id, workspace_id, space_id, actor_id, health_status,
                    signal_count, truncated, policy_version, source_fingerprint,
                    derived_at, expires_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as timestamptz) + interval '5 minutes')
                on conflict (workspace_id, space_id, actor_id) do update
                    set health_status=excluded.health_status,
                        signal_count=excluded.signal_count,
                        truncated=excluded.truncated,
                        policy_version=excluded.policy_version,
                        source_fingerprint=excluded.source_fingerprint,
                        derived_at=excluded.derived_at,
                        expires_at=excluded.expires_at
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, health.status(),
            health.signals().size(), health.truncated(), health.policyVersion(),
            sourceFingerprint, java.sql.Timestamp.from(health.derivedAt()),
            java.sql.Timestamp.from(health.derivedAt())
        );
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read detail preference", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize detail value", exception);
        }
    }
}
