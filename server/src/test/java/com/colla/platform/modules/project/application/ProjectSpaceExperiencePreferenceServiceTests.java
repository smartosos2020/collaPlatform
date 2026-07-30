package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreference;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreferenceConflictException;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceExperiencePreferenceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProjectSpaceExperiencePreferenceServiceTests {
    @Test
    void defaultsToSimpleAndOnlyCurrentManagersCanPersistAdvancedMode() {
        Fixture fixture = fixture("owner");
        when(fixture.preferences().find(
            fixture.user().workspaceId(),
            fixture.spaceId(),
            fixture.user().id()
        )).thenReturn(Optional.empty());

        var current = fixture.service().get(fixture.user(), fixture.spaceId());

        assertThat(current.mode()).isEqualTo("simple");
        assertThat(current.version()).isZero();
        assertThat(current.availableModes()).containsExactly("simple", "advanced");

        when(fixture.preferences().save(
            fixture.user().workspaceId(),
            fixture.spaceId(),
            fixture.user().id(),
            1,
            "advanced",
            0
        )).thenReturn(new ExperiencePreference(1, "advanced", 1, Instant.now()));
        assertThat(fixture.service().save(
            fixture.user(), fixture.spaceId(), 1, "advanced", 0
        ).mode()).isEqualTo("advanced");

        Fixture member = fixture("member");
        assertThatThrownBy(() -> member.service().save(
            member.user(), member.spaceId(), 1, "advanced", 0
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("management capability");
        verify(member.preferences(), never()).save(
            member.user().workspaceId(),
            member.spaceId(),
            member.user().id(),
            1,
            "advanced",
            0
        );

        for (String role : new String[]{"guest", null}) {
            Fixture readOnly = fixture(role);
            when(readOnly.preferences().find(
                readOnly.user().workspaceId(),
                readOnly.spaceId(),
                readOnly.user().id()
            )).thenReturn(Optional.empty());
            assertThat(readOnly.service().get(readOnly.user(), readOnly.spaceId()).availableModes())
                .containsExactly("simple");
            assertThatThrownBy(() -> readOnly.service().save(
                readOnly.user(), readOnly.spaceId(), 1, "advanced", 0
            )).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void resetUsesCasAndReturnsTheSafeDefault() {
        Fixture fixture = fixture("owner");
        ExperiencePreference current =
            new ExperiencePreference(1, "advanced", 4, Instant.now());
        when(fixture.preferences().find(
            fixture.user().workspaceId(),
            fixture.spaceId(),
            fixture.user().id()
        )).thenReturn(Optional.of(current));

        var reset = fixture.service().reset(fixture.user(), fixture.spaceId(), 4);

        verify(fixture.preferences()).reset(
            fixture.user().workspaceId(),
            fixture.spaceId(),
            fixture.user().id(),
            4
        );
        assertThat(reset.mode()).isEqualTo("simple");
        assertThat(reset.version()).isZero();
    }

    @Test
    void recalibrationDoesNotTreatAStoredAdvancedModeAsAuthorization() {
        Fixture member = fixture("member");
        when(member.preferences().find(
            member.user().workspaceId(),
            member.spaceId(),
            member.user().id()
        )).thenReturn(Optional.of(
            new ExperiencePreference(1, "advanced", 7, Instant.now())
        ));

        var current = member.service().get(member.user(), member.spaceId());

        assertThat(current.mode()).isEqualTo("simple");
        assertThat(current.version()).isEqualTo(7);
        assertThat(current.availableModes()).containsExactly("simple");
    }

    @Test
    void repositoryConflictIsExposedAsHttpConflict() {
        Fixture fixture = fixture("owner");
        when(fixture.preferences().save(
            fixture.user().workspaceId(),
            fixture.spaceId(),
            fixture.user().id(),
            1,
            "simple",
            3
        )).thenThrow(new ExperiencePreferenceConflictException());

        assertThatThrownBy(() -> fixture.service().save(
            fixture.user(), fixture.spaceId(), 1, "simple", 3
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("preference changed");
    }

    private Fixture fixture(String role) {
        ProjectSpaceService spaces = mock(ProjectSpaceService.class);
        ProjectSpaceExperiencePreferenceRepository preferences =
            mock(ProjectSpaceExperiencePreferenceRepository.class);
        CurrentUser user = new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "actor",
            "Actor",
            Set.of(),
            Set.of()
        );
        UUID spaceId = UUID.randomUUID();
        when(spaces.getVisible(user, spaceId)).thenReturn(space(user.workspaceId(), spaceId, role));
        return new Fixture(
            user,
            spaceId,
            preferences,
            new ProjectSpaceExperiencePreferenceService(spaces, preferences)
        );
    }

    private ProjectSpaceSummary space(UUID workspaceId, UUID spaceId, String role) {
        Instant now = Instant.now();
        return new ProjectSpaceSummary(
            spaceId,
            workspaceId,
            "space",
            "Space",
            "",
            "active",
            "discoverable",
            1,
            role,
            role == null ? 0 : 1,
            UUID.randomUUID(),
            now,
            UUID.randomUUID(),
            now,
            null,
            null
        );
    }

    private record Fixture(
        CurrentUser user,
        UUID spaceId,
        ProjectSpaceExperiencePreferenceRepository preferences,
        ProjectSpaceExperiencePreferenceService service
    ) {
    }
}
