package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.CrossSpaceGrantService;
import com.colla.platform.modules.project.application.CrossSpaceRelationService;
import com.colla.platform.modules.project.application.CrossSpaceSyncService;
import com.colla.platform.modules.project.application.CrossTeamPanoramaService;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand.CanonicalRelationReference;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.CrossSpaceGrant;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantFoundation;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantHistory;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.GrantLifecycleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceGrantModels.SaveGrantCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CreateLinkIntentCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.CrossSpaceRelationPolicy;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.EndpointReference;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntent;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.LinkIntentCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.RelationFoundation;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.RelationPolicyLifecycleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceRelationModels.SaveRelationPolicyCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.ExecuteSyncCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.ResolveConflictCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SaveSyncRuleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncConflict;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncFoundation;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRule;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRuleLifecycleCommand;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRun;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRunDetail;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.CrossTeamPanorama;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.PanoramaPreference;
import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.SavePreferenceCommand;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/cross-space")
public final class UserCrossSpaceCollaborationController {
    private final CrossSpaceGrantService grants;
    private final CrossSpaceRelationService relations;
    private final CrossSpaceSyncService sync;
    private final CrossTeamPanoramaService panorama;

    public UserCrossSpaceCollaborationController(
        CrossSpaceGrantService grants,
        CrossSpaceRelationService relations,
        CrossSpaceSyncService sync,
        CrossTeamPanoramaService panorama
    ) {
        this.grants = grants;
        this.relations = relations;
        this.sync = sync;
        this.panorama = panorama;
    }

    @GetMapping("/grants")
    public GrantFoundation grants(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return grants.list(currentUser(authentication), spaceId);
    }

    @PostMapping("/grants")
    public CrossSpaceGrant save(
        @PathVariable UUID spaceId,
        @RequestBody SaveGrantCommand command,
        Authentication authentication
    ) {
        return grants.save(currentUser(authentication), spaceId, command);
    }

    @GetMapping("/grants/{grantId}")
    public GrantHistory history(
        @PathVariable UUID spaceId,
        @PathVariable UUID grantId,
        Authentication authentication
    ) {
        grants.list(currentUser(authentication), spaceId);
        return grants.history(currentUser(authentication), grantId);
    }

    @PostMapping("/grants/{grantId}/lifecycle")
    public CrossSpaceGrant lifecycle(
        @PathVariable UUID spaceId,
        @PathVariable UUID grantId,
        @RequestBody GrantLifecycleCommand command,
        Authentication authentication
    ) {
        grants.list(currentUser(authentication), spaceId);
        return grants.lifecycle(currentUser(authentication), grantId, command);
    }

    @GetMapping("/relations")
    public RelationFoundation relations(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return relations.list(currentUser(authentication), spaceId);
    }

