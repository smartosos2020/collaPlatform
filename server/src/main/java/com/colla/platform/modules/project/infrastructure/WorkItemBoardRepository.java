package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardOrder;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreference;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreferenceCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemBoardRepository {
    Optional<BoardPreference> findPreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    );

    BoardPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        BoardPreferenceCommand command
    );

    List<BoardOrder> listOrders(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<UUID> workItemIds
    );

    BoardOrder reserveOrder(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId,
        String columnKey,
        String swimlaneKey,
        long rank,
        long expectedOrderVersion,
        long sourceWorkItemVersion
    );

    BoardOrder alignOrderSourceVersion(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId,
        long expectedOrderVersion,
        long sourceWorkItemVersion
    );

    Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String operation,
        String requestId
    );

    CommandRecord beginCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId,
        String operation,
        String requestId,
        String requestHash,
        long expectedVersion
    );

    void completeCommand(UUID commandId, String responseJson);

    void recordRender(
        UUID workspaceId,
        UUID spaceId,
        String viewKey,
        int columnCount,
        int laneCount,
        int cardCount
    );

    record CommandRecord(
        UUID id,
        String requestHash,
        String status,
        String responseJson
    ) {
    }
}
