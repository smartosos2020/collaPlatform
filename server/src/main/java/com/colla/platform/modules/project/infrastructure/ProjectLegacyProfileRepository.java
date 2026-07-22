package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.DuplicateOwnerItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.Findings;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.IllegalRoleItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ImDriftItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.MissingConversationItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.MissingOwnerItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.OrphanMemberItem;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ProjectTotals;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.RoleDistribution;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.SharedConversationItem;
import java.util.UUID;

public interface ProjectLegacyProfileRepository {
    ProjectTotals summarizeTotals(UUID workspaceId);

    RoleDistribution summarizeRoleDistribution(UUID workspaceId);

    Findings<OrphanMemberItem> findOrphanMembers(UUID workspaceId, int limit);

    Findings<IllegalRoleItem> findIllegalRoles(UUID workspaceId, int limit);

    Findings<DuplicateOwnerItem> findDuplicateOwners(UUID workspaceId, int limit);

    Findings<SharedConversationItem> findSharedConversations(UUID workspaceId, int limit);

    Findings<MissingOwnerItem> findProjectsWithoutOwner(UUID workspaceId, int limit);

    Findings<ImDriftItem> findImDrifts(UUID workspaceId, int limit);

    Findings<MissingConversationItem> findMissingConversations(UUID workspaceId, int limit);
}
