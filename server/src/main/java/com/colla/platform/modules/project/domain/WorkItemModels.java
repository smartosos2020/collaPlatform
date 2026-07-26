package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class WorkItemModels {
    private WorkItemModels() {
    }

    public enum WorkItemStatus {
        active,
        archived;

        public static WorkItemStatus parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_WORK_ITEM_STATUS", "Invalid work item status");
            }
        }
    }

    public record WorkItem(
        UUID id,
        UUID workspaceId,
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
        String status,
        long version,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Instant archivedAt
    ) {
    }

    public record WorkItemView(
        WorkItem item,
        JsonNode fieldValues,
        JsonNode runtime,
        List<String> availableActions
    ) {
    }

    public record WorkItemCreateForm(
        UUID typeDefinitionId,
        UUID typeVersionId,
        String typeKey,
        String typeName,
        JsonNode runtime
    ) {
    }

    public record WorkItemPage(List<WorkItemView> items, UUID nextCursor) {
    }

    public record WorkItemParticipant(
        UUID id,
        UUID userId,
        String displayName,
        String role,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record WorkItemActivity(
        UUID id,
        long sequence,
        String type,
        UUID actorId,
        String actorDisplayName,
        JsonNode payload,
        Instant occurredAt
    ) {
    }

    public record WorkItemParticipantState(
        long workItemVersion,
        List<WorkItemParticipant> participants
    ) {
    }

    public record WorkItemActivityPage(
        List<WorkItemActivity> items,
        Long nextBeforeSequence
    ) {
    }

    public record WorkItemComment(
        UUID id,
        UUID authorId,
        String authorDisplayName,
        String content,
        long version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record WorkItemCommentState(long workItemVersion, List<WorkItemComment> comments) {
    }

    public record WorkItemAttachment(
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

    public record WorkItemAttachmentState(
        long workItemVersion,
        List<WorkItemAttachment> attachments
    ) {
    }

    public static WorkItemRuntimeException failure(String code, String message) {
        return new WorkItemRuntimeException(code, message);
    }

    public static WorkItemRuntimeException failure(String code, String message, Throwable cause) {
        return new WorkItemRuntimeException(code, message, cause);
    }

    public static final class WorkItemRuntimeException extends RuntimeException {
        private final String code;

        public WorkItemRuntimeException(String code, String message) {
            super(message);
            this.code = code;
        }

        public WorkItemRuntimeException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
