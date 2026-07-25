package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDefinition;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemLayoutRepository {
    Optional<LayoutDefinition> findByKind(UUID workspaceId, UUID spaceId, UUID typeId, String layoutKind);

    Optional<LayoutDefinition> findById(UUID workspaceId, UUID spaceId, UUID typeId, UUID layoutId);

    List<LayoutNode> listNodes(UUID workspaceId, UUID layoutId);

    List<FieldAccessPolicy> listPolicies(UUID workspaceId, UUID layoutId);

    void insertLayout(LayoutDefinitionInsert definition);

    int updateLayout(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID layoutId,
        String configHash,
        UUID actorId,
        long expectedAggregateVersion
    );

    void replaceNodes(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID layoutId,
        List<LayoutNode> nodes,
        UUID actorId
    );

    void replacePolicies(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID layoutId,
        List<FieldAccessPolicy> policies,
        UUID actorId
    );

    record LayoutDefinitionInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String layoutKind,
        String configHash,
        UUID actorId
    ) {
    }
}
