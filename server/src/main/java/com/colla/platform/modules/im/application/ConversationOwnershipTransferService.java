package com.colla.platform.modules.im.application;

import com.colla.platform.modules.im.contract.ConversationOwnershipTransfer;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationOwnershipTransferService implements ConversationOwnershipTransfer {
    private final JdbcTemplate jdbcTemplate;

    public ConversationOwnershipTransferService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int transfer(UUID workspaceId, UUID previousOwnerId, UUID nextOwnerId) {
        return jdbcTemplate.update(
            """
                update conversations
                set owner_id = ?, updated_at = now()
                where workspace_id = ? and owner_id = ? and archived_at is null
                """,
            nextOwnerId,
            workspaceId,
            previousOwnerId
        );
    }
}
