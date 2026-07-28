package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.CrossSpaceRelationModels.MAX_INTENTS;
import static com.colla.platform.modules.project.domain.CrossSpaceRelationModels.MAX_POLICIES;
import static com.colla.platform.modules.project.domain.CrossSpaceRelationModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand.CanonicalRelationReference;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand.CreateCommand;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand.WithdrawCommand;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.CrossSpaceGrant;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CreateLinkIntentCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CrossSpaceRelationPolicy;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.EndpointReference;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntent;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntentCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.RelationFoundation;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.RelationPolicyLifecycleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.SaveRelationPolicyCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.infrastructure.CrossSpaceRelationRepository;
import com.colla.platform.modules.project.infrastructure.CrossSpaceRelationRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
public class CrossSpaceRelationService {
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");
    private static final Pattern RELATION_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> DIRECTIONS = Set.of(
        "source_to_target", "target_to_source", "bidirectional"
    );

    private final CrossSpaceRelationRepository relations;
    private final CrossSpaceRelationCommand canonical;
    private final CrossSpaceGrantService grants;
    private final WorkItemRepository workItems;
    private final PublishedSnapshotAdapter snapshots;
    private final WorkItemRelationAccessDecisionService access;
    private final AuditLog audit;
    private final TransactionalOutbox outbox;
    private final ObjectMapper mapper;

