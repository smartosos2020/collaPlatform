package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ResourceScheduleModels.SchedulePreference;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.SavePreferenceCommand;
import java.util.Optional;
import java.util.UUID;

public interface ResourceScheduleRepository {
    Optional<SchedulePreference> findPreference(UUID workspaceId, UUID spaceId, UUID userId);
    SchedulePreference savePreference(
        UUID workspaceId, UUID spaceId, UUID userId,
        SavePreferenceCommand command, String hash
    );
    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    );
    void saveCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation,
        String requestId, String hash, Object response
    );
    record CommandRecord(String requestHash, String responseJson) {
    }
}
