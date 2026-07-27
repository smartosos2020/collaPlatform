package com.colla.platform.modules.project.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.project.contract.NodeTaskLifecycleEvent;
import com.colla.platform.modules.project.contract.WorkItemChangedEvent;
import com.colla.platform.modules.project.infrastructure.PersonalWorkRepository;
import java.time.Clock;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class PersonalWorkInvalidationEventHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "project.personal-work.invalidation",
        1,
        Set.of(
            new Subscription(WorkItemChangedEvent.EVENT_TYPE, WorkItemChangedEvent.EVENT_VERSION),
            new Subscription(NodeTaskLifecycleEvent.EVENT_TYPE, NodeTaskLifecycleEvent.EVENT_SCHEMA_VERSION)
        ),
        true
    );

    private final PersonalWorkRepository repository;
    private final Clock clock;

    @Autowired
    public PersonalWorkInvalidationEventHandler(PersonalWorkRepository repository) {
        this(repository, Clock.systemUTC());
    }

    PersonalWorkInvalidationEventHandler(PersonalWorkRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void handle(EventMessage event) {
        repository.invalidateKnownUsers(
            event.workspaceId(),
            event.aggregateId(),
            WorkItemChangedEvent.EVENT_TYPE.equals(event.eventType()) ? "work_item" : "node_task",
            event.aggregateSequence(),
            clock.instant()
        );
    }
}
