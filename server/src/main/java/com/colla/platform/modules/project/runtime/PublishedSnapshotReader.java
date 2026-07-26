package com.colla.platform.modules.project.runtime;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import java.util.Optional;
import java.util.UUID;

public interface PublishedSnapshotReader {
    Optional<PublishedConfigurationVersion> findPublishedSnapshot(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    );
}
