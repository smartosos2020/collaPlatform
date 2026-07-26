package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivity;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemActivityPage;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemAttachment;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemAttachmentState;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemComment;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemCommentState;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemCreateForm;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemPage;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemParticipant;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemParticipantState;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class WorkItemApiDtos {
    private WorkItemApiDtos() {
    }

    static WorkItemResponse response(WorkItemView view) {
        WorkItem item = view.item();
        return new WorkItemResponse(
            item.id(),
            item.spaceId(),
            item.typeDefinitionId(),
            item.typeVersionId(),
            item.typeKey(),
            item.typeName(),
            item.configHash(),
            item.itemNumber(),
            item.displayKey(),
            item.title(),
            view.fieldValues(),
            view.runtime(),
            item.status(),
            item.version(),
            item.createdBy(),
            item.createdAt(),
            item.updatedBy(),
            item.updatedAt(),
            item.archivedAt(),
            view.availableActions()
        );
    }

    static WorkItemPageResponse page(WorkItemPage page) {
        return new WorkItemPageResponse(
            page.items().stream().map(WorkItemApiDtos::response).toList(),
            page.nextCursor()
        );
    }

    static ParticipantListResponse participants(List<WorkItemParticipant> participants) {
        return new ParticipantListResponse(
            participants.stream().map(WorkItemApiDtos::participant).toList()
        );
    }

    static ParticipantMutationResponse participantState(WorkItemParticipantState state) {
        return new ParticipantMutationResponse(
            state.workItemVersion(),
            state.participants().stream().map(WorkItemApiDtos::participant).toList()
        );
    }

    static ActivityPageResponse activities(WorkItemActivityPage page) {
        return new ActivityPageResponse(
            page.items().stream().map(WorkItemApiDtos::activity).toList(),
            page.nextBeforeSequence()
        );
    }

    static WorkItemCreateFormResponse createForm(WorkItemCreateForm form) {
        return new WorkItemCreateFormResponse(
            form.typeDefinitionId(),
            form.typeVersionId(),
            form.typeKey(),
            form.typeName(),
            form.runtime()
        );
    }

    static CommentListResponse comments(List<WorkItemComment> comments) {
        return new CommentListResponse(comments.stream().map(WorkItemApiDtos::comment).toList());
    }

    static CommentMutationResponse commentState(WorkItemCommentState state) {
        return new CommentMutationResponse(
            state.workItemVersion(),
            state.comments().stream().map(WorkItemApiDtos::comment).toList()
        );
    }

    static AttachmentListResponse attachments(List<WorkItemAttachment> attachments) {
        return new AttachmentListResponse(
            attachments.stream().map(WorkItemApiDtos::attachment).toList()
        );
    }

    static AttachmentMutationResponse attachmentState(WorkItemAttachmentState state) {
        return new AttachmentMutationResponse(
            state.workItemVersion(),
            state.attachments().stream().map(WorkItemApiDtos::attachment).toList()
        );
    }

    private static ParticipantResponse participant(WorkItemParticipant participant) {
        return new ParticipantResponse(
            participant.userId(),
            participant.displayName(),
            participant.role(),
            participant.createdBy(),
            participant.createdAt(),
            participant.updatedBy(),
            participant.updatedAt()
        );
    }

    private static ActivityResponse activity(WorkItemActivity activity) {
        return new ActivityResponse(
            activity.sequence(),
            activity.type(),
            activity.actorId(),
            activity.actorDisplayName(),
            activity.payload(),
            activity.occurredAt()
        );
    }

    private static CommentResponse comment(WorkItemComment comment) {
        return new CommentResponse(
            comment.id(),
            comment.authorId(),
            comment.authorDisplayName(),
            comment.content(),
            comment.version(),
            comment.createdAt(),
            comment.updatedAt()
        );
    }

    private static AttachmentResponse attachment(WorkItemAttachment attachment) {
        return new AttachmentResponse(
            attachment.id(),
            attachment.fileId(),
            attachment.fileName(),
            attachment.contentType(),
            attachment.sizeBytes(),
            attachment.createdBy(),
            attachment.createdByDisplayName(),
            attachment.createdAt()
        );
    }

    record WorkItemPageResponse(List<WorkItemResponse> items, UUID nextCursor) {
    }

    record ParticipantListResponse(List<ParticipantResponse> items) {
    }

    record ParticipantMutationResponse(long workItemVersion, List<ParticipantResponse> items) {
    }

    record ParticipantResponse(
        UUID userId,
        String displayName,
        String role,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    record ActivityPageResponse(List<ActivityResponse> items, Long nextBeforeSequence) {
    }

    record ActivityResponse(
        long sequence,
        String type,
        UUID actorId,
        String actorDisplayName,
        JsonNode payload,
        Instant occurredAt
    ) {
    }

    record WorkItemCreateFormResponse(
        UUID typeDefinitionId,
        UUID typeVersionId,
        String typeKey,
        String typeName,
        JsonNode runtime
    ) {
    }

    record CommentListResponse(List<CommentResponse> items) {
    }

    record CommentMutationResponse(long workItemVersion, List<CommentResponse> items) {
    }

    record CommentResponse(
        UUID id,
        UUID authorId,
        String authorDisplayName,
        String content,
        long version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    record AttachmentListResponse(List<AttachmentResponse> items) {
    }

    record AttachmentMutationResponse(long workItemVersion, List<AttachmentResponse> items) {
    }

    record AttachmentResponse(
        UUID id,
        UUID fileId,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID createdBy,
        String createdByDisplayName,
        Instant createdAt
    ) {
    }

    record WorkItemResponse(
        UUID id,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String typeKey,
        String typeName,
        String configHash,
        long itemNumber,
        String displayKey,
        String title,
        JsonNode fieldValues,
        JsonNode runtime,
        String status,
        long version,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Instant archivedAt,
        List<String> availableActions
    ) {
    }
}
