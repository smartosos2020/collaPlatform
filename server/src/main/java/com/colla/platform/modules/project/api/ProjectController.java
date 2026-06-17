package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectService;
import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectStats;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/projects")
    public List<ProjectSummary> listProjects(Authentication authentication) {
        return projectService.listProjects(currentUser(authentication));
    }

    @PostMapping("/projects")
    public ProjectDetail createProject(@Valid @RequestBody CreateProjectRequest request, Authentication authentication) {
        return projectService.createProject(
            currentUser(authentication),
            request.projectKey(),
            request.name(),
            request.description(),
            request.memberIds()
        );
    }

    @GetMapping("/projects/{projectId}")
    public ProjectDetail project(@PathVariable UUID projectId, Authentication authentication) {
        return projectService.getProject(currentUser(authentication), projectId);
    }

    @GetMapping("/projects/{projectId}/stats")
    public ProjectStats projectStats(@PathVariable UUID projectId, Authentication authentication) {
        return projectService.projectStats(currentUser(authentication), projectId);
    }

    @PostMapping("/projects/{projectId}/members")
    public ProjectDetail addMembers(
        @PathVariable UUID projectId,
        @RequestBody AddProjectMembersRequest request,
        Authentication authentication
    ) {
        return projectService.addProjectMembers(currentUser(authentication), projectId, request.memberIds());
    }

    @GetMapping("/projects/{projectId}/issues")
    public List<IssueSummary> issues(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String issueType,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) UUID assigneeId,
        @RequestParam(required = false) String sort,
        Authentication authentication
    ) {
        return projectService.listIssues(currentUser(authentication), projectId, status, issueType, priority, assigneeId, sort);
    }

    @PostMapping("/projects/{projectId}/issues")
    public IssueDetail createIssue(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateIssueRequest request,
        Authentication authentication
    ) {
        return projectService.createIssue(
            currentUser(authentication),
            projectId,
            request.issueType(),
            request.title(),
            request.description(),
            request.priority(),
            request.assigneeId(),
            request.dueAt()
        );
    }

    @GetMapping("/my/issues")
    public List<IssueSummary> myIssues(Authentication authentication) {
        return projectService.listMyIssues(currentUser(authentication));
    }

    @GetMapping("/issues/{issueId}")
    public IssueDetail issue(@PathVariable UUID issueId, Authentication authentication) {
        return projectService.getIssue(currentUser(authentication), issueId);
    }

    @PatchMapping("/issues/{issueId}")
    public IssueDetail updateIssue(
        @PathVariable UUID issueId,
        @Valid @RequestBody UpdateIssueRequest request,
        Authentication authentication
    ) {
        return projectService.updateIssue(
            currentUser(authentication),
            issueId,
            request.title(),
            request.description(),
            request.priority(),
            request.assigneeId(),
            request.dueAt()
        );
    }

    @PostMapping("/issues/{issueId}/transition")
    public IssueDetail transitionIssue(
        @PathVariable UUID issueId,
        @Valid @RequestBody TransitionIssueRequest request,
        Authentication authentication
    ) {
        return projectService.transitionIssue(currentUser(authentication), issueId, request.status());
    }

    @PostMapping("/issues/{issueId}/comments")
    public IssueDetail addComment(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddCommentRequest request,
        Authentication authentication
    ) {
        return projectService.addComment(currentUser(authentication), issueId, request.content());
    }

    @PostMapping("/issues/{issueId}/attachments")
    public IssueDetail addAttachment(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddAttachmentRequest request,
        Authentication authentication
    ) {
        return projectService.addAttachment(currentUser(authentication), issueId, request.fileId());
    }

    @PostMapping("/issues/{issueId}/verifications")
    public IssueDetail addVerification(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddVerificationRequest request,
        Authentication authentication
    ) {
        return projectService.addVerification(
            currentUser(authentication),
            issueId,
            request.result(),
            request.note(),
            request.environment(),
            request.reproductionSteps(),
            request.fixVersion()
        );
    }

    @PostMapping("/issues/{issueId}/relations")
    public IssueDetail addRelation(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddIssueRelationRequest request,
        Authentication authentication
    ) {
        return projectService.addRelation(currentUser(authentication), issueId, request.targetType(), request.targetId());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateProjectRequest(
        @Size(max = 32) String projectKey,
        @NotBlank @Size(max = 128) String name,
        String description,
        List<UUID> memberIds
    ) {
    }

    public record AddProjectMembersRequest(List<UUID> memberIds) {
    }

    public record CreateIssueRequest(
        @NotBlank String issueType,
        @NotBlank @Size(max = 255) String title,
        String description,
        String priority,
        UUID assigneeId,
        LocalDate dueAt
    ) {
    }

    public record UpdateIssueRequest(
        @Size(max = 255) String title,
        String description,
        String priority,
        UUID assigneeId,
        LocalDate dueAt
    ) {
    }

    public record TransitionIssueRequest(@NotBlank String status) {
    }

    public record AddCommentRequest(@NotBlank String content) {
    }

    public record AddAttachmentRequest(UUID fileId) {
    }

    public record AddVerificationRequest(
        @NotBlank String result,
        String note,
        String environment,
        String reproductionSteps,
        String fixVersion
    ) {
    }

    public record AddIssueRelationRequest(@NotBlank String targetType, UUID targetId) {
    }
}
