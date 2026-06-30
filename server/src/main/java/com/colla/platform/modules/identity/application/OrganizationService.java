package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentManager;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentMember;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentSummary;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentTreeNode;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.identity.infrastructure.OrganizationRepository;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final IdentityRepository identityRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public OrganizationService(
        OrganizationRepository organizationRepository,
        IdentityRepository identityRepository,
        PermissionService permissionService,
        AuditService auditService
    ) {
        this.organizationRepository = organizationRepository;
        this.identityRepository = identityRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public List<DepartmentTreeNode> tree(CurrentUser operator) {
        permissionService.requireViewOrganization(operator);
        return buildTree(operator.workspaceId());
    }

    public List<DepartmentMember> listMembers(CurrentUser operator, UUID departmentId) {
        permissionService.requireViewOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        return organizationRepository.listDepartmentMembers(operator.workspaceId(), departmentId);
    }

    @Transactional
    public DepartmentSummary createDepartment(CurrentUser operator, UUID parentId, String code, String name, Integer sortOrder) {
        permissionService.requireManageOrganization(operator);
        if (parentId != null) {
            DepartmentSummary parent = requireDepartment(operator.workspaceId(), parentId);
            if (!"active".equals(parent.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent department is disabled");
            }
        }
        UUID departmentId = organizationRepository.createDepartment(
            operator.workspaceId(),
            parentId,
            normalizeCode(code),
            normalizeName(name),
            sortOrder == null ? 0 : sortOrder,
            operator.id()
        );
        auditService.log(operator, "department.created", "department", departmentId, Map.of("code", code, "parentId", parentId == null ? "" : parentId));
        return requireDepartment(operator.workspaceId(), departmentId);
    }

    @Transactional
    public DepartmentSummary updateDepartment(CurrentUser operator, UUID departmentId, String code, String name, Integer sortOrder) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        organizationRepository.updateDepartment(
            operator.workspaceId(),
            departmentId,
            normalizeCode(code),
            normalizeName(name),
            sortOrder == null ? 0 : sortOrder,
            operator.id()
        );
        auditService.log(operator, "department.updated", "department", departmentId, Map.of("code", code));
        return requireDepartment(operator.workspaceId(), departmentId);
    }

    @Transactional
    public DepartmentSummary moveDepartment(CurrentUser operator, UUID departmentId, UUID parentId, Integer sortOrder) {
        permissionService.requireManageOrganization(operator);
        DepartmentSummary current = requireDepartment(operator.workspaceId(), departmentId);
        if (departmentId.equals(parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department cannot be moved under itself");
        }
        if (parentId != null) {
            DepartmentSummary parent = requireDepartment(operator.workspaceId(), parentId);
            if (parent.path().startsWith(current.path() + "/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department cannot be moved under its descendant");
            }
            if (!"active".equals(parent.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent department is disabled");
            }
        }
        organizationRepository.moveDepartment(operator.workspaceId(), departmentId, parentId, sortOrder == null ? 0 : sortOrder, operator.id());
        auditService.log(operator, "department.moved", "department", departmentId, Map.of("parentId", parentId == null ? "" : parentId));
        return requireDepartment(operator.workspaceId(), departmentId);
    }

    @Transactional
    public void disableDepartment(CurrentUser operator, UUID departmentId) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        organizationRepository.disableDepartment(operator.workspaceId(), departmentId, operator.id());
        auditService.log(operator, "department.disabled", "department", departmentId, Map.of());
    }

    @Transactional
    public void enableDepartment(CurrentUser operator, UUID departmentId) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        organizationRepository.enableDepartment(operator.workspaceId(), departmentId, operator.id());
        auditService.log(operator, "department.enabled", "department", departmentId, Map.of());
    }

    @Transactional
    public void deleteDepartment(CurrentUser operator, UUID departmentId) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        if (organizationRepository.hasChildren(operator.workspaceId(), departmentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department with children cannot be deleted");
        }
        if (organizationRepository.hasActiveMembers(operator.workspaceId(), departmentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department with members cannot be deleted");
        }
        organizationRepository.deleteDepartment(operator.workspaceId(), departmentId);
        auditService.log(operator, "department.deleted", "department", departmentId, Map.of());
    }

    @Transactional
    public void addMember(CurrentUser operator, UUID departmentId, UUID userId, String relationType) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        requireUser(operator.workspaceId(), userId);
        String normalizedRelation = normalizeRelationType(relationType);
        organizationRepository.addDepartmentMember(operator.workspaceId(), departmentId, userId, normalizedRelation, operator.id());
        auditService.log(
            operator,
            "department.member.added",
            "department",
            departmentId,
            Map.of("userId", userId, "relationType", normalizedRelation)
        );
    }

    @Transactional
    public void removeMember(CurrentUser operator, UUID departmentId, UUID userId) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        requireUser(operator.workspaceId(), userId);
        organizationRepository.removeDepartmentMember(operator.workspaceId(), departmentId, userId);
        auditService.log(operator, "department.member.removed", "department", departmentId, Map.of("userId", userId));
    }

    @Transactional
    public void addManager(CurrentUser operator, UUID departmentId, UUID userId, String managerType) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        requireUser(operator.workspaceId(), userId);
        String normalizedType = normalizeManagerType(managerType);
        organizationRepository.addDepartmentMember(operator.workspaceId(), departmentId, userId, "member", operator.id());
        organizationRepository.addDepartmentManager(operator.workspaceId(), departmentId, userId, normalizedType, operator.id());
        auditService.log(
            operator,
            "department.manager.added",
            "department",
            departmentId,
            Map.of("userId", userId, "managerType", normalizedType)
        );
    }

    @Transactional
    public void removeManager(CurrentUser operator, UUID departmentId, UUID userId, String managerType) {
        permissionService.requireManageOrganization(operator);
        requireDepartment(operator.workspaceId(), departmentId);
        requireUser(operator.workspaceId(), userId);
        String normalizedType = normalizeManagerType(managerType);
        organizationRepository.removeDepartmentManager(operator.workspaceId(), departmentId, userId, normalizedType);
        auditService.log(
            operator,
            "department.manager.removed",
            "department",
            departmentId,
            Map.of("userId", userId, "managerType", normalizedType)
        );
    }

    private List<DepartmentTreeNode> buildTree(UUID workspaceId) {
        List<DepartmentSummary> departments = organizationRepository.listDepartments(workspaceId);
        Map<UUID, MutableNode> nodes = new LinkedHashMap<>();
        for (DepartmentSummary department : departments) {
            nodes.put(department.id(), new MutableNode(department));
        }
        List<MutableNode> roots = new ArrayList<>();
        for (MutableNode node : nodes.values()) {
            if (node.department().parentId() == null || !nodes.containsKey(node.department().parentId())) {
                roots.add(node);
            } else {
                nodes.get(node.department().parentId()).children().add(node);
            }
        }
        return roots.stream().map(root -> toTreeNode(workspaceId, root)).toList();
    }

    private DepartmentTreeNode toTreeNode(UUID workspaceId, MutableNode node) {
        return new DepartmentTreeNode(
            node.department(),
            organizationRepository.listDepartmentManagers(workspaceId, node.department().id()),
            node.children().stream().map(child -> toTreeNode(workspaceId, child)).toList()
        );
    }

    private DepartmentSummary requireDepartment(UUID workspaceId, UUID departmentId) {
        return organizationRepository.findDepartment(workspaceId, departmentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
    }

    private UserAccount requireUser(UUID workspaceId, UUID userId) {
        UserAccount user = identityRepository.findUserById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!workspaceId.equals(user.workspaceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department code is required");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department name is required");
        }
        return name.trim();
    }

    private String normalizeRelationType(String relationType) {
        if (relationType == null || relationType.isBlank()) {
            return "member";
        }
        String normalized = relationType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("primary", "member").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported department member relation type");
        }
        return normalized;
    }

    private String normalizeManagerType(String managerType) {
        if (managerType == null || managerType.isBlank()) {
            return "primary";
        }
        String normalized = managerType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("primary", "deputy").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported department manager type");
        }
        return normalized;
    }

    private record MutableNode(DepartmentSummary department, List<MutableNode> children) {
        private MutableNode(DepartmentSummary department) {
            this(department, new ArrayList<>());
        }
    }
}
