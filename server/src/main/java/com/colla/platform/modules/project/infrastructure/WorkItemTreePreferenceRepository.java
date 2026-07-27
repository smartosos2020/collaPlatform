package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreference;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreferenceCommand;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemTreePreferenceRepository {
    Optional<TreePreference> find(UUID workspaceId, UUID spaceId, UUID userId, String viewKey);

    TreePreference save(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        TreePreferenceCommand command
    );
}
