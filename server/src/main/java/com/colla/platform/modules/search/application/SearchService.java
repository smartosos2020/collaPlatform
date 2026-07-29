package com.colla.platform.modules.search.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.modules.search.domain.SearchModels.SearchResponse;
import com.colla.platform.modules.search.domain.SearchModels.AdminGovernanceSearchResponse;
import com.colla.platform.modules.search.domain.SearchModels.AdminGovernanceSearchResult;
import com.colla.platform.modules.search.domain.SearchModels.SearchFilters;
import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import com.colla.platform.modules.search.domain.SearchModels.SearchFacet;
import com.colla.platform.modules.search.infrastructure.SearchRepository;
import com.colla.platform.modules.platform.contract.PlatformSearchProjectionProvider;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchService {
    private final SearchRepository searchRepository;
    private final SearchIndexService searchIndexService;
    private final PlatformObjectResolverRegistry objectResolverRegistry;
    private final KnowledgeContentRepository contentRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final PlatformSearchProjectionProvider workItemSearchProvider;
    private final JwtTokenProperties tokenProperties;
    private final MeterRegistry meterRegistry;

    public SearchService(
        SearchRepository searchRepository,
        SearchIndexService searchIndexService,
        PlatformObjectResolverRegistry objectResolverRegistry,
        KnowledgeContentRepository contentRepository,
        PermissionService permissionService,
        AuditService auditService,
        List<PlatformSearchProjectionProvider> searchProjectionProviders,
        JwtTokenProperties tokenProperties,
        MeterRegistry meterRegistry
    ) {
        this.searchRepository = searchRepository;
        this.searchIndexService = searchIndexService;
        this.objectResolverRegistry = objectResolverRegistry;
        this.contentRepository = contentRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.workItemSearchProvider = searchProjectionProviders.stream()
            .filter(provider -> "work_item".equals(provider.objectType()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("WorkItem search projection provider is required"));
        this.tokenProperties = tokenProperties;
        this.meterRegistry = meterRegistry;
    }

    public SearchResponse search(
        CurrentUser currentUser,
        String query,
        int limit,
        UUID knowledgeBaseId,
        UUID directoryId,
        String contentType,
        List<String> tags,
        UUID maintainerId,
        String knowledgeStatus,
        String updatedFrom,
        String updatedTo,
        List<UUID> spaceIds,
        List<String> objectTypes,
        List<String> objectStatuses,
        List<String> participantRoles,
        String cursor
    ) {
        Timer.Sample timer = Timer.start(meterRegistry);
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be at least 2 characters");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        List<UUID> safeSpaces = normalizeUuids(spaceIds, 20, "spaceIds");
        List<String> safeTypes = normalizeValues(
            objectTypes,
            Set.of("knowledge_content", "base", "base_table", "base_record", "message", "work_item"),
            10,
            "objectTypes"
        );
        List<String> safeStatuses = normalizeValues(
            objectStatuses,
            Set.of("active", "draft", "resolved", "closed", "archived"),
            10,
            "objectStatuses"
        );
        List<String> safeRoles = normalizeValues(
            participantRoles,
            Set.of("owner", "assignee", "collaborator", "watcher"),
            4,
            "participantRoles"
        );
        SearchFilters filters = new SearchFilters(
            knowledgeBaseId,
            directoryId,
            normalizeContentType(contentType),
            normalizeTags(tags),
            maintainerId,
            normalizeKnowledgeStatus(knowledgeStatus),
            parseInstantOrDate(updatedFrom, false),
            parseInstantOrDate(updatedTo, true),
            safeSpaces,
            safeTypes,
            safeStatuses,
            safeRoles
        );
        int offset = decodeCursor(currentUser, normalizedQuery, filters, cursor);
        int scanLimit = 500;
        List<SearchResult> candidates = searchRepository.search(
            currentUser.workspaceId(), currentUser.id(), normalizedQuery, filters, scanLimit
        );
        Set<UUID> allowedWorkItems = allowedWorkItems(currentUser, candidates, Set.copyOf(safeRoles));
        List<SearchResult> visible = candidates.stream()
            .filter(result -> isUserContentResult(result.objectType()))
            .filter(result -> safeRoles.isEmpty() || "work_item".equals(result.objectType()))
            .filter(result -> !"work_item".equals(result.objectType()) || allowedWorkItems.contains(result.objectId()))
            .map(result -> hydrateResult(currentUser, result))
            .filter(result -> "available".equals(result.accessState()))
            .toList();
        List<SearchResult> items = visible.stream().skip(offset).limit(boundedLimit).toList();
        boolean hasMore = visible.size() > offset + items.size();
        String nextCursor = hasMore && !items.isEmpty()
            ? encodeCursor(currentUser, normalizedQuery, filters, offset + items.size())
            : null;
        if (items.isEmpty() && knowledgeBaseId != null) {
            auditService.log(
                currentUser,
                "knowledge.search.no_result",
                "knowledge_base",
                knowledgeBaseId,
                Map.of("query", normalizedQuery)
            );
        }
        List<SearchFacet> facets = items.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                SearchResult::objectType,
                java.util.TreeMap::new,
                java.util.stream.Collectors.counting()
            ))
            .entrySet().stream()
            .map(entry -> new SearchFacet("objectType", entry.getKey(), Math.toIntExact(entry.getValue())))
            .toList();
        meterRegistry.counter("colla.search.query.total", "scope", "user_content").increment();
        meterRegistry.summary("colla.search.visible.results", "scope", "user_content").record(items.size());
        timer.stop(Timer.builder("colla.search.query.duration")
            .description("Permission-calibrated user content search duration")
            .tag("scope", "user_content")
            .register(meterRegistry));
        return new SearchResponse(normalizedQuery, "user_content", items, facets, nextCursor);
    }

    public AdminGovernanceSearchResponse searchGovernance(CurrentUser currentUser, String query, int limit) {
        if (!permissionService.canAccessAdmin(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin governance search permission required");
        }
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        List<AdminGovernanceSearchResult> items = governanceCatalog().stream()
            .filter(item -> normalizedQuery.isBlank()
                || item.title().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || item.description().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || item.governanceType().toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .limit(boundedLimit)
            .toList();
        return new AdminGovernanceSearchResponse(normalizedQuery, "admin_governance", items);
    }

    private SearchResult hydrateResult(CurrentUser currentUser, SearchResult result) {
        PlatformObjectSummary summary = objectResolverRegistry.resolve(currentUser, result.objectType(), result.objectId());
        if (summary.accessState() != ObjectAccessState.available) {
            return new SearchResult(
                result.objectType(),
                result.objectId(),
                unavailableTitle(summary.accessState()),
                null,
                null,
                null,
                result.score(),
                result.updatedAt(),
                summary.accessState().name(),
                "对象当前为 " + summary.accessState().name() + " 状态，搜索结果不展示原始内容。",
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null
            );
        }
        if ("knowledge_content".equals(result.objectType())) {
            KnowledgeBaseItem document = contentRepository.findItem(currentUser.workspaceId(), result.objectId()).orElse(null);
            if (document != null && "object_ref".equals(document.contentType()) && document.targetObjectType() != null && document.targetObjectId() != null) {
                PlatformObjectSummary targetSummary = objectResolverRegistry.resolve(currentUser, document.targetObjectType(), document.targetObjectId());
                if (targetSummary.accessState() != ObjectAccessState.available) {
                    return new SearchResult(
                        result.objectType(),
                        result.objectId(),
                        unavailableTitle(targetSummary.accessState()),
                        null,
                        null,
                        null,
                        result.score(),
                        result.updatedAt(),
                        targetSummary.accessState().name(),
                        "对象入口目标当前为 " + targetSummary.accessState().name() + " 状态，搜索结果不展示原始内容。",
                        result.knowledgeBaseId(),
                        result.knowledgeBaseName(),
                        result.parentItemId(),
                        result.directoryPath(),
                        List.of(),
                        null,
                        null,
                        null,
                        result.contentType(),
                        result.hitSource()
                    );
                }
            }
        }
        return new SearchResult(
            result.objectType(),
            result.objectId(),
            summary.title() == null ? result.title() : summary.title(),
            result.excerpt(),
            "work_item".equals(result.objectType())
                ? summary.webPath()
                : result.webPath() == null ? summary.webPath() : result.webPath(),
            "work_item".equals(result.objectType())
                ? summary.deepLink()
                : summary.deepLink() == null ? result.deepLink() : summary.deepLink(),
            result.score(),
            result.updatedAt(),
            summary.accessState().name(),
            availableExplanation(summary),
            result.knowledgeBaseId(),
            result.knowledgeBaseName(),
            result.parentItemId(),
            result.directoryPath(),
            result.tags(),
            result.maintainerId(),
            result.maintainerName(),
            result.knowledgeStatus(),
            result.contentType(),
            result.hitSource()
        );
    }

    private boolean isUserContentResult(String objectType) {
        return List.of("knowledge_content", "base", "base_table", "base_record", "message", "approval", "work_item").contains(objectType);
    }

    private List<UUID> normalizeUuids(List<UUID> values, int max, String field) {
        if (values == null) return List.of();
        List<UUID> result = values.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (result.size() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " exceeds hard limit");
        }
        return result;
    }

    private Set<UUID> allowedWorkItems(
        CurrentUser user,
        List<SearchResult> candidates,
        Set<String> participantRoles
    ) {
        List<UUID> ids = candidates.stream()
            .filter(result -> "work_item".equals(result.objectType()))
            .map(SearchResult::objectId)
            .distinct()
            .toList();
        java.util.LinkedHashSet<UUID> allowed = new java.util.LinkedHashSet<>();
        for (int offset = 0; offset < ids.size(); offset += PlatformSearchProjectionProvider.MAX_DECISION_BATCH_SIZE) {
            allowed.addAll(workItemSearchProvider.allowed(
                user,
                ids.subList(offset, Math.min(ids.size(), offset + PlatformSearchProjectionProvider.MAX_DECISION_BATCH_SIZE)),
                participantRoles
            ));
        }
        return Set.copyOf(allowed);
    }

    private List<String> normalizeValues(List<String> values, Set<String> allowed, int max, String field) {
        if (values == null) return List.of();
        List<String> result = values.stream()
            .filter(java.util.Objects::nonNull)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
        if (result.size() > max || !allowed.containsAll(result)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field + " filter");
        }
        return result;
    }

    private String encodeCursor(CurrentUser user, String query, SearchFilters filters, int offset) {
        String payload = user.workspaceId() + "|" + user.id() + "|" + queryHash(query, filters) + "|" + offset;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (payload + "|" + sign(payload)).getBytes(StandardCharsets.UTF_8)
        );
    }

    private int decodeCursor(CurrentUser user, String query, SearchFilters filters, String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            String payload = String.join("|", parts[0], parts[1], parts[2], parts[3]);
            if (parts.length != 5
                || !parts[0].equals(user.workspaceId().toString())
                || !parts[1].equals(user.id().toString())
                || !parts[2].equals(queryHash(query, filters))
                || !MessageDigest.isEqual(
                    sign(payload).getBytes(StandardCharsets.US_ASCII),
                    parts[4].getBytes(StandardCharsets.US_ASCII)
                )) {
                throw new IllegalArgumentException("cursor binding");
            }
            int offset = Integer.parseInt(parts[3]);
            if (offset < 0 || offset > 450) throw new IllegalArgumentException("cursor range");
            return offset;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search cursor is invalid", exception);
        }
    }

    private String queryHash(String query, SearchFilters filters) {
        return sign(query + "|" + filters).substring(0, 16);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenProperties.getAccessSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign search cursor", exception);
        }
    }

    private List<AdminGovernanceSearchResult> governanceCatalog() {
        return List.of(
            new AdminGovernanceSearchResult("permission", "权限排查", "按成员、资源和动作排查权限来源、风险和缺口。", "/admin/permission-governance", "high"),
            new AdminGovernanceSearchResult("permission_risk", "权限风险处置", "检索过期授权、孤立授权、失效主体和高风险组合，并预览单项修复。", "/admin/permission-governance?severity=high", "critical"),
            new AdminGovernanceSearchResult("permission_grant", "授权上下文", "从授权主体定位成员、资源、权限解释和关联审计。", "/admin/permission-governance", "high"),
            new AdminGovernanceSearchResult("audit", "审计日志", "按来源 UI、动作、对象和操作者查询后台与用户侧审计。", "/admin/audit-logs", "medium"),
            new AdminGovernanceSearchResult("audit_permission", "权限变更审计", "快捷查看权限授予、撤销、请求决定和风险修复记录。", "/admin/audit-logs?permissionOnly=true", "high"),
            new AdminGovernanceSearchResult("application", "应用治理", "Base、项目、消息和审批的策略、风险和治理深链。", "/admin/app-governance", "medium"),
            new AdminGovernanceSearchResult("knowledge", "知识库治理", "知识空间、内容状态、订阅、权限和知识风险治理。", "/admin/knowledge-bases", "medium"),
            new AdminGovernanceSearchResult("identity", "组织与成员", "组织架构、成员、用户组和角色权限管理。", "/admin/users", "high")
        );
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("space", "folder", "markdown", "object_ref", "external_link").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document type filter");
        }
        return normalized;
    }

    private String normalizeKnowledgeStatus(String knowledgeStatus) {
        if (knowledgeStatus == null || knowledgeStatus.isBlank()) {
            return null;
        }
        String normalized = knowledgeStatus.trim().toLowerCase(Locale.ROOT);
        if (!List.of("draft", "verified", "needs_review", "outdated", "archived").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid knowledge status filter");
        }
        return normalized;
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
                String value = part.trim().toLowerCase(Locale.ROOT);
                if (!value.isBlank() && !normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        return normalized;
    }

    private Instant parseInstantOrDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(value.trim());
                return endOfDay
                    ? date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                    : date.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid updated time filter");
            }
        }
    }

    private String unavailableTitle(ObjectAccessState accessState) {
        return switch (accessState) {
            case forbidden -> "无权限对象";
            case deleted -> "已删除对象";
            case not_found -> "不存在对象";
            case invalid -> "无效对象";
            default -> "不可访问对象";
        };
    }

    private String availableExplanation(PlatformObjectSummary summary) {
        Object level = summary.metadata().get("permissionLevel");
        if (level != null) {
            return "当前用户通过 " + level + " 权限可查看该对象。";
        }
        return switch (summary.objectType()) {
            case "work_item" -> "当前用户是项目空间成员，可查看该工作项。";
            case "message" -> "当前用户是会话成员，可查看该消息。";
            case "approval" -> "当前用户是审批申请人、处理人或管理员，可查看该审批。";
            default -> "当前用户具备查看该对象的权限。";
        };
    }
}
