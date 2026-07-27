package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.PersonalWorkModels.PersonalCandidate;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PersonalWorkRepository {
    List<PersonalCandidate> listCandidates(
        UUID workspaceId,
        UUID userId,
        Instant beforeUpdatedAt,
        UUID beforeWorkItemId,
        int limit
    );

    void synchronizeProjection(
        UUID workspaceId,
        UUID userId,
        List<PersonalWorkItem> visibleItems,
        Instant refreshedAt
    );

    void markInvalidated(
        UUID workspaceId,
        UUID userId,
        String sourceKey,
        long sourceVersion,
        Instant invalidatedAt
    );

    void invalidateKnownUsers(
        UUID workspaceId,
        UUID workItemId,
        String sourceKey,
        long sourceVersion,
        Instant invalidatedAt
    );
}
