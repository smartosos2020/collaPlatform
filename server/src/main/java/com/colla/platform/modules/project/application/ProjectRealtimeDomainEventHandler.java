package com.colla.platform.modules.project.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProjectRealtimeDomainEventHandler implements DomainEventHandler {
    static final String PROJECT_SPACE_CHANGED = "project_space.changed";

    private static final Descriptor DESCRIPTOR = new Descriptor(
        "project.realtime-invalidation",
        1,
        Set.of(new Subscription(PROJECT_SPACE_CHANGED, 1)),
        true
    );

    private final ProjectSpaceMembershipRepository membershipRepository;
    private final TransactionalOutbox outbox;

    public ProjectRealtimeDomainEventHandler(
        ProjectSpaceMembershipRepository membershipRepository,
        @Lazy TransactionalOutbox outbox
    ) {
        this.membershipRepository = membershipRepository;
        this.outbox = outbox;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    @Transactional
    public void handle(EventMessage event) {
        SignalTarget target = target(event);
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>(target.recipients());
        optionalUuid(event.payload(), "affectedUserId").ifPresent(recipients::add);
        for (UUID recipientId : recipients) {
            outbox.append(
                event.workspaceId(),
                "realtime.signal.requested",
                target.objectType(),
                target.objectId(),
                event.actorId(),
                Map.of(
                    "recipientId", recipientId.toString(),
                    "signalType", booleanValue(event.payload(), "accessInvalidated")
                        ? target.objectType() + ".invalidated"
                        : target.signalType(),
                    "objectType", target.objectType(),
                    "objectId", target.objectId().toString(),
                    "sourceVersion", event.aggregateSequence(),
                    "calibrationPath", target.calibrationPath()
                ),
                "realtime:" + event.eventId() + ":" + recipientId
            );
        }
    }

    private SignalTarget target(EventMessage event) {
        return switch (event.eventType()) {
            case PROJECT_SPACE_CHANGED -> new SignalTarget(
                "project_space", event.aggregateId(), "project_space.changed",
                "/api/project-spaces/" + event.aggregateId(),
                membershipRepository.listMembers(event.workspaceId(), event.aggregateId()).stream()
                    .map(member -> member.userId())
                    .toList()
            );
            default -> throw new DomainEventHandlingException.Permanent(
                "Unsupported project realtime event: " + event.eventType()
            );
        };
    }

    private static java.util.Optional<UUID> optionalUuid(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(value.toString()));
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid project realtime " + key);
        }
    }

    private static boolean booleanValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Boolean flag && flag;
    }

    private record SignalTarget(
        String objectType,
        UUID objectId,
        String signalType,
        String calibrationPath,
        List<UUID> recipients
    ) {
    }
}
