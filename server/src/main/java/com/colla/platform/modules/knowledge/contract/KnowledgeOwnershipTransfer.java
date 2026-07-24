package com.colla.platform.modules.knowledge.contract;

import java.util.UUID;

/**
 * Transfers ownership of active knowledge bases during member offboarding.
 */
public interface KnowledgeOwnershipTransfer {

    int transfer(UUID workspaceId, UUID actorId, UUID previousOwnerId, UUID nextOwnerId);
}
