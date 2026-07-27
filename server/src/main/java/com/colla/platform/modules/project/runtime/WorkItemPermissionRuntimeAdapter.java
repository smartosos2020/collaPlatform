package com.colla.platform.modules.project.runtime;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Interprets only the permission model in the immutable snapshot bound to a WorkItem.
 */
@Component
public final class WorkItemPermissionRuntimeAdapter {
    public Evaluation evaluate(
        RuntimeConfiguration configuration,
        SubjectContext subject,
        String action
    ) {
        return evaluate(configuration, subject, action, EvaluationContext.empty());
    }

    public Evaluation evaluate(
        RuntimeConfiguration configuration,
        SubjectContext subject,
        String action,
        EvaluationContext context
    ) {
        JsonNode model = configuration.snapshot().path("permissionModel");
        if (configuration.snapshotSchemaVersion() < 5) {
            return legacyEvaluation(subject, action);
        }
        if (!model.isObject()) {
            throw failure("UNSUPPORTED_PERMISSION_MODEL", "Bound snapshot has no supported permission model");
        }
        Set<String> effectiveSpaceRoles = inheritedSpaceRoles(model, subject.spaceRoleKeys());
        List<PolicyMatch> matches = new ArrayList<>();
        for (JsonNode policy : model.path("permissionPolicies")) {
            if (!contains(policy.path("actionKeys"), action)
                || !matchesAny(policy.path("subjectSelectors"), subject, effectiveSpaceRoles)
                || !qualifierMatches(policy.path("fieldKeys"), context.fieldKey())
                || !qualifierMatches(policy.path("nodeKeys"), context.nodeKey())
                || !qualifierMatches(policy.path("relationKeys"), context.relationKey())
                || !scopeMatches(policy.path("dataScope"), subject, context)) {
                continue;
            }
            matches.add(new PolicyMatch(
                policy.path("policyKey").asText(),
                policy.path("effect").asText(),
                policy.path("priority").asInt()
            ));
        }
        matches.sort((left, right) -> {
            int priority = Integer.compare(right.priority(), left.priority());
            return priority != 0 ? priority : left.policyKey().compareTo(right.policyKey());
        });
        List<String> deny = matches.stream()
            .filter(match -> "deny".equals(match.effect()))
            .map(PolicyMatch::policyKey)
            .toList();
        List<String> allow = matches.stream()
            .filter(match -> "allow".equals(match.effect()))
            .map(PolicyMatch::policyKey)
            .toList();
        if (!deny.isEmpty() && model.path("denyOverridesAllow").asBoolean(true)) {
            return new Evaluation(false, "explicit_deny", deny);
        }
        if (!allow.isEmpty()) {
            return new Evaluation(true, "policy_allowed", allow);
        }
        return new Evaluation(false, "no_matching_policy", List.of());
    }

    private boolean qualifierMatches(JsonNode configured, String actual) {
        return configured.isEmpty() || actual != null && contains(configured, actual);
    }

    private boolean scopeMatches(
        JsonNode scope,
        SubjectContext subject,
        EvaluationContext context
    ) {
        return switch (scope.path("kind").asText("all")) {
            case "all" -> true;
            case "created_by_subject" -> subject.userId().equals(context.createdBy());
            case "participating" -> context.participantIds().contains(subject.userId());
            case "work_item_role" -> context.workItemRoleKeys().stream()
                .anyMatch(role -> contains(scope.path("values"), role));
            case "explicit_set" -> context.workItemId() != null
                && contains(scope.path("values"), context.workItemId().toString());
            case "field_match" -> fieldMatch(scope, context);
            default -> false;
        };
    }

    private boolean fieldMatch(JsonNode scope, EvaluationContext context) {
        String fieldKey = scope.path("fieldKey").asText();
        String actual = context.fieldValues().get(fieldKey);
        if (actual == null) {
            return false;
        }
        List<String> expected = new ArrayList<>();
        scope.path("values").forEach(value -> expected.add(value.asText()));
        return switch (scope.path("operator").asText()) {
            case "equals" -> expected.size() == 1 && actual.equals(expected.getFirst());
            case "not_equals" -> expected.size() == 1 && !actual.equals(expected.getFirst());
            case "in" -> expected.contains(actual);
            case "contains" -> expected.size() == 1 && actual.contains(expected.getFirst());
            default -> false;
        };
    }

