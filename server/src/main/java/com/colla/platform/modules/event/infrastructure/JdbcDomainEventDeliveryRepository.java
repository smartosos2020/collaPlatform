package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeadLetter;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeliveryResult;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeliveryBacklogStats;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.EventDelivery;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.FailureDecision;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.FailureKind;
import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcDomainEventDeliveryRepository implements DomainEventDeliveryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDomainEventDeliveryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<EventDelivery> claim(String workerId, int limit, Instant now, Duration leaseDuration) {
        Instant leaseUntil = now.plus(leaseDuration);
        return jdbcTemplate.query(
            """
                with candidates as (
                    select delivery.id
                    from domain_event_handler_deliveries delivery
                    join domain_events event on event.id = delivery.event_id
                    where delivery.status = 'pending'
                      and (delivery.next_attempt_at is null or delivery.next_attempt_at <= ?)
                      and (
                          not delivery.ordered_by_aggregate
                          or not exists (
                              select 1
                              from domain_event_handler_deliveries prior_delivery
                              join domain_events prior_event on prior_event.id = prior_delivery.event_id
                              where prior_event.workspace_id = event.workspace_id
                                and prior_event.aggregate_type = event.aggregate_type
                                and prior_event.aggregate_id = event.aggregate_id
                                and prior_event.aggregate_sequence < event.aggregate_sequence
                                and prior_delivery.handler_key = delivery.handler_key
                                and prior_delivery.handler_version = delivery.handler_version
                                and prior_delivery.status in ('pending', 'processing')
                          )
                      )
                    order by event.occurred_at, event.aggregate_sequence, delivery.created_at
                    limit ?
                    for update of delivery skip locked
                ),
                claimed as (
                    update domain_event_handler_deliveries delivery
                    set status = 'processing',
                        worker_id = ?,
                        claimed_at = ?,
                        heartbeat_at = ?,
                        lease_until = ?,
                        attempt_count = delivery.attempt_count + 1,
                        fencing_token = delivery.fencing_token + 1,
                        updated_at = ?
                    from candidates
                    where delivery.id = candidates.id
                    returning delivery.*
                )
                select claimed.id as delivery_id,
                       claimed.handler_key, claimed.handler_version, claimed.ordered_by_aggregate,
                       claimed.status as delivery_status, claimed.attempt_count, claimed.worker_id,
                       claimed.fencing_token, claimed.claimed_at, claimed.lease_until,
                       claimed.next_attempt_at, claimed.failure_kind, claimed.error_fingerprint,
                       claimed.last_error,
                       event.id as event_id, event.workspace_id, event.event_type, event.event_version,
                       event.aggregate_type, event.aggregate_id, event.aggregate_sequence,
                       event.actor_id, event.idempotency_key, event.correlation_id, event.causation_id,
                       event.occurred_at, event.payload, event.retry_count, event.created_at
                from claimed
                join domain_events event on event.id = claimed.event_id
                order by event.occurred_at, event.aggregate_sequence, claimed.created_at
                """,
            this::mapDelivery,
            Timestamp.from(now),
            limit,
            workerId,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(leaseUntil),
            Timestamp.from(now)
        );
    }

    @Override
    public boolean heartbeat(UUID deliveryId, String workerId, long fencingToken, Instant now, Instant leaseUntil) {
        return jdbcTemplate.update(
            """
                update domain_event_handler_deliveries
                set heartbeat_at = ?, lease_until = ?, updated_at = ?
                where id = ? and status = 'processing' and worker_id = ?
                  and fencing_token = ? and lease_until >= ?
                """,
            Timestamp.from(now),
            Timestamp.from(leaseUntil),
            Timestamp.from(now),
            deliveryId,
            workerId,
            fencingToken,
            Timestamp.from(now)
        ) == 1;
    }

    @Override
    public boolean release(EventDelivery delivery, Instant now, String reason) {
        String summary = reason == null ? "Worker released delivery" : reason.substring(0, Math.min(reason.length(), 2048));
        return jdbcTemplate.update(
            """
                update domain_event_handler_deliveries
                set status = 'pending', worker_id = null, lease_until = null, heartbeat_at = null,
                    next_attempt_at = ?, failure_kind = 'TRANSIENT', last_error = ?, updated_at = ?
                where id = ? and status = 'processing' and worker_id = ? and fencing_token = ?
                """,
            Timestamp.from(now),
            summary,
            Timestamp.from(now),
            delivery.id(),
            delivery.workerId(),
            delivery.fencingToken()
        ) == 1;
    }

    @Override
    public int recoverExpired(Instant now) {
        return jdbcTemplate.update(
            """
                update domain_event_handler_deliveries
                set status = 'pending',
                    worker_id = null,
                    lease_until = null,
                    heartbeat_at = null,
                    next_attempt_at = ?,
                    failure_kind = 'TRANSIENT',
                    last_error = coalesce(last_error, 'Lease expired before completion'),
                    updated_at = ?
                where status = 'processing' and lease_until < ?
                """,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    @Override
    public DeliveryBacklogStats stats(Instant now) {
        return jdbcTemplate.queryForObject(
            """
                select count(*) filter (where delivery.status = 'pending') as pending,
                       count(*) filter (where delivery.status = 'processing') as processing,
                       count(*) filter (where delivery.status = 'processing' and delivery.lease_until < ?) as expired,
                       count(*) filter (where delivery.status = 'pending' and delivery.attempt_count > 0) as retries,
                       count(*) filter (where delivery.status = 'dead_letter') as dead_letters,
                       coalesce(greatest(0, extract(epoch from (? - min(event.occurred_at)
                           filter (where delivery.status = 'pending')))), 0)::bigint as oldest_age_seconds
                from domain_event_handler_deliveries delivery
                join domain_events event on event.id = delivery.event_id
                """,
            (rs, rowNum) -> new DeliveryBacklogStats(
                rs.getLong("pending"),
                rs.getLong("processing"),
                rs.getLong("expired"),
                rs.getLong("retries"),
                rs.getLong("dead_letters"),
                rs.getLong("oldest_age_seconds")
            ),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    @Override
    @Transactional
    public DeliveryResult complete(EventDelivery delivery, Map<String, Object> result, Instant completedAt) {
        boolean locked = !jdbcTemplate.query(
            """
                select id
                from domain_event_handler_deliveries
                where id = ? and status = 'processing' and worker_id = ?
                  and fencing_token = ? and lease_until >= ?
                for update
                """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            delivery.id(),
            delivery.workerId(),
            delivery.fencingToken(),
            Timestamp.from(completedAt)
        ).isEmpty();
        if (!locked) {
            return new DeliveryResult(false, null, false, Map.of());
        }
        Map<String, Object> safeResult = result == null ? Map.of() : Map.copyOf(result);
        UUID candidateReceiptId = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
            """
                insert into domain_event_handler_receipts
                    (id, workspace_id, event_id, delivery_id, handler_key, handler_version, result, completed_at)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                on conflict (event_id, handler_key, handler_version) do nothing
                """,
            candidateReceiptId,
            delivery.event().workspaceId(),
            delivery.event().id(),
            delivery.id(),
            delivery.handlerKey(),
            delivery.handlerVersion(),
            writeJson(safeResult),
            Timestamp.from(completedAt)
        );
        ReceiptRow receipt = jdbcTemplate.queryForObject(
            """
                select id, result
                from domain_event_handler_receipts
                where event_id = ? and handler_key = ? and handler_version = ?
                """,
            (rs, rowNum) -> new ReceiptRow(rs.getObject("id", UUID.class), readJson(rs.getString("result"))),
            delivery.event().id(),
            delivery.handlerKey(),
            delivery.handlerVersion()
        );
        int updated = jdbcTemplate.update(
            """
                update domain_event_handler_deliveries
                set status = 'processed', processed_at = ?, lease_until = null,
                    heartbeat_at = ?, updated_at = ?, last_error = null
                where id = ? and status = 'processing' and worker_id = ? and fencing_token = ?
                """,
            Timestamp.from(completedAt),
            Timestamp.from(completedAt),
            Timestamp.from(completedAt),
            delivery.id(),
            delivery.workerId(),
            delivery.fencingToken()
        );
        if (updated != 1) {
            throw new IllegalStateException("Delivery ownership changed while completing " + delivery.id());
        }
        refreshEventCompletion(delivery.event().id(), completedAt);
        return new DeliveryResult(true, receipt.id(), inserted == 1, receipt.result());
    }

    @Override
    @Transactional
    public boolean fail(EventDelivery delivery, FailureDecision decision, Instant failedAt) {
        String targetStatus = decision.deadLetter() ? "dead_letter" : "pending";
        int updated = jdbcTemplate.update(
            """
                update domain_event_handler_deliveries
                set status = ?,
                    worker_id = null,
                    lease_until = null,
                    heartbeat_at = null,
                    next_attempt_at = ?,
                    failure_kind = ?,
                    error_fingerprint = ?,
                    last_error = ?,
                    dead_lettered_at = ?,
                    updated_at = ?
                where id = ? and status = 'processing' and worker_id = ?
                  and fencing_token = ? and lease_until >= ?
                """,
            targetStatus,
            timestamp(decision.nextAttemptAt()),
            decision.kind().name(),
            decision.errorFingerprint(),
            decision.errorSummary(),
            decision.deadLetter() ? Timestamp.from(failedAt) : null,
            Timestamp.from(failedAt),
            delivery.id(),
            delivery.workerId(),
            delivery.fencingToken(),
            Timestamp.from(failedAt)
        );
        if (updated == 1) {
            refreshEventCompletion(delivery.event().id(), failedAt);
        }
        return updated == 1;
    }

    @Override
    public List<DeadLetter> findDeadLetters(UUID workspaceId, String handlerKey, int limit) {
        String handlerClause = handlerKey == null || handlerKey.isBlank() ? "" : " and delivery.handler_key = ? ";
        String sql = """
            select delivery.id, delivery.workspace_id, delivery.event_id, event.event_type, event.event_version,
                   delivery.handler_key, delivery.handler_version, delivery.attempt_count,
                   delivery.failure_kind, delivery.error_fingerprint, delivery.last_error, delivery.dead_lettered_at
            from domain_event_handler_deliveries delivery
            join domain_events event on event.id = delivery.event_id
            where delivery.workspace_id = ? and delivery.status = 'dead_letter'
            """ + handlerClause + " order by delivery.dead_lettered_at desc limit ?";
        return handlerClause.isEmpty()
            ? jdbcTemplate.query(sql, this::mapDeadLetter, workspaceId, limit)
            : jdbcTemplate.query(sql, this::mapDeadLetter, workspaceId, handlerKey, limit);
    }

    @Override
    public Optional<DeadLetter> findDeadLetter(UUID workspaceId, UUID deliveryId) {
        return jdbcTemplate.query(
            """
                select delivery.id, delivery.workspace_id, delivery.event_id, event.event_type, event.event_version,
                       delivery.handler_key, delivery.handler_version, delivery.attempt_count,
                       delivery.failure_kind, delivery.error_fingerprint, delivery.last_error,
                       delivery.dead_lettered_at
                from domain_event_handler_deliveries delivery
                join domain_events event on event.id = delivery.event_id
                where delivery.workspace_id = ? and delivery.id = ? and delivery.status = 'dead_letter'
                """,
            this::mapDeadLetter,
            workspaceId,
            deliveryId
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public boolean replay(UUID workspaceId, UUID deliveryId, UUID actorId, String reason, Instant now) {
        return transitionDeadLetter(workspaceId, deliveryId, actorId, reason, now, "replay");
    }

    @Override
    @Transactional
    public boolean abandon(UUID workspaceId, UUID deliveryId, UUID actorId, String reason, Instant now) {
        return transitionDeadLetter(workspaceId, deliveryId, actorId, reason, now, "abandon");
    }

    @Override
    public void refreshEventCompletion(UUID eventId, Instant completedAt) {
        jdbcTemplate.update(
            """
                update domain_events event
                set status = 'processed', processed_at = ?
                where event.id = ?
                  and exists (
                      select 1 from domain_event_handler_deliveries delivery
                      where delivery.event_id = event.id
                  )
                  and not exists (
                      select 1 from domain_event_handler_deliveries delivery
                      where delivery.event_id = event.id
                        and delivery.status in ('pending', 'processing')
                  )
                """,
            Timestamp.from(completedAt),
            eventId
        );
    }

    private boolean transitionDeadLetter(
        UUID workspaceId,
        UUID deliveryId,
        UUID actorId,
        String reason,
        Instant now,
        String action
    ) {
        TransitionRow row = jdbcTemplate.query(
            """
                select event_id, status, attempt_count
                from domain_event_handler_deliveries
                where workspace_id = ? and id = ? and status = 'dead_letter'
                for update
                """,
            (rs, rowNum) -> new TransitionRow(
                rs.getObject("event_id", UUID.class),
                rs.getString("status"),
                rs.getInt("attempt_count")
            ),
            workspaceId,
            deliveryId
        ).stream().findFirst().orElse(null);
        if (row == null) {
            return false;
        }
        if ("replay".equals(action)) {
            jdbcTemplate.update(
                """
                    update domain_event_handler_deliveries
                    set status = 'pending', next_attempt_at = ?, failure_kind = null,
                        error_fingerprint = null, last_error = null, dead_lettered_at = null,
                        replay_count = replay_count + 1, replayed_at = ?, replayed_by = ?,
                        replay_reason = ?, abandoned_at = null, updated_at = ?
                    where workspace_id = ? and id = ?
                    """,
                Timestamp.from(now),
                Timestamp.from(now),
                actorId,
                reason,
                Timestamp.from(now),
                workspaceId,
                deliveryId
            );
            jdbcTemplate.update(
                "update domain_events set status = 'pending', processed_at = null where id = ?",
                row.eventId()
            );
        } else {
            jdbcTemplate.update(
                """
                    update domain_event_handler_deliveries
                    set status = 'abandoned', abandoned_at = ?, replayed_by = ?,
                        replay_reason = ?, updated_at = ?
                    where workspace_id = ? and id = ?
                    """,
                Timestamp.from(now),
                actorId,
                reason,
                Timestamp.from(now),
                workspaceId,
                deliveryId
            );
            refreshEventCompletion(row.eventId(), now);
        }
        jdbcTemplate.update(
            """
                insert into domain_event_delivery_replays
                    (id, workspace_id, event_id, delivery_id, action, actor_id, reason,
                     previous_status, previous_attempt_count, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            workspaceId,
            row.eventId(),
            deliveryId,
            action,
            actorId,
            reason,
            row.status(),
            row.attemptCount(),
            Timestamp.from(now)
        );
        return true;
    }

    private EventDelivery mapDelivery(ResultSet rs, int rowNum) throws SQLException {
        DomainEvent event = new DomainEvent(
            rs.getObject("event_id", UUID.class),
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
            readJson(rs.getString("payload")),
            rs.getInt("retry_count"),
            rs.getTimestamp("created_at").toInstant()
        );
        String failureKind = rs.getString("failure_kind");
        return new EventDelivery(
            rs.getObject("delivery_id", UUID.class),
            event,
            rs.getString("handler_key"),
            rs.getInt("handler_version"),
            rs.getBoolean("ordered_by_aggregate"),
            rs.getString("delivery_status"),
            rs.getInt("attempt_count"),
            rs.getString("worker_id"),
            rs.getLong("fencing_token"),
            instant(rs.getTimestamp("claimed_at")),
            instant(rs.getTimestamp("lease_until")),
            instant(rs.getTimestamp("next_attempt_at")),
            failureKind == null ? null : FailureKind.valueOf(failureKind),
            rs.getString("error_fingerprint"),
            rs.getString("last_error")
        );
    }

    private DeadLetter mapDeadLetter(ResultSet rs, int rowNum) throws SQLException {
        String failureKind = rs.getString("failure_kind");
        return new DeadLetter(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getString("event_type"),
            rs.getInt("event_version"),
            rs.getString("handler_key"),
            rs.getInt("handler_version"),
            rs.getInt("attempt_count"),
            failureKind == null ? FailureKind.UNKNOWN : FailureKind.valueOf(failureKind),
            rs.getString("error_fingerprint"),
            rs.getString("last_error"),
            instant(rs.getTimestamp("dead_lettered_at"))
        );
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid delivery result", exception);
        }
    }

    private Map<String, Object> readJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid delivery JSON", exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record ReceiptRow(UUID id, Map<String, Object> result) {
    }

    private record TransitionRow(UUID eventId, String status, int attemptCount) {
    }
}
