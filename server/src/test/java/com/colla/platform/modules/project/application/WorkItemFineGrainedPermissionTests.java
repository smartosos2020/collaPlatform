package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter.EvaluationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemFineGrainedPermissionTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkItemPermissionRuntimeAdapter adapter = new WorkItemPermissionRuntimeAdapter();
    private final UUID user = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();

    @Test
    void fieldNodeAndRelationQualifiersOnlyNarrowObjectAccess() {
        ObjectNode model = (ObjectNode) new WorkItemPermissionPresetCatalog(mapper)
            .modelFor("task").deepCopy();
        var deny = model.withArray("permissionPolicies").addObject();
        deny.put("policyKey", "hide_salary");
        deny.put("effect", "deny");
        deny.putArray("actionKeys").add("field_read");
        deny.putArray("subjectSelectors").addObject().put("kind", "space_role").put("key", "member");
        deny.putObject("dataScope").put("kind", "all");
        deny.putArray("fieldKeys").add("salary");
        deny.putArray("nodeKeys");
        deny.putArray("relationKeys");
        deny.put("priority", 900);

        assertThat(adapter.evaluate(config(model), subject(), "view").allowed()).isTrue();
        assertThat(adapter.evaluate(
            config(model), subject(), "field_read", context("salary", null, null)
        ).allowed()).isFalse();
        assertThat(adapter.evaluate(
            config(model), subject(), "field_read", context("title", null, null)
        ).allowed()).isTrue();
    }

    @Test
    void declarativeScopesSupportCreatorParticipantFieldAndExplicitSetWithoutExpressions() {
        ObjectNode model = scopedModel("created_by_subject", null, null);
        assertThat(adapter.evaluate(
            config(model), subject(), "view",
            new EvaluationContext(item, user, Set.of(), Set.of(), Map.of(), null, null, null)
        ).allowed()).isTrue();
        assertThat(adapter.evaluate(
            config(model), subject(), "view",
            new EvaluationContext(item, UUID.randomUUID(), Set.of(), Set.of(), Map.of(), null, null, null)
        ).allowed()).isFalse();

        ObjectNode field = scopedModel("field_match", "severity", "equals");
        ((ObjectNode) field.withArray("permissionPolicies").get(0).path("dataScope"))
            .putArray("values").add("high");
        assertThat(adapter.evaluate(
            config(field), subject(), "view",
            new EvaluationContext(item, null, Set.of(), Set.of(), Map.of("severity", "high"), null, null, null)
        ).allowed()).isTrue();
    }

    private ObjectNode scopedModel(String kind, String fieldKey, String operator) {
        ObjectNode model = mapper.createObjectNode();
        model.put("denyOverridesAllow", true);
        model.putArray("spaceRoleDefinitions");
        var policy = model.putArray("permissionPolicies").addObject();
        policy.put("policyKey", "scoped_view");
        policy.put("effect", "allow");
        policy.putArray("actionKeys").add("view");
        policy.putArray("subjectSelectors").addObject().put("kind", "space_role").put("key", "member");
        var scope = policy.putObject("dataScope");
        scope.put("kind", kind);
        if (fieldKey != null) scope.put("fieldKey", fieldKey);
        if (operator != null) scope.put("operator", operator);
        scope.putArray("values");
        policy.putArray("fieldKeys");
        policy.putArray("nodeKeys");
        policy.putArray("relationKeys");
        return model;
    }

    private RuntimeConfiguration config(ObjectNode model) {
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.set("permissionModel", model);
        return new RuntimeConfiguration(UUID.randomUUID(), UUID.randomUUID(), 1, 5, "c".repeat(64), snapshot);
    }

    private SubjectContext subject() {
        return new SubjectContext(UUID.randomUUID(), user, 1, Set.of(), Set.of("member"), Set.of(), Set.of());
    }

    private EvaluationContext context(String field, String node, String relation) {
        return new EvaluationContext(item, user, Set.of(), Set.of(), Map.of(), field, node, relation);
    }
}
