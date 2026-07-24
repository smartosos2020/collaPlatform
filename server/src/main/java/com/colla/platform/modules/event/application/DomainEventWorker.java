package com.colla.platform.modules.event.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.EventDelivery;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Compatibility entry point for deterministic integration tests. Production
 * scheduling remains in ReliableDomainEventWorker; this class has no business
 * event branches and dispatches exclusively through the public handler registry.
 */
@Component
@ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.COMBINED})
public class DomainEventWorker {
    private static final int MAX_BATCHES = 100;
    private final ObjectProvider<ReliableDomainEventWorker> reliableWorker;
    private final DomainEventDeliveryCoordinator coordinator;
    private final DomainEventHandlerRegistry registry;

    public DomainEventWorker(
        ObjectProvider<ReliableDomainEventWorker> reliableWorker,
        DomainEventDeliveryCoordinator coordinator,
        DomainEventHandlerRegistry registry
    ) {
        this.reliableWorker = reliableWorker;
        this.coordinator = coordinator;
        this.registry = registry;
    }

    public void processPendingEvents() {
        ReliableDomainEventWorker productionWorker = reliableWorker.getIfAvailable();
        if (productionWorker != null) {
            productionWorker.pollOnce();
            return;
        }
        String workerId = "combined-test-dispatcher";
        for (int batch = 0; batch < MAX_BATCHES; batch += 1) {
            var deliveries = coordinator.claim(workerId, 100, Instant.now());
            if (deliveries.isEmpty()) {
                return;
            }
            for (EventDelivery delivery : deliveries) {
                dispatch(delivery);
            }
        }
    }

    private void dispatch(EventDelivery delivery) {
        try {
            DomainEventHandler handler = registry.require(delivery.handlerKey(), delivery.handlerVersion());
            handler.handle(toMessage(delivery));
            coordinator.complete(delivery, Map.of("workerId", delivery.workerId()), Instant.now());
        } catch (RuntimeException exception) {
            coordinator.fail(delivery, exception, Instant.now());
        }
    }

    private static EventMessage toMessage(EventDelivery delivery) {
        var event = delivery.event();
        return new EventMessage(
            event.id(),
            event.workspaceId(),
            event.eventType(),
            event.eventVersion(),
            event.aggregateType(),
            event.aggregateId(),
            event.aggregateSequence(),
            event.actorId(),
            event.idempotencyKey(),
            event.correlationId(),
            event.causationId(),
            event.occurredAt(),
            event.payload()
        );
    }
}
