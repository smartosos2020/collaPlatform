package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationFoundation;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationRule;
import com.colla.platform.modules.project.domain.AutomationRuleModels.SaveRuleCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.AutomationRuleRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationRuleServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID RULE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesBoundedPublicCatalogAndRulesToCurrentMembers() {
        Fixture fixture = fixture("member");
        when(fixture.repository.list(WORKSPACE, SPACE, 101))
            .thenReturn(List.of(rule()));

        AutomationFoundation result = fixture.service.get(user(), SPACE);

        assertThat(result.events()).extracting(value -> value.eventType())
            .contains("project.work-item.changed", "project.resource.changed");
        assertThat(result.actions()).extracting(value -> value.actionType())
            .contains("update_field", "send_notification", "webhook");
        assertThat(result.rules()).singleElement().extracting(AutomationRule::id)
            .isEqualTo(RULE);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void savesValidatedDeclarativeRuleForSpaceOwner() {
        Fixture fixture = fixture("owner");
        SaveRuleCommand command = command("safe-rule", condition("event.aggregateId"));
        when(fixture.repository.findCommand(
            WORKSPACE, SPACE, USER, "save_rule", "safe-rule"
        )).thenReturn(Optional.empty());
        when(fixture.repository.save(
            eq(WORKSPACE), eq(SPACE), eq(USER), eq(null), eq("Notify owner"),
            any(), any(), any(), eq(0L), eq("safe-rule"), any()
        )).thenReturn(rule());

        AutomationRule result = fixture.service.save(user(), SPACE, command);

        assertThat(result.id()).isEqualTo(RULE);
        verify(fixture.auditLog).log(
            any(CurrentUser.class), eq("project_automation.rule_saved"),
            eq("project_automation_rule"), eq(RULE), any()
        );
        verify(fixture.outbox).append(
            eq(WORKSPACE), eq("project.automation.rule.changed"),
            eq("project_automation_rule"), eq(RULE), eq(USER), any(), any()
        );
    }

    @Test
    void rejectsScriptsDeepConditionsAndMemberConfiguration() {
        Fixture member = fixture("member");
        ObjectNode script = condition("event.aggregateId");
        script.put("script", "return true");
        assertThatThrownBy(() -> member.service.save(
            user(), SPACE, command("script-rule", script)
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("owners and administrators");

        Fixture owner = fixture("owner");
        ObjectNode nested = condition("event.aggregateId");
        for (int index = 0; index < 9; index++) {
            ObjectNode parent = JSON.createObjectNode();
            parent.put("schemaVersion", 1);
            parent.put("kind", "not");
            parent.putArray("children").add(nested);
            nested = parent;
        }
        ObjectNode overDepth = nested;
        assertThatThrownBy(() -> owner.service.save(
            user(), SPACE, command("deep-rule", overDepth)
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");

        assertThatThrownBy(() -> owner.service.save(
            user(), SPACE, command("script-rule", script)
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");
    }

    private static Fixture fixture(String role) {
        AutomationRuleRepository repository = mock(AutomationRuleRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        AuditLog auditLog = mock(AuditLog.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        AutomationRuleService service = new AutomationRuleService(
            repository, spaces, auditLog, outbox, JSON
        );
        return new Fixture(repository, auditLog, outbox, service);
    }

    private static SaveRuleCommand command(String requestId, ObjectNode condition) {
        ObjectNode trigger = JSON.createObjectNode();
        trigger.put("schemaVersion", 1);
        trigger.put("type", "event");
        trigger.put("eventType", "project.work-item.changed");
        trigger.put("eventVersion", 1);
        ArrayNode actions = JSON.createArrayNode();
        ObjectNode action = actions.addObject();
        action.put("schemaVersion", 1);
        action.put("actionType", "send_notification");
        action.putObject("config");
        return new SaveRuleCommand(
            1, requestId, 0, null, "Notify owner",
            trigger, condition, actions
        );
    }

    private static ObjectNode condition(String reference) {
        ObjectNode condition = JSON.createObjectNode();
        condition.put("schemaVersion", 1);
        condition.put("kind", "compare");
        condition.put("reference", reference);
        condition.put("operator", "exists");
        return condition;
    }

    private static AutomationRule rule() {
        SaveRuleCommand command = command("rule", condition("event.aggregateId"));
        return new AutomationRule(
            RULE, "Notify owner", "draft", command.trigger(),
            command.condition(), command.actions(), 1, null, USER, NOW
        );
    }

    private static ProjectSpaceSummary space(String role) {
        return new ProjectSpaceSummary(
            SPACE, WORKSPACE, "AUTOMATION", "Automation", "", "active", "private",
            1, role, 1, USER, NOW, USER, NOW, null, null
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            USER, WORKSPACE, UUID.randomUUID(), "owner", "Owner",
            Set.of("owner"), Set.of()
        );
    }

    private record Fixture(
        AutomationRuleRepository repository,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        AutomationRuleService service
    ) {
    }
}
