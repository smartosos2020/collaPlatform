package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.CopyCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.CreateCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.DeleteCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.PresentationConfig;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.RevokeShareCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedView;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedViewExecution;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.ShareCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.TransferCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.UpdateCommand;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeRequest;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewMode;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewRequest;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemSavedViewRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemSavedViewRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemSavedViewRepository.CommandStart;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemSavedViewService {
    private static final Set<String> SCOPES = Set.of("personal", "shared");
    private static final Set<String> MODES = Set.of("table", "list", "tree");

    private final WorkItemSavedViewRepository repository;
    private final WorkItemService workItems;
    private final WorkItemQueryService queries;
    private final WorkItemViewService views;
    private final WorkItemTreeViewService trees;
    private final ProjectSpaceRepository spaces;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemSavedViewService(
        WorkItemSavedViewRepository repository,
        WorkItemService workItems,
        WorkItemQueryService queries,
        WorkItemViewService views,
        WorkItemTreeViewService trees,
        ProjectSpaceRepository spaces,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.workItems = workItems;
        this.queries = queries;
        this.views = views;
        this.trees = trees;
        this.spaces = spaces;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public List<SavedView> list(CurrentUser user, UUID spaceId) {
        workItems.requireQueryScope(user, spaceId);
        return repository.listAccessible(user.workspaceId(), spaceId, user.id(), 100);
    }

    public SavedView get(CurrentUser user, UUID spaceId, UUID viewId) {
        workItems.requireQueryScope(user, spaceId);
        return repository.findAccessible(user.workspaceId(), spaceId, user.id(), viewId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Saved view is not available"));
    }

    @Transactional
    public SavedView create(CurrentUser user, UUID spaceId, CreateCommand command) {
        workItems.requireQueryScope(user, spaceId);
        if (command == null) throw failure("INVALID_SAVED_VIEW_COMMAND", "Create command is required");
        String requestId = requestId(command.requestId());
        String name = name(command.name());
        String description = description(command.description());
        String scope = scope(command.scope());
        Normalized normalized = normalize(user, spaceId, command.query(), command.presentation());
        UUID viewId = stableViewId(user, spaceId, "create", requestId);
        Map<String, Object> payload = Map.of(
            "name", name,
            "description", description,
            "scope", scope,
            "query", normalized.query(),
            "presentation", normalized.presentation()
        );
        return mutate(
            user, spaceId, viewId, "create", requestId, 0, payload,
            ignored -> repository.create(
                viewId, user.workspaceId(), spaceId, user.id(), scope, name, description,
                normalized.query(), normalized.presentation(), normalized.hash()
            )
        );
    }

    @Transactional
    public SavedView update(
        CurrentUser user,
        UUID spaceId,
        UUID viewId,
        UpdateCommand command
    ) {
        SavedView current = requireManage(user, spaceId, viewId);
        if (command == null) throw failure("INVALID_SAVED_VIEW_COMMAND", "Update command is required");
        String requestId = requestId(command.requestId());
        String name = name(command.name());
        String description = description(command.description());
        String scope = scope(command.scope());
        Normalized normalized = normalize(user, spaceId, command.query(), command.presentation());
        Map<String, Object> payload = Map.of(
            "viewId", viewId, "name", name, "description", description, "scope", scope,
            "query", normalized.query(), "presentation", normalized.presentation()
        );
        return mutate(
            user, spaceId, viewId, "update", requestId, command.expectedVersion(), payload,
            ignored -> repository.update(
                user.workspaceId(), spaceId, viewId, command.expectedVersion(),
                scope, name, description, normalized.query(), normalized.presentation(),
                normalized.hash(), user.id()
            )
        );
    }

    @Transactional
    public SavedView copy(
        CurrentUser user,
        UUID spaceId,
        UUID viewId,
        CopyCommand command
    ) {
        SavedView source = get(user, spaceId, viewId);
        if (command == null) throw failure("INVALID_SAVED_VIEW_COMMAND", "Copy command is required");
        String requestId = requestId(command.requestId());
        String name = name(command.name());
        UUID copyId = stableViewId(user, spaceId, "copy", requestId);
        return mutate(
            user, spaceId, copyId, "copy", requestId, 0,
            Map.of("sourceViewId", viewId, "name", name, "sourceHash", source.configHash()),
            ignored -> repository.create(
                copyId, user.workspaceId(), spaceId, user.id(), "personal", name,
                source.description(), source.query(), source.presentation(), source.configHash()
            )
        );
    }

    @Transactional
    public SavedView share(
        CurrentUser user,
        UUID spaceId,
        UUID viewId,
        ShareCommand command
    ) {
        requireManage(user, spaceId, viewId);
        if (command == null || !Set.of("use", "manage").contains(command.permission())) {
            throw failure("INVALID_SAVED_VIEW_SHARE", "Share permission is invalid");
        }
        requireMember(user, spaceId, command.subjectUserId());
        return mutate(
            user, spaceId, viewId, "share", requestId(command.requestId()),
            command.expectedVersion(),
            Map.of(
                "viewId", viewId,
                "subjectUserId", command.subjectUserId(),
                "permission", command.permission()
            ),
            ignored -> repository.share(
                user.workspaceId(), spaceId, viewId, command.expectedVersion(),
                command.subjectUserId(), command.permission(), user.id()
            )
        );
    }

    @Transactional
    public SavedView revoke(
        CurrentUser user,
        UUID spaceId,
        UUID viewId,
        RevokeShareCommand command
    ) {
        requireManage(user, spaceId, viewId);
        if (command == null) throw failure("INVALID_SAVED_VIEW_SHARE", "Revoke command is required");
        return mutate(
            user, spaceId, viewId, "revoke", requestId(command.requestId()),
            command.expectedVersion(),
            Map.of("viewId", viewId, "subjectUserId", command.subjectUserId()),
            ignored -> repository.revoke(
                user.workspaceId(), spaceId, viewId, command.expectedVersion(),
                command.subjectUserId(), user.id()
            )
        );
    }

    @Transactional
    public SavedView transfer(
        CurrentUser user,
        UUID spaceId,
        UUID viewId,
        TransferCommand command
    ) {
        SavedView current = get(user, spaceId, viewId);
        if (!current.ownerUserId().equals(user.id())) {
            throw failure("FORBIDDEN", "Only the saved view owner may transfer ownership");
        }
        if (command == null || command.newOwnerUserId().equals(user.id())) {
            throw failure("INVALID_SAVED_VIEW_TRANSFER", "New owner is invalid");
        }
        requireMember(user, spaceId, command.newOwnerUserId());
        return mutate(
            user, spaceId, viewId, "transfer", requestId(command.requestId()),
            command.expectedVersion(),
            Map.of("viewId", viewId, "newOwnerUserId", command.newOwnerUserId()),
            ignored -> repository.transfer(
                user.workspaceId(), spaceId, viewId, command.expectedVersion(),
                command.newOwnerUserId(), user.id()
            )
        );
    }

    @Transactional
    public SavedView delete(
        CurrentUser user,
        UUID spaceId,
        UUID viewId,
        DeleteCommand command
    ) {
        requireManage(user, spaceId, viewId);
        if (command == null) throw failure("INVALID_SAVED_VIEW_COMMAND", "Delete command is required");
        return mutate(
            user, spaceId, viewId, "delete", requestId(command.requestId()),
            command.expectedVersion(), Map.of("viewId", viewId),
            ignored -> repository.delete(
                user.workspaceId(), spaceId, viewId, command.expectedVersion(), user.id()
            )
        );
    }

    public SavedViewExecution execute(CurrentUser user, UUID spaceId, UUID viewId) {
        SavedView view = get(user, spaceId, viewId);
        Normalized normalized = normalize(user, spaceId, view.query(), view.presentation(), false);
        Object result = execute(user, spaceId, normalized.query(), normalized.presentation());
        return new SavedViewExecution(view, result);
    }

    private Object execute(
        CurrentUser user,
        UUID spaceId,
        QueryDefinition query,
        PresentationConfig presentation
    ) {
        return switch (presentation.mode()) {
            case "table", "list" -> views.render(
                user,
                spaceId,
                new ViewRequest(
                    1,
                    ViewMode.valueOf(presentation.mode()),
                    presentation.density(),
                    presentation.columns(),
                    query
                )
            );
            case "tree" -> trees.render(
                user,
                spaceId,
                new TreeRequest(
                    1,
                    presentation.relationKey(),
                    query,
                    null,
                    50,
                    presentation.maxDepth(),
                    null
                )
            );
            default -> throw failure("SAVED_VIEW_SCHEMA_UNSUPPORTED", "Saved view mode is unsupported");
        };
    }

    private Normalized normalize(
        CurrentUser user,
        UUID spaceId,
        QueryDefinition query,
        PresentationConfig presentation
    ) {
        return normalize(user, spaceId, query, presentation, true);
    }

    private Normalized normalize(
        CurrentUser user,
        UUID spaceId,
        QueryDefinition query,
        PresentationConfig presentation,
        boolean verifyPresentation
    ) {
        if (query == null
            || presentation == null
            || presentation.schemaVersion() != 1
            || !MODES.contains(presentation.mode())) {
            throw failure("SAVED_VIEW_SCHEMA_UNSUPPORTED", "Saved view schema version or mode is unsupported");
        }
        if (query.cursor() != null && !query.cursor().isBlank()) {
            throw failure("SAVED_VIEW_CURSOR_UNSUPPORTED", "Saved views cannot persist a page cursor");
        }
        QueryDefinition withoutCursor = new QueryDefinition(
            query.schemaVersion(),
            query.typeId(),
            query.filter(),
            query.sorts(),
            query.group(),
            query.select(),
            query.limit(),
            null
        );
        QueryDefinition normalized = queries.explain(user, spaceId, withoutCursor).normalized();
        if (verifyPresentation) {
            execute(user, spaceId, normalized, presentation);
        }
        String hash = sha256(objectMapper.valueToTree(Map.of(
            "schemaVersion", 1,
            "query", normalized,
            "presentation", presentation
        )).toString());
        return new Normalized(normalized, presentation, hash);
    }

    private SavedView requireManage(CurrentUser user, UUID spaceId, UUID viewId) {
        SavedView view = get(user, spaceId, viewId);
        if (!view.canManage()) {
            throw failure("FORBIDDEN", "Saved view management is unavailable");
        }
        return view;
    }

    private void requireMember(CurrentUser actor, UUID spaceId, UUID userId) {
        var target = spaces.findById(actor.workspaceId(), spaceId, userId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Saved view subject is not available"));
        if (!target.isMember() || "archived".equals(target.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Saved view subject is not available");
        }
    }

    private SavedView mutate(
        CurrentUser user,
        UUID spaceId,
        UUID viewId,
        String operation,
        String requestId,
        long expectedVersion,
        Object payload,
        Function<CommandReceipt, SavedView> action
    ) {
        String hash = sha256(objectMapper.valueToTree(payload).toString());
        repository.tryStartCommand(new CommandStart(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            viewId,
            operation,
            requestId,
            hash,
            expectedVersion,
            user.id()
        ));
        CommandReceipt receipt = repository.findCommand(
            user.workspaceId(), spaceId, operation, requestId
        ).orElseThrow(() -> failure("SAVED_VIEW_COMMAND_CONFLICT", "Command receipt is unavailable"));
        if (!receipt.requestHash().equals(hash)
            || receipt.expectedVersion() != expectedVersion
            || !receipt.actorId().equals(user.id())
            || (receipt.viewId() != null && !receipt.viewId().equals(viewId))) {
            throw failure("IDEMPOTENCY_KEY_REUSED", "Request ID was reused with different saved view input");
        }
        if ("completed".equals(receipt.status())) {
            return replay(receipt);
        }
        SavedView result = action.apply(receipt);
        repository.completeCommand(receipt.id(), result.id(), objectMapper.valueToTree(result));
        record(user, result, operation, requestId);
        return result;
    }

    private SavedView replay(CommandReceipt receipt) {
        if (receipt.response() == null) {
            throw failure("SAVED_VIEW_COMMAND_CONFLICT", "Completed command has no response");
        }
        try {
            return objectMapper.treeToValue(receipt.response(), SavedView.class);
        } catch (JsonProcessingException exception) {
            throw failure("SAVED_VIEW_STORAGE_INVALID", "Stored command response is invalid", exception);
        }
    }

    private void record(
        CurrentUser user,
        SavedView view,
        String operation,
        String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "spaceId", view.spaceId().toString(),
            "operation", operation,
            "scope", view.scope(),
            "aggregateVersion", view.aggregateVersion(),
            "versionNumber", view.versionNumber()
        );
        auditLog.log(user, "saved_view." + operation, "saved_view", view.id(), metadata);
        String eventId = UUID.nameUUIDFromBytes(
            (user.workspaceId() + ":" + requestId + ":" + operation)
                .getBytes(StandardCharsets.UTF_8)
        ).toString();
        outbox.append(
            user.workspaceId(),
            "saved_view.changed",
            "saved_view",
            view.id(),
            user.id(),
            metadata,
            "saved-view:" + eventId
        );
    }

    private static String requestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Caller-stable request ID is required");
        }
        return normalized;
    }

    private static String name(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 120) {
            throw failure("INVALID_SAVED_VIEW_NAME", "Saved view name must contain 1 to 120 characters");
        }
        return normalized;
    }

    private static String description(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 500) {
            throw failure("INVALID_SAVED_VIEW_DESCRIPTION", "Saved view description exceeds 500 characters");
        }
        return normalized;
    }

    private static String scope(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!SCOPES.contains(normalized)) {
            throw failure("INVALID_SAVED_VIEW_SCOPE", "Saved view scope is invalid");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static UUID stableViewId(
        CurrentUser user,
        UUID spaceId,
        String operation,
        String requestId
    ) {
        return UUID.nameUUIDFromBytes(
            (user.workspaceId() + ":" + spaceId + ":" + user.id() + ":" + operation + ":" + requestId)
                .getBytes(StandardCharsets.UTF_8)
        );
    }

    private record Normalized(
        QueryDefinition query,
        PresentationConfig presentation,
        String hash
    ) {
    }
}
