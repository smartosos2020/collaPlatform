package com.colla.platform.modules.project.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.application.WorkItemConfigurationSnapshotCanonicalizer;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.WorkItemConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishedSnapshotAdapterTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void consumesOnlyCompleteImmutableSnapshotAndValidatesHash() throws Exception {
        var repository = mock(PublishedSnapshotReader.class);
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var snapshot = objectMapper.readTree("""
            {"snapshotSchemaVersion":1,"typeDefinition":{"typeKey":"task"},"fields":[],"layouts":[]}
            """);
        var canonical = canonicalizer.canonicalize(snapshot);
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(repository.findPublishedSnapshot(workspaceId, spaceId, typeId, versionId))
            .thenReturn(Optional.of(version(
                workspaceId, spaceId, typeId, versionId, 1, canonical.configHash(), snapshot
            )));

        var result = new PublishedSnapshotAdapter(repository, canonicalizer)
            .requireComplete(workspaceId, spaceId, typeId, versionId);

        assertEquals(versionId, result.versionId());
        assertEquals(canonical.configHash(), result.configHash());
        assertEquals("task", result.snapshot().path("typeDefinition").path("typeKey").asText());
        assertFalse(result.hasStateFlow());
        assertEquals("not_configured", result.stateFlowAvailability());
    }

    @Test
    void explicitlyRejectsLegacyPartialAndIntegrityMismatch() throws Exception {
        var repository = mock(PublishedSnapshotReader.class);
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var snapshot = objectMapper.readTree("{\"typeKey\":\"task\"}");
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(repository.findPublishedSnapshot(workspaceId, spaceId, typeId, versionId))
            .thenReturn(Optional.of(version(
                workspaceId, spaceId, typeId, versionId, 0, "a".repeat(64), snapshot
            )));
        var adapter = new PublishedSnapshotAdapter(repository, canonicalizer);

        WorkItemConfigurationException legacy = assertThrows(
            WorkItemConfigurationException.class,
            () -> adapter.requireComplete(workspaceId, spaceId, typeId, versionId)
        );
        assertEquals("UNSUPPORTED_SNAPSHOT_SCHEMA", legacy.code());

        var complete = objectMapper.readTree("""
            {"snapshotSchemaVersion":1,"typeDefinition":{"typeKey":"task"},"fields":[],"layouts":[]}
            """);
        when(repository.findPublishedSnapshot(workspaceId, spaceId, typeId, versionId))
            .thenReturn(Optional.of(version(
                workspaceId, spaceId, typeId, versionId, 1, "b".repeat(64), complete
            )));
        WorkItemConfigurationException integrity = assertThrows(
            WorkItemConfigurationException.class,
            () -> adapter.requireComplete(workspaceId, spaceId, typeId, versionId)
        );
        assertEquals("SNAPSHOT_INTEGRITY_FAILURE", integrity.code());
    }

    @Test
    void rejectsFutureSnapshotSchemaWithoutInterpretingItsPayload() throws Exception {
        var repository = mock(PublishedSnapshotReader.class);
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var snapshot = objectMapper.readTree("""
            {"snapshotSchemaVersion":6,"typeDefinition":{"typeKey":"future"},"fields":[],"layouts":[]}
            """);
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(repository.findPublishedSnapshot(workspaceId, spaceId, typeId, versionId))
            .thenReturn(Optional.of(version(
                workspaceId, spaceId, typeId, versionId, 6, "c".repeat(64), snapshot
            )));

        WorkItemConfigurationException unsupported = assertThrows(
            WorkItemConfigurationException.class,
            () -> new PublishedSnapshotAdapter(repository, canonicalizer)
                .requireComplete(workspaceId, spaceId, typeId, versionId)
        );

        assertEquals("UNSUPPORTED_SNAPSHOT_SCHEMA", unsupported.code());
    }

    @Test
    void consumesSchemaV2StateFlowWithoutReadingLatestConfiguration() throws Exception {
        var repository = mock(PublishedSnapshotReader.class);
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var snapshot = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":2,
              "typeDefinition":{"typeKey":"task"},
              "fields":[],
              "layouts":[],
              "stateFlow":{"states":[],"actions":[],"transitions":[],"guards":[]}
            }
            """);
        var canonical = canonicalizer.canonicalize(snapshot);
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(repository.findPublishedSnapshot(workspaceId, spaceId, typeId, versionId))
            .thenReturn(Optional.of(version(
                workspaceId, spaceId, typeId, versionId, 2, canonical.configHash(), snapshot
            )));

        var result = new PublishedSnapshotAdapter(repository, canonicalizer)
            .requireComplete(workspaceId, spaceId, typeId, versionId);

        assertTrue(result.hasStateFlow());
        assertEquals("available", result.stateFlowAvailability());
    }

    @Test
    void exposesSchemaV3NodeFlowDefinitionWithoutClaimingRuntimeActivation() throws Exception {
        var repository = mock(PublishedSnapshotReader.class);
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var snapshot = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":3,
              "typeDefinition":{"typeKey":"project"},
              "fields":[],
              "layouts":[],
              "nodeFlow":{"stages":[],"nodes":[],"edges":[],"branches":[],"joins":[]}
            }
            """);
        var canonical = canonicalizer.canonicalize(snapshot);
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(repository.findPublishedSnapshot(workspaceId, spaceId, typeId, versionId))
            .thenReturn(Optional.of(version(
                workspaceId, spaceId, typeId, versionId, 3, canonical.configHash(), snapshot
            )));

        var result = new PublishedSnapshotAdapter(repository, canonicalizer)
            .requireComplete(workspaceId, spaceId, typeId, versionId);

        assertTrue(result.hasNodeFlowDefinition());
        assertEquals("available", result.nodeFlowAvailability());
        assertFalse(result.hasStateFlow());
    }

    @Test
    void resolvesReadinessWithOneBatchReadAndFailsClosedPerBinding() throws Exception {
        var repository = mock(PublishedSnapshotReader.class);
        var canonicalizer = new WorkItemConfigurationSnapshotCanonicalizer(objectMapper);
        var readySnapshot = objectMapper.readTree("""
            {"snapshotSchemaVersion":1,"typeDefinition":{"typeKey":"task"},"fields":[],"layouts":[]}
            """);
        var readyCanonical = canonicalizer.canonicalize(readySnapshot);
        var legacySnapshot = objectMapper.readTree("{\"typeKey\":\"bug\"}");
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID readyTypeId = UUID.randomUUID();
        UUID readyVersionId = UUID.randomUUID();
        UUID legacyTypeId = UUID.randomUUID();
        UUID legacyVersionId = UUID.randomUUID();
        UUID missingTypeId = UUID.randomUUID();
        UUID missingVersionId = UUID.randomUUID();
        List<UUID> versionIds = List.of(readyVersionId, legacyVersionId, missingVersionId);
        when(repository.findPublishedSnapshots(workspaceId, spaceId, versionIds))
            .thenReturn(List.of(
                version(
                    workspaceId,
                    spaceId,
                    readyTypeId,
                    readyVersionId,
                    1,
                    readyCanonical.configHash(),
                    readySnapshot
                ),
                version(
                    workspaceId,
                    spaceId,
                    legacyTypeId,
                    legacyVersionId,
                    0,
                    "a".repeat(64),
                    legacySnapshot
                )
            ));

        var readiness = new PublishedSnapshotAdapter(repository, canonicalizer)
            .completeReadiness(
                workspaceId,
                spaceId,
                List.of(
                    new PublishedSnapshotAdapter.SnapshotBinding(readyTypeId, readyVersionId),
                    new PublishedSnapshotAdapter.SnapshotBinding(legacyTypeId, legacyVersionId),
                    new PublishedSnapshotAdapter.SnapshotBinding(missingTypeId, missingVersionId)
                )
            );

        assertTrue(readiness.get(readyTypeId));
        assertFalse(readiness.get(legacyTypeId));
        assertFalse(readiness.get(missingTypeId));
        verify(repository, times(1)).findPublishedSnapshots(workspaceId, spaceId, versionIds);
    }

    private PublishedConfigurationVersion version(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId,
        int schemaVersion,
        String hash,
        com.fasterxml.jackson.databind.JsonNode snapshot
    ) {
        return new PublishedConfigurationVersion(
            versionId,
            workspaceId,
            spaceId,
            typeId,
            2,
            "published",
            schemaVersion,
            hash,
            snapshot,
            null,
            null,
            UUID.randomUUID(),
            Instant.parse("2026-07-26T00:00:00Z")
        );
    }
}
