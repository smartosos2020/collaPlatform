package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AutomationConnectorModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CONNECTORS = 50;
    public static final int MAX_DELIVERIES = 100;
    private AutomationConnectorModels() {}

    public record Connector(
        UUID id, String name, String targetUri, String credentialReference,
        String status, int signingVersion, int version, Instant updatedAt
    ) {}
    public record DeliveryAttempt(
        int attemptNumber, String outcome, Integer httpStatus,
        String errorCode, int durationMs, Instant attemptedAt
    ) {}
    public record Delivery(
        UUID id, UUID connectorId, UUID runId, int payloadVersion,
        String payloadHash, String status, int attemptCount,
        Instant nextAttemptAt, List<DeliveryAttempt> attempts,
        String deadLetterReason, Instant createdAt, Instant completedAt
    ) {}
    public record ConnectorFoundation(
        int schemaVersion, List<Connector> connectors, List<Delivery> deliveries,
        boolean connectorsTruncated, boolean deliveriesTruncated,
        int maxPayloadBytes, int connectTimeoutMs, int responseTimeoutMs
    ) {}
    public record SaveConnectorCommand(
        int schemaVersion, String requestId, UUID connectorId, int expectedVersion,
        String name, String targetUri, String credentialReference
    ) {}
    public record TestDeliveryCommand(
        int schemaVersion, String requestId, String payload, boolean dryRun
    ) {}
    public record DeliveryGovernanceCommand(
        int schemaVersion, String requestId, String action, String reason
    ) {}
}
