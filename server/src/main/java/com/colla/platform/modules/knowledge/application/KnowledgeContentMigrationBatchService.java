package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentMigrationPlan;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentMigrationModels.KnowledgeContentMigrationBatchPlan;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentMigrationModels.KnowledgeContentMigrationItemPlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeContentMigrationBatchService {
    public KnowledgeContentMigrationBatchPlan plan(
        UUID workspaceId,
        String idempotencyKey,
        List<KnowledgeContentMigrationPlan> migrationPlans
    ) {
        if (workspaceId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Workspace and idempotency key are required");
        }
        List<KnowledgeContentMigrationPlan> sorted = migrationPlans == null ? List.of() : migrationPlans.stream()
            .sorted(Comparator.comparing(KnowledgeContentMigrationPlan::itemId))
            .toList();
        List<KnowledgeContentMigrationItemPlan> items = sorted.stream().map(this::itemPlan).toList();
        int failures = (int) items.stream().filter(item -> "failed".equals(item.status())).count();
        String sourceChecksum = combinedChecksum(sorted.stream().map(KnowledgeContentMigrationPlan::sourceChecksum).toList());
        String targetChecksum = combinedChecksum(sorted.stream().map(KnowledgeContentMigrationPlan::targetChecksum).toList());
        return new KnowledgeContentMigrationBatchPlan(
            UUID.nameUUIDFromBytes((workspaceId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)),
            workspaceId,
            idempotencyKey,
            "dry_run",
            failures == 0 ? "planned" : "failed",
            items.size(),
            items.size() - failures,
            failures,
            sourceChecksum,
            targetChecksum,
            items
        );
    }

    private KnowledgeContentMigrationItemPlan itemPlan(KnowledgeContentMigrationPlan plan) {
        boolean safe = plan.safeToApply();
        String failureCode = safe ? null : plan.issues().stream().filter(issue -> issue.isError()).map(issue -> issue.code()).findFirst().orElse("SCHEMA_INVALID");
        String failureMessage = safe ? null : plan.issues().stream().filter(issue -> issue.isError()).map(issue -> issue.message()).findFirst().orElse("Canonical migration is not safe to apply");
        return new KnowledgeContentMigrationItemPlan(
            plan.itemId(),
            plan.sourceChecksum(),
            plan.targetChecksum(),
            safe ? "planned" : "failed",
            failureCode,
            failureMessage,
            plan.canonicalDocument().document()
        );
    }

    private String combinedChecksum(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            values.forEach(value -> digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest.digest()) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