    public CrossSpaceRelationService(
        CrossSpaceRelationRepository relations,
        CrossSpaceRelationCommand canonical,
        CrossSpaceGrantService grants,
        WorkItemRepository workItems,
        PublishedSnapshotAdapter snapshots,
        WorkItemRelationAccessDecisionService access,
        AuditLog audit,
        TransactionalOutbox outbox,
        ObjectMapper mapper
    ) {
        this.relations = relations;
        this.canonical = canonical;
        this.grants = grants;
        this.workItems = workItems;
        this.snapshots = snapshots;
        this.access = access;
        this.audit = audit;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    public RelationFoundation list(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        List<CrossSpaceRelationPolicy> policies = relations.listPolicies(
            user.workspaceId(), spaceId, MAX_POLICIES + 1
        );
        List<LinkIntent> intents = relations.listIntents(
            user.workspaceId(), spaceId, MAX_INTENTS + 1
        );
        return new RelationFoundation(
            SCHEMA_VERSION,
            DIRECTIONS.stream().sorted().toList(),
            policies.size() > MAX_POLICIES
                ? policies.subList(0, MAX_POLICIES) : policies,
            intents.size() > MAX_INTENTS ? intents.subList(0, MAX_INTENTS) : intents,
            policies.size() > MAX_POLICIES,
            intents.size() > MAX_INTENTS
        );
    }

    @Transactional
    public CrossSpaceRelationPolicy createPolicy(
        CurrentUser user, UUID spaceId, SaveRelationPolicyCommand command
    ) {
        validatePolicy(command);
        access.requireManager(user, spaceId);
        String operation = "policy_create";
        String requestHash = hash(command);
        Optional<CrossSpaceRelationPolicy> replay = replay(
            user, operation, command.requestId(), requestHash,
            CrossSpaceRelationPolicy.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        CrossSpaceGrant grant = grants.requireActiveGrant(user, command.grantId(), "relate");
        if (!grant.sourceSpaceId().equals(spaceId)) {
            throw failure("CROSS_SPACE_REFERENCE_FORBIDDEN", "Cross-space reference is forbidden");
        }
        requireScope(
            grant, command.direction(),
            command.sourceTypeId(), command.sourceVersionId(),
            command.targetTypeId(), command.targetVersionId()
        );
        var source = snapshots.requireComplete(
            user.workspaceId(), grant.sourceSpaceId(),
            command.sourceTypeId(), command.sourceVersionId()
        );
        var target = snapshots.requireComplete(
            user.workspaceId(), grant.targetSpaceId(),
            command.targetTypeId(), command.targetVersionId()
        );
        CrossSpaceRelationPolicy result;
        try {
            result = relations.createPolicy(
                user.workspaceId(), user.id(), grant.id(),
                grant.sourceSpaceId(), grant.targetSpaceId(),
                command.relationKey(), command.direction(),
                command.sourceTypeId(), command.sourceVersionId(), source.configHash(),
                command.targetTypeId(), command.targetVersionId(), target.configHash()
            );
        } catch (DataIntegrityViolationException exception) {
            throw failure(
                "CROSS_SPACE_RELATION_POLICY_CONFLICT",
                "An equivalent cross-space relation policy already exists",
                exception
            );
        }
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    @Transactional
    public CrossSpaceRelationPolicy policyLifecycle(
        CurrentUser user,
        UUID policyId,
        RelationPolicyLifecycleCommand command
    ) {
        validatePolicyLifecycle(command);
        CrossSpaceRelationPolicy policy = requirePolicy(user, policyId, true);
        String party = resolveManagerParty(user, policy, command.party());
        String operation = "policy_" + command.action();
        String requestHash = hash(command);
        Optional<CrossSpaceRelationPolicy> replay = replay(
            user, operation, command.requestId(), requestHash,
            CrossSpaceRelationPolicy.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        if (!Set.of("revoke", "archive").contains(command.action())) {
            grants.requireActiveGrant(user, policy.grantId(), "relate");
        }
        int updated = relations.transitionPolicy(
            user.workspaceId(), policy.id(), command.expectedVersion(),
            user.id(), command.action(), party
        );
        if (updated != 1) {
            throw failure(
                "CROSS_SPACE_RELATION_VERSION_CONFLICT",
                "Cross-space relation policy changed or transition is invalid"
            );
        }
        CrossSpaceRelationPolicy result = requirePolicy(user, policyId, false);
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    @Transactional
    public LinkIntent createIntent(
        CurrentUser user, UUID policyId, CreateLinkIntentCommand command
    ) {
        validateIntent(command);
        CrossSpaceRelationPolicy policy = requirePolicy(user, policyId, true);
        if (!"active".equals(policy.status())
            || policy.version() != command.expectedPolicyVersion()) {
            throw failure(
                "CROSS_SPACE_RELATION_VERSION_CONFLICT",
                "Cross-space relation policy is not active at the expected version"
            );
        }
        grants.requireActiveGrant(user, policy.grantId(), "relate");
        boolean reverse = "target_to_source".equals(policy.direction());
        UUID initiatingSpace = reverse ? policy.targetSpaceId() : policy.sourceSpaceId();
        access.requireWritable(user, initiatingSpace);
        WorkItem source = endpoint(
            user, policy.sourceSpaceId(), command.sourceWorkItemId(),
            policy.sourceTypeId(), policy.sourceVersionId(), policy.sourceConfigHash()
        );
        WorkItem target = endpoint(
            user, policy.targetSpaceId(), command.targetWorkItemId(),
            policy.targetTypeId(), policy.targetVersionId(), policy.targetConfigHash()
        );
        WorkItem initiatingItem = reverse ? target : source;
        access.requireAction(
            user, initiatingSpace, initiatingItem, "relate", policy.relationKey()
        );
        requireVersion(source, command.expectedSourceVersion());
        requireVersion(target, command.expectedTargetVersion());
        String operation = "intent_create";
        String requestHash = hash(command);
        Optional<LinkIntent> replay = replay(
            user, operation, command.requestId(), requestHash, LinkIntent.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        LinkIntent result;
        try {
            result = relations.createIntent(
                user.workspaceId(), user.id(), policy,
                source.id(), source.version(), target.id(), target.version()
            );
        } catch (DataIntegrityViolationException exception) {
            throw failure(
                "CROSS_SPACE_RELATION_INTENT_CONFLICT",
                "An intent already exists for these endpoints",
                exception
            );
        }
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    @Transactional
    public LinkIntent intentLifecycle(
        CurrentUser user, UUID intentId, LinkIntentCommand command
    ) {
        validateIntentLifecycle(command);
        LinkIntent intent = requireIntent(user, intentId, true);
        CrossSpaceRelationPolicy policy = requirePolicy(user, intent.policyId(), true);
        String operation = "intent_" + command.action();
        String requestHash = hash(command);
        Optional<LinkIntent> replay = replay(
            user, operation, command.requestId(), requestHash, LinkIntent.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        UUID relationId = null;
        String reasonHash = null;
        if ("accept".equals(command.action())) {
            if (!"active".equals(policy.status())
                || policy.version() != intent.policyVersion()) {
                throw failure(
                    "CROSS_SPACE_RELATION_POLICY_CHANGED",
                    "The confirmed relation policy is no longer current"
                );
            }
            grants.requireActiveGrant(user, policy.grantId(), "relate");
            boolean reverse = "target_to_source".equals(policy.direction());
            UUID acceptingSpace = reverse
                ? policy.sourceSpaceId() : policy.targetSpaceId();
            access.requireWritable(user, acceptingSpace);
            WorkItem source = endpoint(
                user, intent.sourceSpaceId(), intent.sourceWorkItemId(),
                policy.sourceTypeId(), policy.sourceVersionId(), policy.sourceConfigHash()
            );
            WorkItem target = endpoint(
                user, intent.targetSpaceId(), intent.targetWorkItemId(),
                policy.targetTypeId(), policy.targetVersionId(), policy.targetConfigHash()
            );
            WorkItem acceptingItem = reverse ? source : target;
            access.requireAction(
                user, acceptingSpace, acceptingItem, "accept_link", policy.relationKey()
            );
            WorkItem canonicalSource = reverse ? target : source;
            WorkItem canonicalTarget = reverse ? source : target;
            CanonicalRelationReference edge = canonical.create(new CreateCommand(
                user.workspaceId(), user.id(),
                canonicalSource.spaceId(), canonicalSource.id(), canonicalSource.version(),
                canonicalTarget.spaceId(), canonicalTarget.id(), canonicalTarget.version(),
                policy.relationKey(), policy.direction(),
                canonicalSource.typeDefinitionId(), canonicalSource.typeVersionId(),
                canonicalSource.configHash(),
                canonicalTarget.typeDefinitionId(), canonicalTarget.typeVersionId(),
                canonicalTarget.configHash(),
                policy.id(), policy.version()
            ));
            relationId = edge.relationId();
        } else {
            UUID requiredSpace = "cancel".equals(command.action())
                ? intent.sourceSpaceId() : intent.targetSpaceId();
            access.requireWritable(user, requiredSpace);
            reasonHash = hashText(command.reason());
        }
        int updated = relations.completeIntent(
            user.workspaceId(), intent.id(), command.expectedVersion(),
            user.id(), command.action(), relationId, reasonHash
        );
        if (updated != 1) {
            throw failure(
                "CROSS_SPACE_RELATION_VERSION_CONFLICT",
                "Cross-space link intent changed or is no longer pending"
            );
        }
        LinkIntent result = requireIntent(user, intentId, false);
        complete(user, operation, command.requestId(), requestHash, result, result.id());
        return result;
    }

    public EndpointReference endpointReference(
        CurrentUser user, UUID policyId, UUID workItemId
    ) {
        CrossSpaceRelationPolicy policy = requirePolicy(user, policyId, false);
        grants.requireActiveGrant(user, policy.grantId(), "reference");
        boolean sourceParty = member(user, policy.sourceSpaceId());
        boolean targetParty = member(user, policy.targetSpaceId());
        if (!sourceParty && !targetParty) {
            throw failure("CROSS_SPACE_REFERENCE_FORBIDDEN", "Cross-space reference is forbidden");
        }
        UUID endpointSpace = sourceParty ? policy.targetSpaceId() : policy.sourceSpaceId();
        UUID typeId = sourceParty ? policy.targetTypeId() : policy.sourceTypeId();
        UUID versionId = sourceParty ? policy.targetVersionId() : policy.sourceVersionId();
        String configHash = sourceParty
            ? policy.targetConfigHash() : policy.sourceConfigHash();
        WorkItem item = endpoint(
            user, endpointSpace, workItemId, typeId, versionId, configHash
        );
        return new EndpointReference(
            endpointSpace,
            item.id(),
            "ref-" + item.id().toString().substring(0, 8),
            item.typeKey(),
            "active".equals(item.status()),
            item.version()
        );
    }

    public CanonicalRelationReference relation(
        CurrentUser user, UUID policyId, UUID relationId
    ) {
        CrossSpaceRelationPolicy policy = requirePolicy(user, policyId, false);
        CanonicalRelationReference edge = canonical.find(user.workspaceId(), relationId)
            .filter(value -> value.policyId().equals(policy.id()))
            .orElseThrow(() -> failure(
                "CROSS_SPACE_REFERENCE_FORBIDDEN",
                "Cross-space relation reference is forbidden"
            ));
        if (!member(user, edge.sourceSpaceId()) && !member(user, edge.targetSpaceId())) {
            throw failure("CROSS_SPACE_REFERENCE_FORBIDDEN", "Cross-space reference is forbidden");
        }
        return edge;
    }

    @Transactional
    public CanonicalRelationReference withdraw(
        CurrentUser user,
        UUID policyId,
        UUID relationId,
        long expectedVersion,
        String reason,
        String requestId
    ) {
        if (!validRequestId(requestId) || reason == null
            || reason.trim().length() < 3 || reason.trim().length() > 512) {
            throw failure(
                "CROSS_SPACE_RELATION_COMMAND_INVALID",
                "Cross-space relation withdrawal is invalid"
            );
        }
        CrossSpaceRelationPolicy policy = requirePolicy(user, policyId, true);
        requireEitherManager(user, policy);
        CanonicalRelationReference current = relation(user, policyId, relationId);
        String operation = "relation_withdraw";
        Map<String, Object> request = Map.of(
            "relationId", relationId,
            "expectedVersion", expectedVersion,
            "reason", reason.trim()
        );
        String requestHash = hash(request);
        Optional<CanonicalRelationReference> replay = replay(
            user, operation, requestId, requestHash, CanonicalRelationReference.class
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        CanonicalRelationReference result = canonical.withdraw(new WithdrawCommand(
            user.workspaceId(), user.id(), current.relationId(),
            expectedVersion, hashText(reason.trim())
        ));
        complete(user, operation, requestId, requestHash, result, result.relationId());
        return result;
    }

    private CrossSpaceRelationPolicy requirePolicy(
        CurrentUser user, UUID policyId, boolean lock
    ) {
        CrossSpaceRelationPolicy policy = relations.findPolicy(
            user.workspaceId(), policyId, lock
        ).orElseThrow(() -> failure(
            "CROSS_SPACE_RELATION_NOT_FOUND",
            "Cross-space relation policy is not available"
        ));
        if (!member(user, policy.sourceSpaceId()) && !member(user, policy.targetSpaceId())) {
            throw failure(
                "CROSS_SPACE_RELATION_NOT_FOUND",
                "Cross-space relation policy is not available"
            );
        }
        return policy;
    }

    private LinkIntent requireIntent(CurrentUser user, UUID intentId, boolean lock) {
        LinkIntent intent = relations.findIntent(user.workspaceId(), intentId, lock)
            .orElseThrow(() -> failure(
                "CROSS_SPACE_RELATION_NOT_FOUND",
                "Cross-space link intent is not available"
            ));
        if (!member(user, intent.sourceSpaceId()) && !member(user, intent.targetSpaceId())) {
            throw failure(
                "CROSS_SPACE_RELATION_NOT_FOUND",
                "Cross-space link intent is not available"
            );
        }
        return intent;
    }

    private WorkItem endpoint(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID typeId,
        UUID versionId,
        String configHash
    ) {
        WorkItem item = workItems.find(user.workspaceId(), spaceId, workItemId)
            .orElseThrow(() -> failure(
                "CROSS_SPACE_REFERENCE_FORBIDDEN",
                "Cross-space endpoint reference is forbidden"
            ));
        if (!item.typeDefinitionId().equals(typeId)
            || !item.typeVersionId().equals(versionId)
            || !item.configHash().equals(configHash)) {
            throw failure(
                "CROSS_SPACE_REFERENCE_FORBIDDEN",
                "Cross-space endpoint reference is forbidden"
            );
        }
        return item;
    }

    private void requireVersion(WorkItem item, long expected) {
        if (item.version() != expected || !"active".equals(item.status())) {
            throw failure(
                "CROSS_SPACE_RELATION_ENDPOINT_VERSION_CONFLICT",
                "Cross-space endpoint changed or is not active"
            );
        }
    }

    private void validatePolicy(SaveRelationPolicyCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.grantId() == null
            || command.relationKey() == null
            || !RELATION_KEY.matcher(command.relationKey()).matches()
            || !DIRECTIONS.contains(command.direction())
            || command.sourceTypeId() == null || command.sourceVersionId() == null
            || command.targetTypeId() == null || command.targetVersionId() == null) {
            throw failure(
                "CROSS_SPACE_RELATION_POLICY_INVALID",
                "Cross-space relation policy is invalid"
            );
        }
    }

    private void validatePolicyLifecycle(RelationPolicyLifecycleCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedVersion() < 1
            || !Set.of("request", "confirm", "pause", "resume", "revoke", "archive")
                .contains(command.action())
            || ("confirm".equals(command.action())
                && !Set.of("source", "target").contains(command.party()))
            || (Set.of("revoke", "archive").contains(command.action())
                && (command.reason() == null || command.reason().trim().length() < 3
                    || command.reason().trim().length() > 512))) {
            throw failure(
                "CROSS_SPACE_RELATION_COMMAND_INVALID",
                "Cross-space relation policy command is invalid"
            );
        }
    }

    private void validateIntent(CreateLinkIntentCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId())
            || command.expectedPolicyVersion() < 1
            || command.sourceWorkItemId() == null || command.targetWorkItemId() == null
            || command.expectedSourceVersion() < 0
            || command.expectedTargetVersion() < 0) {
            throw failure(
                "CROSS_SPACE_RELATION_INTENT_INVALID",
                "Cross-space link intent is invalid"
            );
        }
    }

    private void validateIntentLifecycle(LinkIntentCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedVersion() < 1
            || !Set.of("accept", "reject", "cancel").contains(command.action())
            || (!"accept".equals(command.action())
                && (command.reason() == null || command.reason().trim().length() < 3
                    || command.reason().trim().length() > 512))) {
            throw failure(
                "CROSS_SPACE_RELATION_COMMAND_INVALID",
                "Cross-space link intent command is invalid"
            );
        }
    }

    private void requireScope(
        CrossSpaceGrant grant,
        String direction,
        UUID sourceTypeId,
        UUID sourceVersionId,
        UUID targetTypeId,
        UUID targetVersionId
    ) {
        String grantDirection = grant.scope().path("direction").asText();
        if (!"bidirectional".equals(grantDirection)
            && !grantDirection.equals(direction)) {
            throw failure(
                "CROSS_SPACE_REFERENCE_FORBIDDEN",
                "Cross-space relation direction is outside the grant"
            );
        }
        boolean match = false;
        for (JsonNode scope : grant.scope().path("typeScopes")) {
            if (sourceTypeId.toString().equals(scope.path("sourceTypeId").asText())
                && sourceVersionId.toString().equals(scope.path("sourceVersionId").asText())
                && targetTypeId.toString().equals(scope.path("targetTypeId").asText())
                && targetVersionId.toString().equals(scope.path("targetVersionId").asText())) {
                match = true;
                break;
            }
        }
        if (!match) {
            throw failure(
                "CROSS_SPACE_REFERENCE_FORBIDDEN",
                "Cross-space relation type pair is outside the grant"
            );
        }
    }

    private String resolveManagerParty(
        CurrentUser user, CrossSpaceRelationPolicy policy, String requested
    ) {
        if ("source".equals(requested)) {
            access.requireManager(user, policy.sourceSpaceId());
            return "source";
        }
        if ("target".equals(requested)) {
            access.requireManager(user, policy.targetSpaceId());
            return "target";
        }
        if (manager(user, policy.sourceSpaceId())) {
            return "source";
        }
        if (manager(user, policy.targetSpaceId())) {
            return "target";
        }
        throw failure(
            "CROSS_SPACE_RELATION_FORBIDDEN",
            "Cross-space relation governance is forbidden"
        );
    }

    private void requireEitherManager(CurrentUser user, CrossSpaceRelationPolicy policy) {
        if (!manager(user, policy.sourceSpaceId())
            && !manager(user, policy.targetSpaceId())) {
            throw failure(
                "CROSS_SPACE_RELATION_FORBIDDEN",
                "Cross-space relation governance is forbidden"
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

    private <T> Optional<T> replay(
        CurrentUser user,
        String operation,
        String requestId,
        String requestHash,
        Class<T> type
    ) {
        Optional<CommandReceipt> receipt = relations.findReceipt(
            user.workspaceId(), user.id(), operation, requestId
        );
        if (receipt.isEmpty()) {
            return Optional.empty();
        }
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
        CurrentUser user,
        String operation,
        String requestId,
        String requestHash,
        Object result,
        UUID aggregateId
    ) {
        relations.saveReceipt(
            user.workspaceId(), user.id(), operation, requestId,
            requestHash, mapper.valueToTree(result)
        );
        audit.log(
            user, "project_cross_space.relation_" + operation,
            "project_cross_space_relation", aggregateId,
            Map.of("operation", operation)
        );
        outbox.append(
            user.workspaceId(), "project.cross-space.relation.changed",
            "project_cross_space_relation", aggregateId, user.id(),
            Map.of("operation", operation),
            "cross-space-relation:" + operation + ":" + requestId
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
