package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.contract.WorkItemCreationCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CanonicalWorkItemCreationAdapter implements WorkItemCreationCommand {
    private final WorkItemService workItems;
    private final ObjectMapper objectMapper;

    public CanonicalWorkItemCreationAdapter(WorkItemService workItems, ObjectMapper objectMapper) {
        this.workItems = workItems;
        this.objectMapper = objectMapper;
    }

    @Override
    public CreatedWorkItem create(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String title,
        Map<String, Object> fieldValues,
        String requestId
    ) {
        WorkItemView created = workItems.create(
            user,
            spaceId,
            typeId,
            title,
            objectMapper.valueToTree(fieldValues == null ? Map.of() : fieldValues),
            requestId
        );
        var item = created.item();
        return new CreatedWorkItem(
            item.id(),
            item.spaceId(),
            item.typeDefinitionId(),
            item.typeKey(),
            item.displayKey(),
            item.title(),
            item.version(),
            "/project-spaces/" + item.spaceId() + "/work-items/" + item.id()
        );
    }
}
