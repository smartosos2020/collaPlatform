package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.Configuration;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.ConfiguredType;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.GovernanceTypeCounts;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.UserTypeSummary;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypePresetCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class WorkItemTypeApiDtos {
    private WorkItemTypeApiDtos() {
    }

    static SpaceConfigurationWorkItemTypeCollection configuration(Configuration configuration) {
        return new SpaceConfigurationWorkItemTypeCollection(
            configuration.spaceId(),
            configuration.spaceStatus(),
            configuration.availableActions(),
            configuration.items().stream().map(WorkItemTypeApiDtos::configuredType).toList()
        );
    }

    static SpaceConfigurationWorkItemType configuredType(ConfiguredType configured) {
        WorkItemTypeDefinition type = configured.definition();
        return new SpaceConfigurationWorkItemType(
            type.id(), type.typeKey(), type.name(), type.icon(), type.description(), type.sortOrder(),
            type.status(), type.system(), type.system() ? "development_preset" : "workspace_custom",
            type.system() ? WorkItemTypePresetCatalog.CATALOG_VERSION : null,
            type.aggregateVersion(),
            new WorkItemTypeVersionView(
                type.currentVersionId(), type.currentVersionNumber(), type.currentVersionStatus(),
                type.currentConfigHash(), type.currentConfig()
            ),
            type.createdBy(), type.createdAt(), type.updatedBy(), type.updatedAt(), configured.availableActions()
        );
    }

    static UserWorkItemTypeSummary userSummary(UserTypeSummary summary) {
        return new UserWorkItemTypeSummary(summary.typeKey(), summary.name(), summary.icon(), summary.sortOrder());
    }

    static AdminWorkItemTypeCounts governanceCounts(GovernanceTypeCounts counts) {
        return new AdminWorkItemTypeCounts(counts.total(), counts.active(), counts.disabled(), counts.retired());
    }

    record SpaceConfigurationWorkItemTypeCollection(
        UUID spaceId,
        String spaceStatus,
        List<String> availableActions,
        List<SpaceConfigurationWorkItemType> items
    ) {
    }

    record SpaceConfigurationWorkItemType(
        UUID id,
        String typeKey,
        String name,
        String icon,
        String description,
        int sortOrder,
        String status,
        boolean system,
        String source,
        String presetCatalogVersion,
        long aggregateVersion,
        WorkItemTypeVersionView currentVersion,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        List<String> availableActions
    ) {
    }

    record WorkItemTypeVersionView(UUID id, int number, String status, String configHash, JsonNode config) {
    }

    record UserWorkItemTypeSummary(String typeKey, String name, String icon, int sortOrder) {
    }

    record AdminWorkItemTypeCounts(int total, int active, int disabled, int retired) {
    }
}
