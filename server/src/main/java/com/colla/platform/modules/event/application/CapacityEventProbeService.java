package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerPage;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerCursor;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerSlice;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityProbeAcknowledgement;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityRealtimeExpectation;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityRunSummary;
import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.event.infrastructure.DomainEventDeliveryRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CapacityEventProbeService {
    private static final String HANDLER_KEY = "realtime.signal";
    private static final String EVENT_TYPE = "realtime.signal.requested";
    private static final String CURSOR_SCHEMA = "capacity-ledger-membership-watermark.v1";
    private final CapacityEventProbeProperties properties;
    private final TransactionalOutbox outbox;
    private final DomainEventRepository eventRepository;
    private final DomainEventDeliveryRepository deliveryRepository;

    public CapacityEventProbeService(
        CapacityEventProbeProperties properties,
        TransactionalOutbox outbox,
        DomainEventRepository eventRepository,
        DomainEventDeliveryRepository deliveryRepository
    ) {
        this.properties = properties;
        this.outbox = outbox;
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public CapacityProbeAcknowledgement produce(
        CurrentUser actor,
        UUID runId,
        String suppliedSecret,
        String aggregateKey,
        String requestKey
    ) {
        requireAccess(actor, suppliedSecret);
        String safeAggregateKey = requireKey(aggregateKey, "aggregateKey");
        String safeRequestKey = requireKey(requestKey, "requestKey");
        validateLimits();

        deliveryRepository.lockCapacityRun(actor.workspaceId(), runId);
        UUID eventId = namedUuid("capacity-event", actor.workspaceId(), runId, safeRequestKey);
        UUID aggregateId = namedUuid("capacity-aggregate", actor.workspaceId(), runId, safeAggregateKey);
        String calibrationPath = "/api/admin/event-deliveries/capacity-runs/" + runId + "/ledger";
        Map<String, Object> payload = probePayload(aggregateId, calibrationPath);
        String idempotencyKey = "capacity.probe:" + runId + ":" + safeRequestKey;
        DomainEvent existing = eventRepository.findById(eventId).orElse(null);
        if (existing != null) {
            requireSameSemantics(
                existing,
                actor.workspaceId(),
                runId,
                aggregateId,
                idempotencyKey,
                payload
            );
            return acknowledgement(existing);
        }

        if (deliveryRepository.countCapacityProbeEvents(actor.workspaceId(), runId) >= properties.getMaxEventsPerRun()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Capacity run event limit reached");
        }

        UUID appendedId = outbox.append(new EventEnvelope(
            eventId,
            actor.workspaceId(),
            EVENT_TYPE,
            1,
            "capacity_probe",
            aggregateId,
            actor.id(),
            idempotencyKey,
            runId,
            null,
            Instant.now(),
            payload
        ));
        DomainEvent event = eventRepository.findById(appendedId)
            .orElseThrow(() -> new IllegalStateException("Capacity probe event was not persisted"));
        requireSameSemantics(
            event,
            actor.workspaceId(),
            runId,
            aggregateId,
            idempotencyKey,
            payload
        );
        return acknowledgement(event);
    }

    public CapacityRunSummary summary(CurrentUser actor, UUID runId, String suppliedSecret) {
        requireAccess(actor, suppliedSecret);
        return deliveryRepository.capacityRunSummary(actor.workspaceId(), runId, HANDLER_KEY, Instant.now());
    }

    public CapacityLedgerPage ledger(
        CurrentUser actor,
        UUID runId,
        String suppliedSecret,
        String cursor,
        int requestedLimit
    ) {
        requireAccess(actor, suppliedSecret);
        validateLimits();
        int limit = Math.min(Math.max(requestedLimit, 1), properties.getMaxPageSize());
        CapacityLedgerSlice slice = deliveryRepository.capacityRunLedger(
            actor.workspaceId(),
            runId,
            HANDLER_KEY,
            decodeCursor(cursor, actor.workspaceId(), runId, HANDLER_KEY),
            limit
        );
        return new CapacityLedgerPage(
            slice.entries(),
            encodeCursor(slice.nextCursor(), actor.workspaceId(), runId, HANDLER_KEY)
        );
    }

    private void requireAccess(CurrentUser actor, String suppliedSecret) {
        if (actor == null || (!actor.hasRole("admin") && !actor.hasPermission("admin.access"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capacity probe is disabled");
        }
        byte[] expected = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        byte[] supplied = (suppliedSecret == null ? "" : suppliedSecret).getBytes(StandardCharsets.UTF_8);
        if (expected.length < 32 || !MessageDigest.isEqual(expected, supplied)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Capacity probe authorization failed");
        }
    }

    private void validateLimits() {
        if (properties.getMaxEventsPerRun() < 1 || properties.getMaxEventsPerRun() > 1_000_000
            || properties.getMaxPageSize() < 1 || properties.getMaxPageSize() > 10_000) {
            throw new IllegalStateException("Capacity probe limits are invalid");
        }
    }

    private static String requireKey(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is invalid");
        }
        return normalized;
    }

    private static CapacityProbeAcknowledgement acknowledgement(DomainEvent event) {
        UUID sideEffectId = UUID.nameUUIDFromBytes(
            ("realtime:" + event.id()).getBytes(StandardCharsets.UTF_8)
        );
        UUID runId = event.correlationId();
        String sequenceKey = "capacity_probe:" + event.aggregateId();
        String calibrationPath = "/api/admin/event-deliveries/capacity-runs/" + runId + "/ledger";
        return new CapacityProbeAcknowledgement(
            event.id(),
            sideEffectId,
            event.aggregateId(),
            event.aggregateSequence(),
            new CapacityRealtimeExpectation(
                sideEffectId,
                event.workspaceId(),
                "object",
                sequenceKey,
                event.aggregateSequence(),
                event.aggregateId(),
                calibrationPath
            )
        );
    }

    private static UUID namedUuid(String namespace, UUID workspaceId, UUID runId, String key) {
        return UUID.nameUUIDFromBytes(
            (namespace + ":" + workspaceId + ":" + runId + ":" + key).getBytes(StandardCharsets.UTF_8)
        );
    }

    CapacityLedgerCursor decodeCursor(String cursor, UUID workspaceId, UUID runId, String handlerKey) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] tokenParts = cursor.split("\\.", -1);
            if (tokenParts.length != 2) {
                throw new IllegalArgumentException("wrong token part count");
            }
            byte[] body = Base64.getUrlDecoder().decode(tokenParts[0]);
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
            if (!MessageDigest.isEqual(signCursor(body), suppliedSignature)) {
                throw new IllegalArgumentException("invalid signature");
            }
            String[] fields = new String(body, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 8) {
                throw new IllegalArgumentException("wrong field count");
            }
            if (!CURSOR_SCHEMA.equals(fields[0])
                || !workspaceId.equals(UUID.fromString(fields[1]))
                || !runId.equals(UUID.fromString(fields[2]))
                || !handlerKey.equals(fields[3])) {
                throw new IllegalArgumentException("cursor binding mismatch");
            }
            CapacityLedgerCursor parsed = new CapacityLedgerCursor(
                Instant.parse(fields[4]),
                UUID.fromString(fields[5]),
                Instant.parse(fields[6]),
                UUID.fromString(fields[7])
            );
            if (parsed.afterCreatedAt().isAfter(parsed.membershipWatermarkCreatedAt())
                || (parsed.afterCreatedAt().equals(parsed.membershipWatermarkCreatedAt())
                    && parsed.afterEventId().compareTo(parsed.membershipWatermarkEventId()) > 0)) {
                throw new IllegalArgumentException("cursor exceeds membership watermark");
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity ledger cursor is invalid");
        }
    }

    String encodeCursor(
        CapacityLedgerCursor cursor,
        UUID workspaceId,
        UUID runId,
        String handlerKey
    ) {
        if (cursor == null) {
            return null;
        }
        String value = String.join(
            "|",
            CURSOR_SCHEMA,
            workspaceId.toString(),
            runId.toString(),
            handlerKey,
            cursor.afterCreatedAt().toString(),
            cursor.afterEventId().toString(),
            cursor.membershipWatermarkCreatedAt().toString(),
            cursor.membershipWatermarkEventId().toString()
        );
        byte[] body = value.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(body) + "." + encoder.encodeToString(signCursor(body));
    }

    private static Map<String, Object> probePayload(UUID aggregateId, String calibrationPath) {
        return Map.of(
            "signalType", "capacity.probe",
            "objectType", "capacity_probe",
            "objectId", aggregateId.toString(),
            "sequenceScope", "OBJECT",
            "sequenceKey", "capacity_probe:" + aggregateId,
            "calibrationPath", calibrationPath,
            "safePayload", Map.of()
        );
    }

    private static void requireSameSemantics(
        DomainEvent existing,
        UUID workspaceId,
        UUID runId,
        UUID aggregateId,
        String idempotencyKey,
        Map<String, Object> payload
    ) {
        boolean same = workspaceId.equals(existing.workspaceId())
            && EVENT_TYPE.equals(existing.eventType())
            && existing.eventVersion() == 1
            && "capacity_probe".equals(existing.aggregateType())
            && aggregateId.equals(existing.aggregateId())
            && idempotencyKey.equals(existing.idempotencyKey())
            && runId.equals(existing.correlationId())
            && payload.equals(existing.payload());
        if (!same) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Capacity probe requestKey conflicts with existing aggregateKey or payload"
            );
        }
    }

    private byte[] signCursor(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Capacity ledger cursor signing is unavailable", exception);
        }
    }
}
