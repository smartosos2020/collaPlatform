package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeBaseSpaceRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionEntry;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionGrant;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionRequest;
import com.colla.platform.modules.permission.infrastructure.ResourcePermissionRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResourcePermissionManagementService {
    private static final Set<String> MANAGED_RESOURCE_TYPES = Set.of("knowledge_content", "base", "project", "knowledge_base");
    private static final Set<String> SUBJECT_TYPES = Set.of("user", "department", "user_group", "role");

    private final ResourcePermissionRepository resourcePermissionRepository;
    private final PermissionDecisionService permissionDecisionService;
    private final KnowledgeBaseSpaceRepository knowledgeBaseSpaceRepository;
    private final KnowledgeContentRepository contentRepository;
    private final DomainEventRepository eventRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public ResourcePermissionManagementService(
        ResourcePermissionRepository resourcePermissionRepository,
        PermissionDecisionService permissionDecisionService,
        KnowledgeBaseSpaceRepository knowledgeBaseSpaceRepository,
        KnowledgeContentRepository contentRepository,
        DomainEventRepository eventRepository,
        AuditService auditService,
        JdbcTemplate jdbcTemplate
    ) {
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.permissionDecisionService = permissionDecisionService;
        this.knowledgeBaseSpaceRepository = knowledgeBaseSpaceRepository;
        this.contentRepository = contentRepository;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ResourcePermissionEntry> list(CurrentUser currentUser, String resourceType, UUID resourceId) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        requireManage(currentUser, normalizedResourceType, resourceId);
        return resourcePermissionRepository.listGrants(currentUser.workspaceId(), normalizedResourceType, resourceId);
    }

    public List<ResourcePermissionRequest> listRequests(CurrentUser currentUser, String resourceType, UUID resourceId, String status) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        requireManage(currentUser, normalizedResourceType, resourceId);
        String normalizedStatus = normalizeRequestStatus(status, true);
        return jdbcTemplate.query(
            """
                select r.id, r.resource_type, r.resource_id, r.requester_id,
                       coalesce(requester.display_name, requester.username) requester_name,
                       r.permission_level, r.reason, r.status, r.decided_by,
                       coalesce(decider.display_name, decider.username) decided_by_name,
                       r.decided_at, r.decision_note, r.created_at, r.updated_at
                from resource_permission_requests r
                join users requester on requester.id = r.requester_id and requester.workspace_id = r.workspace_id
                left join users decider on decider.id = r.decided_by and decider.workspace_id = r.workspace_id
                where r.workspace_id = ?
                  and r.resource_type = ?
                  and r.resource_id = ?
                  and (?::varchar is null or r.status = ?)
                order by
                  case r.status when 'submitted' then 0 when 'approved' then 1 else 2 end,
                  r.created_at desc
                """,
            this::mapRequest,
            currentUser.workspaceId(),
            normalizedResourceType,
            resourceId,
            normalizedStatus,
            normalizedStatus
        );
    }

    @Transactional
    public ResourcePermissionEntry grant(
        CurrentUser currentUser,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        Instant expiresAt,
        boolean confirmHighRisk
    ) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        String normalizedSubjectType = normalizeSubjectType(subjectType);
        String normalizedPermission = permissionDecisionService.requiredLevel(permissionLevel);
        requireManage(currentUser, normalizedResourceType, resourceId);
        if (!resourcePermissionRepository.subjectExists(currentUser.workspaceId(), normalizedSubjectType, subjectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission subject not found");
        }
        boolean highRisk = isHighRisk(normalizedPermission);
        if (highRisk && !confirmHighRisk) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "High-risk resource permission change requires confirmation");
        }
        resourcePermissionRepository.upsertGrant(new ResourcePermissionGrant(
            currentUser.workspaceId(),
            normalizedResourceType,
            resourceId,
            normalizedSubjectType,
            subjectId,
            normalizedPermission,
            "direct",
            null,
            expiresAt,
            currentUser.id()
        ));
        if ("knowledge_base".equals(normalizedResourceType)) {
            KnowledgeBaseSpaceSummary space = findKnowledgeBase(currentUser.workspaceId(), resourceId);
            int propagated = propagateKnowledgeBaseGrant(
                currentUser.workspaceId(),
                space.rootItemId(),
                space.id(),
                normalizedSubjectType,
                subjectId,
                normalizedPermission,
                expiresAt,
                currentUser.id()
            );
            auditService.log(
                currentUser,
                "resource.permission.inherited.propagated",
                "knowledge_base",
                resourceId,
                Map.of("documentCount", propagated, "subjectType", normalizedSubjectType, "subjectId", subjectId.toString())
            );
        }
        auditService.log(
            currentUser,
            "resource.permission.granted",
            normalizedResourceType,
            resourceId,
            Map.of(
                "subjectType", normalizedSubjectType,
                "subjectId", subjectId,
                "permissionLevel", normalizedPermission,
                "highRisk", highRisk
            )
        );
        return resourcePermissionRepository.listGrants(currentUser.workspaceId(), normalizedResourceType, resourceId).stream()
            .filter(entry -> entry.subjectType().equals(normalizedSubjectType) && entry.subjectId().equals(subjectId))
            .findFirst()
            .orElseThrow();
    }

    @Transactional
    public void revoke(CurrentUser currentUser, UUID permissionId, boolean confirmHighRisk) {
        ResourcePermissionEntry entry = resourcePermissionRepository.findGrant(currentUser.workspaceId(), permissionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource permission not found"));
        requireManage(currentUser, entry.resourceType(), entry.resourceId());
        if (!"direct".equals(entry.sourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inherited permission cannot be revoked on child resource");
        }
        boolean highRisk = isHighRisk(entry.permissionLevel());
        if (highRisk && !confirmHighRisk) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "High-risk resource permission change requires confirmation");
        }
        if (!resourcePermissionRepository.revokeGrant(currentUser.workspaceId(), permissionId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource permission not found");
        }
        int revokedInherited = 0;
        if ("knowledge_base".equals(entry.resourceType())) {
            KnowledgeBaseSpaceSummary space = findKnowledgeBase(currentUser.workspaceId(), entry.resourceId());
            revokedInherited = revokeKnowledgeBaseInheritedGrants(
                currentUser.workspaceId(),
                space.rootItemId(),
                space.id(),
                entry.subjectType(),
                entry.subjectId(),
                currentUser.id()
            );
        }
        auditService.log(
            currentUser,
            "resource.permission.revoked",
            entry.resourceType(),
            entry.resourceId(),
            Map.of(
                "permissionId", permissionId,
                "subjectType", entry.subjectType(),
                "subjectId", entry.subjectId(),
                "permissionLevel", entry.permissionLevel(),
                "highRisk", highRisk,
                "revokedInherited", revokedInherited
            )
        );
    }

    @Transactional
    public void breakInheritance(CurrentUser currentUser, String resourceType, UUID resourceId, boolean confirmHighRisk) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        if (!"knowledge_content".equals(normalizedResourceType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only knowledge content permission inheritance can be broken");
        }
        if (!confirmHighRisk) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Breaking permission inheritance requires confirmation");
        }
        requireManage(currentUser, normalizedResourceType, resourceId);
        int revoked = jdbcTemplate.update(
            """
                update resource_permissions
                set status = 'revoked',
                    updated_by = ?,
                    updated_at = now()
                where workspace_id = ?
                  and resource_type = 'knowledge_content'
                  and resource_id = ?
                  and source_type = 'inherited'
                  and status = 'active'
                """,
            currentUser.id(),
            currentUser.workspaceId(),
            resourceId
        );
        auditService.log(
            currentUser,
            "resource.permission.inheritance.broken",
            "knowledge_content",
            resourceId,
            Map.of("revokedInherited", revoked)
        );
    }

    @Transactional
    public void restoreInheritance(CurrentUser currentUser, String resourceType, UUID resourceId) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        if (!"knowledge_content".equals(normalizedResourceType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only knowledge content permission inheritance can be restored");
        }
        requireManage(currentUser, normalizedResourceType, resourceId);
        KnowledgeBaseItem item = contentRepository.findItem(currentUser.workspaceId(), resourceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"));
        int restored;
        if (item.parentId() != null) {
            restored = restoreItemInheritanceFromParent(
                currentUser.workspaceId(),
                resourceId,
                item.parentId(),
                currentUser.id()
            );
        } else {
            KnowledgeBaseSpaceSummary space = knowledgeBaseSpaceRepository
                .findSpaceByRootItemId(currentUser.workspaceId(), resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Root item is not a knowledge base root"));
            restored = restoreRootInheritanceFromKnowledgeBase(
                currentUser.workspaceId(),
                resourceId,
                space.id(),
                currentUser.id()
            );
        }
        auditService.log(
            currentUser,
            "resource.permission.inheritance.restored",
            "knowledge_content",
            resourceId,
            Map.of("restoredInherited", restored)
        );
    }

    @Transactional
    public ResourcePermissionRequest requestPermission(
        CurrentUser currentUser,
        String resourceType,
        UUID resourceId,
        String permissionLevel,
        String reason
    ) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        String normalizedPermission = permissionDecisionService.requiredLevel(permissionLevel);
        verifyRequestableResource(currentUser, normalizedResourceType, resourceId);
        String normalizedReason = normalizeNullableText(reason, 512);
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into resource_permission_requests
                    (id, workspace_id, resource_type, resource_id, requester_id, permission_level, reason,
                     status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'submitted', now(), now())
                """,
            requestId,
            currentUser.workspaceId(),
            normalizedResourceType,
            resourceId,
            currentUser.id(),
            normalizedPermission,
            normalizedReason
        );
        List<UUID> managers = findManagerUserIds(currentUser.workspaceId(), normalizedResourceType, resourceId).stream()
            .filter(userId -> !userId.equals(currentUser.id()))
            .distinct()
            .toList();
        for (UUID managerId : managers) {
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                normalizedResourceType,
                resourceId,
                currentUser.id(),
                Map.of(
                    "recipientId", managerId.toString(),
                    "notificationType", "resource_permission_request",
                    "title", currentUser.displayName() + " 申请访问资源",
                    "body", permissionRequestBody(normalizedPermission, normalizedReason),
                    "targetType", normalizedResourceType,
                    "targetId", resourceId.toString(),
                    "webPath", webPath(currentUser.workspaceId(), normalizedResourceType, resourceId),
                    "dedupeKey", "resource.permission_request:" + requestId + ":" + managerId
                ),
                "notification.resource.permission_request:" + requestId + ":" + managerId
            );
        }
        auditService.log(
            currentUser,
            "resource.permission.requested",
            normalizedResourceType,
            resourceId,
            Map.of(
                "requestId", requestId.toString(),
                "permissionLevel", normalizedPermission,
                "notifiedCount", managers.size()
            )
        );
        return findPermissionRequest(currentUser.workspaceId(), requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Permission request was not created"));
    }

    @Transactional
    public ResourcePermissionRequest approveRequest(CurrentUser currentUser, UUID requestId, String note) {
        ResourcePermissionRequest request = findSubmittedPermissionRequest(currentUser.workspaceId(), requestId);
        requireManage(currentUser, request.resourceType(), request.resourceId());
        String normalizedNote = normalizeNullableText(note, 512);
        jdbcTemplate.update(
            """
                update resource_permission_requests
                set status = 'approved',
                    decided_by = ?,
                    decided_at = now(),
                    decision_note = ?,
                    updated_at = now()
                where workspace_id = ? and id = ? and status = 'submitted'
                """,
            currentUser.id(),
            normalizedNote,
            currentUser.workspaceId(),
            requestId
        );
        grant(
            currentUser,
            request.resourceType(),
            request.resourceId(),
            "user",
            request.requesterId(),
            request.permissionLevel(),
            null,
            true
        );
        notifyRequester(currentUser, request, "resource_permission_request_approved", "访问申请已通过");
        auditService.log(
            currentUser,
            "resource.permission.request.approved",
            request.resourceType(),
            request.resourceId(),
            Map.of("requestId", requestId.toString(), "requesterId", request.requesterId().toString())
        );
        return findPermissionRequest(currentUser.workspaceId(), requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource permission request not found"));
    }

    @Transactional
    public ResourcePermissionRequest rejectRequest(CurrentUser currentUser, UUID requestId, String note) {
        ResourcePermissionRequest request = findSubmittedPermissionRequest(currentUser.workspaceId(), requestId);
        requireManage(currentUser, request.resourceType(), request.resourceId());
        String normalizedNote = normalizeNullableText(note, 512);
        jdbcTemplate.update(
            """
                update resource_permission_requests
                set status = 'rejected',
                    decided_by = ?,
                    decided_at = now(),
                    decision_note = ?,
                    updated_at = now()
                where workspace_id = ? and id = ? and status = 'submitted'
                """,
            currentUser.id(),
            normalizedNote,
            currentUser.workspaceId(),
            requestId
        );
        notifyRequester(currentUser, request, "resource_permission_request_rejected", "访问申请已拒绝");
        auditService.log(
            currentUser,
            "resource.permission.request.rejected",
            request.resourceType(),
            request.resourceId(),
            Map.of("requestId", requestId.toString(), "requesterId", request.requesterId().toString())
        );
        return findPermissionRequest(currentUser.workspaceId(), requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource permission request not found"));
    }

    private int propagateKnowledgeBaseGrant(
        UUID workspaceId,
        UUID rootItemId,
        UUID spaceId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        Instant expiresAt,
        UUID actorId
    ) {
        int updated = jdbcTemplate.update(
            """
                with recursive subtree as (
                    select d.id
                    from knowledge_base_items d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select child.id
                    from knowledge_base_items child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                update resource_permissions rp
                set permission_level = ?,
                    expires_at = ?,
                    status = 'active',
                    updated_by = ?,
                    updated_at = now()
                from subtree
                where rp.workspace_id = ?
                  and rp.resource_type = 'knowledge_content'
                  and rp.resource_id = subtree.id
                  and rp.subject_type = ?
                  and rp.subject_id = ?
                  and rp.source_type = 'inherited'
                  and rp.source_id = ?
                  and rp.status = 'active'
                """,
            workspaceId,
            rootItemId,
            workspaceId,
            permissionLevel,
            expiresAt,
            actorId,
            workspaceId,
            subjectType,
            subjectId,
            spaceId
        );
        int inserted = jdbcTemplate.update(
            """
                with recursive subtree as (
                    select d.id
                    from knowledge_base_items d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select child.id
                    from knowledge_base_items child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                insert into resource_permissions
                    (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
                     source_type, source_id, expires_at, status, created_by, created_at, updated_by, updated_at)
                select gen_random_uuid(), ?, 'knowledge_content', subtree.id, ?, ?, ?, 'inherited', ?, ?, 'active', ?, now(), ?, now()
                from subtree
                where not exists (
                    select 1
                    from resource_permissions existing
                    where existing.workspace_id = ?
                      and existing.resource_type = 'knowledge_content'
                      and existing.resource_id = subtree.id
                      and existing.subject_type = ?
                      and existing.subject_id = ?
                      and existing.status = 'active'
                )
                """,
            workspaceId,
            rootItemId,
            workspaceId,
            workspaceId,
            subjectType,
            subjectId,
            permissionLevel,
            spaceId,
            expiresAt,
            actorId,
            actorId,
            workspaceId,
            subjectType,
            subjectId
        );
        return updated + inserted;
    }

    private int revokeKnowledgeBaseInheritedGrants(
        UUID workspaceId,
        UUID rootItemId,
        UUID spaceId,
        String subjectType,
        UUID subjectId,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                with recursive subtree as (
                    select d.id
                    from knowledge_base_items d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select child.id
                    from knowledge_base_items child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                update resource_permissions rp
                set status = 'revoked',
                    updated_by = ?,
                    updated_at = now()
                from subtree
                where rp.workspace_id = ?
                  and rp.resource_type = 'knowledge_content'
                  and rp.resource_id = subtree.id
                  and rp.subject_type = ?
                  and rp.subject_id = ?
                  and rp.source_type = 'inherited'
                  and rp.source_id = ?
                  and rp.status = 'active'
                """,
            workspaceId,
            rootItemId,
            workspaceId,
            actorId,
            workspaceId,
            subjectType,
            subjectId,
            spaceId
        );
    }

    private int restoreItemInheritanceFromParent(UUID workspaceId, UUID itemId, UUID parentId, UUID actorId) {
        return jdbcTemplate.update(
            """
                insert into resource_permissions
                    (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
                     source_type, source_id, expires_at, status, created_by, created_at, updated_by, updated_at)
                select gen_random_uuid(), ?, 'knowledge_content', ?, parent.subject_type, parent.subject_id, parent.permission_level,
                       'inherited', ?, parent.expires_at, 'active', ?, now(), ?, now()
                from resource_permissions parent
                where parent.workspace_id = ?
                  and parent.resource_type = 'knowledge_content'
                  and parent.resource_id = ?
                  and parent.status = 'active'
                  and (parent.expires_at is null or parent.expires_at > now())
                  and not exists (
                      select 1
                      from resource_permissions existing
                      where existing.workspace_id = parent.workspace_id
                        and existing.resource_type = 'knowledge_content'
                        and existing.resource_id = ?
                        and existing.subject_type = parent.subject_type
                        and existing.subject_id = parent.subject_id
                        and existing.status = 'active'
                  )
                """,
            workspaceId,
            itemId,
            parentId,
            actorId,
            actorId,
            workspaceId,
            parentId,
            itemId
        );
    }

    private int restoreRootInheritanceFromKnowledgeBase(UUID workspaceId, UUID rootItemId, UUID spaceId, UUID actorId) {
        return jdbcTemplate.update(
            """
                insert into resource_permissions
                    (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
                     source_type, source_id, expires_at, status, created_by, created_at, updated_by, updated_at)
                select gen_random_uuid(), ?, 'knowledge_content', ?, kb.subject_type, kb.subject_id, kb.permission_level,
                       'inherited', ?, kb.expires_at, 'active', ?, now(), ?, now()
                from resource_permissions kb
                where kb.workspace_id = ?
                  and kb.resource_type = 'knowledge_base'
                  and kb.resource_id = ?
                  and kb.status = 'active'
                  and (kb.expires_at is null or kb.expires_at > now())
                  and not exists (
                      select 1
                      from resource_permissions existing
                      where existing.workspace_id = kb.workspace_id
                        and existing.resource_type = 'knowledge_content'
                        and existing.resource_id = ?
                        and existing.subject_type = kb.subject_type
                        and existing.subject_id = kb.subject_id
                        and existing.status = 'active'
                  )
                """,
            workspaceId,
            rootItemId,
            spaceId,
            actorId,
            actorId,
            workspaceId,
            spaceId,
            rootItemId
        );
    }

    private List<UUID> findManagerUserIds(UUID workspaceId, String resourceType, UUID resourceId) {
        if ("knowledge_content".equals(resourceType)) {
            return contentRepository.findItemManagerUserIds(workspaceId, resourceId);
        }
        if (!"knowledge_base".equals(resourceType)) {
            return List.of();
        }
        return jdbcTemplate.query(
            """
                with manager_permissions as (
                    select 'user' subject_type, k.owner_id subject_id
                    from knowledge_base_spaces k
                    where k.workspace_id = ? and k.id = ? and k.deleted_at is null
                    union all
                    select rp.subject_type, rp.subject_id
                    from resource_permissions rp
                    where rp.workspace_id = ?
                      and rp.resource_type = 'knowledge_base'
                      and rp.resource_id = ?
                      and rp.permission_level in ('owner', 'manage')
                      and rp.status = 'active'
                      and (rp.expires_at is null or rp.expires_at > now())
                )
                select distinct user_id
                from (
                    select mp.subject_id user_id
                    from manager_permissions mp
                    join users u on mp.subject_type = 'user' and u.id = mp.subject_id and u.workspace_id = ? and u.deleted_at is null
                    union all
                    select dm.user_id
                    from manager_permissions mp
                    join department_members dm on mp.subject_type = 'department'
                        and dm.workspace_id = ?
                        and dm.department_id = mp.subject_id
                        and dm.ended_at is null
                    union all
                    select ugm.subject_id
                    from manager_permissions mp
                    join user_group_members ugm on mp.subject_type = 'user_group'
                        and ugm.workspace_id = ?
                        and ugm.group_id = mp.subject_id
                        and ugm.subject_type = 'user'
                        and ugm.removed_at is null
                    union all
                    select dm.user_id
                    from manager_permissions mp
                    join user_group_members ugm on mp.subject_type = 'user_group'
                        and ugm.workspace_id = ?
                        and ugm.group_id = mp.subject_id
                        and ugm.subject_type = 'department'
                        and ugm.removed_at is null
                    join department_members dm on dm.workspace_id = ugm.workspace_id
                        and dm.department_id = ugm.subject_id
                        and dm.ended_at is null
                    union all
                    select ur.user_id
                    from manager_permissions mp
                    join user_roles ur on mp.subject_type = 'role'
                        and ur.workspace_id = ?
                        and ur.role_id = mp.subject_id
                ) expanded
                """,
            (rs, rowNum) -> rs.getObject("user_id", UUID.class),
            workspaceId,
            resourceId,
            workspaceId,
            resourceId,
            workspaceId,
            workspaceId,
            workspaceId,
            workspaceId,
            workspaceId
        );
    }

    private void verifyRequestableResource(CurrentUser currentUser, String resourceType, UUID resourceId) {
        if ("knowledge_base".equals(resourceType)) {
            findKnowledgeBase(currentUser.workspaceId(), resourceId);
            return;
        }
        if ("knowledge_content".equals(resourceType)) {
            contentRepository.findItem(currentUser.workspaceId(), resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge content not found"));
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource access request is not supported for this type");
    }

    private KnowledgeBaseSpaceSummary findKnowledgeBase(UUID workspaceId, UUID spaceId) {
        return knowledgeBaseSpaceRepository.findSpace(workspaceId, spaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found"));
    }

    private ResourcePermissionRequest findSubmittedPermissionRequest(UUID workspaceId, UUID requestId) {
        ResourcePermissionRequest request = findPermissionRequest(workspaceId, requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource permission request not found"));
        if (!"submitted".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource permission request is already decided");
        }
        return request;
    }

    private java.util.Optional<ResourcePermissionRequest> findPermissionRequest(UUID workspaceId, UUID requestId) {
        List<ResourcePermissionRequest> requests = jdbcTemplate.query(
            """
                select r.id, r.resource_type, r.resource_id, r.requester_id,
                       coalesce(requester.display_name, requester.username) requester_name,
                       r.permission_level, r.reason, r.status, r.decided_by,
                       coalesce(decider.display_name, decider.username) decided_by_name,
                       r.decided_at, r.decision_note, r.created_at, r.updated_at
                from resource_permission_requests r
                join users requester on requester.id = r.requester_id and requester.workspace_id = r.workspace_id
                left join users decider on decider.id = r.decided_by and decider.workspace_id = r.workspace_id
                where r.workspace_id = ? and r.id = ?
                """,
            this::mapRequest,
            workspaceId,
            requestId
        );
        return requests.stream().findFirst();
    }

    private void notifyRequester(
        CurrentUser currentUser,
        ResourcePermissionRequest request,
        String notificationType,
        String title
    ) {
        eventRepository.append(
            currentUser.workspaceId(),
            "notification.created",
            request.resourceType(),
            request.resourceId(),
            currentUser.id(),
            Map.of(
                "recipientId", request.requesterId().toString(),
                "notificationType", notificationType,
                "title", title,
                "body", "权限：" + request.permissionLevel(),
                "targetType", request.resourceType(),
                "targetId", request.resourceId().toString(),
                "webPath", webPath(currentUser.workspaceId(), request.resourceType(), request.resourceId()),
                "dedupeKey", "resource.permission_request.decision:" + request.id()
            ),
            "notification.resource.permission_request.decision:" + request.id()
        );
    }

    private ResourcePermissionRequest mapRequest(ResultSet rs, int rowNum) throws SQLException {
        return new ResourcePermissionRequest(
            rs.getObject("id", UUID.class),
            rs.getString("resource_type"),
            rs.getObject("resource_id", UUID.class),
            rs.getObject("requester_id", UUID.class),
            rs.getString("requester_name"),
            rs.getString("permission_level"),
            rs.getString("reason"),
            rs.getString("status"),
            rs.getObject("decided_by", UUID.class),
            rs.getString("decided_by_name"),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
            rs.getString("decision_note"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private void requireManage(CurrentUser currentUser, String resourceType, UUID resourceId) {
        if (currentUser.hasRole("admin")) {
            return;
        }
        PermissionDecision decision = permissionDecisionService.decide(currentUser, resourceType, resourceId, "manage");
        permissionDecisionService.requireAllowed(decision);
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim().toLowerCase();
        if ("kb".equals(normalized) || "knowledge-base".equals(normalized) || "knowledgebase".equals(normalized)) {
            normalized = "knowledge_base";
        }
        if (!MANAGED_RESOURCE_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported managed resource type");
        }
        return normalized;
    }

    private String normalizeSubjectType(String subjectType) {
        String normalized = permissionDecisionService.normalizeSubjectType(subjectType);
        if (!SUBJECT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resource permission subject type");
        }
        return normalized;
    }

    private String normalizeRequestStatus(String status, boolean allowBlank) {
        if (status == null || status.isBlank()) {
            return allowBlank ? null : "submitted";
        }
        String normalized = status.trim().toLowerCase();
        if (!Set.of("submitted", "approved", "rejected").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resource permission request status");
        }
        return normalized;
    }

    private String normalizeNullableText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String permissionRequestBody(String permissionLevel, String reason) {
        if (reason == null || reason.isBlank()) {
            return "申请权限：" + permissionLevel;
        }
        return "申请权限：" + permissionLevel + "；原因：" + reason;
    }

    private String webPath(UUID workspaceId, String resourceType, UUID resourceId) {
        return switch (resourceType) {
            case "knowledge_content" -> knowledgeBaseSpaceRepository.findSpaceByItemId(workspaceId, resourceId)
                .map(space -> "/knowledge-bases/" + space.id() + "/items/" + resourceId)
                .orElse("/knowledge-bases");
            case "knowledge_base" -> "/knowledge-bases/" + resourceId;
            default -> "/";
        };
    }

    private boolean isHighRisk(String permissionLevel) {
        return List.of("manage", "owner").contains(permissionLevel);
    }
}
