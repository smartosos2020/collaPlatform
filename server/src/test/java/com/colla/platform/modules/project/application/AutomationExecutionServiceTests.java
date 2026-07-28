package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.identity.contract.SubjectDirectory;
import com.colla.platform.modules.identity.contract.SubjectDirectory.MemberProfile;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.AutomationRun;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.ExecuteRuleCommand;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationRule;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleVersion;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.AutomationExecutionRepository;
import com.colla.platform.modules.project.infrastructure.AutomationRuleRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationExecutionServiceTests {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID SPACE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();
    private static final UUID RULE = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void executesNotificationThroughPublicOutboxWithStepReceipt() {
        Fixture fixture = fixture("owner");
        stubRule(fixture, false);
        when(fixture.subjects.findActiveMember(WORKSPACE, USER, RECIPIENT))
            .thenReturn(Optional.of(new MemberProfile(
                RECIPIENT, "recipient", "Recipient", null, null, List.of()
            )));
        when(fixture.executions.begin(
            eq(WORKSPACE), eq(SPACE), eq(RULE), eq(1),
            eq("manual"), eq("execute-1"), eq(USER), eq(false), any()
        )).thenReturn(new AutomationExecutionRepository.StartResult(run("running", false), false));
        when(fixture.executions.startStep(
            eq(WORKSPACE), eq(SPACE), eq(RUN), eq(0),
            eq("send_notification"), any()
        )).thenReturn(null);
        when(fixture.executions.findActionReceipt(
            WORKSPACE, SPACE, RULE, 1, 0, "execute-1:0"
        )).thenReturn(Optional.empty());
        when(fixture.executions.get(WORKSPACE, SPACE, RUN))
            .thenReturn(run("succeeded", false));

        AutomationRun result = fixture.service.execute(
            user(), SPACE, RULE, command(false)
        );

        assertThat(result.status()).isEqualTo("succeeded");
        verify(fixture.outbox).append(
            eq(WORKSPACE), eq("notification.created"),
            eq("project_space"), eq(SPACE), eq(USER), any(), any()
        );
        verify(fixture.executions).saveActionReceipt(
            eq(WORKSPACE), eq(SPACE), eq(RULE), eq(1), eq(0),
            eq("execute-1:0"), any(), any()
        );
    }

    @Test
    void dryRunCreatesNoBusinessSideEffectAndMemberCannotRunManually() {
        Fixture owner = fixture("owner");
        stubRule(owner, true);
        when(owner.executions.begin(
            eq(WORKSPACE), eq(SPACE), eq(RULE), eq(1),
            eq("manual"), eq("execute-1"), eq(USER), eq(true), any()
        )).thenReturn(new AutomationExecutionRepository.StartResult(run("running", true), false));
        when(owner.executions.get(WORKSPACE, SPACE, RUN))
            .thenReturn(run("succeeded", true));

        AutomationRun result = owner.service.execute(
            user(), SPACE, RULE, command(true)
        );

        assertThat(result.dryRun()).isTrue();
        verify(owner.outbox, never()).append(
            eq(WORKSPACE), eq("notification.created"),
            any(), any(), any(), any(), any()
        );

        Fixture member = fixture("member");
        assertThatThrownBy(() -> member.service.execute(
            user(), SPACE, RULE, command(false)
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("owners and administrators");
    }

    private static void stubRule(Fixture fixture, boolean dryRun) {
        ObjectNode definition = definition();
        AutomationRule rule = new AutomationRule(
            RULE, "Notify", dryRun ? "disabled" : "enabled",
            definition.path("trigger"), definition.path("condition"),
            definition.path("actions"), 3, 1, USER, NOW
        );
        when(fixture.rules.find(WORKSPACE, SPACE, RULE))
            .thenReturn(Optional.of(rule));
        when(fixture.rules.findVersion(WORKSPACE, SPACE, RULE, 1))
            .thenReturn(Optional.of(new RuleVersion(
                UUID.randomUUID(), RULE, 1, "a".repeat(64),
                definition, USER, NOW
            )));
    }

    private static Fixture fixture(String role) {
        AutomationRuleRepository rules = mock(AutomationRuleRepository.class);
        AutomationExecutionRepository executions = mock(AutomationExecutionRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        SubjectDirectory subjects = mock(SubjectDirectory.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        when(spaces.findById(WORKSPACE, SPACE, USER))
            .thenReturn(Optional.of(space(role)));
        AutomationExecutionService service = new AutomationExecutionService(
            rules, executions, spaces, mock(WorkItemService.class),
            mock(WorkItemRelationService.class), subjects, mock(AuditLog.class),
            outbox, JSON, mock(AutomationQuotaService.class)
        );
        return new Fixture(rules, executions, subjects, outbox, service);
    }

    private static ExecuteRuleCommand command(boolean dryRun) {
        ObjectNode event = JSON.createObjectNode();
        event.put("aggregateId", UUID.randomUUID().toString());
        return new ExecuteRuleCommand(1, "execute-1", dryRun, event);
    }

    private static ObjectNode definition() {
        ObjectNode definition = JSON.createObjectNode();
        definition.put("schemaVersion", 1);
        definition.put("name", "Notify");
        ObjectNode trigger = definition.putObject("trigger");
        trigger.put("schemaVersion", 1);
        trigger.put("type", "event");
        trigger.put("eventType", "project.work-item.changed");
        trigger.put("eventVersion", 1);
        ObjectNode condition = definition.putObject("condition");
        condition.put("schemaVersion", 1);
        condition.put("kind", "compare");
        condition.put("reference", "event.aggregateId");
        condition.put("operator", "exists");
        ObjectNode action = definition.putArray("actions").addObject();
        action.put("schemaVersion", 1);
        action.put("actionType", "send_notification");
        ObjectNode config = action.putObject("config");
        config.put("recipientId", RECIPIENT.toString());
        config.put("title", "Automation notification");
        config.put("body", "A bounded body");
        return definition;
    }

    private static AutomationRun run(String status, boolean dryRun) {
        return new AutomationRun(
            RUN, RULE, 1, "manual", "execute-1", USER,
            status, dryRun, "b".repeat(64), List.of(), null,
            1, 1, NOW, "running".equals(status) ? null : NOW
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
            USER, WORKSPACE, null, "owner", "Owner",
            Set.of("owner"), Set.of()
        );
    }

    private record Fixture(
        AutomationRuleRepository rules,
        AutomationExecutionRepository executions,
        SubjectDirectory subjects,
        TransactionalOutbox outbox,
        AutomationExecutionService service
    ) {
    }
}
