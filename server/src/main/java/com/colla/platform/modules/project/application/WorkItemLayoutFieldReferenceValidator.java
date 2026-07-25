package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.application.WorkItemLayoutConditionDsl.FieldReference;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkItemLayoutFieldReferenceValidator {
    private final WorkItemFieldRepository fieldRepository;
    private final WorkItemFieldTypeRegistry fieldTypeRegistry;
    private final WorkItemLayoutConditionDsl conditionDsl;

    public WorkItemLayoutFieldReferenceValidator(
        WorkItemFieldRepository fieldRepository,
        WorkItemFieldTypeRegistry fieldTypeRegistry,
        WorkItemLayoutConditionDsl conditionDsl
    ) {
        this.fieldRepository = fieldRepository;
        this.fieldTypeRegistry = fieldTypeRegistry;
        this.conditionDsl = conditionDsl;
    }

    public void validateForSave(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        List<LayoutNode> nodes,
        List<FieldAccessPolicy> policies
    ) {
        for (Reference reference : references(nodes, policies)) {
            FieldDefinition field = fieldRepository.findById(workspaceId, spaceId, typeId, reference.fieldId())
                .orElseThrow(() -> failure(
                    "INVALID_LAYOUT_FIELD_REFERENCE",
                    "A layout field reference is unavailable"
                ));
            if (!field.fieldKey().equals(reference.fieldKey()) || !"active".equals(field.status())) {
                throw failure("INVALID_LAYOUT_FIELD_REFERENCE", "A layout field reference is unavailable");
            }
        }
        validateConditions(workspaceId, spaceId, typeId, nodes);
    }

    public List<LayoutDiagnostic> diagnostics(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        List<LayoutNode> nodes,
        List<FieldAccessPolicy> policies
    ) {
        List<LayoutDiagnostic> diagnostics = new ArrayList<>();
        for (Reference reference : references(nodes, policies)) {
            var field = fieldRepository.findById(workspaceId, spaceId, typeId, reference.fieldId());
            if (field.isEmpty()) {
                diagnostics.add(diagnostic("FIELD_REFERENCE_UNAVAILABLE", reference));
            } else if (!field.get().fieldKey().equals(reference.fieldKey())) {
                diagnostics.add(diagnostic("FIELD_REFERENCE_KEY_MISMATCH", reference));
            } else if (!"active".equals(field.get().status())) {
                diagnostics.add(diagnostic(
                    "FIELD_REFERENCE_" + field.get().status().toUpperCase(),
                    reference
                ));
            }
        }
        return List.copyOf(diagnostics);
    }

    private List<Reference> references(List<LayoutNode> nodes, List<FieldAccessPolicy> policies) {
        Map<String, Reference> references = new LinkedHashMap<>();
        for (LayoutNode node : nodes) {
            if (node.fieldId() != null) {
                Reference reference = new Reference(node.fieldId(), node.fieldKey(), node.nodeKey());
                references.put("node:" + node.id(), reference);
            }
        }
        for (FieldAccessPolicy policy : policies) {
            Reference reference = new Reference(policy.fieldId(), policy.fieldKey(), policy.policyKey());
            references.put("policy:" + policy.id(), reference);
        }
        return List.copyOf(references.values());
    }

    private LayoutDiagnostic diagnostic(String code, Reference reference) {
        return new LayoutDiagnostic(
            code,
            reference.sourceKey(),
            reference.fieldKey(),
            "The referenced field is not currently available"
        );
    }

    private void validateConditions(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        List<LayoutNode> nodes
    ) {
        Map<UUID, FieldDefinition> fields = new LinkedHashMap<>();
        fieldRepository.listByType(workspaceId, spaceId, typeId, "")
            .forEach(field -> fields.put(field.id(), field));
        Map<String, LayoutNode> fieldNodes = new LinkedHashMap<>();
        nodes.stream()
            .filter(node -> node.fieldId() != null)
            .forEach(node -> fieldNodes.put(node.fieldKey(), node));
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (LayoutNode node : nodes) {
            List<FieldReference> references = conditionDsl.fieldReferences(node.visibilityCondition());
            if (references.isEmpty()) {
                continue;
            }
            List<String> nodeDependencies = new ArrayList<>();
            for (FieldReference reference : references) {
                FieldDefinition field = fields.get(reference.fieldId());
                if (field == null
                    || !field.fieldKey().equals(reference.fieldKey())
                    || !"active".equals(field.status())) {
                    throw failure(
                        "INVALID_LAYOUT_CONDITION_REFERENCE",
                        "Condition field reference is unavailable"
                    );
                }
                if (!fieldTypeRegistry.require(field.fieldType()).operators().contains(reference.operator())) {
                    throw failure(
                        "INVALID_LAYOUT_CONDITION_OPERATOR",
                        "Condition operator is not supported by field type " + field.fieldType()
                    );
                }
                if (!fieldNodes.containsKey(reference.fieldKey())) {
                    throw failure(
                        "LAYOUT_CONDITION_HIDDEN_DEPENDENCY",
                        "Condition field " + reference.fieldKey() + " must be present in the same layout"
                    );
                }
                validateLiteral(field.fieldType(), reference.operator(), reference.value());
                nodeDependencies.add(reference.fieldKey());
            }
            if (node.fieldKey() != null) {
                dependencies.put(node.fieldKey(), List.copyOf(nodeDependencies));
            }
        }
        detectConditionCycles(dependencies);
    }

    private void validateLiteral(String fieldType, String operator, com.fasterxml.jackson.databind.JsonNode value) {
        if ("is_empty".equals(operator)) {
            return;
        }
        boolean valid = switch (fieldType) {
            case "number" -> value.isNumber()
                || ("between".equals(operator) && value.isArray() && value.size() == 2
                    && value.get(0).isNumber() && value.get(1).isNumber());
            case "boolean" -> value.isBoolean();
            case "multi_select", "user", "attachment", "work_item_reference" -> value.isArray();
            case "single_select" -> value.isTextual() || ("in".equals(operator) && value.isArray());
            default -> value.isTextual()
                || ("between".equals(operator) && value.isArray() && value.size() == 2);
        };
        if (!valid) {
            throw failure(
                "INVALID_LAYOUT_CONDITION_VALUE",
                "Condition value is incompatible with field type " + fieldType
            );
        }
    }

    private void detectConditionCycles(Map<String, List<String>> dependencies) {
        Set<String> complete = new java.util.HashSet<>();
        for (String key : dependencies.keySet()) {
            visit(key, dependencies, new java.util.LinkedHashSet<>(), complete);
        }
    }

    private void visit(
        String key,
        Map<String, List<String>> dependencies,
        Set<String> path,
        Set<String> complete
    ) {
        if (complete.contains(key)) {
            return;
        }
        if (!path.add(key)) {
            throw failure("LAYOUT_CONDITION_CYCLE", "Layout condition dependency cycle detected at " + key);
        }
        for (String dependency : dependencies.getOrDefault(key, List.of())) {
            if (dependencies.containsKey(dependency)) {
                visit(dependency, dependencies, path, complete);
            }
        }
        path.remove(key);
        complete.add(key);
    }

    private record Reference(UUID fieldId, String fieldKey, String sourceKey) {
    }
}
