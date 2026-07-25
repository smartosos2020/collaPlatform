package com.colla.platform.modules.project.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.application.WorkItemFieldAccessPolicyEvaluator;
import com.colla.platform.modules.project.application.WorkItemFieldAccessPolicyEvaluator.EvaluationContext;
import com.colla.platform.modules.project.application.WorkItemLayoutConditionDsl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemLayoutSampleVisibilityIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private WorkItemFieldAccessPolicyEvaluator policyEvaluator;

    @MockitoSpyBean
    private WorkItemLayoutConditionDsl conditionDsl;

    @Test
    void userSampleDoesNotDiscloseOrEvaluateHiddenFieldValues() throws Exception {
        TestUser root = root("wil-sample-visibility-root");
        TestUser owner = member(root.token(), "sampleowner");
        TestUser ordinary = member(root.token(), "samplemember");
        UUID spaceId = createSpace(owner.token(), "wil-sample-visibility");
        addSpaceMember(spaceId, ordinary.id(), "member", owner.id());
        UUID typeId = UUID.fromString(
            createType(owner.token(), spaceId, "sample_visibility").get("id").asText()
        );
        JsonNode publicField = createField(owner.token(), spaceId, typeId, "public_trigger");
        String hiddenKey = "classified_" + suffix();
        JsonNode hiddenField = createField(owner.token(), spaceId, typeId, hiddenKey);
        JsonNode gatedField = createField(owner.token(), spaceId, typeId, "gated_result");
        saveLayout(
            owner.token(),
            spaceId,
            typeId,
            sampleLayoutBody(publicField, hiddenField, gatedField)
        );

        mockMvc.perform(post(samplePath(spaceId, typeId))
                .header("Authorization", bearer(ordinary.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldValues\":{\"public_trigger\":\"ready\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessProjection.public_trigger.mode").value("write"))
            .andExpect(jsonPath("$.accessProjection.gated_result.mode").value("write"))
            .andExpect(jsonPath("$.accessProjection." + hiddenKey).doesNotExist());

        clearInvocations(policyEvaluator);
        MvcResult hiddenResult = mockMvc.perform(post(samplePath(spaceId, typeId))
                .header("Authorization", bearer(ordinary.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldValues":{"%s":"unlock"}}
                    """.formatted(hiddenKey)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"))
            .andReturn();
        assertEvaluatorNeverReceived(hiddenKey);

        clearInvocations(policyEvaluator);
        String unknownKey = "unknown_" + suffix();
        MvcResult unknownResult = mockMvc.perform(post(samplePath(spaceId, typeId))
                .header("Authorization", bearer(ordinary.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldValues":{"%s":"unlock"}}
                    """.formatted(unknownKey)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"))
            .andReturn();
        assertEvaluatorNeverReceived(unknownKey);

        JsonNode hiddenResponse = json(hiddenResult);
        JsonNode unknownResponse = json(unknownResult);
        assertEquals(hiddenResponse.path("error"), unknownResponse.path("error"));
        assertEquals(
            "Work item layout is not available",
            hiddenResponse.path("error").path("message").asText()
        );
    }

    private void assertEvaluatorNeverReceived(String rejectedKey) {
        ArgumentCaptor<EvaluationContext> contexts =
            ArgumentCaptor.forClass(EvaluationContext.class);
        verify(policyEvaluator, atLeastOnce()).evaluate(any(), contexts.capture());
        assertFalse(contexts.getAllValues().isEmpty());
        assertTrue(contexts.getAllValues().stream()
            .allMatch(context -> !context.fieldValues().containsKey(rejectedKey)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, JsonNode>> conditionValues =
            ArgumentCaptor.forClass(Map.class);
        verify(conditionDsl, atLeastOnce()).evaluate(
            any(),
            conditionValues.capture(),
            any()
        );
        assertFalse(conditionValues.getAllValues().isEmpty());
        assertTrue(conditionValues.getAllValues().stream()
            .allMatch(values -> !values.containsKey(rejectedKey)));
    }

    private void saveLayout(
        String token,
        UUID spaceId,
        UUID typeId,
        String body
    ) throws Exception {
        mockMvc.perform(put(
                "/api/project-spaces/" + spaceId + "/configuration/types/" + typeId
                    + "/layouts/create"
            )
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wil-sample-layout-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    private String sampleLayoutBody(
        JsonNode publicField,
        JsonNode hiddenField,
        JsonNode gatedField
    ) {
        UUID sectionId = UUID.randomUUID();
        String hiddenKey = hiddenField.get("fieldKey").asText();
        return """
            {
              "nodes":[
                {"id":"%s","nodeKey":"sample_main","nodeType":"section","sortOrder":0,
                 "config":{},"visibilityCondition":{"schemaVersion":1}},
                {"id":"%s","parentId":"%s","nodeKey":"public_trigger_node","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":0,
                 "config":{},"visibilityCondition":{"schemaVersion":1}},
                {"id":"%s","parentId":"%s","nodeKey":"classified_node","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":1,
                 "config":{},"visibilityCondition":{"schemaVersion":1}},
                {"id":"%s","parentId":"%s","nodeKey":"gated_result_node","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":2,
                 "config":{},"visibilityCondition":{"schemaVersion":1,"expression":{
                   "kind":"predicate","source":"field","fieldId":"%s",
                   "fieldKey":"%s","operator":"is_empty"
                 }}}
              ],
              "policies":[
                {"id":"%s","fieldId":"%s","fieldKey":"%s","policyKey":"public_access",
                 "policy":{"schemaVersion":1,"default":{"mode":"write","required":false},"rules":[]}},
                {"id":"%s","fieldId":"%s","fieldKey":"%s","policyKey":"classified_access",
                 "policy":{"schemaVersion":1,"default":{"mode":"write","required":false},
                   "rules":[
                     {"ruleKey":"member_hidden","roles":["member"],
                      "mode":"hidden","required":false}
                   ]}},
                {"id":"%s","fieldId":"%s","fieldKey":"%s","policyKey":"gated_access",
                 "policy":{"schemaVersion":1,"default":{"mode":"write","required":false},
                   "rules":[
                     {"ruleKey":"hidden_value_must_not_apply","roles":["member"],
                      "mode":"hidden","required":false,
                      "when":{"schemaVersion":1,"expression":{
                        "kind":"predicate","source":"field","fieldId":"%s",
                        "fieldKey":"%s","operator":"eq","value":"unlock"
                      }}}
                   ]}}
              ],
              "aggregateVersion":0
            }
            """.formatted(
            sectionId,
            UUID.randomUUID(), sectionId,
            publicField.get("id").asText(), publicField.get("fieldKey").asText(),
            UUID.randomUUID(), sectionId,
            hiddenField.get("id").asText(), hiddenKey,
            UUID.randomUUID(), sectionId,
            gatedField.get("id").asText(), gatedField.get("fieldKey").asText(),
            hiddenField.get("id").asText(), hiddenKey,
            UUID.randomUUID(), publicField.get("id").asText(), publicField.get("fieldKey").asText(),
            UUID.randomUUID(), hiddenField.get("id").asText(), hiddenKey,
            UUID.randomUUID(), gatedField.get("id").asText(), gatedField.get("fieldKey").asText(),
            hiddenField.get("id").asText(), hiddenKey
        );
    }

    private JsonNode createField(String token, UUID spaceId, UUID typeId, String key)
        throws Exception {
        return json(mockMvc.perform(post(
                "/api/project-spaces/" + spaceId + "/configuration/types/" + typeId + "/fields"
            )
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wil-sample-field-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"%s","name":"%s","fieldType":"text","config":{},"sortOrder":10}
                    """.formatted(key, key)))
            .andExpect(status().isOk())
            .andReturn());
    }

    private JsonNode createType(String token, UUID spaceId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/project-spaces/" + spaceId + "/configuration/types")
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wil-sample-type-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeKey\":\"" + key + "\",\"name\":\"" + key
                    + "\",\"sortOrder\":10}"))
            .andExpect(status().isOk())
            .andReturn());
    }

    private UUID createSpace(String token, String prefix) throws Exception {
        String key = prefix + "-" + suffix();
        return UUID.fromString(json(mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceKey\":\"" + key + "\",\"name\":\"" + prefix
                    + "\",\"visibility\":\"private\"}"))
            .andExpect(status().isOk())
            .andReturn()).get("id").asText());
    }

    private TestUser root(String fingerprint) throws Exception {
        UUID id = jdbcTemplate.queryForObject(
            "select id from users where username='admin'",
            UUID.class
        );
        return new TestUser(
            id,
            login("admin", "admin123456", fingerprint + "-" + suffix())
        );
    }

    private TestUser member(String rootToken, String prefix) throws Exception {
        String username = prefix + suffix();
        JsonNode response = json(mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(rootToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"member123456","displayName":"%s",
                     "email":"%s@example.com","roleCode":"member"}
                    """.formatted(username, prefix, username)))
            .andExpect(status().isOk())
            .andReturn());
        UUID id = UUID.fromString(response.get("id").asText());
        return new TestUser(
            id,
            login(username, "member123456", prefix + "-" + suffix())
        );
    }

    private String login(String username, String password, String fingerprint)
        throws Exception {
        return json(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","deviceType":"web",
                     "deviceFingerprint":"%s","deviceName":"MockMvc","appVersion":"test"}
                    """.formatted(username, password, fingerprint)))
            .andExpect(status().isOk())
            .andReturn()).get("accessToken").asText();
    }

    private void addSpaceMember(UUID spaceId, UUID userId, String role, UUID actorId) {
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from project_spaces where id=?",
            UUID.class,
            spaceId
        );
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId,
            workspaceId,
            spaceId,
            userId,
            actorId,
            actorId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments
                    (id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at)
                values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            spaceId,
            memberId,
            role,
            actorId
        );
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String samplePath(UUID spaceId, UUID typeId) {
        return "/api/project-spaces/" + spaceId + "/types/" + typeId
            + "/layouts/create/sample";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record TestUser(UUID id, String token) {
    }
}
