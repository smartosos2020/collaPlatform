package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository.AppendResult;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalOutboxAdapter implements TransactionalOutbox {
    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxAdapter.class);
    private final DomainEventRepository eventRepository;
    private final EventEnvelopePolicy envelopePolicy;
    private final DomainEventDeliveryMaterializer deliveryMaterializer;

    public TransactionalOutboxAdapter(
        DomainEventRepository eventRepository,
        EventEnvelopePolicy envelopePolicy,
        DomainEventDeliveryMaterializer deliveryMaterializer
    ) {
        this.eventRepository = eventRepository;
        this.envelopePolicy = envelopePolicy;
        this.deliveryMaterializer = deliveryMaterializer;
    }

    @Override
    @Transactional
    public UUID append(EventEnvelope event) {
        EventEnvelope normalized = envelopePolicy.normalize(event);
        AppendResult result = eventRepository.appendEnvelope(normalized);
        eventRepository.findById(result.eventId()).ifPresent(deliveryMaterializer::materialize);
        log.info(
            "domain_event_appended eventId={} workspaceId={} eventType={} eventVersion={} aggregateType={} aggregateId={} correlationId={} created={}",
            result.eventId(),
            normalized.workspaceId(),
            normalized.eventType(),
            normalized.eventVersion(),
            normalized.aggregateType(),
            normalized.aggregateId(),
            normalized.correlationId(),
            result.created()
        );
        return result.eventId();
    }
}
