package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ResourceCapacityModels.Allocation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityRule;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.MutateAllocationCommand;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.SaveCapacityRuleCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceCapacityRepository {
    List<Allocation> listAllocations(UUID workspaceId, UUID spaceId, int limit);
    List<CapacityRule> listRules(UUID workspaceId, UUID spaceId, int limit);
    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    );
    Allocation mutateAllocation(
        UUID workspaceId, UUID spaceId, UUID actorId,
        MutateAllocationCommand command, String hash
    );
    CapacityRule saveRule(
        UUID workspaceId, UUID spaceId, UUID actorId,
        SaveCapacityRuleCommand command, String hash
    );
    record CommandRecord(String requestHash, String responseJson) {
    }
}
