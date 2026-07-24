package com.colla.platform.modules.event.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeadLetter;
import com.colla.platform.modules.event.infrastructure.DomainEventDeliveryRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DomainEventMaintenanceService {
    private final DomainEventDeliveryRepository repository;
    private final DomainEventDeliveryProperties properties;
    private final AuditLog auditLog;

    public DomainEventMaintenanceService(
        DomainEventDeliveryRepository repository,
        DomainEventDeliveryProperties properties,
        AuditLog auditLog
    ) {
        this.repository = repository;
        this.properties = properties;
        this.auditLog = auditLog;
    }

    public List<DeadLetter> inspect(CurrentUser actor, String handlerKey, int requestedLimit) {
        requireOperator(actor);
        int limit = Math.min(Math.max(requestedLimit, 1), properties.getMaxMaintenanceBatch());
        return repository.findDeadLetters(actor.workspaceId(), normalizeHandlerKey(handlerKey), limit);
    }

    @Transactional
    public DeadLetter replay(CurrentUser actor, UUID deliveryId, String reason) {
        requireOperator(actor);
        String normalizedReason = requireReason(reason);
        DeadLetter before = repository.findDeadLetter(actor.workspaceId(), deliveryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead-letter delivery not found"));
        if (!repository.replay(actor.workspaceId(), deliveryId, actor.id(), normalizedReason, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is no longer replayable");
        }
        auditLog.log(actor, "event_delivery.replayed", "event_delivery", deliveryId, Map.of(
            "eventId", before.eventId().toString(),
            "handlerKey", before.handlerKey(),
            "handlerVersion", before.handlerVersion(),
            "attemptCount", before.attemptCount(),
            "reason", normalizedReason
        ));
        return before;
    }

    @Transactional
    public DeadLetter abandon(CurrentUser actor, UUID deliveryId, String reason) {
        requireOperator(actor);
        String normalizedReason = requireReason(reason);
        DeadLetter before = repository.findDeadLetter(actor.workspaceId(), deliveryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead-letter delivery not found"));
        if (!repository.abandon(actor.workspaceId(), deliveryId, actor.id(), normalizedReason, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is no longer abandonable");
        }
        auditLog.log(actor, "event_delivery.abandoned", "event_delivery", deliveryId, Map.of(
            "eventId", before.eventId().toString(),
            "handlerKey", before.handlerKey(),
            "handlerVersion", before.handlerVersion(),
            "attemptCount", before.attemptCount(),
            "reason", normalizedReason
        ));
        return before;
    }

    private static void requireOperator(CurrentUser actor) {
        if (actor == null || (!actor.hasRole("admin") && !actor.hasPermission("admin.access"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason must contain 10 to 512 characters");
        }
        return normalized;
    }

    private static String normalizeHandlerKey(String handlerKey) {
        if (handlerKey == null || handlerKey.isBlank()) {
            return null;
        }
        String normalized = handlerKey.trim();
        if (!normalized.matches("[a-z][a-z0-9._-]{2,95}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid handler key");
        }
        return normalized;
    }
}
