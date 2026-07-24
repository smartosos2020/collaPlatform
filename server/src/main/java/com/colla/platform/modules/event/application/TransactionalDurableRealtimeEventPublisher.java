package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.shared.realtime.DurableRealtimeEventPublisher;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TransactionalDurableRealtimeEventPublisher implements DurableRealtimeEventPublisher {
    private final TransactionalOutbox outbox;

    public TransactionalDurableRealtimeEventPublisher(TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public void append(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        String idempotencyKey
    ) {
        outbox.append(
            workspaceId,
            eventType,
            aggregateType,
            aggregateId,
            actorId,
            payload,
            idempotencyKey
        );
    }
}
