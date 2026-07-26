package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityImpact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationCompatibilityAnalyzerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemConfigurationCompatibilityAnalyzer analyzer =
        new WorkItemConfigurationCompatibilityAnalyzer();

    @Test
    void classifiesStableFieldOptionLayoutAndAccessPaths() throws Exception {
        var before = snapshot(false, "text", true);
        var after = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{"typeKey":"task"},
              "fields":[
                {
                  "fieldKey":"title",
                  "fieldType":"number",
                  "required":true,
                  "options":[],
                  "config":{"referenceType":""}
                }
              ],
              "layouts":[
                {
                  "layoutKind":"create",
                  "nodes":[],
                  "policies":[
                    {"fieldKey":"title","policyKey":"default","write":false,"visibility":"hidden"}
                  ]
                }
              ]
            }
            """);

        var report = analyzer.analyze("a".repeat(64), before, "b".repeat(64), after);

        assertEquals(CompatibilityImpact.blocked, report.overallImpact());
        assertTrue(report.instanceMigrationRequired());
        assertTrue(report.findings().stream().anyMatch(finding ->
            finding.keyPath().equals("$.fields[title].fieldType")
                && finding.reasonCode().equals("field_type_changed")
        ));
        assertTrue(report.findings().stream().anyMatch(finding ->
            finding.keyPath().equals("$.fields[title].options[open]")
                && finding.reasonCode().equals("option_removed")
        ));
        assertTrue(report.findings().stream().anyMatch(finding ->
            finding.keyPath().equals("$.layouts[create].nodes[title]")
                && finding.reasonCode().equals("layout_entry_removed")
        ));
    }

    @Test
    void additiveFieldIsCompatibleAndDoesNotClaimInstanceMigration() throws Exception {
        var before = snapshot(false, "text", false);
        var after = before.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) after.path("fields")).add(
            objectMapper.readTree("""
                {"fieldKey":"priority","fieldType":"single_select","required":false,"options":[]}
                """)
        );

        var report = analyzer.analyze("a".repeat(64), before, "b".repeat(64), after);

        assertEquals(CompatibilityImpact.compatible, report.overallImpact());
        assertTrue(!report.instanceMigrationRequired());
        assertEquals("additive_change", report.findings().getFirst().reasonCode());
    }

    private com.fasterxml.jackson.databind.JsonNode snapshot(
        boolean required,
        String fieldType,
        boolean withOptionAndNode
    ) throws Exception {
        return objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{"typeKey":"task"},
              "fields":[
                {
                  "fieldKey":"title",
                  "fieldType":"%s",
                  "required":%s,
                  "options":%s,
                  "config":{"referenceType":"work_item"}
                }
              ],
              "layouts":[
                {
                  "layoutKind":"create",
                  "nodes":%s,
                  "policies":[
                    {"fieldKey":"title","policyKey":"default","write":true,"visibility":"visible"}
                  ]
                }
              ]
            }
            """.formatted(
                fieldType,
                required,
                withOptionAndNode ? "[{\"optionKey\":\"open\",\"name\":\"Open\"}]" : "[]",
                withOptionAndNode ? "[{\"nodeKey\":\"title\",\"fieldKey\":\"title\"}]" : "[]"
            ));
    }
}
