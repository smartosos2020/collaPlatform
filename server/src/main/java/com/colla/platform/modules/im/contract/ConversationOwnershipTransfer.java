package com.colla.platform.modules.im.contract;

import java.util.UUID;

/**
 * Transfers ownership of active conversations during member offboarding.
 */
public interface ConversationOwnershipTransfer {

    int transfer(UUID workspaceId, UUID previousOwnerId, UUID nextOwnerId);
}
