package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.AutomationConnectorModels.Connector;
import com.colla.platform.modules.project.domain.AutomationConnectorModels.Delivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationConnectorRepository {
    List<Connector> list(UUID workspaceId, UUID spaceId, int limit);
    List<Delivery> deliveries(UUID workspaceId, UUID spaceId, int limit);
    Optional<Connector> find(UUID workspaceId, UUID spaceId, UUID id);
    Connector save(UUID workspaceId, UUID spaceId, UUID id, int expectedVersion,
                   String name, String targetUri, String credentialReference);
    Delivery beginDelivery(UUID workspaceId, UUID spaceId, UUID connectorId,
                           UUID runId, String payloadHash, String nonce);
    Delivery recordAttempt(UUID workspaceId, UUID spaceId, UUID deliveryId,
                           String outcome, Integer httpStatus, String errorCode,
                           int durationMs, boolean retryable);
    Delivery govern(UUID workspaceId, UUID spaceId, UUID deliveryId, String action, String reason);
}
