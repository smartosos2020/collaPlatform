package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectNavigation;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectReference;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlatformObjectService {
    private final PlatformObjectResolverRegistry resolverRegistry;
    private final PlatformObjectRepository objectRepository;

    public PlatformObjectService(PlatformObjectResolverRegistry resolverRegistry, PlatformObjectRepository objectRepository) {
        this.resolverRegistry = resolverRegistry;
        this.objectRepository = objectRepository;
    }

    public PlatformObjectNavigation navigation(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            recordAccess(currentUser, summary);
        }
        String webPath = summary.webPath();
        return new PlatformObjectNavigation(
            summary,
            webPath,
            summary.deepLink(),
            webPath == null ? "/" : webPath
        );
    }

    public PlatformObjectSummary markAccessed(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            recordAccess(currentUser, summary);
        }
        return summary;
    }

    public List<PlatformObjectSummary> recent(CurrentUser currentUser, int limit) {
        return objectRepository.listRecentAccesses(currentUser.workspaceId(), currentUser.id(), limit).stream()
            .map(reference -> resolveReference(currentUser, reference))
            .toList();
    }

    public List<PlatformObjectSummary> favorites(CurrentUser currentUser, int limit) {
        return objectRepository.listFavorites(currentUser.workspaceId(), currentUser.id(), limit).stream()
            .map(reference -> resolveReference(currentUser, reference))
            .toList();
    }

    public PlatformObjectSummary addFavorite(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            objectRepository.addFavorite(currentUser.workspaceId(), currentUser.id(), objectType, objectId);
            recordAccess(currentUser, summary);
        }
        return summary;
    }

    public void removeFavorite(CurrentUser currentUser, String objectType, UUID objectId) {
        objectRepository.removeFavorite(currentUser.workspaceId(), currentUser.id(), objectType, objectId);
    }

    private PlatformObjectSummary resolveReference(CurrentUser currentUser, PlatformObjectReference reference) {
        return resolverRegistry.resolve(currentUser, reference.objectType(), reference.objectId());
    }

    private void recordAccess(CurrentUser currentUser, PlatformObjectSummary summary) {
        objectRepository.recordRecentAccess(
            currentUser.workspaceId(),
            currentUser.id(),
            summary.objectType(),
            summary.objectId(),
            summary.webPath(),
            summary.deepLink(),
            summary.title()
        );
    }
}
