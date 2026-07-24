package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentitySecurityChangePublisher {
    private final TransactionalOutbox outbox;

    public IdentitySecurityChangePublisher(TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    public void publish(
        UUID workspaceId,
        UUID actorId,
        String aggregateType,
        UUID aggregateId,
        String calibrationPath,
        String mutationKey
    ) {
        outbox.append(
            workspaceId,
            IdentityRealtimeDomainEventHandler.SECURITY_CHANGED,
            aggregateType,
            aggregateId,
            actorId,
            Map.of("calibrationPath", calibrationPath),
            "identity.security:" + UUID.nameUUIDFromBytes(mutationKey.getBytes(StandardCharsets.UTF_8))
        );
    }
}
