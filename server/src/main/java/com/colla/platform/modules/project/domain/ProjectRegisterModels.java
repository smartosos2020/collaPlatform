package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectRegisterModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_REFERENCES = 100;
    public static final int MAX_RESPONSES = 20;
    public static final int MAX_HISTORY = 100;

    private ProjectRegisterModels() {
    }

    public record ReferenceInput(
        UUID id,
        String sourceType,
        UUID sourceId
    ) {
    }

    public record ResponseInput(
        UUID id,
        String responseType,
        String description,
        UUID ownerUserId,
        LocalDate dueDate,
        String status
    ) {
    }

    public record PlanAction(
        UUID planId,
        long expectedPlanVersion,
        String operation,
        String requestId
    ) {
    }

    public record CreateCommand(
        int schemaVersion,
        String requestId,
        String entryType,
        String title,
        String summary,
        UUID ownerUserId,
        LocalDate dueDate,
        Integer probability,
        Integer impact,
        String decisionBasis,
        String changeImpact,
        List<ReferenceInput> references,
        List<ResponseInput> responses
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
        Integer probability,
        Integer impact,
        String decisionBasis,
        String changeImpact,
        UUID supersedesEntryId,
        String verification,
        List<ReferenceInput> references,
        List<ResponseInput> responses,
        PlanAction planAction
    ) {
    }

    public record RegisterSummary(
        UUID id,
        String entryType,
        String title,
        String summary,
        String status,
        UUID ownerUserId,
        LocalDate dueDate,
        Integer probability,
        Integer impact,
        int score,
        String decisionBasis,
        String changeImpact,
        UUID supersedesEntryId,
        String verification,
        long version,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record RegisterReference(
        UUID id,
        String sourceType,
        UUID sourceId,
        long sourceVersion
    ) {
    }

    public record ResponsePlan(
        UUID id,
        String responseType,
        String description,
        UUID ownerUserId,
        LocalDate dueDate,
        String status
    ) {
    }

    public record RegisterHistory(
        long sequence,
        String operation,
        String fromStatus,
        String toStatus,
        String reason,
        UUID actorId,
        long entryVersion,
        Instant occurredAt
    ) {
    }

    public record RegisterEntry(
        RegisterSummary entry,
        List<RegisterReference> references,
        List<ResponsePlan> responses,
        List<RegisterHistory> history,
        boolean referencesTruncated
    ) {
    }
}
