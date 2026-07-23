package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import java.util.List;
import java.util.UUID;

public final class WorkItemFieldConfigurationModels {
    private WorkItemFieldConfigurationModels() {
    }

    public record Configuration(
        UUID spaceId,
        UUID typeDefinitionId,
        String spaceStatus,
        List<String> availableActions,
        List<ConfiguredField> fields
    ) {
    }

    public record ConfiguredField(
        FieldDefinition definition,
        List<FieldOption> options,
        List<String> availableActions
    ) {
    }

    public record ReorderField(UUID fieldId, int sortOrder, long aggregateVersion) {
    }
}
