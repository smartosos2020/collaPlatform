package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CrossSpaceRelationPolicy;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrossSpaceRelationRepository {
    List<CrossSpaceRelationPolicy> listPolicies(
        UUID workspaceId, UUID spaceId, int limit
    );

    List<LinkIntent> listIntents(UUID workspaceId, UUID spaceId, int limit);

    Optional<CrossSpaceRelationPolicy> findPolicy(
        UUID workspaceId, UUID policyId, boolean lock
    );

    Optional<LinkIntent> findIntent(UUID workspaceId, UUID intentId, boolean lock);

    CrossSpaceRelationPolicy createPolicy(
        UUID workspaceId,
        UUID actorId,
        UUID grantId,
        UUID sourceSpaceId,
        UUID targetSpaceId,
        String relationKey,
        String direction,
        UUID sourceTypeId,
        UUID sourceVersionId,
        String sourceConfigHash,
        UUID targetTypeId,
        UUID targetVersionId,
        String targetConfigHash
    );

    int transitionPolicy(
        UUID workspaceId,
        UUID policyId,
        long expectedVersion,
        UUID actorId,
        String action,
        String party
    );

    LinkIntent createIntent(
        UUID workspaceId,
        UUID actorId,
        CrossSpaceRelationPolicy policy,
        UUID sourceWorkItemId,
        long sourceVersion,
        UUID targetWorkItemId,
        long targetVersion
    );

    int completeIntent(
        UUID workspaceId,
        UUID intentId,
        long expectedVersion,
        UUID actorId,
        String action,
        UUID relationId,
        String reasonHash
    );

    Optional<CommandReceipt> findReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId
    );

    void saveReceipt(
        UUID workspaceId,
        UUID actorId,
        String operation,
        String requestId,
        String requestHash,
        JsonNode response
    );

    record CommandReceipt(String requestHash, JsonNode response) {
    }
}
