package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.project.contract.WorkItemWorkflowEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemWorkflowConsumerContractHandlerTests {
    private final WorkItemWorkflowConsumerContractHandler handler =
        new WorkItemWorkflowConsumerContractHandler();

    @Test
    void subscribesToEveryLifecycleContractAndAcceptsVersionOne() {
        assertThat(handler.descriptor().subscriptions())
            .extracting("eventType")
            .containsExactlyInAnyOrder(
                WorkItemWorkflowEvent.ACTION_EXECUTED,
                WorkItemWorkflowEvent.STATE_CHANGED,
                WorkItemWorkflowEvent.INITIALIZED,
                WorkItemWorkflowEvent.BINDING_CHANGED
            );
        assertThatCode(() -> handler.handle(message(payload(1)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownPayloadSchemaAsPermanentForDeadLetterClassification() {
        assertThatThrownBy(() -> handler.handle(message(payload(2))))
            .isInstanceOf(DomainEventHandlingException.Permanent.class)
            .hasMessageContaining("Unsupported workflow event schema version");
    }

    private EventMessage message(Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        return new EventMessage(
            eventId, UUID.randomUUID(), WorkItemWorkflowEvent.STATE_CHANGED, 1,
            WorkItemWorkflowEvent.AGGREGATE_TYPE, UUID.randomUUID(), 1,
            UUID.randomUUID(), eventId.toString(), UUID.randomUUID(), null,
            Instant.now(), payload
        );
    }

    private Map<String, Object> payload(int schemaVersion) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(
            new WorkItemWorkflowEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64),
                "start_progress", "forward", "open", "in_progress", 1, 1,
                "policy:decision"
            ).payload()
        );
        payload.put("eventSchemaVersion", schemaVersion);
        return payload;
    }
}
