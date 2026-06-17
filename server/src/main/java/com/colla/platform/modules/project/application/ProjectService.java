package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.im.application.ImService;
import com.colla.platform.modules.im.infrastructure.ImRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.modules.project.domain.ProjectModels.IssueActivity;
import com.colla.platform.modules.project.domain.ProjectModels.IssueAttachment;
import com.colla.platform.modules.project.domain.ProjectModels.IssueComment;
import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.modules.project.domain.ProjectModels.IssueRelation;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectStats;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectSummary;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_.-]{2,64})");

    private final ProjectRepository projectRepository;
    private final ImRepository imRepository;
    private final ImService imService;
    private final PlatformObjectRepository objectRepository;
    private final PlatformObjectResolverRegistry objectResolverRegistry;
    private final DomainEventRepository eventRepository;
    private final FileRepository fileRepository;
    private final AuditService auditService;

    public ProjectService(
        ProjectRepository projectRepository,
        ImRepository imRepository,
        ImService imService,
        PlatformObjectRepository objectRepository,
        PlatformObjectResolverRegistry objectResolverRegistry,
        DomainEventRepository eventRepository,
        FileRepository fileRepository,
        AuditService auditService
    ) {
        this.projectRepository = projectRepository;
        this.imRepository = imRepository;
        this.imService = imService;
        this.objectRepository = objectRepository;
        this.objectResolverRegistry = objectResolverRegistry;
        this.eventRepository = eventRepository;
        this.fileRepository = fileRepository;
        this.auditService = auditService;
    }

    public List<ProjectSummary> listProjects(CurrentUser currentUser) {
        return projectRepository.listProjects(currentUser.workspaceId(), currentUser.id());
    }

    @Transactional
    public ProjectDetail createProject(CurrentUser currentUser, String projectKey, String name, String description, List<UUID> memberIds) {
        String normalizedKey = normalizeProjectKey(projectKey, name);
        List<UUID> members = normalizeMembers(currentUser.id(), memberIds);
        UUID conversationId = imRepository.createConversation(
            currentUser.workspaceId(),
            "project",
            name + " 项目群",
            currentUser.id(),
            currentUser.id()
        );
        UUID projectId = projectRepository.createProject(
            currentUser.workspaceId(),
            normalizedKey,
            name.trim(),
            description,
            conversationId,
            currentUser.id()
        );
        for (UUID memberId : members) {
            projectRepository.addProjectMember(
                currentUser.workspaceId(),
                projectId,
                memberId,
                currentUser.id().equals(memberId) ? "owner" : "member",
                currentUser.id()
            );
            imRepository.addMember(
                currentUser.workspaceId(),
                conversationId,
                memberId,
                currentUser.id().equals(memberId) ? "owner" : "member"
            );
        }
        eventRepository.append(
            currentUser.workspaceId(),
            "project.created",
            "project",
            projectId,
            currentUser.id(),
            Map.of("projectId", projectId.toString(), "conversationId", conversationId.toString()),
            "project.created:" + projectId
        );
        auditService.log(currentUser, "project.created", "project", projectId, Map.of("projectKey", normalizedKey));
        return getProject(currentUser, projectId);
    }

    public ProjectDetail getProject(CurrentUser currentUser, UUID projectId) {
        return projectRepository.findProject(currentUser.workspaceId(), projectId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Transactional
    public ProjectDetail addProjectMembers(CurrentUser currentUser, UUID projectId, List<UUID> memberIds) {
        requireProjectEditor(currentUser, projectId);
        ProjectSummary project = projectRepository.findProjectById(currentUser.workspaceId(), projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        for (UUID memberId : new LinkedHashSet<>(memberIds == null ? List.of() : memberIds)) {
            projectRepository.addProjectMember(currentUser.workspaceId(), projectId, memberId, "member", currentUser.id());
            if (project.conversationId() != null) {
                imRepository.addMember(currentUser.workspaceId(), project.conversationId(), memberId, "member");
            }
            eventRepository.append(
                currentUser.workspaceId(),
                "project.member.added",
                "project",
                projectId,
                currentUser.id(),
                Map.of("projectId", projectId.toString(), "memberId", memberId.toString()),
                "project.member.added:" + projectId + ":" + memberId
            );
            auditService.log(currentUser, "project.member.added", "project", projectId, Map.of("memberId", memberId.toString()));
        }
        return getProject(currentUser, projectId);
    }

    public List<IssueSummary> listIssues(
        CurrentUser currentUser,
        UUID projectId,
        String status,
        String issueType,
        String priority,
        UUID assigneeId,
        String sort
    ) {
        requireProjectMember(currentUser, projectId);
        return projectRepository.listIssues(
            currentUser.workspaceId(),
            projectId,
            currentUser.id(),
            normalizeOptionalStatus(status),
            normalizeOptionalIssueType(issueType),
            normalizeOptionalPriority(priority),
            assigneeId,
            normalizeIssueSort(sort)
        );
    }

    public List<IssueSummary> listMyIssues(CurrentUser currentUser) {
        return projectRepository.listMyIssues(currentUser.workspaceId(), currentUser.id());
    }

    public ProjectStats projectStats(CurrentUser currentUser, UUID projectId) {
        requireProjectMember(currentUser, projectId);
        return projectRepository.projectStats(currentUser.workspaceId(), projectId);
    }

    @Transactional
    public IssueDetail createIssue(
        CurrentUser currentUser,
        UUID projectId,
        String issueType,
        String title,
        String description,
        String priority,
        UUID assigneeId,
        LocalDate dueAt
    ) {
        requireProjectEditor(currentUser, projectId);
        if (assigneeId != null && !projectRepository.isProjectMember(currentUser.workspaceId(), projectId, assigneeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee must be a project member");
        }
        ProjectSummary project = projectRepository.findProjectById(currentUser.workspaceId(), projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        String type = normalizeIssueType(issueType);
        String issueKey = project.projectKey() + "-" + projectRepository.nextIssueNumber(projectId);
        UUID issueId = projectRepository.createIssue(
            currentUser.workspaceId(),
            projectId,
            issueKey,
            type,
            title.trim(),
            description,
            normalizePriority(priority),
            assigneeId,
            currentUser.id(),
            dueAt,
            currentUser.id()
        );
        projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "created", null, issueKey);
        registerIssueObject(currentUser.workspaceId(), issueId, type, issueKey, title);
        appendIssueEvent(currentUser, "issue.created", issueId, Map.of("projectId", projectId.toString(), "issueId", issueId.toString()));
        notifyAssignee(currentUser, issueId, assigneeId, "issue.assigned", "你被指派了事项 " + issueKey);
        postProjectMessage(currentUser, project.conversationId(), "创建了 " + label(type) + " " + issueKey + " /issues/" + issueId);
        auditService.log(currentUser, "issue.created", "issue", issueId, Map.of("projectId", projectId.toString(), "issueType", type));
        return getIssue(currentUser, issueId);
    }

    public IssueDetail getIssue(CurrentUser currentUser, UUID issueId) {
        IssueSummary issue = requireIssueAccess(currentUser, issueId);
        return new IssueDetail(
            issue,
            projectRepository.listComments(currentUser.workspaceId(), issueId),
            projectRepository.listAttachments(currentUser.workspaceId(), issueId),
            projectRepository.listActivities(currentUser.workspaceId(), issueId),
            projectRepository.listVerifications(currentUser.workspaceId(), issueId),
            hydrateRelations(currentUser, projectRepository.listRelations(currentUser.workspaceId(), issueId))
        );
    }

    @Transactional
    public IssueDetail updateIssue(
        CurrentUser currentUser,
        UUID issueId,
        String title,
        String description,
        String priority,
        UUID assigneeId,
        LocalDate dueAt
    ) {
        IssueSummary before = requireIssueEditor(currentUser, issueId);
        if (assigneeId != null && !projectRepository.isProjectMember(currentUser.workspaceId(), before.projectId(), assigneeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee must be a project member");
        }
        projectRepository.updateIssue(
            currentUser.workspaceId(),
            issueId,
            title == null || title.isBlank() ? before.title() : title.trim(),
            description,
            normalizePriority(priority == null ? before.priority() : priority),
            assigneeId,
            dueAt,
            currentUser.id()
        );
        projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "updated", null, null);
        if (assigneeId != null && !assigneeId.equals(before.assigneeId())) {
            appendIssueEvent(currentUser, "issue.assigned", issueId, Map.of("issueId", issueId.toString(), "assigneeId", assigneeId.toString()));
            notifyAssignee(currentUser, issueId, assigneeId, "issue.assigned", "你被指派了事项 " + before.issueKey());
        }
        IssueSummary after = projectRepository.findIssue(currentUser.workspaceId(), issueId).orElseThrow();
        registerIssueObject(currentUser.workspaceId(), issueId, after.issueType(), after.issueKey(), after.title());
        auditService.log(currentUser, "issue.updated", "issue", issueId, Map.of("projectId", before.projectId().toString()));
        return getIssue(currentUser, issueId);
    }

    @Transactional
    public IssueDetail transitionIssue(CurrentUser currentUser, UUID issueId, String targetStatus) {
        IssueSummary issue = requireIssueEditor(currentUser, issueId);
        String next = normalizeStatus(targetStatus);
        if (!allowedNextStatuses(issue.status()).contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal issue transition");
        }
        projectRepository.transitionIssue(currentUser.workspaceId(), issueId, next, currentUser.id());
        projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "transitioned", issue.status(), next);
        appendIssueEvent(currentUser, "issue.transitioned", issueId, Map.of("issueId", issueId.toString(), "from", issue.status(), "to", next));
        notifyIssueParticipants(
            currentUser,
            issue,
            "issue.transitioned",
            issue.issueKey() + " 状态已变更为 " + next,
            issue.status() + " -> " + next
        );
        ProjectSummary project = projectRepository.findProjectById(currentUser.workspaceId(), issue.projectId()).orElseThrow();
        postProjectMessage(currentUser, project.conversationId(), "流转了 " + issue.issueKey() + "：" + issue.status() + " -> " + next + " /issues/" + issueId);
        auditService.log(currentUser, "issue.transitioned", "issue", issueId, Map.of("from", issue.status(), "to", next));
        return getIssue(currentUser, issueId);
    }

    @Transactional
    public IssueDetail addComment(CurrentUser currentUser, UUID issueId, String content) {
        IssueSummary issue = requireIssueEditor(currentUser, issueId);
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required");
        }
        UUID commentId = projectRepository.addComment(currentUser.workspaceId(), issueId, currentUser.id(), content.trim());
        projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "commented", null, commentId.toString());
        for (UUID mentionedUserId : resolveCommentMentions(currentUser, issue.projectId(), content)) {
            appendIssueEvent(
                currentUser,
                "issue.comment.mentioned",
                issueId,
                Map.of("issueId", issueId.toString(), "commentId", commentId.toString(), "mentionedUserId", mentionedUserId.toString())
            );
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "issue",
                issueId,
                currentUser.id(),
                Map.of(
                    "recipientId", mentionedUserId.toString(),
                    "notificationType", "issue_comment_mention",
                    "title", currentUser.displayName() + " 在 " + issue.issueKey() + " 评论中提到了你",
                    "body", content.trim(),
                    "targetType", "issue",
                    "targetId", issueId.toString(),
                    "webPath", "/issues/" + issueId,
                    "dedupeKey", "issue.comment.mention:" + commentId + ":" + mentionedUserId
                ),
                "notification.issue.comment.mention:" + commentId + ":" + mentionedUserId
            );
        }
        ProjectSummary project = projectRepository.findProjectById(currentUser.workspaceId(), issue.projectId()).orElseThrow();
        postProjectMessage(currentUser, project.conversationId(), "评论了 " + issue.issueKey() + " /issues/" + issueId);
        return getIssue(currentUser, issueId);
    }

    @Transactional
    public IssueDetail addAttachment(CurrentUser currentUser, UUID issueId, UUID fileId) {
        IssueSummary issue = requireIssueEditor(currentUser, issueId);
        fileRepository.find(currentUser.workspaceId(), fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        projectRepository.addAttachment(currentUser.workspaceId(), issueId, fileId, currentUser.id());
        fileRepository.addUsage(currentUser.workspaceId(), fileId, "issue", issueId, currentUser.id());
        projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "attachment.added", null, fileId.toString());
        return getIssue(currentUser, issue.id());
    }

    @Transactional
    public IssueDetail addVerification(
        CurrentUser currentUser,
        UUID issueId,
        String result,
        String note,
        String environment,
        String reproductionSteps,
        String fixVersion
    ) {
        IssueSummary issue = requireIssueEditor(currentUser, issueId);
        if (!"bug".equals(issue.issueType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only bug issues can be verified");
        }
        String normalizedResult = normalizeVerificationResult(result);
        String normalizedNote = note == null ? "" : note.trim();
        String normalizedEnvironment = trimToNull(environment);
        String normalizedSteps = trimToNull(reproductionSteps);
        String normalizedFixVersion = trimToNull(fixVersion);
        UUID verificationId = projectRepository.addVerification(
            currentUser.workspaceId(),
            issueId,
            currentUser.id(),
            normalizedResult,
            normalizedNote,
            normalizedEnvironment,
            normalizedSteps,
            normalizedFixVersion
        );
        projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "verified", null, normalizedResult);
        if ("passed".equals(normalizedResult) && "resolved".equals(issue.status())) {
            projectRepository.transitionIssue(currentUser.workspaceId(), issueId, "closed", currentUser.id());
            projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "transitioned", "resolved", "closed");
        } else if ("failed".equals(normalizedResult) && "resolved".equals(issue.status())) {
            projectRepository.transitionIssue(currentUser.workspaceId(), issueId, "in_progress", currentUser.id());
            projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "transitioned", "resolved", "in_progress");
        }
        appendIssueEvent(
            currentUser,
            "issue.verified",
            issueId,
            Map.of("issueId", issueId.toString(), "verificationId", verificationId.toString(), "result", normalizedResult)
        );
        notifyIssueParticipants(
            currentUser,
            issue,
            "issue.verified",
            issue.issueKey() + " 已完成 BUG 验证",
            normalizedResult
        );
        ProjectSummary project = projectRepository.findProjectById(currentUser.workspaceId(), issue.projectId()).orElseThrow();
        postProjectMessage(currentUser, project.conversationId(), "验证了 " + issue.issueKey() + "：" + normalizedResult + " /issues/" + issueId);
        auditService.log(currentUser, "issue.verified", "issue", issueId, Map.of("result", normalizedResult));
        return getIssue(currentUser, issueId);
    }

    @Transactional
    public IssueDetail addRelation(CurrentUser currentUser, UUID issueId, String targetType, UUID targetId) {
        IssueSummary issue = requireIssueEditor(currentUser, issueId);
        if (targetType == null || targetType.isBlank() || targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target object is required");
        }
        String normalizedTargetType = targetType.trim();
        if ("issue".equals(normalizedTargetType) && issueId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issue cannot relate to itself");
        }
        PlatformObjectSummary target = objectResolverRegistry.resolve(currentUser, normalizedTargetType, targetId);
        if (target.accessState() != ObjectAccessState.available) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Target object is not available");
        }
        projectRepository.addRelation(currentUser.workspaceId(), issueId, normalizedTargetType, targetId, currentUser.id());
        projectRepository.addActivity(currentUser.workspaceId(), issueId, currentUser.id(), "relation.added", null, normalizedTargetType + ":" + targetId);
        appendIssueEvent(
            currentUser,
            "issue.relation.added",
            issueId,
            Map.of("issueId", issueId.toString(), "targetType", normalizedTargetType, "targetId", targetId.toString())
        );
        auditService.log(
            currentUser,
            "issue.relation.added",
            "issue",
            issueId,
            Map.of("targetType", normalizedTargetType, "targetId", targetId.toString(), "projectId", issue.projectId().toString())
        );
        return getIssue(currentUser, issueId);
    }

    private IssueSummary requireIssueAccess(CurrentUser currentUser, UUID issueId) {
        IssueSummary issue = projectRepository.findIssue(currentUser.workspaceId(), issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        requireProjectMember(currentUser, issue.projectId());
        return issue;
    }

    private IssueSummary requireIssueEditor(CurrentUser currentUser, UUID issueId) {
        IssueSummary issue = projectRepository.findIssue(currentUser.workspaceId(), issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        requireProjectEditor(currentUser, issue.projectId());
        return issue;
    }

    private void requireProjectMember(CurrentUser currentUser, UUID projectId) {
        if (!projectRepository.isProjectMember(currentUser.workspaceId(), projectId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        }
    }

    private void requireProjectEditor(CurrentUser currentUser, UUID projectId) {
        if (!projectRepository.canEditProject(currentUser.workspaceId(), projectId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project edit denied");
        }
    }

    private List<UUID> normalizeMembers(UUID ownerId, List<UUID> memberIds) {
        List<UUID> members = new ArrayList<>(new LinkedHashSet<>(memberIds == null ? List.of() : memberIds));
        if (!members.contains(ownerId)) {
            members.add(ownerId);
        }
        return members;
    }

    private Set<UUID> resolveCommentMentions(CurrentUser currentUser, UUID projectId, String content) {
        Matcher matcher = MENTION_PATTERN.matcher(content);
        List<String> usernames = new ArrayList<>();
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        Set<UUID> mentionedUserIds = new LinkedHashSet<>(
            projectRepository.findActiveProjectUserIdsByUsernames(currentUser.workspaceId(), projectId, usernames)
        );
        mentionedUserIds.remove(currentUser.id());
        return mentionedUserIds;
    }

    private void notifyAssignee(CurrentUser currentUser, UUID issueId, UUID assigneeId, String eventType, String title) {
        if (assigneeId == null || assigneeId.equals(currentUser.id())) {
            return;
        }
        eventRepository.append(
            currentUser.workspaceId(),
            "notification.created",
            "issue",
            issueId,
            currentUser.id(),
            Map.of(
                "recipientId", assigneeId.toString(),
                "notificationType", eventType,
                "title", title,
                "body", "点击查看事项详情",
                "targetType", "issue",
                "targetId", issueId.toString(),
                "webPath", "/issues/" + issueId,
                "dedupeKey", eventType + ":" + issueId + ":" + assigneeId
            ),
            "notification." + eventType + ":" + issueId + ":" + assigneeId
        );
    }

    private void notifyIssueParticipants(CurrentUser currentUser, IssueSummary issue, String eventType, String title, String body) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (issue.assigneeId() != null) {
            recipients.add(issue.assigneeId());
        }
        recipients.add(issue.reporterId());
        recipients.remove(currentUser.id());
        for (UUID recipientId : recipients) {
            UUID eventKey = UUID.randomUUID();
            eventRepository.append(
                currentUser.workspaceId(),
                "notification.created",
                "issue",
                issue.id(),
                currentUser.id(),
                Map.of(
                    "recipientId", recipientId.toString(),
                    "notificationType", eventType,
                    "title", title,
                    "body", body,
                    "targetType", "issue",
                    "targetId", issue.id().toString(),
                    "webPath", "/issues/" + issue.id(),
                    "dedupeKey", eventType + ":" + issue.id() + ":" + recipientId + ":" + eventKey
                ),
                "notification:" + eventKey
            );
        }
    }

    private void appendIssueEvent(CurrentUser currentUser, String eventType, UUID issueId, Map<String, Object> payload) {
        eventRepository.append(
            currentUser.workspaceId(),
            eventType,
            "issue",
            issueId,
            currentUser.id(),
            payload,
            eventType + ":" + issueId + ":" + UUID.randomUUID()
        );
    }

    private void postProjectMessage(CurrentUser currentUser, UUID conversationId, String content) {
        if (conversationId == null) {
            return;
        }
        try {
            imService.sendMessage(currentUser, conversationId, UUID.randomUUID().toString(), "system", content);
        } catch (RuntimeException ignored) {
            // Project activity remains persisted even if the best-effort IM message fails.
        }
    }

    private void registerIssueObject(UUID workspaceId, UUID issueId, String issueType, String issueKey, String title) {
        objectRepository.upsertObjectLink(
            workspaceId,
            "issue",
            issueId,
            "/issues/" + issueId,
            "colla://issue/" + issueId,
            issueKey + " " + title
        );
    }

    private String normalizeProjectKey(String projectKey, String name) {
        String source = projectKey == null || projectKey.isBlank() ? name : projectKey;
        String normalized = source == null ? "" : source.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project key is required");
        }
        return normalized.length() > 12 ? normalized.substring(0, 12) : normalized;
    }

    private String normalizeIssueType(String issueType) {
        String type = issueType == null || issueType.isBlank() ? "task" : issueType.toLowerCase(Locale.ROOT);
        if (!List.of("requirement", "task", "bug").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid issue type");
        }
        return type;
    }

    private String normalizeOptionalIssueType(String issueType) {
        return issueType == null || issueType.isBlank() ? null : normalizeIssueType(issueType);
    }

    private String normalizePriority(String priority) {
        String value = priority == null || priority.isBlank() ? "medium" : priority.toLowerCase(Locale.ROOT);
        if (!List.of("low", "medium", "high", "urgent").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid issue priority");
        }
        return value;
    }

    private String normalizeOptionalPriority(String priority) {
        return priority == null || priority.isBlank() ? null : normalizePriority(priority);
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "" : status.toLowerCase(Locale.ROOT);
        if (!List.of("open", "in_progress", "resolved", "closed").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid issue status");
        }
        return value;
    }

    private String normalizeOptionalStatus(String status) {
        return status == null || status.isBlank() ? null : normalizeStatus(status);
    }

    private String normalizeIssueSort(String sort) {
        String value = sort == null || sort.isBlank() ? "updated_desc" : sort.toLowerCase(Locale.ROOT);
        if (!List.of("updated_desc", "created_desc", "due_asc", "priority_desc").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid issue sort");
        }
        return value;
    }

    private String normalizeVerificationResult(String result) {
        String value = result == null || result.isBlank() ? "" : result.toLowerCase(Locale.ROOT);
        if (!List.of("passed", "failed", "blocked").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification result");
        }
        return value;
    }

    private List<String> allowedNextStatuses(String current) {
        return switch (current) {
            case "open" -> List.of("in_progress", "resolved", "closed");
            case "in_progress" -> List.of("open", "resolved", "closed");
            case "resolved" -> List.of("in_progress", "closed");
            case "closed" -> List.of("open");
            default -> List.of();
        };
    }

    private String label(String issueType) {
        return switch (issueType) {
            case "bug" -> "Bug";
            case "requirement" -> "需求";
            default -> "任务";
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<IssueRelation> hydrateRelations(CurrentUser currentUser, List<IssueRelation> relations) {
        return relations.stream()
            .map(relation -> new IssueRelation(
                relation.id(),
                relation.issueId(),
                relation.targetType(),
                relation.targetId(),
                relation.createdBy(),
                relation.createdByName(),
                relation.createdAt(),
                objectResolverRegistry.resolve(currentUser, relation.targetType(), relation.targetId())
            ))
            .toList();
    }
}
