package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.ProjectSpaceMembershipDtos.ProjectSpaceInvitationView;
import com.colla.platform.modules.project.api.ProjectSpaceMembershipDtos.ProjectSpaceMemberView;
import com.colla.platform.modules.project.application.ProjectSpaceMembershipService;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-space-invitations/{invitationId}")
public class ProjectSpaceInvitationController {
    private final ProjectSpaceMembershipService membershipService;

    public ProjectSpaceInvitationController(ProjectSpaceMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping("/accept")
    public ProjectSpaceMemberView accept(@PathVariable UUID invitationId, Authentication authentication) {
        return ProjectSpaceMembershipDtos.member(membershipService.acceptInvitation(currentUser(authentication), invitationId));
    }

    @PostMapping("/reject")
    public ProjectSpaceInvitationView reject(@PathVariable UUID invitationId, Authentication authentication) {
        return ProjectSpaceMembershipDtos.invitation(membershipService.rejectInvitation(currentUser(authentication), invitationId));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
