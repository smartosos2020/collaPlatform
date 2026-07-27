package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemApiDtos.WorkItemResponse;
import com.colla.platform.modules.project.api.WorkItemRelationApiDtos.RelationResponse;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyIssue;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyReport;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyMutation;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyNavigation;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyNode;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyPage;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyRebuildBatch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemHierarchyApiDtos {
    private WorkItemHierarchyApiDtos() {
    }

    public static HierarchyPageResponse page(HierarchyPage page) {
        return new HierarchyPageResponse(
            page.items().stream().map(WorkItemHierarchyApiDtos::node).toList(),
            page.nextCursor(),
            page.truncated()
        );
    }

    public static HierarchyNavigationResponse navigation(HierarchyNavigation value) {
        return new HierarchyNavigationResponse(
            node(value.focus()),
            nodes(value.breadcrumbs()),
            value.parent() == null ? null : node(value.parent()),
            nodes(value.children()),
            nodes(value.siblings()),
            nodes(value.localTree()),
            value.truncated(),
            value.degradationReason()
        );
    }

    public static HierarchyMutationResponse mutation(HierarchyMutation value) {
        RelationResponse relation = WorkItemRelationApiDtos.response(value.relation());
        WorkItemResponse child = value.child() == null
            ? null
            : WorkItemApiDtos.response(value.child());
        return new HierarchyMutationResponse(relation, child);
    }

    public static ConsistencyReportResponse consistency(ConsistencyReport value) {
        return new ConsistencyReportResponse(
            value.relationKey(),
            value.edgeCount(),
            value.expectedPathCount(),
            value.actualPathCount(),
            value.issues().stream().map(WorkItemHierarchyApiDtos::issue).toList(),
            value.truncated()
        );
    }

    public static HierarchyRebuildBatchResponse batch(HierarchyRebuildBatch value) {
        return new HierarchyRebuildBatchResponse(
            value.id(),
            value.spaceId(),
            value.relationKey(),
            value.requestId(),
            value.dryRun(),
            value.status(),
            value.attempt(),
            value.edgeCount(),
            value.expectedPathCount(),
            value.issueCount(),
            value.failures().stream().map(WorkItemHierarchyApiDtos::issue).toList(),
            value.requestedBy(),
            value.createdAt(),
            value.completedAt()
        );
    }

    private static List<HierarchyNodeResponse> nodes(List<HierarchyNode> values) {
        return values.stream().map(WorkItemHierarchyApiDtos::node).toList();
    }

    private static HierarchyNodeResponse node(HierarchyNode value) {
        return new HierarchyNodeResponse(
            value.id(),
            value.typeDefinitionId(),
            value.typeVersionId(),
            value.typeKey(),
            value.displayKey(),
            value.title(),
            value.status(),
            value.version(),
            value.depth(),
            value.directRelationId()
        );
    }

    private static ConsistencyIssueResponse issue(ConsistencyIssue value) {
        return new ConsistencyIssueResponse(
            value.code(),
            value.ancestorWorkItemId(),
            value.descendantWorkItemId(),
            value.expectedDepth(),
            value.actualDepth()
        );
    }

    public record HierarchyNodeResponse(
        UUID id,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String typeKey,
        String displayKey,
        String title,
        String status,
        long version,
        int depth,
        UUID directRelationId
    ) {
    }

    public record HierarchyPageResponse(
        List<HierarchyNodeResponse> items,
        String nextCursor,
        boolean truncated
    ) {
    }

    public record HierarchyNavigationResponse(
        HierarchyNodeResponse focus,
        List<HierarchyNodeResponse> breadcrumbs,
        HierarchyNodeResponse parent,
        List<HierarchyNodeResponse> children,
        List<HierarchyNodeResponse> siblings,
        List<HierarchyNodeResponse> localTree,
        boolean truncated,
        String degradationReason
    ) {
    }

    public record HierarchyMutationResponse(
        RelationResponse relation,
        WorkItemResponse child
    ) {
    }

    public record ConsistencyIssueResponse(
        String code,
        UUID ancestorWorkItemId,
        UUID descendantWorkItemId,
        Integer expectedDepth,
        Integer actualDepth
    ) {
    }

    public record ConsistencyReportResponse(
        String relationKey,
        int edgeCount,
        int expectedPathCount,
        int actualPathCount,
        List<ConsistencyIssueResponse> issues,
        boolean truncated
    ) {
    }

    public record HierarchyRebuildBatchResponse(
        UUID id,
        UUID spaceId,
        String relationKey,
        String requestId,
        boolean dryRun,
        String status,
        int attempt,
        int edgeCount,
        int expectedPathCount,
        int issueCount,
        List<ConsistencyIssueResponse> failures,
        UUID requestedBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }
}
