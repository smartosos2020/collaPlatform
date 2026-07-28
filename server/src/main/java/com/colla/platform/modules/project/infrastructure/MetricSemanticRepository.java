package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.MetricSemanticModels.Dimension;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricDefinition;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricExpression;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricVersion;
import com.colla.platform.modules.project.domain.MetricSemanticModels.Window;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricSemanticRepository {
    List<Dimension> dimensions(UUID workspaceId, UUID spaceId);

    List<MetricDefinition> list(UUID workspaceId, UUID spaceId, int limit);

    Optional<MetricDefinition> find(UUID workspaceId, UUID spaceId, UUID metricId);

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    );

    MetricDefinition save(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID metricId,
        String metricKey, String name, String description, String unit,
        MetricExpression expression, Window window, long expectedVersion,
        String requestId, String requestHash
    );

    MetricVersion publish(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID metricId,
        long expectedVersion, String definitionHash,
        String requestId, String requestHash
    );

    MetricDefinition lifecycle(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID metricId,
        String action, long expectedVersion, String requestId, String requestHash
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
