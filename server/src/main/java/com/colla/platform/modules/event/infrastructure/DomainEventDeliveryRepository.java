package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeadLetter;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeliveryResult;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeliveryBacklogStats;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerCursor;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerSlice;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityRunSummary;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.EventDelivery;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.FailureDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface DomainEventDeliveryRepository {
    Instant currentTime();

    List<EventDelivery> claim(String workerId, int limit, Instant now, Duration leaseDuration);

    boolean heartbeat(UUID deliveryId, String workerId, long fencingToken, Instant now, Instant leaseUntil);

    boolean release(EventDelivery delivery, Instant now, String reason);

    int recoverExpired(Instant now, int limit);

    DeliveryResult complete(EventDelivery delivery, Map<String, Object> result, Instant completedAt);

    boolean fail(EventDelivery delivery, FailureDecision decision, Instant failedAt);

    List<DeadLetter> findDeadLetters(UUID workspaceId, String handlerKey, int limit);

    Optional<DeadLetter> findDeadLetter(UUID workspaceId, UUID deliveryId);

    boolean replay(UUID workspaceId, UUID deliveryId, UUID actorId, String reason, Instant now);

    boolean abandon(UUID workspaceId, UUID deliveryId, UUID actorId, String reason, Instant now);

    void refreshEventCompletion(UUID eventId, Instant completedAt);

    DeliveryBacklogStats stats(Instant now);

    void lockCapacityRun(UUID workspaceId, UUID runId);

    long countCapacityProbeEvents(UUID workspaceId, UUID runId);

    CapacityRunSummary capacityRunSummary(UUID workspaceId, UUID runId, String handlerKey, Instant now);

    CapacityLedgerSlice capacityRunLedger(
        UUID workspaceId,
        UUID runId,
        String handlerKey,
        CapacityLedgerCursor cursor,
        int limit
    );
}
