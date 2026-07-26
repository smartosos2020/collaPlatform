package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.project.contract.NodeTaskLifecycleEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NodeTaskConsumerContractHandlerTests {
    private final NodeTaskConsumerContractHandler handler = new NodeTaskConsumerContractHandler();

    @Test
    void acceptsMinimalDueContractWithoutPrivateTaskReads() {
        UUID workItemId = UUID.randomUUID();
        var event = new NodeTaskLifecycleEvent(
            UUID.randomUUID(), UUID.randomUUID(), workItemId,
            "timed_out", "review", Instant.parse("2026-07-27T00:00:00Z")
        );
        assertThatCode(() -> handler.handle(message(
            NodeTaskLifecycleEvent.EVENT_TYPE, workItemId, event.payload()
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrUnsupportedPublicContract() {
        assertThatThrownBy(() -> handler.handle(message(
            NodeTaskLifecycleEvent.EVENT_TYPE, UUID.randomUUID(),
            Map.of("eventSchemaVersion", 2)
        ))).isInstanceOf(DomainEventHandlingException.Permanent.class);
    }

    private EventMessage message(String eventType, UUID aggregateId, Map<String, Object> payload) {
        return new EventMessage(
            UUID.randomUUID(), UUID.randomUUID(), eventType, 1, "work_item", aggregateId,
            1, null, "node-task-test", null, null, Instant.now(), payload
        );
    }
}
