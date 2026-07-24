package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.identity.contract.SubjectDirectory;
import com.colla.platform.modules.identity.contract.SubjectDirectory.MemberProfile;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceInvitation;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMemberCandidate;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceRole;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceRoleCapability;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectSpaceMembershipService {
    private static final int DEFAULT_INVITATION_HOURS = 72;
    private static final int MAX_INVITATION_HOURS = 24 * 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ProjectSpaceMembershipRepository membershipRepository;
    private final SubjectDirectory subjectDirectory;
    private final ProjectSpaceService projectSpaceService;
    private final AuditLog auditService;
    private final TransactionalOutbox eventRepository;

    public ProjectSpaceMembershipService(
        ProjectSpaceMembershipRepository membershipRepository,
        SubjectDirectory subjectDirectory,
        ProjectSpaceService projectSpaceService,
        AuditLog auditService,
        TransactionalOutbox eventRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.subjectDirectory = subjectDirectory;
        this.projectSpaceService = projectSpaceService;
        this.auditService = auditService;
        this.eventRepository = eventRepository;
    }

    public List<ProjectSpaceRoleCapability> roleCapabilities() {
        return List.of(ProjectSpaceRole.values()).stream()
            .map(role -> new ProjectSpaceRoleCapability(
                role.name(), role.capabilities().stream().sorted().toList(),
                role == ProjectSpaceRole.owner, role == ProjectSpaceRole.owner
            ))
            .toList();
    }

    public List<ProjectSpaceMember> listMembers(CurrentUser currentUser, UUID spaceId) {
        requireEffectiveMember(currentUser, spaceId);
        return membershipRepository.listMembers(currentUser.workspaceId(), spaceId).stream()
            .filter(member -> "active".equals(member.memberStatus()))
            .toList();
    }

    public List<ProjectSpaceMemberCandidate> searchCandidates(CurrentUser currentUser, UUID spaceId, String query) {
        requireManager(currentUser, spaceId, false);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member search query is too long");
        }
        var existing = membershipRepository.listMembers(currentUser.workspaceId(), spaceId).stream()
            .filter(member -> "active".equals(member.memberStatus()))
            .map(ProjectSpaceMember::userId)
            .collect(java.util.stream.Collectors.toSet());
        return subjectDirectory.listActiveMembers(currentUser.workspaceId(), currentUser.id()).stream()
            .filter(member -> !existing.contains(member.id()))
            .filter(member -> matches(member, normalized))
            .sorted(Comparator.comparing(MemberProfile::displayName, String.CASE_INSENSITIVE_ORDER))
            .limit(50)
            .map(member -> new ProjectSpaceMemberCandidate(
                member.id(), member.username(), member.displayName(), member.avatarFileId(), member.email(),
                member.departments()
            ))
            .toList();
    }

    @Transactional
    public ProjectSpaceMember addMember(CurrentUser currentUser, UUID spaceId, UUID userId, String roleKey) {
        lockActiveSpace(currentUser, spaceId);
        ProjectSpaceMember actor = requireManager(currentUser, spaceId, true);
        ProjectSpaceRole role = assignableRole(actor, roleKey);
        MemberProfile target = requireActiveWorkspaceUser(currentUser, userId);
        ProjectSpaceMember existing = membershipRepository.findMemberByUser(currentUser.workspaceId(), spaceId, userId).orElse(null);
        if (existing != null && "active".equals(existing.memberStatus())) {
            if (role.name().equals(existing.roleKey())) {
                return existing;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a project space member");
        }
        UUID memberId;
        try {
            if (existing == null) {
                memberId = membershipRepository.createMember(
                    currentUser.workspaceId(), spaceId, target.id(), role.name(), currentUser.id()
                );
            } else {
                memberId = existing.id();
                membershipRepository.reactivateMember(
                    currentUser.workspaceId(), spaceId, memberId, role.name(), currentUser.id()
                );
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project space membership already changed", exception);
        }
        ProjectSpaceMember added = requireMember(currentUser.workspaceId(), spaceId, memberId);
        auditMember(currentUser, "project_space.member.added", spaceId, added, Map.of("role", role.name()));
        notifyUser(currentUser, added.userId(), spaceId, "project_space.member.added", "已加入项目空间", role.name(), memberId.toString());
        return added;
    }

    /**
     * Migration-only member writer used by the project space migration batch pipeline. The caller
     * must already hold migration governance permission (requireManageProjects); this method
     * intentionally skips the actor's space-role checks, member-level audit and member
     * notifications because the batch-level migration audit covers the whole operation. It keeps
     * the regular member write invariants and row shapes: the space must be active, the user must
     * be an active member of the same workspace, an existing active membership with the same role
     * is an idempotent no-op, and otherwise member plus role assignment rows are written exactly
     * like the standard membership paths.
     */
    @Transactional
    public ProjectSpaceMember addMigratedMember(UUID workspaceId, UUID spaceId, UUID userId, String roleKey, UUID actorId) {
        String status = membershipRepository.lockSpaceStatus(workspaceId, spaceId);
        if (!"active".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project space must be active for migration member writes");
        }
        ProjectSpaceRole role = parseRole(roleKey);
        MemberProfile target = subjectDirectory.findActiveMember(workspaceId, actorId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Migration member is not an active workspace user"));
        ProjectSpaceMember existing = membershipRepository.findMemberByUser(workspaceId, spaceId, userId).orElse(null);
        if (existing != null && "active".equals(existing.memberStatus())) {
            if (role.name().equals(existing.roleKey())) {
                return existing;
            }
            membershipRepository.changeRole(workspaceId, spaceId, existing.id(), role.name(), actorId);
            return requireMember(workspaceId, spaceId, existing.id());
        }
        UUID memberId;
        if (existing == null) {
            memberId = membershipRepository.createMember(workspaceId, spaceId, target.id(), role.name(), actorId);
        } else {
            memberId = existing.id();
            membershipRepository.reactivateMember(workspaceId, spaceId, memberId, role.name(), actorId);
        }
        return requireMember(workspaceId, spaceId, memberId);
    }

    @Transactional
    public ProjectSpaceMember changeRole(CurrentUser currentUser, UUID spaceId, UUID memberId, String roleKey) {
        lockActiveSpace(currentUser, spaceId);
        ProjectSpaceMember actor = requireManager(currentUser, spaceId, true);
        ProjectSpaceMember target = requireActiveMember(currentUser.workspaceId(), spaceId, memberId);
        ProjectSpaceRole nextRole = parseRole(roleKey);
        ProjectSpaceRole actorRole = parseRole(actor.roleKey());
        ProjectSpaceRole previousRole = parseRole(target.roleKey());
        if (nextRole == ProjectSpaceRole.owner || previousRole == ProjectSpaceRole.owner) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use owner transfer for owner role changes");
        }
        if (!actorRole.canAssign(nextRole) || (actorRole == ProjectSpaceRole.admin && previousRole == ProjectSpaceRole.admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project space role change is not allowed");
        }
        if (nextRole == previousRole) {
            return target;
        }
        membershipRepository.changeRole(currentUser.workspaceId(), spaceId, memberId, nextRole.name(), currentUser.id());
        ProjectSpaceMember changed = requireMember(currentUser.workspaceId(), spaceId, memberId);
        auditMember(currentUser, "project_space.member.role_changed", spaceId, changed, Map.of(
            "previousRole", previousRole.name(), "currentRole", nextRole.name()
        ));
        notifyUser(currentUser, changed.userId(), spaceId, "project_space.member.role_changed", "空间角色已变更", nextRole.name(), memberId.toString());
        return changed;
    }

    @Transactional
    public ProjectSpaceMember removeMember(CurrentUser currentUser, UUID spaceId, UUID memberId) {
        lockSpace(currentUser, spaceId);
        ProjectSpaceMember actor = requireManager(currentUser, spaceId, true);
        ProjectSpaceMember target = requireMember(currentUser.workspaceId(), spaceId, memberId);
        if ("removed".equals(target.memberStatus())) {
            return target;
        }
        if (target.userId().equals(currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use leave to remove the current member");
        }
        ProjectSpaceRole actorRole = parseRole(actor.roleKey());
        ProjectSpaceRole targetRole = parseRole(target.roleKey());
        if (actorRole == ProjectSpaceRole.admin && (targetRole == ProjectSpaceRole.owner || targetRole == ProjectSpaceRole.admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Space admin cannot remove owner or admin");
        }
        protectLastOwner(currentUser.workspaceId(), spaceId, targetRole);
        membershipRepository.removeMember(currentUser.workspaceId(), spaceId, memberId, currentUser.id());
        ProjectSpaceMember removed = requireMember(currentUser.workspaceId(), spaceId, memberId);
        auditMember(currentUser, "project_space.member.removed", spaceId, removed, Map.of("previousRole", targetRole.name()));
        notifyUser(currentUser, removed.userId(), spaceId, "project_space.member.removed", "已移出项目空间", targetRole.name(), memberId.toString());
        return removed;
    }

    @Transactional
    public ProjectSpaceMember leave(CurrentUser currentUser, UUID spaceId) {
        lockSpace(currentUser, spaceId);
        ProjectSpaceMember actor = requireEffectiveMember(currentUser, spaceId);
        ProjectSpaceRole role = parseRole(actor.roleKey());
        protectLastOwner(currentUser.workspaceId(), spaceId, role);
        membershipRepository.removeMember(currentUser.workspaceId(), spaceId, actor.id(), currentUser.id());
        ProjectSpaceMember removed = requireMember(currentUser.workspaceId(), spaceId, actor.id());
        auditMember(currentUser, "project_space.member.left", spaceId, removed, Map.of("previousRole", role.name()));
        return removed;
    }

    @Transactional
    public List<ProjectSpaceMember> transferOwner(CurrentUser currentUser, UUID spaceId, UUID targetMemberId) {
        lockActiveSpace(currentUser, spaceId);
        ProjectSpaceMember actor = requireEffectiveMember(currentUser, spaceId);
        ProjectSpaceMember target = requireActiveMember(currentUser.workspaceId(), spaceId, targetMemberId);
        if (target.id().equals(actor.id())) {
            return membershipRepository.listMembers(currentUser.workspaceId(), spaceId);
        }
        if ("owner".equals(target.roleKey()) && "admin".equals(actor.roleKey())) {
            return membershipRepository.listMembers(currentUser.workspaceId(), spaceId);
        }
        if (!"owner".equals(actor.roleKey())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project space owner role required");
        }
        if (!"active".equals(target.userStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target member is disabled");
        }
        String previousTargetRole = target.roleKey();
        membershipRepository.transferOwner(currentUser.workspaceId(), spaceId, actor.id(), target.id(), currentUser.id());
        auditMember(currentUser, "project_space.owner.transferred", spaceId, target, Map.of(
            "previousOwnerUserId", actor.userId().toString(),
            "currentOwnerUserId", target.userId().toString(),
            "previousTargetRole", previousTargetRole
        ));
        notifyUser(currentUser, target.userId(), spaceId, "project_space.owner.transferred", "已成为项目空间所有者", "owner", target.id().toString());
        return membershipRepository.listMembers(currentUser.workspaceId(), spaceId);
    }

    public List<ProjectSpaceInvitation> listInvitations(CurrentUser currentUser, UUID spaceId) {
        requireManager(currentUser, spaceId, false);
        membershipRepository.expireInvitations(currentUser.workspaceId(), spaceId);
        return membershipRepository.listInvitations(currentUser.workspaceId(), spaceId);
    }

    @Transactional
    public ProjectSpaceInvitation invite(
        CurrentUser currentUser,
        UUID spaceId,
        UUID inviteeUserId,
        String roleKey,
        Integer expiresInHours
    ) {
        lockActiveSpace(currentUser, spaceId);
        ProjectSpaceMember actor = requireManager(currentUser, spaceId, true);
        ProjectSpaceRole role = assignableRole(actor, roleKey);
        if (role == ProjectSpaceRole.owner) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner role requires transfer");
        }
        MemberProfile invitee = requireActiveWorkspaceUser(currentUser, inviteeUserId);
        ProjectSpaceMember existingMember = membershipRepository.findMemberByUser(
            currentUser.workspaceId(), spaceId, inviteeUserId
        ).orElse(null);
        if (existingMember != null && "active".equals(existingMember.memberStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a project space member");
        }
        membershipRepository.expireInvitations(currentUser.workspaceId(), spaceId);
        ProjectSpaceInvitation pending = membershipRepository.findPendingInvitationByUser(
            currentUser.workspaceId(), spaceId, inviteeUserId
        ).orElse(null);
        if (pending != null) {
            if (role.name().equals(pending.roleKey())) {
                return pending;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending invitation already exists");
        }
        Instant expiresAt = Instant.now().plus(invitationHours(expiresInHours), ChronoUnit.HOURS);
        ProjectSpaceInvitation invitation;
        try {
            invitation = membershipRepository.createInvitation(
                currentUser.workspaceId(), spaceId, invitee.id(), role.name(), tokenHash(), expiresAt,
                currentUser.id(), RequestBoundaryContext.current().requestId()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending invitation already exists", exception);
        }
        auditInvitation(currentUser, "project_space.invitation.created", invitation, Map.of(
            "role", role.name(), "previousStatus", "none", "currentStatus", "pending"
        ));
        notifyInvitation(currentUser, invitation, "项目空间邀请", "created");
        return invitation;
    }

    @Transactional
    public ProjectSpaceInvitation resendInvitation(CurrentUser currentUser, UUID spaceId, UUID invitationId, Integer expiresInHours) {
        lockActiveSpace(currentUser, spaceId);
        requireManager(currentUser, spaceId, true);
        membershipRepository.expireInvitations(currentUser.workspaceId(), spaceId);
        ProjectSpaceInvitation invitation = requireInvitationForSpace(currentUser, spaceId, invitationId);
        if (!"pending".equals(invitation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending invitations can be resent");
        }
        boolean changed = membershipRepository.resendInvitation(
            currentUser.workspaceId(), invitationId, tokenHash(),
            Instant.now().plus(invitationHours(expiresInHours), ChronoUnit.HOURS),
            RequestBoundaryContext.current().requestId()
        );
        ProjectSpaceInvitation resent = requireInvitation(currentUser.workspaceId(), invitationId);
        if (!changed) {
            return resent;
        }
        auditInvitation(currentUser, "project_space.invitation.resent", resent, Map.of(
            "version", Long.toString(resent.version()), "previousStatus", "pending", "currentStatus", "pending"
        ));
        notifyInvitation(currentUser, resent, "项目空间邀请已更新", "resent");
        return resent;
    }

    @Transactional
    public ProjectSpaceInvitation revokeInvitation(CurrentUser currentUser, UUID spaceId, UUID invitationId) {
        lockSpace(currentUser, spaceId);
        requireManager(currentUser, spaceId, true);
        membershipRepository.expireInvitations(currentUser.workspaceId(), spaceId);
        ProjectSpaceInvitation invitation = requireInvitationForSpace(currentUser, spaceId, invitationId);
        if ("revoked".equals(invitation.status())) {
            return invitation;
        }
        if (!"pending".equals(invitation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending invitations can be revoked");
        }
        membershipRepository.updateInvitationStatus(currentUser.workspaceId(), invitationId, "revoked", currentUser.id());
        ProjectSpaceInvitation revoked = requireInvitation(currentUser.workspaceId(), invitationId);
        auditInvitation(currentUser, "project_space.invitation.revoked", revoked, Map.of(
            "previousStatus", "pending", "currentStatus", "revoked"
        ));
        return revoked;
    }

    @Transactional
    public ProjectSpaceMember acceptInvitation(CurrentUser currentUser, UUID invitationId) {
        ProjectSpaceInvitation invitation = requireRecipientInvitation(currentUser, invitationId);
        lockActiveSpace(currentUser, invitation.spaceId());
        membershipRepository.expireInvitations(currentUser.workspaceId(), invitation.spaceId());
        invitation = requireRecipientInvitation(currentUser, invitationId);
        ProjectSpaceMember existing = membershipRepository.findMemberByUser(
            currentUser.workspaceId(), invitation.spaceId(), currentUser.id()
        ).orElse(null);
        if ("accepted".equals(invitation.status()) && existing != null && existing.effective()) {
            return existing;
        }
        if (!"pending".equals(invitation.status()) || !invitation.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation is no longer available");
        }
        UUID memberId;
        if (existing == null) {
            memberId = membershipRepository.createMember(
                currentUser.workspaceId(), invitation.spaceId(), currentUser.id(), invitation.roleKey(), currentUser.id()
            );
        } else if ("removed".equals(existing.memberStatus())) {
            memberId = existing.id();
            membershipRepository.reactivateMember(
                currentUser.workspaceId(), invitation.spaceId(), memberId, invitation.roleKey(), currentUser.id()
            );
        } else {
            memberId = existing.id();
        }
        membershipRepository.updateInvitationStatus(currentUser.workspaceId(), invitationId, "accepted", currentUser.id());
        ProjectSpaceMember accepted = requireMember(currentUser.workspaceId(), invitation.spaceId(), memberId);
        auditInvitation(currentUser, "project_space.invitation.accepted", invitation, Map.of(
            "memberId", memberId.toString(), "role", invitation.roleKey(),
            "previousStatus", "pending", "currentStatus", "accepted"
        ));
        notifyUser(currentUser, invitation.invitedBy(), invitation.spaceId(), "project_space.invitation.accepted", "项目空间邀请已接受", invitation.roleKey(), invitationId.toString());
        return accepted;
    }

    @Transactional
    public ProjectSpaceInvitation rejectInvitation(CurrentUser currentUser, UUID invitationId) {
        ProjectSpaceInvitation invitation = requireRecipientInvitation(currentUser, invitationId);
        lockSpace(currentUser, invitation.spaceId());
        membershipRepository.expireInvitations(currentUser.workspaceId(), invitation.spaceId());
        invitation = requireRecipientInvitation(currentUser, invitationId);
        if ("rejected".equals(invitation.status())) {
            return invitation;
        }
        if (!"pending".equals(invitation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation is no longer available");
        }
        membershipRepository.updateInvitationStatus(currentUser.workspaceId(), invitationId, "rejected", currentUser.id());
        ProjectSpaceInvitation rejected = requireInvitation(currentUser.workspaceId(), invitationId);
        auditInvitation(currentUser, "project_space.invitation.rejected", rejected, Map.of(
            "previousStatus", "pending", "currentStatus", "rejected"
        ));
        return rejected;
    }

    public void requireCanDeactivateUser(UUID workspaceId, UUID userId) {
        List<UUID> soleOwnerSpaces = membershipRepository.listSoleOwnerSpaceIds(workspaceId, userId);
        if (!soleOwnerSpaces.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Member is the last active owner of " + soleOwnerSpaces.size() + " project space(s)"
            );
        }
    }

    @Transactional
    public int handoverSoleOwnerSpaces(CurrentUser operator, UUID userId, UUID handoverToUserId) {
        requireActiveWorkspaceUser(operator, handoverToUserId);
        List<UUID> spaceIds = membershipRepository.listSoleOwnerSpaceIds(operator.workspaceId(), userId);
        for (UUID spaceId : spaceIds) {
            membershipRepository.lockSpaceStatus(operator.workspaceId(), spaceId);
            ProjectSpaceMember source = membershipRepository.findMemberByUser(operator.workspaceId(), spaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Project space owner membership changed"));
            ProjectSpaceMember target = membershipRepository.findMemberByUser(
                operator.workspaceId(), spaceId, handoverToUserId
            ).orElse(null);
            UUID targetMemberId;
            if (target == null) {
                targetMemberId = membershipRepository.createMember(
                    operator.workspaceId(), spaceId, handoverToUserId, "admin", operator.id()
                );
            } else if (!target.effective()) {
                if (!"active".equals(target.userStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Handover member is not active");
                }
                targetMemberId = target.id();
                membershipRepository.reactivateMember(
                    operator.workspaceId(), spaceId, targetMemberId, "admin", operator.id()
                );
            } else {
                targetMemberId = target.id();
            }
            membershipRepository.transferOwner(
                operator.workspaceId(), spaceId, source.id(), targetMemberId, operator.id()
            );
            ProjectSpaceMember newOwner = requireMember(operator.workspaceId(), spaceId, targetMemberId);
            auditMember(operator, "project_space.owner.handover", spaceId, newOwner, Map.of(
                "previousOwnerUserId", userId.toString(),
                "currentOwnerUserId", handoverToUserId.toString(),
                "source", "member_offboarding"
            ));
            notifyUser(
                operator, handoverToUserId, spaceId, "project_space.owner.handover",
                "项目空间所有权已交接", "owner", userId + ":" + handoverToUserId
            );
        }
        return spaceIds.size();
    }

    private ProjectSpaceMember requireManager(CurrentUser currentUser, UUID spaceId, boolean mutation) {
        ProjectSpaceMember actor = requireEffectiveMember(currentUser, spaceId);
        ProjectSpaceRole role = parseRole(actor.roleKey());
        if (!role.canManageMembers()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project space owner or admin role required");
        }
        if (mutation && !"active".equals(projectSpaceService.getVisible(currentUser, spaceId).status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project space must be active for member governance");
        }
        return actor;
    }

    private ProjectSpaceMember requireEffectiveMember(CurrentUser currentUser, UUID spaceId) {
        projectSpaceService.getVisible(currentUser, spaceId);
        ProjectSpaceMember member = membershipRepository.findMemberByUser(
            currentUser.workspaceId(), spaceId, currentUser.id()
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project space not found"));
        if (!member.effective()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active project space membership required");
        }
        return member;
    }

    private ProjectSpaceMember requireActiveMember(UUID workspaceId, UUID spaceId, UUID memberId) {
        ProjectSpaceMember member = requireMember(workspaceId, spaceId, memberId);
        if (!member.effective()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target project space member is not active");
        }
        return member;
    }

    private ProjectSpaceMember requireMember(UUID workspaceId, UUID spaceId, UUID memberId) {
        return membershipRepository.findMember(workspaceId, spaceId, memberId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project space member not found"));
    }

    private MemberProfile requireActiveWorkspaceUser(CurrentUser currentUser, UUID userId) {
        return subjectDirectory.findActiveMember(currentUser.workspaceId(), currentUser.id(), userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active workspace member not found"));
    }

    private ProjectSpaceInvitation requireRecipientInvitation(CurrentUser currentUser, UUID invitationId) {
        ProjectSpaceInvitation invitation = requireInvitation(currentUser.workspaceId(), invitationId);
        if (!currentUser.id().equals(invitation.inviteeUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        return invitation;
    }

    private ProjectSpaceInvitation requireInvitationForSpace(CurrentUser currentUser, UUID spaceId, UUID invitationId) {
        ProjectSpaceInvitation invitation = requireInvitation(currentUser.workspaceId(), invitationId);
        if (!spaceId.equals(invitation.spaceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        return invitation;
    }

    private ProjectSpaceInvitation requireInvitation(UUID workspaceId, UUID invitationId) {
        return membershipRepository.findInvitation(workspaceId, invitationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
    }

    private ProjectSpaceRole assignableRole(ProjectSpaceMember actor, String roleKey) {
        ProjectSpaceRole role = parseRole(roleKey);
        if (!parseRole(actor.roleKey()).canAssign(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project space role assignment is not allowed");
        }
        return role;
    }

    private ProjectSpaceRole parseRole(String roleKey) {
        try {
            return ProjectSpaceRole.parse(roleKey);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void protectLastOwner(UUID workspaceId, UUID spaceId, ProjectSpaceRole targetRole) {
        if (targetRole == ProjectSpaceRole.owner && membershipRepository.countActiveOwners(workspaceId, spaceId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project space must keep at least one active owner");
        }
    }

    private void lockActiveSpace(CurrentUser currentUser, UUID spaceId) {
        String status = lockSpace(currentUser, spaceId);
        if (!"active".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project space must be active for member governance");
        }
    }

    private String lockSpace(CurrentUser currentUser, UUID spaceId) {
        return membershipRepository.lockSpaceStatus(currentUser.workspaceId(), spaceId);
    }

    private int invitationHours(Integer value) {
        int hours = value == null ? DEFAULT_INVITATION_HOURS : value;
        if (hours < 1 || hours > MAX_INVITATION_HOURS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation expiry must be between 1 and 720 hours");
        }
        return hours;
    }

    private String tokenHash() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean matches(MemberProfile member, String query) {
        if (query.isBlank()) {
            return true;
        }
        return contains(member.username(), query) || contains(member.displayName(), query) || contains(member.email(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void auditMember(CurrentUser actor, String action, UUID spaceId, ProjectSpaceMember member, Map<String, Object> details) {
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>(details);
        metadata.put("spaceId", spaceId.toString());
        metadata.put("memberId", member.id().toString());
        metadata.put("userId", member.userId().toString());
        auditService.log(actor, action, "project_space_member", member.id(), metadata);
    }

    private void auditInvitation(CurrentUser actor, String action, ProjectSpaceInvitation invitation, Map<String, Object> details) {
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>(details);
        metadata.put("spaceId", invitation.spaceId().toString());
        metadata.put("inviteeUserId", invitation.inviteeUserId().toString());
        metadata.put("status", invitation.status());
        metadata.put("invitationVersion", invitation.version());
        auditService.log(actor, action, "project_space_invitation", invitation.id(), metadata);
    }

    private void notifyInvitation(CurrentUser actor, ProjectSpaceInvitation invitation, String title, String action) {
        notifyUser(
            actor, invitation.inviteeUserId(), invitation.spaceId(), "project_space.invitation." + action,
            title, invitation.roleKey(), invitation.id() + ":" + invitation.version()
        );
    }

    private void notifyUser(
        CurrentUser actor,
        UUID recipientId,
        UUID spaceId,
        String eventKey,
        String title,
        String body,
        String mutationKey
    ) {
        String requestId = RequestBoundaryContext.current().requestId();
        String dedupeSource = eventKey + ":" + mutationKey + ":" + requestId;
        String dedupe = eventKey + ":" + UUID.nameUUIDFromBytes(dedupeSource.getBytes(StandardCharsets.UTF_8));
        eventRepository.append(
            actor.workspaceId(), "notification.created", "project_space", spaceId, actor.id(),
            Map.of(
                "recipientId", recipientId.toString(),
                "notificationType", "project",
                "title", title,
                "body", body,
                "targetType", "project_space",
                "targetId", spaceId.toString(),
                "webPath", "/project-spaces/" + spaceId,
                "dedupeKey", dedupe
            ),
            dedupe
        );
    }
}
