package com.colla.platform.modules.project.runtime;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;

import com.colla.platform.modules.project.application.WorkItemConfigurationSnapshotCanonicalizer;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class PublishedSnapshotAdapter {
    private final PublishedSnapshotReader snapshotReader;
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer;

    public PublishedSnapshotAdapter(
        PublishedSnapshotReader snapshotReader,
        WorkItemConfigurationSnapshotCanonicalizer canonicalizer
    ) {
        this.snapshotReader = snapshotReader;
        this.canonicalizer = canonicalizer;
    }

    public RuntimeConfiguration requireComplete(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    ) {
        PublishedConfigurationVersion version = snapshotReader.findPublishedSnapshot(
            workspaceId, spaceId, typeId, versionId
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN",
            "Published configuration snapshot is not available"
        ));
        if (!version.completeSnapshot() || !version.supportedSnapshot()) {
            throw failure(
                "UNSUPPORTED_SNAPSHOT_SCHEMA",
                "Published snapshot schema is not supported by the work item runtime"
            );
        }
        var canonical = canonicalizer.canonicalize(version.snapshot());
        if (!canonical.configHash().equals(version.configHash())) {
            throw failure(
                "SNAPSHOT_INTEGRITY_FAILURE",
                "Published configuration snapshot failed integrity validation"
            );
        }
        return new RuntimeConfiguration(
            version.id(),
            version.typeDefinitionId(),
            version.versionNumber(),
            version.snapshotSchemaVersion(),
            version.configHash(),
            canonical.payload()
        );
    }

    public SnapshotAvailability inspect(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    ) {
        return snapshotReader.findPublishedSnapshot(workspaceId, spaceId, typeId, versionId)
            .map(version -> new SnapshotAvailability(
                version.supportedSnapshot()
                    ? version.snapshot().path("stateFlow").isObject()
                        ? "supported_with_state_flow"
                        : "supported_without_state_flow"
                    : version.completeSnapshot() ? "unsupported" : "legacy_partial",
                version.snapshotSchemaVersion(),
                version.configHash()
            ))
            .orElse(new SnapshotAvailability("not_found", 0, ""));
    }

    public record RuntimeConfiguration(
        UUID versionId,
        UUID typeDefinitionId,
        int versionNumber,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot
    ) {
        public boolean hasStateFlow() {
            return snapshot.path("stateFlow").isObject();
        }

        public String stateFlowAvailability() {
            return hasStateFlow() ? "available" : "not_configured";
        }
    }

    public record SnapshotAvailability(String status, int snapshotSchemaVersion, String configHash) {
    }
}
