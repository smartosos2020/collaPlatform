package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CrossSpaceRelationModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_POLICIES = 50;
    public static final int MAX_INTENTS = 100;
    public static final int MAX_CANDIDATES = 50;

    private CrossSpaceRelationModels() {
    }

    public record SaveRelationPolicyCommand(
        int schemaVersion,
        String requestId,
        UUID grantId,
        String relationKey,
        String direction,
        UUID sourceTypeId,
        UUID sourceVersionId,
        UUID targetTypeId,
        UUID targetVersionId
    ) {
    }

    public record RelationPolicyLifecycleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action,
        String party,
        String reason
    ) {
    }

    public record CrossSpaceRelationPolicy(
        UUID id,
        UUID grantId,
        UUID sourceSpaceId,
        UUID targetSpaceId,
        String relationKey,
        String direction,
        UUID sourceTypeId,
        UUID sourceVersionId,
        String sourceConfigHash,
        UUID targetTypeId,
        UUID targetVersionId,
        String targetConfigHash,
        String status,
        long version,
        UUID sourceConfirmedBy,
        UUID targetConfirmedBy,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record CreateLinkIntentCommand(
        int schemaVersion,
        String requestId,
        long expectedPolicyVersion,
        UUID sourceWorkItemId,
        long expectedSourceVersion,
        UUID targetWorkItemId,
        long expectedTargetVersion
    ) {
    }

    public record LinkIntentCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action,
        String reason
    ) {
    }

    public record LinkIntent(
        UUID id,
        UUID policyId,
        long policyVersion,
        UUID sourceSpaceId,
        UUID sourceWorkItemId,
        long sourceExpectedVersion,
        UUID targetSpaceId,
        UUID targetWorkItemId,
        long targetExpectedVersion,
        String status,
        long version,
        UUID sourceConfirmedBy,
        UUID targetConfirmedBy,
        UUID canonicalRelationId,
        Instant updatedAt
    ) {
    }

    public record EndpointReference(
        UUID spaceId,
        UUID workItemId,
        String opaqueReference,
        String typeKey,
        boolean active,
        long version
    ) {
    }

    public record RelationFoundation(
        int schemaVersion,
        List<String> directions,
        List<CrossSpaceRelationPolicy> policies,
        List<LinkIntent> intents,
        boolean policiesTruncated,
        boolean intentsTruncated
    ) {
    }
}
