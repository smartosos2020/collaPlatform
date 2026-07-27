package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterEntry;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.ReferenceInput;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.ResponseInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRegisterRepository {
    List<RegisterSummary> list(UUID workspaceId, UUID spaceId, String entryType, int limit);

    Optional<RegisterEntry> find(
        UUID workspaceId, UUID spaceId, UUID entryId, int historyLimit
    );

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    );

    RegisterEntry create(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId,
        String requestHash, String entryType, String title, String summary,
        UUID ownerUserId, LocalDate dueDate, Integer probability, Integer impact,
        String decisionBasis, String changeImpact, List<ReferenceInput> references,
        List<ResponseInput> responses, Map<UUID, Long> sourceVersions
    );

    RegisterEntry mutate(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID entryId,
        String operation, String requestId, String requestHash, long expectedVersion,
        String reason, String title, String summary, UUID ownerUserId,
        LocalDate dueDate, Integer probability, Integer impact,
        String decisionBasis, String changeImpact, UUID supersedesEntryId,
        String verification, List<ReferenceInput> references,
        List<ResponseInput> responses, Map<UUID, Long> sourceVersions
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
