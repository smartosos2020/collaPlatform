package com.colla.platform.modules.identity.infrastructure;

import com.colla.platform.modules.identity.domain.UserGroupModels.ExpandedUserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserGroupRepository {
    List<UserGroupSummary> listGroups(UUID workspaceId, boolean activeOnly);

    Optional<UserGroupSummary> findGroup(UUID workspaceId, UUID groupId);

    UUID createGroup(UUID workspaceId, String code, String name, String description, String groupType, UUID actorId);

    void updateGroup(UUID workspaceId, UUID groupId, String code, String name, String description, String groupType, UUID actorId);

    void disableGroup(UUID workspaceId, UUID groupId, UUID actorId);

    void deleteGroup(UUID workspaceId, UUID groupId);

    boolean hasActiveMembers(UUID workspaceId, UUID groupId);

    List<UserGroupMember> listMembers(UUID workspaceId, UUID groupId);

    UUID addMember(UUID workspaceId, UUID groupId, String subjectType, UUID subjectId, UUID actorId);

    void removeMember(UUID workspaceId, UUID groupId, UUID memberId);

    List<ExpandedUserGroupMember> listExpandedMembers(UUID workspaceId, UUID groupId);
}
