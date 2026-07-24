package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository.ReceiptResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a handler's durable idempotency result in the same transaction as
 * the handler-owned side effect.
 */
@Service
public class DomainEventReceiptStore {
    private static final Logger log = LoggerFactory.getLogger(DomainEventReceiptStore.class);
    private final DomainEventRepository repository;

    public DomainEventReceiptStore(DomainEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReceiptResult complete(
        UUID workspaceId,
        UUID eventId,
        UUID deliveryId,
        String handlerKey,
        int handlerVersion,
        Map<String, Object> result
    ) {
        ReceiptResult receipt = repository.recordReceipt(
            workspaceId,
            eventId,
            deliveryId,
            handlerKey,
            handlerVersion,
            result,
            Instant.now()
        );
        log.info(
            "domain_event_handler_receipt eventId={} workspaceId={} deliveryId={} handlerKey={} handlerVersion={} receiptId={} created={}",
            eventId,
            workspaceId,
            deliveryId,
            handlerKey,
            handlerVersion,
            receipt.receiptId(),
            receipt.created()
        );
        return receipt;
    }
}
