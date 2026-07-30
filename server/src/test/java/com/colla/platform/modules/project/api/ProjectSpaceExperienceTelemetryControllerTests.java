package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.application.ProjectSpaceExperienceTelemetryService;
import com.colla.platform.modules.project.api.ProjectSpaceExperienceTelemetryController.TelemetryBatchRequest;
import com.colla.platform.modules.project.api.ProjectSpaceExperienceTelemetryController.TelemetryEventRequest;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEventCommand;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

class ProjectSpaceExperienceTelemetryControllerTests {
    private static final String VALID_EVENT_JSON = """
        {
          "eventId": "123e4567-e89b-42d3-a456-426614174000",
          "eventKind": "entry",
          "routeKey": "overview",
          "mode": "simple",
          "outcome": "shown",
          "durationBucket": "under_5s",
          "errorCode": "none",
          "freshness": "fresh"
        }
        """;

    @Test
    void mapsTheLowSensitiveBatchAndReturnsNoContent() {
        ProjectSpaceExperienceTelemetryService service =
            mock(ProjectSpaceExperienceTelemetryService.class);
        ProjectSpaceExperienceTelemetryController controller =
            new ProjectSpaceExperienceTelemetryController(service);
        UUID eventId = UUID.randomUUID();
        TelemetryEventRequest event = new TelemetryEventRequest(
            eventId,
            "entry",
            "overview",
            "simple",
            "shown",
            "under_5s",
            "none",
            "fresh"
        );

        var response = controller.record(
            new TelemetryBatchRequest(1, List.of(event)),
            new UsernamePasswordAuthenticationToken(user(), null)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).record(1, List.of(new TelemetryEventCommand(
            eventId,
            "entry",
            "overview",
            "simple",
            "shown",
            "under_5s",
            "none",
            "fresh"
        )));
        assertThat(ProjectSpaceExperienceTelemetryController.class
            .getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/project-space-experience/telemetry");
    }

    @Test
    void refusesCallsWithoutACurrentUserPrincipal() {
        ProjectSpaceExperienceTelemetryController controller =
            new ProjectSpaceExperienceTelemetryController(
                mock(ProjectSpaceExperienceTelemetryService.class)
            );
        TelemetryBatchRequest request = new TelemetryBatchRequest(
            1,
            List.of(new TelemetryEventRequest(
                UUID.randomUUID(),
                "entry",
                "overview",
                "simple",
                "shown",
                "under_5s",
                "none",
                "fresh"
            ))
        );

        assertThatThrownBy(() -> controller.record(request, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401");
    }

    @Test
    void acceptsTheStrictJsonAllowlistThroughMvc() throws Exception {
        ProjectSpaceExperienceTelemetryService service =
            mock(ProjectSpaceExperienceTelemetryService.class);
        MockMvc mvc = mvc(service);

        mvc.perform(post("/api/project-space-experience/telemetry")
                .principal(new UsernamePasswordAuthenticationToken(user(), null))
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "schemaVersion": 1,
                      "events": [%s]
                    }
                    """.formatted(VALID_EVENT_JSON)))
            .andExpect(status().isNoContent());

        verify(service).record(
            1,
            List.of(new TelemetryEventCommand(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                "entry",
                "overview",
                "simple",
                "shown",
                "under_5s",
                "none",
                "fresh"
            ))
        );
    }

    @Test
    void rejectsSensitiveOrUnknownJsonPropertiesThroughMvc() throws Exception {
        for (String property : List.of("title", "body", "spaceId", "userId")) {
            ProjectSpaceExperienceTelemetryService service =
                mock(ProjectSpaceExperienceTelemetryService.class);
            MockMvc mvc = mvc(service);
            String eventWithSensitiveProperty = VALID_EVENT_JSON.replace(
                "\n}",
                ",\n  \"" + property + "\": \"sensitive\"\n}"
            );

            mvc.perform(post("/api/project-space-experience/telemetry")
                    .principal(new UsernamePasswordAuthenticationToken(user(), null))
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "schemaVersion": 1,
                          "events": [%s]
                        }
                        """.formatted(eventWithSensitiveProperty)))
                .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        ProjectSpaceExperienceTelemetryService service =
            mock(ProjectSpaceExperienceTelemetryService.class);
        MockMvc mvc = mvc(service);
        mvc.perform(post("/api/project-space-experience/telemetry")
                .principal(new UsernamePasswordAuthenticationToken(user(), null))
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "schemaVersion": 1,
                      "spaceId": "sensitive",
                      "events": [%s]
                    }
                    """.formatted(VALID_EVENT_JSON)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOversizedStringFieldsThroughMvc() throws Exception {
        Map<String, String> validValues = Map.of(
            "eventKind", "entry",
            "routeKey", "overview",
            "mode", "simple",
            "outcome", "shown",
            "durationBucket", "under_5s",
            "errorCode", "none",
            "freshness", "fresh"
        );

        for (var entry : validValues.entrySet()) {
            ProjectSpaceExperienceTelemetryService service =
                mock(ProjectSpaceExperienceTelemetryService.class);
            MockMvc mvc = mvc(service);
            String oversizedEvent = VALID_EVENT_JSON.replace(
                "\"" + entry.getKey() + "\": \"" + entry.getValue() + "\"",
                "\"" + entry.getKey() + "\": \"" + "x".repeat(33) + "\""
            );

            mvc.perform(post("/api/project-space-experience/telemetry")
                    .principal(new UsernamePasswordAuthenticationToken(user(), null))
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "schemaVersion": 1,
                          "events": [%s]
                        }
                        """.formatted(oversizedEvent)))
                .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }
    }

    private MockMvc mvc(ProjectSpaceExperienceTelemetryService service) {
        ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return MockMvcBuilders
            .standaloneSetup(new ProjectSpaceExperienceTelemetryController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
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
