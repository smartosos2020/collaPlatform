package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.ChangePreview;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.ImpactAnalysis;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.ImpactLink;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.ImpactNode;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.RelationGroup;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.RelationSummary;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.TargetCandidate;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.TargetPage;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationDefinitionBinding;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationPage;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationRepository.ImpactEdge;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.WorkItemRelationRuntimeAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkItemRelationExperienceService {
    private static final int MAX_TARGETS = 50;
    private static final int MAX_RELATIONS = 200;
    private static final int MAX_IMPACT_NODES = 200;
    private static final int MAX_IMPACT_DEPTH = 32;

    private final WorkItemRepository workItemRepository;
    private final WorkItemRelationRepository relationRepository;
    private final WorkItemRelationRuntimeAdapter runtimeAdapter;
    private final WorkItemRelationAccessDecisionService accessDecision;
    private final WorkItemRelationService relationService;

    public WorkItemRelationExperienceService(
        WorkItemRepository workItemRepository,
        WorkItemRelationRepository relationRepository,
        WorkItemRelationRuntimeAdapter runtimeAdapter,
        WorkItemRelationAccessDecisionService accessDecision,
        WorkItemRelationService relationService
    ) {
        this.workItemRepository = workItemRepository;
        this.relationRepository = relationRepository;
        this.runtimeAdapter = runtimeAdapter;
        this.accessDecision = accessDecision;
        this.relationService = relationService;
    }

    public TargetPage targets(
        CurrentUser user,
        UUID spaceId,
        UUID sourceWorkItemId,
        String relationKey,
        String query,
        UUID cursor,
        int limit
    ) {
        accessDecision.requireVisible(user, spaceId);
        WorkItem source = requireItem(user, spaceId, sourceWorkItemId);
        RelationDefinitionBinding binding = runtimeAdapter.requireForSource(
            source, key(relationKey)
        );
        int safeLimit = Math.max(1, Math.min(limit, MAX_TARGETS));
        List<WorkItem> rows = workItemRepository.searchRelationTargets(
            user.workspaceId(),
            spaceId,
            binding.targetTypeKeys(),
            query,
            cursor,
            safeLimit + 1
        );
        rows = rows.stream().filter(item -> binding.allowSelf()
            || !item.id().equals(sourceWorkItemId)).toList();
        boolean truncated = rows.size() > safeLimit;
        List<WorkItem> visible = rows.stream().limit(safeLimit).toList();
        return new TargetPage(
            binding.relationKey(),
            visible.stream().map(this::candidate).toList(),
            truncated && !visible.isEmpty() ? visible.getLast().id() : null,
            truncated
        );
    }

    public RelationSummary summary(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        String relationKey,
        int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RELATIONS));
        RelationPage page = relationService.list(
            user, spaceId, workItemId, relationKey, null, safeLimit
        );
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        for (RelationView item : page.items()) {
            String groupKey = item.relationKey() + ":" + item.perspective();
            groups.computeIfAbsent(
                groupKey,
                ignored -> new MutableGroup(
                    item.relationKey(), item.perspective(), item.displayName()
                )
            ).count++;
        }
        boolean truncated = page.nextCursor() != null;
        List<RelationGroup> result = groups.values().stream()
            .map(group -> new RelationGroup(
                group.relationKey,
                group.perspective,
                group.displayName,
                group.count,
                truncated
            ))
            .toList();
        return new RelationSummary(
            workItemId,
            result,
            page.items(),
            truncated,
            calibration(workItemId, page.items())
        );
    }

    public ImpactAnalysis impact(
        CurrentUser user,
        UUID spaceId,
        UUID focusWorkItemId,
        String relationKey,
        String direction,
        int maxDepth,
        int limit
    ) {
        accessDecision.requireVisible(user, spaceId);
        WorkItem focus = requireItem(user, spaceId, focusWorkItemId);
        RelationDefinitionBinding binding = runtimeAdapter.requireForSource(
            focus, key(relationKey)
        );
        if (!Set.of("dependency", "blocking").contains(binding.kind().name())) {
            throw failure(
                "RELATION_IMPACT_UNSUPPORTED",
                "Impact analysis is only available for dependency and blocking relations"
            );
        }
        String safeDirection = "upstream".equals(direction) ? "upstream" : "downstream";
        int safeDepth = Math.max(
            1, Math.min(Math.min(maxDepth, binding.maxDepth()), MAX_IMPACT_DEPTH)
        );
        int safeLimit = Math.max(1, Math.min(limit, MAX_IMPACT_NODES));
        List<ImpactEdge> rows = relationRepository.listImpact(
            user.workspaceId(),
            spaceId,
            binding.relationKey(),
            focusWorkItemId,
            safeDirection,
            safeDepth,
            safeLimit + 1
        );
        boolean truncated = rows.size() > safeLimit;
        List<ImpactEdge> visible = rows.stream().limit(safeLimit).toList();
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(focusWorkItemId);
        visible.forEach(edge -> {
            ids.add(edge.sourceWorkItemId());
            ids.add(edge.targetWorkItemId());
        });
        List<ImpactNode> nodes = workItemRepository.findAll(
            user.workspaceId(), spaceId, List.copyOf(ids)
        ).stream().map(this::impactNode).toList();
        if (nodes.size() != ids.size()) {
            throw failure(
                "RELATION_IMPACT_CALIBRATION_REQUIRED",
                "Impact analysis changed while it was being read"
            );
        }
        return new ImpactAnalysis(
            focusWorkItemId,
            binding.relationKey(),
            safeDirection,
            safeDepth,
            nodes,
            visible.stream().map(edge -> new ImpactLink(
                edge.relationId(),
                edge.sourceWorkItemId(),
                edge.targetWorkItemId(),
                edge.depth()
            )).toList(),
            truncated,
            truncated ? "node_budget_reached" : null,
            calibration(focusWorkItemId, visible)
        );
    }

    public ChangePreview preview(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        long expectedSourceVersion,
        long expectedTargetVersion,
        String reason
    ) {
        WorkItem source = requireItem(user, spaceId, sourceWorkItemId);
        WorkItem target = requireItem(user, spaceId, targetWorkItemId);
        var capabilities = relationService.capabilities(
            user, spaceId, key(relationKey), sourceWorkItemId, targetWorkItemId
        );
        Map<String, Object> retained = new LinkedHashMap<>();
        retained.put("targetWorkItemId", targetWorkItemId);
        retained.put("expectedSourceVersion", expectedSourceVersion);
        retained.put("expectedTargetVersion", expectedTargetVersion);
        if (reason != null && !reason.isBlank()) {
            retained.put("reason", reason);
        }
        return new ChangePreview(
            capabilities.relationKey(),
            sourceWorkItemId,
            targetWorkItemId,
            source.version(),
            target.version(),
            source.version() != expectedSourceVersion || target.version() != expectedTargetVersion,
            capabilities.canCreate(),
            capabilities.denialReasons(),
            Map.copyOf(retained)
        );
    }

    private WorkItem requireItem(CurrentUser user, UUID spaceId, UUID workItemId) {
        return workItemRepository.find(user.workspaceId(), spaceId, workItemId)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Work item is not available"
            ));
    }

    private TargetCandidate candidate(WorkItem item) {
        return new TargetCandidate(
            item.id(), item.displayKey(), item.title(), item.typeKey(),
            item.typeName(), item.status(), item.version()
        );
    }

    private ImpactNode impactNode(WorkItem item) {
        return new ImpactNode(
            item.id(), item.displayKey(), item.title(), item.typeKey(),
            item.status(), item.version()
        );
    }

    private String key(String value) {
        String result = value == null ? "" : value.trim().toLowerCase();
        if (result.isBlank() || result.length() > 64) {
            throw failure("INVALID_RELATION_KEY", "Relation key is invalid");
        }
        return result;
    }

    private String calibration(UUID focusId, Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (focusId + ":" + value).getBytes(StandardCharsets.UTF_8)
            );
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class MutableGroup {
        private final String relationKey;
        private final String perspective;
        private final String displayName;
        private int count;

        private MutableGroup(String relationKey, String perspective, String displayName) {
            this.relationKey = relationKey;
            this.perspective = perspective;
            this.displayName = displayName;
        }
    }
}
