package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionKind;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.GuardDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.GuardKind;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.StateCategory;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.StateDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.TransitionDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.DecisionContext;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.RuntimeFlow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkItemStateFlowDecisionServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemStateFlowDecisionService service = new WorkItemStateFlowDecisionService();

    @Test
    void projectionAndExecutionDecisionShareAuthorizationGuardAndRequiredFieldLogic() throws Exception {
        RuntimeFlow flow = flow();

        var deniedRole = service.decide(
            flow, "open", "complete",
            new DecisionContext("guest", Set.of("watcher"), objectMapper.readTree(
                "{\"priority\":\"high\",\"resolution\":\"fixed\"}"
            ))
        );
        var deniedGuard = service.decide(
            flow, "open", "complete",
            new DecisionContext("member", Set.of(), objectMapper.readTree(
                "{\"priority\":\"low\",\"resolution\":\"fixed\"}"
            ))
        );
        var deniedRequired = service.decide(
            flow, "open", "complete",
            new DecisionContext("member", Set.of(), objectMapper.readTree(
                "{\"priority\":\"high\"}"
            ))
        );
        var allowed = service.decide(
            flow, "open", "complete",
            new DecisionContext("member", Set.of(), objectMapper.readTree(
                "{\"priority\":\"high\",\"resolution\":\"fixed\"}"
            ))
        );

        assertThat(deniedRole.reasonCode()).isEqualTo("not_authorized");
        assertThat(deniedGuard.reasonCode()).isEqualTo("guard_not_satisfied");
        assertThat(deniedRequired.reasonCode()).isEqualTo("required_fields_missing");
        assertThat(allowed.allowed()).isTrue();
        assertThat(service.available(
            flow, "open",
            new DecisionContext("member", Set.of(), objectMapper.readTree(
                "{\"priority\":\"high\"}"
            ))
        )).singleElement().satisfies(action ->
            assertThat(action.requiredFieldKeys()).containsExactly("resolution")
        );
        assertThat(service.available(
            flow, "open",
            new DecisionContext("member", Set.of(), objectMapper.readTree(
                "{\"priority\":\"high\",\"resolution\":\"fixed\"}"
            ))
        )).extracting("actionKey").containsExactly("complete");
    }

    @Test
    void participantAndLifecycleActionsUseTheSameDecisionPath() throws Exception {
        RuntimeFlow flow = flow();
        var watcher = service.decide(
            flow, "open", "complete",
            new DecisionContext("guest", Set.of("assignee"), objectMapper.readTree(
                "{\"priority\":\"high\",\"resolution\":\"fixed\"}"
            ))
        );
        var reopen = service.decide(
            flow, "done", "reopen",
            new DecisionContext("owner", Set.of(), objectMapper.readTree("{}"))
        );

        assertThat(watcher.allowed()).isTrue();
        assertThat(reopen.allowed()).isTrue();
        assertThat(service.available(
            flow, "done",
            new DecisionContext("owner", Set.of(), objectMapper.readTree("{}"))
        )).extracting("actionKey").containsExactly("reopen");
    }

    private RuntimeFlow flow() throws Exception {
        StateDefinition open = new StateDefinition("open", "Open", "", "", StateCategory.initial, 100);
        StateDefinition done = new StateDefinition("done", "Done", "", "", StateCategory.terminal, 200);
        ActionDefinition complete = new ActionDefinition(
            "complete", "Complete", "", ActionKind.forward,
            List.of("member", "assignee"), List.of("resolution"),
            objectMapper.readTree("{}"), List.of(), 100
        );
        ActionDefinition reopen = new ActionDefinition(
            "reopen", "Reopen", "", ActionKind.reopen,
            List.of("owner"), List.of(), objectMapper.readTree("{}"), List.of(), 200
        );
        GuardDefinition priority = new GuardDefinition(
            "priority_high", GuardKind.field, "eq", "priority", null,
            List.of(), objectMapper.readTree("\"high\""), List.of()
        );
        return new RuntimeFlow(
            "available", "version:hash", open,
            Map.of("open", open, "done", done),
            Map.of("complete", complete, "reopen", reopen),
            Map.of("priority_high", priority),
            List.of(
                new TransitionDefinition(
                    "open_complete", "complete", "open", "done", "priority_high", 100
                ),
                new TransitionDefinition(
                    "done_reopen", "reopen", "done", "open", null, 200
                )
            )
        );
    }
}
