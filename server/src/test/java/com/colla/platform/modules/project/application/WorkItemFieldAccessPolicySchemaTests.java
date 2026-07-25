package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.WorkItemLayoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemFieldAccessPolicySchemaTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemTypeConfigCanonicalizer canonicalizer =
        new WorkItemTypeConfigCanonicalizer(objectMapper);
    private final WorkItemLayoutConditionDsl conditionDsl =
        new WorkItemLayoutConditionDsl(objectMapper, canonicalizer);
    private final WorkItemFieldAccessPolicySchema schema =
        new WorkItemFieldAccessPolicySchema(objectMapper, canonicalizer, conditionDsl);

    @Test
    void canonicalizesEquivalentPoliciesAndRuleOrderingToStableHash() throws Exception {
        JsonNode first = schema.canonicalize(objectMapper.readTree("""
            {
              "schemaVersion":1,
              "default":{"mode":"write","required":false},
              "rules":[
                {"ruleKey":"guest_read","roles":["guest"],"mode":"read","required":false},
                {"ruleKey":"member_required","roles":["member"],"mode":"write","required":true}
              ]
            }
            """));
        JsonNode second = schema.canonicalize(objectMapper.readTree("""
            {
              "rules":[
                {"required":true,"mode":"write","roles":["member"],"ruleKey":"member_required"},
                {"required":false,"mode":"read","roles":["guest"],"ruleKey":"guest_read"}
              ],
              "default":{"required":false,"mode":"write"},
              "schemaVersion":1
            }
            """));
        assertEquals(first, second);
        assertEquals(canonicalizer.hash(first), canonicalizer.hash(second));
    }

    @Test
    void rejectsUnknownVersionsRolesContextsModesAndEmptyAuthorization() throws Exception {
        assertCode("""
            {"schemaVersion":2,"default":{"mode":"write","required":false},"rules":[]}
            """, "INVALID_FIELD_ACCESS_POLICY");
        assertCode("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},
             "rules":[{"ruleKey":"bad","roles":[],"mode":"read","required":false}]}
            """, "INVALID_FIELD_ACCESS_POLICY");
        assertCode("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},
             "rules":[{"ruleKey":"bad","roles":["workspace_admin"],"mode":"read","required":false}]}
            """, "INVALID_FIELD_ACCESS_POLICY");
        assertCode("""
            {"schemaVersion":1,"default":{"mode":"editable","required":false},"rules":[]}
            """, "INVALID_FIELD_ACCESS_POLICY");
        assertCode("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},
             "rules":[{"ruleKey":"bad","roles":["member"],"mode":"read","required":false,
               "when":{"schemaVersion":1,"expression":{"kind":"predicate","source":"context",
                 "contextKey":"actor_role","operator":"eq","value":"root"}}}]}
            """, "INVALID_FIELD_ACCESS_POLICY");
    }

    @Test
    void rejectsConflictingRulesAndRequiredReadWhileRetainingFieldReferences() throws Exception {
        assertCode("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},
             "rules":[
               {"ruleKey":"one","roles":["member"],"mode":"read","required":false},
               {"ruleKey":"two","roles":["member"],"mode":"hidden","required":false}
             ]}
            """, "CONFLICTING_FIELD_ACCESS_POLICY_RULE");
        assertCode("""
            {"schemaVersion":1,"default":{"mode":"read","required":true},"rules":[]}
            """, "INVALID_FIELD_ACCESS_POLICY");
        UUID fieldId = UUID.randomUUID();
        JsonNode policy = schema.canonicalize(objectMapper.readTree("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},
             "rules":[{"ruleKey":"conditional","roles":["member"],"mode":"read","required":false,
               "when":{"schemaVersion":1,"expression":{"kind":"predicate","source":"field",
                 "fieldId":"%s","fieldKey":"risk","operator":"eq","value":"high"}}}]}
            """.formatted(fieldId)));
        assertEquals(fieldId, schema.fieldReferences(policy).getFirst().fieldId());
        assertNotEquals("", canonicalizer.hash(policy));
    }

    private void assertCode(String json, String code) throws Exception {
        WorkItemLayoutException exception = assertThrows(
            WorkItemLayoutException.class,
            () -> schema.canonicalize(objectMapper.readTree(json))
        );
        assertEquals(code, exception.code());
    }
}
