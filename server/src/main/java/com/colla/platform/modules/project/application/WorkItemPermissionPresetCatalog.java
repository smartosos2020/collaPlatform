package com.colla.platform.modules.project.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic initial permission definition for every work-item type.
 *
 * <p>The preset preserves the S02 access ceiling when S11 runtime is not active. It deliberately
 * does not map enterprise administrator roles to content owner.</p>
 */
@Component
public final class WorkItemPermissionPresetCatalog {
    private final ObjectMapper objectMapper;

    public WorkItemPermissionPresetCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode modelFor(String typeKey) {
        ObjectNode model = objectMapper.createObjectNode();
        model.put("schemaVersion", 1);
        ArrayNode spaceRoles = model.putArray("spaceRoleDefinitions");
        spaceRole(spaceRoles, "guest", "访客", List.of(), List.of("view", "comment", "permission_request"), 100);
        spaceRole(spaceRoles, "member", "成员", List.of("guest"), List.of(
            "create", "view", "edit", "archive", "restore", "comment", "attach",
            "transition", "relate", "accept_link", "permission_explain", "permission_request"
        ), 200);
        spaceRole(spaceRoles, "admin", "空间管理员", List.of("member"), List.of(
            "participant_manage", "workflow_manage", "relation_manage", "role_assign",
            "policy_manage", "governance_inspect", "migration_manage"
        ), 300);
        spaceRole(spaceRoles, "owner", "空间所有者", List.of("admin"), List.of("delete"), 400);

        ArrayNode workItemRoles = model.putArray("workItemRoleDefinitions");
        workItemRole(workItemRoles, "creator", "创建人", List.of("creator"), false, 100);
        workItemRole(workItemRoles, "assignee", "负责人", List.of("explicit", "field"), true, 200);
        workItemRole(workItemRoles, "collaborator", "协作者", List.of("explicit", "participant", "group"), true, 300);
        workItemRole(workItemRoles, "watcher", "关注者", List.of("explicit", "participant"), true, 400);

        ArrayNode policies = model.putArray("permissionPolicies");
        policy(policies, "space_guest_baseline", "allow",
            List.of("view", "comment", "permission_request", "field_read"), "space_role", "guest", 100, 100);
        policy(policies, "space_member_baseline", "allow",
            List.of("create", "view", "edit", "archive", "restore", "comment", "attach",
                "transition", "relate", "accept_link", "permission_explain", "permission_request",
                "field_read", "field_write"),
            "space_role", "member", 200, 200);
        policy(policies, "space_admin_governance", "allow",
            List.of("participant_manage", "workflow_manage", "relation_manage", "role_assign",
                "policy_manage", "governance_inspect", "migration_manage"),
            "space_role", "admin", 300, 300);
        policy(policies, "space_owner_dangerous_actions", "allow",
            List.of("delete"), "space_role", "owner", 400, 400);
        policy(policies, "creator_collaboration", "allow",
            List.of("view", "edit", "comment", "attach", "transition", "relate"),
            "work_item_role", "creator", 250, 500);

        ArrayNode legacy = model.putArray("legacyMappings");
        legacy(legacy, "space_member_role", "owner", "map", "space_role", "owner");
        legacy(legacy, "space_member_role", "admin", "map", "space_role", "admin");
        legacy(legacy, "space_member_role", "member", "map", "space_role", "member");
        legacy(legacy, "space_member_role", "guest", "map", "space_role", "guest");
        legacy(legacy, "work_item_participant", "owner", "map", "work_item_role", "assignee");
        legacy(legacy, "work_item_participant", "collaborator", "map", "work_item_role", "collaborator");
        legacy(legacy, "field_access_policy", "*", "preserve_in_snapshot", "field_policy", "*");
        legacy(legacy, "resource_acl", "*", "review_required", "none", "");
        model.put("boundTypeKey", typeKey);
        model.put("denyOverridesAllow", true);
        return model;
    }

    private void spaceRole(
        ArrayNode target,
        String key,
        String name,
        List<String> inherits,
        List<String> actions,
        int order
    ) {
        ObjectNode role = target.addObject();
        role.put("roleKey", key);
        role.put("name", name);
        addStrings(role.putArray("inheritedRoleKeys"), inherits);
        addStrings(role.putArray("actionKeys"), actions);
        role.put("system", true);
        role.put("sortOrder", order);
    }

    private void workItemRole(
        ArrayNode target,
        String key,
        String name,
        List<String> sources,
        boolean multiple,
        int order
    ) {
        ObjectNode role = target.addObject();
        role.put("roleKey", key);
        role.put("name", name);
        addStrings(role.putArray("sourceKinds"), sources);
        role.put("multiple", multiple);
        role.put("system", true);
        role.put("sortOrder", order);
    }

    private void policy(
        ArrayNode target,
        String key,
        String effect,
        List<String> actions,
        String subjectKind,
        String subjectKey,
        int priority,
        int order
    ) {
        ObjectNode policy = target.addObject();
        policy.put("policyKey", key);
        policy.put("effect", effect);
        addStrings(policy.putArray("actionKeys"), actions);
        ObjectNode selector = policy.putArray("subjectSelectors").addObject();
        selector.put("kind", subjectKind);
        selector.put("key", subjectKey);
        selector.putNull("subjectId");
        policy.putObject("dataScope").put("kind", "all");
        policy.putArray("fieldKeys");
        policy.putArray("nodeKeys");
        policy.putArray("relationKeys");
        policy.put("priority", priority);
        policy.put("system", true);
        policy.put("sortOrder", order);
    }

    private void legacy(
        ArrayNode target,
        String sourceKind,
        String sourceKey,
        String disposition,
        String targetLayer,
        String targetKey
    ) {
        ObjectNode mapping = target.addObject();
        mapping.put("sourceKind", sourceKind);
        mapping.put("sourceKey", sourceKey);
        mapping.put("disposition", disposition);
        mapping.put("targetLayer", targetLayer);
        mapping.put("targetKey", targetKey);
    }

    private void addStrings(ArrayNode target, List<String> values) {
        values.forEach(target::add);
    }
}
