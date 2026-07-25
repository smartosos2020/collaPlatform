package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.application.WorkItemLayoutCanonicalizer.CanonicalLayout;
import com.colla.platform.modules.project.application.WorkItemLayoutGraphCommandHandler.NodeCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutAggregate;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDefinition;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutKind;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutCommandRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutCommandRepository.CommandReceipt;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutCommandRepository.CommandStart;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository.LayoutDefinitionInsert;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemLayoutConfigurationService {
    private final WorkItemLayoutRepository layoutRepository;
    private final WorkItemLayoutCommandRepository commandRepository;
    private final WorkItemLayoutCanonicalizer canonicalizer;
    private final WorkItemLayoutFieldReferenceValidator fieldReferenceValidator;
    private final WorkItemLayoutActionPolicy actionPolicy;
    private final WorkItemLayoutGraphCommandHandler graphCommandHandler;
    private final WorkItemTypeDefinitionService typeService;
    private final WorkItemTypeConfigCanonicalizer hashCanonicalizer;
    private final ProjectSpaceRepository spaceRepository;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemLayoutConfigurationService(
        WorkItemLayoutRepository layoutRepository,
        WorkItemLayoutCommandRepository commandRepository,
        WorkItemLayoutCanonicalizer canonicalizer,
        WorkItemLayoutFieldReferenceValidator fieldReferenceValidator,
        WorkItemLayoutActionPolicy actionPolicy,
        WorkItemLayoutGraphCommandHandler graphCommandHandler,
        WorkItemTypeDefinitionService typeService,
        WorkItemTypeConfigCanonicalizer hashCanonicalizer,
        ProjectSpaceRepository spaceRepository,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.layoutRepository = layoutRepository;
        this.commandRepository = commandRepository;
        this.canonicalizer = canonicalizer;
        this.fieldReferenceValidator = fieldReferenceValidator;
        this.actionPolicy = actionPolicy;
        this.graphCommandHandler = graphCommandHandler;
        this.typeService = typeService;
        this.hashCanonicalizer = hashCanonicalizer;
        this.spaceRepository = spaceRepository;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public LayoutAggregate get(CurrentUser user, UUID spaceId, UUID typeId, String layoutKind) {
        Context context = requireContext(user, spaceId, typeId, false);
        String kind = LayoutKind.parse(layoutKind).name();
        LayoutDefinition definition = layoutRepository.findByKind(
            user.workspaceId(), spaceId, typeId, kind
        ).orElseThrow(() -> failure("LAYOUT_NOT_FOUND", "Work item layout is not available"));
        return aggregate(context, definition);
    }

    @Transactional
    public LayoutAggregate save(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String layoutKind,
        List<LayoutNode> nodes,
        List<FieldAccessPolicy> policies,
        long expectedAggregateVersion,
        String requestId
    ) {
        if (expectedAggregateVersion < 0) {
            throw failure("INVALID_LAYOUT_VERSION", "Layout aggregate version must be non-negative");
        }
        Context context = requireContext(user, spaceId, typeId, true);
        CanonicalLayout canonical = canonicalizer.canonicalize(layoutKind, nodes, policies);
        fieldReferenceValidator.validateForSave(
            user.workspaceId(), spaceId, typeId, canonical.nodes(), canonical.policies()
        );
        Command command = begin(
            user,
            spaceId,
            typeId,
            canonical.layoutKind(),
            canonical,
            expectedAggregateVersion,
            requestId
        );
        return persist(
            user, context, spaceId, typeId, canonical, expectedAggregateVersion, command
        );
    }

    private LayoutAggregate persist(
        CurrentUser user,
        Context context,
        UUID spaceId,
        UUID typeId,
        CanonicalLayout canonical,
        long expectedAggregateVersion,
        Command command
    ) {
        if (command.replay()) {
            return replay(user, context, spaceId, typeId, command.receipt());
        }

        try {
            LayoutDefinition definition = layoutRepository.findByKind(
                user.workspaceId(), spaceId, typeId, canonical.layoutKind()
            ).orElse(null);
            UUID layoutId;
            if (definition == null) {
                if (expectedAggregateVersion != 0) {
                    throw failure("LAYOUT_VERSION_CONFLICT", "Layout aggregate version changed");
                }
                layoutId = UUID.randomUUID();
                layoutRepository.insertLayout(new LayoutDefinitionInsert(
                    layoutId,
                    user.workspaceId(),
                    spaceId,
                    typeId,
                    canonical.layoutKind(),
                    canonical.hash(),
                    user.id()
                ));
            } else {
                layoutId = definition.id();
                if (layoutRepository.updateLayout(
                    user.workspaceId(),
                    spaceId,
                    typeId,
                    layoutId,
                    canonical.hash(),
                    user.id(),
                    expectedAggregateVersion
                ) != 1) {
                    throw failure("LAYOUT_VERSION_CONFLICT", "Layout aggregate version changed");
                }
            }
            layoutRepository.replaceNodes(
                user.workspaceId(), spaceId, typeId, layoutId, canonical.nodes(), user.id()
            );
            layoutRepository.replacePolicies(
                user.workspaceId(), spaceId, typeId, layoutId, canonical.policies(), user.id()
            );
            LayoutDefinition saved = layoutRepository.findById(
                user.workspaceId(), spaceId, typeId, layoutId
            ).orElseThrow(() -> failure("LAYOUT_NOT_FOUND", "Saved work item layout is unavailable"));
            recordChange(
                user,
                saved,
                canonical.nodes().size(),
                canonical.policies().size(),
                command.requestId()
            );
            commandRepository.complete(command.receipt().id(), layoutId);
            return aggregate(context, saved);
        } catch (DataIntegrityViolationException exception) {
            throw failure("INVALID_LAYOUT_GRAPH", "Layout graph violates its persistence contract", exception);
        }
    }

    @Transactional
    public LayoutAggregate applyNodeCommand(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String layoutKind,
        NodeCommand command,
        String requestId
    ) {
        if (command == null || command.aggregateVersion() < 0) {
            throw failure("INVALID_LAYOUT_COMMAND", "Layout node command and aggregate version are required");
        }
        Context context = requireContext(user, spaceId, typeId, true);
        String kind = LayoutKind.parse(layoutKind).name();
        Command lifecycle = beginNodeCommand(user, spaceId, typeId, kind, command, requestId);
        if (lifecycle.replay()) {
            return replay(user, context, spaceId, typeId, lifecycle.receipt());
        }
        LayoutDefinition definition = layoutRepository.findByKind(
            user.workspaceId(), spaceId, typeId, kind
        ).orElseThrow(() -> failure("LAYOUT_NOT_FOUND", "Work item layout is not available"));
        LayoutAggregate current = aggregate(context, definition);
        if (current.definition().aggregateVersion() != command.aggregateVersion()) {
            throw failure("LAYOUT_VERSION_CONFLICT", "Layout aggregate version changed");
        }
        var changed = graphCommandHandler.apply(current.nodes(), current.policies(), command);
        CanonicalLayout canonical = canonicalizer.canonicalize(kind, changed.nodes(), changed.policies());
        fieldReferenceValidator.validateForSave(
            user.workspaceId(), spaceId, typeId, canonical.nodes(), canonical.policies()
        );
        return persist(
            user, context, spaceId, typeId, canonical, command.aggregateVersion(), lifecycle
        );
    }

    private Command beginNodeCommand(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String layoutKind,
        NodeCommand nodeCommand,
        String requestId
    ) {
        String normalizedRequestId = normalizeRequestId(requestId);
        String operation = "node:" + layoutKind + ":" + nodeCommand.operation();
        String requestHash = hashCanonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", user.id().toString(),
            "operation", operation,
            "spaceId", spaceId.toString(),
            "typeId", typeId.toString(),
            "command", nodeCommand
        )));
        return startCommand(
            user, spaceId, typeId, normalizedRequestId, operation, requestHash
        );
    }

    private LayoutAggregate replay(
        CurrentUser user,
        Context context,
        UUID spaceId,
        UUID typeId,
        CommandReceipt receipt
    ) {
        if (receipt.responseLayoutId() == null) {
            throw failure("LAYOUT_IDEMPOTENCY_CONFLICT", "Completed layout command has no response");
        }
        LayoutDefinition definition = layoutRepository.findById(
            user.workspaceId(), spaceId, typeId, receipt.responseLayoutId()
        ).orElseThrow(() -> failure("LAYOUT_NOT_FOUND", "Work item layout is not available"));
        return aggregate(context, definition);
    }

    private LayoutAggregate aggregate(Context context, LayoutDefinition definition) {
        List<LayoutNode> nodes = layoutRepository.listNodes(
            definition.workspaceId(), definition.id()
        );
        List<FieldAccessPolicy> policies = layoutRepository.listPolicies(
            definition.workspaceId(), definition.id()
        );
        return new LayoutAggregate(
            definition,
            nodes,
            policies,
            fieldReferenceValidator.diagnostics(
                definition.workspaceId(),
                definition.spaceId(),
                definition.typeDefinitionId(),
                nodes,
                policies
            ),
            actionPolicy.availableActions(
                context.space().currentUserRole(),
                context.space().status(),
                context.type().status()
            )
        );
    }

    private Context requireContext(CurrentUser user, UUID spaceId, UUID typeId, boolean writable) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project configuration is not available"));
        if (!space.isMember()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project configuration is not available");
        }
        if (!actionPolicy.isManager(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Project space owner or admin role required");
        }
        WorkItemTypeDefinition type;
        try {
            type = typeService.get(user.workspaceId(), spaceId, typeId);
        } catch (WorkItemTypeException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project configuration is not available");
        }
        if (writable && !"active".equals(space.status())) {
            throw failure("SPACE_UNAVAILABLE", "Project space must be active for layout configuration");
        }
        if (writable && "retired".equals(type.status())) {
            throw failure("RETIRED_TYPE", "Retired work item types cannot configure layouts");
        }
        return new Context(space, type);
    }

    private Command begin(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String layoutKind,
        CanonicalLayout canonical,
        long expectedAggregateVersion,
        String requestId
    ) {
        String normalizedRequestId = normalizeRequestId(requestId);
        String operation = "save:" + layoutKind;
        String requestHash = hashCanonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", user.id().toString(),
            "operation", operation,
            "spaceId", spaceId.toString(),
            "typeId", typeId.toString(),
            "aggregateVersion", expectedAggregateVersion,
            "config", canonical.config()
        )));
        return startCommand(user, spaceId, typeId, normalizedRequestId, operation, requestHash);
    }

    private Command startCommand(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String normalizedRequestId,
        String operation,
        String requestHash
    ) {
        boolean started = commandRepository.tryStart(new CommandStart(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            typeId,
            normalizedRequestId,
            operation,
            requestHash,
            user.id()
        ));
        CommandReceipt receipt = commandRepository.find(user.workspaceId(), normalizedRequestId)
            .orElseThrow(() -> failure("LAYOUT_IDEMPOTENCY_CONFLICT", "Layout command is unavailable"));
        if (!receipt.spaceId().equals(spaceId)
            || !receipt.typeDefinitionId().equals(typeId)
            || !receipt.operation().equals(operation)
            || !receipt.requestHash().equals(requestHash)
            || !receipt.createdBy().equals(user.id())) {
            throw failure(
                "LAYOUT_IDEMPOTENCY_CONFLICT",
                "Request id was already used with a different layout command"
            );
        }
        if (!started && !"completed".equals(receipt.status())) {
            throw failure("LAYOUT_IDEMPOTENCY_IN_PROGRESS", "The original layout command is in progress");
        }
        return new Command(!started, normalizedRequestId, receipt);
    }

    private String normalizeRequestId(String requestId) {
        String normalized = requestId == null ? "" : requestId.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id must contain 1 to 120 characters");
        }
        return normalized;
    }

    private void recordChange(
        CurrentUser user,
        LayoutDefinition definition,
        int nodeCount,
        int policyCount,
        String requestId
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "space_configuration");
        metadata.put("requestId", requestId);
        metadata.put("spaceId", definition.spaceId().toString());
        metadata.put("typeDefinitionId", definition.typeDefinitionId().toString());
        metadata.put("layoutKind", definition.layoutKind());
        metadata.put("configHash", definition.configHash());
        metadata.put("nodeCount", nodeCount);
        metadata.put("policyCount", policyCount);
        metadata.put("aggregateVersion", definition.aggregateVersion());
        var boundary = RequestBoundaryContext.current();
        metadata.put("sourceUi", boundary.sourceUi());
        metadata.put("apiSurface", boundary.apiSurface());
        metadata.put("client", boundary.client());
        auditLog.append(
            user.workspaceId(),
            user.id(),
            "work_item_layout.saved",
            "work_item_layout",
            definition.id(),
            null,
            null,
            metadata
        );
        outbox.append(
            user.workspaceId(),
            "work_item_layout.saved",
            "work_item_layout",
            definition.id(),
            user.id(),
            metadata,
            eventKey(user.workspaceId(), requestId, definition.id())
        );
    }

    private String eventKey(UUID workspaceId, String requestId, UUID layoutId) {
        UUID key = UUID.nameUUIDFromBytes(
            (workspaceId + ":" + requestId + ":" + layoutId).getBytes(StandardCharsets.UTF_8)
        );
        return "wil:" + key;
    }

    private record Context(ProjectSpaceSummary space, WorkItemTypeDefinition type) {
    }

    private record Command(boolean replay, String requestId, CommandReceipt receipt) {
    }
}
