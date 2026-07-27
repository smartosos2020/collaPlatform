package com.colla.platform.modules.project.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stable vocabulary for the versioned S11 permission model.
 *
 * <p>These definitions describe configuration. They do not grant runtime access by themselves.
 * Runtime decisions must resolve the immutable snapshot bound to a work item.</p>
 */
public final class WorkItemPermissionModels {
    public static final int PERMISSION_MODEL_SCHEMA_VERSION = 1;
    public static final int MAX_SPACE_ROLES = 32;
    public static final int MAX_WORK_ITEM_ROLES = 32;
    public static final int MAX_PERMISSION_POLICIES = 256;
    public static final int MAX_SUBJECT_SELECTORS = 32;
    public static final int MAX_POLICY_ACTIONS = 64;
    public static final int MAX_SCOPE_VALUES = 128;
    public static final int MAX_ROLE_INHERITANCE_DEPTH = 8;
    public static final Pattern SEMANTIC_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    public static final Set<String> ACTION_KEYS = Set.of(
        "create", "view", "edit", "archive", "restore", "delete",
        "comment", "attach", "participant_manage",
        "transition", "workflow_manage",
        "relate", "accept_link", "relation_manage",
        "field_read", "field_write",
        "role_assign", "policy_manage", "permission_explain", "permission_request",
        "governance_inspect", "migration_manage"
    );

    public static final Set<String> SOURCE_KINDS = Set.of(
        "explicit", "creator", "participant", "field", "space_role", "group", "migration"
    );

    private WorkItemPermissionModels() {
    }

    public enum PolicyEffect {
        allow,
        deny
    }

    public enum SubjectKind {
        enterprise_role,
        space_role,
        work_item_role,
        participant_role,
        user,
        department,
        user_group,
        everyone
    }

    public enum DataScopeKind {
        all,
        created_by_subject,
        participating,
        work_item_role,
        field_match,
        explicit_set
    }

    public record SubjectSelector(
        SubjectKind kind,
        String key,
        UUID subjectId
    ) {
    }

    public record DataScope(
        DataScopeKind kind,
        String fieldKey,
        String operator,
        List<String> values
    ) {
        public DataScope {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record SpaceRoleDefinition(
        String roleKey,
        String name,
        List<String> inheritedRoleKeys,
        List<String> actionKeys,
        boolean system,
        int sortOrder
    ) {
        public SpaceRoleDefinition {
            inheritedRoleKeys = inheritedRoleKeys == null ? List.of() : List.copyOf(inheritedRoleKeys);
            actionKeys = actionKeys == null ? List.of() : List.copyOf(actionKeys);
        }
    }

    public record WorkItemRoleDefinition(
        String roleKey,
        String name,
        List<String> sourceKinds,
        boolean multiple,
        boolean system,
        int sortOrder
    ) {
        public WorkItemRoleDefinition {
            sourceKinds = sourceKinds == null ? List.of() : List.copyOf(sourceKinds);
        }
    }

    public record PermissionPolicyDefinition(
        String policyKey,
        PolicyEffect effect,
        List<String> actionKeys,
        List<SubjectSelector> subjectSelectors,
        DataScope dataScope,
        List<String> fieldKeys,
        List<String> nodeKeys,
        List<String> relationKeys,
        int priority,
        boolean system,
        int sortOrder
    ) {
        public PermissionPolicyDefinition {
            actionKeys = actionKeys == null ? List.of() : List.copyOf(actionKeys);
            subjectSelectors = subjectSelectors == null ? List.of() : List.copyOf(subjectSelectors);
            fieldKeys = fieldKeys == null ? List.of() : List.copyOf(fieldKeys);
            nodeKeys = nodeKeys == null ? List.of() : List.copyOf(nodeKeys);
            relationKeys = relationKeys == null ? List.of() : List.copyOf(relationKeys);
        }
    }

    public record LegacyPermissionMapping(
        String sourceKind,
        String sourceKey,
        String disposition,
        String targetLayer,
        String targetKey
    ) {
    }
}
