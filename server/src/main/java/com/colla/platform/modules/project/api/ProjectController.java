package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectService;
import com.colla.platform.modules.project.api.UserProjectDtos.UserIssueDetailView;
import com.colla.platform.modules.project.api.UserProjectDtos.UserIssueView;
import com.colla.platform.modules.project.api.UserProjectDtos.UserProjectDetailView;
import com.colla.platform.modules.project.api.UserProjectDtos.UserProjectView;
import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectStats;
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
    public List<UserProjectView> listProjects(Authentication authentication) {
        return projectService.listProjects(currentUser(authentication)).stream()
            .map(UserProjectDtos::project)
            .toList();
    }

    @PostMapping("/projects")
    public UserProjectDetailView createProject(@Valid @RequestBody CreateProjectRequest request, Authentication authentication) {
        return UserProjectDtos.projectDetail(projectService.createProject(
            currentUser(authentication),
            request.projectKey(),
            request.name(),
            request.description(),
            request.memberIds()
        ));
    }

    @GetMapping("/projects/{projectId}")
    public UserProjectDetailView project(@PathVariable UUID projectId, Authentication authentication) {
        return UserProjectDtos.projectDetail(projectService.getProject(currentUser(authentication), projectId));
    }

    @GetMapping("/projects/{projectId}/stats")
    public ProjectStats projectStats(@PathVariable UUID projectId, Authentication authentication) {
        return projectService.projectStats(currentUser(authentication), projectId);
    }

    @PostMapping("/projects/{projectId}/members")
    public UserProjectDetailView addMembers(
        @PathVariable UUID projectId,
        @RequestBody AddProjectMembersRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.projectDetail(projectService.addProjectMembers(currentUser(authentication), projectId, request.memberIds()));
    }

    @GetMapping("/projects/{projectId}/issues")
    public List<UserIssueView> issues(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String issueType,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) UUID assigneeId,
        @RequestParam(required = false) String sort,
        Authentication authentication
    ) {
        return projectService.listIssues(currentUser(authentication), projectId, status, issueType, priority, assigneeId, sort).stream()
            .map(UserProjectDtos::issue)
            .toList();
    }

    @PostMapping("/projects/{projectId}/issues")
    public UserIssueDetailView createIssue(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateIssueRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.issueDetail(projectService.createIssue(
            currentUser(authentication),
            projectId,
            request.issueType(),
            request.title(),
            request.description(),
            request.priority(),
            request.assigneeId(),
            request.dueAt()
        ));
    }

    @GetMapping("/my/issues")
    public List<UserIssueView> myIssues(Authentication authentication) {
        return projectService.listMyIssues(currentUser(authentication)).stream()
            .map(UserProjectDtos::issue)
            .toList();
    }

    @GetMapping("/issues/{issueId}")
    public UserIssueDetailView issue(@PathVariable UUID issueId, Authentication authentication) {
        return UserProjectDtos.issueDetail(projectService.getIssue(currentUser(authentication), issueId));
    }

    @PatchMapping("/issues/{issueId}")
    public UserIssueDetailView updateIssue(
        @PathVariable UUID issueId,
        @Valid @RequestBody UpdateIssueRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.issueDetail(projectService.updateIssue(
            currentUser(authentication),
            issueId,
            request.title(),
            request.description(),
            request.priority(),
            request.assigneeId(),
            request.dueAt()
        ));
    }

    @PostMapping("/issues/{issueId}/transition")
    public UserIssueDetailView transitionIssue(
        @PathVariable UUID issueId,
        @RequestBody TransitionIssueRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.issueDetail(projectService.transitionIssue(
            currentUser(authentication),
            issueId,
            request.action(),
            request.status(),
            request.reason(),
            request.targetIssueId(),
            request.dueAt()
        ));
    }

    @PostMapping("/issues/{issueId}/comments")
    public UserIssueDetailView addComment(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddCommentRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.issueDetail(projectService.addComment(currentUser(authentication), issueId, request.content()));
    }

    @PostMapping("/issues/{issueId}/attachments")
    public UserIssueDetailView addAttachment(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddAttachmentRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.issueDetail(projectService.addAttachment(currentUser(authentication), issueId, request.fileId()));
    }

    @PostMapping("/issues/{issueId}/verifications")
    public UserIssueDetailView addVerification(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddVerificationRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.issueDetail(projectService.addVerification(
            currentUser(authentication),
            issueId,
            request.result(),
            request.note(),
            request.environment(),
            request.reproductionSteps(),
            request.fixVersion()
        ));
    }

    @PostMapping("/issues/{issueId}/relations")
    public UserIssueDetailView addRelation(
        @PathVariable UUID issueId,
        @Valid @RequestBody AddIssueRelationRequest request,
        Authentication authentication
    ) {
        return UserProjectDtos.issueDetail(projectService.addRelation(currentUser(authentication), issueId, request.targetType(), request.targetId()));
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

    public record TransitionIssueRequest(
        String action,
        String status,
        String reason,
        UUID targetIssueId,
        LocalDate dueAt
    ) {
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
