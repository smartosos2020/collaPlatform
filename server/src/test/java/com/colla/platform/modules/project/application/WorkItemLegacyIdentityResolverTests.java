package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemLegacyIdentityResolverTests {
    @Test
    void reusesUnoccupiedUuidButReturnsAnExplicitDecision() {
        WorkItemRepository repository = mock(WorkItemRepository.class);
        when(repository.findSpaceId(any(), any())).thenReturn(Optional.empty());
        var resolver = new WorkItemLegacyIdentityResolver(repository);
        UUID sourceId = UUID.randomUUID();

        var result = resolver.resolve(UUID.randomUUID(), UUID.randomUUID(), "issue", sourceId);

        assertThat(result.targetId()).isEqualTo(sourceId);
        assertThat(result.decision()).isEqualTo("uuid_reused");
    }

    @Test
    void deterministicallyRemapsAnOccupiedUuid() {
        WorkItemRepository repository = mock(WorkItemRepository.class);
        UUID occupiedSpace = UUID.randomUUID();
        when(repository.findSpaceId(any(), any()))
            .thenReturn(Optional.of(occupiedSpace))
            .thenReturn(Optional.empty());
        var resolver = new WorkItemLegacyIdentityResolver(repository);
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        var first = resolver.resolve(workspaceId, spaceId, "issue", sourceId);
        when(repository.findSpaceId(any(), any()))
            .thenReturn(Optional.of(occupiedSpace))
            .thenReturn(Optional.empty());
        var replay = resolver.resolve(workspaceId, spaceId, "issue", sourceId);

        assertThat(first.targetId()).isNotEqualTo(sourceId);
        assertThat(first.targetId()).isEqualTo(replay.targetId());
        assertThat(first.decision()).isEqualTo("uuid_conflict_remapped");
    }
}
