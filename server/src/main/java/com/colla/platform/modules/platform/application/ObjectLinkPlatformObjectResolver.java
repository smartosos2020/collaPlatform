package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ObjectLinkPlatformObjectResolver implements PlatformObjectResolver {
    private final String objectType;
    private final PlatformObjectRepository objectRepository;

    public ObjectLinkPlatformObjectResolver(String objectType, PlatformObjectRepository objectRepository) {
        this.objectType = objectType;
        this.objectRepository = objectRepository;
    }

    @Override
    public String objectType() {
        return objectType;
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        return objectRepository.findObjectLink(currentUser.workspaceId(), objectType, objectId)
            .map(link -> {
                ObjectAccessState state = link.deletedAt() == null ? ObjectAccessState.available : ObjectAccessState.deleted;
                return new PlatformObjectSummary(
                    link.objectType(),
                    link.objectId(),
                    state,
                    state == ObjectAccessState.available ? link.titleSnapshot() : null,
                    null,
                    null,
                    state == ObjectAccessState.available ? link.webPath() : null,
                    state == ObjectAccessState.available ? link.deepLink() : null,
                    Map.of()
                );
            });
    }
}
