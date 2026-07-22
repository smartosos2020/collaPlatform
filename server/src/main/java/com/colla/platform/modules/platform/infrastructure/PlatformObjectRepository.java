package com.colla.platform.modules.platform.infrastructure;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectLinkRecord;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectReference;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectTypeRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformObjectRepository {
    Optional<ObjectLinkRecord> findObjectLink(UUID workspaceId, String objectType, UUID objectId);

    Optional<ObjectLinkRecord> findObjectLinkByPath(UUID workspaceId, String pathOrDeepLink);

    void upsertObjectLink(UUID workspaceId, String objectType, UUID objectId, String webPath, String deepLink, String titleSnapshot);

    void markObjectLinkDeleted(UUID workspaceId, String objectType, UUID objectId);

    List<String> listObjectTypes();

    List<PlatformObjectTypeRule> listObjectTypeRules();

    void recordRecentAccess(UUID workspaceId, UUID userId, String objectType, UUID objectId, String webPath, String deepLink, String titleSnapshot);

    List<PlatformObjectReference> listRecentAccesses(UUID workspaceId, UUID userId, int limit);

    void addFavorite(UUID workspaceId, UUID userId, String objectType, UUID objectId);

    void removeFavorite(UUID workspaceId, UUID userId, String objectType, UUID objectId);

    List<PlatformObjectReference> listFavorites(UUID workspaceId, UUID userId, int limit);

    List<PlatformObjectReference> listObjectCandidates(UUID workspaceId, List<String> objectTypes, String query, int limit);
}
