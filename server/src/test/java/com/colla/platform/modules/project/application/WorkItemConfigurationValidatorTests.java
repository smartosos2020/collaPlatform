package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationValidatorTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemConfigurationValidator validator = new WorkItemConfigurationValidator(
        new WorkItemConfigurationSnapshotCanonicalizer(objectMapper),
        new WorkItemStateFlowValidator(
            new WorkItemStateFlowGuardRegistry(),
            new WorkItemStateFlowSideEffectRegistry()
        ),
        new WorkItemNodeFlowValidator(new WorkItemNodeTypeRegistry())
    );

    @Test
    void acceptsSelfContainedConfigurationGraph() throws Exception {
        var result = validator.validate(objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{
                "typeKey":"task",
                "workspaceId":"00000000-0000-0000-0000-000000000001",
                "spaceId":"00000000-0000-0000-0000-000000000002"
              },
              "fields":[
                {
                  "id":"00000000-0000-0000-0000-000000000003",
                  "fieldKey":"title",
                  "fieldType":"text",
                  "status":"active",
                  "sortOrder":0,
                  "config":{},
                  "options":[]
                }
              ],
              "layouts":[
                {
                  "layoutKind":"create",
                  "nodes":[
                    {
                      "nodeKey":"main",
                      "parentKey":null,
                      "nodeType":"section",
                      "fieldKey":null,
                      "sortOrder":0,
                      "config":{},
                      "visibilityCondition":{"schemaVersion":1}
                    },
                    {
                      "nodeKey":"title_field",
                      "parentKey":"main",
                      "nodeType":"field",
                      "fieldKey":"title",
                      "sortOrder":0,
                      "config":{},
                      "visibilityCondition":{"schemaVersion":1}
                    }
                  ],
                  "policies":[]
                },
                {"layoutKind":"detail","nodes":[],"policies":[]}
              ]
            }
            """));

        assertTrue(result.valid());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void reportsDuplicateKeysCrossScopeAndDanglingLayoutReferences() throws Exception {
        var result = validator.validate(objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{
                "typeKey":"task",
                "workspaceId":"00000000-0000-0000-0000-000000000001",
                "spaceId":"00000000-0000-0000-0000-000000000002"
              },
              "fields":[
                {
                  "fieldKey":"title",
                  "status":"active",
                  "config":{"targetSpaceId":"00000000-0000-0000-0000-000000000999"},
                  "options":[]
                },
                {"fieldKey":"title","status":"disabled","config":{},"options":[]}
              ],
              "layouts":[
                {
                  "layoutKind":"create",
                  "nodes":[
                    {
                      "nodeKey":"unknown",
                      "parentKey":"missing",
                      "fieldKey":"missing_field",
                      "visibilityCondition":{"fieldKey":"other_missing"}
                    }
                  ],
                  "policies":[]
                }
              ]
            }
            """));

        assertFalse(result.valid());
        assertTrue(result.diagnostics().stream().anyMatch(value -> "cross_space_reference".equals(value.code())));
        assertTrue(result.diagnostics().stream().anyMatch(value -> "duplicate_or_missing_field_key".equals(value.code())));
        assertTrue(result.diagnostics().stream().anyMatch(value -> "unknown_layout_field".equals(value.code())));
        assertTrue(result.diagnostics().stream().anyMatch(value -> "missing_layout_parent".equals(value.code())));
    }
}
