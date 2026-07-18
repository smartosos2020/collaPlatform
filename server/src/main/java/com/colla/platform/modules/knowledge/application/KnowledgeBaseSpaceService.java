package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.base.application.BaseService;
import com.colla.platform.modules.base.domain.BaseModels.BaseDetail;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentTemplate;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItemTreeNode;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeObjectReference;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentContext;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseAccessItemStat;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseAccessStats;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseBulkGovernanceResult;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseDiscovery;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseGovernanceDashboard;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseGovernanceRisk;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseHealthMetrics;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSearchTermStat;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSubscription;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeBaseItemRepository;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeBaseSpaceRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectReference;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.modules.permission.application.PermissionDecisionService;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeBaseSpaceService {
    private final KnowledgeBaseSpaceRepository knowledgeBaseSpaceRepository;
    private final KnowledgeBaseItemRepository itemRepository;
    private final PlatformObjectRepository objectRepository;
    private final KnowledgeContentService contentService;
    private final PermissionDecisionService permissionDecisionService;
    private final PlatformObjectResolverRegistry objectResolverRegistry;
    private final AuditService auditService;
    private final BaseService baseService;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBaseSpaceService(
        KnowledgeBaseSpaceRepository knowledgeBaseSpaceRepository,
        KnowledgeBaseItemRepository itemRepository,
        PlatformObjectRepository objectRepository,
        KnowledgeContentService contentService,
        PermissionDecisionService permissionDecisionService,
        PlatformObjectResolverRegistry objectResolverRegistry,
        AuditService auditService,
        BaseService baseService,
        JdbcTemplate jdbcTemplate
    ) {
        this.knowledgeBaseSpaceRepository = knowledgeBaseSpaceRepository;
        this.itemRepository = itemRepository;
        this.objectRepository = objectRepository;
        this.contentService = contentService;
        this.permissionDecisionService = permissionDecisionService;
        this.objectResolverRegistry = objectResolverRegistry;
        this.auditService = auditService;
        this.baseService = baseService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<KnowledgeBaseSpaceSummary> listSpaces(CurrentUser currentUser, boolean includeArchived) {
        knowledgeBaseSpaceRepository.backfillLegacySpaces(currentUser.workspaceId());
        return knowledgeBaseSpaceRepository.listSpaces(currentUser.workspaceId(), includeArchived).stream()
            .filter(space -> canView(currentUser, space))
            .toList();
    }

    @Transactional
    public KnowledgeBaseSpaceDetail getSpace(CurrentUser currentUser, UUID spaceId) {
        knowledgeBaseSpaceRepository.backfillLegacySpaces(currentUser.workspaceId());
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        KnowledgeBaseItem root = itemRepository.findItem(currentUser.workspaceId(), space.rootItemId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base root document not found"));
        KnowledgeBaseItem home = space.homeItemId() == null
            ? root
            : itemRepository.findItem(currentUser.workspaceId(), space.homeItemId()).orElse(root);
        return new KnowledgeBaseSpaceDetail(space, root, home);
    }

    public List<KnowledgeBaseItem> listItems(CurrentUser currentUser, UUID spaceId, boolean includeArchived) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        return visibleKnowledgeItems(currentUser, space.rootItemId()).stream()
            .filter(document -> includeArchived || !document.archived())
            .toList();
    }

    public List<KnowledgeBaseItemTreeNode> itemTree(CurrentUser currentUser, UUID spaceId, boolean includeArchived) {
        return buildTree(listItems(currentUser, spaceId, includeArchived));
    }

    public KnowledgeContent getItem(CurrentUser currentUser, UUID spaceId, UUID itemId) {
        requireContentRoute(currentUser, spaceId, itemId);
        return hydrateDetailTargetSummary(currentUser, contentService.getContent(currentUser, itemId));
    }

    public void requireItemAccess(CurrentUser currentUser, UUID spaceId, UUID itemId) {
        requireContentRoute(currentUser, spaceId, itemId);
    }

    public List<KnowledgeContentTemplate> listTemplates(CurrentUser currentUser, UUID spaceId) {
        requireView(currentUser, spaceId);
        return contentService.listTemplates(currentUser, spaceId);
    }

    public List<KnowledgeObjectReference> listObjectReferences(CurrentUser currentUser, String targetObjectType, UUID targetObjectId) {
        String normalizedType = normalizeTargetObjectType("object_ref", targetObjectType);
        PlatformObjectSummary target = objectResolverRegistry.resolve(currentUser, normalizedType, targetObjectId);
        if (target.accessState() == ObjectAccessState.forbidden) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Target object is not accessible");
        }
        if (target.accessState() != ObjectAccessState.available) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target object does not exist");
        }
        return itemRepository.listObjectReferences(currentUser.workspaceId(), normalizedType, targetObjectId).stream()
            .filter(item -> currentUser.id().equals(item.createdBy())
                || permissionDecisionService.decide(currentUser, "knowledge_content", item.id(), "view").allowed())
            .map(item -> {
                KnowledgeBaseItem hydrated = hydrateTargetSummary(currentUser, item);
                KnowledgeContentContext context = contentService.knowledgeContext(currentUser, item.id());
                return new KnowledgeObjectReference(
                    context == null ? null : context.spaceId(),
                    context == null ? null : context.spaceName(),
                    item.id(), hydrated.title(), context == null ? null : context.webPath(),
                    item.displayMode(), item.targetTitleStrategy(), item.entryAlias()
                );
            })
            .toList();
    }

    @Transactional
    public KnowledgeContent createItem(
        CurrentUser currentUser,
        UUID spaceId,
        UUID parentId,
        String title,
        String contentType,
        String content,
        String targetObjectType,
        UUID targetObjectId,
        String targetRoute,
        String displayMode,
        String targetTitleStrategy,
        String entryAlias
    ) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        UUID targetParentId = parentId == null ? space.rootItemId() : parentId;
        requireItemInsideKnowledgeBase(currentUser, space.rootItemId(), targetParentId);
        String normalizedContentType = normalizeItemContentType(contentType);
        String normalizedTargetObjectType = normalizeTargetObjectType(normalizedContentType, targetObjectType);
        UUID normalizedTargetObjectId = normalizeTargetObjectId(normalizedContentType, targetObjectId);
        String normalizedTargetRoute = normalizeTargetRoute(normalizedContentType, targetRoute);
        if ("object_ref".equals(normalizedContentType)) {
            normalizedTargetRoute = resolveCanonicalTargetRoute(currentUser, normalizedTargetObjectType, normalizedTargetObjectId);
        }
        KnowledgeContent detail = contentService.createItem(currentUser, targetParentId, title, normalizedContentType, content, null, null, "view", false);
        itemRepository.updateKnowledgeNodeMetadata(
            currentUser.workspaceId(),
            detail.item().id(),
            nodeKindFor(normalizedContentType),
            normalizedTargetObjectType,
            normalizedTargetObjectId,
            normalizedTargetRoute,
            normalizeDisplayMode(displayMode),
            normalizeTitleStrategy(targetTitleStrategy),
            normalizeEntryAlias(entryAlias),
            currentUser.id()
        );
        KnowledgeContent created = hydrateDetailTargetSummary(currentUser, contentService.getContent(currentUser, detail.item().id()));
        auditService.log(currentUser, "knowledge.node.created", "knowledge_content", created.item().id(), knowledgeNodeAuditPayload(spaceId, created.item()));
        return created;
    }

    @Transactional
    public KnowledgeContent createBaseEntry(
        CurrentUser currentUser,
        UUID spaceId,
        UUID parentId,
        UUID existingBaseId,
        String newBaseName,
        String newBaseDescription,
        String displayMode,
        String targetTitleStrategy,
        String entryAlias
    ) {
        boolean attachExisting = existingBaseId != null;
        boolean createNew = newBaseName != null && !newBaseName.isBlank();
        if (attachExisting == createNew) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly one existing base or new base name is required");
        }
        BaseDetail base = attachExisting
            ? baseService.getBase(currentUser, existingBaseId)
            : baseService.createBase(currentUser, newBaseName, newBaseDescription);
        String title = entryAlias == null || entryAlias.isBlank() ? base.base().name() : entryAlias.trim();
        return createItem(currentUser, spaceId, parentId, title, "object_ref", null, "base", base.base().id(),
            null, displayMode, targetTitleStrategy, entryAlias);
    }

    @Transactional
    public KnowledgeContent updateObjectEntry(
        CurrentUser currentUser,
        UUID spaceId,
        UUID itemId,
        String displayMode,
        String targetTitleStrategy,
        String entryAlias
    ) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        requireContentItem(currentUser, space, itemId);
        KnowledgeBaseItem item = contentService.requireEdit(currentUser, itemId);
        if (!"object_ref".equals(item.contentType()) || item.targetObjectType() == null || item.targetObjectId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge item is not an object entry");
        }
        String canonicalRoute = resolveCanonicalTargetRoute(currentUser, item.targetObjectType(), item.targetObjectId());
        itemRepository.updateKnowledgeNodeMetadata(currentUser.workspaceId(), itemId, "object_ref", item.targetObjectType(),
            item.targetObjectId(), canonicalRoute, normalizeDisplayMode(displayMode), normalizeTitleStrategy(targetTitleStrategy),
            normalizeEntryAlias(entryAlias), currentUser.id());
        KnowledgeContent updated = hydrateDetailTargetSummary(currentUser, contentService.getContent(currentUser, itemId));
        auditService.log(currentUser, "knowledge.node.object_entry.updated", "knowledge_content", itemId,
            knowledgeNodeAuditPayload(spaceId, updated.item()));
        return updated;
    }

    @Transactional
    public KnowledgeContent createItemFromTemplate(CurrentUser currentUser, UUID spaceId, UUID templateId, UUID parentId, String title) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        UUID targetParentId = parentId == null ? space.rootItemId() : parentId;
        requireItemInsideKnowledgeBase(currentUser, space.rootItemId(), targetParentId);
        return contentService.createFromTemplate(currentUser, templateId, targetParentId, title);
    }

    @Transactional
    public KnowledgeContent moveItem(CurrentUser currentUser, UUID spaceId, UUID itemId, UUID parentId, Integer sortOrder) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        requireContentItem(currentUser, space, itemId);
        UUID targetParentId = parentId == null ? space.rootItemId() : parentId;
        requireItemInsideKnowledgeBase(currentUser, space.rootItemId(), targetParentId);
        KnowledgeContent moved = hydrateDetailTargetSummary(currentUser, contentService.moveItem(currentUser, itemId, targetParentId, sortOrder));
        auditService.log(currentUser, "knowledge.node.moved", "knowledge_content", itemId, knowledgeNodeAuditPayload(spaceId, moved.item()));
        return moved;
    }

    @Transactional
    public KnowledgeContent copyItem(CurrentUser currentUser, UUID spaceId, UUID itemId, UUID parentId, String title) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        requireContentItem(currentUser, space, itemId);
        UUID targetParentId = parentId == null ? space.rootItemId() : parentId;
        requireItemInsideKnowledgeBase(currentUser, space.rootItemId(), targetParentId);
        KnowledgeContent copied = hydrateDetailTargetSummary(
            currentUser,
            contentService.copyItem(currentUser, itemId, targetParentId, title)
        );
        auditService.log(
            currentUser,
            "knowledge.node.copied",
            "knowledge_content",
            copied.item().id(),
            knowledgeNodeAuditPayload(spaceId, copied.item())
        );
        return copied;
    }

    @Transactional
    public KnowledgeContent archiveItem(CurrentUser currentUser, UUID spaceId, UUID itemId) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        requireContentItem(currentUser, space, itemId);
        KnowledgeContent archived = hydrateDetailTargetSummary(currentUser, contentService.archiveItem(currentUser, itemId));
        auditService.log(currentUser, "knowledge.node.archived", "knowledge_content", itemId, knowledgeNodeAuditPayload(spaceId, archived.item()));
        return archived;
    }

    @Transactional
    public KnowledgeContent restoreItem(CurrentUser currentUser, UUID spaceId, UUID itemId) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        requireContentItem(currentUser, space, itemId);
        KnowledgeContent restored = hydrateDetailTargetSummary(currentUser, contentService.restoreItem(currentUser, itemId));
        auditService.log(currentUser, "knowledge.node.restored", "knowledge_content", itemId, knowledgeNodeAuditPayload(spaceId, restored.item()));
        return restored;
    }

    @Transactional
    public KnowledgeBaseDiscovery discovery(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        List<KnowledgeBaseItem> knowledge_base_items = visibleKnowledgeItems(currentUser, space.rootItemId());
        List<KnowledgeBaseItem> markdownDocuments = knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()))
            .filter(document -> !document.archived())
            .toList();
        Map<UUID, KnowledgeBaseItem> byId = markdownDocuments.stream()
            .collect(java.util.stream.Collectors.toMap(KnowledgeBaseItem::id, document -> document, (left, right) -> left));
        List<KnowledgeBaseItem> recentAccessed = idsToDocuments(
            objectRepository.listRecentAccesses(currentUser.workspaceId(), currentUser.id(), 50).stream()
                .filter(reference -> "knowledge_content".equals(reference.objectType()))
                .map(PlatformObjectReference::objectId)
                .toList(),
            byId,
            6
        );
        List<KnowledgeBaseItem> favorites = idsToDocuments(
            objectRepository.listFavorites(currentUser.workspaceId(), currentUser.id(), 50).stream()
                .filter(reference -> "knowledge_content".equals(reference.objectType()))
                .map(PlatformObjectReference::objectId)
                .toList(),
            byId,
            6
        );
        LocalDate reviewCutoff = LocalDate.now().plusDays(7);
        List<KnowledgeBaseItem> maintainedByMe = markdownDocuments.stream()
            .filter(document -> currentUser.id().equals(document.maintainerId()))
            .sorted(Comparator.comparing(KnowledgeBaseItem::updatedAt).reversed())
            .limit(6)
            .toList();
        List<KnowledgeBaseItem> dueForReview = markdownDocuments.stream()
            .filter(document -> currentUser.id().equals(document.maintainerId()) || currentUser.id().equals(space.ownerId()))
            .filter(document -> "needs_review".equals(document.knowledgeStatus())
                || "outdated".equals(document.knowledgeStatus())
                || (document.reviewDueAt() != null && !document.reviewDueAt().isAfter(reviewCutoff)))
            .sorted(Comparator.comparing((KnowledgeBaseItem document) -> document.reviewDueAt() == null ? LocalDate.MAX : document.reviewDueAt()))
            .limit(6)
            .toList();
        Set<UUID> priorityIds = new HashSet<>();
        List<KnowledgeBaseItem> popular = new ArrayList<>();
        for (KnowledgeBaseItem document : favorites) {
            if (priorityIds.add(document.id())) {
                popular.add(document);
            }
        }
        for (KnowledgeBaseItem document : recentAccessed) {
            if (priorityIds.add(document.id())) {
                popular.add(document);
            }
        }
        markdownDocuments.stream()
            .sorted(Comparator.comparing(KnowledgeBaseItem::updatedAt).reversed())
            .filter(document -> priorityIds.add(document.id()))
            .limit(6)
            .forEach(popular::add);
        List<KnowledgeBaseItem> recommended = new ArrayList<>();
        Set<String> myTags = new HashSet<>();
        maintainedByMe.forEach(document -> myTags.addAll(document.tags()));
        favorites.forEach(document -> myTags.addAll(document.tags()));
        markdownDocuments.stream()
            .filter(document -> document.tags().stream().anyMatch(myTags::contains))
            .filter(document -> recommended.stream().noneMatch(existing -> existing.id().equals(document.id())))
            .limit(6)
            .forEach(recommended::add);
        if (recommended.size() < 6) {
            markdownDocuments.stream()
                .sorted(Comparator.comparing(KnowledgeBaseItem::updatedAt).reversed())
                .filter(document -> recommended.stream().noneMatch(existing -> existing.id().equals(document.id())))
                .limit(6 - recommended.size())
                .forEach(recommended::add);
        }
        List<KnowledgeBaseItem> subscribedDocuments = idsToDocuments(
            knowledgeBaseSpaceRepository.listSubscribedItemIds(currentUser.workspaceId(), currentUser.id(), space.rootItemId(), 20),
            byId,
            6
        );
        return new KnowledgeBaseDiscovery(
            space.id(),
            recentAccessed,
            favorites,
            maintainedByMe,
            dueForReview,
            popular.stream().limit(6).toList(),
            recommended,
            subscribedDocuments,
            knowledgeBaseSpaceRepository.isSubscribed(currentUser.workspaceId(), currentUser.id(), "knowledge_base", space.id())
        );
    }

    @Transactional
    public KnowledgeBaseSubscription subscribe(CurrentUser currentUser, UUID spaceId, String targetType, UUID targetId) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        String normalizedTargetType = normalizeSubscriptionTargetType(targetType);
        UUID normalizedTargetId = normalizeSubscriptionTarget(currentUser, space, normalizedTargetType, targetId);
        knowledgeBaseSpaceRepository.subscribe(currentUser.workspaceId(), currentUser.id(), normalizedTargetType, normalizedTargetId);
        auditService.log(currentUser, "knowledge.subscription.created", normalizedTargetType, normalizedTargetId, Map.of("spaceId", space.id().toString()));
        return new KnowledgeBaseSubscription(normalizedTargetType, normalizedTargetId, true);
    }

    @Transactional
    public KnowledgeBaseSubscription unsubscribe(CurrentUser currentUser, UUID spaceId, String targetType, UUID targetId) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        String normalizedTargetType = normalizeSubscriptionTargetType(targetType);
        UUID normalizedTargetId = normalizeSubscriptionTarget(currentUser, space, normalizedTargetType, targetId);
        knowledgeBaseSpaceRepository.unsubscribe(currentUser.workspaceId(), currentUser.id(), normalizedTargetType, normalizedTargetId);
        auditService.log(currentUser, "knowledge.subscription.removed", normalizedTargetType, normalizedTargetId, Map.of("spaceId", space.id().toString()));
        return new KnowledgeBaseSubscription(normalizedTargetType, normalizedTargetId, false);
    }

    @Transactional
    public KnowledgeBaseGovernanceDashboard governance(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        List<KnowledgeBaseItem> knowledge_base_items = itemRepository.listKnowledgeBaseItems(currentUser.workspaceId(), space.rootItemId());
        List<KnowledgeBaseGovernanceRisk> risks = governanceRisks(currentUser.workspaceId(), space, knowledge_base_items);
        return new KnowledgeBaseGovernanceDashboard(
            space.id(),
            healthMetrics(knowledge_base_items, risks),
            risks,
            accessStats(currentUser.workspaceId(), knowledge_base_items, space.id())
        );
    }

    @Transactional
    public KnowledgeBaseBulkGovernanceResult bulkGovernance(
        CurrentUser currentUser,
        UUID spaceId,
        List<UUID> itemIds,
        UUID maintainerId,
        List<String> tags,
        boolean replaceTags,
        boolean archive,
        boolean requestReview,
        LocalDate reviewDueAt
    ) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        Map<UUID, KnowledgeBaseItem> knowledge_base_itemsById = itemRepository.listKnowledgeBaseItems(currentUser.workspaceId(), space.rootItemId()).stream()
            .collect(java.util.stream.Collectors.toMap(KnowledgeBaseItem::id, document -> document, (left, right) -> left));
        List<UUID> targets = (itemIds == null ? List.<UUID>of() : itemIds).stream()
            .filter(Objects::nonNull)
            .distinct()
            .limit(100)
            .filter(id -> knowledge_base_itemsById.containsKey(id) && !id.equals(space.rootItemId()))
            .toList();
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk governance requires at least one document in the knowledge base");
        }
        List<String> normalizedTags = normalizeTags(tags);
        int updated = 0;
        int archived = 0;
        int reviewRequested = 0;
        for (UUID itemId : targets) {
            KnowledgeBaseItem document = knowledge_base_itemsById.get(itemId);
            if (maintainerId != null || !normalizedTags.isEmpty() || requestReview) {
                List<String> nextTags = replaceTags
                    ? normalizedTags
                    : mergeTags(document.tags(), normalizedTags);
                String nextStatus = requestReview ? "needs_review" : document.knowledgeStatus();
                LocalDate nextReviewDueAt = requestReview
                    ? (reviewDueAt == null ? LocalDate.now() : reviewDueAt)
                    : document.reviewDueAt();
                itemRepository.updateKnowledgeMetadata(
                    currentUser.workspaceId(),
                    itemId,
                    maintainerId == null ? document.maintainerId() : maintainerId,
                    nextTags,
                    document.category(),
                    nextStatus,
                    nextReviewDueAt,
                    "verified".equals(nextStatus) ? document.verifiedAt() : null,
                    currentUser.id()
                );
                updated++;
                if (requestReview) {
                    reviewRequested++;
                }
            }
            if (archive && !document.archived()) {
                contentService.archiveItem(currentUser, itemId);
                archived++;
            }
        }
        auditService.log(
            currentUser,
            "knowledge_base.governance.bulk_updated",
            "knowledge_base",
            space.id(),
            Map.of(
                "targetCount", Integer.toString(targets.size()),
                "updatedCount", Integer.toString(updated),
                "archivedCount", Integer.toString(archived),
                "reviewRequestedCount", Integer.toString(reviewRequested)
            )
        );
        return new KnowledgeBaseBulkGovernanceResult(updated, archived, reviewRequested);
    }

    @Transactional
    public String exportGovernanceCsv(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseGovernanceDashboard dashboard = governance(currentUser, spaceId);
        StringBuilder csv = new StringBuilder("section,key,value,resourceType,resourceId,title,subjectType,subjectName,permissionLevel,reason\n");
        KnowledgeBaseHealthMetrics health = dashboard.health();
        csv.append(row("health", "itemCount", health.itemCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "activeItemCount", health.activeItemCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "outdatedItemCount", health.outdatedItemCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "unmaintainedItemCount", health.unmaintainedItemCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "ownerlessItemCount", health.ownerlessItemCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "highRiskPermissionCount", health.highRiskPermissionCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "blockCoverageGapCount", health.blockCoverageGapCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "emptyBlockCount", health.emptyBlockCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "invalidEmbedBlockCount", health.invalidEmbedBlockCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "blockCoveragePercent", health.blockCoveragePercent(), null, null, null, null, null, null, null));
        csv.append(row("access", "visitorCount", dashboard.accessStats().visitorCount(), null, null, null, null, null, null, null));
        csv.append(row("access", "accessCount", dashboard.accessStats().accessCount(), null, null, null, null, null, null, null));
        for (KnowledgeBaseGovernanceRisk risk : dashboard.risks()) {
            csv.append(row("risk", risk.ruleCode(), risk.severity(), risk.resourceType(), risk.resourceId(), risk.title(), risk.subjectType(), risk.subjectName(), risk.permissionLevel(), risk.reason()));
        }
        for (KnowledgeBaseAccessItemStat stat : dashboard.accessStats().popularItems()) {
            csv.append(row("popular_document", stat.item().id().toString(), stat.accessCount(), "knowledge_content", stat.item().id(), stat.item().title(), null, null, null, "访客 " + stat.visitorCount()));
        }
        for (KnowledgeBaseSearchTermStat term : dashboard.accessStats().noResultTerms()) {
            csv.append(row("search_no_result", term.query(), term.count(), "knowledge_base", dashboard.spaceId(), null, null, null, null, "最近 " + term.lastSearchedAt()));
        }
        return csv.toString();
    }

    @Transactional
    public KnowledgeBaseSpaceDetail createSpace(
        CurrentUser currentUser,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String visibility,
        String defaultPermissionLevel
    ) {
        String normalizedName = normalizeName(name);
        String normalizedCode = uniqueCode(currentUser.workspaceId(), normalizeCode(code, normalizedName), null);
        String normalizedVisibility = normalizeVisibility(visibility);
        String normalizedDefaultPermission = normalizeDefaultPermission(defaultPermissionLevel);
        String normalizedDescription = normalizeNullable(description, 512);
        String normalizedIcon = normalizeNullable(icon, 64);
        String normalizedCoverUrl = normalizeNullable(coverUrl, 1024);
        KnowledgeContent root = contentService.createItem(
            currentUser,
            null,
            "根目录",
            "space",
            "# 根目录",
            null,
            null,
            "view",
            true
        );
        KnowledgeContent home = contentService.createItem(
            currentUser,
            root.item().id(),
            "首页",
            "markdown",
            "# " + normalizedName + "\n\n在这里维护知识库介绍、常用入口和目录说明。",
            "知识库首页",
            null,
            normalizedDefaultPermission,
            false
        );
        UUID spaceId = knowledgeBaseSpaceRepository.createSpace(
            currentUser.workspaceId(),
            normalizedName,
            normalizedCode,
            normalizedDescription,
            normalizedIcon,
            normalizedCoverUrl,
            normalizedVisibility,
            root.item().id(),
            home.item().id(),
            currentUser.id(),
            normalizedDefaultPermission,
            currentUser.id()
        );
        permissionDecisionService.grantResource(
            currentUser.workspaceId(),
            "knowledge_base",
            spaceId,
            "user",
            currentUser.id(),
            "owner",
            "owner",
            null,
            null,
            currentUser.id()
        );
        auditService.log(currentUser, "knowledge_base.created", "knowledge_base", spaceId, Map.of(
            "rootItemId", root.item().id().toString(),
            "code", normalizedCode
        ));
        return getSpace(currentUser, spaceId);
    }

    @Transactional
    public KnowledgeBaseSpaceDetail updateSpace(
        CurrentUser currentUser,
        UUID spaceId,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String visibility,
        UUID homeItemId,
        String defaultPermissionLevel
    ) {
        KnowledgeBaseSpaceSummary before = requireManage(currentUser, spaceId);
        String normalizedName = normalizeName(name == null || name.isBlank() ? before.name() : name);
        String normalizedCode = uniqueCode(
            currentUser.workspaceId(),
            normalizeCode(code == null || code.isBlank() ? before.code() : code, normalizedName),
            before.id()
        );
        UUID normalizedHomeItemId = homeItemId == null ? before.homeItemId() : homeItemId;
        requireItemInsideKnowledgeBase(currentUser, before.rootItemId(), normalizedHomeItemId);
        knowledgeBaseSpaceRepository.updateSpace(
            currentUser.workspaceId(),
            spaceId,
            normalizedName,
            normalizedCode,
            normalizeNullable(description, 512),
            normalizeNullable(icon, 64),
            normalizeNullable(coverUrl, 1024),
            normalizeVisibility(visibility == null ? before.visibility() : visibility),
            normalizedHomeItemId,
            normalizeDefaultPermission(defaultPermissionLevel == null ? before.defaultPermissionLevel() : defaultPermissionLevel),
            currentUser.id()
        );
        auditService.log(currentUser, "knowledge_base.updated", "knowledge_base", spaceId, Map.of("code", normalizedCode));
        return getSpace(currentUser, spaceId);
    }

    @Transactional
    public KnowledgeBaseSpaceDetail disableSpace(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        knowledgeBaseSpaceRepository.updateStatus(currentUser.workspaceId(), space.id(), "disabled", currentUser.id());
        auditService.log(currentUser, "knowledge_base.disabled", "knowledge_base", space.id(), Map.of(
            "rootItemId", space.rootItemId().toString()
        ));
        return getSpace(currentUser, spaceId);
    }

    @Transactional
    public KnowledgeBaseSpaceDetail restoreSpace(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        knowledgeBaseSpaceRepository.updateStatus(currentUser.workspaceId(), space.id(), "active", currentUser.id());
        KnowledgeBaseItem root = itemRepository.findItem(currentUser.workspaceId(), space.rootItemId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base root document not found"));
        if (root.archived()) {
            contentService.restoreItem(currentUser, root.id());
        }
        auditService.log(currentUser, "knowledge_base.restored", "knowledge_base", space.id(), Map.of(
            "rootItemId", space.rootItemId().toString()
        ));
        return getSpace(currentUser, spaceId);
    }

    @Transactional
    public KnowledgeBaseSpaceDetail archiveSpace(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        knowledgeBaseSpaceRepository.updateStatus(currentUser.workspaceId(), space.id(), "archived", currentUser.id());
        contentService.archiveItem(currentUser, space.rootItemId());
        auditService.log(currentUser, "knowledge_base.archived", "knowledge_base", space.id(), Map.of(
            "rootItemId", space.rootItemId().toString()
        ));
        return getSpace(currentUser, spaceId);
    }

    @Transactional
    public void deleteSpace(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        knowledgeBaseSpaceRepository.deleteSpace(currentUser.workspaceId(), space.id(), currentUser.id());
        auditService.log(currentUser, "knowledge_base.deleted", "knowledge_base", space.id(), Map.of(
            "rootItemId", space.rootItemId().toString()
        ));
    }

    private KnowledgeBaseHealthMetrics healthMetrics(List<KnowledgeBaseItem> knowledge_base_items, List<KnowledgeBaseGovernanceRisk> risks) {
        BlockGovernanceStats blockStats = blockGovernanceStats(knowledge_base_items);
        long itemCount = knowledge_base_items.stream().filter(document -> "markdown".equals(document.contentType())).count();
        long activeItemCount = knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()) && !document.archived())
            .count();
        long outdatedItemCount = knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()) && !document.archived())
            .filter(document -> "outdated".equals(document.knowledgeStatus())
                || "needs_review".equals(document.knowledgeStatus())
                || (document.reviewDueAt() != null && !document.reviewDueAt().isAfter(LocalDate.now())))
            .count();
        long unmaintainedItemCount = knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()) && !document.archived())
            .filter(document -> document.maintainerId() == null)
            .count();
        long ownerlessItemCount = risks.stream().filter(risk -> "document_without_owner".equals(risk.ruleCode())).count();
        long highRiskPermissionCount = risks.stream()
            .filter(risk -> List.of("critical", "high").contains(risk.severity()))
            .count();
        return new KnowledgeBaseHealthMetrics(
            itemCount,
            activeItemCount,
            outdatedItemCount,
            unmaintainedItemCount,
            ownerlessItemCount,
            highRiskPermissionCount,
            blockStats.coverageGapCount(),
            blockStats.emptyBlockCount(),
            blockStats.invalidEmbedBlockCount(),
            blockStats.coveragePercent()
        );
    }

    private List<KnowledgeBaseGovernanceRisk> governanceRisks(UUID workspaceId, KnowledgeBaseSpaceSummary space, List<KnowledgeBaseItem> knowledge_base_items) {
        Map<UUID, KnowledgeBaseItem> knowledge_base_itemsById = knowledge_base_items.stream()
            .collect(java.util.stream.Collectors.toMap(KnowledgeBaseItem::id, document -> document, (left, right) -> left));
        List<KnowledgeBaseGovernanceRisk> risks = new ArrayList<>();
        knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()) && !document.archived())
            .filter(document -> document.maintainerId() == null)
            .forEach(document -> risks.add(new KnowledgeBaseGovernanceRisk(
                "unmaintained:" + document.id(),
                "document_without_maintainer",
                "medium",
                "knowledge_content",
                document.id(),
                document.title(),
                null,
                null,
                null,
                null,
                "知识内容未设置维护人。",
                knowledgeContentWebPath(space.id(), document.id())
            )));
        BlockGovernanceStats blockStats = blockGovernanceStats(knowledge_base_items);
        if (blockStats.coverageGapCount() > 0) {
            risks.add(new KnowledgeBaseGovernanceRisk(
                "block-coverage:" + space.id(),
                "block_coverage_gap",
                "high",
                "knowledge_base",
                space.id(),
                space.name(),
                null,
                null,
                null,
                null,
                "存在 " + blockStats.coverageGapCount() + " 个活动知识内容仍只依赖旧富文本，需投影为 blocks。",
                "/knowledge-bases/" + space.id()
            ));
        }
        if (blockStats.emptyBlockCount() > 0) {
            risks.add(new KnowledgeBaseGovernanceRisk(
                "empty-block:" + space.id(),
                "empty_content_block",
                "medium",
                "knowledge_base",
                space.id(),
                space.name(),
                null,
                null,
                null,
                null,
                "存在 " + blockStats.emptyBlockCount() + " 个空内容块，建议清理或补全文本。",
                "/knowledge-bases/" + space.id()
            ));
        }
        if (blockStats.invalidEmbedBlockCount() > 0) {
            risks.add(new KnowledgeBaseGovernanceRisk(
                "invalid-embed:" + space.id(),
                "invalid_embedded_object",
                "high",
                "knowledge_base",
                space.id(),
                space.name(),
                null,
                null,
                null,
                null,
                "存在 " + blockStats.invalidEmbedBlockCount() + " 个无法解析或缺少目标的嵌入对象块。",
                "/knowledge-bases/" + space.id()
            ));
        }
        knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()) && !document.archived())
            .filter(document -> "outdated".equals(document.knowledgeStatus())
                || "needs_review".equals(document.knowledgeStatus())
                || (document.reviewDueAt() != null && !document.reviewDueAt().isAfter(LocalDate.now())))
            .forEach(document -> risks.add(new KnowledgeBaseGovernanceRisk(
                "outdated:" + document.id(),
                "document_review_overdue",
                "high",
                "knowledge_content",
                document.id(),
                document.title(),
                null,
                null,
                null,
                null,
                "知识状态为待复核/已过期，或复核日期已到期。",
                knowledgeContentWebPath(space.id(), document.id())
            )));
        risks.addAll(permissionRisks(workspaceId, space, knowledge_base_itemsById));
        if ("workspace".equals(space.visibility()) || List.of("edit", "comment").contains(space.defaultPermissionLevel())) {
            risks.add(new KnowledgeBaseGovernanceRisk(
                "workspace-scope:" + space.id(),
                "knowledge_base_broad_visibility",
                "medium",
                "knowledge_base",
                space.id(),
                space.name(),
                null,
                null,
                null,
                space.defaultPermissionLevel(),
                "知识库可见范围或默认权限较宽，建议定期复核。",
                "/knowledge-bases/" + space.id()
            ));
        }
        return risks.stream()
            .sorted(Comparator.comparingInt((KnowledgeBaseGovernanceRisk risk) -> severityRank(risk.severity())).reversed())
            .limit(100)
            .toList();
    }

    private BlockGovernanceStats blockGovernanceStats(List<KnowledgeBaseItem> knowledge_base_items) {
        List<UUID> activeMarkdownIds = knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()) && !document.archived())
            .map(KnowledgeBaseItem::id)
            .toList();
        if (activeMarkdownIds.isEmpty()) {
            return new BlockGovernanceStats(0, 0, 0, 100.0d);
        }
        String idArray = activeMarkdownIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
                with target_knowledge_base_items as (
                    select d.workspace_id, d.id
                    from knowledge_base_items d
                    where d.id = any(string_to_array(?, ',')::uuid[])
                      and d.deleted_at is null
                      and d.archived_at is null
                ),
                active_blocks as (
                    select b.*
                    from knowledge_content_blocks b
                    join target_knowledge_base_items d on d.workspace_id = b.workspace_id and d.id = b.item_id
                    where b.deleted_at is null
                )
                select
                    count(distinct d.id) filter (
                        where not exists (
                              select 1 from active_blocks b where b.workspace_id = d.workspace_id and b.item_id = d.id
                          )
                    ) coverage_gap_count,
                    count(b.id) filter (
                        where b.block_type not in ('divider', 'toc')
                          and coalesce(b.plain_text, '') = ''
                          and coalesce(b.content, '') = ''
                          and coalesce(b.rich_content, '{}'::jsonb) = '{}'::jsonb
                    ) empty_block_count,
                    count(b.id) filter (
                        where b.block_type in ('embed', 'embed_object', 'base_view', 'issue_embed', 'message_embed', 'file_embed', 'link', 'link_card')
                          and (
                              coalesce(b.content, '') !~ '"objectType"\\s*:'
                              or coalesce(b.content, '') !~ '"objectId"\\s*:'
                          )
                    ) invalid_embed_block_count,
                    count(distinct d.id) total_count,
                    count(distinct d.id) filter (
                        where exists (
                            select 1 from active_blocks b where b.workspace_id = d.workspace_id and b.item_id = d.id
                        )
                    ) covered_count
                from target_knowledge_base_items d
                left join active_blocks b on b.workspace_id = d.workspace_id and b.item_id = d.id
                """,
            idArray
        );
        long total = asLong(row.get("total_count"));
        long covered = asLong(row.get("covered_count"));
        double coveragePercent = total == 0 ? 100.0d : Math.round((covered * 10000.0d / total)) / 100.0d;
        return new BlockGovernanceStats(
            asLong(row.get("coverage_gap_count")),
            asLong(row.get("empty_block_count")),
            asLong(row.get("invalid_embed_block_count")),
            coveragePercent
        );
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private record BlockGovernanceStats(long coverageGapCount, long emptyBlockCount, long invalidEmbedBlockCount, double coveragePercent) {
    }

    private List<KnowledgeBaseGovernanceRisk> permissionRisks(
        UUID workspaceId,
        KnowledgeBaseSpaceSummary space,
        Map<UUID, KnowledgeBaseItem> knowledge_base_itemsById
    ) {
        return jdbcTemplate.query(
            """
                with recursive subtree as (
                    select d.id, d.title
                    from knowledge_base_items d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select child.id, child.title
                    from knowledge_base_items child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                ),
                scoped_resources as (
                    select 'knowledge_base'::varchar resource_type, ?::uuid resource_id, ?::varchar title
                    union all
                    select 'knowledge_content'::varchar, id, title from subtree
                )
                select rp.id::text id, rp.resource_type, rp.resource_id, sr.title,
                       rp.subject_type, rp.subject_id,
                       coalesce(u.display_name, u.username, d.name, ug.name, r.name) subject_name,
                       rp.permission_level,
                       case
                           when rp.subject_type = 'user' and (u.status <> 'active' or u.deleted_at is not null)
                               then 'disabled_subject_permission'
                           when rp.subject_type = 'department' and (d.status <> 'active' or d.deleted_at is not null)
                               then 'disabled_subject_permission'
                           when rp.subject_type = 'user_group' and (ug.status <> 'active' or ug.deleted_at is not null)
                               then 'disabled_subject_permission'
                           when rp.subject_type in ('department', 'user_group') and rp.permission_level in ('manage', 'owner')
                               then 'broad_high_permission'
                           else 'document_without_owner'
                       end rule_code
                from scoped_resources sr
                join resource_permissions rp on rp.workspace_id = ?
                    and rp.resource_type = sr.resource_type
                    and rp.resource_id = sr.resource_id
                    and rp.status = 'active'
                left join users u on rp.subject_type = 'user' and u.workspace_id = rp.workspace_id and u.id = rp.subject_id
                left join departments d on rp.subject_type = 'department' and d.workspace_id = rp.workspace_id and d.id = rp.subject_id
                left join user_groups ug on rp.subject_type = 'user_group' and ug.workspace_id = rp.workspace_id and ug.id = rp.subject_id
                left join roles r on rp.subject_type = 'role' and r.workspace_id = rp.workspace_id and r.id = rp.subject_id
                where (
                    (rp.subject_type = 'user' and (u.status <> 'active' or u.deleted_at is not null))
                    or (rp.subject_type = 'department' and (d.status <> 'active' or d.deleted_at is not null))
                    or (rp.subject_type = 'user_group' and (ug.status <> 'active' or ug.deleted_at is not null))
                    or (rp.subject_type in ('department', 'user_group') and rp.permission_level in ('manage', 'owner'))
                )
                union all
                select 'owner:' || sr.resource_type || ':' || sr.resource_id,
                       sr.resource_type,
                       sr.resource_id,
                       sr.title,
                       null,
                       null,
                       null,
                       null,
                       'document_without_owner'
                from scoped_resources sr
                where sr.resource_type = 'knowledge_content'
                  and not exists (
                      select 1
                      from resource_permissions owner_rp
                      where owner_rp.workspace_id = ?
                        and owner_rp.resource_type = sr.resource_type
                        and owner_rp.resource_id = sr.resource_id
                        and owner_rp.permission_level = 'owner'
                        and owner_rp.status = 'active'
                  )
                """,
            (rs, rowNum) -> {
                String rule = rs.getString("rule_code");
                UUID resourceId = rs.getObject("resource_id", UUID.class);
                String resourceType = rs.getString("resource_type");
                String severity = switch (rule) {
                    case "broad_high_permission" -> "critical";
                    case "disabled_subject_permission", "document_without_owner" -> "high";
                    default -> "medium";
                };
                return new KnowledgeBaseGovernanceRisk(
                    rs.getString("id"),
                    rule,
                    severity,
                    resourceType,
                    resourceId,
                    knowledge_base_itemsById.get(resourceId) == null ? rs.getString("title") : knowledge_base_itemsById.get(resourceId).title(),
                    rs.getString("subject_type"),
                    rs.getObject("subject_id", UUID.class),
                    rs.getString("subject_name"),
                    rs.getString("permission_level"),
                    riskReason(rule),
                    "knowledge_content".equals(resourceType) ? knowledgeContentWebPath(space.id(), resourceId) : "/knowledge-bases/" + space.id()
                );
            },
            workspaceId,
            space.rootItemId(),
            workspaceId,
            space.id(),
            space.name(),
            workspaceId,
            workspaceId
        );
    }

    private String knowledgeContentWebPath(UUID spaceId, UUID itemId) {
        return "/knowledge-bases/" + spaceId + "/items/" + itemId;
    }

    private KnowledgeBaseAccessStats accessStats(UUID workspaceId, List<KnowledgeBaseItem> knowledge_base_items, UUID spaceId) {
        Map<UUID, KnowledgeBaseItem> markdownById = knowledge_base_items.stream()
            .filter(document -> "markdown".equals(document.contentType()) && !document.archived())
            .collect(java.util.stream.Collectors.toMap(KnowledgeBaseItem::id, document -> document, (left, right) -> left));
        if (markdownById.isEmpty()) {
            return new KnowledgeBaseAccessStats(0, 0, List.of(), List.of(), noResultTerms(workspaceId, spaceId));
        }
        List<UUID> itemIds = new ArrayList<>(markdownById.keySet());
        String idArray = itemIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        Long visitorCount = jdbcTemplate.queryForObject(
            "select count(distinct user_id) from object_recent_accesses where workspace_id = ? and object_type = 'knowledge_content' and object_id = any(string_to_array(?, ',')::uuid[])",
            Long.class,
            workspaceId,
            idArray
        );
        Long accessCount = jdbcTemplate.queryForObject(
            "select coalesce(sum(access_count), 0) from object_recent_accesses where workspace_id = ? and object_type = 'knowledge_content' and object_id = any(string_to_array(?, ',')::uuid[])",
            Long.class,
            workspaceId,
            idArray
        );
        List<KnowledgeBaseAccessItemStat> stats = jdbcTemplate.query(
            """
                select object_id,
                       count(distinct user_id) visitor_count,
                       coalesce(sum(access_count), 0) access_count,
                       max(last_accessed_at) last_accessed_at
                from object_recent_accesses
                where workspace_id = ?
                  and object_type = 'knowledge_content'
                  and object_id = any(string_to_array(?, ',')::uuid[])
                group by object_id
                """,
            (rs, rowNum) -> new KnowledgeBaseAccessItemStat(
                markdownById.get(rs.getObject("object_id", UUID.class)),
                rs.getLong("visitor_count"),
                rs.getLong("access_count"),
                rs.getTimestamp("last_accessed_at") == null ? null : rs.getTimestamp("last_accessed_at").toInstant()
            ),
            workspaceId,
            idArray
        ).stream().filter(stat -> stat.item() != null).toList();
        List<KnowledgeBaseAccessItemStat> popular = stats.stream()
            .sorted(Comparator.comparingLong(KnowledgeBaseAccessItemStat::accessCount).reversed())
            .limit(5)
            .toList();
        Set<UUID> accessedIds = stats.stream().map(stat -> stat.item().id()).collect(java.util.stream.Collectors.toSet());
        List<KnowledgeBaseAccessItemStat> lowAccess = new ArrayList<>();
        markdownById.values().stream()
            .filter(document -> !accessedIds.contains(document.id()))
            .sorted(Comparator.comparing(KnowledgeBaseItem::updatedAt))
            .limit(5)
            .map(document -> new KnowledgeBaseAccessItemStat(document, 0, 0, null))
            .forEach(lowAccess::add);
        if (lowAccess.size() < 5) {
            stats.stream()
                .sorted(Comparator.comparingLong(KnowledgeBaseAccessItemStat::accessCount))
                .limit(5 - lowAccess.size())
                .forEach(lowAccess::add);
        }
        return new KnowledgeBaseAccessStats(
            visitorCount == null ? 0 : visitorCount,
            accessCount == null ? 0 : accessCount,
            popular,
            lowAccess,
            noResultTerms(workspaceId, spaceId)
        );
    }

    private List<KnowledgeBaseSearchTermStat> noResultTerms(UUID workspaceId, UUID spaceId) {
        return jdbcTemplate.query(
            """
                select metadata ->> 'query' query_text, count(*) search_count, max(created_at) last_searched_at
                from audit_logs
                where workspace_id = ?
                  and action = 'knowledge.search.no_result'
                  and target_type = 'knowledge_base'
                  and target_id = ?
                  and metadata ->> 'query' is not null
                group by metadata ->> 'query'
                order by search_count desc, last_searched_at desc
                limit 10
                """,
            (rs, rowNum) -> new KnowledgeBaseSearchTermStat(
                rs.getString("query_text"),
                rs.getLong("search_count"),
                rs.getTimestamp("last_searched_at").toInstant()
            ),
            workspaceId,
            spaceId
        );
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    private String riskReason(String rule) {
        return switch (rule) {
            case "disabled_subject_permission" -> "停用或删除主体仍保留知识库相关授权。";
            case "broad_high_permission" -> "部门或用户组持有 manage/owner 高风险授权。";
            case "document_without_owner" -> "知识文档存在授权记录但没有 owner 权限主体。";
            default -> "需要复核的知识库治理风险。";
        };
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split(",")) {
                String tag = part.trim().toLowerCase(Locale.ROOT);
                if (!tag.isBlank() && !normalized.contains(tag)) {
                    normalized.add(tag.length() <= 32 ? tag : tag.substring(0, 32));
                }
            }
        }
        return normalized;
    }

    private List<String> mergeTags(List<String> currentTags, List<String> incomingTags) {
        List<String> merged = new ArrayList<>(currentTags == null ? List.of() : currentTags);
        for (String tag : incomingTags) {
            if (!merged.contains(tag)) {
                merged.add(tag);
            }
        }
        return merged;
    }

    private String row(String section, String key, Object value, String resourceType, UUID resourceId, String title, String subjectType, String subjectName, String permissionLevel, String reason) {
        return csv(section) + ','
            + csv(key) + ','
            + csv(value == null ? "" : String.valueOf(value)) + ','
            + csv(resourceType) + ','
            + csv(resourceId == null ? "" : resourceId.toString()) + ','
            + csv(title) + ','
            + csv(subjectType) + ','
            + csv(subjectName) + ','
            + csv(permissionLevel) + ','
            + csv(reason) + '\n';
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private KnowledgeBaseSpaceSummary requireView(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = knowledgeBaseSpaceRepository.findSpace(currentUser.workspaceId(), spaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found"));
        if (!canView(currentUser, space)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge base is not visible");
        }
        return space;
    }

    private KnowledgeBaseSpaceSummary requireManage(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        PermissionDecision knowledgeBaseDecision = permissionDecisionService.decide(currentUser, "knowledge_base", space.id(), "manage");
        PermissionDecision rootDecision = permissionDecisionService.decide(currentUser, "knowledge_content", space.rootItemId(), "manage");
        if (!knowledgeBaseDecision.allowed() && !rootDecision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge base manage permission is required");
        }
        return space;
    }

    private boolean canView(CurrentUser currentUser, KnowledgeBaseSpaceSummary space) {
        if (currentUser.id().equals(space.ownerId())) {
            return true;
        }
        PermissionDecision knowledgeBaseDecision = permissionDecisionService.decide(currentUser, "knowledge_base", space.id(), "view");
        PermissionDecision rootDecision = permissionDecisionService.decide(currentUser, "knowledge_content", space.rootItemId(), "view");
        return knowledgeBaseDecision.allowed() || rootDecision.allowed();
    }

    private List<KnowledgeBaseItem> visibleKnowledgeItems(CurrentUser currentUser, UUID rootItemId) {
        return itemRepository.listKnowledgeBaseItems(currentUser.workspaceId(), rootItemId).stream()
            .filter(document -> currentUser.id().equals(document.createdBy())
                || permissionDecisionService.decide(currentUser, "knowledge_content", document.id(), "view").allowed())
            .map(document -> hydrateTargetSummary(currentUser, document))
            .toList();
    }

    private KnowledgeBaseItem hydrateTargetSummary(CurrentUser currentUser, KnowledgeBaseItem document) {
        if ("external_link".equals(document.contentType())) {
            PlatformObjectSummary summary = new PlatformObjectSummary(
                "external_link",
                document.id(),
                ObjectAccessState.available,
                document.title(),
                document.targetRoute(),
                document.archived() ? "archived" : "active",
                document.targetRoute(),
                null,
                Map.of("sourceModule", "knowledge")
            );
            return copyKnowledgeBaseItem(document, document.title(), document.targetRoute(), summary);
        }
        if (!"object_ref".equals(document.contentType()) || document.targetObjectType() == null || document.targetObjectId() == null) {
            return document;
        }
        PlatformObjectSummary targetSummary = objectResolverRegistry.resolve(currentUser, document.targetObjectType(), document.targetObjectId());
        if (targetSummary.accessState() != ObjectAccessState.available) {
            auditService.log(
                currentUser,
                "knowledge.node.target_unavailable",
                "knowledge_content",
                document.id(),
                Map.of(
                    "targetObjectType", document.targetObjectType(),
                    "targetObjectId", document.targetObjectId().toString(),
                    "accessState", targetSummary.accessState().name()
                )
            );
            return copyKnowledgeBaseItem(document, unavailableTargetTitle(targetSummary.accessState()), null, targetSummary);
        }
        String title = switch (document.targetTitleStrategy() == null ? "manual" : document.targetTitleStrategy()) {
            case "follow_target" -> targetSummary.title();
            case "alias" -> document.entryAlias() == null || document.entryAlias().isBlank() ? document.title() : document.entryAlias();
            default -> document.title();
        };
        String canonicalRoute = targetSummary.webPath();
        if ("base".equals(document.targetObjectType()) && document.targetRoute() != null
            && canonicalRoute != null && document.targetRoute().startsWith(canonicalRoute)) {
            canonicalRoute = document.targetRoute();
        }
        return copyKnowledgeBaseItem(document, title, canonicalRoute, targetSummary);
    }

    private KnowledgeContent hydrateDetailTargetSummary(CurrentUser currentUser, KnowledgeContent detail) {
        return new KnowledgeContent(
            hydrateTargetSummary(currentUser, detail.item()),
            detail.content(),
            detail.blocks(),
            detail.relations(),
            detail.permissions(),
            detail.shareLinks(),
            detail.comments(),
            detail.context()
        );
    }

    private KnowledgeBaseItem copyKnowledgeBaseItem(
        KnowledgeBaseItem document,
        String title,
        String targetRoute,
        PlatformObjectSummary targetSummary
    ) {
        return new KnowledgeBaseItem(
            document.id(),
            document.parentId(),
            title,
            document.contentType(),
            document.currentVersionNo(),
            document.permissionLevel(),
            document.createdBy(),
            document.createdByName(),
            document.createdAt(),
            document.updatedBy(),
            document.updatedByName(),
            document.updatedAt(),
            document.sortOrder(),
            document.description(),
            document.coverUrl(),
            document.defaultPermissionLevel(),
            document.knowledgeBase(),
            document.archived(),
            document.maintainerId(),
            document.maintainerName(),
            document.tags(),
            document.category(),
            document.knowledgeStatus(),
            document.reviewDueAt(),
            document.verifiedAt(),
            document.itemKind(),
            document.targetObjectType(),
            document.targetObjectId(),
            targetRoute,
            document.displayMode(),
            document.targetTitleStrategy(),
            document.entryAlias(),
            targetSummary
        );
    }

    private String unavailableTargetTitle(ObjectAccessState accessState) {
        return switch (accessState) {
            case forbidden -> "无权限对象入口";
            case disabled -> "已停用对象入口";
            case deleted -> "已删除对象入口";
            case not_found -> "不存在对象入口";
            case invalid -> "无效对象入口";
            default -> "不可访问对象入口";
        };
    }

    private Map<String, Object> knowledgeNodeAuditPayload(UUID spaceId, KnowledgeBaseItem document) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("spaceId", spaceId.toString());
        payload.put("nodeKind", document.itemKind());
        payload.put("contentType", document.contentType());
        if (document.targetObjectType() != null) {
            payload.put("targetObjectType", document.targetObjectType());
        }
        if (document.targetObjectId() != null) {
            payload.put("targetObjectId", document.targetObjectId().toString());
        }
        return payload;
    }

    private Comparator<KnowledgeBaseItem> documentOrder() {
        return Comparator
            .comparingInt(KnowledgeBaseItem::sortOrder)
            .thenComparing(document -> document.title().toLowerCase(Locale.ROOT))
            .thenComparing(KnowledgeBaseItem::updatedAt, Comparator.reverseOrder());
    }

    private String normalizeItemContentType(String contentType) {
        String type = contentType == null || contentType.isBlank() ? "markdown" : contentType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("markdown", "folder", "object_ref", "external_link").contains(type)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid knowledge item type");
        }
        return type;
    }

    private String nodeKindFor(String contentType) {
        return switch (contentType) {
            case "folder" -> "directory";
            case "object_ref" -> "object_ref";
            case "external_link" -> "external_link";
            default -> "content";
        };
    }

    private String normalizeTargetObjectType(String contentType, String targetObjectType) {
        if ("external_link".equals(contentType)) {
            return "external_link";
        }
        if (!"object_ref".equals(contentType)) {
            return null;
        }
        if (targetObjectType == null || targetObjectType.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Target object type is required");
        }
        String normalized = targetObjectType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("knowledge_content", "base", "file", "project", "external_link").contains(normalized)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid target object type");
        }
        return normalized;
    }

    private UUID normalizeTargetObjectId(String contentType, UUID targetObjectId) {
        if ("object_ref".equals(contentType) && targetObjectId == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Target object id is required");
        }
        return "object_ref".equals(contentType) ? targetObjectId : null;
    }

    private String normalizeTargetRoute(String contentType, String targetRoute) {
        if (!"external_link".equals(contentType)) {
            return null;
        }
        if (targetRoute == null || targetRoute.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Target route is required");
        }
        String normalized = targetRoute.trim();
        if (normalized.length() > 1024) {
            return normalized.substring(0, 1024);
        }
        return normalized;
    }

    private String resolveCanonicalTargetRoute(CurrentUser currentUser, String targetObjectType, UUID targetObjectId) {
        PlatformObjectSummary target = objectResolverRegistry.resolve(currentUser, targetObjectType, targetObjectId);
        if (target.accessState() == ObjectAccessState.forbidden) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Target object is not accessible"
            );
        }
        if (target.accessState() != ObjectAccessState.available) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Target object does not exist"
            );
        }
        if (target.webPath() == null || target.webPath().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Target object has no canonical route"
            );
        }
        return target.webPath();
    }

    private String normalizeDisplayMode(String displayMode) {
        String normalized = displayMode == null || displayMode.isBlank() ? "default" : displayMode.trim().toLowerCase(Locale.ROOT);
        if (!List.of("default", "inline", "preview", "link").contains(normalized)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid display mode");
        }
        return normalized;
    }

    private String normalizeTitleStrategy(String targetTitleStrategy) {
        String normalized = targetTitleStrategy == null || targetTitleStrategy.isBlank() ? "manual" : targetTitleStrategy.trim().toLowerCase(Locale.ROOT);
        if (!List.of("manual", "follow_target", "alias").contains(normalized)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid title strategy");
        }
        return normalized;
    }

    private String normalizeEntryAlias(String entryAlias) {
        if (entryAlias == null || entryAlias.isBlank()) {
            return null;
        }
        String normalized = entryAlias.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private List<KnowledgeBaseItemTreeNode> buildTree(List<KnowledgeBaseItem> knowledge_base_items) {
        Map<UUID, List<KnowledgeBaseItem>> byParent = new HashMap<>();
        Set<UUID> visibleIds = new LinkedHashSet<>();
        for (KnowledgeBaseItem document : knowledge_base_items) {
            visibleIds.add(document.id());
            byParent.computeIfAbsent(document.parentId(), ignored -> new ArrayList<>()).add(document);
        }
        for (List<KnowledgeBaseItem> siblings : byParent.values()) {
            siblings.sort(documentOrder());
        }
        List<KnowledgeBaseItem> roots = knowledge_base_items.stream()
            .filter(document -> document.parentId() == null || !visibleIds.contains(document.parentId()))
            .sorted(documentOrder())
            .toList();
        Set<UUID> visited = new LinkedHashSet<>();
        List<KnowledgeBaseItemTreeNode> nodes = new ArrayList<>();
        for (KnowledgeBaseItem root : roots) {
            nodes.add(buildTreeNode(root, "", 0, byParent, visited));
        }
        return nodes;
    }

    private KnowledgeBaseItemTreeNode buildTreeNode(
        KnowledgeBaseItem document,
        String parentPath,
        int depth,
        Map<UUID, List<KnowledgeBaseItem>> byParent,
        Set<UUID> visited
    ) {
        visited.add(document.id());
        String path = parentPath.isBlank() ? document.title() : parentPath + " / " + document.title();
        List<KnowledgeBaseItemTreeNode> children = new ArrayList<>();
        for (KnowledgeBaseItem child : byParent.getOrDefault(document.id(), List.of())) {
            if (!visited.contains(child.id())) {
                children.add(buildTreeNode(child, path, depth + 1, byParent, visited));
            }
        }
        return new KnowledgeBaseItemTreeNode(document, path, depth, children.size(), !children.isEmpty(), children);
    }

    private List<KnowledgeBaseItem> idsToDocuments(List<UUID> ids, Map<UUID, KnowledgeBaseItem> byId, int limit) {
        return ids.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .limit(limit)
            .toList();
    }

    private String normalizeSubscriptionTargetType(String targetType) {
        String normalized = targetType == null || targetType.isBlank() ? "knowledge_base" : targetType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("knowledge_base", "knowledge_content").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription target type");
        }
        return normalized;
    }

    private UUID normalizeSubscriptionTarget(CurrentUser currentUser, KnowledgeBaseSpaceSummary space, String targetType, UUID targetId) {
        if ("knowledge_base".equals(targetType)) {
            UUID normalizedTargetId = targetId == null ? space.id() : targetId;
            if (!space.id().equals(normalizedTargetId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription target must be the current knowledge base");
            }
            return normalizedTargetId;
        }
        if (targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document subscription target is required");
        }
        requireItemInsideKnowledgeBase(currentUser, space.rootItemId(), targetId);
        contentService.getContent(currentUser, targetId);
        return targetId;
    }

    private void requireItemInsideKnowledgeBase(CurrentUser currentUser, UUID rootItemId, UUID itemId) {
        if (rootItemId.equals(itemId)) {
            return;
        }
        contentService.documentPath(currentUser, itemId).stream()
            .filter(item -> rootItemId.equals(item.id()))
            .findFirst()
            .orElseThrow(this::itemNotFound);
    }

    private void requireContentItem(CurrentUser currentUser, KnowledgeBaseSpaceSummary space, UUID itemId) {
        if (space.rootItemId().equals(itemId)) {
            throw itemNotFound();
        }
        KnowledgeBaseSpaceSummary actualSpace = knowledgeBaseSpaceRepository.findSpaceByItemId(currentUser.workspaceId(), itemId)
            .orElseThrow(this::itemNotFound);
        if (!space.id().equals(actualSpace.id())) {
            throw itemNotFound();
        }
    }

    private void requireContentRoute(CurrentUser currentUser, UUID spaceId, UUID itemId) {
        KnowledgeBaseSpaceSummary space = knowledgeBaseSpaceRepository.findSpace(currentUser.workspaceId(), spaceId)
            .orElseThrow(this::itemNotFound);
        requireContentItem(currentUser, space, itemId);
    }

    private ResponseStatusException itemNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base item not found");
    }

    private String uniqueCode(UUID workspaceId, String baseCode, UUID excludeId) {
        String candidate = baseCode;
        int suffix = 2;
        while (knowledgeBaseSpaceRepository.codeExists(workspaceId, candidate, excludeId)) {
            candidate = baseCode + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge base name is required");
        }
        String normalized = name.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private String normalizeCode(String code, String name) {
        String source = code == null || code.isBlank() ? name : code;
        String normalized = source.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) {
            normalized = "kb-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64).replaceAll("-+$", "");
    }

    private String normalizeVisibility(String visibility) {
        String normalized = visibility == null || visibility.isBlank() ? "private" : visibility.trim().toLowerCase(Locale.ROOT);
        if (!List.of("private", "workspace").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid knowledge base visibility");
        }
        return normalized;
    }

    private String normalizeDefaultPermission(String permissionLevel) {
        String normalized = permissionLevel == null || permissionLevel.isBlank() ? "view" : permissionLevel.trim().toLowerCase(Locale.ROOT);
        if (!List.of("view", "comment", "edit").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid knowledge base default permission");
        }
        return normalized;
    }

    private String normalizeNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}




