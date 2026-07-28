package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.contract.CrossSpaceWorkItemCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowCommandResult;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class CanonicalCrossSpaceWorkItemAdapter
    implements CrossSpaceWorkItemCommand {
    private final WorkItemService workItems;

    public CanonicalCrossSpaceWorkItemAdapter(WorkItemService workItems) {
        this.workItems = workItems;
    }

    @Override
    public EndpointSnapshot snapshot(
        CurrentUser actor, UUID spaceId, UUID workItemId
    ) {
        WorkItemView view = workItems.get(actor, spaceId, workItemId);
        var item = view.item();
        return new EndpointSnapshot(
            spaceId, item.id(), item.typeDefinitionId(), item.typeVersionId(),
            item.configHash(), item.title(), view.fieldValues(),
            item.status(), item.version()
        );
    }

    @Override
    public CommandResult update(
        CurrentUser actor,
        UUID spaceId,
        UUID workItemId,
        String title,
        JsonNode fieldPatch,
        long expectedVersion,
        String requestId
    ) {
        WorkItemView result = workItems.update(
            actor, spaceId, workItemId, title, fieldPatch, expectedVersion, requestId
        );
        return new CommandResult(
            result.item().id(), result.item().version(), result.item().status()
        );
    }

    @Override
    public CommandResult transition(
        CurrentUser actor,
        UUID spaceId,
        UUID workItemId,
        String actionKey,
        String fromStateKey,
        JsonNode fieldPatch,
        long expectedVersion,
        String requestId
    ) {
        WorkflowCommandResult result = workItems.executeWorkflowAction(
            actor, spaceId, workItemId, actionKey, fromStateKey,
            expectedVersion, fieldPatch, requestId
        );
        return new CommandResult(
            workItemId, result.workItemVersion(), result.toStateKey()
        );
    }
}
