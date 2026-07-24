package com.colla.platform.modules.identity.application;

import com.colla.platform.shared.realtime.DurableRealtimeEventPublisher;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentitySecurityChangePublisher {
    private static final String SECURITY_CHANGED = "identity.security.changed";
    private final DurableRealtimeEventPublisher eventPublisher;

    public IdentitySecurityChangePublisher(DurableRealtimeEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(
        UUID workspaceId,
        UUID actorId,
        String aggregateType,
        UUID aggregateId,
        String calibrationPath,
        String mutationKey
    ) {
        eventPublisher.append(
            workspaceId,
            SECURITY_CHANGED,
            aggregateType,
            aggregateId,
            actorId,
            Map.of("calibrationPath", calibrationPath),
            "identity.security:" + UUID.nameUUIDFromBytes(mutationKey.getBytes(StandardCharsets.UTF_8))
        );
    }
}
