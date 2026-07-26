package com.colla.platform.modules.project.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.project.contract.WorkItemWorkflowEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Consumer-side contract boundary for workflow lifecycle events.
 *
 * <p>The handler deliberately validates only the public event payload. It does
 * not read workflow private tables or create notification/search content.
 * Durable delivery receipts provide replay deduplication; consumers that later
 * opt in to user-facing projections must use this public contract.</p>
 */
@Component
public final class WorkItemWorkflowConsumerContractHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "project.workflow.consumer-contract",
        1,
        Set.of(
            new Subscription(WorkItemWorkflowEvent.ACTION_EXECUTED, 1),
            new Subscription(WorkItemWorkflowEvent.STATE_CHANGED, 1),
            new Subscription(WorkItemWorkflowEvent.INITIALIZED, 1),
            new Subscription(WorkItemWorkflowEvent.BINDING_CHANGED, 1)
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
        int schemaVersion = integer(payload, "eventSchemaVersion");
        if (schemaVersion != WorkItemWorkflowEvent.EVENT_SCHEMA_VERSION) {
            throw new DomainEventHandlingException.Permanent(
                "Unsupported workflow event schema version: " + schemaVersion
            );
        }
        uuid(payload, "spaceId");
        uuid(payload, "typeDefinitionId");
        uuid(payload, "typeVersionId");
        required(payload, "configHash");
        required(payload, "actionKind");
        required(payload, "toStateKey");
        required(payload, "decisionReference");
        nonNegativeLong(payload, "workItemVersion");
        nonNegativeLong(payload, "aggregateVersion");
    }

    private static String required(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new DomainEventHandlingException.Permanent(
                "Missing workflow event " + key
            );
        }
        return value.toString();
    }

    private static UUID uuid(Map<String, Object> payload, String key) {
        try {
            return UUID.fromString(required(payload, key));
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent(
                "Invalid workflow event " + key
            );
        }
    }

    private static int integer(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(required(payload, key));
        } catch (NumberFormatException exception) {
            throw new DomainEventHandlingException.Permanent(
                "Invalid workflow event " + key
            );
        }
    }

    private static long nonNegativeLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        long result;
        if (value instanceof Number number) {
            result = number.longValue();
        } else {
            try {
                result = Long.parseLong(required(payload, key));
            } catch (NumberFormatException exception) {
                throw new DomainEventHandlingException.Permanent(
                    "Invalid workflow event " + key
                );
            }
        }
        if (result < 0) {
            throw new DomainEventHandlingException.Permanent(
                "Invalid workflow event " + key
            );
        }
        return result;
    }
}
