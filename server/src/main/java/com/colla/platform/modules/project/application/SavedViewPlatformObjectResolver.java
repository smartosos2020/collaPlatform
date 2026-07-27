package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectResolver;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemSavedViewRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class SavedViewPlatformObjectResolver implements PlatformObjectResolver {
    private final WorkItemSavedViewRepository views;
    private final ProjectSpaceRepository spaces;

    public SavedViewPlatformObjectResolver(
        WorkItemSavedViewRepository views,
        ProjectSpaceRepository spaces
    ) {
        this.views = views;
        this.spaces = spaces;
    }

    @Override
    public String objectType() {
        return "saved_view";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(
        UUID workspaceId,
        UUID actorId,
        UUID objectId
    ) {
        return find(workspaceId, objectId).map(view -> {
            var space = spaces.findById(workspaceId, view.spaceId(), actorId);
            if (space.isEmpty()
                || !space.get().isMember()
                || "archived".equals(space.get().status())
                || views.findAccessible(workspaceId, view.spaceId(), actorId, objectId).isEmpty()) {
                return PlatformObjectSummary.unavailable(
                    objectType(), objectId, ObjectAccessState.forbidden
                );
            }
            return new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                view.name(),
                "Saved WorkItem View",
                view.status(),
                "/project-spaces/" + view.spaceId() + "/work-items?savedViewId=" + objectId,
                "colla://saved-view/" + objectId,
                Map.of(
                    "spaceId", view.spaceId().toString(),
                    "version", view.aggregateVersion(),
                    "sourceModule", "project"
                )
            );
        });
    }

    @Override
    public ObjectAccessState accessState(UUID workspaceId, UUID actorId, UUID objectId) {
        return resolve(workspaceId, actorId, objectId)
            .map(PlatformObjectSummary::accessState)
            .orElse(ObjectAccessState.not_found);
    }

    private Optional<com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedView> find(
        UUID workspaceId,
        UUID objectId
    ) {
        return views.findSpaceId(workspaceId, objectId)
            .flatMap(spaceId -> views.findAny(workspaceId, spaceId, objectId));
    }
}
