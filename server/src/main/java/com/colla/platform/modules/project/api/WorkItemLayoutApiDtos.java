package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutAggregate;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemLayoutApiDtos {
    private WorkItemLayoutApiDtos() {
    }

    public static LayoutView view(LayoutAggregate aggregate) {
        var definition = aggregate.definition();
        return new LayoutView(
            definition.id(),
            definition.spaceId(),
            definition.typeDefinitionId(),
            definition.layoutKind(),
            definition.configHash(),
            definition.status(),
            definition.aggregateVersion(),
            definition.createdBy(),
            definition.createdAt(),
            definition.updatedBy(),
            definition.updatedAt(),
            aggregate.nodes().stream().map(WorkItemLayoutApiDtos::node).toList(),
            aggregate.policies().stream().map(WorkItemLayoutApiDtos::policy).toList(),
            aggregate.diagnostics().stream().map(WorkItemLayoutApiDtos::diagnostic).toList(),
            aggregate.availableActions()
        );
    }

    private static NodeView node(LayoutNode node) {
        return new NodeView(
            node.id(),
            node.parentId(),
            node.nodeKey(),
            node.nodeType(),
            node.fieldId(),
            node.fieldKey(),
            node.sortOrder(),
            node.config(),
            node.visibilityCondition()
        );
    }

    private static PolicyView policy(FieldAccessPolicy policy) {
        return new PolicyView(
            policy.id(),
            policy.fieldId(),
            policy.fieldKey(),
            policy.policyKey(),
            policy.policy(),
            policy.configHash()
        );
    }

    private static DiagnosticView diagnostic(LayoutDiagnostic diagnostic) {
        return new DiagnosticView(
            diagnostic.code(),
            diagnostic.nodeKey(),
            diagnostic.fieldKey(),
            diagnostic.message()
        );
    }

    public record LayoutView(
        UUID id,
        UUID spaceId,
        UUID typeDefinitionId,
        String layoutKind,
        String configHash,
        String status,
        long aggregateVersion,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        List<NodeView> nodes,
        List<PolicyView> policies,
        List<DiagnosticView> diagnostics,
        List<String> availableActions
    ) {
    }

    public record NodeView(
        UUID id,
        UUID parentId,
        String nodeKey,
        String nodeType,
        UUID fieldId,
        String fieldKey,
        int sortOrder,
        JsonNode config,
        JsonNode visibilityCondition
    ) {
    }

    public record PolicyView(
        UUID id,
        UUID fieldId,
        String fieldKey,
        String policyKey,
        JsonNode policy,
        String configHash
    ) {
    }

    public record DiagnosticView(String code, String nodeKey, String fieldKey, String message) {
    }
}
