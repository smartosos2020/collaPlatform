package com.colla.platform.modules.project.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.application.WorkItemStateFlowPresetCatalog;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemStateRuntimeAdapterTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemStateRuntimeAdapter adapter = new WorkItemStateRuntimeAdapter();

    @Test
    void parsesOnlyTheProvidedBoundConfigurationWithDeterministicInitialState() {
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set(
            "stateFlow",
            new WorkItemStateFlowPresetCatalog(objectMapper).stateFlowFor("task").orElseThrow()
        );
        var flow = adapter.adapt(new RuntimeConfiguration(
            versionId, typeId, 7, 2, "a".repeat(64), snapshot
        ));

        assertThat(flow.configured()).isTrue();
        assertThat(flow.initialState().stateKey()).isEqualTo("open");
        assertThat(flow.policyVersion()).isEqualTo(versionId + ":" + "a".repeat(64));
        assertThat(flow.transitions()).extracting("transitionKey")
            .containsExactly(
                "open_start_progress", "in_progress_complete", "done_reopen",
                "open_terminate", "in_progress_terminate", "canceled_restore"
            );
    }

    @Test
    void explicitlyReportsCapabilityMissingInsteadOfGuessingAState() {
        var flow = adapter.adapt(new RuntimeConfiguration(
            UUID.randomUUID(), UUID.randomUUID(), 1, 2, "b".repeat(64),
            objectMapper.createObjectNode()
        ));

        assertThat(flow.availability()).isEqualTo("not_configured");
        assertThat(flow.initialState()).isNull();
        assertThat(flow.actions()).isEmpty();
    }
}
