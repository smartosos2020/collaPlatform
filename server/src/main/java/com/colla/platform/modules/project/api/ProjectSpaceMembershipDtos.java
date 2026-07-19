package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceInvitation;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMemberCandidate;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceRoleCapability;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ProjectSpaceMembershipDtos {
    private ProjectSpaceMembershipDtos() {
    }

    static ProjectSpaceMemberView member(ProjectSpaceMember member) {
        return new ProjectSpaceMemberView(
            member.id(), member.spaceId(), member.userId(), member.username(), member.displayName(),
            member.avatarFileId(), member.email(), member.userStatus(), member.memberStatus(), member.roleKey(),
            member.effective(), member.joinedAt(), member.removedAt(), member.updatedAt()
        );
    }

    static ProjectSpaceInvitationView invitation(ProjectSpaceInvitation invitation) {
        return new ProjectSpaceInvitationView(
            invitation.id(), invitation.spaceId(), invitation.inviteeUserId(), invitation.inviteeDisplayName(),
            invitation.inviteeEmail(), invitation.roleKey(), invitation.status(), invitation.expiresAt(),
            invitation.invitedBy(), invitation.invitedAt(), invitation.respondedAt(), invitation.revokedBy(),
            invitation.revokedAt(), invitation.version(), invitation.updatedAt(), invitation.lastSentAt()
        );
    }

    record ProjectSpaceMemberView(
        UUID id,
        UUID spaceId,
        UUID userId,
        String username,
        String displayName,
        UUID avatarFileId,
        String email,
        String userStatus,
        String memberStatus,
        String roleKey,
        boolean effective,
        Instant joinedAt,
        Instant removedAt,
        Instant updatedAt
    ) {
    }

    record ProjectSpaceInvitationView(
        UUID id,
        UUID spaceId,
        UUID inviteeUserId,
        String inviteeDisplayName,
        String inviteeEmail,
        String roleKey,
        String status,
        Instant expiresAt,
        UUID invitedBy,
        Instant invitedAt,
        Instant respondedAt,
        UUID revokedBy,
        Instant revokedAt,
        long version,
        Instant updatedAt,
        Instant lastSentAt
    ) {
    }

    record ProjectSpaceCandidateView(
        UUID userId,
        String username,
        String displayName,
        UUID avatarFileId,
        String email,
        List<String> departments
    ) {
        static ProjectSpaceCandidateView from(ProjectSpaceMemberCandidate candidate) {
            return new ProjectSpaceCandidateView(
                candidate.userId(), candidate.username(), candidate.displayName(), candidate.avatarFileId(),
                candidate.email(), candidate.departments()
            );
        }
    }

    record ProjectSpaceRoleCapabilityView(
        String roleKey,
        List<String> capabilities,
        boolean canManageOwner,
        boolean canGrantAdmin
    ) {
        static ProjectSpaceRoleCapabilityView from(ProjectSpaceRoleCapability capability) {
            return new ProjectSpaceRoleCapabilityView(
                capability.roleKey(), capability.capabilities(), capability.canManageOwner(), capability.canGrantAdmin()
            );
        }
    }
}
