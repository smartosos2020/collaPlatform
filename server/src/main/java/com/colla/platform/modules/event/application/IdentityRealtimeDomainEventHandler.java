package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentityRealtimeDomainEventHandler implements DomainEventHandler {
    static final String SECURITY_CHANGED = "identity.security.changed";
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "identity.realtime-invalidation",
        1,
        Set.of(new Subscription(SECURITY_CHANGED, 1)),
        true
    );

    private final TransactionalOutbox outbox;

    public IdentityRealtimeDomainEventHandler(@Lazy TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    @Transactional
    public void handle(EventMessage event) {
        outbox.append(
            event.workspaceId(),
            "realtime.signal.requested",
            event.aggregateType(),
            event.aggregateId(),
            event.actorId(),
            Map.of(
                "signalType", "identity.invalidated",
                "objectType", event.aggregateType(),
                "objectId", event.aggregateId().toString(),
                "sourceVersion", event.aggregateSequence(),
                "calibrationPath", calibrationPath(event)
            ),
            "realtime:" + event.eventId()
        );
    }

    private String calibrationPath(EventMessage event) {
        Object value = event.payload().get("calibrationPath");
        if (value == null || value.toString().isBlank()) {
            throw new DomainEventHandlingException.Permanent("Missing identity realtime calibrationPath");
        }
        return value.toString();
    }
}
