package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.ProjectSpaceMembershipDtos.ProjectSpaceCandidateView;
import com.colla.platform.modules.project.api.ProjectSpaceMembershipDtos.ProjectSpaceInvitationView;
import com.colla.platform.modules.project.api.ProjectSpaceMembershipDtos.ProjectSpaceMemberView;
import com.colla.platform.modules.project.api.ProjectSpaceMembershipDtos.ProjectSpaceRoleCapabilityView;
import com.colla.platform.modules.project.application.ProjectSpaceMembershipService;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}")
public class ProjectSpaceMembershipController {
    private final ProjectSpaceMembershipService membershipService;

    public ProjectSpaceMembershipController(ProjectSpaceMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/role-capabilities")
    public List<ProjectSpaceRoleCapabilityView> roleCapabilities() {
        return membershipService.roleCapabilities().stream().map(ProjectSpaceRoleCapabilityView::from).toList();
    }

    @GetMapping("/members")
    public List<ProjectSpaceMemberView> members(@PathVariable UUID spaceId, Authentication authentication) {
        return membershipService.listMembers(currentUser(authentication), spaceId).stream()
            .map(ProjectSpaceMembershipDtos::member)
            .toList();
    }

    @GetMapping("/member-candidates")
    public List<ProjectSpaceCandidateView> candidates(
        @PathVariable UUID spaceId,
        @RequestParam(defaultValue = "") String query,
        Authentication authentication
    ) {
        return membershipService.searchCandidates(currentUser(authentication), spaceId, query).stream()
            .map(ProjectSpaceCandidateView::from)
            .toList();
    }

    @PostMapping("/members")
    public ProjectSpaceMemberView addMember(
        @PathVariable UUID spaceId,
        @Valid @RequestBody AddProjectSpaceMemberRequest request,
        Authentication authentication
    ) {
        return ProjectSpaceMembershipDtos.member(membershipService.addMember(
            currentUser(authentication), spaceId, request.userId(), request.roleKey()
        ));
    }

    @PatchMapping("/members/{memberId}/role")
    public ProjectSpaceMemberView changeRole(
        @PathVariable UUID spaceId,
        @PathVariable UUID memberId,
        @Valid @RequestBody ChangeProjectSpaceRoleRequest request,
        Authentication authentication
    ) {
        return ProjectSpaceMembershipDtos.member(membershipService.changeRole(
            currentUser(authentication), spaceId, memberId, request.roleKey()
        ));
    }

    @DeleteMapping("/members/{memberId}")
    public ProjectSpaceMemberView removeMember(
        @PathVariable UUID spaceId,
        @PathVariable UUID memberId,
        Authentication authentication
    ) {
        return ProjectSpaceMembershipDtos.member(membershipService.removeMember(
            currentUser(authentication), spaceId, memberId
        ));
    }

    @PostMapping("/members/leave")
    public ProjectSpaceMemberView leave(@PathVariable UUID spaceId, Authentication authentication) {
        return ProjectSpaceMembershipDtos.member(membershipService.leave(currentUser(authentication), spaceId));
    }

    @PostMapping("/members/{memberId}/transfer-owner")
    public List<ProjectSpaceMemberView> transferOwner(
        @PathVariable UUID spaceId,
        @PathVariable UUID memberId,
        Authentication authentication
    ) {
        return membershipService.transferOwner(currentUser(authentication), spaceId, memberId).stream()
            .map(ProjectSpaceMembershipDtos::member)
            .toList();
    }

    @GetMapping("/invitations")
    public List<ProjectSpaceInvitationView> invitations(@PathVariable UUID spaceId, Authentication authentication) {
        return membershipService.listInvitations(currentUser(authentication), spaceId).stream()
            .map(ProjectSpaceMembershipDtos::invitation)
            .toList();
    }

    @PostMapping("/invitations")
    public ProjectSpaceInvitationView invite(
        @PathVariable UUID spaceId,
        @Valid @RequestBody InviteProjectSpaceMemberRequest request,
        Authentication authentication
    ) {
        return ProjectSpaceMembershipDtos.invitation(membershipService.invite(
            currentUser(authentication), spaceId, request.userId(), request.roleKey(), request.expiresInHours()
        ));
    }

    @PostMapping("/invitations/{invitationId}/resend")
    public ProjectSpaceInvitationView resend(
        @PathVariable UUID spaceId,
        @PathVariable UUID invitationId,
        @RequestBody(required = false) ResendProjectSpaceInvitationRequest request,
        Authentication authentication
    ) {
        return ProjectSpaceMembershipDtos.invitation(membershipService.resendInvitation(
            currentUser(authentication), spaceId, invitationId, request == null ? null : request.expiresInHours()
        ));
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ProjectSpaceInvitationView revoke(
        @PathVariable UUID spaceId,
        @PathVariable UUID invitationId,
        Authentication authentication
    ) {
        return ProjectSpaceMembershipDtos.invitation(membershipService.revokeInvitation(
            currentUser(authentication), spaceId, invitationId
        ));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record AddProjectSpaceMemberRequest(@NotNull UUID userId, @NotBlank String roleKey) {
    }

    public record ChangeProjectSpaceRoleRequest(@NotBlank String roleKey) {
    }

    public record InviteProjectSpaceMemberRequest(
        @NotNull UUID userId,
        @NotBlank String roleKey,
        Integer expiresInHours
    ) {
    }

    public record ResendProjectSpaceInvitationRequest(Integer expiresInHours) {
    }
}
