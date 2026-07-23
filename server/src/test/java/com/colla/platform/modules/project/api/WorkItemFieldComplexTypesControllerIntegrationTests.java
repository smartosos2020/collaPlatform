package com.colla.platform.modules.project.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.identity.infrastructure.OrganizationRepository;
import com.colla.platform.modules.identity.infrastructure.UserGroupRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.MinioClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemFieldComplexTypesControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private FileRepository fileRepository;

    @MockitoBean
    private MinioClient minioClient;

    @Test
    void userScopeUsesWorkspaceIdentityFactsAndHidesMissingDisabledAndForeignSubjects() throws Exception {
        TestUser root = root("wif3-user-root");
        TestUser owner = member(root.token(), "wif3owner");
        TestUser candidate = member(root.token(), "wif3candidate");
        UUID spaceId = createSpace(owner.token(), "wif3-user");
        UUID typeId = createType(owner.token(), spaceId, "delivery", "Delivery");
        JsonNode field = createField(owner.token(), spaceId, typeId, "reviewers", "Reviewers", "user");
        UUID departmentId = organizationRepository.createDepartment(
            owner.workspaceId(), null, "dept-" + suffix(), "Engineering", 10, owner.id()
        );
        organizationRepository.addDepartmentMember(
            owner.workspaceId(), departmentId, candidate.id(), "primary", owner.id()
        );
        UUID groupId = userGroupRepository.createGroup(
            owner.workspaceId(), "group-" + suffix(), "Reviewers", "", "normal", owner.id()
        );
        userGroupRepository.addMember(owner.workspaceId(), groupId, "user", candidate.id(), owner.id());

        ObjectNode typeConfig = objectMapper.createObjectNode();
        typeConfig.putArray("allowedSubjectTypes").add("member").add("department").add("user_group");
        typeConfig.putArray("selectionScope")
            .addObject().put("subjectType", "department").put("subjectId", departmentId.toString());
        typeConfig.withArray("selectionScope")
            .addObject().put("subjectType", "user_group").put("subjectId", groupId.toString());
        typeConfig.put("maxSelections", 3);
        ObjectNode body = configuration(
            0, objectMapper.createArrayNode().add(candidate.id().toString()), typeConfig
        );
        JsonNode configured = configure(
            owner.token(), spaceId, typeId, field, "wif3-user-ok-" + suffix(), body, 200
        ).body();
        assertEquals(candidate.id().toString(), configured.at("/config/defaultValue/0").asText());
        assertFalse(configured.toString().contains(candidate.displayName()));

        jdbcTemplate.update(
            "update users set status='disabled', updated_at=now() where workspace_id=? and id=?",
            owner.workspaceId(), candidate.id()
        );
        body.put("aggregateVersion", 1);
        configure(owner.token(), spaceId, typeId, field, "wif3-user-disabled-" + suffix(), body, 400)
            .withError("invalid_complex_field_reference");
        assertEquals(1L, fieldVersion(field));
        jdbcTemplate.update(
            "update users set status='active', updated_at=now() where workspace_id=? and id=?",
            owner.workspaceId(), candidate.id()
        );

        UUID foreignWorkspace = UUID.randomUUID();
        UUID foreignUser = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces(id,name,slug,status,created_at,updated_at) values(?,?,?,'active',now(),now())",
            foreignWorkspace, "Foreign", "foreign-" + suffix()
        );
        jdbcTemplate.update(
            """
                insert into users(id,workspace_id,username,password_hash,display_name,status,created_at,updated_at)
                values(?,?,?,?,?,'active',now(),now())
                """,
            foreignUser, foreignWorkspace, "foreign-" + suffix(), "x", "Foreign"
        );
        body.set("defaultValue", objectMapper.createArrayNode().add(foreignUser.toString()));
        configure(owner.token(), spaceId, typeId, field, "wif3-user-foreign-" + suffix(), body, 400)
            .withError("invalid_complex_field_reference");
        assertEquals(1L, fieldVersion(field));
    }

    @Test
    void temporalUrlAndReferenceContractsNormalizeSafelyWithoutCreatingInstances() throws Exception {
        TestUser root = root("wif3-values-root");
        TestUser owner = member(root.token(), "wif3owner");
        UUID spaceId = createSpace(owner.token(), "wif3-values");
        UUID sourceTypeId = createType(owner.token(), spaceId, "delivery", "Delivery");
        UUID targetTypeId = createType(owner.token(), spaceId, "incident", "Incident");

        JsonNode dateField = createField(owner.token(), spaceId, sourceTypeId, "due_date", "Due date", "date");
        ObjectNode dateConfig = objectMapper.createObjectNode()
            .put("calendar", "iso8601")
            .put("precision", "day")
            .put("defaultStrategy", "none")
            .put("min", "2026-01-01")
            .put("max", "2026-12-31");
        JsonNode date = configure(
            owner.token(), spaceId, sourceTypeId, dateField, "wif3-date-" + suffix(),
            configuration(0, objectMapper.getNodeFactory().textNode("2026-07-23"), dateConfig), 200
        ).body();
        assertEquals("2026-07-23", date.at("/config/defaultValue").asText());

        JsonNode datetimeField = createField(owner.token(), spaceId, sourceTypeId, "started_at", "Started", "datetime");
        ObjectNode datetimeConfig = objectMapper.createObjectNode()
            .put("storageTimezone", "UTC")
            .put("displayTimezone", "Asia/Shanghai")
            .put("precision", "minute")
            .put("defaultStrategy", "none")
            .putNull("min")
            .putNull("max");
        JsonNode datetime = configure(
            owner.token(), spaceId, sourceTypeId, datetimeField, "wif3-datetime-" + suffix(),
            configuration(
                0,
                objectMapper.getNodeFactory().textNode("2026-07-23T12:34:56.789Z"),
                datetimeConfig
            ),
            200
        ).body();
        assertEquals("2026-07-23T12:34:00Z", datetime.at("/config/defaultValue").asText());

        JsonNode urlField = createField(owner.token(), spaceId, sourceTypeId, "source_url", "Source URL", "url");
        ObjectNode urlConfig = objectMapper.createObjectNode()
            .put("maxLength", 512)
            .put("allowCredentials", false);
        urlConfig.putArray("allowedSchemes").add("https");
        JsonNode url = configure(
            owner.token(), spaceId, sourceTypeId, urlField, "wif3-url-" + suffix(),
            configuration(
                0,
                objectMapper.getNodeFactory().textNode("HTTPS://Example.COM:443/a/../b?q=1"),
                urlConfig
            ),
            200
        ).body();
        assertEquals("https://example.com/b?q=1", url.at("/config/defaultValue").asText());
        configure(
            owner.token(), spaceId, sourceTypeId, urlField, "wif3-url-danger-" + suffix(),
            configuration(
                1,
                objectMapper.getNodeFactory().textNode("https://user:secret@example.com/path"),
                urlConfig
            ),
            400
        ).withError("invalid_default_value");

        JsonNode referenceField = createField(
            owner.token(), spaceId, sourceTypeId, "related", "Related", "work_item_reference"
        );
        ObjectNode referenceConfig = objectMapper.createObjectNode()
            .put("maxReferences", 10)
            .put("direction", "outbound")
            .put("relationCapability", "deferred");
        referenceConfig.putArray("targetTypeIds").add(targetTypeId.toString());
        JsonNode reference = configure(
            owner.token(), spaceId, sourceTypeId, referenceField, "wif3-reference-" + suffix(),
            configuration(0, objectMapper.createArrayNode(), referenceConfig), 200
        ).body();
        assertEquals(targetTypeId.toString(), reference.at("/config/typeConfig/targetTypeIds/0").asText());
        assertFalse(openApi().path("paths").has("/api/work-items"));

        referenceConfig.putArray("targetTypeIds").add(UUID.randomUUID().toString());
        configure(
            owner.token(), spaceId, sourceTypeId, referenceField, "wif3-reference-hidden-" + suffix(),
            configuration(1, objectMapper.createArrayNode(), referenceConfig), 400
        ).withError("invalid_complex_field_reference");
    }

    @Test
    void attachmentsUseFileAccessAndConstraintsWithoutCopyingFileMetadata() throws Exception {
        TestUser root = root("wif3-file-root");
        TestUser owner = member(root.token(), "wif3owner");
        TestUser other = member(root.token(), "wif3other");
        UUID spaceId = createSpace(owner.token(), "wif3-file");
        UUID typeId = createType(owner.token(), spaceId, "delivery", "Delivery");
        JsonNode field = createField(owner.token(), spaceId, typeId, "evidence", "Evidence", "attachment");
        FileMetadata allowed = completedFile(owner, "allowed.pdf", "application/pdf", 1024);
        FileMetadata forbidden = completedFile(other, "private.pdf", "application/pdf", 1024);

        ObjectNode typeConfig = objectMapper.createObjectNode()
            .put("maxFiles", 2)
            .put("maxFileSizeBytes", 2048);
        typeConfig.putArray("allowedContentTypes").add("application/pdf");
        JsonNode configured = configure(
            owner.token(), spaceId, typeId, field, "wif3-file-ok-" + suffix(),
            configuration(0, objectMapper.createArrayNode().add(allowed.id().toString()), typeConfig), 200
        ).body();
        assertEquals(allowed.id().toString(), configured.at("/config/defaultValue/0").asText());
        assertFalse(configured.toString().contains(allowed.originalName()));
        assertFalse(configured.toString().contains(allowed.objectKey()));

        configure(
            owner.token(), spaceId, typeId, field, "wif3-file-forbidden-" + suffix(),
            configuration(1, objectMapper.createArrayNode().add(forbidden.id().toString()), typeConfig), 400
        ).withError("invalid_complex_field_reference");
        assertEquals(1L, fieldVersion(field));

        typeConfig.put("maxFileSizeBytes", 512);
        configure(
            owner.token(), spaceId, typeId, field, "wif3-file-size-" + suffix(),
            configuration(1, objectMapper.createArrayNode().add(allowed.id().toString()), typeConfig), 400
        ).withError("invalid_complex_field_reference");
        assertEquals(1L, fieldVersion(field));
    }

    @Test
    void complexConfigurationKeepsRoleBoundariesCatalogAndAuditRedaction() throws Exception {
        TestUser root = root("wif3-rbac-root");
        TestUser owner = member(root.token(), "wif3owner");
        TestUser admin = member(root.token(), "wif3admin");
        TestUser member = member(root.token(), "wif3member");
        TestUser guest = member(root.token(), "wif3guest");
        TestUser outsider = member(root.token(), "wif3outside");
        TestUser governor = admin(root.token(), "wif3governor");
        UUID spaceId = createSpace(owner.token(), "wif3-rbac");
        addSpaceMember(spaceId, admin.id(), "admin", owner.id());
        addSpaceMember(spaceId, member.id(), "member", owner.id());
        addSpaceMember(spaceId, guest.id(), "guest", owner.id());
        UUID typeId = createType(owner.token(), spaceId, "delivery", "Delivery");
        JsonNode field = createField(owner.token(), spaceId, typeId, "source_url", "Source URL", "url");
        ObjectNode typeConfig = objectMapper.createObjectNode()
            .put("maxLength", 512)
            .put("allowCredentials", false);
        typeConfig.putArray("allowedSchemes").add("https");
        ObjectNode first = configuration(
            0, objectMapper.getNodeFactory().textNode("https://example.com/private?q=secret"), typeConfig
        );
        configure(owner.token(), spaceId, typeId, field, "wif3-owner-" + suffix(), first, 200);

        TestUser adminSession = relogin(admin);
        ObjectNode second = configuration(
            1, objectMapper.getNodeFactory().textNode("https://example.org/public"), typeConfig
        );
        configure(adminSession.token(), spaceId, typeId, field, "wif3-admin-" + suffix(), second, 200);
        for (TestUser denied : List.of(relogin(member), relogin(guest))) {
            configure(denied.token(), spaceId, typeId, field, "wif3-denied-" + suffix(), second, 403);
        }
        for (TestUser hidden : List.of(relogin(outsider), relogin(governor))) {
            configure(hidden.token(), spaceId, typeId, field, "wif3-hidden-" + suffix(), second, 404);
        }

        JsonNode catalog = json(mockMvc.perform(get(basePath(spaceId) + "/field-types")
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andReturn());
        JsonNode urlType = findType(catalog, "url");
        assertFalse(urlType.path("sortable").asBoolean());
        assertEquals("not_applicable", urlType.path("referencePolicy").asText());
        JsonNode attachmentType = findType(catalog, "attachment");
        assertEquals("file_module", attachmentType.path("referencePolicy").asText());
        assertEquals("unavailable_without_snapshot", attachmentType.path("invalidReferencePolicy").asText());
        assertTrue(attachmentType.path("typeConfigSchema").path("properties").has("maxFiles"));

        String metadata = jdbcTemplate.queryForObject(
            """
                select metadata::text from audit_logs
                where target_id=? and action='work_item_field.configured'
                order by created_at asc limit 1
                """,
            String.class,
            UUID.fromString(field.path("id").asText())
        );
        assertFalse(metadata.contains("example.com"));
        assertFalse(metadata.contains("private"));
        assertFalse(metadata.contains("secret"));
        assertTrue(metadata.contains("configHash"));
        JsonNode openApi = openApi();
        assertTrue(openApi.path("components").path("schemas").path("ConfigureFieldRequest")
            .path("properties").has("typeConfig"));
    }

    private FileMetadata completedFile(TestUser uploader, String name, String contentType, long size) {
        FileMetadata pending = fileRepository.createPending(
            uploader.workspaceId(), uploader.workspaceId() + "/" + UUID.randomUUID() + "/" + name,
            name, contentType, size, uploader.id()
        );
        return fileRepository.complete(uploader.workspaceId(), pending.id(), uploader.id()).orElseThrow();
    }

    private JsonNode findType(JsonNode catalog, String key) {
        for (JsonNode item : catalog.path("items")) {
            if (key.equals(item.path("key").asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing type " + key);
    }

    private JsonNode openApi() throws Exception {
        return json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
    }

    private ConfigResult configure(
        String token,
        UUID spaceId,
        UUID typeId,
        JsonNode field,
        String requestId,
        ObjectNode body,
        int expectedStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(put(configurationPath(spaceId, typeId, field))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().is(expectedStatus))
            .andReturn();
        JsonNode payload = result.getResponse().getContentAsString().isBlank()
            ? objectMapper.createObjectNode()
            : json(result);
        return new ConfigResult(payload);
    }

    private ObjectNode configuration(long version, JsonNode defaultValue, JsonNode typeConfig) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("schemaVersion", 1);
        body.put("required", false);
        body.set("defaultValue", defaultValue);
        body.putArray("validationRules");
        body.set("typeConfig", typeConfig);
        body.putArray("options");
        body.put("aggregateVersion", version);
        return body;
    }

    private JsonNode createField(
        String token,
        UUID spaceId,
        UUID typeId,
        String key,
        String name,
        String type
    ) throws Exception {
        return json(mockMvc.perform(post(fieldsPath(spaceId, typeId))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wif3-field-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"%s","name":"%s","fieldType":"%s","config":{},"sortOrder":10}
                    """.formatted(key, name, type)))
            .andExpect(status().isOk())
            .andReturn());
    }

    private UUID createType(String token, UUID spaceId, String key, String name) throws Exception {
        JsonNode response = json(mockMvc.perform(post(basePath(spaceId) + "/types")
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wif3-type-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeKey\":\"" + key + "\",\"name\":\"" + name + "\",\"sortOrder\":10}"))
            .andExpect(status().isOk())
            .andReturn());
        return UUID.fromString(response.path("id").asText());
    }

    private UUID createSpace(String token, String prefix) throws Exception {
        String key = prefix + "-" + suffix();
        JsonNode response = json(mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceKey\":\"" + key + "\",\"name\":\"" + prefix + "\",\"visibility\":\"private\"}"))
            .andExpect(status().isOk())
            .andReturn());
        return UUID.fromString(response.path("id").asText());
    }

    private TestUser root(String fingerprint) throws Exception {
        UUID id = jdbcTemplate.queryForObject("select id from users where username='admin'", UUID.class);
        UUID workspaceId = jdbcTemplate.queryForObject("select workspace_id from users where id=?", UUID.class, id);
        return new TestUser(id, workspaceId, "admin", "Administrator",
            login("admin", "admin123456", fingerprint + "-" + suffix()));
    }

    private TestUser member(String rootToken, String prefix) throws Exception {
        return createIdentity(rootToken, prefix, "member");
    }

    private TestUser admin(String rootToken, String prefix) throws Exception {
        return createIdentity(rootToken, prefix, "admin");
    }

    private TestUser createIdentity(String rootToken, String prefix, String role) throws Exception {
        String username = prefix + suffix();
        String displayName = prefix + " user";
        JsonNode response = json(mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(rootToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"member123456","displayName":"%s",
                     "email":"%s@example.com","roleCode":"%s"}
                    """.formatted(username, displayName, username, role)))
            .andExpect(status().isOk())
            .andReturn());
        UUID id = UUID.fromString(response.path("id").asText());
        UUID workspaceId = jdbcTemplate.queryForObject("select workspace_id from users where id=?", UUID.class, id);
        return new TestUser(
            id,
            workspaceId,
            username,
            displayName,
            login(username, "member123456", username + "-" + suffix())
        );
    }

    private TestUser relogin(TestUser user) throws Exception {
        return new TestUser(
            user.id(), user.workspaceId(), user.username(), user.displayName(),
            login(user.username(), "member123456", user.username() + "-" + suffix())
        );
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        return json(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","deviceType":"web","deviceFingerprint":"%s",
                     "deviceName":"MockMvc","appVersion":"test"}
                    """.formatted(username, password, fingerprint)))
            .andExpect(status().isOk())
            .andReturn()).path("accessToken").asText();
    }

    private void addSpaceMember(UUID spaceId, UUID userId, String role, UUID actorId) {
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from project_spaces where id=?", UUID.class, spaceId
        );
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId, workspaceId, spaceId, userId, actorId, actorId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments
                    (id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at)
                values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, memberId, role, actorId
        );
    }

    private long fieldVersion(JsonNode field) {
        return jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_field_definitions where id=?",
            Long.class,
            UUID.fromString(field.path("id").asText())
        );
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String configurationPath(UUID spaceId, UUID typeId, JsonNode field) {
        return fieldsPath(spaceId, typeId) + "/" + field.path("id").asText() + "/configuration";
    }

    private String fieldsPath(UUID spaceId, UUID typeId) {
        return basePath(spaceId) + "/types/" + typeId + "/fields";
    }

    private String basePath(UUID spaceId) {
        return "/api/project-spaces/" + spaceId + "/configuration";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record TestUser(
        UUID id,
        UUID workspaceId,
        String username,
        String displayName,
        String token
    ) {
    }

    private record ConfigResult(JsonNode body) {
        ConfigResult withError(String code) {
            assertEquals(code, body.path("error").path("code").asText());
            return this;
        }
    }
}
