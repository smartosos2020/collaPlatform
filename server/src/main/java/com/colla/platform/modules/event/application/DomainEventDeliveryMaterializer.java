package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DomainEventDeliveryMaterializer {
    private static final Logger log = LoggerFactory.getLogger(DomainEventDeliveryMaterializer.class);
    private final DomainEventHandlerRegistry registry;
    private final DomainEventRepository repository;

    public DomainEventDeliveryMaterializer(
        DomainEventHandlerRegistry registry,
        DomainEventRepository repository
    ) {
        this.registry = registry;
        this.repository = repository;
    }

    public int materialize(DomainEvent event) {
        List<DomainEventHandler.Descriptor> descriptors = registry
            .matching(event.eventType(), event.eventVersion())
            .stream()
            .map(DomainEventHandler::descriptor)
            .toList();
        int inserted = repository.ensureDeliveries(event.workspaceId(), event.id(), descriptors);
        if (descriptors.isEmpty()) {
            log.warn(
                "domain_event_without_handler eventId={} workspaceId={} eventType={} eventVersion={} correlationId={} isolation=observable_pending",
                event.id(),
                event.workspaceId(),
                event.eventType(),
                event.eventVersion(),
                event.correlationId()
            );
        } else {
            log.info(
                "domain_event_deliveries_materialized eventId={} workspaceId={} eventType={} eventVersion={} correlationId={} matched={} inserted={}",
                event.id(),
                event.workspaceId(),
                event.eventType(),
                event.eventVersion(),
                event.correlationId(),
                descriptors.size(),
                inserted
            );
        }
        return inserted;
    }
}
