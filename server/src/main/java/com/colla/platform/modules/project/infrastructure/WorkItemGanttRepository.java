package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreference;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.ScheduleIndexEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemGanttRepository {
    Optional<GanttPreference> findPreference(
        UUID workspaceId, UUID spaceId, UUID userId, String viewKey
    );

    GanttPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        GanttPreferenceCommand command
    );

    void replaceScheduleIndex(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<ScheduleIndexEntry> entries
    );

    void recordRender(
        UUID workspaceId,
        UUID spaceId,
        String viewKey,
        int rowCount,
        int dependencyCount,
        int maxDepth
    );
}
