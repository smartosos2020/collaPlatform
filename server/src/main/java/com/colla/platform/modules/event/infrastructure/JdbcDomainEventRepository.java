package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository.ReceiptResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcDomainEventRepository implements DomainEventRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDomainEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public UUID append(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        String idempotencyKey
    ) {
        UUID eventId = UUID.randomUUID();
        return appendEnvelope(new EventEnvelope(
            eventId,
            workspaceId,
            eventType,
            1,
            aggregateType,
            aggregateId,
            actorId,
            idempotencyKey,
            eventId,
            null,
            Instant.now(),
            payload
        )).eventId();
    }

    @Override
    @Transactional
    public AppendResult appendEnvelope(EventEnvelope event) {
        Optional<DomainEvent> existing = findExisting(event);
        if (existing.isPresent()) {
            return new AppendResult(existing.get().id(), false);
        }
        String aggregateLockKey = event.workspaceId() + ":" + event.aggregateType() + ":" + event.aggregateId();
        jdbcTemplate.queryForList(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            aggregateLockKey
        );
        Long aggregateSequence = jdbcTemplate.queryForObject(
            """
                select coalesce(max(aggregate_sequence), 0) + 1
                from domain_events
                where workspace_id = ? and aggregate_type = ? and aggregate_id = ?
                """,
            Long.class,
            event.workspaceId(),
            event.aggregateType(),
            event.aggregateId()
        );
        try {
            jdbcTemplate.update(
                """
                    insert into domain_events
                        (id, workspace_id, event_type, event_version, aggregate_type, aggregate_id,
                         aggregate_sequence, actor_id, payload, status, idempotency_key,
                         correlation_id, causation_id, occurred_at, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'pending', ?, ?, ?, ?, clock_timestamp())
                    """,
                event.eventId(),
                event.workspaceId(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateType(),
                event.aggregateId(),
                aggregateSequence,
                event.actorId(),
                writePayload(event.payload()),
                event.idempotencyKey(),
                event.correlationId(),
                event.causationId(),
                Timestamp.from(event.occurredAt())
            );
        } catch (DuplicateKeyException exception) {
            return findExisting(event)
                .map(found -> new AppendResult(found.id(), false))
                .orElseThrow(() -> exception);
        }
        return new AppendResult(event.eventId(), true);
    }

    @Override
    public Optional<DomainEvent> findById(UUID eventId) {
        return jdbcTemplate.query(
            """
                select id, workspace_id, event_type, event_version, aggregate_type, aggregate_id,
                       aggregate_sequence, actor_id, idempotency_key, correlation_id, causation_id,
                       occurred_at, payload, retry_count, created_at
                from domain_events
                where id = ?
                """,
            this::mapEvent,
            eventId
        ).stream().findFirst();
    }

    @Override
    public int ensureDeliveries(UUID workspaceId, UUID eventId, List<Descriptor> descriptors) {
        int inserted = 0;
        for (Descriptor descriptor : descriptors) {
            inserted += jdbcTemplate.update(
                """
                    insert into domain_event_handler_deliveries
                        (id, workspace_id, event_id, handler_key, handler_version,
                         ordered_by_aggregate, status, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, 'pending', clock_timestamp(), clock_timestamp())
                    on conflict (event_id, handler_key, handler_version) do nothing
                    """,
                UUID.randomUUID(),
                workspaceId,
                eventId,
                descriptor.handlerKey(),
                descriptor.handlerVersion(),
                descriptor.orderedByAggregate()
            );
        }
        return inserted;
    }

    @Override
    @Transactional
    public ReceiptResult recordReceipt(
        UUID workspaceId,
        UUID eventId,
        UUID deliveryId,
        String handlerKey,
        int handlerVersion,
        Map<String, Object> result,
        Instant completedAt
    ) {
        Optional<ReceiptResult> existing = findReceipt(eventId, handlerKey, handlerVersion);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID receiptId = UUID.randomUUID();
        Map<String, Object> safeResult = result == null ? Map.of() : Map.copyOf(result);
        try {
            jdbcTemplate.update(
                """
                    insert into domain_event_handler_receipts
                        (id, workspace_id, event_id, delivery_id, handler_key, handler_version, result, completed_at)
                    values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    """,
                receiptId,
                workspaceId,
                eventId,
                deliveryId,
                handlerKey,
                handlerVersion,
                writePayload(safeResult),
                Timestamp.from(completedAt)
            );
        } catch (DuplicateKeyException exception) {
            return findReceipt(eventId, handlerKey, handlerVersion).orElseThrow(() -> exception);
        }
        return new ReceiptResult(receiptId, true, safeResult, completedAt);
    }

    @Override
    public Optional<ReceiptResult> findReceipt(UUID eventId, String handlerKey, int handlerVersion) {
        return jdbcTemplate.query(
            """
                select id, result, completed_at
                from domain_event_handler_receipts
                where event_id = ? and handler_key = ? and handler_version = ?
                """,
            (rs, rowNum) -> new ReceiptResult(
                rs.getObject("id", UUID.class),
                false,
                readPayload(rs.getString("result")),
                rs.getTimestamp("completed_at").toInstant()
            ),
            eventId,
            handlerKey,
            handlerVersion
        ).stream().findFirst();
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
                returning id, workspace_id, event_type, event_version, aggregate_type, aggregate_id,
                          aggregate_sequence, actor_id, idempotency_key, correlation_id, causation_id,
                          occurred_at, payload, retry_count, created_at
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
            rs.getInt("event_version"),
            rs.getString("aggregate_type"),
            rs.getObject("aggregate_id", UUID.class),
            rs.getLong("aggregate_sequence"),
            rs.getObject("actor_id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getObject("correlation_id", UUID.class),
            rs.getObject("causation_id", UUID.class),
            rs.getTimestamp("occurred_at").toInstant(),
            readPayload(rs.getString("payload")),
            rs.getInt("retry_count"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private Optional<DomainEvent> findExisting(EventEnvelope event) {
        Optional<DomainEvent> byId = findById(event.eventId());
        if (byId.isPresent() || event.idempotencyKey() == null) {
            return byId;
        }
        return jdbcTemplate.query(
            """
                select id, workspace_id, event_type, event_version, aggregate_type, aggregate_id,
                       aggregate_sequence, actor_id, idempotency_key, correlation_id, causation_id,
                       occurred_at, payload, retry_count, created_at
                from domain_events
                where workspace_id = ? and idempotency_key = ?
                """,
            this::mapEvent,
            event.workspaceId(),
            event.idempotencyKey()
        ).stream().findFirst();
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
