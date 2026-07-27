package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreference;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.WindowIndexEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemCalendarRepository {
    Optional<CalendarPreference> findPreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    );

    CalendarPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        CalendarPreferenceCommand command
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

    void replaceWindowIndex(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<WindowIndexEntry> entries
    );

    void recordRender(
        UUID workspaceId,
        UUID spaceId,
        String viewKey,
        int windowDays,
        int eventCount,
        int overlapLanes
    );

    record CommandRecord(
        UUID id,
        String requestHash,
        String status,
        String responseJson
    ) {
    }
}
