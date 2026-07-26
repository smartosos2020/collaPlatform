package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationThreeWayMergeTests {
    private ObjectMapper objectMapper;
    private WorkItemConfigurationThreeWayMerge merge;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        merge = new WorkItemConfigurationThreeWayMerge(objectMapper, canonicalizer);
    }

    @Test
    void mergesIndependentAdditionsBySemanticKey() throws Exception {
        JsonNode base = snapshot("""
            [{"id":"1","fieldKey":"title","name":"Title","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]}]
            """);
        JsonNode upstream = snapshot("""
            [
              {"id":"1","fieldKey":"title","name":"Title","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]},
              {"id":"2","fieldKey":"priority","name":"Priority","fieldType":"text","config":{},"sortOrder":2,"status":"active","system":false,"options":[]}
            ]
            """);
        JsonNode local = snapshot("""
            [
              {"id":"1","fieldKey":"title","name":"Title","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]},
              {"id":"3","fieldKey":"owner","name":"Owner","fieldType":"text","config":{},"sortOrder":3,"status":"active","system":false,"options":[]}
            ]
            """);

        var result = merge.merge(base, upstream, local, Map.of());

        assertTrue(result.conflicts().isEmpty());
        assertEquals(3, result.snapshot().path("fields").size());
        assertEquals("owner", result.snapshot().path("fields").get(2).path("fieldKey").asText());
    }

    @Test
    void exposesConcurrentRenameAndAppliesExplicitResolution() throws Exception {
        JsonNode base = snapshot("""
            [{"id":"1","fieldKey":"title","name":"Title","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]}]
            """);
        JsonNode upstream = snapshot("""
            [{"id":"1","fieldKey":"title","name":"Headline","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]}]
            """);
        JsonNode local = snapshot("""
            [{"id":"1","fieldKey":"title","name":"Subject","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]}]
            """);

        var preview = merge.merge(base, upstream, local, Map.of());
        assertEquals(1, preview.conflicts().size());
        assertEquals("$.fields[title].name", preview.conflicts().getFirst().keyPath());
        assertEquals("Subject", preview.snapshot().path("fields").get(0).path("name").asText());

        var resolved = merge.merge(
            base,
            upstream,
            local,
            Map.of("$.fields[title].name", "upstream")
        );
        assertEquals("Headline", resolved.snapshot().path("fields").get(0).path("name").asText());
    }

    @Test
    void reportsDeleteOrModifyWithoutSilentlyDroppingLocalValue() throws Exception {
        JsonNode base = snapshot("""
            [{"id":"1","fieldKey":"title","name":"Title","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]}]
            """);
        JsonNode upstream = snapshot("[]");
        JsonNode local = snapshot("""
            [{"id":"1","fieldKey":"title","name":"Subject","fieldType":"text","config":{},"sortOrder":1,"status":"active","system":false,"options":[]}]
            """);

        var result = merge.merge(base, upstream, local, Map.of());

        assertEquals(1, result.conflicts().size());
        assertEquals("delete_or_modify", result.conflicts().getFirst().reason());
        assertEquals(1, result.snapshot().path("fields").size());
    }

    private JsonNode snapshot(String fields) throws Exception {
        return objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{"id":"t","workspaceId":"w","spaceId":"s","typeKey":"task"},
              "fields":%s,
              "layouts":[
                {"id":"c","layoutKind":"create","status":"active","nodes":[],"policies":[]},
                {"id":"d","layoutKind":"detail","status":"active","nodes":[],"policies":[]}
              ]
            }
            """.formatted(fields));
    }
}
