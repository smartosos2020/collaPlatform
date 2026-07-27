package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkItemRelationExperienceModels {
    private WorkItemRelationExperienceModels() {
    }

    public record TargetCandidate(
        UUID id,
        String displayKey,
        String title,
        String typeKey,
        String typeName,
        String status,
        long version
    ) {
    }

    public record TargetPage(
        String relationKey,
        List<TargetCandidate> items,
        UUID nextCursor,
        boolean truncated
    ) {
    }

    public record RelationGroup(
        String relationKey,
        String perspective,
        String displayName,
        int count,
        boolean truncated
    ) {
    }

    public record RelationSummary(
        UUID workItemId,
        List<RelationGroup> groups,
        List<RelationView> items,
        boolean truncated,
        String calibrationToken
    ) {
    }

    public record ImpactNode(
        UUID id,
        String displayKey,
        String title,
        String typeKey,
        String status,
        long version
    ) {
    }

    public record ImpactLink(
        UUID relationId,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        int depth
    ) {
    }

    public record ImpactAnalysis(
        UUID focusWorkItemId,
        String relationKey,
        String direction,
        int maxDepth,
        List<ImpactNode> nodes,
        List<ImpactLink> links,
        boolean truncated,
        String truncationReason,
        String calibrationToken
    ) {
    }

    public record ChangePreview(
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        long currentSourceVersion,
        long currentTargetVersion,
        boolean versionConflict,
        boolean canCreate,
        List<String> denialReasons,
        Map<String, Object> retainedInput
    ) {
    }
}
