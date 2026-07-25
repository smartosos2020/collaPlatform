package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.WorkItemConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationSnapshotCanonicalizerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer =
        new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);

    @Test
    void equivalentObjectAndStableCollectionOrderingProduceTheSameHash() throws Exception {
        var first = canonicalizer.canonicalize(objectMapper.readTree("""
            {
              "snapshotSchemaVersion": 1,
              "fields": [
                {"id":"b","fieldKey":"priority","sortOrder":20,"config":{"step":1.00}},
                {"id":"a","fieldKey":"title","sortOrder":10,"config":{"step":1}}
              ],
              "layouts": [
                {"layoutKind":"detail","nodes":[],"policies":[]},
                {"layoutKind":"create","nodes":[],"policies":[]}
              ],
              "typeDefinition":{"typeKey":"task"}
            }
            """));
        var second = canonicalizer.canonicalize(objectMapper.readTree("""
            {
              "typeDefinition":{"typeKey":"task"},
              "layouts": [
                {"policies":[],"nodes":[],"layoutKind":"create"},
                {"policies":[],"nodes":[],"layoutKind":"detail"}
              ],
              "fields": [
                {"config":{"step":1},"sortOrder":10,"fieldKey":"title","id":"a"},
                {"config":{"step":1.0},"sortOrder":20,"fieldKey":"priority","id":"b"}
              ],
              "snapshotSchemaVersion": 1
            }
            """));

        assertEquals(first.payload(), second.payload());
        assertEquals(first.configHash(), second.configHash());
        assertEquals(64, first.configHash().length());
    }

    @Test
    void rejectsUnknownSnapshotSchema() throws Exception {
        WorkItemConfigurationException exception = assertThrows(
            WorkItemConfigurationException.class,
            () -> canonicalizer.canonicalize(objectMapper.readTree(
                "{\"snapshotSchemaVersion\":2,\"typeDefinition\":{},\"fields\":[],\"layouts\":[]}"
            ))
        );
        assertEquals("UNSUPPORTED_SNAPSHOT_SCHEMA", exception.code());
    }
}
