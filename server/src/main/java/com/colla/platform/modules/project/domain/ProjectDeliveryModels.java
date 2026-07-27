package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectDeliveryModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_DELIVERABLES = 100;
    public static final int MAX_VERSIONS = 50;
    public static final int MAX_MATERIALS = 50;
    public static final int MAX_REVIEW_ITEMS = 30;
    public static final int MAX_SIGNERS = 30;

    private ProjectDeliveryModels() {
    }

    public record MaterialInput(
        UUID id,
        String sourceType,
        UUID sourceId,
        String externalUri
    ) {
    }

    public record CreateCommand(
        int schemaVersion,
        String requestId,
        String title,
        String summary,
        UUID ownerUserId,
        LocalDate dueDate,
        UUID planId,
        UUID milestoneId,
        List<UUID> registerEntryIds
    ) {
    }

    public record MutateCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String operation,
        String reason,
        String title,
        String summary,
        UUID ownerUserId,
        LocalDate dueDate,
        String versionLabel,
        String versionNote,
        List<MaterialInput> materials,
        List<String> reviewItems,
        List<UUID> requiredSignerIds,
        Integer quorum,
        String conclusion,
        String comment
    ) {
    }

    public record DeliverableSummary(
        UUID id,
        String title,
        String summary,
        String status,
        UUID ownerUserId,
        LocalDate dueDate,
        UUID planId,
        UUID milestoneId,
        List<UUID> registerEntryIds,
        UUID currentVersionId,
        long version,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record MaterialReference(
        UUID id,
        String sourceType,
        UUID sourceId,
        long sourceVersion,
        String externalUri
    ) {
    }

    public record DeliverableVersion(
        UUID id,
        int sequence,
        String label,
        String note,
        UUID submittedBy,
        Instant submittedAt,
        List<MaterialReference> materials
    ) {
    }

    public record Signoff(
        long sequence,
        UUID signerId,
        String conclusion,
        String comment,
        boolean revoked,
        Instant occurredAt
    ) {
    }

    public record ReviewRound(
        UUID id,
        int round,
        UUID deliverableVersionId,
        List<String> reviewItems,
        List<UUID> requiredSignerIds,
        int quorum,
        String status,
        String conclusion,
        List<Signoff> signoffs,
        Instant openedAt,
        Instant closedAt
    ) {
    }

    public record Acceptance(
        long sequence,
        String conclusion,
        String comment,
        UUID actorId,
        UUID reviewId,
        Instant occurredAt
    ) {
    }

    public record Deliverable(
        DeliverableSummary deliverable,
        List<DeliverableVersion> versions,
        List<ReviewRound> reviews,
        List<Acceptance> acceptances,
        boolean materialsTruncated
    ) {
    }
}
