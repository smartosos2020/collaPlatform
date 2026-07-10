package com.colla.platform.modules.doc.api;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseAccessDocumentStat;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseAccessStats;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseGovernanceDashboard;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseGovernanceRisk;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseHealthMetrics;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

final class AdminKnowledgeBaseDtos {
    private AdminKnowledgeBaseDtos() {
    }

    static AdminKnowledgeBaseSpaceView space(KnowledgeBaseSpaceSummary space) {
        return new AdminKnowledgeBaseSpaceView(
            space.id(),
            space.name(),
            space.code(),
            space.description(),
            space.icon(),
            space.coverUrl(),
            space.status(),
            space.visibility(),
            space.rootDocumentId(),
            space.homeDocumentId(),
            space.ownerId(),
            space.ownerName(),
            space.defaultPermissionLevel(),
            space.createdAt(),
            space.updatedAt(),
            space.documentCount(),
            new AdminKnowledgeBaseGovernanceState(space.status(), space.visibility(), space.defaultPermissionLevel()),
            new AdminKnowledgeBaseAuditScope("knowledge_base", space.id(), List.of(
                "knowledge_base.created",
                "knowledge_base.updated",
                "knowledge_base.disabled",
                "knowledge_base.archived",
                "knowledge_base.governance.bulk_updated"
            )),
            availableActions(space.status())
        );
    }

    static AdminKnowledgeBaseDetailView detail(KnowledgeBaseSpaceDetail detail) {
        return new AdminKnowledgeBaseDetailView(
            space(detail.space()),
            content(detail.rootDocument()),
            content(detail.homeDocument())
        );
    }

    static AdminKnowledgeBaseGovernanceView governance(KnowledgeBaseSpaceSummary space, KnowledgeBaseGovernanceDashboard dashboard) {
        List<AdminKnowledgeBaseGovernanceRiskView> risks = dashboard.risks().stream()
            .map(AdminKnowledgeBaseDtos::risk)
            .toList();
        return new AdminKnowledgeBaseGovernanceView(
            space(space),
            health(dashboard.health()),
            risks,
            accessStats(dashboard.accessStats()),
            severityBuckets(risks),
            List.of("assign_maintainer", "replace_tags", "request_review", "archive_low_value")
        );
    }

    private static AdminKnowledgeBaseContentRef content(DocumentSummary document) {
        return new AdminKnowledgeBaseContentRef(
            document.id(),
            document.parentId(),
            document.title(),
            document.docType(),
            document.archived(),
            document.maintainerId(),
            document.maintainerName(),
            document.tags(),
            document.knowledgeStatus(),
            document.reviewDueAt() == null ? null : document.reviewDueAt().toString()
        );
    }

    private static AdminKnowledgeBaseHealthView health(KnowledgeBaseHealthMetrics health) {
        return new AdminKnowledgeBaseHealthView(
            health.documentCount(),
            health.activeDocumentCount(),
            health.outdatedDocumentCount(),
            health.unmaintainedDocumentCount(),
            health.ownerlessDocumentCount(),
            health.highRiskPermissionCount(),
            health.blockCoverageGapCount(),
            health.emptyBlockCount(),
            health.invalidEmbedBlockCount(),
            health.blockCoveragePercent()
        );
    }

    private static AdminKnowledgeBaseAccessStatsView accessStats(KnowledgeBaseAccessStats accessStats) {
        return new AdminKnowledgeBaseAccessStatsView(
            accessStats.visitorCount(),
            accessStats.accessCount(),
            accessStats.popularDocuments().stream().map(AdminKnowledgeBaseDtos::accessDocument).toList(),
            accessStats.lowAccessDocuments().stream().map(AdminKnowledgeBaseDtos::accessDocument).toList(),
            accessStats.noResultTerms()
        );
    }

    private static AdminKnowledgeBaseAccessDocumentView accessDocument(KnowledgeBaseAccessDocumentStat stat) {
        return new AdminKnowledgeBaseAccessDocumentView(
            content(stat.document()),
            stat.visitorCount(),
            stat.accessCount(),
            stat.lastAccessedAt()
        );
    }

