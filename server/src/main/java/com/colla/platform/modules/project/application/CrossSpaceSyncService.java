package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.MAX_CHAIN_DEPTH;
import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.MAX_FIELD_MAPPINGS;
import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.MAX_RETRIES;
import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.MAX_RULES;
import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.MAX_RUNS;
import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.MAX_STATE_MAPPINGS;
import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.MAX_STEPS;
import static com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand.CanonicalRelationReference;
import com.colla.platform.modules.project.contract.CrossSpaceWorkItemCommand;
import com.colla.platform.modules.project.contract.CrossSpaceWorkItemCommand.CommandResult;
import com.colla.platform.modules.project.contract.CrossSpaceWorkItemCommand.EndpointSnapshot;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CrossSpaceRelationPolicy;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.ExecuteSyncCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.ResolveConflictCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SaveSyncRuleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncConflict;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncFoundation;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRule;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRuleLifecycleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRun;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRunDetail;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncStep;
import com.colla.platform.modules.project.infrastructure.CrossSpaceRelationRepository;
import com.colla.platform.modules.project.infrastructure.CrossSpaceSyncRepository;
import com.colla.platform.modules.project.infrastructure.CrossSpaceSyncRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.CrossSpaceSyncRepository.NewRule;
import com.colla.platform.modules.project.infrastructure.CrossSpaceSyncRepository.NewRun;
import com.colla.platform.modules.project.infrastructure.CrossSpaceSyncRepository.NewVersion;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrossSpaceSyncService {
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");
    private static final Pattern KEY = Pattern.compile("(?:title|[a-z][a-z0-9_]{0,63})");
    private static final Set<String> DIRECTIONS = Set.of(
        "source_to_target", "target_to_source", "bidirectional"
    );
    private static final Set<String> EXECUTION_DIRECTIONS = Set.of(
        "source_to_target", "target_to_source"
    );
    private static final Set<String> TRIGGERS = Set.of(
        "manual", "work_item_changed", "workflow_state_changed"
    );
    private static final Set<String> STRATEGIES = Set.of(
        "manual", "source_wins", "target_wins"
    );

    private final CrossSpaceSyncRepository sync;
    private final CrossSpaceRelationRepository relationPolicies;
    private final CrossSpaceRelationCommand relations;
    private final CrossSpaceWorkItemCommand workItems;
    private final CrossSpaceGrantService grants;
    private final WorkItemRelationAccessDecisionService access;
    private final AuditLog audit;
    private final TransactionalOutbox outbox;
    private final ObjectMapper mapper;

    public CrossSpaceSyncService(
        CrossSpaceSyncRepository sync,
        CrossSpaceRelationRepository relationPolicies,
        CrossSpaceRelationCommand relations,
        CrossSpaceWorkItemCommand workItems,
        CrossSpaceGrantService grants,
        WorkItemRelationAccessDecisionService access,
        AuditLog audit,
        TransactionalOutbox outbox,
        ObjectMapper mapper
    ) {
        this.sync = sync;
        this.relationPolicies = relationPolicies;
        this.relations = relations;
        this.workItems = workItems;
        this.grants = grants;
        this.access = access;
        this.audit = audit;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    public SyncFoundation list(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        List<SyncRule> rules = sync.listRules(
            user.workspaceId(), spaceId, MAX_RULES + 1
        );
        List<SyncRun> runs = sync.listRuns(
            user.workspaceId(), spaceId, MAX_RUNS + 1
        );
        List<SyncConflict> conflicts = sync.listConflicts(
            user.workspaceId(), spaceId, MAX_RUNS + 1
        );
        boolean truncated = rules.size() > MAX_RULES
            || runs.size() > MAX_RUNS || conflicts.size() > MAX_RUNS;
        return new SyncFoundation(
            SCHEMA_VERSION,
            EXECUTION_DIRECTIONS.stream().sorted().toList(),
            TRIGGERS.stream().sorted().toList(),
            STRATEGIES.stream().sorted().toList(),
            bounded(rules, MAX_RULES),
            bounded(runs, MAX_RUNS),
            bounded(conflicts, MAX_RUNS),
            truncated
        );
    }

    public SyncRunDetail run(CurrentUser user, UUID runId) {
        SyncRun run = requireRun(user, runId);
        SyncConflict conflict = sync.listConflicts(
            user.workspaceId(), run.sourceSpaceId(), MAX_RUNS
        ).stream().filter(value -> value.runId().equals(run.id())).findFirst().orElse(null);
        return new SyncRunDetail(
            run, sync.listSteps(user.workspaceId(), run.id(), MAX_STEPS), conflict
        );
    }

    @Transactional
    public SyncRule save(
        CurrentUser user, UUID spaceId, SaveSyncRuleCommand command
    ) {
        validateRule(command);
        access.requireManager(user, spaceId);
        String operation = command.ruleId() == null ? "rule_create" : "rule_revise";
        String requestHash = hash(command);
        Optional<SyncRule> replay = replay(
            user, operation, command.requestId(), requestHash, SyncRule.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        CrossSpaceRelationPolicy policy = requirePolicy(
            user, command.policyId(), false
        );
        if (!policy.sourceSpaceId().equals(spaceId)
            || !policy.grantId().equals(command.grantId())
            || !"active".equals(policy.status())) {
            throw failure(
                "CROSS_SPACE_SYNC_REFERENCE_FORBIDDEN",
                "Cross-space sync reference is forbidden"
            );
        }
        requireGrant(user, command.grantId(), command.fieldMappings(), command.stateMappings());
        CanonicalRelationReference relation = relations.find(
            user.workspaceId(), command.canonicalRelationId()
        ).filter(value -> value.policyId().equals(policy.id())
            && "active".equals(value.status()))
            .orElseThrow(() -> failure(
                "CROSS_SPACE_SYNC_REFERENCE_FORBIDDEN",
                "Cross-space sync relation is forbidden"
            ));
        String configHash = hash(Map.of(
            "direction", command.direction(),
            "trigger", command.trigger(),
            "fieldMappings", command.fieldMappings(),
            "stateMappings", command.stateMappings(),
            "conflictStrategy", command.conflictStrategy()
        ));
        SyncRule result;
        try {
            if (command.ruleId() == null) {
                result = sync.createRule(new NewRule(
                    user.workspaceId(), user.id(), command.grantId(), policy.id(),
                    relation.relationId(), policy.sourceSpaceId(), policy.targetSpaceId(),
                    command.name().trim(), command.direction(), command.trigger(),
                    command.fieldMappings(), command.stateMappings(),
                    command.conflictStrategy(), configHash
                ));
            } else {
                SyncRule existing = requireRule(user, command.ruleId(), true);
                if (!existing.grantId().equals(command.grantId())
                    || !existing.policyId().equals(command.policyId())
                    || !existing.canonicalRelationId().equals(command.canonicalRelationId())) {
                    throw failure(
                        "CROSS_SPACE_SYNC_BOUNDARY_IMMUTABLE",
                        "Sync grant, policy and relation cannot change"
                    );
                }
                result = sync.reviseRule(new NewVersion(
                    user.workspaceId(), user.id(), existing.id(), command.name().trim(),
                    command.direction(), command.trigger(), command.fieldMappings(),
                    command.stateMappings(), command.conflictStrategy(), configHash
                ), command.expectedVersion());
            }
        } catch (DataIntegrityViolationException | IllegalStateException exception) {
            throw failure(
                "CROSS_SPACE_SYNC_VERSION_CONFLICT",
                "Sync rule changed or conflicts with an existing rule",
                exception
            );
        }
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    @Transactional
    public SyncRule lifecycle(
        CurrentUser user, UUID ruleId, SyncRuleLifecycleCommand command
    ) {
        validateLifecycle(command);
        SyncRule rule = requireRule(user, ruleId, true);
        String party = resolveManagerParty(user, rule, command.party());
        String operation = "rule_" + command.action();
        String requestHash = hash(command);
        Optional<SyncRule> replay = replay(
            user, operation, command.requestId(), requestHash, SyncRule.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        if (!Set.of("revoke", "archive").contains(command.action())) {
            requireGrant(
                user, rule.grantId(),
                rule.configuration().fieldMappings(), rule.configuration().stateMappings()
            );
            requireActiveRelation(user, rule);
        }
        if (sync.transitionRule(
            user.workspaceId(), rule.id(), command.expectedVersion(),
            user.id(), command.action(), party
        ) != 1) {
            throw failure(
                "CROSS_SPACE_SYNC_VERSION_CONFLICT",
                "Sync rule changed or transition is invalid"
            );
        }
        SyncRule result = requireRule(user, ruleId, false);
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    @Transactional
    public SyncRun execute(
        CurrentUser user, UUID ruleId, ExecuteSyncCommand command
    ) {
        validateExecution(command);
        SyncRule rule = requireRule(user, ruleId, true);
        if (!"active".equals(rule.status())
            || rule.currentVersion() != command.expectedRuleVersion()
            || !directionAllowed(rule.configuration().direction(), command.direction())) {
            throw failure(
                "CROSS_SPACE_SYNC_VERSION_CONFLICT",
                "Sync rule is not active at the requested version and direction"
            );
        }
        requireGrant(
            user, rule.grantId(),
            rule.configuration().fieldMappings(), rule.configuration().stateMappings()
        );
        CanonicalRelationReference relation = requireActiveRelation(user, rule);
        boolean reverse = "target_to_source".equals(command.direction());
        UUID sourceSpace = reverse ? relation.targetSpaceId() : relation.sourceSpaceId();
        UUID sourceItem = reverse ? relation.targetWorkItemId() : relation.sourceWorkItemId();
        UUID targetSpace = reverse ? relation.sourceSpaceId() : relation.targetSpaceId();
        UUID targetItem = reverse ? relation.sourceWorkItemId() : relation.targetWorkItemId();
        UUID sourceActorId = reverse
            ? rule.targetConfirmedBy() : rule.sourceConfirmedBy();
        UUID targetActorId = reverse
            ? rule.sourceConfirmedBy() : rule.targetConfirmedBy();
        if (sourceActorId == null || targetActorId == null) {
            throw failure(
                "CROSS_SPACE_SYNC_REAUTHORIZE_REQUIRED",
                "Both sync parties must confirm the current rule"
            );
        }
        CurrentUser sourceActor = delegated(user, sourceActorId);
        CurrentUser targetActor = delegated(user, targetActorId);
        access.requireManager(sourceActor, sourceSpace);
        access.requireManager(targetActor, targetSpace);
        EndpointSnapshot source = workItems.snapshot(sourceActor, sourceSpace, sourceItem);
        EndpointSnapshot target = workItems.snapshot(targetActor, targetSpace, targetItem);
        String fingerprint = hash(Map.of(
            "ruleVersion", rule.currentVersion(),
            "direction", command.direction(),
            "sourceId", source.workItemId(),
            "sourceVersion", source.version(),
            "targetId", target.workItemId(),
            "targetVersion", target.version(),
            "mappings", rule.configuration().configHash()
        ));
        String operation = "run_execute";
        String requestHash = hash(command);
        Optional<SyncRun> receiptReplay = replay(
            user, operation, command.requestId(), requestHash, SyncRun.class
        );
        if (receiptReplay.isPresent()) {
            return receiptReplay.get();
        }
        Optional<SyncRun> originReplay = sync.findRunByOrigin(
            user.workspaceId(), rule.id(), command.direction(),
            command.originId(), fingerprint
        );
        if (originReplay.isPresent()) {
            complete(
                user, operation, command.requestId(), requestHash,
                originReplay.get(), originReplay.get().id()
            );
            return originReplay.get();
        }
        UUID runId = UUID.randomUUID();
        SyncRun created;
        try {
            created = sync.createRun(new NewRun(
                runId, user.workspaceId(), user.id(), rule,
                command.direction(), command.originId(), command.causationId(),
                command.chainDepth(), fingerprint,
                sourceSpace, sourceItem, source.version(),
                targetSpace, targetItem, target.version()
            ));
        } catch (DataIntegrityViolationException exception) {
            SyncRun replay = sync.findRunByOrigin(
                user.workspaceId(), rule.id(), command.direction(),
                command.originId(), fingerprint
            ).orElseThrow(() -> failure(
                "CROSS_SPACE_SYNC_DUPLICATE",
                "Sync origin is already running"
            ));
            complete(user, operation, command.requestId(), requestHash, replay, replay.id());
            return replay;
        }
        if (source.version() != command.expectedSourceVersion()
            || target.version() != command.expectedTargetVersion()) {
            conflict(
                user, created, "target_version",
                fingerprint(source), fingerprint(target)
            );
            SyncRun result = requireRun(user, created.id());
            complete(user, operation, command.requestId(), requestHash, result, result.id());
            return result;
        }
        SyncRun result = apply(
            user, rule, created, sourceActor, targetActor, source, target
        );
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    @Transactional
    public SyncConflict resolve(
        CurrentUser user, UUID conflictId, ResolveConflictCommand command
    ) {
        validateResolution(command);
        SyncConflict conflict = sync.findConflict(
            user.workspaceId(), conflictId, true
        ).orElseThrow(() -> failure(
            "CROSS_SPACE_SYNC_NOT_FOUND", "Sync conflict is not available"
        ));
        SyncRun run = requireRun(user, conflict.runId());
        SyncRule rule = requireRule(user, run.ruleId(), true);
        requireEitherManager(user, rule);
        String operation = "conflict_resolve";
        String requestHash = hash(command);
        Optional<SyncConflict> replay = replay(
            user, operation, command.requestId(), requestHash, SyncConflict.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        if (sync.resolveConflict(
            user.workspaceId(), conflict.id(), command.expectedVersion(),
            user.id(), command.resolution(), hashText(command.reason())
        ) != 1) {
            throw failure(
                "CROSS_SPACE_SYNC_VERSION_CONFLICT",
                "Sync conflict changed or is already closed"
            );
        }
        if ("compensate".equals(command.resolution())) {
            sync.finishRun(
                user.workspaceId(), run.id(), "compensated",
                run.resultTargetVersion(), "MANUAL_COMPENSATION_RECORDED"
            );
        } else if ("dead_letter".equals(command.resolution())) {
            sync.finishRun(
                user.workspaceId(), run.id(), "dead_letter",
                run.resultTargetVersion(), "MANUAL_DEAD_LETTER"
            );
        }
        SyncConflict result = sync.findConflict(
            user.workspaceId(), conflictId, false
        ).orElseThrow();
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    private SyncRun apply(
        CurrentUser caller,
        SyncRule rule,
        SyncRun run,
        CurrentUser sourceActor,
        CurrentUser targetActor,
        EndpointSnapshot source,
        EndpointSnapshot target
    ) {
        ObjectNode patch = mapper.createObjectNode();
        String title = null;
        List<JsonNode> fieldMappings = nodes(rule.configuration().fieldMappings());
        for (JsonNode mapping : fieldMappings) {
            String sourceField = mapping.path("sourceField").asText();
            String targetField = mapping.path("targetField").asText();
            JsonNode value = "title".equals(sourceField)
                ? mapper.valueToTree(source.title()) : source.fieldValues().path(sourceField);
            if (!value.isMissingNode()) {
                if ("title".equals(targetField)) {
                    title = value.asText();
                } else {
                    patch.set(targetField, value.deepCopy());
                }
            }
        }
        long currentTargetVersion = target.version();
        int index = 0;
        if (!fieldMappings.isEmpty()) {
            String requestId = commandId(run, index);
            try {
                CommandResult result = workItems.update(
                    targetActor, target.spaceId(), target.workItemId(),
                    title, patch, currentTargetVersion, requestId
                );
                for (JsonNode mapping : fieldMappings) {
                    sync.appendStep(caller.workspaceId(), run.id(), new SyncStep(
                        index++, "field", mappingKey(mapping),
                        run.inputFingerprint(), requestId, "succeeded",
                        currentTargetVersion, result.version(), null
                    ));
                }
                currentTargetVersion = result.version();
            } catch (RuntimeException exception) {
                sync.appendStep(caller.workspaceId(), run.id(), new SyncStep(
                    index, "field", "field-batch", run.inputFingerprint(),
                    requestId, "failed", currentTargetVersion, null,
                    "CANONICAL_FIELD_COMMAND_FAILED"
                ));
                conflict(
                    caller, run, "partial_failure",
                    fingerprint(source), fingerprint(target)
                );
                return requireRun(caller, run.id());
            }
        }
        for (JsonNode mapping : nodes(rule.configuration().stateMappings())) {
            String sourceState = source.fieldValues().path("state").asText("");
            if (!sourceState.equals(mapping.path("sourceState").asText())) {
                sync.appendStep(caller.workspaceId(), run.id(), new SyncStep(
                    index++, "state", mappingKey(mapping), run.inputFingerprint(),
                    null, "skipped", currentTargetVersion, currentTargetVersion, null
                ));
                continue;
            }
            String requestId = commandId(run, index);
            try {
                CommandResult state = workItems.transition(
                    targetActor, target.spaceId(), target.workItemId(),
                    mapping.path("targetAction").asText(),
                    mapping.path("targetFromState").asText(),
                    mapper.createObjectNode(), currentTargetVersion, requestId
                );
                sync.appendStep(caller.workspaceId(), run.id(), new SyncStep(
                    index++, "state", mappingKey(mapping), run.inputFingerprint(),
                    requestId, "succeeded", currentTargetVersion, state.version(), null
                ));
                currentTargetVersion = state.version();
            } catch (RuntimeException exception) {
                sync.appendStep(caller.workspaceId(), run.id(), new SyncStep(
                    index, "state", mappingKey(mapping), run.inputFingerprint(),
                    requestId, "failed", currentTargetVersion, null,
                    "CANONICAL_STATE_COMMAND_FAILED"
                ));
                conflict(
                    caller, run, "partial_failure",
                    fingerprint(source), fingerprint(target)
                );
                return requireRun(caller, run.id());
            }
        }
        sync.finishRun(
            caller.workspaceId(), run.id(), "succeeded", currentTargetVersion, null
        );
        return requireRun(caller, run.id());
    }

    private void conflict(
        CurrentUser user, SyncRun run, String kind,
        String sourceFingerprint, String targetFingerprint
    ) {
        sync.finishRun(user.workspaceId(), run.id(), "conflict", null, kind);
        sync.createConflict(
            user.workspaceId(), run.id(), kind, sourceFingerprint, targetFingerprint
        );
    }

    private SyncRule requireRule(CurrentUser user, UUID ruleId, boolean lock) {
        SyncRule rule = sync.findRule(user.workspaceId(), ruleId, lock)
            .orElseThrow(() -> failure(
                "CROSS_SPACE_SYNC_NOT_FOUND", "Sync rule is not available"
            ));
        if (!member(user, rule.sourceSpaceId()) && !member(user, rule.targetSpaceId())) {
            throw failure("CROSS_SPACE_SYNC_NOT_FOUND", "Sync rule is not available");
        }
        return rule;
    }

    private SyncRun requireRun(CurrentUser user, UUID runId) {
        SyncRun run = sync.findRun(user.workspaceId(), runId)
            .orElseThrow(() -> failure(
                "CROSS_SPACE_SYNC_NOT_FOUND", "Sync run is not available"
            ));
        if (!member(user, run.sourceSpaceId()) && !member(user, run.targetSpaceId())) {
            throw failure("CROSS_SPACE_SYNC_NOT_FOUND", "Sync run is not available");
        }
        return run;
    }

    private CrossSpaceRelationPolicy requirePolicy(
        CurrentUser user, UUID policyId, boolean lock
    ) {
        CrossSpaceRelationPolicy policy = relationPolicies.findPolicy(
            user.workspaceId(), policyId, lock
        ).orElseThrow(() -> failure(
            "CROSS_SPACE_SYNC_REFERENCE_FORBIDDEN",
            "Cross-space sync policy is forbidden"
        ));
        if (!member(user, policy.sourceSpaceId()) && !member(user, policy.targetSpaceId())) {
            throw failure(
                "CROSS_SPACE_SYNC_REFERENCE_FORBIDDEN",
                "Cross-space sync policy is forbidden"
            );
        }
        return policy;
    }

    private CanonicalRelationReference requireActiveRelation(
        CurrentUser user, SyncRule rule
    ) {
        return relations.find(user.workspaceId(), rule.canonicalRelationId())
            .filter(value -> "active".equals(value.status())
                && value.policyId().equals(rule.policyId()))
            .orElseThrow(() -> failure(
                "CROSS_SPACE_SYNC_REFERENCE_FORBIDDEN",
                "Cross-space sync relation is forbidden"
            ));
    }

    private void requireGrant(
        CurrentUser user, UUID grantId, JsonNode fields, JsonNode states
    ) {
        if (!fields.isEmpty()) {
            grants.requireActiveGrant(user, grantId, "read_fields");
            grants.requireActiveGrant(user, grantId, "sync_fields");
        }
        if (!states.isEmpty()) {
            grants.requireActiveGrant(user, grantId, "sync_state");
        }
    }

    private void validateRule(SaveSyncRuleCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedVersion() < 0
            || (command.ruleId() == null && command.expectedVersion() != 0)
            || (command.ruleId() != null && command.expectedVersion() < 1)
            || command.grantId() == null || command.policyId() == null
            || command.canonicalRelationId() == null
            || command.name() == null || command.name().trim().length() < 2
            || command.name().trim().length() > 160
            || !DIRECTIONS.contains(command.direction())
            || !TRIGGERS.contains(command.trigger())
            || !STRATEGIES.contains(command.conflictStrategy())
            || !validMappings(command.fieldMappings(), command.stateMappings())) {
            throw failure(
                "CROSS_SPACE_SYNC_RULE_INVALID", "Cross-space sync rule is invalid"
            );
        }
    }

    private boolean validMappings(JsonNode fields, JsonNode states) {
        if (fields == null || states == null || !fields.isArray() || !states.isArray()
            || fields.size() > MAX_FIELD_MAPPINGS
            || states.size() > MAX_STATE_MAPPINGS
            || (fields.isEmpty() && states.isEmpty())) {
            return false;
        }
        for (JsonNode mapping : fields) {
            if (!KEY.matcher(mapping.path("sourceField").asText()).matches()
                || !KEY.matcher(mapping.path("targetField").asText()).matches()
                || !"copy".equals(mapping.path("transform").asText())
                || mapping.size() != 3) {
                return false;
            }
        }
        for (JsonNode mapping : states) {
            if (!KEY.matcher(mapping.path("sourceState").asText()).matches()
                || !KEY.matcher(mapping.path("targetFromState").asText()).matches()
                || !KEY.matcher(mapping.path("targetAction").asText()).matches()
                || mapping.size() != 3) {
                return false;
            }
        }
        return true;
    }

    private void validateLifecycle(SyncRuleLifecycleCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedVersion() < 1
            || !Set.of("request","confirm","pause","resume","revoke","archive")
                .contains(command.action())
            || ("confirm".equals(command.action())
                && !Set.of("source","target").contains(command.party()))
            || (Set.of("revoke","archive").contains(command.action())
                && (command.reason() == null || command.reason().trim().length() < 3
                    || command.reason().trim().length() > 512))) {
            throw failure(
                "CROSS_SPACE_SYNC_COMMAND_INVALID", "Sync rule command is invalid"
            );
        }
    }

    private void validateExecution(ExecuteSyncCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedRuleVersion() < 1
            || !EXECUTION_DIRECTIONS.contains(command.direction())
            || !validRequestId(command.originId()) || !validRequestId(command.causationId())
            || command.chainDepth() < 0 || command.chainDepth() > MAX_CHAIN_DEPTH
            || command.expectedSourceVersion() < 0 || command.expectedTargetVersion() < 0) {
            throw failure(
                "CROSS_SPACE_SYNC_COMMAND_INVALID",
                "Sync execution command is invalid or exceeds loop bounds"
            );
        }
    }

    private void validateResolution(ResolveConflictCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedVersion() < 1
            || !Set.of("retry","source_wins","target_wins","skip",
                       "compensate","dead_letter").contains(command.resolution())
            || command.reason() == null || command.reason().trim().length() < 3
            || command.reason().trim().length() > 512) {
            throw failure(
                "CROSS_SPACE_SYNC_COMMAND_INVALID",
                "Sync conflict resolution is invalid"
            );
        }
    }

    private String resolveManagerParty(
        CurrentUser user, SyncRule rule, String requested
    ) {
        if ("source".equals(requested)) {
            access.requireManager(user, rule.sourceSpaceId());
            return "source";
        }
        if ("target".equals(requested)) {
            access.requireManager(user, rule.targetSpaceId());
            return "target";
        }
        if (manager(user, rule.sourceSpaceId())) return "source";
        if (manager(user, rule.targetSpaceId())) return "target";
        throw failure(
            "CROSS_SPACE_SYNC_FORBIDDEN", "Cross-space sync governance is forbidden"
        );
    }

    private void requireEitherManager(CurrentUser user, SyncRule rule) {
        if (!manager(user, rule.sourceSpaceId())
            && !manager(user, rule.targetSpaceId())) {
            throw failure(
                "CROSS_SPACE_SYNC_FORBIDDEN",
                "Cross-space sync governance is forbidden"
            );
        }
    }

    private boolean member(CurrentUser user, UUID spaceId) {
        try {
            access.requireVisible(user, spaceId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean manager(CurrentUser user, UUID spaceId) {
        try {
            access.requireManager(user, spaceId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private CurrentUser delegated(CurrentUser caller, UUID actorId) {
        return new CurrentUser(
            actorId, caller.workspaceId(), UUID.randomUUID(),
            "cross-space-sync", "Cross-space sync",
            Set.of(), Set.of()
        );
    }

    private boolean directionAllowed(String configured, String requested) {
        return "bidirectional".equals(configured) || configured.equals(requested);
    }

    private List<JsonNode> nodes(JsonNode value) {
        List<JsonNode> values = new ArrayList<>();
        value.forEach(values::add);
        return values;
    }

    private String mappingKey(JsonNode mapping) {
        if (mapping.has("sourceField")) {
            return mapping.path("sourceField").asText()
                + "->" + mapping.path("targetField").asText();
        }
        return mapping.path("sourceState").asText()
            + "->" + mapping.path("targetAction").asText();
    }

    private String commandId(SyncRun run, int index) {
        return "cross-sync:" + run.id() + ":" + index;
    }

    private String fingerprint(EndpointSnapshot value) {
        return hash(Map.of(
            "spaceId", value.spaceId(),
            "workItemId", value.workItemId(),
            "version", value.version(),
            "configHash", value.configHash()
        ));
    }

    private <T> List<T> bounded(List<T> values, int max) {
        return values.size() > max ? values.subList(0, max) : values;
    }

    private <T> Optional<T> replay(
        CurrentUser user, String operation, String requestId,
        String requestHash, Class<T> type
    ) {
        Optional<CommandReceipt> receipt = sync.findReceipt(
            user.workspaceId(), user.id(), operation, requestId
        );
        if (receipt.isEmpty()) return Optional.empty();
        if (!receipt.get().requestHash().equals(requestHash)) {
            throw failure(
                "CROSS_SPACE_REQUEST_CONFLICT",
                "Request id was already used for different input"
            );
        }
        try {
            return Optional.of(mapper.treeToValue(receipt.get().response(), type));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void complete(
        CurrentUser user, String operation, String requestId,
        String requestHash, Object response, UUID aggregateId
    ) {
        sync.saveReceipt(
            user.workspaceId(), user.id(), operation, requestId,
            requestHash, mapper.valueToTree(response)
        );
        audit.log(
            user, "project_cross_space.sync_" + operation,
            "project_cross_space_sync", aggregateId, Map.of("operation", operation)
        );
        outbox.append(
            user.workspaceId(), "project.cross-space.sync.changed",
            "project_cross_space_sync", aggregateId, user.id(),
            Map.of("operation", operation),
            "cross-space-sync:" + operation + ":" + requestId
        );
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(value)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String hashText(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean validRequestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }
}
