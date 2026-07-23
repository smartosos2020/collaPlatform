package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import java.util.List;
import java.util.UUID;

public interface WorkItemFieldOptionRepository {
    List<FieldOption> listByField(UUID workspaceId, UUID spaceId, UUID typeId, UUID fieldId);

    void insert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        ConfigureFieldOption option,
        UUID actorId
    );

    int update(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String optionKey,
        ConfigureFieldOption option,
        UUID actorId
    );
}
