package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkItemConfigurationTemplateModels {
    private WorkItemConfigurationTemplateModels() {
    }

    public record ConfigurationTemplate(
        UUID id,
        UUID ownerWorkspaceId,
        String scope,
        String templateKey,
        String name,
        String description,
        String visibility,
        String status,
        UUID currentVersionId,
        long aggregateVersion,
        Instant updatedAt
    ) {
        public boolean platform() {
            return "platform".equals(scope);
        }
    }

    public record ConfigurationTemplateVersion(
        UUID id,
        UUID templateId,
        UUID ownerWorkspaceId,
        int versionNumber,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot,
        UUID sourceSpaceId,
        UUID sourceTypeDefinitionId,
        UUID sourceConfigurationVersionId,
        String sourceCatalogVersion,
        UUID publishedBy,
        Instant publishedAt
    ) {
    }

    public record TemplateInstallation(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID templateId,
        UUID installedVersionId,
        UUID upstreamVersionId,
        String status,
        JsonNode lastLineageSummary,
        long aggregateVersion,
        Instant updatedAt
    ) {
        public boolean attached() {
            return "attached".equals(status);
        }
    }

    public record MergeConflict(
        String keyPath,
        JsonNode baseValue,
        JsonNode upstreamValue,
        JsonNode localValue,
        String reason
    ) {
    }

    public record TemplateUpgradePreview(
        UUID installationId,
        UUID templateId,
        UUID baseVersionId,
        UUID upstreamVersionId,
        String baseHash,
        String upstreamHash,
        String localHash,
        String mergedHash,
        JsonNode mergedSnapshot,
        List<MergeConflict> conflicts,
        Map<String, Integer> summary,
        boolean upgradeAvailable
    ) {
    }

    public record TemplateCommandReceipt(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String requestId,
        String operation,
        String requestHash,
        String status,
        JsonNode responsePayload,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }
}
