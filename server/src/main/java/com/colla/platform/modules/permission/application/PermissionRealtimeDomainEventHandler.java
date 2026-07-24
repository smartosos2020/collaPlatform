package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PermissionRealtimeDomainEventHandler implements DomainEventHandler {
    static final String SECURITY_CHANGED = "permission.security.changed";
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "permission.realtime-invalidation",
        1,
        Set.of(new Subscription(SECURITY_CHANGED, 1)),
        true
    );

    private final TransactionalOutbox outbox;

    public PermissionRealtimeDomainEventHandler(@Lazy TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    @Transactional
    public void handle(EventMessage event) {
        String objectType = required(event.payload(), "objectType");
        String calibrationPath = required(event.payload(), "calibrationPath");
        outbox.append(
            event.workspaceId(),
            "realtime.signal.requested",
            objectType,
            event.aggregateId(),
            event.actorId(),
            Map.of(
                "signalType", "permission.invalidated",
                "objectType", objectType,
                "objectId", event.aggregateId().toString(),
                "sourceVersion", event.aggregateSequence(),
                "calibrationPath", calibrationPath
            ),
            "realtime:" + event.eventId()
        );
    }

    private static String required(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new DomainEventHandlingException.Permanent("Missing permission realtime " + key);
        }
        return value.toString();
    }
}
