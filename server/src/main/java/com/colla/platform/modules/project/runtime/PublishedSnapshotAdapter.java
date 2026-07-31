package com.colla.platform.modules.project.runtime;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;

import com.colla.platform.modules.project.application.WorkItemConfigurationSnapshotCanonicalizer;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.WorkItemConfigurationException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return requireComplete(version);
    }

    public Map<UUID, Boolean> completeReadiness(
        UUID workspaceId,
        UUID spaceId,
        List<SnapshotBinding> bindings
    ) {
        List<SnapshotBinding> safeBindings = bindings == null ? List.of() : List.copyOf(bindings);
        Map<UUID, RuntimeConfiguration> configurations = completeConfigurations(
            workspaceId,
            spaceId,
            safeBindings
        );
        Map<UUID, Boolean> readiness = new LinkedHashMap<>();
        for (SnapshotBinding binding : safeBindings) {
            readiness.put(binding.typeId(), configurations.containsKey(binding.typeId()));
        }
        return Map.copyOf(readiness);
    }

    /**
     * Resolves every valid current type binding through one immutable snapshot batch read.
     *
     * <p>Unsupported, missing, cross-type, or integrity-invalid snapshots are omitted so
     * callers can fail closed per type without falling back to N single-snapshot reads.</p>
     */
    public Map<UUID, RuntimeConfiguration> completeConfigurations(
        UUID workspaceId,
        UUID spaceId,
        List<SnapshotBinding> bindings
    ) {
        List<SnapshotBinding> safeBindings = bindings == null ? List.of() : List.copyOf(bindings);
        Map<UUID, PublishedConfigurationVersion> versionsById = new LinkedHashMap<>();
        snapshotReader.findPublishedSnapshots(
            workspaceId,
            spaceId,
            safeBindings.stream().map(SnapshotBinding::versionId).toList()
        ).forEach(version -> versionsById.put(version.id(), version));

        Map<UUID, RuntimeConfiguration> configurations = new LinkedHashMap<>();
        for (SnapshotBinding binding : safeBindings) {
            PublishedConfigurationVersion version = versionsById.get(binding.versionId());
            if (version != null && version.typeDefinitionId().equals(binding.typeId())) {
                try {
                    configurations.put(binding.typeId(), requireComplete(version));
                } catch (WorkItemConfigurationException exception) {
                    // Per-binding failure is intentionally closed without aborting other types.
                }
            }
        }
        return Map.copyOf(configurations);
    }

    private RuntimeConfiguration requireComplete(PublishedConfigurationVersion version) {
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
                    ? version.snapshot().path("nodeFlow").isObject()
                        ? "supported_with_node_flow_definition"
                        : version.snapshot().path("stateFlow").isObject()
                            ? "supported_with_state_flow"
                            : "supported_without_workflow"
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

        public boolean hasNodeFlowDefinition() {
            return snapshot.path("nodeFlow").isObject();
        }

        public String nodeFlowAvailability() {
            return hasNodeFlowDefinition() ? "available" : "not_configured";
        }
    }

    public record SnapshotBinding(UUID typeId, UUID versionId) {
    }

    public record SnapshotAvailability(String status, int snapshotSchemaVersion, String configHash) {
    }
}
