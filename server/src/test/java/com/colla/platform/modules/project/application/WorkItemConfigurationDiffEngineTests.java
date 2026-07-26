package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiffImpact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationDiffEngineTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemConfigurationDiffEngine engine = new WorkItemConfigurationDiffEngine();

    @Test
    void classifiesAndOrdersSemanticChangesByStableKeyPath() throws Exception {
        var before = objectMapper.readTree("""
            {
              "fields":[
                {"fieldKey":"title","required":false},
                {"fieldKey":"priority","options":[{"value":"high"},{"value":"low"}]}
              ],
              "accessPolicies":[{"role":"member","visible":true}]
            }
            """);
        var after = objectMapper.readTree("""
            {
              "fields":[
                {"fieldKey":"title","required":true},
                {"fieldKey":"priority","options":[{"value":"high"}]},
                {"fieldKey":"owner","required":false}
              ],
              "accessPolicies":[{"role":"member","visible":false}]
            }
            """);

        var result = engine.diff("a".repeat(64), before, "b".repeat(64), after);

        assertTrue(result.breaking());
        assertTrue(result.items().stream().anyMatch(item ->
            item.impact() == DiffImpact.breaking && "removed".equals(item.changeType())
        ));
        assertTrue(result.items().stream().anyMatch(item -> item.impact() == DiffImpact.additive));
        assertTrue(result.items().stream().anyMatch(item ->
            item.impact() == DiffImpact.breaking && item.keyPath().contains("accessPolicies")
        ));
        assertEquals(
            result.items().stream().map(item -> item.keyPath()).sorted().toList(),
            result.items().stream().map(item -> item.keyPath()).toList()
        );
    }

    @Test
    void identicalSnapshotsHaveNoBehavioralImpact() throws Exception {
        var snapshot = objectMapper.readTree("{\"snapshotSchemaVersion\":1,\"fields\":[]}");
        var result = engine.diff("a".repeat(64), snapshot, "a".repeat(64), snapshot.deepCopy());

        assertFalse(result.breaking());
        assertTrue(result.items().isEmpty());
        assertEquals(0, result.summary().values().stream().mapToInt(Integer::intValue).sum());
    }
}
