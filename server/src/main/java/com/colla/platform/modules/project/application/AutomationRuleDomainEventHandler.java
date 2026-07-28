package com.colla.platform.modules.project.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.project.infrastructure.AutomationRuleRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AutomationRuleDomainEventHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "project.automation-rules",
        1,
        Set.of(
            new Subscription("project.work-item.changed", 1),
            new Subscription("project.workflow.changed", 1),
            new Subscription("project.node-workflow.changed", 1),
            new Subscription("project.relation.changed", 1),
            new Subscription("project.resource.changed", 1)
        ),
        true
    );
    private final AutomationRuleRepository rules;
    private final AutomationExecutionService executions;
    private final ObjectMapper objectMapper;

    public AutomationRuleDomainEventHandler(
        AutomationRuleRepository rules,
        @Lazy AutomationExecutionService executions,
        ObjectMapper objectMapper
    ) {
        this.rules = rules;
        this.executions = executions;
        this.objectMapper = objectMapper;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void handle(EventMessage event) {
        UUID directSpaceId = optionalUuid(event.payload(), "spaceId");
        UUID spaceId = directSpaceId == null
            ? optionalUuid(event.payload(), "space_id") : directSpaceId;
        if (spaceId == null) return;
        CurrentUser actor = new CurrentUser(
            event.actorId(), event.workspaceId(), null,
            "automation-event", "Automation event", Set.of(), Set.of()
        );
        ObjectNode input = objectMapper.createObjectNode();
        input.put("eventId", event.eventId().toString());
        input.put("eventType", event.eventType());
        input.put("eventVersion", event.eventVersion());
        input.put("aggregateId", event.aggregateId().toString());
        input.put("actorId", event.actorId().toString());
        input.put("workspaceId", event.workspaceId().toString());
        input.put("occurredAt", event.occurredAt().toString());
        input.set("payload", objectMapper.valueToTree(event.payload()));
        rules.list(event.workspaceId(), spaceId, 101).stream()
            .filter(rule -> "enabled".equals(rule.status()))
            .filter(rule -> event.eventType().equals(
                rule.trigger().path("eventType").asText()
            ))
            .limit(20)
            .forEach(rule -> executions.executeEvent(
                actor, spaceId, rule.id(), event.eventId().toString(), input
            ));
    }

    private UUID optionalUuid(Map<String, Object> payload, String name) {
        try {
            Object value = payload.get(name);
            return value == null ? null : UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
