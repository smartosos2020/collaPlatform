package com.colla.platform.modules.project.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Public S10 canonical command boundary for cross-space relation edges.
 * S18 callers must not access the edge or history tables directly.
 */
public interface CrossSpaceRelationCommand {
    CanonicalRelationReference create(CreateCommand command);

    Optional<CanonicalRelationReference> find(UUID workspaceId, UUID relationId);

    CanonicalRelationReference withdraw(WithdrawCommand command);

    record CreateCommand(
        UUID workspaceId,
        UUID actorId,
        UUID sourceSpaceId,
        UUID sourceWorkItemId,
        long expectedSourceVersion,
        UUID targetSpaceId,
        UUID targetWorkItemId,
        long expectedTargetVersion,
        String relationKey,
        String direction,
        UUID sourceDefinitionTypeId,
        UUID sourceDefinitionVersionId,
        String sourceDefinitionHash,
        UUID targetDefinitionTypeId,
        UUID targetDefinitionVersionId,
        String targetDefinitionHash,
        UUID policyId,
        long policyVersion
    ) {
    }

    record WithdrawCommand(
        UUID workspaceId,
        UUID actorId,
        UUID relationId,
        long expectedVersion,
        String reasonHash
    ) {
    }

    record CanonicalRelationReference(
        UUID relationId,
        UUID sourceSpaceId,
        UUID sourceWorkItemId,
        UUID targetSpaceId,
        UUID targetWorkItemId,
        String relationKey,
        String direction,
        String status,
        long version,
        UUID policyId,
        long policyVersion,
        Instant updatedAt
    ) {
    }
}