    private static AdminKnowledgeBaseGovernanceRiskView risk(KnowledgeBaseGovernanceRisk risk) {
        return new AdminKnowledgeBaseGovernanceRiskView(
            risk.id(),
            risk.ruleCode(),
            risk.severity(),
            risk.resourceType(),
            risk.resourceId(),
            risk.title(),
            risk.subjectType(),
            risk.subjectId(),
            risk.subjectName(),
            risk.permissionLevel(),
            risk.reason(),
            risk.actionPath(),
            new AdminRiskBadge(risk.severity(), riskWeight(risk.severity()))
        );
    }

    private static Map<String, Long> severityBuckets(List<AdminKnowledgeBaseGovernanceRiskView> risks) {
        return risks.stream().collect(Collectors.groupingBy(AdminKnowledgeBaseGovernanceRiskView::severity, Collectors.counting()));
    }

    private static List<String> availableActions(String status) {
        if ("archived".equals(status)) {
            return List.of("restore", "audit");
        }
        if ("disabled".equals(status)) {
            return List.of("enable", "archive", "audit");
        }
        return List.of("edit", "disable", "archive", "govern", "audit");
    }

    private static int riskWeight(String severity) {
        return switch (severity == null ? "" : severity) {
            case "critical" -> 100;
            case "high" -> 80;
            case "medium" -> 50;
            case "low" -> 20;
            default -> 0;
        };
    }

    record AdminKnowledgeBaseSpaceView(
        UUID id,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String status,
        String visibility,
        UUID rootDocumentId,
        UUID homeDocumentId,
        UUID ownerId,
        String ownerName,
        String defaultPermissionLevel,
        Instant createdAt,
        Instant updatedAt,
        long documentCount,
        AdminKnowledgeBaseGovernanceState governance,
        AdminKnowledgeBaseAuditScope auditScope,
        List<String> availableActions
    ) {
    }

    record AdminKnowledgeBaseDetailView(
        AdminKnowledgeBaseSpaceView space,
        AdminKnowledgeBaseContentRef rootDocument,
        AdminKnowledgeBaseContentRef homeDocument
    ) {
    }

    record AdminKnowledgeBaseGovernanceView(
        AdminKnowledgeBaseSpaceView space,
        AdminKnowledgeBaseHealthView health,
        List<AdminKnowledgeBaseGovernanceRiskView> risks,
        AdminKnowledgeBaseAccessStatsView accessStats,
        Map<String, Long> severityBuckets,
        List<String> bulkActions
    ) {
    }

    record AdminKnowledgeBaseContentRef(
        UUID id,
        UUID parentId,
        String title,
        String docType,
        boolean archived,
        UUID maintainerId,
        String maintainerName,
        List<String> tags,
        String knowledgeStatus,
        String reviewDueAt
    ) {
    }

    record AdminKnowledgeBaseGovernanceState(String status, String visibility, String defaultPermissionLevel) {
    }

    record AdminKnowledgeBaseAuditScope(String targetType, UUID targetId, List<String> actions) {
    }

    record AdminKnowledgeBaseHealthView(
        long documentCount,
        long activeDocumentCount,
        long outdatedDocumentCount,
        long unmaintainedDocumentCount,
        long ownerlessDocumentCount,
        long highRiskPermissionCount,
        long blockCoverageGapCount,
        long emptyBlockCount,
        long invalidEmbedBlockCount,
        double blockCoveragePercent
    ) {
    }

    record AdminKnowledgeBaseGovernanceRiskView(
        String id,
        String ruleCode,
        String severity,
        String resourceType,
        UUID resourceId,
        String title,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String permissionLevel,
        String reason,
        String actionPath,
        AdminRiskBadge risk
    ) {
    }

    record AdminRiskBadge(String severity, int weight) {
    }

    record AdminKnowledgeBaseAccessStatsView(
        long visitorCount,
        long accessCount,
        List<AdminKnowledgeBaseAccessDocumentView> popularDocuments,
        List<AdminKnowledgeBaseAccessDocumentView> lowAccessDocuments,
        List<?> noResultTerms
    ) {
    }

    record AdminKnowledgeBaseAccessDocumentView(
        AdminKnowledgeBaseContentRef document,
        long visitorCount,
        long accessCount,
        Instant lastAccessedAt
    ) {
    }
}
