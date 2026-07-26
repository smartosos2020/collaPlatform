package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectResolver;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkItemPlatformObjectResolver implements PlatformObjectResolver {
    private final WorkItemRepository repository;
    private final ProjectSpaceRepository spaceRepository;

    public WorkItemPlatformObjectResolver(
        WorkItemRepository repository,
        ProjectSpaceRepository spaceRepository
    ) {
        this.repository = repository;
        this.spaceRepository = spaceRepository;
    }

    @Override
    public String objectType() {
        return "work_item";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(
        UUID workspaceId,
        UUID actorId,
        UUID objectId
    ) {
        return repository.find(workspaceId, findSpaceId(workspaceId, objectId).orElse(null), objectId)
            .flatMap(item -> {
                var space = spaceRepository.findById(workspaceId, item.spaceId(), actorId);
                if (space.isEmpty() || !space.get().isMember() || "archived".equals(space.get().status())) {
                    return Optional.of(PlatformObjectSummary.unavailable(
                        "work_item", objectId, ObjectAccessState.forbidden
                    ));
                }
                return Optional.of(new PlatformObjectSummary(
                    "work_item",
                    objectId,
                    ObjectAccessState.available,
                    item.displayKey() + " " + item.title(),
                    item.typeName(),
                    item.status(),
                    "/project-spaces/" + item.spaceId() + "/work-items/" + item.id(),
                    "colla://work-item/" + item.id(),
                    Map.of(
                        "spaceId", item.spaceId().toString(),
                        "typeDefinitionId", item.typeDefinitionId().toString(),
                        "typeVersionId", item.typeVersionId().toString(),
                        "configHash", item.configHash(),
                        "version", item.version(),
                        "sourceModule", "project"
                    )
                ));
            });
    }

    @Override
    public ObjectAccessState accessState(UUID workspaceId, UUID actorId, UUID objectId) {
        return resolve(workspaceId, actorId, objectId)
            .map(PlatformObjectSummary::accessState)
            .orElse(ObjectAccessState.not_found);
    }

    private Optional<UUID> findSpaceId(UUID workspaceId, UUID objectId) {
        return repository.findSpaceId(workspaceId, objectId);
    }
}
