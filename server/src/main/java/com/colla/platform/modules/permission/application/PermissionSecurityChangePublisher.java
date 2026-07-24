package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PermissionSecurityChangePublisher {
    private final TransactionalOutbox outbox;

    public PermissionSecurityChangePublisher(TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    public void publish(
        UUID workspaceId,
        UUID actorId,
        String aggregateType,
        UUID aggregateId,
        String objectType,
        String calibrationPath,
        String mutationKey
    ) {
        outbox.append(
            workspaceId,
            PermissionRealtimeDomainEventHandler.SECURITY_CHANGED,
            aggregateType,
            aggregateId,
            actorId,
            Map.of(
                "objectType", objectType,
                "calibrationPath", calibrationPath
            ),
            "permission.security:" + UUID.nameUUIDFromBytes(mutationKey.getBytes(StandardCharsets.UTF_8))
        );
    }
}
