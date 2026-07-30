package com.colla.platform.modules.platform.infrastructure;

import com.colla.platform.modules.platform.contract.DashboardPersonalization.CardPreference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDashboardPersonalizationRepository implements DashboardPersonalizationRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDashboardPersonalizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long currentVersion(UUID workspaceId, UUID userId) {
        Long value = jdbcTemplate.queryForObject(
            "select coalesce(max(layout_version), 0) from platform_dashboard_card_layouts where workspace_id = ? and user_id = ?",
            Long.class,
            workspaceId,
            userId
        );
        return value == null ? 0 : value;
    }

    @Override
    public List<CardPreference> layout(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select card_key, position, hidden
                from platform_dashboard_card_layouts
                where workspace_id = ? and user_id = ?
                order by position, card_key
                """,
            (rs, rowNum) -> new CardPreference(rs.getString("card_key"), rs.getInt("position"), rs.getBoolean("hidden")),
            workspaceId,
            userId
        );
    }

    @Override
    public boolean tryStartCommand(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String operation,
        String requestId,
        String requestHash
    ) {
        return jdbcTemplate.update(
            """
                insert into platform_personalization_commands
                    (id, workspace_id, user_id, request_id, operation, request_hash, status, created_at)
                values (?, ?, ?, ?, ?, ?, 'started', now())
                on conflict (workspace_id, user_id, operation, request_id) do nothing
                """,
            id, workspaceId, userId, requestId, operation, requestHash
        ) == 1;
    }

    @Override
    public Optional<Long> completedCommand(
        UUID workspaceId,
        UUID userId,
        String operation,
        String requestId,
        String requestHash
    ) {
        return jdbcTemplate.query(
            """
                select response_version from platform_personalization_commands
                where workspace_id = ? and user_id = ? and operation = ?
                  and request_id = ? and request_hash = ? and status = 'completed'
                """,
            (rs, rowNum) -> rs.getLong("response_version"),
            workspaceId, userId, operation, requestId, requestHash
        ).stream().findFirst();
    }

    @Override
    public boolean replace(
        UUID workspaceId,
        UUID userId,
        long expectedVersion,
        long nextVersion,
        List<CardPreference> cards
    ) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            resultSet -> null,
            workspaceId + ":" + userId
        );
        if (currentVersion(workspaceId, userId) != expectedVersion) {
            return false;
        }
        jdbcTemplate.update(
            "delete from platform_dashboard_card_layouts where workspace_id = ? and user_id = ?",
            workspaceId, userId
        );
        for (CardPreference card : cards) {
            jdbcTemplate.update(
                """
                    insert into platform_dashboard_card_layouts
                        (id, workspace_id, user_id, card_key, position, hidden, layout_version, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, now(), now())
                    """,
                UUID.randomUUID(), workspaceId, userId, card.cardKey(), card.position(), card.hidden(), nextVersion
            );
        }
        return true;
    }

    @Override
    public void completeCommand(UUID id, long responseVersion) {
        jdbcTemplate.update(
            """
                update platform_personalization_commands
                set status = 'completed', response_version = ?, completed_at = now()
                where id = ? and status = 'started'
                """,
            responseVersion, id
        );
    }

    @Override
    public Instant updatedAt(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            "select max(updated_at) updated_at from platform_dashboard_card_layouts where workspace_id = ? and user_id = ?",
            rs -> rs.next() && rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toInstant()
                : Instant.EPOCH,
            workspaceId, userId
        );
    }
}
