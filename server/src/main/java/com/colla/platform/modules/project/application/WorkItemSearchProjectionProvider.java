package com.colla.platform.modules.project.application;

import com.colla.platform.modules.platform.contract.PlatformSearchProjectionProvider;
import com.colla.platform.modules.platform.contract.PlatformSearchProjectionProvider.SearchDocument;
import com.colla.platform.modules.project.application.WorkItemPermissionDecisionService.ContextDecisionInput;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter.EvaluationContext;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkItemSearchProjectionProvider implements PlatformSearchProjectionProvider {
    private final WorkItemRepository repository;
    private final ProjectSpaceRepository spaces;
    private final PublishedSnapshotAdapter snapshots;
    private final WorkItemPermissionDecisionService decisions;

    public WorkItemSearchProjectionProvider(
        WorkItemRepository repository,
        ProjectSpaceRepository spaces,
        PublishedSnapshotAdapter snapshots,
        WorkItemPermissionDecisionService decisions
    ) {
        this.repository = repository;
        this.spaces = spaces;
        this.snapshots = snapshots;
        this.decisions = decisions;
    }

    @Override
    public String objectType() {
        return "work_item";
    }

    @Override
    public Optional<SearchDocument> findDocument(UUID workspaceId, UUID objectId) {
        UUID spaceId = repository.findSpaceId(workspaceId, objectId).orElse(null);
        return repository.find(workspaceId, spaceId, objectId)
            .filter(item -> !"archived".equals(item.status()))
            .map(WorkItemSearchProjectionProvider::document);
    }

    @Override
    public List<SearchDocument> listDocuments(UUID workspaceId, UUID afterId, int limit) {
        return repository.listForSearchRebuild(workspaceId, afterId, Math.min(Math.max(limit, 1), 250))
            .stream()
            .map(WorkItemSearchProjectionProvider::document)
            .toList();
    }

    @Override
    public Set<UUID> allowed(
        CurrentUser user,
        List<UUID> objectIds,
        Set<String> requiredParticipantRoles
    ) {
        if (objectIds == null || objectIds.isEmpty()) return Set.of();
        if (objectIds.size() > MAX_DECISION_BATCH_SIZE) {
            throw new IllegalArgumentException("Search decision batch exceeds 200 items");
        }
        Set<String> safeRequiredRoles = requiredParticipantRoles == null ? Set.of() : Set.copyOf(requiredParticipantRoles);
        Map<UUID, com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary> visibleSpaces =
            spaces.listVisible(user.workspaceId(), user.id()).stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.id(), value -> value));
        List<UUID> preparedIds = new ArrayList<>();
        List<ContextDecisionInput> inputs = new ArrayList<>();
        for (UUID workItemId : new LinkedHashSet<>(objectIds)) {
            UUID spaceId = repository.findSpaceId(user.workspaceId(), workItemId).orElse(null);
            var space = spaceId == null ? null : visibleSpaces.get(spaceId);
            var item = spaceId == null ? null : repository.find(user.workspaceId(), spaceId, workItemId).orElse(null);
            if (space == null || item == null || "archived".equals(item.status())) continue;
            var participants = repository.listParticipants(user.workspaceId(), spaceId, workItemId);
            Set<String> participantRoles = participants.stream()
                .filter(value -> value.userId().equals(user.id()))
                .map(value -> value.role().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!safeRequiredRoles.isEmpty() && java.util.Collections.disjoint(participantRoles, safeRequiredRoles)) {
                continue;
            }
            LinkedHashSet<String> workItemRoles = new LinkedHashSet<>(participantRoles);
            if (item.createdBy().equals(user.id())) workItemRoles.add("creator");
            SubjectContext subject = new SubjectContext(
                user.workspaceId(),
                user.id(),
                item.version(),
                user.roles().stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()),
                Set.of(space.currentUserRole()),
                workItemRoles,
                participantRoles
            );
            Map<String, String> fields = new HashMap<>();
            item.fieldValues().fields().forEachRemaining(entry -> {
                if (entry.getValue().isValueNode()) fields.put(entry.getKey(), entry.getValue().asText());
            });
            EvaluationContext context = new EvaluationContext(
                item.id(),
                item.createdBy(),
                participants.stream().map(value -> value.userId()).collect(java.util.stream.Collectors.toSet()),
                workItemRoles,
                Map.copyOf(fields),
                null,
                null,
                null
            );
            var configuration = snapshots.requireComplete(
                user.workspaceId(), spaceId, item.typeDefinitionId(), item.typeVersionId()
            );
            if (!configuration.configHash().equals(item.configHash())) continue;
            preparedIds.add(workItemId);
            inputs.add(new ContextDecisionInput(configuration, subject, spaceId, workItemId, "view", context));
        }
        if (inputs.isEmpty()) return Set.of();
        var results = decisions.decideContextBatch(inputs);
        LinkedHashSet<UUID> allowed = new LinkedHashSet<>();
        for (int index = 0; index < results.size(); index++) {
            if (results.get(index).allowed()) allowed.add(preparedIds.get(index));
        }
        return Set.copyOf(allowed);
    }

    private static SearchDocument document(WorkItem item) {
        String title = item.displayKey() + " " + item.title();
        return new SearchDocument(
            "work_item",
            item.id(),
            title,
            item.title().substring(0, Math.min(item.title().length(), 240)),
            "/project-spaces/" + item.spaceId() + "/work-items/" + item.id(),
            "colla://work-item/" + item.id(),
            title + " " + item.typeKey() + " " + item.typeName(),
            item.updatedAt(),
            item.spaceId(),
            item.typeKey(),
            item.status(),
            item.version()
        );
    }
}
