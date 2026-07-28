package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.CrossSpaceGrantModels.MAX_GRANTS;
import static com.colla.platform.modules.project.domain.CrossSpaceGrantModels.MAX_INSTANCE_SCOPES;
import static com.colla.platform.modules.project.domain.CrossSpaceGrantModels.MAX_TYPE_SCOPES;
import static com.colla.platform.modules.project.domain.CrossSpaceGrantModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.identity.contract.IdentityGovernance;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.CrossSpaceGrant;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantFoundation;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantHistory;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantLifecycleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.SaveGrantCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.CrossSpaceGrantRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrossSpaceGrantService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Set<String> DIRECTIONS = Set.of(
        "source_to_target", "target_to_source", "bidirectional"
    );
    private static final Set<String> OPERATIONS = Set.of(
        "reference", "relate", "read_fields", "sync_fields", "sync_state"
    );

    private final CrossSpaceGrantRepository grants;
    private final ProjectSpaceRepository spaces;
    private final IdentityGovernance identities;
    private final PublishedSnapshotAdapter snapshots;
    private final AuditLog audit;
    private final TransactionalOutbox outbox;
    private final ObjectMapper mapper;

    public CrossSpaceGrantService(
        CrossSpaceGrantRepository grants,
        ProjectSpaceRepository spaces,
        IdentityGovernance identities,
        PublishedSnapshotAdapter snapshots,
        AuditLog audit,
        TransactionalOutbox outbox,
        ObjectMapper mapper
    ) {
        this.grants = grants;
        this.spaces = spaces;
        this.identities = identities;
        this.snapshots = snapshots;
        this.audit = audit;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    public GrantFoundation list(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        List<CrossSpaceGrant> values = grants.listVisible(
            user.workspaceId(), user.id(), spaceId, MAX_GRANTS + 1
        );
        boolean truncated = values.size() > MAX_GRANTS;
        return new GrantFoundation(
            SCHEMA_VERSION,
            DIRECTIONS.stream().sorted().toList(),
            OPERATIONS.stream().sorted().toList(),
            truncated ? values.subList(0, MAX_GRANTS) : values,
            truncated
        );
    }

    public GrantHistory history(CurrentUser user, UUID grantId) {
        CrossSpaceGrant grant = requireVisibleGrant(user, grantId);
        return new GrantHistory(
            safeGrant(user, grant),
            grants.listVersions(user.workspaceId(), grantId, 50)
        );
    }

    @Transactional
    public CrossSpaceGrant save(
        CurrentUser user, UUID sourceSpaceId, SaveGrantCommand command
    ) {
        requireManager(user, sourceSpaceId);
        validateSave(command, sourceSpaceId);
        String operation = command.grantId() == null ? "create" : "revise";
        String requestHash = hash(command);
        Optional<CrossSpaceGrantRepository.CommandReceipt> replay = grants.findReceipt(
            user.workspaceId(), user.id(), operation, command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().response(), CrossSpaceGrant.class);
        }
        validateScope(user, sourceSpaceId, command.targetSpaceId(), command.scope());
        CrossSpaceGrant result;
        if (command.grantId() == null) {
            result = grants.create(
                user.workspaceId(), sourceSpaceId, command.targetSpaceId(), user.id(),
                command.name().trim(), command.scope(), hash(command.scope())
            );
        } else {
            CrossSpaceGrant existing = requireVisibleGrant(user, command.grantId());
            if (!existing.sourceSpaceId().equals(sourceSpaceId)
                || !existing.targetSpaceId().equals(command.targetSpaceId())) {
                throw failure("CROSS_SPACE_GRANT_BOUNDARY_IMMUTABLE", "Grant spaces cannot change");
            }
            result = grants.revise(
                user.workspaceId(), command.grantId(), user.id(), command.expectedVersion(),
                command.name().trim(), command.scope(), hash(command.scope())
            );
        }
        complete(user, operation, command.requestId(), requestHash, result);
        return result;
    }

    @Transactional
    public CrossSpaceGrant lifecycle(
        CurrentUser user, UUID grantId, GrantLifecycleCommand command
    ) {
        validateLifecycle(command);
        CrossSpaceGrant grant = requireVisibleGrant(user, grantId);
        String party = resolveParty(user, grant, command.party());
        String operation = command.action();
        String requestHash = hash(command);
        Optional<CrossSpaceGrantRepository.CommandReceipt> replay = grants.findReceipt(
            user.workspaceId(), user.id(), operation, command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().response(), CrossSpaceGrant.class);
        }
        if ("resume".equals(operation) && !confirmationsCurrent(user, grant)) {
            throw failure("CROSS_SPACE_GRANT_REAUTHORIZE_REQUIRED", "Both spaces must confirm again");
        }
        CrossSpaceGrant result = grants.transition(
            user.workspaceId(), grantId, user.id(), command.expectedVersion(),
            command.action(), party
        );
        complete(user, operation, command.requestId(), requestHash, result);
        return result;
    }

    public CrossSpaceGrant requireAuthorized(
        CurrentUser user, UUID grantId, String operation
    ) {
        CrossSpaceGrant grant = requireVisibleGrant(user, grantId);
        requireActiveGrant(user, grant, operation);
        requireVisible(user, grant.sourceSpaceId());
        requireVisible(user, grant.targetSpaceId());
        return grant;
    }

    /**
     * Public S18 authorization contract for dual-party commands. The caller's endpoint
     * permission is deliberately checked by the consuming service; grant validity itself
     * must not require one actor to become a member of both spaces.
     */
    public CrossSpaceGrant requireActiveGrant(
        CurrentUser user, UUID grantId, String operation
    ) {
        CrossSpaceGrant grant = grants.find(user.workspaceId(), grantId)
            .orElseThrow(() -> failure(
                "CROSS_SPACE_REFERENCE_FORBIDDEN",
                "Cross-space reference is forbidden"
            ));
        requireActiveGrant(user, grant, operation);
        return grant;
    }

    private void requireActiveGrant(
        CurrentUser user, CrossSpaceGrant grant, String operation
    ) {
        if (!OPERATIONS.contains(operation)
            || !grants.isCurrentlyAuthorized(user.workspaceId(), grant.id())
            || !confirmationsCurrent(user, grant)
            || !containsOperation(grant.scope(), operation)) {
            throw failure("CROSS_SPACE_REFERENCE_FORBIDDEN", "Cross-space reference is forbidden");
        }
    }

    private void validateSave(SaveGrantCommand command, UUID sourceSpaceId) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedVersion() < 0
            || command.targetSpaceId() == null || command.targetSpaceId().equals(sourceSpaceId)
            || command.name() == null || command.name().trim().length() < 2
            || command.name().trim().length() > 160 || command.scope() == null
            || !command.scope().isObject()
            || (command.grantId() == null && command.expectedVersion() != 0)
            || (command.grantId() != null && command.expectedVersion() == 0)) {
            throw failure("CROSS_SPACE_GRANT_INVALID", "Cross-space grant is invalid");
        }
    }

    private void validateScope(
        CurrentUser user, UUID sourceSpaceId, UUID targetSpaceId, JsonNode scope
    ) {
        if (scope.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
            || !DIRECTIONS.contains(scope.path("direction").asText())
            || !scope.path("operations").isArray()
            || scope.path("operations").isEmpty()
            || scope.path("operations").size() > OPERATIONS.size()
            || !scope.path("typeScopes").isArray()
            || scope.path("typeScopes").isEmpty()
            || scope.path("typeScopes").size() > MAX_TYPE_SCOPES
            || (scope.has("instanceScopes")
                && (!scope.path("instanceScopes").isArray()
                    || scope.path("instanceScopes").size() > MAX_INSTANCE_SCOPES))) {
            throw failure("CROSS_SPACE_SCOPE_INVALID", "Grant scope is invalid or exceeds its bound");
        }
        Set<String> operations = new HashSet<>();
        scope.path("operations").forEach(value -> operations.add(value.asText()));
        if (!OPERATIONS.containsAll(operations)
            || operations.size() != scope.path("operations").size()) {
            throw failure("CROSS_SPACE_SCOPE_INVALID", "Grant operations are invalid");
        }
        for (JsonNode type : scope.path("typeScopes")) {
            UUID sourceType = uuid(type, "sourceTypeId");
            UUID sourceVersion = uuid(type, "sourceVersionId");
            UUID targetType = uuid(type, "targetTypeId");
            UUID targetVersion = uuid(type, "targetVersionId");
            snapshots.requireComplete(user.workspaceId(), sourceSpaceId, sourceType, sourceVersion);
            snapshots.requireComplete(user.workspaceId(), targetSpaceId, targetType, targetVersion);
        }
    }

    private void validateLifecycle(GrantLifecycleCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !validRequestId(command.requestId()) || command.expectedVersion() < 1
            || !Set.of("request", "confirm", "pause", "resume", "revoke", "archive")
                .contains(command.action())
            || ("confirm".equals(command.action())
                && !Set.of("source", "target").contains(command.party()))
            || (Set.of("pause", "revoke", "archive").contains(command.action())
                && (command.reason() == null || command.reason().trim().length() < 3
                    || command.reason().trim().length() > 512))) {
            throw failure("CROSS_SPACE_GRANT_COMMAND_INVALID", "Grant command is invalid");
        }
    }

    private String resolveParty(CurrentUser user, CrossSpaceGrant grant, String requested) {
        if (requested != null && Set.of("source", "target").contains(requested)) {
            UUID spaceId = "target".equals(requested)
                ? grant.targetSpaceId() : grant.sourceSpaceId();
            requireManager(user, spaceId);
            return requested;
        }
        if (manager(user, grant.sourceSpaceId())) {
            return "source";
        }
        if (manager(user, grant.targetSpaceId())) {
            return "target";
        }
        throw failure("CROSS_SPACE_GRANT_FORBIDDEN", "Grant is not available");
    }

    private CrossSpaceGrant requireVisibleGrant(CurrentUser user, UUID grantId) {
        CrossSpaceGrant grant = grants.find(user.workspaceId(), grantId)
            .orElseThrow(() -> failure("CROSS_SPACE_GRANT_NOT_FOUND", "Grant is not available"));
        if (!member(user, grant.sourceSpaceId()) && !member(user, grant.targetSpaceId())) {
            throw failure("CROSS_SPACE_GRANT_NOT_FOUND", "Grant is not available");
        }
        return grant;
    }

    private CrossSpaceGrant safeGrant(CurrentUser user, CrossSpaceGrant grant) {
        return grant;
    }

    private void complete(
        CurrentUser user, String operation, String requestId,
        String requestHash, CrossSpaceGrant result
    ) {
        JsonNode response = mapper.valueToTree(result);
        grants.saveReceipt(
            user.workspaceId(), user.id(), operation, requestId,
            requestHash, result.id(), response
        );
        audit.log(
            user, "project_cross_space.grant_" + operation,
            "project_cross_space_grant", result.id(),
            Map.of(
                "sourceSpaceId", result.sourceSpaceId(),
                "targetSpaceId", result.targetSpaceId(),
                "version", result.currentVersion(),
                "status", result.status()
            )
        );
        outbox.append(
            user.workspaceId(), "project.cross-space.grant.changed",
            "project_cross_space_grant", result.id(), user.id(),
            Map.of(
                "change", operation,
                "sourceSpaceId", result.sourceSpaceId().toString(),
                "targetSpaceId", result.targetSpaceId().toString(),
                "version", result.currentVersion(),
                "status", result.status()
            ),
            "cross-space-grant:" + operation + ":" + requestId
        );
    }

    private ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaces.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("CROSS_SPACE_NOT_FOUND", "Space is not available"));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("CROSS_SPACE_NOT_FOUND", "Space is not available");
        }
        return space;
    }

    private void requireManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if (!space.canManage() || !"active".equals(space.status())) {
            throw failure("CROSS_SPACE_GRANT_FORBIDDEN", "Grant management is forbidden");
        }
    }

    private boolean member(CurrentUser user, UUID spaceId) {
        return spaces.findActiveRole(user.workspaceId(), spaceId, user.id()).isPresent();
    }

    private boolean manager(CurrentUser user, UUID spaceId) {
        return spaces.findActiveRole(user.workspaceId(), spaceId, user.id())
            .filter(role -> "owner".equals(role) || "admin".equals(role))
            .isPresent();
    }

    private boolean confirmationsCurrent(CurrentUser user, CrossSpaceGrant grant) {
        return grant.sourceConfirmedBy() != null
            && grant.targetConfirmedBy() != null
            && identities.isActive(
                user.workspaceId(), IdentityGovernance.Resource.MEMBER,
                grant.sourceConfirmedBy()
            )
            && identities.isActive(
                user.workspaceId(), IdentityGovernance.Resource.MEMBER,
                grant.targetConfirmedBy()
            );
    }

    private boolean containsOperation(JsonNode scope, String operation) {
        for (JsonNode value : scope.path("operations")) {
            if (operation.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asText());
        } catch (IllegalArgumentException exception) {
            throw failure("CROSS_SPACE_SCOPE_INVALID", "Type scope identity is invalid");
        }
    }

    private boolean validRequestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private String hash(Object value) {
        try {
            byte[] payload = mapper.writeValueAsBytes(value);
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload)
            );
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void requireHash(
        CrossSpaceGrantRepository.CommandReceipt receipt, String requestHash
    ) {
        if (!receipt.requestHash().equals(requestHash)) {
            throw failure(
                "CROSS_SPACE_REQUEST_CONFLICT",
                "Request id was already used for different input"
            );
        }
    }

    private <T> T read(JsonNode value, Class<T> type) {
        try {
            return mapper.treeToValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
