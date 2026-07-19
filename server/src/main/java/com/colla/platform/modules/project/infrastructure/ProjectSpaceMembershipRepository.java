package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceInvitation;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSpaceMembershipRepository {
    String lockSpaceStatus(UUID workspaceId, UUID spaceId);

    List<ProjectSpaceMember> listMembers(UUID workspaceId, UUID spaceId);

    Optional<ProjectSpaceMember> findMember(UUID workspaceId, UUID spaceId, UUID memberId);

    Optional<ProjectSpaceMember> findMemberByUser(UUID workspaceId, UUID spaceId, UUID userId);

    long countActiveOwners(UUID workspaceId, UUID spaceId);

    UUID createMember(UUID workspaceId, UUID spaceId, UUID userId, String roleKey, UUID actorId);

    void reactivateMember(UUID workspaceId, UUID spaceId, UUID memberId, String roleKey, UUID actorId);

    void changeRole(UUID workspaceId, UUID spaceId, UUID memberId, String roleKey, UUID actorId);

    void removeMember(UUID workspaceId, UUID spaceId, UUID memberId, UUID actorId);

    void transferOwner(UUID workspaceId, UUID spaceId, UUID ownerMemberId, UUID targetMemberId, UUID actorId);

    List<UUID> listSoleOwnerSpaceIds(UUID workspaceId, UUID userId);

    List<ProjectSpaceInvitation> listInvitations(UUID workspaceId, UUID spaceId);

    Optional<ProjectSpaceInvitation> findInvitation(UUID workspaceId, UUID invitationId);

    Optional<ProjectSpaceInvitation> findPendingInvitationByUser(UUID workspaceId, UUID spaceId, UUID userId);

    ProjectSpaceInvitation createInvitation(
        UUID workspaceId,
        UUID spaceId,
        UUID inviteeUserId,
        String roleKey,
        String tokenHash,
        Instant expiresAt,
        UUID actorId,
        String requestId
    );

    boolean resendInvitation(UUID workspaceId, UUID invitationId, String tokenHash, Instant expiresAt, String requestId);

    void updateInvitationStatus(UUID workspaceId, UUID invitationId, String status, UUID actorId);

    int expireInvitations(UUID workspaceId, UUID spaceId);
}
