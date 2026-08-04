package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemRuntimeProjectionTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemTypeConfigCanonicalizer canonicalizer =
        new WorkItemTypeConfigCanonicalizer(objectMapper);
    private final WorkItemLayoutConditionDsl conditionDsl =
        new WorkItemLayoutConditionDsl(objectMapper, canonicalizer);
    private final WorkItemFieldAccessPolicyEvaluator evaluator =
        new WorkItemFieldAccessPolicyEvaluator(
            new WorkItemFieldAccessPolicySchema(objectMapper, canonicalizer, conditionDsl),
            conditionDsl,
            objectMapper
        );
    private final WorkItemRuntimeProjection projection =
        new WorkItemRuntimeProjection(evaluator, objectMapper);

    @Test
    void fieldConfigurationRequiredIsProjectedAndNamedWhenMissing() throws Exception {
        var snapshot = objectMapper.readTree("""
            {
              "typeDefinition":{"status":"active"},
              "fields":[{
                "id":"00000000-0000-0000-0000-000000000001",
                "fieldKey":"title",
                "name":"标题",
                "fieldType":"text",
                "status":"active",
                "config":{"schemaVersion":1,"required":true,"defaultValue":null,"validationRules":[],"typeConfig":{}}
              }],
              "layouts":[{
                "layoutKind":"create",
                "nodes":[],
                "policies":[]
              }]
            }
            """);
        var configuration = new RuntimeConfiguration(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            5,
            "a".repeat(64),
            snapshot
        );

        var runtime = projection.runtimePresentation(
            configuration,
            "member",
            "active",
            "create",
            objectMapper.createObjectNode()
        );

        assertThat(runtime.path("accessProjection").path("title").path("required").asBoolean())
            .isTrue();
        assertThatThrownBy(() -> projection.prepareCreate(
            configuration,
            "member",
            "active",
            objectMapper.createObjectNode()
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("标题");
    }
}
