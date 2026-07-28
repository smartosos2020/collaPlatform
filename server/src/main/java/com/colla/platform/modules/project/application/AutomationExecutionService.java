package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.AutomationExecutionModels.MAX_RECENT_RUNS;
import static com.colla.platform.modules.project.domain.AutomationExecutionModels.MAX_STEPS;
import static com.colla.platform.modules.project.domain.AutomationExecutionModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.identity.contract.SubjectDirectory;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.AutomationRun;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.ExecuteRuleCommand;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.ExecutionFoundation;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationRule;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleVersion;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.AutomationExecutionRepository;
import com.colla.platform.modules.project.infrastructure.AutomationRuleRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class AutomationExecutionService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private final AutomationRuleRepository rules;
    private final AutomationExecutionRepository executions;
    private final ProjectSpaceRepository spaces;
    private final WorkItemService workItems;
    private final WorkItemRelationService relations;
    private final SubjectDirectory subjects;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final AutomationQuotaService quotas;

    public AutomationExecutionService(
        AutomationRuleRepository rules,
        AutomationExecutionRepository executions,
        ProjectSpaceRepository spaces,
        @Lazy WorkItemService workItems,
        @Lazy WorkItemRelationService relations,
        SubjectDirectory subjects,
        AuditLog auditLog,
        @Lazy TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        AutomationQuotaService quotas
    ) {
        this.rules = rules;
        this.executions = executions;
        this.spaces = spaces;
        this.workItems = workItems;
        this.relations = relations;
        this.subjects = subjects;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.quotas = quotas;
    }

    public ExecutionFoundation list(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        List<AutomationRun> runs = executions.list(
            user.workspaceId(), spaceId, MAX_RECENT_RUNS + 1
        );
        boolean truncated = runs.size() > MAX_RECENT_RUNS;
        return new ExecutionFoundation(
            truncated ? runs.subList(0, MAX_RECENT_RUNS) : runs,
            truncated
        );
    }

    public AutomationRun execute(
        CurrentUser user, UUID spaceId, UUID ruleId, ExecuteRuleCommand command
    ) {
        requireConfigurable(user, spaceId);
        validate(command);
        return execute(
            user, spaceId, ruleId, command, "manual", command.requestId()
        );
    }

    AutomationRun executeEvent(
        CurrentUser user, UUID spaceId, UUID ruleId,
        String sourceKey, JsonNode event
    ) {
        requireVisible(user, spaceId);
        return execute(
            user, spaceId, ruleId,
            new ExecuteRuleCommand(1, sourceKey, false, event),
            "event", sourceKey
        );
    }

    private AutomationRun execute(
        CurrentUser user, UUID spaceId, UUID ruleId,
        ExecuteRuleCommand command, String sourceType, String sourceKey
    ) {
        AutomationRule rule = rules.find(user.workspaceId(), spaceId, ruleId)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Automation rule is not available"
            ));
        if (rule.publishedVersion() == null
            || (!command.dryRun() && !"enabled".equals(rule.status()))) {
            throw failure(
                "AUTOMATION_RULE_NOT_EXECUTABLE",
                "Automation rule must have an enabled published version"
            );
        }
        RuleVersion version = rules.findVersion(
            user.workspaceId(), spaceId, ruleId, rule.publishedVersion()
        ).orElseThrow(() -> failure(
            "AUTOMATION_RULE_VERSION_MISSING",
            "Published automation rule version is unavailable"
        ));
        ObjectNode input = objectMapper.createObjectNode();
        input.put("ruleId", ruleId.toString());
        input.put("ruleVersion", version.versionNumber());
        input.put("dryRun", command.dryRun());
        input.set("event", command.event());
        String inputHash = hash(input);
        AutomationExecutionRepository.StartResult start = executions.begin(
            user.workspaceId(), spaceId, ruleId, version.versionNumber(),
            sourceType, sourceKey, user.id(), command.dryRun(), inputHash
        );
        if (start.replay()) return start.run();
        if (!command.dryRun()) {
            quotas.claim(
                user.workspaceId(), spaceId, ruleId, user.id(),
                sourceType, sourceType + ":" + sourceKey
            );
        }
        UUID runId = start.run().id();
        ObjectNode definition = object(version.definition(), "definition");
        JsonNode condition = required(definition, "condition");
        ArrayNode actions = array(definition, "actions");
        if (!matches(condition, command.event())) {
            ObjectNode output = objectMapper.createObjectNode();
            output.put("matched", false);
            executions.completeRun(
                user.workspaceId(), spaceId, runId, "skipped", output, null
            );
            return executions.get(user.workspaceId(), spaceId, runId);
        }
        if (actions.isEmpty() || actions.size() > MAX_STEPS) {
            executions.completeRun(
                user.workspaceId(), spaceId, runId, "failed",
                objectMapper.createObjectNode(), "AUTOMATION_ACTION_INVALID"
            );
            return executions.get(user.workspaceId(), spaceId, runId);
        }
        ArrayNode results = objectMapper.createArrayNode();
        for (int index = 0; index < actions.size(); index++) {
            JsonNode action = actions.get(index);
            String actionType = text(action, "actionType");
            String actionHash = hash(action);
            executions.startStep(
                user.workspaceId(), spaceId, runId, index, actionType, actionHash
            );
            try {
                ObjectNode result;
                if (command.dryRun()) {
                    result = objectMapper.createObjectNode();
                    result.put("dryRun", true);
                    result.put("actionType", actionType);
                    executions.completeStep(
                        user.workspaceId(), spaceId, runId, index,
                        "skipped", result, null
                    );
                } else {
                    String key = sourceKey + ":" + index;
                    Optional<AutomationExecutionRepository.ActionReceipt> receipt =
                        executions.findActionReceipt(
                            user.workspaceId(), spaceId, ruleId,
                            version.versionNumber(), index, key
                        );
                    if (receipt.isPresent()) {
                        if (!receipt.get().inputHash().equals(actionHash)) {
                            throw failure(
                                "AUTOMATION_ACTION_RECEIPT_CONFLICT",
                                "Action receipt input changed"
                            );
                        }
                        result = object(receipt.get().response(), "action receipt");
                    } else {
                        result = executeAction(
                            user, spaceId, runId, index, actionType,
                            object(action.path("config"), "action config")
                        );
                        executions.saveActionReceipt(
                            user.workspaceId(), spaceId, ruleId,
                            version.versionNumber(), index, key, actionHash, result
                        );
                    }
                    executions.completeStep(
                        user.workspaceId(), spaceId, runId, index,
                        "succeeded", result, null
                    );
                }
                results.add(result);
            } catch (RuntimeException exception) {
                String code = exception instanceof WorkItemRuntimeException runtime
                    ? runtime.code() : "AUTOMATION_ACTION_FAILED";
                executions.completeStep(
                    user.workspaceId(), spaceId, runId, index,
                    "failed", objectMapper.createObjectNode(), code
                );
                ObjectNode output = objectMapper.createObjectNode();
                output.put("matched", true);
                output.set("results", results);
                executions.completeRun(
                    user.workspaceId(), spaceId, runId, "failed", output, code
                );
                emitRun(user, spaceId, ruleId, runId, "failed", sourceKey);
                return executions.get(user.workspaceId(), spaceId, runId);
            }
        }
        ObjectNode output = objectMapper.createObjectNode();
        output.put("matched", true);
        output.set("results", results);
        executions.completeRun(
            user.workspaceId(), spaceId, runId, "succeeded", output, null
        );
        emitRun(user, spaceId, ruleId, runId, "succeeded", sourceKey);
        return executions.get(user.workspaceId(), spaceId, runId);
    }

    private ObjectNode executeAction(
        CurrentUser user, UUID spaceId, UUID runId, int index,
        String actionType, ObjectNode config
    ) {
        String requestId = "automation:" + runId + ":" + index;
        JsonNode response = switch (actionType) {
            case "update_field" -> objectMapper.valueToTree(workItems.update(
                user, spaceId, uuid(config, "workItemId"),
                optionalText(config, "title"), required(config, "fieldValues"),
                positive(config, "expectedVersion"), requestId
            ));
            case "transition_state" -> objectMapper.valueToTree(
                workItems.executeWorkflowAction(
                    user, spaceId, uuid(config, "workItemId"),
                    requiredText(config, "actionKey"),
                    requiredText(config, "fromStateKey"),
                    positive(config, "expectedVersion"),
                    config.path("fieldPatch").isMissingNode()
                        ? objectMapper.createObjectNode() : config.path("fieldPatch"),
                    requestId
                )
            );
            case "advance_node" -> objectMapper.valueToTree(
                workItems.executeNodeTask(
                    user, spaceId, uuid(config, "workItemId"),
                    uuid(config, "taskId"),
                    requiredText(config, "operation"),
                    optionalText(config, "decision"),
                    optionalUuid(config, "targetAssigneeId"),
                    positive(config, "expectedWorkItemVersion"),
                    positive(config, "expectedInstanceVersion"),
                    requestId
                )
            );
            case "create_related_item" -> createRelated(
                user, spaceId, requestId, config
            );
            case "send_notification" -> notification(
                user, spaceId, requestId, config
            );
            default -> throw failure(
                "AUTOMATION_ACTION_UNSUPPORTED",
                "Automation action is not executable in this milestone"
            );
        };
        return object(response, "action response");
    }

    private JsonNode createRelated(
        CurrentUser user, UUID spaceId, String requestId, ObjectNode config
    ) {
        var created = workItems.create(
            user, spaceId, uuid(config, "typeId"),
            requiredText(config, "title"),
            config.path("fieldValues").isObject()
                ? config.path("fieldValues") : objectMapper.createObjectNode(),
            requestId + ":create"
        );
        var relation = relations.create(
            user, spaceId, requiredText(config, "relationKey"),
            uuid(config, "sourceWorkItemId"), created.item().id(),
            positive(config, "expectedSourceVersion"),
            created.item().version(), requestId + ":relation"
        );
        ObjectNode result = objectMapper.createObjectNode();
        result.put("workItemId", created.item().id().toString());
        result.put("relationId", relation.id().toString());
        return result;
    }

    private JsonNode notification(
        CurrentUser user, UUID spaceId, String requestId, ObjectNode config
    ) {
        UUID recipientId = uuid(config, "recipientId");
        if (subjects.findActiveMember(
            user.workspaceId(), user.id(), recipientId
        ).isEmpty()) {
            throw failure(
                "AUTOMATION_RECIPIENT_INVALID",
                "Notification recipient is not available"
            );
        }
        String title = bounded(requiredText(config, "title"), 160, "title");
        String body = bounded(optionalText(config, "body"), 1000, "body");
        UUID targetId = optionalUuid(config, "targetId");
        String targetType = optionalText(config, "targetType");
        String webPath = optionalText(config, "webPath");
        outbox.append(
            user.workspaceId(), "notification.created",
            targetType == null ? "project_space" : targetType,
            targetId == null ? spaceId : targetId,
            user.id(),
            Map.ofEntries(
                Map.entry("recipientId", recipientId.toString()),
                Map.entry("notificationType", "automation"),
                Map.entry("title", title),
                Map.entry("body", body == null ? "" : body),
                Map.entry("targetType", targetType == null ? "project_space" : targetType),
                Map.entry("targetId", (targetId == null ? spaceId : targetId).toString()),
                Map.entry("webPath", webPath == null
                    ? "/project-spaces/" + spaceId + "/work-items" : webPath),
                Map.entry("dedupeKey", "automation:" + requestId)
            ),
            "automation-notification:" + requestId
        );
        ObjectNode result = objectMapper.createObjectNode();
        result.put("recipientId", recipientId.toString());
        result.put("queued", true);
        return result;
    }

    private boolean matches(JsonNode condition, JsonNode event) {
        String kind = text(condition, "kind");
        if ("all".equals(kind)) {
            for (JsonNode child : condition.path("children")) {
                if (!matches(child, event)) return false;
            }
            return true;
        }
        if ("any".equals(kind)) {
            for (JsonNode child : condition.path("children")) {
                if (matches(child, event)) return true;
            }
            return false;
        }
        if ("not".equals(kind)) {
            return !matches(condition.path("children").get(0), event);
        }
        if (!"compare".equals(kind)) return false;
        String reference = requiredText(object(condition, "condition"), "reference");
        JsonNode actual = reference.startsWith("event.")
            ? event.at("/" + reference.substring(6).replace(".", "/"))
            : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        String operator = requiredText(object(condition, "condition"), "operator");
        JsonNode expected = condition.path("value");
        return switch (operator) {
            case "exists" -> !actual.isMissingNode() && !actual.isNull();
            case "equals" -> actual.asText().equals(expected.asText());
            case "not_equals" -> !actual.asText().equals(expected.asText());
            case "contains" -> actual.asText().contains(expected.asText());
            case "gt" -> actual.asDouble() > expected.asDouble();
            case "gte" -> actual.asDouble() >= expected.asDouble();
            case "lt" -> actual.asDouble() < expected.asDouble();
            case "lte" -> actual.asDouble() <= expected.asDouble();
            default -> false;
        };
    }

    private ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaces.findById(
            user.workspaceId(), spaceId, user.id()
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Project space is not available"
        ));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    private void requireConfigurable(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if (!"active".equals(space.status())
            || !Set.of("owner", "admin").contains(space.currentUserRole())) {
            throw failure(
                "FORBIDDEN",
                "Only project space owners and administrators can execute automation manually"
            );
        }
    }

    private void validate(ExecuteRuleCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || command.requestId() == null
            || !REQUEST_ID.matcher(command.requestId()).matches()
            || command.event() == null || !command.event().isObject()
            || json(command.event()).length() > 32_768) {
            throw failure(
                "AUTOMATION_EXECUTION_INVALID",
                "Automation execution input is invalid"
            );
        }
    }

    private void emitRun(
        CurrentUser user, UUID spaceId, UUID ruleId, UUID runId,
        String status, String sourceKey
    ) {
        auditLog.log(
            user, "project_automation.run_" + status,
            "project_automation_run", runId,
            Map.of("rule_id", ruleId.toString(), "space_id", spaceId.toString())
        );
        outbox.append(
            user.workspaceId(), "project.automation.run.changed",
            "project_automation_run", runId, user.id(),
            Map.of(
                "ruleId", ruleId.toString(),
                "spaceId", spaceId.toString(),
                "status", status
            ),
            "project-automation-run:" + sourceKey
        );
    }

    private ObjectNode object(JsonNode value, String label) {
        if (value == null || !value.isObject()) {
            throw failure(
                "AUTOMATION_ACTION_INVALID", "Automation " + label + " must be an object"
            );
        }
        return (ObjectNode) value;
    }

    private ArrayNode array(JsonNode value, String name) {
        JsonNode node = value.path(name);
        if (!node.isArray()) {
            throw failure(
                "AUTOMATION_ACTION_INVALID", "Automation " + name + " must be an array"
            );
        }
        return (ArrayNode) node;
    }

    private JsonNode required(JsonNode value, String name) {
        JsonNode node = value.path(name);
        if (node.isMissingNode() || node.isNull()) {
            throw failure(
                "AUTOMATION_ACTION_INVALID", "Automation " + name + " is required"
            );
        }
        return node;
    }

    private String requiredText(JsonNode value, String name) {
        String result = optionalText(value, name);
        if (result == null || result.isBlank()) {
            throw failure(
                "AUTOMATION_ACTION_INVALID", "Automation " + name + " is required"
            );
        }
        return result;
    }

    private String optionalText(JsonNode value, String name) {
        JsonNode node = value.path(name);
        return node.isTextual() ? node.textValue() : null;
    }

    private String text(JsonNode value, String name) {
        return optionalText(value, name);
    }

    private UUID uuid(JsonNode value, String name) {
        UUID result = optionalUuid(value, name);
        if (result == null) {
            throw failure(
                "AUTOMATION_ACTION_INVALID", "Automation " + name + " is invalid"
            );
        }
        return result;
    }

    private UUID optionalUuid(JsonNode value, String name) {
        try {
            String text = optionalText(value, name);
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private long positive(JsonNode value, String name) {
        long result = value.path(name).asLong(-1);
        if (result < 1) {
            throw failure(
                "AUTOMATION_ACTION_INVALID", "Automation " + name + " is invalid"
            );
        }
        return result;
    }

    private String bounded(String value, int maximum, String name) {
        if (value != null && value.length() > maximum) {
            throw failure(
                "AUTOMATION_ACTION_INVALID", "Automation " + name + " is too long"
            );
        }
        return value;
    }

    private String hash(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    json(value).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
