package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ResourcePlanningModels.Estimate;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.SaveCalendarCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourcePlanningRepository {
    Optional<WorkCalendar> findCalendar(UUID workspaceId, UUID spaceId);

    List<Estimate> listEstimates(UUID workspaceId, UUID spaceId, int limit);

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId,
        String operation, String requestId
    );

    WorkCalendar saveCalendar(
        UUID workspaceId, UUID spaceId, UUID actorId,
        SaveCalendarCommand command, String requestHash
    );

    Estimate saveEstimate(
        UUID workspaceId, UUID spaceId, UUID actorId,
        UUID workItemId, long workItemVersion, String unit,
        java.math.BigDecimal amount, long expectedVersion,
        String requestId, String requestHash
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
