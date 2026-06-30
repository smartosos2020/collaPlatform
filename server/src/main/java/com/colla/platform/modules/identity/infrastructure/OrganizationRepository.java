package com.colla.platform.modules.identity.infrastructure;

import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentManager;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentMember;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    List<DepartmentSummary> listDepartments(UUID workspaceId);

    Optional<DepartmentSummary> findDepartment(UUID workspaceId, UUID departmentId);

    List<DepartmentManager> listDepartmentManagers(UUID workspaceId, UUID departmentId);

    List<DepartmentMember> listDepartmentMembers(UUID workspaceId, UUID departmentId);

    UUID createDepartment(UUID workspaceId, UUID parentId, String code, String name, int sortOrder, UUID actorId);

    void updateDepartment(UUID workspaceId, UUID departmentId, String code, String name, int sortOrder, UUID actorId);

    void moveDepartment(UUID workspaceId, UUID departmentId, UUID parentId, int sortOrder, UUID actorId);

    void disableDepartment(UUID workspaceId, UUID departmentId, UUID actorId);

    void enableDepartment(UUID workspaceId, UUID departmentId, UUID actorId);

    void deleteDepartment(UUID workspaceId, UUID departmentId);

    boolean hasChildren(UUID workspaceId, UUID departmentId);

    boolean hasActiveMembers(UUID workspaceId, UUID departmentId);

    void addDepartmentMember(UUID workspaceId, UUID departmentId, UUID userId, String relationType, UUID actorId);

    void removeDepartmentMember(UUID workspaceId, UUID departmentId, UUID userId);

    void addDepartmentManager(UUID workspaceId, UUID departmentId, UUID userId, String managerType, UUID actorId);

    void removeDepartmentManager(UUID workspaceId, UUID departmentId, UUID userId, String managerType);
}
