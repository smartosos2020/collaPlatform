package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.MAX_AUDIT;
import static com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.MAX_SLICES;
import static com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.CollaborationAuditEntry;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.CollaborationSlice;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.CrossTeamPanorama;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.PanoramaHealth;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.PanoramaPreference;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.SavePreferenceCommand;
import com.colla.platform.modules.project.infrastructure.CrossTeamPanoramaPreferenceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrossTeamPanoramaService {
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");
    private final CrossSpaceGrantService grants;
    private final CrossSpaceRelationService relations;
    private final CrossSpaceSyncService sync;
    private final CrossTeamPanoramaPreferenceRepository preferences;
    private final WorkItemRelationAccessDecisionService access;

    public CrossTeamPanoramaService(
        CrossSpaceGrantService grants,
        CrossSpaceRelationService relations,
        CrossSpaceSyncService sync,
        CrossTeamPanoramaPreferenceRepository preferences,
        WorkItemRelationAccessDecisionService access
    ) {
        this.grants = grants;
        this.relations = relations;
        this.sync = sync;
        this.preferences = preferences;
        this.access = access;
    }

    public CrossTeamPanorama get(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        var grantFacts = grants.list(user, spaceId);
        var relationFacts = relations.list(user, spaceId);
        var syncFacts = sync.list(user, spaceId);
        List<CollaborationSlice> slices = new ArrayList<>();
        grantFacts.grants().forEach(value -> slices.add(new CollaborationSlice(
            "grant", value.id(), value.sourceSpaceId(), value.targetSpaceId(),
            value.status(), value.currentVersion(), "cross-space-grant", value.updatedAt()
        )));
        relationFacts.policies().forEach(value -> slices.add(new CollaborationSlice(
            "relation", value.id(), value.sourceSpaceId(), value.targetSpaceId(),
            value.status(), value.version(), "cross-space-relation-policy", value.updatedAt()
        )));
        syncFacts.rules().forEach(value -> slices.add(new CollaborationSlice(
            "sync", value.id(), value.sourceSpaceId(), value.targetSpaceId(),
            value.status(), value.currentVersion(), "cross-space-sync-rule", value.updatedAt()
        )));
        syncFacts.conflicts().forEach(value -> slices.add(new CollaborationSlice(
            "conflict", value.id(), spaceId, spaceId, value.status(), value.version(),
            "cross-space-sync-conflict", value.createdAt()
        )));
        slices.sort(Comparator.comparing(CollaborationSlice::observedAt).reversed());
        boolean truncated = grantFacts.truncated()
            || relationFacts.policiesTruncated() || relationFacts.intentsTruncated()
            || syncFacts.truncated() || slices.size() > MAX_SLICES;
        List<CollaborationSlice> bounded = List.copyOf(
            slices.subList(0, Math.min(slices.size(), MAX_SLICES))
        );
        List<CollaborationAuditEntry> audit = bounded.stream()
            .map(value -> new CollaborationAuditEntry(
                value.kind(), value.identity(), value.status(), value.version(),
                value.source(), value.observedAt()
            ))
            .limit(MAX_AUDIT).toList();
        int open = (int) syncFacts.conflicts().stream()
            .filter(value -> "open".equals(value.status())).count();
        String health = open > 0 ? "attention" : truncated ? "unknown" : "healthy";
        return new CrossTeamPanorama(
            SCHEMA_VERSION,
            preferences.find(user.workspaceId(), spaceId, user.id())
                .orElse(new PanoramaPreference(false, 30, 0)),
            bounded,
            audit,
            new PanoramaHealth(
                health, grantFacts.grants().size(), relationFacts.policies().size(),
                syncFacts.rules().size(), open, truncated,
                truncated ? "bounded-source-truncated" : open > 0
                    ? "open-sync-conflict" : "current-authorized-facts"
            ),
            Instant.now()
        );
    }

    @Transactional
    public PanoramaPreference savePreference(
        CurrentUser user, UUID spaceId, SavePreferenceCommand command
    ) {
        access.requireVisible(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || command.requestId() == null
            || !REQUEST_ID.matcher(command.requestId()).matches()
            || command.expectedVersion() < 0
            || command.windowDays() < 1 || command.windowDays() > 90) {
            throw failure("CROSS_TEAM_PANORAMA_COMMAND_INVALID", "Panorama preference is invalid");
        }
        try {
            return preferences.save(
                user.workspaceId(), spaceId, user.id(), command.compact(),
                command.windowDays(), command.expectedVersion()
            );
        } catch (IllegalStateException exception) {
            throw failure("CROSS_TEAM_PANORAMA_VERSION_CONFLICT", "Panorama preference changed");
        }
    }
}