    @PostMapping("/relation-policies")
    public CrossSpaceRelationPolicy createPolicy(
        @PathVariable UUID spaceId,
        @RequestBody SaveRelationPolicyCommand command,
        Authentication authentication
    ) {
        return relations.createPolicy(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/relation-policies/{policyId}/lifecycle")
    public CrossSpaceRelationPolicy policyLifecycle(
        @PathVariable UUID spaceId,
        @PathVariable UUID policyId,
        @RequestBody RelationPolicyLifecycleCommand command,
        Authentication authentication
    ) {
        relations.list(currentUser(authentication), spaceId);
        return relations.policyLifecycle(currentUser(authentication), policyId, command);
    }

    @PostMapping("/relation-policies/{policyId}/intents")
    public LinkIntent createIntent(
        @PathVariable UUID spaceId,
        @PathVariable UUID policyId,
        @RequestBody CreateLinkIntentCommand command,
        Authentication authentication
    ) {
        relations.list(currentUser(authentication), spaceId);
        return relations.createIntent(currentUser(authentication), policyId, command);
    }

    @PostMapping("/link-intents/{intentId}/lifecycle")
    public LinkIntent intentLifecycle(
        @PathVariable UUID spaceId,
        @PathVariable UUID intentId,
        @RequestBody LinkIntentCommand command,
        Authentication authentication
    ) {
        relations.list(currentUser(authentication), spaceId);
        return relations.intentLifecycle(currentUser(authentication), intentId, command);
    }

    @GetMapping("/relation-policies/{policyId}/endpoints/{workItemId}")
    public EndpointReference endpointReference(
        @PathVariable UUID spaceId,
        @PathVariable UUID policyId,
        @PathVariable UUID workItemId,
        Authentication authentication
    ) {
        relations.list(currentUser(authentication), spaceId);
        return relations.endpointReference(
            currentUser(authentication), policyId, workItemId
        );
    }

    @GetMapping("/relation-policies/{policyId}/relations/{relationId}")
    public CanonicalRelationReference relation(
        @PathVariable UUID spaceId,
        @PathVariable UUID policyId,
        @PathVariable UUID relationId,
        Authentication authentication
    ) {
        relations.list(currentUser(authentication), spaceId);
        return relations.relation(currentUser(authentication), policyId, relationId);
    }

    @PostMapping("/relation-policies/{policyId}/relations/{relationId}:withdraw")
    public CanonicalRelationReference withdraw(
        @PathVariable UUID spaceId,
        @PathVariable UUID policyId,
        @PathVariable UUID relationId,
        @RequestBody WithdrawRelationRequest request,
        Authentication authentication
    ) {
        relations.list(currentUser(authentication), spaceId);
        return relations.withdraw(
            currentUser(authentication), policyId, relationId,
            request.expectedVersion(), request.reason(), request.requestId()
        );
    }

    @GetMapping("/sync")
    public SyncFoundation sync(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return sync.list(currentUser(authentication), spaceId);
    }

    @PostMapping("/sync-rules")
    public SyncRule saveSyncRule(
        @PathVariable UUID spaceId,
        @RequestBody SaveSyncRuleCommand command,
        Authentication authentication
    ) {
        return sync.save(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/sync-rules/{ruleId}/lifecycle")
    public SyncRule syncRuleLifecycle(
        @PathVariable UUID spaceId,
        @PathVariable UUID ruleId,
        @RequestBody SyncRuleLifecycleCommand command,
        Authentication authentication
    ) {
        sync.list(currentUser(authentication), spaceId);
        return sync.lifecycle(currentUser(authentication), ruleId, command);
    }

    @PostMapping("/sync-rules/{ruleId}/runs")
    public SyncRun executeSync(
        @PathVariable UUID spaceId,
        @PathVariable UUID ruleId,
        @RequestBody ExecuteSyncCommand command,
        Authentication authentication
    ) {
        sync.list(currentUser(authentication), spaceId);
        return sync.execute(currentUser(authentication), ruleId, command);
    }

    @GetMapping("/sync-runs/{runId}")
    public SyncRunDetail syncRun(
        @PathVariable UUID spaceId,
        @PathVariable UUID runId,
        Authentication authentication
    ) {
        sync.list(currentUser(authentication), spaceId);
        return sync.run(currentUser(authentication), runId);
    }

    @PostMapping("/sync-conflicts/{conflictId}/resolve")
    public SyncConflict resolveSyncConflict(
        @PathVariable UUID spaceId,
        @PathVariable UUID conflictId,
        @RequestBody ResolveConflictCommand command,
        Authentication authentication
    ) {
        sync.list(currentUser(authentication), spaceId);
        return sync.resolve(currentUser(authentication), conflictId, command);
    }

    @GetMapping("/panorama")
    public CrossTeamPanorama panorama(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return panorama.get(currentUser(authentication), spaceId);
    }

    @PostMapping("/panorama/preference")
    public PanoramaPreference savePanoramaPreference(
        @PathVariable UUID spaceId,
        @RequestBody SavePreferenceCommand command,
        Authentication authentication
    ) {
        return panorama.savePreference(currentUser(authentication), spaceId, command);
    }

    public record WithdrawRelationRequest(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String reason
    ) {
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
