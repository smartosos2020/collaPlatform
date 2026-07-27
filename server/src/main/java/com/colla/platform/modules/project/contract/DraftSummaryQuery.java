package com.colla.platform.modules.project.contract;

import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Minimal, owner-filtered draft summary port. Consumers must not read project draft tables.
 */
public interface DraftSummaryQuery {
    List<DraftSummary> listOwn(CurrentUser user, int limit);

    record DraftSummary(
        UUID draftId,
        UUID spaceId,
        String spaceName,
        UUID typeId,
        String typeName,
        String status,
        long version,
        Instant updatedAt,
        String recoveryPath
    ) {
    }
}
