package com.colla.platform.modules.platform.contract;

import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Horizontal owner port for rebuildable search documents and permission-calibrated identities.
 * Implementations remain in the module that owns the source facts.
 */
public interface PlatformSearchProjectionProvider {
    int MAX_DECISION_BATCH_SIZE = 200;

    String objectType();

    Optional<SearchDocument> findDocument(UUID workspaceId, UUID objectId);

    List<SearchDocument> listDocuments(UUID workspaceId, UUID afterId, int limit);

    Set<UUID> allowed(
        CurrentUser user,
        List<UUID> objectIds,
        Set<String> participantRoles
    );

    record SearchDocument(
        String objectType,
        UUID objectId,
        String title,
        String excerpt,
        String webPath,
        String deepLink,
        String searchText,
        Instant updatedAt,
        UUID spaceId,
        String objectSubtype,
        String objectStatus,
        long sourceVersion
    ) {
    }
}
