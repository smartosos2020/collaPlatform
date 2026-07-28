package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.CollaborationSlice;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.SavePreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.CrossTeamPanoramaPreferenceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossTeamPanoramaServiceTests {
    @Test
    void rejectsPreferenceOutsideBoundedWindowBeforePersistence() {
        CrossTeamPanoramaPreferenceRepository repository =
            mock(CrossTeamPanoramaPreferenceRepository.class);
        WorkItemRelationAccessDecisionService access =
            mock(WorkItemRelationAccessDecisionService.class);
        CrossTeamPanoramaService service = new CrossTeamPanoramaService(
            mock(CrossSpaceGrantService.class),
            mock(CrossSpaceRelationService.class),
            mock(CrossSpaceSyncService.class),
            repository,
            access
        );
        CurrentUser user = new CurrentUser(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "actor", "Actor", Set.of(), Set.of()
        );

        assertThatThrownBy(() -> service.savePreference(
            user, UUID.randomUUID(),
            new SavePreferenceCommand(1, "panorama-invalid-1", 0, false, 91)
        )).isInstanceOf(WorkItemRuntimeException.class);
        verify(repository, never()).save(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void sliceContractCannotCopyContentOrPermissionSnapshots() {
        assertThat(Arrays.stream(CollaborationSlice.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain(
                "title", "fieldValues", "stateBody", "memberNames",
                "permissionSnapshot", "reason"
            );
    }
}
