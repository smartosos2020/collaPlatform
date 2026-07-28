package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.AutomationRuleModels.MAX_ACTIONS;
import static com.colla.platform.modules.project.domain.AutomationRuleModels.MAX_CONDITION_DEPTH;
import static com.colla.platform.modules.project.domain.AutomationRuleModels.MAX_CONDITION_NODES;
import static com.colla.platform.modules.project.domain.AutomationRuleModels.MAX_RULES;
import static com.colla.platform.modules.project.domain.AutomationRuleModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.AutomationRuleModels.ActionCatalogEntry;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationFoundation;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationRule;
import com.colla.platform.modules.project.domain.AutomationRuleModels.EventCatalogEntry;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleLifecycleCommand;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleVersion;
import com.colla.platform.modules.project.domain.AutomationRuleModels.SaveRuleCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.AutomationRuleRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationRuleService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Pattern REFERENCE = Pattern.compile(
        "^(event|workItem|actor|resource)\\.[A-Za-z][A-Za-z0-9_.-]{0,119}$"
    );
    private static final Set<String> OPERATORS = Set.of(
        "equals", "not_equals", "contains", "gt", "gte", "lt", "lte", "exists"
    );
    private static final List<EventCatalogEntry> EVENTS = List.of(
        event("project.work-item.changed", "aggregateId", "actorId", "eventType", "occurredAt", "workspaceId"),
        event("project.workflow.changed", "aggregateId", "actorId", "eventType", "occurredAt", "workspaceId"),
        event("project.node-workflow.changed", "aggregateId", "actorId", "eventType", "occurredAt", "workspaceId"),
        event("project.relation.changed", "aggregateId", "actorId", "eventType", "occurredAt", "workspaceId"),
        event("project.resource.changed", "aggregateId", "actorId", "eventType", "kind", "occurredAt", "version", "workspaceId")
    );
    private static final List<ActionCatalogEntry> ACTIONS = List.of(
        new ActionCatalogEntry("update_field", 1, true, "project"),
        new ActionCatalogEntry("transition_state", 1, true, "project"),
        new ActionCatalogEntry("advance_node", 1, true, "project"),
        new ActionCatalogEntry("create_related_item", 1, true, "project"),
        new ActionCatalogEntry("send_notification", 1, true, "notification"),
        new ActionCatalogEntry("webhook", 1, true, "project")
    );

    private final AutomationRuleRepository repository;
    private final ProjectSpaceRepository spaces;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public AutomationRuleService(
        AutomationRuleRepository repository,
        ProjectSpaceRepository spaces,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.spaces = spaces;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public AutomationFoundation get(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        List<AutomationRule> rules = repository.list(
            user.workspaceId(), spaceId, MAX_RULES + 1
        );
        boolean truncated = rules.size() > MAX_RULES;
        return new AutomationFoundation(
            SCHEMA_VERSION, EVENTS, ACTIONS,
            truncated ? rules.subList(0, MAX_RULES) : rules,
            truncated
        );
    }

    @Transactional
    public AutomationRule save(
        CurrentUser user, UUID spaceId, SaveRuleCommand command
    ) {
        requireConfigurable(user, spaceId);
        validateSave(command);
        String requestHash = hash(command);
        Optional<AutomationRuleRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "save_rule", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), AutomationRule.class);
        }
        AutomationRule result = repository.save(
            user.workspaceId(), spaceId, user.id(), command.ruleId(),
            command.name().trim(), command.trigger(), command.condition(),
            command.actions(), command.expectedVersion(),
            command.requestId(), requestHash
        );
        emit(user, spaceId, result.id(), result.version(), "saved", command.requestId());
        return result;
    }

    @Transactional
    public RuleVersion publish(
        CurrentUser user, UUID spaceId, UUID ruleId,
        RuleLifecycleCommand command
    ) {
        requireConfigurable(user, spaceId);
        validateLifecycle(command, "publish");
        AutomationRule rule = requireRule(user, spaceId, ruleId);
        if (rule.version() != command.expectedVersion()) {
            throw failure(
                "AUTOMATION_RULE_VERSION_CONFLICT",
                "Automation rule changed; refresh before publishing"
            );
        }
        ObjectNode definition = objectMapper.createObjectNode();
        definition.put("schemaVersion", SCHEMA_VERSION);
        definition.put("name", rule.name());
        definition.set("trigger", rule.trigger());
        definition.set("condition", rule.condition());
        definition.set("actions", rule.actions());
        String definitionHash = hash(definition);
        String requestHash = hash(command);
        Optional<AutomationRuleRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "publish_rule", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), RuleVersion.class);
        }
        RuleVersion result = repository.publish(
            user.workspaceId(), spaceId, user.id(), ruleId,
            command.expectedVersion(), definitionHash, definition,
            command.requestId(), requestHash
        );
        emit(user, spaceId, ruleId, result.versionNumber(), "published", command.requestId());
        return result;
    }

    @Transactional
    public AutomationRule lifecycle(
        CurrentUser user, UUID spaceId, UUID ruleId,
        RuleLifecycleCommand command
    ) {
        requireConfigurable(user, spaceId);
        validateLifecycle(command, command.action());
        requireRule(user, spaceId, ruleId);
        String operation = command.action() + "_rule";
        String requestHash = hash(command);
        Optional<AutomationRuleRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), operation, command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), AutomationRule.class);
        }
        AutomationRule result = repository.changeLifecycle(
            user.workspaceId(), spaceId, user.id(), ruleId,
            command.action(), command.expectedVersion(),
            command.requestId(), requestHash
        );
        emit(
            user, spaceId, ruleId, result.version(),
            command.action() + "d", command.requestId()
        );
        return result;
    }

    private void validateSave(SaveRuleCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || command.name() == null || command.name().trim().length() < 2
            || command.name().trim().length() > 160
            || command.trigger() == null || command.condition() == null
            || command.actions() == null
            || (command.ruleId() == null && command.expectedVersion() != 0)
            || (command.ruleId() != null && command.expectedVersion() == 0)) {
            throw failure("AUTOMATION_RULE_INVALID", "Automation rule is invalid");
        }
        validateTrigger(command.trigger());
        int[] nodes = {0};
        validateCondition(command.condition(), 1, nodes);
        validateActions(command.actions());
        if (json(command).length() > 65_536) {
            throw failure("AUTOMATION_RULE_TOO_LARGE", "Automation rule exceeds the size limit");
        }
    }

    private void validateTrigger(JsonNode trigger) {
        if ("schedule".equals(text(trigger, "type"))) {
            try {
                String kind = text(trigger, "kind");
                String timezone = text(trigger, "timezone");
                String expression = text(trigger, "expression");
                String missedPolicy = text(trigger, "missedPolicy");
                if (!trigger.isObject()
                    || trigger.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
                    || !Set.of("cron", "fixed_time", "due", "overdue", "dwell").contains(kind)
                    || timezone == null || expression == null || expression.isBlank()
                    || expression.length() > 160
                    || !Set.of("skip", "latest", "bounded").contains(missedPolicy)
                    || forbidden(trigger)) {
                    throw failure("AUTOMATION_TRIGGER_INVALID", "Automation schedule trigger is invalid");
                }
                ZoneId.of(timezone);
                return;
            } catch (ZoneRulesException exception) {
                throw failure("AUTOMATION_TRIGGER_INVALID", "Automation schedule timezone is invalid");
            }
        }
        String eventType = text(trigger, "eventType");
        int eventVersion = trigger.path("eventVersion").asInt(-1);
        if (!trigger.isObject() || trigger.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
            || !"event".equals(text(trigger, "type"))
            || EVENTS.stream().noneMatch(value ->
                value.eventType().equals(eventType) && value.eventVersion() == eventVersion
            )
            || forbidden(trigger)) {
            throw failure(
                "AUTOMATION_TRIGGER_INVALID",
                "Automation trigger must reference a supported public event"
            );
        }
    }

    private void validateCondition(JsonNode node, int depth, int[] nodes) {
        nodes[0]++;
        if (!node.isObject() || depth > MAX_CONDITION_DEPTH
            || nodes[0] > MAX_CONDITION_NODES || forbidden(node)) {
            throw failure(
                "AUTOMATION_CONDITION_INVALID",
                "Automation condition is invalid or exceeds its bound"
            );
        }
        String kind = text(node, "kind");
        if ("compare".equals(kind)) {
            String reference = text(node, "reference");
            String operator = text(node, "operator");
            if (reference == null || !REFERENCE.matcher(reference).matches()
                || !OPERATORS.contains(operator)) {
                throw failure(
                    "AUTOMATION_CONDITION_INVALID",
                    "Automation comparison is not supported"
                );
            }
            return;
        }
        JsonNode children = node.path("children");
        int minimum = "not".equals(kind) ? 1 : 0;
        int maximum = "not".equals(kind) ? 1 : 16;
        if (!Set.of("all", "any", "not").contains(kind) || !children.isArray()
            || children.size() < minimum || children.size() > maximum) {
            throw failure(
                "AUTOMATION_CONDITION_INVALID",
                "Automation condition group is invalid"
            );
        }
        children.forEach(child -> validateCondition(child, depth + 1, nodes));
    }

    private void validateActions(JsonNode actions) {
        Set<String> supported = new HashSet<>(
            ACTIONS.stream().map(ActionCatalogEntry::actionType).toList()
        );
        if (!actions.isArray() || actions.isEmpty() || actions.size() > MAX_ACTIONS) {
            throw failure(
                "AUTOMATION_ACTION_INVALID",
                "Automation actions must be a bounded non-empty list"
            );
        }
        actions.forEach(action -> {
            if (!action.isObject()
                || action.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
                || !supported.contains(text(action, "actionType"))
                || forbidden(action)) {
                throw failure(
                    "AUTOMATION_ACTION_INVALID",
                    "Automation action is not supported"
                );
            }
        });
    }

    private boolean forbidden(JsonNode node) {
        String value = node.toString().toLowerCase(java.util.Locale.ROOT);
        return value.contains("\"script\"")
            || value.contains("\"sql\"")
            || value.contains("\"code\"")
            || value.contains("\"template\"");
    }

    private AutomationRule requireRule(
        CurrentUser user, UUID spaceId, UUID ruleId
    ) {
        return repository.find(user.workspaceId(), spaceId, ruleId)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Automation rule is not available"
            ));
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
                "Only project space owners and administrators can configure automation"
            );
        }
    }

    private void validateLifecycle(
        RuleLifecycleCommand command, String requiredAction
    ) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 1
            || command.action() == null
            || (!"publish".equals(requiredAction)
                && !Set.of("enable", "disable", "archive").contains(requiredAction))
            || !requiredAction.equals(command.action())) {
            throw failure(
                "AUTOMATION_RULE_COMMAND_INVALID",
                "Automation lifecycle command is invalid"
            );
        }
    }

    private void emit(
        CurrentUser user, UUID spaceId, UUID ruleId,
        long version, String change, String requestId
    ) {
        auditLog.log(
            user, "project_automation.rule_" + change,
            "project_automation_rule", ruleId,
            Map.of("space_id", spaceId.toString(), "version", version)
        );
        outbox.append(
            user.workspaceId(), "project.automation.rule.changed",
            "project_automation_rule", ruleId, user.id(),
            Map.of("change", change, "spaceId", spaceId.toString(), "version", version),
            "project-automation-rule:" + requestId
        );
    }

    private static EventCatalogEntry event(String eventType, String... fields) {
        return new EventCatalogEntry(eventType, 1, List.of(fields));
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isTextual() ? value.textValue() : null;
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private void requireHash(
        AutomationRuleRepository.CommandRecord record, String hash
    ) {
        if (!hash.equals(record.requestHash())) {
            throw failure(
                "AUTOMATION_REQUEST_CONFLICT",
                "Request ID was reused with different input"
            );
        }
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

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
