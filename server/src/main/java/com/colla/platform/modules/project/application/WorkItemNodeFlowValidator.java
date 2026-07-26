package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.CANDIDATE_ROLES;
import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.MAX_BRANCHES;
import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.MAX_CANDIDATE_ROLES;
import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.MAX_EDGES;
import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.MAX_JOINS;
import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.MAX_NODES;
import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.MAX_STAGES;
import static com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.SEMANTIC_KEY;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiagnosticSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemNodeFlowValidator {
    private static final Set<String> MANUAL_STRATEGIES = Set.of("single", "any", "all", "quorum");
    private static final Set<String> RECOVERY_ROLES = Set.of("owner", "admin");
    private static final Set<String> COMPENSATION_ACTIONS = Set.of(
        "record_audit_marker", "close_open_work"
    );
    private final WorkItemNodeTypeRegistry registry;

    public WorkItemNodeFlowValidator(WorkItemNodeTypeRegistry registry) {
        this.registry = registry;
    }

    public void validate(
        JsonNode nodeFlow,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (nodeFlow == null || nodeFlow.isMissingNode() || nodeFlow.isNull()) {
            return;
        }
        if (!nodeFlow.isObject()) {
            error(diagnostics, "invalid_node_flow", "$.nodeFlow", "Node flow must be an object");
            return;
        }

        JsonNode stages = array(nodeFlow, "stages", diagnostics);
        JsonNode nodes = array(nodeFlow, "nodes", diagnostics);
        JsonNode edges = array(nodeFlow, "edges", diagnostics);
        JsonNode branches = array(nodeFlow, "branches", diagnostics);
        JsonNode joins = array(nodeFlow, "joins", diagnostics);
        if (stages == null || nodes == null || edges == null || branches == null || joins == null) {
            return;
        }
        budget(stages, MAX_STAGES, "$.nodeFlow.stages", "stage", diagnostics);
        budget(nodes, MAX_NODES, "$.nodeFlow.nodes", "node", diagnostics);
        budget(edges, MAX_EDGES, "$.nodeFlow.edges", "edge", diagnostics);
        budget(branches, MAX_BRANCHES, "$.nodeFlow.branches", "branch", diagnostics);
        budget(joins, MAX_JOINS, "$.nodeFlow.joins", "join", diagnostics);

        Set<String> stageKeys = validateStages(stages, diagnostics);
        NodeIndex nodeIndex = validateNodes(
            nodes,
            stageKeys,
            fieldKeys,
            activeFieldKeys,
            hiddenFieldKeys,
            diagnostics
        );
        EdgeIndex edgeIndex = validateEdges(
            edges,
            nodeIndex,
            fieldKeys,
            activeFieldKeys,
            hiddenFieldKeys,
            diagnostics
        );
        validateBranches(branches, nodeIndex, edgeIndex, diagnostics);
        validateJoins(joins, nodeIndex, edgeIndex, diagnostics);
        JsonNode recoveryCommands = optionalArray(nodeFlow, "recoveryCommands", diagnostics);
        JsonNode compensations = optionalArray(nodeFlow, "compensations", diagnostics);
        Set<String> recoveryKeys = validateRecoveryCommands(
            recoveryCommands, nodeIndex, diagnostics
        );
        validateCompensations(compensations, recoveryKeys, diagnostics);
        validateTopology(nodeIndex, edgeIndex, diagnostics);
    }

    private Set<String> validateRecoveryCommands(
        JsonNode values,
        NodeIndex nodes,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (values == null) {
            return Set.of();
        }
        budget(values, 64, "$.nodeFlow.recoveryCommands", "recovery command", diagnostics);
        Set<String> keys = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            JsonNode value = values.get(index);
            String path = "$.nodeFlow.recoveryCommands[" + index + "]";
            semanticKey(
                value.path("commandKey").asText(""),
                path + ".commandKey",
                "recovery_command",
                keys,
                diagnostics
            );
            String kind = value.path("kind").asText("");
            if (!Set.of("return_to", "jump", "terminate", "correct").contains(kind)) {
                error(diagnostics, "invalid_recovery_command_kind", path + ".kind", "Recovery kind is not registered");
            }
            JsonNode fromNodeKeys = value.path("fromNodeKeys");
            if (!fromNodeKeys.isArray() || fromNodeKeys.isEmpty() || fromNodeKeys.size() > MAX_NODES) {
                error(diagnostics, "invalid_recovery_sources", path + ".fromNodeKeys", "Recovery requires bounded explicit source nodes");
            } else {
                Set<String> sources = new HashSet<>();
                for (int sourceIndex = 0; sourceIndex < fromNodeKeys.size(); sourceIndex++) {
                    String source = fromNodeKeys.get(sourceIndex).asText("");
                    if (!nodes.byKey().containsKey(source) || !sources.add(source)) {
                        error(
                            diagnostics,
                            "invalid_recovery_source",
                            path + ".fromNodeKeys[" + sourceIndex + "]",
                            "Recovery source must be a unique node in the same snapshot"
                        );
                    }
                }
            }
            String target = value.path("targetNodeKey").asText("");
            if ("terminate".equals(kind)) {
                if (!target.isBlank()) {
                    error(diagnostics, "unexpected_recovery_target", path + ".targetNodeKey", "Terminate cannot declare a target node");
                }
            } else if (!nodes.byKey().containsKey(target)
                || Set.of("start", "branch", "join", "end").contains(nodes.kinds().get(target))) {
                error(diagnostics, "invalid_recovery_target", path + ".targetNodeKey", "Recovery target must be a manual or automatic node");
            }
            JsonNode roles = value.path("authorizedRoles");
            if (!roles.isArray() || roles.isEmpty() || roles.size() > RECOVERY_ROLES.size()) {
                error(diagnostics, "invalid_recovery_roles", path + ".authorizedRoles", "Recovery requires owner/admin roles");
            } else {
                Set<String> unique = new HashSet<>();
                for (int roleIndex = 0; roleIndex < roles.size(); roleIndex++) {
                    String role = roles.get(roleIndex).asText("");
                    if (!RECOVERY_ROLES.contains(role) || !unique.add(role)) {
                        error(diagnostics, "invalid_recovery_role", path + ".authorizedRoles[" + roleIndex + "]", "Recovery roles are owner/admin only");
                    }
                }
            }
            if (!"cancel_open".equals(value.path("closeMode").asText())) {
                error(diagnostics, "invalid_recovery_close_mode", path + ".closeMode", "Recovery must deterministically cancel open tasks, tokens, and joins");
            }
            String expectedConfirmation = switch (kind) {
                case "return_to" -> "RETURN_NODE_WORKFLOW";
                case "jump" -> "JUMP_NODE_WORKFLOW";
                case "terminate" -> "TERMINATE_NODE_WORKFLOW";
                case "correct" -> "CORRECT_NODE_WORKFLOW";
                default -> "";
            };
            if (!expectedConfirmation.equals(value.path("confirmation").asText())) {
                error(diagnostics, "invalid_recovery_confirmation", path + ".confirmation", "Recovery requires its exact frozen confirmation");
            }
        }
        return Set.copyOf(keys);
    }

    private void validateCompensations(
        JsonNode values,
        Set<String> recoveryKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (values == null) {
            return;
        }
        budget(values, 64, "$.nodeFlow.compensations", "compensation", diagnostics);
        Set<String> keys = new HashSet<>();
        Set<String> orders = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            JsonNode value = values.get(index);
            String path = "$.nodeFlow.compensations[" + index + "]";
            semanticKey(
                value.path("compensationKey").asText(""),
                path + ".compensationKey",
                "compensation",
                keys,
                diagnostics
            );
            String commandKey = value.path("commandKey").asText("");
            if (!recoveryKeys.contains(commandKey)) {
                error(diagnostics, "unknown_compensation_command", path + ".commandKey", "Compensation must reference a recovery command");
            }
            if (!COMPENSATION_ACTIONS.contains(value.path("actionKey").asText(""))) {
                error(diagnostics, "unknown_compensation_action", path + ".actionKey", "Compensation action is not registered");
            }
            int sortOrder = value.path("sortOrder").asInt(-1);
            if (!value.path("sortOrder").canConvertToInt() || sortOrder < 0
                || !orders.add(commandKey + ":" + sortOrder)) {
                error(diagnostics, "duplicate_or_invalid_compensation_order", path + ".sortOrder", "Compensation order must be unique per command");
            }
        }
    }

    private Set<String> validateStages(JsonNode stages, List<ConfigurationDiagnostic> diagnostics) {
        Set<String> keys = new LinkedHashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (int index = 0; index < stages.size(); index++) {
            JsonNode stage = stages.get(index);
            String path = "$.nodeFlow.stages[" + index + "]";
            String key = stage.path("stageKey").asText("");
            semanticKey(key, path + ".stageKey", "stage", keys, diagnostics);
            if (stage.path("label").asText("").isBlank()) {
                error(diagnostics, "missing_stage_label", path + ".label", "Stage label is required");
            }
            if (!stage.path("sortOrder").canConvertToInt() || !sortOrders.add(stage.path("sortOrder").asInt())) {
                error(diagnostics, "duplicate_or_invalid_stage_order", path + ".sortOrder", "Stage order must be unique");
            }
        }
        if (keys.isEmpty()) {
            error(diagnostics, "missing_stage", "$.nodeFlow.stages", "At least one stage is required");
        }
        return keys;
    }

    private NodeIndex validateNodes(
        JsonNode nodes,
        Set<String> stageKeys,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        Map<String, String> kinds = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        int starts = 0;
        int ends = 0;
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode node = nodes.get(index);
            String path = "$.nodeFlow.nodes[" + index + "]";
            String key = node.path("nodeKey").asText("");
            if (semanticKey(key, path + ".nodeKey", "node", keys, diagnostics)) {
                byKey.put(key, node);
            }
            String stageKey = node.path("stageKey").asText("");
            if (!stageKeys.contains(stageKey)) {
                error(diagnostics, "unknown_node_stage", path + ".stageKey", "Node references an unknown stage");
            }
            String kind = node.path("kind").asText("").trim().toLowerCase();
            if (!registry.supportsNodeKind(kind)) {
                error(diagnostics, "unknown_node_kind", path + ".kind", "Node kind is not registered");
            } else {
                kinds.put(key, kind);
                starts += "start".equals(kind) ? 1 : 0;
                ends += "end".equals(kind) ? 1 : 0;
            }
            String strategy = node.path("processingStrategy").asText("").trim().toLowerCase();
            if (!registry.supportsProcessingStrategy(strategy)) {
                error(
                    diagnostics,
                    "unknown_processing_strategy",
                    path + ".processingStrategy",
                    "Processing strategy is not registered"
                );
            } else if ("manual".equals(kind) && !MANUAL_STRATEGIES.contains(strategy)) {
                error(
                    diagnostics,
                    "invalid_manual_processing_strategy",
                    path + ".processingStrategy",
                    "Manual nodes require single, any, all, or quorum processing"
                );
            } else if (!"manual".equals(kind) && !"automatic".equals(strategy)) {
                error(
                    diagnostics,
                    "invalid_automatic_processing_strategy",
                    path + ".processingStrategy",
                    "Start, automatic, branch, join, and end nodes require automatic processing"
                );
            }
            validateCandidateRoles(node, kind, strategy, path, diagnostics);
            if (!node.path("sortOrder").canConvertToInt() || !sortOrders.add(node.path("sortOrder").asInt())) {
                error(diagnostics, "duplicate_or_invalid_node_order", path + ".sortOrder", "Node order must be unique");
            }
            if (!node.path("configuration").isObject()) {
                error(diagnostics, "invalid_node_configuration", path + ".configuration", "Node configuration must be an object");
            } else {
                validateCollaborationConfiguration(
                    node,
                    kind,
                    path + ".configuration",
                    fieldKeys,
                    activeFieldKeys,
                    hiddenFieldKeys,
                    diagnostics
                );
            }
        }
        if (starts != 1) {
            error(diagnostics, "invalid_start_node_count", "$.nodeFlow.nodes", "Node flow requires exactly one start node");
        }
        if (ends < 1) {
            error(diagnostics, "missing_end_node", "$.nodeFlow.nodes", "Node flow requires at least one end node");
        }
        return new NodeIndex(Map.copyOf(byKey), Map.copyOf(kinds));
    }

    private void validateCandidateRoles(
        JsonNode node,
        String kind,
        String strategy,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        JsonNode roles = node.path("candidateRoles");
        if (!roles.isArray()) {
            error(diagnostics, "invalid_candidate_roles", path + ".candidateRoles", "Candidate roles must be an array");
            return;
        }
        if (roles.size() > MAX_CANDIDATE_ROLES) {
            error(diagnostics, "candidate_role_budget_exceeded", path + ".candidateRoles", "Candidate role budget exceeded");
        }
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < roles.size(); index++) {
            String role = roles.get(index).asText("");
            if (!CANDIDATE_ROLES.contains(role) || !unique.add(role)) {
                error(
                    diagnostics,
                    "duplicate_or_unknown_candidate_role",
                    path + ".candidateRoles[" + index + "]",
                    "Candidate roles must be unique registered roles"
                );
            }
        }
        JsonNode assignment = node.path("configuration").path("assignment");
        boolean hasAlternativeCandidateSource = assignment.path("explicitUserIds").size() > 0
            || assignment.path("participantRoles").size() > 0
            || assignment.path("spaceRoles").size() > 0
            || assignment.path("fieldParticipantKeys").size() > 0;
        if ("manual".equals(kind) && roles.isEmpty() && !hasAlternativeCandidateSource) {
            error(
                diagnostics,
                "missing_candidate_source",
                path + ".candidateRoles",
                "Manual nodes require at least one controlled candidate source"
            );
        }
        JsonNode quorum = node.get("quorumCount");
        if ("quorum".equals(strategy)) {
            if (quorum == null || !quorum.canConvertToInt() || quorum.asInt() < 1) {
                error(diagnostics, "invalid_node_quorum", path + ".quorumCount", "Quorum nodes require a positive quorum");
            }
        } else if (quorum != null && !quorum.isNull()) {
            error(diagnostics, "unexpected_node_quorum", path + ".quorumCount", "Quorum is only valid for quorum processing");
        }
    }

    private void validateCollaborationConfiguration(
        JsonNode node,
        String kind,
        String path,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        JsonNode configuration = node.path("configuration");
        if (!"manual".equals(kind)) {
            if (configuration.size() > 0) {
                error(
                    diagnostics,
                    "unexpected_node_collaboration_configuration",
                    path,
                    "Only manual nodes may declare collaboration configuration"
                );
            }
            return;
        }
        JsonNode form = configuration.path("form");
        if (!form.isMissingNode()) {
            if (!form.isObject() || !form.path("fields").isArray()) {
                error(diagnostics, "invalid_node_form", path + ".form", "Node form must contain a fields array");
            } else {
                validateForm(form.path("fields"), path + ".form.fields", fieldKeys, activeFieldKeys, hiddenFieldKeys, diagnostics);
            }
        }
        JsonNode assignment = configuration.path("assignment");
        if (!assignment.isMissingNode()) {
            if (!assignment.isObject()) {
                error(diagnostics, "invalid_node_assignment", path + ".assignment", "Node assignment must be an object");
            } else {
                validateAssignment(
                    assignment,
                    path + ".assignment",
                    fieldKeys,
                    activeFieldKeys,
                    hiddenFieldKeys,
                    diagnostics
                );
            }
        }
        JsonNode artifacts = configuration.path("artifacts");
        if (!artifacts.isMissingNode()) {
            validateArtifacts(artifacts, path + ".artifacts", diagnostics);
        }
        JsonNode schedule = configuration.path("schedule");
        if (!schedule.isMissingNode()) {
            validateSchedule(schedule, path + ".schedule", diagnostics);
        }
    }

    private void validateForm(
        JsonNode fields,
        String path,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        budget(fields, 64, path, "node form field", diagnostics);
        Set<String> unique = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (int index = 0; index < fields.size(); index++) {
            JsonNode field = fields.get(index);
            String fieldPath = path + "[" + index + "]";
            String key = field.path("fieldKey").asText("");
            String mode = field.path("mode").asText("");
            if (!fieldKeys.contains(key) || !activeFieldKeys.contains(key) || !unique.add(key)) {
                error(diagnostics, "invalid_node_form_field", fieldPath + ".fieldKey", "Node form field must be unique and active");
            }
            if (!Set.of("hidden", "read", "write").contains(mode)) {
                error(diagnostics, "invalid_node_form_mode", fieldPath + ".mode", "Node form mode must be hidden, read, or write");
            }
            if (hiddenFieldKeys.contains(key) && !"hidden".equals(mode)) {
                error(diagnostics, "hidden_node_form_field", fieldPath + ".mode", "Globally hidden fields must remain hidden");
            }
            if (field.path("required").asBoolean(false) && !"write".equals(mode)) {
                error(diagnostics, "invalid_required_node_form_field", fieldPath + ".required", "Only writable fields may be required");
            }
            if (!field.path("sortOrder").canConvertToInt() || !orders.add(field.path("sortOrder").asInt())) {
                error(diagnostics, "duplicate_or_invalid_node_form_order", fieldPath + ".sortOrder", "Node form order must be unique");
            }
        }
    }

    private void validateAssignment(
        JsonNode assignment,
        String path,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        validateUuidArray(assignment.path("explicitUserIds"), path + ".explicitUserIds", 128, diagnostics);
        validateRoleArray(assignment.path("participantRoles"), path + ".participantRoles", diagnostics);
        validateRoleArray(assignment.path("spaceRoles"), path + ".spaceRoles", diagnostics);
        JsonNode participantFields = assignment.path("fieldParticipantKeys");
        if (!participantFields.isMissingNode()) {
            if (!participantFields.isArray() || participantFields.size() > 32) {
                error(diagnostics, "invalid_field_participant_keys", path + ".fieldParticipantKeys", "Field participant keys must be a bounded array");
            } else {
                Set<String> unique = new HashSet<>();
                for (int index = 0; index < participantFields.size(); index++) {
                    String key = participantFields.get(index).asText("");
                    if (!fieldKeys.contains(key) || !activeFieldKeys.contains(key)
                        || hiddenFieldKeys.contains(key) || !unique.add(key)) {
                        error(
                            diagnostics,
                            "invalid_field_participant_key",
                            path + ".fieldParticipantKeys[" + index + "]",
                            "Field participant source must be a unique visible active field"
                        );
                    }
                }
            }
        }
        assignment.fieldNames().forEachRemaining(name -> {
            if (!Set.of("explicitUserIds", "participantRoles", "spaceRoles", "fieldParticipantKeys").contains(name)) {
                error(diagnostics, "unsupported_assignment_rule", path + "." + name, "Dynamic assignment rule is not registered");
            }
        });
    }

    private void validateUuidArray(
        JsonNode values,
        String path,
        int maximum,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (values.isMissingNode()) {
            return;
        }
        if (!values.isArray() || values.size() > maximum) {
            error(diagnostics, "invalid_explicit_user_ids", path, "Explicit users must be a bounded array");
            return;
        }
        Set<UUID> unique = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            try {
                if (!unique.add(UUID.fromString(values.get(index).asText()))) {
                    throw new IllegalArgumentException("duplicate");
                }
            } catch (IllegalArgumentException exception) {
                error(diagnostics, "invalid_explicit_user_id", path + "[" + index + "]", "Explicit users must be unique UUIDs");
            }
        }
    }

    private void validateRoleArray(
        JsonNode values,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (values.isMissingNode()) {
            return;
        }
        if (!values.isArray() || values.size() > MAX_CANDIDATE_ROLES) {
            error(diagnostics, "invalid_assignment_roles", path, "Assignment roles must be a bounded array");
            return;
        }
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String role = values.get(index).asText("");
            if (!CANDIDATE_ROLES.contains(role) || !unique.add(role)) {
                error(diagnostics, "invalid_assignment_role", path + "[" + index + "]", "Assignment roles must be unique registered roles");
            }
        }
    }

    private void validateArtifacts(JsonNode artifacts, String path, List<ConfigurationDiagnostic> diagnostics) {
        if (!artifacts.isArray()) {
            error(diagnostics, "invalid_node_artifacts", path, "Node artifacts must be an array");
            return;
        }
        budget(artifacts, 16, path, "node artifact", diagnostics);
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (int index = 0; index < artifacts.size(); index++) {
            JsonNode artifact = artifacts.get(index);
            String itemPath = path + "[" + index + "]";
            semanticKey(artifact.path("artifactKey").asText(""), itemPath + ".artifactKey", "artifact", keys, diagnostics);
            String artifactKind = artifact.path("kind").asText("");
            if (!Set.of("file", "object").contains(artifactKind)) {
                error(diagnostics, "invalid_node_artifact_kind", itemPath + ".kind", "Artifact kind must be file or object");
            }
            int maximum = artifact.path("maxCount").asInt(0);
            if (!artifact.path("maxCount").canConvertToInt() || maximum < 1 || maximum > 32) {
                error(diagnostics, "invalid_node_artifact_count", itemPath + ".maxCount", "Artifact maxCount must be between 1 and 32");
            }
            JsonNode objectTypes = artifact.path("objectTypes");
            if ("object".equals(artifactKind) && (!objectTypes.isArray() || objectTypes.isEmpty())) {
                error(diagnostics, "missing_node_artifact_object_type", itemPath + ".objectTypes", "Object artifacts require object types");
            }
            if ("file".equals(artifactKind) && objectTypes.isArray() && !objectTypes.isEmpty()) {
                error(diagnostics, "unexpected_node_artifact_object_type", itemPath + ".objectTypes", "File artifacts cannot declare object types");
            }
            if (!artifact.path("sortOrder").canConvertToInt() || !orders.add(artifact.path("sortOrder").asInt())) {
                error(diagnostics, "duplicate_or_invalid_node_artifact_order", itemPath + ".sortOrder", "Artifact order must be unique");
            }
        }
    }

    private void validateSchedule(JsonNode schedule, String path, List<ConfigurationDiagnostic> diagnostics) {
        if (!schedule.isObject()) {
            error(diagnostics, "invalid_node_schedule", path, "Node schedule must be an object");
            return;
        }
        for (String name : List.of("plannedDelayMinutes", "dueAfterMinutes", "escalationAfterMinutes")) {
            JsonNode value = schedule.get(name);
            if (value != null && (!value.canConvertToInt() || value.asInt() < 0 || value.asInt() > 525_600)) {
                error(diagnostics, "invalid_node_schedule_duration", path + "." + name, "Schedule duration must fit one year");
            }
        }
        if (schedule.has("timeZone") && !"UTC".equals(schedule.path("timeZone").asText())) {
            error(diagnostics, "unsupported_node_schedule_timezone", path + ".timeZone", "M3 schedules use UTC instants");
        }
        if (schedule.has("calendar") && !"elapsed".equals(schedule.path("calendar").asText())) {
            error(diagnostics, "unsupported_node_schedule_calendar", path + ".calendar", "M3 schedules use elapsed time");
        }
        if (schedule.has("pausePolicy") && !"not_supported".equals(schedule.path("pausePolicy").asText())) {
            error(
                diagnostics,
                "unsupported_node_schedule_pause_policy",
                path + ".pausePolicy",
                "Pause and resume are explicitly unavailable in the M3 schedule contract"
            );
        }
    }

    private EdgeIndex validateEdges(
        JsonNode edges,
        NodeIndex nodes,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        Map<String, Integer> priorityBySource = new HashMap<>();
        for (int index = 0; index < edges.size(); index++) {
            JsonNode edge = edges.get(index);
            String path = "$.nodeFlow.edges[" + index + "]";
            String key = edge.path("edgeKey").asText("");
            if (semanticKey(key, path + ".edgeKey", "edge", keys, diagnostics)) {
                byKey.put(key, edge);
            }
            String from = edge.path("fromNodeKey").asText("");
            String to = edge.path("toNodeKey").asText("");
            if (!nodes.byKey().containsKey(from)) {
                error(diagnostics, "unknown_edge_source", path + ".fromNodeKey", "Edge source node is unknown");
            }
            if (!nodes.byKey().containsKey(to)) {
                error(diagnostics, "unknown_edge_target", path + ".toNodeKey", "Edge target node is unknown");
            }
            if (!from.isBlank() && from.equals(to)) {
                error(diagnostics, "self_referencing_edge", path, "Self-referencing edges are not allowed");
            }
            int priority = edge.path("priority").asInt(Integer.MIN_VALUE);
            String priorityKey = from + ":" + priority;
            if (!edge.path("priority").canConvertToInt() || priority < 0
                || priorityBySource.putIfAbsent(priorityKey, index) != null) {
                error(diagnostics, "duplicate_or_invalid_edge_priority", path + ".priority", "Edge priority must be unique per source");
            }
            outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
            incoming.computeIfAbsent(to, ignored -> new ArrayList<>()).add(from);
            validateCondition(
                edge.get("condition"),
                path + ".condition",
                fieldKeys,
                activeFieldKeys,
                hiddenFieldKeys,
                diagnostics
            );
        }
        return new EdgeIndex(Map.copyOf(byKey), immutable(outgoing), immutable(incoming));
    }

    private void validateBranches(
        JsonNode branches,
        NodeIndex nodes,
        EdgeIndex edges,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> keys = new HashSet<>();
        Set<String> nodeKeys = new HashSet<>();
        for (int index = 0; index < branches.size(); index++) {
            JsonNode branch = branches.get(index);
            String path = "$.nodeFlow.branches[" + index + "]";
            semanticKey(branch.path("branchKey").asText(""), path + ".branchKey", "branch", keys, diagnostics);
            String nodeKey = branch.path("nodeKey").asText("");
            if (!"branch".equals(nodes.kinds().get(nodeKey)) || !nodeKeys.add(nodeKey)) {
                error(diagnostics, "invalid_branch_node", path + ".nodeKey", "Branch must reference one unique branch node");
            }
            String mode = branch.path("mode").asText("");
            if (!Set.of("exclusive", "parallel").contains(mode)) {
                error(diagnostics, "invalid_branch_mode", path + ".mode", "Branch mode must be exclusive or parallel");
            }
            JsonNode edgeKeys = branch.path("edgeKeys");
            if (!edgeKeys.isArray() || edgeKeys.size() < 2) {
                error(diagnostics, "invalid_branch_edges", path + ".edgeKeys", "Branch requires at least two outgoing edges");
                continue;
            }
            Set<String> uniqueEdges = new HashSet<>();
            for (int edgeIndex = 0; edgeIndex < edgeKeys.size(); edgeIndex++) {
                String edgeKey = edgeKeys.get(edgeIndex).asText("");
                JsonNode edge = edges.byKey().get(edgeKey);
                if (edge == null || !nodeKey.equals(edge.path("fromNodeKey").asText()) || !uniqueEdges.add(edgeKey)) {
                    error(
                        diagnostics,
                        "invalid_branch_edge_reference",
                        path + ".edgeKeys[" + edgeIndex + "]",
                        "Branch edge must be a unique outgoing edge"
                    );
                }
            }
        }
        for (Map.Entry<String, String> node : nodes.kinds().entrySet()) {
            if ("branch".equals(node.getValue()) && !nodeKeys.contains(node.getKey())) {
                error(diagnostics, "missing_branch_definition", "$.nodeFlow.branches", "Every branch node requires a definition");
            }
        }
    }

    private void validateJoins(
        JsonNode joins,
        NodeIndex nodes,
        EdgeIndex edges,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> keys = new HashSet<>();
        Set<String> nodeKeys = new HashSet<>();
        for (int index = 0; index < joins.size(); index++) {
            JsonNode join = joins.get(index);
            String path = "$.nodeFlow.joins[" + index + "]";
            semanticKey(join.path("joinKey").asText(""), path + ".joinKey", "join", keys, diagnostics);
            String nodeKey = join.path("nodeKey").asText("");
            if (!"join".equals(nodes.kinds().get(nodeKey)) || !nodeKeys.add(nodeKey)) {
                error(diagnostics, "invalid_join_node", path + ".nodeKey", "Join must reference one unique join node");
            }
            String policy = join.path("policy").asText("");
            if (!Set.of("all", "any", "quorum").contains(policy)) {
                error(diagnostics, "invalid_join_policy", path + ".policy", "Join policy must be all, any, or quorum");
            }
            JsonNode inbound = join.path("inboundEdgeKeys");
            if (!inbound.isArray() || inbound.size() < 2) {
                error(diagnostics, "invalid_join_edges", path + ".inboundEdgeKeys", "Join requires at least two inbound edges");
                continue;
            }
            Set<String> uniqueEdges = new HashSet<>();
            for (int edgeIndex = 0; edgeIndex < inbound.size(); edgeIndex++) {
                String edgeKey = inbound.get(edgeIndex).asText("");
                JsonNode edge = edges.byKey().get(edgeKey);
                if (edge == null || !nodeKey.equals(edge.path("toNodeKey").asText()) || !uniqueEdges.add(edgeKey)) {
                    error(
                        diagnostics,
                        "invalid_join_edge_reference",
                        path + ".inboundEdgeKeys[" + edgeIndex + "]",
                        "Join edge must be a unique inbound edge"
                    );
                }
            }
            JsonNode quorum = join.get("quorumCount");
            if ("quorum".equals(policy)) {
                if (quorum == null || !quorum.canConvertToInt()
                    || quorum.asInt() < 1 || quorum.asInt() > inbound.size()) {
                    error(diagnostics, "invalid_join_quorum", path + ".quorumCount", "Join quorum must fit inbound edges");
                }
            } else if (quorum != null && !quorum.isNull()) {
                error(diagnostics, "unexpected_join_quorum", path + ".quorumCount", "Quorum is only valid for quorum joins");
            }
        }
        for (Map.Entry<String, String> node : nodes.kinds().entrySet()) {
            if ("join".equals(node.getValue()) && !nodeKeys.contains(node.getKey())) {
                error(diagnostics, "missing_join_definition", "$.nodeFlow.joins", "Every join node requires a definition");
            }
        }
    }

    private void validateTopology(
        NodeIndex nodes,
        EdgeIndex edges,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        String start = nodes.kinds().entrySet().stream()
            .filter(entry -> "start".equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        for (Map.Entry<String, String> entry : nodes.kinds().entrySet()) {
            String key = entry.getKey();
            String kind = entry.getValue();
            int incoming = edges.incoming().getOrDefault(key, List.of()).size();
            int outgoing = edges.outgoing().getOrDefault(key, List.of()).size();
            if ("start".equals(kind) && incoming != 0) {
                error(diagnostics, "start_has_incoming_edge", "$.nodeFlow.nodes[" + key + "]", "Start node cannot have inbound edges");
            }
            if ("end".equals(kind) && outgoing != 0) {
                error(diagnostics, "end_has_outgoing_edge", "$.nodeFlow.nodes[" + key + "]", "End node cannot have outbound edges");
            }
            if (!"start".equals(kind) && incoming == 0) {
                error(diagnostics, "node_without_incoming_edge", "$.nodeFlow.nodes[" + key + "]", "Non-start node requires an inbound edge");
            }
            if (!"end".equals(kind) && outgoing == 0) {
                error(diagnostics, "node_without_outgoing_edge", "$.nodeFlow.nodes[" + key + "]", "Non-end node requires an outbound edge");
            }
        }
        if (start == null) {
            return;
        }
        Set<String> reachable = traverse(start, edges.outgoing());
        for (String key : nodes.byKey().keySet()) {
            if (!reachable.contains(key)) {
                error(diagnostics, "unreachable_node", "$.nodeFlow.nodes[" + key + "]", "Node is unreachable from start");
            }
        }
        if (cycle(start, edges.outgoing(), new HashSet<>(), new HashSet<>())) {
            error(diagnostics, "node_flow_cycle", "$.nodeFlow.edges", "Node flow contains an unsupported cycle");
        }
        Set<String> canReachEnd = new HashSet<>();
        for (Map.Entry<String, String> node : nodes.kinds().entrySet()) {
            if ("end".equals(node.getValue())) {
                canReachEnd.addAll(traverse(node.getKey(), edges.incoming()));
            }
        }
        for (String key : nodes.byKey().keySet()) {
            if (!canReachEnd.contains(key)) {
                error(diagnostics, "node_without_terminal_path", "$.nodeFlow.nodes[" + key + "]", "Node cannot reach an end node");
            }
        }
    }

    private void validateCondition(
        JsonNode condition,
        String path,
        Set<String> fieldKeys,
        Set<String> activeFieldKeys,
        Set<String> hiddenFieldKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (condition == null || condition.isNull()) {
            return;
        }
        if (!registry.conditionShapeSupported(condition)) {
            error(diagnostics, "invalid_branch_condition", path, "Branch condition uses an unsupported declarative shape");
            return;
        }
        String fieldKey = condition.path("fieldKey").asText("");
        if (!fieldKey.isBlank()) {
            if (!fieldKeys.contains(fieldKey)) {
                error(diagnostics, "unknown_branch_field", path + ".fieldKey", "Branch condition references an unknown field");
            } else if (!activeFieldKeys.contains(fieldKey)) {
                error(diagnostics, "inactive_branch_field", path + ".fieldKey", "Branch condition references an inactive field");
            } else if (hiddenFieldKeys.contains(fieldKey)) {
                error(diagnostics, "hidden_branch_field", path + ".fieldKey", "Branch conditions cannot disclose hidden fields");
            }
        }
        for (JsonNode operand : condition.path("operands")) {
            validateCondition(operand, path + ".operands", fieldKeys, activeFieldKeys, hiddenFieldKeys, diagnostics);
        }
        if (condition.has("operand")) {
            validateCondition(condition.get("operand"), path + ".operand", fieldKeys, activeFieldKeys, hiddenFieldKeys, diagnostics);
        }
    }

    private JsonNode array(
        JsonNode nodeFlow,
        String name,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        JsonNode value = nodeFlow.path(name);
        if (!value.isArray()) {
            error(diagnostics, "invalid_node_flow_" + name, "$.nodeFlow." + name, name + " must be an array");
            return null;
        }
        return value;
    }

    private JsonNode optionalArray(
        JsonNode nodeFlow,
        String name,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        JsonNode value = nodeFlow.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isArray()) {
            error(diagnostics, "invalid_node_flow_" + name, "$.nodeFlow." + name, name + " must be an array");
            return null;
        }
        return value;
    }

    private void budget(
        JsonNode values,
        int maximum,
        String path,
        String label,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (values.size() > maximum) {
            error(diagnostics, label + "_budget_exceeded", path, "At most " + maximum + " " + label + " entries are allowed");
        }
    }

    private boolean semanticKey(
        String value,
        String path,
        String label,
        Set<String> keys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!SEMANTIC_KEY.matcher(value).matches() || !keys.add(value)) {
            error(
                diagnostics,
                "duplicate_or_invalid_" + label + "_key",
                path,
                label + " key must be unique and use the semantic key format"
            );
            return false;
        }
        return true;
    }

    private Set<String> traverse(String start, Map<String, List<String>> graph) {
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (visited.add(current)) {
                pending.addAll(graph.getOrDefault(current, List.of()));
            }
        }
        return visited;
    }

    private boolean cycle(
        String node,
        Map<String, List<String>> graph,
        Set<String> visiting,
        Set<String> visited
    ) {
        if (visiting.contains(node)) {
            return true;
        }
        if (!visited.add(node)) {
            return false;
        }
        visiting.add(node);
        for (String target : graph.getOrDefault(node, List.of())) {
            if (cycle(target, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(node);
        return false;
    }

    private Map<String, List<String>> immutable(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    private void error(
        List<ConfigurationDiagnostic> diagnostics,
        String code,
        String path,
        String message
    ) {
        diagnostics.add(new ConfigurationDiagnostic(code, DiagnosticSeverity.error, path, message));
    }

    private record NodeIndex(Map<String, JsonNode> byKey, Map<String, String> kinds) {
    }

    private record EdgeIndex(
        Map<String, JsonNode> byKey,
        Map<String, List<String>> outgoing,
        Map<String, List<String>> incoming
    ) {
    }
}
