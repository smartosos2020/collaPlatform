package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.MetricDashboardModels.Dashboard;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardConfig;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardPreference;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardVersion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface MetricDashboardRepository {
    List<Dashboard> list(UUID workspaceId, UUID spaceId, int limit);

    Optional<Dashboard> find(UUID workspaceId, UUID spaceId, UUID dashboardId);

    Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId
    );

    Dashboard save(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID dashboardId,
        String dashboardKey,
        String name,
        String description,
        DashboardConfig config,
        long expectedVersion,
        String requestId,
        String requestHash
    );

    DashboardVersion publish(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID dashboardId,
        long expectedVersion,
        String definitionHash,
        String requestId,
        String requestHash
    );

    Dashboard lifecycle(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID dashboardId,
        String action,
        long expectedVersion,
        String requestId,
        String requestHash
    );

    DashboardPreference preference(
        UUID workspaceId,
        UUID spaceId,
        UUID dashboardId,
        UUID userId
    );

    DashboardPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID dashboardId,
        UUID userId,
        boolean compact,
        Map<String, String> filterValues,
        long expectedVersion
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
