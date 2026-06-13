package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Optional;
import java.util.UUID;

public interface PlatformObjectResolver {
    String objectType();

    Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId);
}