    private Evaluation legacyEvaluation(SubjectContext subject, String action) {
        Set<String> roles = subject.spaceRoleKeys();
        boolean owner = roles.contains("owner");
        boolean admin = owner || roles.contains("admin");
        boolean member = admin || roles.contains("member");
        boolean guest = member || roles.contains("guest");
        boolean creator = subject.workItemRoleKeys().contains("creator");
        boolean allowed = switch (action) {
            case "view", "comment", "permission_request", "field_read" -> guest || creator;
            case "create", "edit", "archive", "restore", "attach", "transition",
                "relate", "accept_link", "permission_explain", "field_write" -> member || creator;
            case "participant_manage", "workflow_manage", "relation_manage", "role_assign",
                "policy_manage", "governance_inspect", "migration_manage" -> admin;
            case "delete" -> owner;
            default -> false;
        };
        return new Evaluation(
            allowed,
            allowed ? "legacy_snapshot_ceiling" : "legacy_snapshot_denied",
            allowed ? List.of("legacy_space_role_ceiling") : List.of()
        );
    }

    private Set<String> inheritedSpaceRoles(JsonNode model, Set<String> directRoles) {
        Map<String, Set<String>> inherited = new HashMap<>();
        for (JsonNode role : model.path("spaceRoleDefinitions")) {
            Set<String> parents = new LinkedHashSet<>();
            role.path("inheritedRoleKeys").forEach(value -> parents.add(value.asText()));
            inherited.put(role.path("roleKey").asText(), parents);
        }
        Set<String> result = new LinkedHashSet<>();
        directRoles.forEach(role -> expand(role, inherited, result, new HashSet<>()));
        return Set.copyOf(result);
    }

    private void expand(
        String role,
        Map<String, Set<String>> inherited,
        Set<String> result,
        Set<String> visiting
    ) {
        if (!visiting.add(role)) {
            throw failure("INVALID_PERMISSION_MODEL", "Space role inheritance cycle detected at runtime");
        }
        result.add(role);
        for (String parent : inherited.getOrDefault(role, Set.of())) {
            expand(parent, inherited, result, visiting);
        }
        visiting.remove(role);
    }

    private boolean matchesAny(
        JsonNode selectors,
        SubjectContext subject,
        Set<String> effectiveSpaceRoles
    ) {
        for (JsonNode selector : selectors) {
            String kind = selector.path("kind").asText();
            String key = selector.path("key").asText();
            boolean matches = switch (kind) {
                case "everyone" -> true;
                case "enterprise_role" -> subject.enterpriseRoleKeys().contains(key);
                case "space_role" -> effectiveSpaceRoles.contains(key);
                case "work_item_role" -> subject.workItemRoleKeys().contains(key);
                case "participant_role" -> subject.participantRoleKeys().contains(key);
                case "user" -> subject.userId().toString().equals(selector.path("subjectId").asText());
                default -> false;
            };
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    public record Evaluation(boolean allowed, String reasonCode, List<String> safePolicySources) {
        public Evaluation {
            safePolicySources = List.copyOf(safePolicySources);
        }
    }

    public record EvaluationContext(
        UUID workItemId,
        UUID createdBy,
        Set<UUID> participantIds,
        Set<String> workItemRoleKeys,
        Map<String, String> fieldValues,
        String fieldKey,
        String nodeKey,
        String relationKey
    ) {
        public EvaluationContext {
            participantIds = Set.copyOf(participantIds == null ? Set.of() : participantIds);
            workItemRoleKeys = Set.copyOf(workItemRoleKeys == null ? Set.of() : workItemRoleKeys);
            fieldValues = Map.copyOf(fieldValues == null ? Map.of() : fieldValues);
        }

        public static EvaluationContext empty() {
            return new EvaluationContext(null, null, Set.of(), Set.of(), Map.of(), null, null, null);
        }
    }

    private record PolicyMatch(String policyKey, String effect, int priority) {
    }
}
