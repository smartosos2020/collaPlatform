package com.colla.platform.modules.project.contract;

import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal canonical creation boundary for non-project modules.
 *
 * <p>Callers select an already-visible project space and active WorkItem type. They never pass
 * legacy project/issue identities or import project-private DTOs.</p>
 */
public interface WorkItemCreationCommand {
    CreatedWorkItem create(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String title,
        Map<String, Object> fieldValues,
        String requestId
    );

    record CreatedWorkItem(
        UUID id,
        UUID spaceId,
        UUID typeId,
        String typeKey,
        String displayKey,
        String title,
        long version,
        String webPath
    ) {
    }
}
