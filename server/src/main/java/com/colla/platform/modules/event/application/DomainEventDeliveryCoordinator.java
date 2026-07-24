package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeliveryResult;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeliveryBacklogStats;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.EventDelivery;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.FailureDecision;
import com.colla.platform.modules.event.infrastructure.DomainEventDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DomainEventDeliveryCoordinator {
    private static final Logger log = LoggerFactory.getLogger(DomainEventDeliveryCoordinator.class);
    private final DomainEventDeliveryRepository repository;
    private final DomainEventFailureClassifier failureClassifier;
    private final DomainEventDeliveryProperties properties;

    public DomainEventDeliveryCoordinator(
        DomainEventDeliveryRepository repository,
        DomainEventFailureClassifier failureClassifier,
        DomainEventDeliveryProperties properties
    ) {
        this.repository = repository;
        this.failureClassifier = failureClassifier;
        this.properties = properties;
        properties.validate();
    }

    public List<EventDelivery> claim(String workerId, int requestedLimit, Instant now) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 128) {
            throw new IllegalArgumentException("Worker id must contain 1 to 128 characters");
        }
        int limit = Math.min(Math.max(requestedLimit, 1), properties.getMaxClaimBatch());
        List<EventDelivery> deliveries = repository.claim(workerId, limit, now, properties.getLeaseDuration());
        log.info("domain_event_delivery_claim workerId={} requested={} claimed={}", workerId, limit, deliveries.size());
        return deliveries;
    }

    public boolean heartbeat(EventDelivery delivery, Instant now) {
        Instant executionDeadline = delivery.claimedAt().plus(properties.getMaxExecutionDuration());
        if (!now.isBefore(executionDeadline)) {
            log.warn(
                "domain_event_delivery_budget_exhausted deliveryId={} eventId={} handlerKey={} workerId={} fencingToken={}",
                delivery.id(),
                delivery.event().id(),
                delivery.handlerKey(),
                delivery.workerId(),
                delivery.fencingToken()
            );
            return false;
        }
        Instant leaseUntil = now.plus(properties.getLeaseDuration());
        if (leaseUntil.isAfter(executionDeadline)) {
            leaseUntil = executionDeadline;
        }
        return repository.heartbeat(
            delivery.id(),
            delivery.workerId(),
            delivery.fencingToken(),
            now,
            leaseUntil
        );
    }

    public boolean release(EventDelivery delivery, Instant now, String reason) {
        boolean released = repository.release(delivery, now, reason);
        log.info(
            "domain_event_delivery_release deliveryId={} eventId={} handlerKey={} workerId={} fencingToken={} accepted={}",
            delivery.id(), delivery.event().id(), delivery.handlerKey(), delivery.workerId(),
            delivery.fencingToken(), released
        );
        return released;
    }

    public DeliveryResult complete(EventDelivery delivery, Map<String, Object> result, Instant now) {
        DeliveryResult completed = repository.complete(delivery, result, now);
        log.info(
            "domain_event_delivery_complete deliveryId={} eventId={} handlerKey={} workerId={} fencingToken={} accepted={} receiptId={}",
            delivery.id(),
            delivery.event().id(),
            delivery.handlerKey(),
            delivery.workerId(),
            delivery.fencingToken(),
            completed.accepted(),
            completed.receiptId()
        );
        return completed;
    }

    public boolean fail(EventDelivery delivery, Throwable failure, Instant now) {
        FailureDecision decision = failureClassifier.classify(delivery, failure, now);
        boolean accepted = repository.fail(delivery, decision, now);
        log.warn(
            "domain_event_delivery_failed deliveryId={} eventId={} handlerKey={} workerId={} fencingToken={} kind={} deadLetter={} fingerprint={} accepted={}",
            delivery.id(),
            delivery.event().id(),
            delivery.handlerKey(),
            delivery.workerId(),
            delivery.fencingToken(),
            decision.kind(),
            decision.deadLetter(),
            decision.errorFingerprint(),
            accepted
        );
        return accepted;
    }

    public int recoverExpired(Instant now) {
        int recovered = repository.recoverExpired(now);
        if (recovered > 0) {
            log.warn("domain_event_delivery_leases_recovered count={}", recovered);
        }
        return recovered;
    }

    public DeliveryBacklogStats stats(Instant now) {
        return repository.stats(now);
    }
}
