package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkItemFieldComplexConfigCanonicalizerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemFieldConfigCanonicalizer canonicalizer = new WorkItemFieldConfigCanonicalizer(
        new WorkItemFieldTypeRegistry(objectMapper),
        new WorkItemTypeConfigCanonicalizer(objectMapper),
        objectMapper
    );

    @Test
    void normalizesUserScopeAndDefaultsWithoutIdentitySnapshots() throws Exception {
        JsonNode config = canonicalizer.canonicalize("user", json("""
            {
              "schemaVersion":1,"required":false,
              "defaultValue":["00000000-0000-0000-0000-000000000002"],
              "validationRules":[],
              "typeConfig":{
                "allowedSubjectTypes":["user_group","member","department"],
                "selectionScope":[
                  {"subjectType":"user_group","subjectId":"00000000-0000-0000-0000-000000000020"},
                  {"subjectType":"member","subjectId":"00000000-0000-0000-0000-000000000002"}
                ],
                "maxSelections":3
              }
            }
            """)).config();

        assertEquals("department", config.at("/typeConfig/allowedSubjectTypes/0").asText());
        assertEquals("member", config.at("/typeConfig/selectionScope/0/subjectType").asText());
        assertEquals(3, config.at("/typeConfig/maxSelections").asInt());
        assertEquals("00000000-0000-0000-0000-000000000002", config.at("/defaultValue/0").asText());
        assertTrue(config.toString().contains("subjectId"));
        assertTrue(!config.toString().contains("displayName"));
    }

    @Test
    void separatesDateAndDatetimeSemanticsAndWhitelistsRelativeDefaults() throws Exception {
        JsonNode date = canonicalizer.canonicalize("date", json("""
            {
              "schemaVersion":1,"required":false,"defaultValue":"2026-07-23","validationRules":[],
              "typeConfig":{"calendar":"iso8601","precision":"day","defaultStrategy":"none",
                "min":"2026-01-01","max":"2026-12-31"}
            }
            """)).config();
        assertEquals("2026-07-23", date.path("defaultValue").asText());

        JsonNode datetime = canonicalizer.canonicalize("datetime", json("""
            {
              "schemaVersion":1,"required":false,"defaultValue":"2026-07-23T12:34:56.789Z",
              "validationRules":[],
              "typeConfig":{"storageTimezone":"UTC","displayTimezone":"Asia/Shanghai",
                "precision":"minute","defaultStrategy":"none",
                "min":"2026-01-01T00:00:00Z","max":"2027-01-01T00:00:00Z"}
            }
            """)).config();
        assertEquals("2026-07-23T12:34:00Z", datetime.path("defaultValue").asText());
        assertEquals("Asia/Shanghai", datetime.at("/typeConfig/displayTimezone").asText());

        assertCode("INVALID_DEFAULT_VALUE", "date", """
            {
              "schemaVersion":1,"required":false,"defaultValue":"2026-07-23","validationRules":[],
              "typeConfig":{"calendar":"iso8601","precision":"day","defaultStrategy":"today",
                "min":null,"max":null}
            }
            """);
        assertCode("INVALID_COMPLEX_FIELD_CONFIGURATION", "datetime", """
            {
              "schemaVersion":1,"required":false,"defaultValue":null,"validationRules":[],
              "typeConfig":{"storageTimezone":"Asia/Shanghai","displayTimezone":"UTC",
                "precision":"second","defaultStrategy":"none","min":null,"max":null}
            }
            """);
    }

    @Test
    void normalizesSafeUrlsAndRejectsDangerousSchemesCredentialsAndControls() throws Exception {
        JsonNode config = canonicalizer.canonicalize("url", json("""
            {
              "schemaVersion":1,"required":false,
              "defaultValue":"HTTPS://Example.COM:443/a/../b?q=1","validationRules":[],
              "typeConfig":{"allowedSchemes":["https","http"],"maxLength":512,"allowCredentials":false}
            }
            """)).config();
        assertEquals("https://example.com/b?q=1", config.path("defaultValue").asText());
        assertCode("INVALID_DEFAULT_VALUE", "url", urlConfig("javascript:alert(1)"));
        assertCode("INVALID_DEFAULT_VALUE", "url", urlConfig("https://user:secret@example.com/path"));
        assertCode("INVALID_DEFAULT_VALUE", "url", urlConfig("https://example.com/\u0001"));
    }

    @Test
    void canonicalizesAttachmentConstraintsAndMimeWildcards() throws Exception {
        JsonNode config = canonicalizer.canonicalize("attachment", json("""
            {
              "schemaVersion":1,"required":false,
              "defaultValue":["00000000-0000-0000-0000-000000000002"],"validationRules":[],
              "typeConfig":{"maxFiles":2,"allowedContentTypes":["image/*","application/pdf"],
                "maxFileSizeBytes":1048576}
            }
            """)).config();
        assertEquals("application/pdf", config.at("/typeConfig/allowedContentTypes/0").asText());
        assertEquals(1048576, config.at("/typeConfig/maxFileSizeBytes").asLong());
        assertCode("INVALID_COMPLEX_FIELD_CONFIGURATION", "attachment", """
            {
              "schemaVersion":1,"required":false,"defaultValue":[],"validationRules":[],
              "typeConfig":{"maxFiles":0,"allowedContentTypes":[],"maxFileSizeBytes":1}
            }
            """);
    }

    @Test
    void freezesReferenceConfigurationWithoutCreatingInstanceReferences() throws Exception {
        JsonNode config = canonicalizer.canonicalize("work_item_reference", json("""
            {
              "schemaVersion":1,"required":false,"defaultValue":[],"validationRules":[],
              "typeConfig":{
                "targetTypeIds":[
                  "00000000-0000-0000-0000-000000000002",
                  "00000000-0000-0000-0000-000000000001"
                ],
                "maxReferences":5,"direction":"outbound","relationCapability":"deferred"
              }
            }
            """)).config();
        assertEquals("00000000-0000-0000-0000-000000000001",
            config.at("/typeConfig/targetTypeIds/0").asText());
        assertEquals("deferred", config.at("/typeConfig/relationCapability").asText());
        assertCode("INVALID_DEFAULT_VALUE", "work_item_reference", """
            {
              "schemaVersion":1,"required":false,
              "defaultValue":["00000000-0000-0000-0000-000000000001"],"validationRules":[],
              "typeConfig":{"targetTypeIds":[],"maxReferences":5,
                "direction":"outbound","relationCapability":"deferred"}
            }
            """);
    }

    @Test
    void rejectsUnknownComplexPropertiesAndInvalidScopeShapes() throws Exception {
        assertCode("INVALID_COMPLEX_FIELD_CONFIGURATION", "user", """
            {
              "schemaVersion":1,"required":false,"defaultValue":[],"validationRules":[],
              "typeConfig":{"allowedSubjectTypes":["member"],"selectionScope":[],
                "maxSelections":1,"displayName":"leak"}
            }
            """);
        assertCode("INVALID_COMPLEX_FIELD_CONFIGURATION", "url", """
            {
              "schemaVersion":1,"required":false,"defaultValue":null,"validationRules":[],
              "typeConfig":{"allowedSchemes":["file"],"maxLength":100,"allowCredentials":false}
            }
            """);
    }

    private String urlConfig(String value) throws Exception {
        var typeConfig = objectMapper.createObjectNode();
        typeConfig.set("allowedSchemes", objectMapper.createArrayNode().add("https"));
        typeConfig.put("maxLength", 512);
        typeConfig.put("allowCredentials", false);
        var config = objectMapper.createObjectNode();
        config.put("schemaVersion", 1);
        config.put("required", false);
        config.put("defaultValue", value);
        config.set("validationRules", objectMapper.createArrayNode());
        config.set("typeConfig", typeConfig);
        return objectMapper.writeValueAsString(config);
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private void assertCode(String code, String type, String value) throws Exception {
        WorkItemFieldException failure = assertThrows(
            WorkItemFieldException.class,
            () -> canonicalizer.canonicalize(type, json(value))
        );
        assertEquals(code, failure.code());
    }
}
