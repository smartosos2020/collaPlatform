package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemViewModels.ExportJob;
import com.colla.platform.modules.project.domain.WorkItemViewModels.PreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewPreference;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemViewRepository {
    Optional<ViewPreference> findPreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    );

    ViewPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        PreferenceCommand command
    );

    ExportRecord createOrFindExport(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String requestId,
        String requestHash,
        JsonNode query,
        JsonNode columns,
        Instant expiresAt
    );

    Optional<ExportRecord> findExport(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        UUID exportId
    );

    record ExportRecord(
        ExportJob job,
        JsonNode query,
        JsonNode columns,
        String requestHash,
        UUID ownerUserId
    ) {
    }
}
