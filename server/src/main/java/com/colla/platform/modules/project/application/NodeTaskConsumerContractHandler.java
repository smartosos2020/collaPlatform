package com.colla.platform.modules.project.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.project.contract.NodeTaskLifecycleEvent;
import com.colla.platform.modules.project.contract.WorkItemNodeWorkflowEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Replay-safe notification/search contract boundary. Consumers receive only public
 * identifiers and lifecycle facts and never query project workflow private tables.
 */
@Component
public final class NodeTaskConsumerContractHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "project.node-task.consumer-contract",
        1,
        Set.of(
            new Subscription(WorkItemNodeWorkflowEvent.EVENT_TYPE, 1),
            new Subscription(NodeTaskLifecycleEvent.EVENT_TYPE, 1)
        ),
        true
    );

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void handle(EventMessage event) {
        Map<String, Object> payload = event.payload();
        if (integer(payload, "eventSchemaVersion") != 1) {
            throw permanent("Unsupported node task event schema version");
        }
        uuid(payload, "spaceId");
        required(payload, "eventKind");
        required(payload, "nodeKey");
        if (NodeTaskLifecycleEvent.EVENT_TYPE.equals(event.eventType())) {
            uuid(payload, "taskId");
            uuid(payload, "workItemId");
            required(payload, "dueAt");
        } else {
            uuid(payload, "instanceId");
            uuid(payload, "typeDefinitionId");
            uuid(payload, "typeVersionId");
            required(payload, "configHash");
            required(payload, "decisionReference");
            nonNegative(payload, "workItemVersion");
            nonNegative(payload, "aggregateVersion");
        }
    }

    private static String required(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw permanent("Missing node task event " + key);
        }
        return value.toString();
    }

    private static UUID uuid(Map<String, Object> payload, String key) {
        try {
            return UUID.fromString(required(payload, key));
        } catch (IllegalArgumentException exception) {
            throw permanent("Invalid node task event " + key);
        }
    }

    private static int integer(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        try {
            return value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(required(payload, key));
        } catch (NumberFormatException exception) {
            throw permanent("Invalid node task event " + key);
        }
    }

    private static void nonNegative(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        try {
            long parsed = value instanceof Number number
                ? number.longValue()
                : Long.parseLong(required(payload, key));
            if (parsed < 0) {
                throw permanent("Invalid node task event " + key);
            }
        } catch (NumberFormatException exception) {
            throw permanent("Invalid node task event " + key);
        }
    }

    private static DomainEventHandlingException.Permanent permanent(String message) {
        return new DomainEventHandlingException.Permanent(message);
    }
}
