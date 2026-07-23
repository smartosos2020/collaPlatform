package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkItemFieldConfigCanonicalizerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemFieldConfigCanonicalizer canonicalizer = new WorkItemFieldConfigCanonicalizer(
        new WorkItemFieldTypeRegistry(objectMapper),
        new WorkItemTypeConfigCanonicalizer(objectMapper),
        objectMapper
    );

    @Test
    void normalizesNullAndEmptyObjectsToOneImmutableCanonicalContract() {
        var fromNull = canonicalizer.canonicalize("text", null);
        var fromEmpty = canonicalizer.canonicalize("text", objectMapper.createObjectNode());

        assertEquals(fromNull.config(), fromEmpty.config());
        assertEquals(fromNull.hash(), fromEmpty.hash());
        assertEquals(64, fromNull.hash().length());
        assertNotSame(fromNull.config(), fromEmpty.config());
        assertEquals(1, fromNull.config().path("schemaVersion").asInt());
        assertEquals(false, fromNull.config().path("required").asBoolean());
    }

    @Test
    void rejectsUnknownPropertiesAndNonObjects() throws Exception {
        WorkItemFieldException nonObject = assertThrows(
            WorkItemFieldException.class,
            () -> canonicalizer.canonicalize("text", objectMapper.readTree("[]"))
        );
        assertEquals("INVALID_FIELD_CONFIGURATION", nonObject.code());

        WorkItemFieldException nonEmpty = assertThrows(
            WorkItemFieldException.class,
            () -> canonicalizer.canonicalize("single_select", objectMapper.readTree("{\"options\":[]}"))
        );
        assertEquals("INVALID_FIELD_CONFIGURATION", nonEmpty.code());
    }

    @Test
    void normalizesSelectDefaultsRulesAndPropertyOrderDeterministically() throws Exception {
        List<ConfigureFieldOption> options = List.of(
            new ConfigureFieldOption("low", "Low", "#22C55E", 20, "active"),
            new ConfigureFieldOption("high", "High", "#EF4444", 10, "active")
        );
        var first = canonicalizer.canonicalize("single_select", objectMapper.readTree("""
            {
              "required":true,
              "schemaVersion":1,
              "validationRules":[{
                "kind":"allowed_values","ruleKey":"allowed","schemaVersion":1,
                "config":{"values":["low","high"]}
              }],
              "defaultValue":"high"
            }
            """), options);
        var second = canonicalizer.canonicalize("single_select", objectMapper.readTree("""
            {
              "defaultValue":"high","schemaVersion":1,"required":true,
              "validationRules":[{
                "schemaVersion":1,"ruleKey":"allowed","config":{"values":["high","low"]},
                "kind":"allowed_values"
              }]
            }
            """), options);

        assertEquals(first.config(), second.config());
        assertEquals(first.hash(), second.hash());
        assertEquals("high", first.config().path("defaultValue").asText());
    }

    @Test
    void validatesTextNumberBooleanAndRuleCombinations() throws Exception {
        var text = canonicalizer.canonicalize("text", objectMapper.readTree("""
            {"schemaVersion":1,"required":true,"defaultValue":"hello","validationRules":[
              {"ruleKey":"length","kind":"length","schemaVersion":1,"config":{"max":20,"min":2}},
              {"ruleKey":"pattern","kind":"regex","schemaVersion":1,"config":{"pattern":"^[a-z]+$"}}
            ]}
            """));
        assertEquals(2, text.config().path("validationRules").size());

        var number = canonicalizer.canonicalize("number", objectMapper.readTree("""
            {"schemaVersion":1,"required":false,"defaultValue":1.00,"validationRules":[
              {"ruleKey":"range","kind":"number_range","schemaVersion":1,"config":{"min":0,"max":10}},
              {"ruleKey":"precision","kind":"number_precision","schemaVersion":1,"config":{"precision":5,"scale":2}}
            ]}
            """));
        assertEquals("1", number.config().path("defaultValue").toString());

        var bool = canonicalizer.canonicalize("boolean", objectMapper.readTree("""
            {"schemaVersion":1,"required":false,"defaultValue":true,"validationRules":[]}
            """));
        assertEquals(true, bool.config().path("defaultValue").asBoolean());

        String firstId = "00000000-0000-0000-0000-000000000002";
        String secondId = "00000000-0000-0000-0000-000000000001";
        var user = canonicalizer.canonicalize("user", objectMapper.readTree("""
            {"schemaVersion":1,"required":false,"defaultValue":["%s","%s"],"validationRules":[]}
            """.formatted(firstId, secondId)));
        assertEquals(secondId, user.config().path("defaultValue").get(0).asText());
        assertEquals("2026-07-22", canonicalizer.canonicalize("date", objectMapper.readTree("""
            {"schemaVersion":1,"required":false,"defaultValue":"2026-07-22","validationRules":[]}
            """)).config().path("defaultValue").asText());
        assertEquals("2026-07-22T12:00:00Z", canonicalizer.canonicalize("datetime", objectMapper.readTree("""
            {"schemaVersion":1,"required":false,"defaultValue":"2026-07-22T12:00:00Z","validationRules":[]}
            """)).config().path("defaultValue").asText());
        assertEquals(1, canonicalizer.canonicalize("attachment", objectMapper.readTree("""
            {"schemaVersion":1,"required":false,"defaultValue":["%s"],"validationRules":[]}
            """.formatted(firstId))).config().path("defaultValue").size());
        assertEquals(0, canonicalizer.canonicalize("work_item_reference", objectMapper.readTree("""
            {"schemaVersion":1,"required":false,"defaultValue":[],"validationRules":[]}
            """)).config().path("defaultValue").size());
    }

    @Test
    void rejectsUnknownVersionsIncompatibleDefaultsAndContradictoryRules() throws Exception {
        assertCode("INVALID_FIELD_CONFIGURATION", "text", """
            {"schemaVersion":2,"required":false,"defaultValue":null,"validationRules":[]}
            """);
        assertCode("INVALID_DEFAULT_VALUE", "number", """
            {"schemaVersion":1,"required":false,"defaultValue":"1","validationRules":[]}
            """);
        assertCode("INVALID_VALIDATION_RULE", "text", """
            {"schemaVersion":1,"required":false,"defaultValue":null,"validationRules":[
              {"ruleKey":"bad","kind":"length","schemaVersion":1,"config":{"min":10,"max":2}}
            ]}
            """);
        assertCode("INVALID_DEFAULT_VALUE", "date", """
            {"schemaVersion":1,"required":false,"defaultValue":"2026-02-30","validationRules":[]}
            """);
        assertCode("INVALID_DEFAULT_VALUE", "user", """
            {"schemaVersion":1,"required":false,"defaultValue":["not-a-uuid"],"validationRules":[]}
            """);
        assertCode("INVALID_VALIDATION_RULE", "boolean", """
            {"schemaVersion":1,"required":false,"defaultValue":null,"validationRules":[
              {"ruleKey":"length","kind":"length","schemaVersion":1,"config":{"min":1,"max":2}}
            ]}
            """);
        assertCode("INVALID_VALIDATION_RULE", "text", """
            {"schemaVersion":1,"required":false,"defaultValue":null,"validationRules":[
              {"ruleKey":"future","kind":"length","schemaVersion":2,"config":{"min":1,"max":2}}
            ]}
            """);
    }

    private void assertCode(String code, String type, String json) throws Exception {
        WorkItemFieldException failure = assertThrows(
            WorkItemFieldException.class,
            () -> canonicalizer.canonicalize(type, objectMapper.readTree(json))
        );
        assertEquals(code, failure.code());
    }
}
