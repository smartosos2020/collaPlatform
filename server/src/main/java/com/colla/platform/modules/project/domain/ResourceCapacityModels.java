package com.colla.platform.modules.project.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ResourceCapacityModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ALLOCATIONS = 200;
    public static final int MAX_BUCKETS = 366;

    private ResourceCapacityModels() {
    }

    public record MutateAllocationCommand(
        int schemaVersion,
        String requestId,
        String operation,
        UUID allocationId,
        long expectedVersion,
        UUID workItemId,
        UUID userId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal allocationPercent,
        String reason
    ) {
    }

    public record SaveCapacityRuleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        UUID userId,
        int dailyMinutes,
        BigDecimal warningPercent
    ) {
    }

    public record Allocation(
        UUID id,
        UUID workItemId,
        UUID userId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal allocationPercent,
        String status,
        long version,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record CapacityRule(
        UUID id,
        UUID userId,
        int dailyMinutes,
        BigDecimal warningPercent,
        long version,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record LoadBucket(
        UUID userId,
        LocalDate date,
        int capacityMinutes,
        int allocatedMinutes,
        int actualMinutes,
        String signal,
        boolean conflict,
        String explanation
    ) {
    }

    public record CapacityFoundation(
        List<Allocation> allocations,
        List<CapacityRule> rules,
        List<LoadBucket> buckets,
        boolean truncated
    ) {
    }
}
