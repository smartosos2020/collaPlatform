package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface DomainEventRepository {
    UUID append(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        String idempotencyKey
    );

    AppendResult appendEnvelope(EventEnvelope event);

    Optional<DomainEvent> findById(UUID eventId);

    int ensureDeliveries(UUID workspaceId, UUID eventId, List<Descriptor> descriptors);

    ReceiptResult recordReceipt(
        UUID workspaceId,
        UUID eventId,
        UUID deliveryId,
        String handlerKey,
        int handlerVersion,
        Map<String, Object> result,
        Instant completedAt
    );

    Optional<ReceiptResult> findReceipt(UUID eventId, String handlerKey, int handlerVersion);

    List<DomainEvent> claimPending(int limit);

    void markProcessed(UUID eventId, Instant processedAt);

    void markFailed(UUID eventId, int retryCount, Instant nextAttemptAt, String errorMessage);

    record AppendResult(UUID eventId, boolean created) {
    }

    record ReceiptResult(UUID receiptId, boolean created, Map<String, Object> result, Instant completedAt) {
        public ReceiptResult {
            result = result == null ? Map.of() : Map.copyOf(result);
        }
    }
}
