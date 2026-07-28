package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.MetricRiskModels.EvidenceReference;
import com.colla.platform.modules.project.domain.ProjectDetailModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityFoundation;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class MetricRiskEvidenceResolver {
    private final ProjectDetailService projectDetail;
    private final ResourceCapacityService capacity;

    public MetricRiskEvidenceResolver(
        ProjectDetailService projectDetail,
        ResourceCapacityService capacity
    ) {
        this.projectDetail = projectDetail;
        this.capacity = capacity;
    }

    public Map<String, List<EvidenceReference>> resolve(
        CurrentUser user,
        UUID spaceId,
        Instant anchor
    ) {
        ProjectDetail detail = projectDetail.get(user, spaceId);
        CapacityFoundation resource = capacity.get(user, spaceId);
        Map<String, List<EvidenceReference>> result = new LinkedHashMap<>();
        List<EvidenceReference> overdue = new ArrayList<>();
        detail.deviations().stream()
            .filter(value -> value.overdueMilestones() > 0)
            .limit(20)
            .forEach(value -> overdue.add(evidence(
                "ProjectPlanService",
                value.planId().toString(),
                value.planVersion(),
                anchor,
                value.overdueMilestones() + " currently visible milestone(s) overdue"
            )));
        result.put("overdue", List.copyOf(overdue));

        List<EvidenceReference> blocked = new ArrayList<>();
        if (detail.blocking().openIssues() + detail.blocking().highRisks() > 0) {
            blocked.add(evidence(
                "ProjectDetailService",
                spaceId.toString(),
                1,
                anchor,
                "Visible blocking facts: issues=" + detail.blocking().openIssues()
                    + ", high risks=" + detail.blocking().highRisks()
            ));
        }
        result.put("blocked", List.copyOf(blocked));

        List<EvidenceReference> quality = new ArrayList<>();
        if (detail.blocking().rejectedDeliverables() > 0
            || detail.blocking().pendingAcceptances() > 0) {
            quality.add(evidence(
                "ProjectDeliveryService",
                spaceId.toString(),
                1,
                anchor,
                "Visible delivery facts: rejected=" + detail.blocking().rejectedDeliverables()
                    + ", pending acceptances=" + detail.blocking().pendingAcceptances()
            ));
        }
        result.put("quality", List.copyOf(quality));

        long conflicts = resource.buckets().stream()
            .filter(value -> value.conflict() || "overloaded".equals(value.signal()))
            .limit(201)
            .count();
        List<EvidenceReference> resourceEvidence = new ArrayList<>();
        if (conflicts > 0) {
            resourceEvidence.add(evidence(
                "ResourceCapacityService",
                spaceId.toString(),
                1,
                anchor,
                "Current visible capacity conflicts=" + Math.min(conflicts, 200)
            ));
        }
        result.put("resource", List.copyOf(resourceEvidence));

        List<EvidenceReference> stagnation = detail.health().signals().stream()
            .filter(value -> value.code().contains("stagn")
                || value.code().contains("blocked")
                || "critical".equals(value.severity()))
            .limit(20)
            .map(value -> evidence(
                "ProjectDetailService.health",
                value.sourceId().toString(),
                value.sourceVersion(),
                value.observedAt(),
                value.explanation()
            ))
            .toList();
        result.put("stagnation", stagnation);
        return Map.copyOf(result);
    }

    private EvidenceReference evidence(
        String sourceType,
        String sourceIdentity,
        long sourceVersion,
        Instant observedAt,
        String explanation
    ) {
        return new EvidenceReference(
            sourceType,
            sourceIdentity,
            sourceVersion,
            observedAt,
            explanation,
            true
        );
    }
}
