package com.colla.platform.modules.event.domain;

import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class DomainEventDeliveryModels {
    private DomainEventDeliveryModels() {
    }

    public enum FailureKind {
        TRANSIENT,
        PERMANENT,
        UNKNOWN
    }

    public record EventDelivery(
        UUID id,
        DomainEvent event,
        String handlerKey,
        int handlerVersion,
        boolean orderedByAggregate,
        String status,
        int attemptCount,
        String workerId,
        long fencingToken,
        Instant claimedAt,
        Instant leaseUntil,
        Instant nextAttemptAt,
        FailureKind failureKind,
        String errorFingerprint,
        String lastError
    ) {
    }

    public record FailureDecision(
        FailureKind kind,
        boolean deadLetter,
        Instant nextAttemptAt,
        String errorSummary,
        String errorFingerprint
    ) {
    }

    public record DeadLetter(
        UUID deliveryId,
        UUID workspaceId,
        UUID eventId,
        String eventType,
        int eventVersion,
        String handlerKey,
        int handlerVersion,
        int attemptCount,
        FailureKind failureKind,
        String errorFingerprint,
        String lastError,
        Instant deadLetteredAt
    ) {
    }

    public record DeliveryResult(boolean accepted, UUID receiptId, boolean receiptCreated, Map<String, Object> result) {
        public DeliveryResult {
            result = result == null ? Map.of() : Map.copyOf(result);
        }
    }

    public record DeliveryBacklogStats(
        long pending,
        long processing,
        long expiredLeases,
        long retries,
        long deadLetters,
        long oldestPendingAgeSeconds
    ) {
    }
}
