package com.colla.platform.modules.doc.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseAccessDocumentStat;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseAccessStats;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseBulkGovernanceResult;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseDiscovery;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseGovernanceDashboard;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseGovernanceRisk;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseHealthMetrics;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSearchTermStat;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceDetail;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSpaceSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeBaseSubscription;
import com.colla.platform.modules.doc.infrastructure.DocumentRepository;
import com.colla.platform.modules.doc.infrastructure.KnowledgeBaseSpaceRepository;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectReference;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.modules.permission.application.PermissionDecisionService;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
    private final DocumentRepository documentRepository;
    private final PlatformObjectRepository objectRepository;
    private final DocumentService documentService;
    private final PermissionDecisionService permissionDecisionService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBaseSpaceService(
        KnowledgeBaseSpaceRepository knowledgeBaseSpaceRepository,
        DocumentRepository documentRepository,
        PlatformObjectRepository objectRepository,
        DocumentService documentService,
        PermissionDecisionService permissionDecisionService,
        AuditService auditService,
        JdbcTemplate jdbcTemplate
    ) {
        this.knowledgeBaseSpaceRepository = knowledgeBaseSpaceRepository;
        this.documentRepository = documentRepository;
        this.objectRepository = objectRepository;
        this.documentService = documentService;
        this.permissionDecisionService = permissionDecisionService;
        this.auditService = auditService;
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
        DocumentSummary root = documentRepository.findDocument(currentUser.workspaceId(), space.rootDocumentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base root document not found"));
        DocumentSummary home = documentRepository.findDocument(currentUser.workspaceId(), space.homeDocumentId())
            .orElse(root);
        return new KnowledgeBaseSpaceDetail(space, root, home);
    }

    @Transactional
    public KnowledgeBaseDiscovery discovery(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireView(currentUser, spaceId);
        List<DocumentSummary> documents = visibleKnowledgeDocuments(currentUser, space.rootDocumentId());
        List<DocumentSummary> markdownDocuments = documents.stream()
            .filter(document -> "markdown".equals(document.docType()))
            .filter(document -> !document.archived())
            .toList();
        Map<UUID, DocumentSummary> byId = markdownDocuments.stream()
            .collect(java.util.stream.Collectors.toMap(DocumentSummary::id, document -> document, (left, right) -> left));
        List<DocumentSummary> recentAccessed = idsToDocuments(
            objectRepository.listRecentAccesses(currentUser.workspaceId(), currentUser.id(), 50).stream()
                .filter(reference -> "document".equals(reference.objectType()))
                .map(PlatformObjectReference::objectId)
                .toList(),
            byId,
            6
        );
        List<DocumentSummary> favorites = idsToDocuments(
            objectRepository.listFavorites(currentUser.workspaceId(), currentUser.id(), 50).stream()
                .filter(reference -> "document".equals(reference.objectType()))
                .map(PlatformObjectReference::objectId)
                .toList(),
            byId,
            6
        );
        LocalDate reviewCutoff = LocalDate.now().plusDays(7);
        List<DocumentSummary> maintainedByMe = markdownDocuments.stream()
            .filter(document -> currentUser.id().equals(document.maintainerId()))
            .sorted(Comparator.comparing(DocumentSummary::updatedAt).reversed())
            .limit(6)
            .toList();
        List<DocumentSummary> dueForReview = markdownDocuments.stream()
            .filter(document -> currentUser.id().equals(document.maintainerId()) || currentUser.id().equals(space.ownerId()))
            .filter(document -> "needs_review".equals(document.knowledgeStatus())
                || "outdated".equals(document.knowledgeStatus())
                || (document.reviewDueAt() != null && !document.reviewDueAt().isAfter(reviewCutoff)))
            .sorted(Comparator.comparing((DocumentSummary document) -> document.reviewDueAt() == null ? LocalDate.MAX : document.reviewDueAt()))
            .limit(6)
            .toList();
        Set<UUID> priorityIds = new HashSet<>();
        List<DocumentSummary> popular = new ArrayList<>();
        for (DocumentSummary document : favorites) {
            if (priorityIds.add(document.id())) {
                popular.add(document);
            }
        }
        for (DocumentSummary document : recentAccessed) {
            if (priorityIds.add(document.id())) {
                popular.add(document);
            }
        }
        markdownDocuments.stream()
            .sorted(Comparator.comparing(DocumentSummary::updatedAt).reversed())
            .filter(document -> priorityIds.add(document.id()))
            .limit(6)
            .forEach(popular::add);
        List<DocumentSummary> recommended = new ArrayList<>();
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
                .sorted(Comparator.comparing(DocumentSummary::updatedAt).reversed())
                .filter(document -> recommended.stream().noneMatch(existing -> existing.id().equals(document.id())))
                .limit(6 - recommended.size())
                .forEach(recommended::add);
        }
        List<DocumentSummary> subscribedDocuments = idsToDocuments(
            knowledgeBaseSpaceRepository.listSubscribedDocumentIds(currentUser.workspaceId(), currentUser.id(), space.rootDocumentId(), 20),
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
        List<DocumentSummary> documents = documentRepository.listKnowledgeBaseDocuments(currentUser.workspaceId(), space.rootDocumentId());
        List<KnowledgeBaseGovernanceRisk> risks = governanceRisks(currentUser.workspaceId(), space, documents);
        return new KnowledgeBaseGovernanceDashboard(
            space.id(),
            healthMetrics(documents, risks),
            risks,
            accessStats(currentUser.workspaceId(), documents, space.id())
        );
    }

    @Transactional
    public KnowledgeBaseBulkGovernanceResult bulkGovernance(
        CurrentUser currentUser,
        UUID spaceId,
        List<UUID> documentIds,
        UUID maintainerId,
        List<String> tags,
        boolean replaceTags,
        boolean archive,
        boolean requestReview,
        LocalDate reviewDueAt
    ) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        Map<UUID, DocumentSummary> documentsById = documentRepository.listKnowledgeBaseDocuments(currentUser.workspaceId(), space.rootDocumentId()).stream()
            .collect(java.util.stream.Collectors.toMap(DocumentSummary::id, document -> document, (left, right) -> left));
        List<UUID> targets = (documentIds == null ? List.<UUID>of() : documentIds).stream()
            .filter(Objects::nonNull)
            .distinct()
            .limit(100)
            .filter(id -> documentsById.containsKey(id) && !id.equals(space.rootDocumentId()))
            .toList();
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk governance requires at least one document in the knowledge base");
        }
        List<String> normalizedTags = normalizeTags(tags);
        int updated = 0;
        int archived = 0;
        int reviewRequested = 0;
        for (UUID documentId : targets) {
            DocumentSummary document = documentsById.get(documentId);
            if (maintainerId != null || !normalizedTags.isEmpty() || requestReview) {
                List<String> nextTags = replaceTags
                    ? normalizedTags
                    : mergeTags(document.tags(), normalizedTags);
                String nextStatus = requestReview ? "needs_review" : document.knowledgeStatus();
                LocalDate nextReviewDueAt = requestReview
                    ? (reviewDueAt == null ? LocalDate.now() : reviewDueAt)
                    : document.reviewDueAt();
                documentRepository.updateKnowledgeMetadata(
                    currentUser.workspaceId(),
                    documentId,
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
                documentService.archiveDocument(currentUser, documentId);
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
        csv.append(row("health", "documentCount", health.documentCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "activeDocumentCount", health.activeDocumentCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "outdatedDocumentCount", health.outdatedDocumentCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "unmaintainedDocumentCount", health.unmaintainedDocumentCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "ownerlessDocumentCount", health.ownerlessDocumentCount(), null, null, null, null, null, null, null));
        csv.append(row("health", "highRiskPermissionCount", health.highRiskPermissionCount(), null, null, null, null, null, null, null));
        csv.append(row("access", "visitorCount", dashboard.accessStats().visitorCount(), null, null, null, null, null, null, null));
        csv.append(row("access", "accessCount", dashboard.accessStats().accessCount(), null, null, null, null, null, null, null));
        for (KnowledgeBaseGovernanceRisk risk : dashboard.risks()) {
            csv.append(row("risk", risk.ruleCode(), risk.severity(), risk.resourceType(), risk.resourceId(), risk.title(), risk.subjectType(), risk.subjectName(), risk.permissionLevel(), risk.reason()));
        }
        for (KnowledgeBaseAccessDocumentStat stat : dashboard.accessStats().popularDocuments()) {
            csv.append(row("popular_document", stat.document().id().toString(), stat.accessCount(), "document", stat.document().id(), stat.document().title(), null, null, null, "访客 " + stat.visitorCount()));
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
        DocumentDetail root = documentService.createDocument(
            currentUser,
            null,
            normalizedName,
            "space",
            "# " + normalizedName,
            normalizedDescription,
            normalizedCoverUrl,
            normalizedDefaultPermission,
            true
        );
        DocumentDetail home = documentService.createDocument(
            currentUser,
            root.document().id(),
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
            root.document().id(),
            home.document().id(),
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
            "rootDocumentId", root.document().id().toString(),
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
        UUID homeDocumentId,
        String defaultPermissionLevel
    ) {
        KnowledgeBaseSpaceSummary before = requireManage(currentUser, spaceId);
        String normalizedName = normalizeName(name == null || name.isBlank() ? before.name() : name);
        String normalizedCode = uniqueCode(
            currentUser.workspaceId(),
            normalizeCode(code == null || code.isBlank() ? before.code() : code, normalizedName),
            before.id()
        );
        UUID normalizedHomeDocumentId = homeDocumentId == null ? before.homeDocumentId() : homeDocumentId;
        requireDocumentInsideKnowledgeBase(currentUser, before.rootDocumentId(), normalizedHomeDocumentId);
        knowledgeBaseSpaceRepository.updateSpace(
            currentUser.workspaceId(),
            spaceId,
            normalizedName,
            normalizedCode,
            normalizeNullable(description, 512),
            normalizeNullable(icon, 64),
            normalizeNullable(coverUrl, 1024),
            normalizeVisibility(visibility == null ? before.visibility() : visibility),
            normalizedHomeDocumentId,
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
            "rootDocumentId", space.rootDocumentId().toString()
        ));
        return getSpace(currentUser, spaceId);
    }

    @Transactional
    public KnowledgeBaseSpaceDetail restoreSpace(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        knowledgeBaseSpaceRepository.updateStatus(currentUser.workspaceId(), space.id(), "active", currentUser.id());
        DocumentSummary root = documentRepository.findDocument(currentUser.workspaceId(), space.rootDocumentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base root document not found"));
        if (root.archived()) {
            documentService.restoreDocument(currentUser, root.id());
        }
        auditService.log(currentUser, "knowledge_base.restored", "knowledge_base", space.id(), Map.of(
            "rootDocumentId", space.rootDocumentId().toString()
        ));
        return getSpace(currentUser, spaceId);
    }

    @Transactional
    public KnowledgeBaseSpaceDetail archiveSpace(CurrentUser currentUser, UUID spaceId) {
        KnowledgeBaseSpaceSummary space = requireManage(currentUser, spaceId);
        knowledgeBaseSpaceRepository.updateStatus(currentUser.workspaceId(), space.id(), "archived", currentUser.id());
        documentService.archiveDocument(currentUser, space.rootDocumentId());
        auditService.log(currentUser, "knowledge_base.archived", "knowledge_base", space.id(), Map.of(
            "rootDocumentId", space.rootDocumentId().toString()
        ));
        return getSpace(currentUser, spaceId);
    }

    private KnowledgeBaseHealthMetrics healthMetrics(List<DocumentSummary> documents, List<KnowledgeBaseGovernanceRisk> risks) {
        long documentCount = documents.stream().filter(document -> "markdown".equals(document.docType())).count();
        long activeDocumentCount = documents.stream()
            .filter(document -> "markdown".equals(document.docType()) && !document.archived())
            .count();
        long outdatedDocumentCount = documents.stream()
            .filter(document -> "markdown".equals(document.docType()) && !document.archived())
            .filter(document -> "outdated".equals(document.knowledgeStatus())
                || "needs_review".equals(document.knowledgeStatus())
                || (document.reviewDueAt() != null && !document.reviewDueAt().isAfter(LocalDate.now())))
            .count();
        long unmaintainedDocumentCount = documents.stream()
            .filter(document -> "markdown".equals(document.docType()) && !document.archived())
            .filter(document -> document.maintainerId() == null)
            .count();
        long ownerlessDocumentCount = risks.stream().filter(risk -> "document_without_owner".equals(risk.ruleCode())).count();
        long highRiskPermissionCount = risks.stream()
            .filter(risk -> List.of("critical", "high").contains(risk.severity()))
            .count();
        return new KnowledgeBaseHealthMetrics(
            documentCount,
            activeDocumentCount,
            outdatedDocumentCount,
            unmaintainedDocumentCount,
            ownerlessDocumentCount,
            highRiskPermissionCount
        );
    }

    private List<KnowledgeBaseGovernanceRisk> governanceRisks(UUID workspaceId, KnowledgeBaseSpaceSummary space, List<DocumentSummary> documents) {
        Map<UUID, DocumentSummary> documentsById = documents.stream()
            .collect(java.util.stream.Collectors.toMap(DocumentSummary::id, document -> document, (left, right) -> left));
        List<KnowledgeBaseGovernanceRisk> risks = new ArrayList<>();
        documents.stream()
            .filter(document -> "markdown".equals(document.docType()) && !document.archived())
            .filter(document -> document.maintainerId() == null)
            .forEach(document -> risks.add(new KnowledgeBaseGovernanceRisk(
                "unmaintained:" + document.id(),
                "document_without_maintainer",
                "medium",
                "document",
                document.id(),
                document.title(),
                null,
                null,
                null,
                null,
                "知识文档未设置维护人。",
                "/docs/" + document.id()
            )));
        documents.stream()
            .filter(document -> "markdown".equals(document.docType()) && !document.archived())
            .filter(document -> "outdated".equals(document.knowledgeStatus())
                || "needs_review".equals(document.knowledgeStatus())
                || (document.reviewDueAt() != null && !document.reviewDueAt().isAfter(LocalDate.now())))
            .forEach(document -> risks.add(new KnowledgeBaseGovernanceRisk(
                "outdated:" + document.id(),
                "document_review_overdue",
                "high",
                "document",
                document.id(),
                document.title(),
                null,
                null,
                null,
                null,
                "知识状态为待复核/已过期，或复核日期已到期。",
                "/docs/" + document.id()
            )));
        risks.addAll(permissionRisks(workspaceId, space, documentsById));
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

    private List<KnowledgeBaseGovernanceRisk> permissionRisks(
        UUID workspaceId,
        KnowledgeBaseSpaceSummary space,
        Map<UUID, DocumentSummary> documentsById
    ) {
        return jdbcTemplate.query(
            """
                with recursive subtree as (
                    select d.id, d.title
                    from documents d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select child.id, child.title
                    from documents child
                    join subtree parent on child.parent_id = parent.id
                    where child.workspace_id = ? and child.deleted_at is null
                ),
                scoped_resources as (
                    select 'knowledge_base'::varchar resource_type, ?::uuid resource_id, ?::varchar title
                    union all
                    select 'document'::varchar, id, title from subtree
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
                where sr.resource_type = 'document'
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
                    documentsById.get(resourceId) == null ? rs.getString("title") : documentsById.get(resourceId).title(),
                    rs.getString("subject_type"),
                    rs.getObject("subject_id", UUID.class),
                    rs.getString("subject_name"),
                    rs.getString("permission_level"),
                    riskReason(rule),
                    "document".equals(resourceType) ? "/docs/" + resourceId : "/knowledge-bases/" + space.id()
                );
            },
            workspaceId,
            space.rootDocumentId(),
            workspaceId,
            space.id(),
            space.name(),
            workspaceId,
            workspaceId
        );
    }

    private KnowledgeBaseAccessStats accessStats(UUID workspaceId, List<DocumentSummary> documents, UUID spaceId) {
        Map<UUID, DocumentSummary> markdownById = documents.stream()
            .filter(document -> "markdown".equals(document.docType()) && !document.archived())
            .collect(java.util.stream.Collectors.toMap(DocumentSummary::id, document -> document, (left, right) -> left));
        if (markdownById.isEmpty()) {
            return new KnowledgeBaseAccessStats(0, 0, List.of(), List.of(), noResultTerms(workspaceId, spaceId));
        }
        List<UUID> documentIds = new ArrayList<>(markdownById.keySet());
        String idArray = documentIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        Long visitorCount = jdbcTemplate.queryForObject(
            "select count(distinct user_id) from object_recent_accesses where workspace_id = ? and object_type = 'document' and object_id = any(string_to_array(?, ',')::uuid[])",
            Long.class,
            workspaceId,
            idArray
        );
        Long accessCount = jdbcTemplate.queryForObject(
            "select coalesce(sum(access_count), 0) from object_recent_accesses where workspace_id = ? and object_type = 'document' and object_id = any(string_to_array(?, ',')::uuid[])",
            Long.class,
            workspaceId,
            idArray
        );
        List<KnowledgeBaseAccessDocumentStat> stats = jdbcTemplate.query(
            """
                select object_id,
                       count(distinct user_id) visitor_count,
                       coalesce(sum(access_count), 0) access_count,
                       max(last_accessed_at) last_accessed_at
                from object_recent_accesses
                where workspace_id = ?
                  and object_type = 'document'
                  and object_id = any(string_to_array(?, ',')::uuid[])
                group by object_id
                """,
            (rs, rowNum) -> new KnowledgeBaseAccessDocumentStat(
                markdownById.get(rs.getObject("object_id", UUID.class)),
                rs.getLong("visitor_count"),
                rs.getLong("access_count"),
                rs.getTimestamp("last_accessed_at") == null ? null : rs.getTimestamp("last_accessed_at").toInstant()
            ),
            workspaceId,
            idArray
        ).stream().filter(stat -> stat.document() != null).toList();
        List<KnowledgeBaseAccessDocumentStat> popular = stats.stream()
            .sorted(Comparator.comparingLong(KnowledgeBaseAccessDocumentStat::accessCount).reversed())
            .limit(5)
            .toList();
        Set<UUID> accessedIds = stats.stream().map(stat -> stat.document().id()).collect(java.util.stream.Collectors.toSet());
        List<KnowledgeBaseAccessDocumentStat> lowAccess = new ArrayList<>();
        markdownById.values().stream()
            .filter(document -> !accessedIds.contains(document.id()))
            .sorted(Comparator.comparing(DocumentSummary::updatedAt))
            .limit(5)
            .map(document -> new KnowledgeBaseAccessDocumentStat(document, 0, 0, null))
            .forEach(lowAccess::add);
        if (lowAccess.size() < 5) {
            stats.stream()
                .sorted(Comparator.comparingLong(KnowledgeBaseAccessDocumentStat::accessCount))
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
        PermissionDecision rootDecision = permissionDecisionService.decide(currentUser, "document", space.rootDocumentId(), "manage");
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
        PermissionDecision rootDecision = permissionDecisionService.decide(currentUser, "document", space.rootDocumentId(), "view");
        return knowledgeBaseDecision.allowed() || rootDecision.allowed();
    }

    private List<DocumentSummary> visibleKnowledgeDocuments(CurrentUser currentUser, UUID rootDocumentId) {
        return documentRepository.listKnowledgeBaseDocuments(currentUser.workspaceId(), rootDocumentId).stream()
            .filter(document -> currentUser.id().equals(document.createdBy())
                || permissionDecisionService.decide(currentUser, "document", document.id(), "view").allowed())
            .toList();
    }

    private List<DocumentSummary> idsToDocuments(List<UUID> ids, Map<UUID, DocumentSummary> byId, int limit) {
        return ids.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .limit(limit)
            .toList();
    }

    private String normalizeSubscriptionTargetType(String targetType) {
        String normalized = targetType == null || targetType.isBlank() ? "knowledge_base" : targetType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("knowledge_base", "document").contains(normalized)) {
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
        requireDocumentInsideKnowledgeBase(currentUser, space.rootDocumentId(), targetId);
        documentService.getDocument(currentUser, targetId);
        return targetId;
    }

    private void requireDocumentInsideKnowledgeBase(CurrentUser currentUser, UUID rootDocumentId, UUID documentId) {
        if (rootDocumentId.equals(documentId)) {
            return;
        }
        documentService.documentPath(currentUser, documentId).stream()
            .filter(item -> rootDocumentId.equals(item.id()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Home document must belong to the knowledge base"));
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
