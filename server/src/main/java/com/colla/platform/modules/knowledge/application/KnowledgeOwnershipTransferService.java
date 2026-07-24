package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.contract.KnowledgeOwnershipTransfer;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeOwnershipTransferService implements KnowledgeOwnershipTransfer {
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeOwnershipTransferService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int transfer(UUID workspaceId, UUID actorId, UUID previousOwnerId, UUID nextOwnerId) {
        return jdbcTemplate.update(
            """
                update knowledge_base_spaces
                set owner_id = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and owner_id = ? and deleted_at is null
                """,
            nextOwnerId,
            actorId,
            workspaceId,
            previousOwnerId
        );
    }
}
