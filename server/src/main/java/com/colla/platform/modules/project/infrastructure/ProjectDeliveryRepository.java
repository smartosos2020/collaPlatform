package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Deliverable;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MaterialInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDeliveryRepository {
    List<DeliverableSummary> list(UUID workspaceId, UUID spaceId, int limit);

    Optional<Deliverable> find(UUID workspaceId, UUID spaceId, UUID deliverableId);

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    );

    Deliverable create(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId,
        String requestHash, String title, String summary, UUID ownerUserId,
        LocalDate dueDate, UUID planId, UUID milestoneId,
        List<UUID> registerEntryIds
    );

    Deliverable mutate(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID deliverableId,
        String operation, String requestId, String requestHash, long expectedVersion,
        String reason, String title, String summary, UUID ownerUserId,
        LocalDate dueDate, String versionLabel, String versionNote,
        List<MaterialInput> materials, Map<UUID, Long> materialVersions,
        List<String> reviewItems, List<UUID> requiredSignerIds, int quorum,
        String conclusion, String comment
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
