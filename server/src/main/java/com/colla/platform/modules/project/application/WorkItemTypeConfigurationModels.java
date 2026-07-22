package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.WorkItemTypeCounts;
import java.util.List;
import java.util.UUID;

public final class WorkItemTypeConfigurationModels {
    private WorkItemTypeConfigurationModels() {
    }

    public record Configuration(
        UUID spaceId,
        String spaceStatus,
        List<String> availableActions,
        List<ConfiguredType> items
    ) {
    }

    public record ConfiguredType(WorkItemTypeDefinition definition, List<String> availableActions) {
    }

    public record UserTypeSummary(String typeKey, String name, String icon, int sortOrder) {
    }

    public record ReorderType(UUID typeId, int sortOrder, long aggregateVersion) {
    }

    public record GovernanceTypeCounts(int total, int active, int disabled, int retired) {
        public static GovernanceTypeCounts from(WorkItemTypeCounts counts) {
            return new GovernanceTypeCounts(counts.total(), counts.active(), counts.disabled(), counts.retired());
        }
    }
}
