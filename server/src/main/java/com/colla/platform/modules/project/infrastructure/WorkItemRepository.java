package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivity;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemParticipant;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemComment;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.FieldProjection;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface WorkItemRepository {
    Optional<LockedType> lockCurrentType(UUID workspaceId, UUID spaceId, UUID typeId);

    Optional<LockedType> findCurrentType(UUID workspaceId, UUID spaceId, UUID typeId);

    long nextNumber(UUID workspaceId, UUID spaceId, UUID typeId);

    void insert(NewWorkItem item);

    Optional<WorkItem> find(UUID workspaceId, UUID spaceId, UUID workItemId);

    Optional<WorkItem> lock(UUID workspaceId, UUID spaceId, UUID workItemId);

    Optional<UUID> findSpaceId(UUID workspaceId, UUID workItemId);

    List<WorkItem> list(UUID workspaceId, UUID spaceId, UUID typeId, UUID cursor, int limit);

    int update(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String title,
        JsonNode fieldValues,
        UUID actorId,
        long expectedVersion
    );

    int workflowUpdate(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        JsonNode fieldValues,
        UUID actorId,
        long expectedVersion
    );

    int workflowBindingUpdate(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID expectedTypeVersionId,
        String expectedConfigHash,
        UUID targetTypeVersionId,
        String targetConfigHash,
        JsonNode fieldValues,
        UUID actorId,
        long expectedVersion
    );

    int transition(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String expectedStatus,
        String targetStatus,
        UUID actorId,
        long expectedVersion
    );

    void replaceFieldProjections(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        List<FieldProjection> projections
    );

    int rebuildFieldProjections(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        List<FieldProjection> projections
    );

    List<WorkItemParticipant> listParticipants(UUID workspaceId, UUID spaceId, UUID workItemId);

    Optional<WorkItemParticipant> findParticipant(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId
    );

    void upsertParticipant(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId,
        String role,
        UUID actorId
    );

    int removeParticipant(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId
    );

    long countResponsibleParticipants(UUID workspaceId, UUID spaceId, UUID workItemId);

    int touch(UUID workspaceId, UUID spaceId, UUID workItemId, UUID actorId, long expectedVersion);

    void appendActivity(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String activityType,
        UUID actorId,
        JsonNode publicPayload
    );

    List<WorkItemActivity> listActivities(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        Long beforeSequence,
        int limit
    );

    List<WorkItemComment> listComments(UUID workspaceId, UUID spaceId, UUID workItemId);

    void insertComment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID authorId,
        String content
    );

    List<AttachmentLink> listAttachments(UUID workspaceId, UUID spaceId, UUID workItemId);

    int insertAttachment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID fileId,
        UUID actorId
    );

    List<WorkItem> queryByProjection(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String fieldKey,
        String operator,
        FieldProjection queryValue,
        String sortDirection,
        int limit
    );

    boolean tryStartCommand(CommandStart command);

    Optional<CommandReceipt> findCommand(UUID workspaceId, String operation, String requestId);

    void completeCommand(UUID commandId, UUID workItemId, JsonNode response);

    record LockedType(
        UUID typeId,
        UUID versionId,
        String typeKey,
        String typeName,
        String typeStatus,
        String spaceStatus,
        String configHash
    ) {
    }

    record NewWorkItem(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        long itemNumber,
        String displayKey,
        String title,
        JsonNode fieldValues,
        UUID actorId
    ) {
    }

    record CommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String operation,
        String requestId,
        String requestHash,
        UUID actorId
    ) {
    }

    record CommandReceipt(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String operation,
        String requestId,
        String requestHash,
        String status,
        JsonNode response,
        UUID createdBy
    ) {
    }

    record AttachmentLink(
        UUID id,
        UUID fileId,
        UUID createdBy,
        Instant createdAt
    ) {
    }
}
