package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public final class WorkItemCompatibilityModels {
    private WorkItemCompatibilityModels() {
    }

    public enum ReadStage {
        LEGACY,
        SHADOW,
        CANONICAL_READ,
        CANONICAL_WRITE,
        COMPLETE;

        public static ReadStage parse(String value) {
            return valueOf(value.toUpperCase());
        }

        public boolean prefersCanonical() {
            return this == CANONICAL_READ || this == CANONICAL_WRITE || this == COMPLETE;
        }
    }

    public record CutoverState(
        UUID spaceId,
        ReadStage readStage,
        boolean legacyWriteEnabled,
        boolean killSwitchEnabled,
        long version
    ) {
        public static CutoverState legacy(UUID spaceId) {
            return new CutoverState(spaceId, ReadStage.LEGACY, true, false, 0);
        }

        public boolean canonicalReadPreferred() {
            return !killSwitchEnabled && readStage.prefersCanonical();
        }
    }

    public record LegacyWorkItemMap(
        String sourceType,
        UUID sourceId,
        UUID sourceProjectId,
        UUID spaceId,
        UUID workItemId,
        String identityDecision,
        String sourceFingerprint,
        String status
    ) {
        public String canonicalLocation() {
            return "/project-spaces/" + spaceId + "/work-items/" + workItemId;
        }
    }

    public record CompatibilityWorkItem(
        String source,
        UUID sourceId,
        UUID canonicalId,
        UUID spaceId,
        String displayKey,
        String typeKey,
        String title,
        String status,
        JsonNode fieldValues,
        String canonicalLocation,
        Instant updatedAt
    ) {
    }

    public record LegacyProfile(
        UUID workspaceId,
        Instant sourceWatermark,
        long projects,
        long issues,
        long members,
        long comments,
        long attachments,
        long activities,
        long relations,
        long orphanIssues,
        long orphanComments,
        long orphanAttachments,
        long crossWorkspaceReferences,
        String sourceFingerprint
    ) {
    }
}
