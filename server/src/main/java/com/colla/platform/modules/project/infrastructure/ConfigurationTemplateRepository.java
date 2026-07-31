package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.ConfigurationTemplate;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.ConfigurationTemplateVersion;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateCommandReceipt;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateInstallation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigurationTemplateRepository {
    void importPlatformTemplate(PlatformTemplateImport value);

    List<ConfigurationTemplate> listVisible(UUID workspaceId);

    Optional<ConfigurationTemplate> findVisible(UUID workspaceId, UUID templateId);

    Optional<ConfigurationTemplate> lockVisible(UUID workspaceId, UUID templateId);

    Optional<ConfigurationTemplateVersion> findVersion(
        UUID workspaceId,
        UUID templateId,
        UUID versionId
    );

    List<ConfigurationTemplateVersion> listVersions(UUID workspaceId, UUID templateId);

    void insertWorkspaceTemplate(NewWorkspaceTemplate template, NewTemplateVersion version);

    void insertVersion(NewTemplateVersion version);

    int switchCurrentVersion(UUID templateId, UUID expectedVersionId, UUID nextVersionId, UUID actorId);

    int withdraw(UUID workspaceId, UUID templateId, UUID actorId);

    Optional<TemplateInstallation> findInstallation(UUID workspaceId, UUID spaceId, UUID typeId);

    Optional<TemplateInstallation> lockInstallation(UUID workspaceId, UUID spaceId, UUID typeId);

    void install(NewInstallation installation);

    int upgrade(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID installationId,
        UUID expectedUpstreamVersionId,
        long expectedAggregateVersion,
        UUID nextUpstreamVersionId,
        JsonNode lineageSummary,
        UUID actorId
    );

    int detach(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID installationId,
        JsonNode lineageSummary,
        UUID actorId
    );

    void appendHistory(TemplateHistory history);

    boolean tryStartCommand(TemplateCommandStart command);

    Optional<TemplateCommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String operation,
        String requestId
    );

    void completeCommand(UUID commandId, JsonNode response);

    record PlatformTemplateImport(
        UUID templateId,
        UUID versionId,
        String templateKey,
        String name,
        String description,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot,
        String sourceCatalogVersion
    ) {
    }

    record NewWorkspaceTemplate(
        UUID id,
        UUID workspaceId,
        String templateKey,
        String name,
        String description,
        UUID actorId
    ) {
    }

    record NewTemplateVersion(
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
        UUID actorId
    ) {
    }

    record NewInstallation(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID templateId,
        UUID versionId,
        JsonNode lineageSummary,
        UUID actorId
    ) {
    }

    record TemplateHistory(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID installationId,
        String operation,
        UUID fromVersionId,
        UUID toVersionId,
        String resultHash,
        JsonNode resultSummary,
        UUID actorId
    ) {
    }

    record TemplateCommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String requestId,
        String operation,
        String requestHash,
        UUID actorId
    ) {
    }
}
