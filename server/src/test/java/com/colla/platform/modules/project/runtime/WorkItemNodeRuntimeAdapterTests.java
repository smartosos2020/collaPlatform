package com.colla.platform.modules.project.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.application.WorkItemNodeFlowPresetCatalog;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemNodeRuntimeAdapterTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemNodeRuntimeAdapter adapter = new WorkItemNodeRuntimeAdapter();

    @Test
    void adaptsOnlyTheBoundNodeSnapshotIntoStableIndexes() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set(
            "nodeFlow",
            new WorkItemNodeFlowPresetCatalog(objectMapper)
                .nodeFlowFor("project")
                .orElseThrow()
        );
        UUID versionId = UUID.randomUUID();
        var flow = adapter.adapt(new RuntimeConfiguration(
            versionId, UUID.randomUUID(), 3, 3, "a".repeat(64), snapshot
        ));

        assertThat(flow.configured()).isTrue();
        assertThat(flow.policyVersion()).isEqualTo(versionId + ":" + "a".repeat(64));
        assertThat(flow.startNode().nodeKey()).isEqualTo("start");
        assertThat(flow.outgoing().get("delivery_split"))
            .extracting("edgeKey")
            .containsExactly("split_primary_delivery", "split_quality_review");
        assertThat(flow.joinsByNode().get("delivery_join").joinKey())
            .isEqualTo("delivery_all");
    }

    @Test
    void missingCapabilityIsExplicitAndUnknownBoundKindsFailClosed() {
        var missing = adapter.adapt(new RuntimeConfiguration(
            UUID.randomUUID(), UUID.randomUUID(), 2, 2, "b".repeat(64),
            objectMapper.createObjectNode()
        ));
        assertThat(missing.availability()).isEqualTo("not_configured");

        ObjectNode snapshot = objectMapper.createObjectNode();
        ObjectNode flow = (ObjectNode) new WorkItemNodeFlowPresetCatalog(objectMapper)
            .nodeFlowFor("project")
            .orElseThrow().deepCopy();
        ((ObjectNode) flow.withArray("nodes").get(0)).put("kind", "script");
        snapshot.set("nodeFlow", flow);
        assertThatThrownBy(() -> adapter.adapt(new RuntimeConfiguration(
            UUID.randomUUID(), UUID.randomUUID(), 3, 3, "c".repeat(64), snapshot
        )))
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo("INVALID_NODE_FLOW_SNAPSHOT");
    }
}
