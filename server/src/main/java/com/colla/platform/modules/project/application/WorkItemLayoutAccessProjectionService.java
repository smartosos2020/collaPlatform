package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;

import com.colla.platform.modules.identity.contract.AuthenticationQuery;
import com.colla.platform.modules.project.application.WorkItemFieldAccessPolicyEvaluator.EvaluationContext;
import com.colla.platform.modules.project.application.WorkItemFieldAccessPolicyEvaluator.FieldAccessDecision;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDefinition;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutKind;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldOptionRepository;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkItemLayoutAccessProjectionService {
    private final WorkItemLayoutRepository layoutRepository;
    private final WorkItemFieldRepository fieldRepository;
    private final WorkItemFieldOptionRepository optionRepository;
    private final ProjectSpaceRepository spaceRepository;
    private final AuthenticationQuery authenticationQuery;
    private final WorkItemTypeDefinitionService typeService;
    private final WorkItemLayoutActionPolicy actionPolicy;
    private final WorkItemLayoutFieldReferenceValidator fieldReferenceValidator;
    private final WorkItemFieldAccessPolicyEvaluator policyEvaluator;
    private final WorkItemLayoutConditionDsl conditionDsl;
    private final ObjectMapper objectMapper;

    public WorkItemLayoutAccessProjectionService(
        WorkItemLayoutRepository layoutRepository,
        WorkItemFieldRepository fieldRepository,
        WorkItemFieldOptionRepository optionRepository,
        ProjectSpaceRepository spaceRepository,
        AuthenticationQuery authenticationQuery,
        WorkItemTypeDefinitionService typeService,
        WorkItemLayoutActionPolicy actionPolicy,
        WorkItemLayoutFieldReferenceValidator fieldReferenceValidator,
        WorkItemFieldAccessPolicyEvaluator policyEvaluator,
        WorkItemLayoutConditionDsl conditionDsl,
        ObjectMapper objectMapper
    ) {
        this.layoutRepository = layoutRepository;
        this.fieldRepository = fieldRepository;
        this.optionRepository = optionRepository;
        this.spaceRepository = spaceRepository;
        this.authenticationQuery = authenticationQuery;
        this.typeService = typeService;
        this.actionPolicy = actionPolicy;
        this.fieldReferenceValidator = fieldReferenceValidator;
        this.policyEvaluator = policyEvaluator;
        this.conditionDsl = conditionDsl;
        this.objectMapper = objectMapper;
    }

    public LayoutAccessProjection project(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String layoutKind
    ) {
        Context context = requireContext(user, spaceId, typeId);
        return project(
            user,
            context,
            LayoutKind.parse(layoutKind).name(),
            new SyntheticContext(
                context.space().currentUserRole(),
                context.space().status(),
                context.type().status(),
                Map.of(),
                Map.of()
            ),
            false
        );
    }

    public LayoutAccessProjection preview(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String layoutKind,
        SyntheticContext requested
    ) {
        Context context = requireContext(user, spaceId, typeId);
        if (!actionPolicy.isManager(context.space().currentUserRole())) {
            throw failure("FORBIDDEN", "Project space owner or admin role required");
        }
        validateSyntheticContext(requested);
        return project(
            user,
            context,
            LayoutKind.parse(layoutKind).name(),
            requested,
            true
        );
    }

    public LayoutAccessProjection sample(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String layoutKind,
        Map<String, JsonNode> fieldValues
    ) {
        Context context = requireContext(user, spaceId, typeId);
        return project(
            user,
            context,
            LayoutKind.parse(layoutKind).name(),
            new SyntheticContext(
                context.space().currentUserRole(),
                context.space().status(),
                context.type().status(),
                fieldValues,
                Map.of()
            ),
            true
        );
    }

    private LayoutAccessProjection project(
        CurrentUser user,
        Context actual,
        String layoutKind,
        SyntheticContext requested,
        boolean synthetic
    ) {
        LayoutDefinition definition = layoutRepository.findByKind(
            user.workspaceId(), actual.space().id(), actual.type().id(), layoutKind
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item layout is not available"));
        List<LayoutNode> nodes = layoutRepository.listNodes(user.workspaceId(), definition.id());
        List<FieldAccessPolicy> policies = layoutRepository.listPolicies(user.workspaceId(), definition.id());
        List<FieldDefinition> fields = fieldRepository.listByType(
            user.workspaceId(), actual.space().id(), actual.type().id(), ""
        );
        validateSampleKeys(fields, requested);
        Map<UUID, FieldDefinition> fieldsById = new LinkedHashMap<>();
        fields.forEach(field -> fieldsById.put(field.id(), field));
        Map<UUID, List<FieldOption>> optionsByField = optionRepository.listByType(
            user.workspaceId(), actual.space().id(), actual.type().id()
        ).stream().collect(java.util.stream.Collectors.groupingBy(
            FieldOption::fieldDefinitionId,
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()
        ));
        Map<UUID, FieldAccessPolicy> policiesByField = new LinkedHashMap<>();
        policies.forEach(policy -> policiesByField.put(policy.fieldId(), policy));
        Map<String, FieldAccessDecision> decisions = new LinkedHashMap<>();
        for (LayoutNode node : nodes) {
            if (node.fieldId() == null) {
                continue;
            }
            FieldDefinition field = fieldsById.get(node.fieldId());
            if (field == null || !field.fieldKey().equals(node.fieldKey())) {
                continue;
            }
            String fieldStatus = requested.fieldStatuses().getOrDefault(
                field.fieldKey(), field.status()
            );
            FieldAccessPolicy policy = policiesByField.get(field.id());
            FieldAccessDecision decision = policyEvaluator.evaluate(
                policy == null ? null : policy.policy(),
                new EvaluationContext(
                    requested.role(),
                    requested.spaceStatus(),
                    requested.typeStatus(),
                    fieldStatus,
                    layoutKind,
                    synthetic,
                    requested.fieldValues()
                )
            );
            if (!"hidden".equals(decision.mode())) {
                decisions.put(field.fieldKey(), decision);
            }
        }
        List<LayoutNode> projectedNodes = filterNodes(
            nodes,
            decisions,
            requested.fieldValues(),
            requested.role(),
            layoutKind,
            synthetic
        );
        Set<String> projectedFieldKeys = new LinkedHashSet<>();
        projectedNodes.stream()
            .filter(node -> node.fieldKey() != null)
            .forEach(node -> projectedFieldKeys.add(node.fieldKey()));
        List<ProjectedField> projectedFields = fields.stream()
            .filter(field -> projectedFieldKeys.contains(field.fieldKey()))
            .map(field -> new ProjectedField(
                field.id(),
                field.fieldKey(),
                field.name(),
                field.description(),
                field.fieldType(),
                field.config(),
                requested.fieldStatuses().getOrDefault(field.fieldKey(), field.status()),
                field.system(),
                optionsByField.getOrDefault(field.id(), List.of()).stream()
                    .map(option -> new ProjectedOption(
                        option.optionKey(),
                        option.name(),
                        option.color(),
                        option.sortOrder(),
                        option.status()
                    ))
                    .toList()
            ))
            .toList();
        Map<String, FieldAccessDecision> projectedDecisions = new LinkedHashMap<>();
        projectedFieldKeys.forEach(key -> projectedDecisions.put(key, decisions.get(key)));
        List<LayoutDiagnostic> diagnostics = fieldReferenceValidator.diagnostics(
            user.workspaceId(),
            actual.space().id(),
            actual.type().id(),
            nodes,
            policies
        ).stream()
            .filter(diagnostic -> diagnostic.fieldKey() == null
                || projectedFieldKeys.contains(diagnostic.fieldKey()))
            .toList();
        if (!actionPolicy.isManager(actual.space().currentUserRole())) {
            diagnostics = List.of();
        }
        return new LayoutAccessProjection(
            definition.id(),
            definition.spaceId(),
            definition.typeDefinitionId(),
            definition.layoutKind(),
            definition.configHash(),
            definition.aggregateVersion(),
            synthetic,
            new ProjectionContext(
                requested.role(),
                requested.spaceStatus(),
                requested.typeStatus(),
                synthetic ? "synthetic" : "runtime"
            ),
            projectedNodes,
            projectedFields,
            Map.copyOf(projectedDecisions),
            diagnostics,
            synthetic ? List.of() : List.of("view")
        );
    }

    private List<LayoutNode> filterNodes(
        List<LayoutNode> nodes,
        Map<String, FieldAccessDecision> decisions,
        Map<String, JsonNode> fieldValues,
        String role,
        String layoutKind,
        boolean synthetic
    ) {
        Map<UUID, List<LayoutNode>> children = new LinkedHashMap<>();
        List<LayoutNode> roots = new ArrayList<>();
        for (LayoutNode node : nodes) {
            if (node.parentId() == null) {
                roots.add(node);
            } else {
                children.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        Map<String, JsonNode> contextValues = Map.of(
            "actor_role", objectMapper.getNodeFactory().textNode(role),
            "layout_kind", objectMapper.getNodeFactory().textNode(layoutKind),
            "mode", objectMapper.getNodeFactory().textNode(synthetic ? "synthetic" : "runtime")
        );
        List<LayoutNode> result = new ArrayList<>();
        roots.stream().sorted(java.util.Comparator.comparingInt(LayoutNode::sortOrder))
            .forEach(root -> collectVisible(
                root, children, decisions, fieldValues, contextValues, result
            ));
        return List.copyOf(result);
    }

    private boolean collectVisible(
        LayoutNode node,
        Map<UUID, List<LayoutNode>> children,
        Map<String, FieldAccessDecision> decisions,
        Map<String, JsonNode> fieldValues,
        Map<String, JsonNode> contextValues,
        List<LayoutNode> output
    ) {
        if (!conditionDsl.evaluate(node.visibilityCondition(), fieldValues, contextValues)) {
            return false;
        }
        if (node.fieldKey() != null && !decisions.containsKey(node.fieldKey())) {
            return false;
        }
        List<LayoutNode> nested = new ArrayList<>();
        children.getOrDefault(node.id(), List.of()).stream()
            .sorted(java.util.Comparator.comparingInt(LayoutNode::sortOrder))
            .forEach(child -> collectVisible(
                child, children, decisions, fieldValues, contextValues, nested
            ));
        if (node.fieldKey() == null
            && !"summary".equals(node.nodeType())
            && nested.isEmpty()) {
            return false;
        }
        LayoutNode safe = new LayoutNode(
            node.id(),
            node.parentId(),
            node.nodeKey(),
            node.nodeType(),
            node.fieldId(),
            node.fieldKey(),
            node.sortOrder(),
            node.config(),
            objectMapper.createObjectNode().put("schemaVersion", 1)
        );
        output.add(safe);
        output.addAll(nested);
        return true;
    }

    private Context requireContext(CurrentUser user, UUID spaceId, UUID typeId) {
        authenticationQuery.findActiveMember(user.workspaceId(), user.id())
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Project content is not available"
            ));
        ProjectSpaceSummary space = spaceRepository.findById(
            user.workspaceId(), spaceId, user.id()
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Project content is not available"
        ));
        if (!space.isMember()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project content is not available");
        }
        WorkItemTypeDefinition type;
        try {
            type = typeService.get(user.workspaceId(), spaceId, typeId);
        } catch (WorkItemTypeException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project content is not available");
        }
        return new Context(space, type);
    }

    private void validateSyntheticContext(SyntheticContext requested) {
        if (requested == null
            || !WorkItemFieldAccessPolicySchema.ROLES.contains(requested.role())
            || !List.of("active", "disabled", "archived").contains(requested.spaceStatus())
            || !List.of("active", "disabled", "retired").contains(requested.typeStatus())) {
            throw failure("INVALID_FIELD_ACCESS_CONTEXT", "Synthetic preview context is invalid");
        }
        requested.fieldStatuses().forEach((key, value) -> {
            if (!List.of("active", "disabled", "retired").contains(value)) {
                throw failure("INVALID_FIELD_ACCESS_CONTEXT", "Synthetic field status is invalid");
            }
        });
    }

    private void validateSampleKeys(List<FieldDefinition> fields, SyntheticContext requested) {
        Set<String> allowed = fields.stream().map(FieldDefinition::fieldKey)
            .collect(java.util.stream.Collectors.toSet());
        requested.fieldValues().keySet().forEach(key -> {
            if (!allowed.contains(key)) {
                throw failure(
                    "INVALID_FIELD_ACCESS_CONTEXT",
                    "Synthetic field sample is outside the work item type"
                );
            }
        });
        requested.fieldStatuses().keySet().forEach(key -> {
            if (!allowed.contains(key)) {
                throw failure(
                    "INVALID_FIELD_ACCESS_CONTEXT",
                    "Synthetic field status is outside the work item type"
                );
            }
        });
    }

    public record SyntheticContext(
        String role,
        String spaceStatus,
        String typeStatus,
        Map<String, JsonNode> fieldValues,
        Map<String, String> fieldStatuses
    ) {
        public SyntheticContext {
            role = role == null ? "" : role.trim().toLowerCase();
            spaceStatus = spaceStatus == null ? "" : spaceStatus.trim().toLowerCase();
            typeStatus = typeStatus == null ? "" : typeStatus.trim().toLowerCase();
            fieldValues = fieldValues == null ? Map.of() : Map.copyOf(fieldValues);
            fieldStatuses = fieldStatuses == null ? Map.of() : Map.copyOf(fieldStatuses);
        }
    }

    public record LayoutAccessProjection(
        UUID id,
        UUID spaceId,
        UUID typeDefinitionId,
        String layoutKind,
        String configHash,
        long aggregateVersion,
        boolean synthetic,
        ProjectionContext context,
        List<LayoutNode> nodes,
        List<ProjectedField> fields,
        Map<String, FieldAccessDecision> accessProjection,
        List<LayoutDiagnostic> diagnostics,
        List<String> availableActions
    ) {
    }

    public record ProjectionContext(
        String role,
        String spaceStatus,
        String typeStatus,
        String mode
    ) {
    }

    public record ProjectedField(
        UUID id,
        String fieldKey,
        String name,
        String description,
        String fieldType,
        JsonNode config,
        String status,
        boolean system,
        List<ProjectedOption> options
    ) {
    }

    public record ProjectedOption(
        String optionKey,
        String name,
        String color,
        int sortOrder,
        String status
    ) {
    }

    private record Context(ProjectSpaceSummary space, WorkItemTypeDefinition type) {
    }
}
