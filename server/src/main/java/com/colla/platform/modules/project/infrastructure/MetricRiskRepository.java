package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicy;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicyVersion;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignal;
import com.colla.platform.modules.project.domain.MetricRiskModels.SignalCandidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricRiskRepository {
    List<RiskPolicy> listPolicies(UUID workspaceId, UUID spaceId, int limit);

    Optional<RiskPolicy> findPolicy(UUID workspaceId, UUID spaceId, UUID policyId);

    List<RiskSignal> listSignals(UUID workspaceId, UUID spaceId, int limit);

    Optional<RiskSignal> findSignal(UUID workspaceId, UUID spaceId, UUID signalId);

    Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId
    );

    RiskPolicy savePolicy(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID policyId,
        String policyKey,
        String name,
        String description,
        List<String> signalTypes,
        String severity,
        int cooldownHours,
        long expectedVersion,
        String requestId,
        String requestHash
    );

    RiskPolicyVersion publishPolicy(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID policyId,
        long expectedVersion,
        String definitionHash,
        String requestId,
        String requestHash
    );

    List<RiskSignal> upsertSignals(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        List<SignalCandidate> candidates,
        String requestId,
        String requestHash
    );

    RiskSignal act(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID signalId,
        String action,
        String reason,
        long expectedVersion,
        String requestId,
        String requestHash
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
