package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.contract.PersonalCollaborationQuery;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ActivityPage;
import com.colla.platform.modules.project.contract.PersonalWorkQuery;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkItem;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkPage;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.WorkBucket;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.WorkBucketView;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PersonalWorkControllerIntegrationTests {
    private static final UUID WORKSPACE =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SPACE =
        UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM =
        UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void preservesCapabilitiesAndPublishesTheSameAvailableActionsContract() throws Exception {
        PersonalWorkQuery personalWork = mock(PersonalWorkQuery.class);
        PersonalCollaborationQuery collaboration = mock(PersonalCollaborationQuery.class);
        CurrentUser user = user();
        when(personalWork.list(eq(user), eq(SPACE), isNull(), eq(50))).thenReturn(page());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MockMvc mvc = mvc(personalWork, collaboration, mapper);
        MvcResult result = mvc.perform(get("/api/personal-work")
                .param("spaceId", SPACE.toString())
                .principal(new UsernamePasswordAuthenticationToken(user, null)))
            .andExpect(status().isOk())
            .andReturn();

        var response = mapper.readTree(result.getResponse().getContentAsByteArray());
        var item = response.at("/buckets/0/items/0");
        assertThat(item.path("capabilities")).isEqualTo(item.path("availableActions"));
        assertThat(item.path("availableActions").toString())
            .isEqualTo("[\"view\",\"edit\"]");
        verify(personalWork).list(user, SPACE, null, 50);
    }

    @Test
    void forwardsOptionalSpaceScopeToActivities() throws Exception {
        PersonalWorkQuery personalWork = mock(PersonalWorkQuery.class);
        PersonalCollaborationQuery collaboration = mock(PersonalCollaborationQuery.class);
        CurrentUser user = user();
        when(collaboration.activities(eq(user), eq(SPACE), eq(123L), eq(30)))
            .thenReturn(new ActivityPage(List.of(), null, 0, 0, false, NOW));

        MockMvc mvc = mvc(
            personalWork,
            collaboration,
            new ObjectMapper().findAndRegisterModules()
        );
        mvc.perform(get("/api/personal-work/activities")
                .param("spaceId", SPACE.toString())
                .param("before", "123")
                .principal(new UsernamePasswordAuthenticationToken(user, null)))
            .andExpect(status().isOk());

        verify(collaboration).activities(user, SPACE, 123L, 30);
    }

    private MockMvc mvc(
        PersonalWorkQuery personalWork,
        PersonalCollaborationQuery collaboration,
        ObjectMapper mapper
    ) {
        return MockMvcBuilders
            .standaloneSetup(new PersonalWorkController(personalWork, collaboration))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
    }

    private PersonalWorkPage page() {
        PersonalWorkItem item = new PersonalWorkItem(
            ITEM,
            SPACE,
            "Operations",
            "task",
            "Task",
            "TASK-1",
            "Visible item",
            "active",
            3,
            NOW,
            List.of(),
            List.of("view", "edit"),
            "/project-spaces/" + SPACE + "/work-items/" + ITEM
        );
        return new PersonalWorkPage(
            List.of(new WorkBucketView(WorkBucket.responsible, 1, List.of(item))),
            null,
            false,
            NOW
        );
    }

    private CurrentUser user() {
        return new CurrentUser(
            USER,
            WORKSPACE,
            UUID.randomUUID(),
            "member",
            "Member",
            Set.of("member"),
            Set.of()
        );
    }
}
