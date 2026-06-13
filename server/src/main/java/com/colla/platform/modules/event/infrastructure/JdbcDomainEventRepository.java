package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDomainEventRepository implements DomainEventRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDomainEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID append(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        String idempotencyKey
    ) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                """
                    insert into domain_events
                        (id, workspace_id, event_type, aggregate_type, aggregate_id, actor_id,
                         payload, status, idempotency_key, created_at)
                    values (?, ?, ?, ?, ?, ?, ?::jsonb, 'pending', ?, now())
                    """,
                id,
                workspaceId,
                eventType,
                aggregateType,
                aggregateId,
                actorId,
                writePayload(payload),
                idempotencyKey
            );
        } catch (DuplicateKeyException exception) {
            return id;
        }
        return id;
    }

    @Override
    public List<DomainEvent> claimPending(int limit) {
        return jdbcTemplate.query(
            """
                update domain_events
                set status = 'processing'
                where id in (
                    select id
                    from domain_events
                    where status = 'pending' and (next_attempt_at is null or next_attempt_at <= now())
                    order by created_at
                    limit ?
                    for update skip locked
                )
                returning id, workspace_id, event_type, aggregate_type, aggregate_id, actor_id, payload, retry_count, created_at
                """,
            this::mapEvent,
            limit
        );
    }

    @Override
    public void markProcessed(UUID eventId, Instant processedAt) {
        jdbcTemplate.update(
            "update domain_events set status = 'processed', processed_at = ? where id = ?",
            Timestamp.from(processedAt),
            eventId
        );
    }

    @Override
    public void markFailed(UUID eventId, int retryCount, Instant nextAttemptAt, String errorMessage) {
        jdbcTemplate.update(
            """
                update domain_events
                set status = 'pending', retry_count = ?, next_attempt_at = ?, last_error = ?
                where id = ?
                """,
            retryCount,
            Timestamp.from(nextAttemptAt),
            errorMessage,
            eventId
        );
    }

    private DomainEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new DomainEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getString("event_type"),
            rs.getString("aggregate_type"),
            rs.getObject("aggregate_id", UUID.class),
            rs.getObject("actor_id", UUID.class),
            readPayload(rs.getString("payload")),
            rs.getInt("retry_count"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid event payload", exception);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid event payload", exception);
        }
    }
}
