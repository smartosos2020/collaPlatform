package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves migration identities explicitly. UUID reuse is still recorded by the
 * migration map; an occupied UUID is deterministically remapped and never guessed.
 */
@Component
public class WorkItemLegacyIdentityResolver {
    private static final String CONFLICT_SEED = "colla:legacy-work-item-conflict:";

    private final WorkItemRepository repository;

    public WorkItemLegacyIdentityResolver(WorkItemRepository repository) {
        this.repository = repository;
    }

    public IdentityResolution resolve(
        UUID workspaceId,
        UUID spaceId,
        String sourceType,
        UUID sourceId
    ) {
        if (repository.findSpaceId(workspaceId, sourceId).isEmpty()) {
            return new IdentityResolution(sourceId, "uuid_reused");
        }
        for (int attempt = 0; attempt < 100; attempt++) {
            UUID candidate = UUID.nameUUIDFromBytes(
                (CONFLICT_SEED + workspaceId + ":" + spaceId + ":" + sourceType + ":"
                    + sourceId + ":" + attempt).getBytes(StandardCharsets.UTF_8)
            );
            if (repository.findSpaceId(workspaceId, candidate).isEmpty()) {
                return new IdentityResolution(candidate, "uuid_conflict_remapped");
            }
        }
        throw new IllegalStateException("WORK_ITEM_ID_CONFLICT_EXHAUSTED");
    }

    public record IdentityResolution(UUID targetId, String decision) {
    }
}
