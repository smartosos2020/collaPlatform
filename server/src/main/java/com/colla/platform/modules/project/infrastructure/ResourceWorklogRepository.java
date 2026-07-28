package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ResourceWorklogModels.MutateWorklogCommand;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Worklog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceWorklogRepository {
    List<Worklog> list(UUID workspaceId, UUID spaceId, int limit, int revisionLimit);

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    );

    Worklog mutate(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        MutateWorklogCommand command,
        UUID effectiveUserId,
        String requestHash
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
