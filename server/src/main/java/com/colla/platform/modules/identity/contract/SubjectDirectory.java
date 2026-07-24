package com.colla.platform.modules.identity.contract;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Minimum-disclosure directory for cross-module subject validation and display.
 */
public interface SubjectDirectory {

    Map<SubjectRef, SubjectSnapshot> resolve(UUID workspaceId, UUID actorId, Collection<SubjectRef> subjects);

    Set<UUID> expandActiveMembers(UUID workspaceId, UUID actorId, Collection<SubjectRef> subjects);

    List<MemberProfile> listActiveMembers(UUID workspaceId, UUID actorId);

    Optional<MemberProfile> findActiveMember(UUID workspaceId, UUID actorId, UUID memberId);

    enum SubjectType {
        MEMBER,
        DEPARTMENT,
        USER_GROUP
    }

    enum SubjectState {
        ACTIVE,
        DISABLED,
        HIDDEN
    }

    record SubjectRef(SubjectType type, UUID id) {
    }

    record SubjectSnapshot(SubjectRef subject, SubjectState state, String displayName) {
        public SubjectSnapshot {
            if (state == SubjectState.HIDDEN) {
                displayName = null;
            }
        }
    }

    record MemberProfile(
        UUID id,
        String username,
        String displayName,
        UUID avatarFileId,
        String email,
        List<String> departments
    ) {
        public MemberProfile {
            departments = departments == null ? List.of() : List.copyOf(departments);
        }
    }
}
