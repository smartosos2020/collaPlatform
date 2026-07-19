package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProjectSpaceModels {
    private ProjectSpaceModels() {
    }

    public enum ProjectSpaceStatus {
        active,
        disabled,
        archived;

        public static ProjectSpaceStatus parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid project space status");
            }
        }

        public boolean canTransitionTo(ProjectSpaceStatus target) {
            if (this == target) {
                return true;
            }
            return switch (this) {
                case active -> Set.of(disabled, archived).contains(target);
                case disabled -> Set.of(active, archived).contains(target);
                case archived -> target == active;
            };
        }
    }

    public enum ProjectSpaceVisibility {
        private_space("private"),
        discoverable("discoverable"),
        workspace("workspace");

        private final String value;

        ProjectSpaceVisibility(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static ProjectSpaceVisibility parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "private" -> private_space;
                case "discoverable" -> discoverable;
                case "workspace" -> workspace;
                default -> throw new IllegalArgumentException("Invalid project space visibility");
            };
        }
    }

    public enum ProjectSpaceRole {
        owner(Set.of("view", "settings", "members", "invite", "roles", "transfer_owner", "lifecycle")),
        admin(Set.of("view", "settings", "members", "invite", "roles", "lifecycle")),
        member(Set.of("view")),
        guest(Set.of("view"));

        private final Set<String> capabilities;

        ProjectSpaceRole(Set<String> capabilities) {
            this.capabilities = capabilities;
        }

        public Set<String> capabilities() {
            return capabilities;
        }

        public boolean canManageMembers() {
            return this == owner || this == admin;
        }

        public boolean canAssign(ProjectSpaceRole target) {
            return this == owner ? target != owner : this == admin && Set.of(member, guest).contains(target);
        }

        public static ProjectSpaceRole parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid project space role");
            }
        }
    }

    public record ProjectSpaceSummary(
        UUID id,
        UUID workspaceId,
        String spaceKey,
        String name,
        String description,
        String status,
        String visibility,
        long version,
        String currentUserRole,
        int memberCount,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Instant disabledAt,
        Instant archivedAt
    ) {
        public boolean isMember() {
            return currentUserRole != null && !currentUserRole.isBlank();
        }

        public boolean canManage() {
            return "owner".equals(currentUserRole) || "admin".equals(currentUserRole);
        }
    }

    public record ProjectSpaceMember(
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
        Instant joinedAt,
        Instant removedAt,
        Instant updatedAt
    ) {
        public boolean effective() {
            return "active".equals(memberStatus) && "active".equals(userStatus) && roleKey != null;
        }
    }

    public record ProjectSpaceInvitation(
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

    public record ProjectSpaceRoleCapability(
        String roleKey,
        List<String> capabilities,
        boolean canManageOwner,
        boolean canGrantAdmin
    ) {
    }

    public record ProjectSpaceMemberCandidate(
        UUID userId,
        String username,
        String displayName,
        UUID avatarFileId,
        String email,
        List<String> departments
    ) {
    }
}
