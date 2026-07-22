package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.DuplicateOwnerItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.Findings;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.IllegalRoleItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ImDriftItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.MissingConversationItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.MissingOwnerItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.OrphanMemberItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ProjectLegacyProfile;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ProjectTotals;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.RoleDistribution;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.SharedConversationItem;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationBatchRecord;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationVerificationReport;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.VerificationMismatch;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.VerifiedSpaceMatch;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ProjectMigrationDtos {
    private ProjectMigrationDtos() {
    }

    static ProjectLegacyProfileView profile(ProjectLegacyProfile profile) {
        return new ProjectLegacyProfileView(
            profile.workspaceId(),
            profile.generatedAt(),
            profile.totals(),
            profile.roleDistribution(),
            bucket(profile.orphanMembers()),
            bucket(profile.illegalRoles()),
            bucket(profile.duplicateOwners()),
            bucket(profile.sharedConversations()),
            bucket(profile.projectsWithoutOwner()),
            bucket(profile.imDrifts()),
            bucket(profile.missingConversations())
        );
    }

    private static <T> FindingBucket<T> bucket(Findings<T> findings) {
        return new FindingBucket<>(
            findings.totalCount(),
            findings.totalCount() > findings.items().size(),
            findings.items()
        );
    }

    static MigrationBatchListItemView batchListItem(MigrationBatchRecord batch) {
        Object counts = batch.summary() == null ? null : batch.summary().get("counts");
        return new MigrationBatchListItemView(
            batch.id(),
            batch.dryRun(),
            batch.status(),
            counts instanceof Map<?, ?> map ? castCounts(map) : Map.of(),
            batch.startedBy(),
            batch.startedAt(),
            batch.finishedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castCounts(Map<?, ?> counts) {
        return (Map<String, Object>) counts;
    }

    static MigrationBatchView batch(MigrationBatchRecord batch) {
        return new MigrationBatchView(
            batch.id(),
            batch.workspaceId(),
            batch.status(),
            batch.dryRun(),
            batch.sourceWatermark(),
            batch.sourceChecksum(),
            batch.resultChecksum(),
            batch.summary(),
            batch.failures(),
            batch.startedBy(),
            batch.startedAt(),
            batch.finishedAt(),
            batch.rolledBackBy(),
            batch.rolledBackAt()
        );
    }

    static MigrationVerificationReportView verificationReport(MigrationVerificationReport report) {
        return new MigrationVerificationReportView(
            report.batchId(),
            report.workspaceId(),
            report.verifiedAt(),
            report.allMatched(),
            report.matches(),
            report.mismatches()
        );
    }

    record MigrationBatchListItemView(
        UUID id,
        boolean dryRun,
        String status,
        Map<String, Object> counts,
        UUID startedBy,
        Instant startedAt,
        Instant finishedAt
    ) {
    }

    record MigrationBatchView(
        UUID id,
        UUID workspaceId,
        String status,
        boolean dryRun,
        Instant sourceWatermark,
        String sourceChecksum,
        String resultChecksum,
        Map<String, Object> summary,
        List<Map<String, Object>> failures,
        UUID startedBy,
        Instant startedAt,
        Instant finishedAt,
        UUID rolledBackBy,
        Instant rolledBackAt
    ) {
    }

    record MigrationVerificationReportView(
        UUID batchId,
        UUID workspaceId,
        Instant verifiedAt,
        boolean allMatched,
        List<VerifiedSpaceMatch> matches,
        List<VerificationMismatch> mismatches
    ) {
    }

    record MigrationConfirmationRequest(String confirmation) {
    }

    record FindingBucket<T>(long totalCount, boolean truncated, List<T> items) {
    }

    record ProjectLegacyProfileView(
        UUID workspaceId,
        Instant generatedAt,
        ProjectTotals totals,
        RoleDistribution roleDistribution,
        FindingBucket<OrphanMemberItem> orphanMembers,
        FindingBucket<IllegalRoleItem> illegalRoles,
        FindingBucket<DuplicateOwnerItem> duplicateOwners,
        FindingBucket<SharedConversationItem> sharedConversations,
        FindingBucket<MissingOwnerItem> projectsWithoutOwner,
        FindingBucket<ImDriftItem> imDrifts,
        FindingBucket<MissingConversationItem> missingConversations
    ) {
    }
}
