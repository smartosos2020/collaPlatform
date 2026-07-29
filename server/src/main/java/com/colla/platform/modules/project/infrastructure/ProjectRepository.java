package com.colla.platform.modules.project.infrastructure;

import java.util.UUID;

/**
 * History-only access required to authorize immutable legacy deep-link maps.
 *
 * <p>No legacy project or issue product reads/writes may be added here.</p>
 */
public interface ProjectRepository {
    boolean legacyProjectExists(UUID workspaceId, UUID projectId);

    boolean isProjectMember(UUID workspaceId, UUID projectId, UUID userId);
}
