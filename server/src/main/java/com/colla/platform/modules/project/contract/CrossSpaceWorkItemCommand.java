package com.colla.platform.modules.project.contract;

import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Public canonical WorkItem field/state command used by S17 and S18 orchestration. */
public interface CrossSpaceWorkItemCommand {
    EndpointSnapshot snapshot(CurrentUser actor, UUID spaceId, UUID workItemId);

    CommandResult update(
        CurrentUser actor,
        UUID spaceId,
        UUID workItemId,
        String title,
        JsonNode fieldPatch,
        long expectedVersion,
        String requestId
    );

    CommandResult transition(
        CurrentUser actor,
        UUID spaceId,
        UUID workItemId,
        String actionKey,
        String fromStateKey,
        JsonNode fieldPatch,
        long expectedVersion,
        String requestId
    );

    record EndpointSnapshot(
        UUID spaceId,
        UUID workItemId,
        UUID typeId,
        UUID typeVersionId,
        String configHash,
        String title,
        JsonNode fieldValues,
        String lifecycleStatus,
        long version
    ) {
    }

    record CommandResult(UUID workItemId, long version, String status) {
    }
}
