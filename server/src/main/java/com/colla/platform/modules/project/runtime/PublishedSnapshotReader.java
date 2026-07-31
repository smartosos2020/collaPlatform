package com.colla.platform.modules.project.runtime;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublishedSnapshotReader {
    Optional<PublishedConfigurationVersion> findPublishedSnapshot(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    );

    List<PublishedConfigurationVersion> findPublishedSnapshots(
        UUID workspaceId,
        UUID spaceId,
        List<UUID> versionIds
    );
}
