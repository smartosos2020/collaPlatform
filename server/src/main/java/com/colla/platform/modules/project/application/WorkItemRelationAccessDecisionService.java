package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationCapabilities;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter.EvaluationContext;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemRelationAccessDecisionService {
    private final ProjectSpaceRepository spaceRepository;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemPermissionDecisionService permissionDecisionService;

    public WorkItemRelationAccessDecisionService(
        ProjectSpaceRepository spaceRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemPermissionDecisionService permissionDecisionService
    ) {
        this.spaceRepository = spaceRepository;
        this.snapshotAdapter = snapshotAdapter;
        this.permissionDecisionService = permissionDecisionService;
    }

    public void requireAction(
        CurrentUser user,
        UUID spaceId,
        WorkItem item,
        String action,
        String relationKey
    ) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        var configuration = snapshotAdapter.requireComplete(
            user.workspaceId(),
            spaceId,
            item.typeDefinitionId(),
            item.typeVersionId()
        );
        if (!configuration.configHash().equals(item.configHash())) {
            throw failure("SNAPSHOT_INTEGRITY_FAILURE", "Relation endpoint permission binding is invalid");
        }
        Set<String> workItemRoles = item.createdBy().equals(user.id())
            ? Set.of("creator") : Set.of();
        SubjectContext subject = new SubjectContext(
            user.workspaceId(),
            user.id(),
            item.version(),
            user.roles().stream()
                .map(role -> role.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet()),
            Set.of(space.currentUserRole()),
            workItemRoles,
            Set.of()
        );
        permissionDecisionService.require(permissionDecisionService.decide(
            configuration,
            subject,
            spaceId,
            item.id(),
            action,
            evaluationContext(user, item, relationKey)
        ));
    }

    private EvaluationContext evaluationContext(
        CurrentUser user,
        WorkItem item,
        String relationKey
    ) {
        Set<String> roles = item.createdBy().equals(user.id()) ? Set.of("creator") : Set.of();
        Set<UUID> participants = item.createdBy().equals(user.id())
            ? Set.of(user.id()) : Set.of();
        var values = new HashMap<String, String>();
        item.fieldValues().fields().forEachRemaining(entry -> {
            if (entry.getValue().isValueNode()) {
                values.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return new EvaluationContext(
            item.id(),
            item.createdBy(),
            participants,
            roles,
            Map.copyOf(values),
            null,
            null,
            relationKey
        );
    }

    public ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project space is not available"));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    public ProjectSpaceSummary requireWritable(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if ("guest".equals(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Guest project space members have read-only relation access");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
        return space;
    }

    public ProjectSpaceSummary requireManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireWritable(user, spaceId);
        if (!"owner".equals(space.currentUserRole())
            && !"admin".equals(space.currentUserRole())) {
            throw failure(
                "FORBIDDEN",
                "Hierarchy recovery requires a project space owner or administrator"
            );
        }
        return space;
    }

    public RelationCapabilities capabilities(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        WorkItem source,
        WorkItem target,
        String relationStatus
    ) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        List<String> reasons = new ArrayList<>();
        boolean writableRole = !"guest".equals(space.currentUserRole());
        if (!writableRole) {
            reasons.add("read_only_role");
        }
        if (!"active".equals(space.status())) {
            reasons.add("space_not_active");
        }
        if (source != null && !"active".equals(source.status())) {
            reasons.add("source_not_active");
        }
        if (target != null && !"active".equals(target.status())) {
            reasons.add("target_not_active");
        }
        boolean endpointsActive = (source == null || "active".equals(source.status()))
            && (target == null || "active".equals(target.status()));
        boolean canMutate = writableRole && "active".equals(space.status());
        return new RelationCapabilities(
            relationKey,
            true,
            canMutate && endpointsActive,
            canMutate && "active".equals(relationStatus),
            canMutate && endpointsActive && "withdrawn".equals(relationStatus),
            List.copyOf(reasons)
        );
    }
}
