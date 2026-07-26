package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public final class WorkItemConfigurationCompatibilityModels {
    private WorkItemConfigurationCompatibilityModels() {
    }

    public enum CompatibilityImpact {
        compatible,
        review_required,
        migration_required,
        blocked
    }

    public record CompatibilityFinding(
        String keyPath,
        CompatibilityImpact impact,
        String reasonCode,
        String recommendation,
        JsonNode beforeValue,
        JsonNode afterValue
    ) {
    }

    public record CompatibilityReport(
        String fromHash,
        String toHash,
        CompatibilityImpact overallImpact,
        List<CompatibilityFinding> findings,
        Map<String, Integer> summary,
        boolean instanceMigrationRequired
    ) {
    }
}
