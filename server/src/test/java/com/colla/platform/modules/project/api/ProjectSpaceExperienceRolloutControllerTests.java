package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.application.ProjectSpaceExperienceRolloutService;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutState;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutView;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryPolicy;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

class ProjectSpaceExperienceRolloutControllerTests {
    @Test
    void delegatesTheAuthenticatedVisibilityBoundEvaluation() {
        ProjectSpaceExperienceRolloutService service =
            mock(ProjectSpaceExperienceRolloutService.class);
        ProjectSpaceExperienceRolloutController controller =
            new ProjectSpaceExperienceRolloutController(service);
        CurrentUser user = user();
        UUID spaceId = UUID.randomUUID();
        RolloutView expected = new RolloutView(
            1,
            "s21-m7-v1",
            true,
            RolloutState.enabled,
            "canonical_project_space",
            Instant.now(),
            30,
            new TelemetryPolicy(1, true, 1000, 20)
        );
        when(service.get(user, spaceId)).thenReturn(expected);

        var result = controller.get(
            spaceId,
            new UsernamePasswordAuthenticationToken(user, null)
        );

        assertThat(result).isEqualTo(expected);
        verify(service).get(user, spaceId);
        assertThat(ProjectSpaceExperienceRolloutController.class
            .getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/project-spaces/{spaceId}/experience-rollout");
    }

    @Test
    void refusesCallsWithoutACurrentUserPrincipal() {
        ProjectSpaceExperienceRolloutService service =
            mock(ProjectSpaceExperienceRolloutService.class);
        ProjectSpaceExperienceRolloutController controller =
            new ProjectSpaceExperienceRolloutController(service);
        UUID spaceId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.get(spaceId, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401");
        assertThatThrownBy(() -> controller.get(
            spaceId,
            new UsernamePasswordAuthenticationToken("anonymous", null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401");
        verifyNoInteractions(service);
    }

    private CurrentUser user() {
        return new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "actor",
            "Actor",
            Set.of(),
            Set.of()
        );
    }
}
