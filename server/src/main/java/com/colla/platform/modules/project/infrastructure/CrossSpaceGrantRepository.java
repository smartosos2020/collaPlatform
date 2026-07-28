package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.CrossSpaceGrant;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrossSpaceGrantRepository {
    List<CrossSpaceGrant> listVisible(UUID workspaceId, UUID userId, UUID spaceId, int limit);

    Optional<CrossSpaceGrant> find(UUID workspaceId, UUID grantId);

    boolean isCurrentlyAuthorized(UUID workspaceId, UUID grantId);

    List<GrantVersion> listVersions(UUID workspaceId, UUID grantId, int limit);

    CrossSpaceGrant create(
        UUID workspaceId, UUID sourceSpaceId, UUID targetSpaceId, UUID actorId,
        String name, JsonNode scope, String scopeHash
    );

    CrossSpaceGrant revise(
        UUID workspaceId, UUID grantId, UUID actorId, long expectedVersion,
        String name, JsonNode scope, String scopeHash
    );

    CrossSpaceGrant transition(
        UUID workspaceId, UUID grantId, UUID actorId, long expectedVersion,
        String action, String party
    );

    Optional<CommandReceipt> findReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId
    );

    void saveReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId,
        String requestHash, UUID grantId, JsonNode response
    );

    record CommandReceipt(String requestHash, JsonNode response) {
    }
}
