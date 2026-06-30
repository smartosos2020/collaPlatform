package com.colla.platform.modules.identity.api;

import com.colla.platform.modules.identity.application.OrganizationService;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentMember;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentSummary;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentTreeNode;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/departments")
public class OrganizationController {
    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/tree")
    public List<DepartmentTreeNode> tree(Authentication authentication) {
        return organizationService.tree(currentUser(authentication));
    }

    @PostMapping
    public DepartmentSummary create(@Valid @RequestBody DepartmentRequest request, Authentication authentication) {
        return organizationService.createDepartment(
            currentUser(authentication),
            request.parentId(),
            request.code(),
            request.name(),
            request.sortOrder()
        );
    }

    @PatchMapping("/{departmentId}")
    public DepartmentSummary update(
        @PathVariable UUID departmentId,
        @Valid @RequestBody DepartmentRequest request,
        Authentication authentication
    ) {
        return organizationService.updateDepartment(
            currentUser(authentication),
            departmentId,
            request.code(),
            request.name(),
            request.sortOrder()
        );
    }

    @PostMapping("/{departmentId}/move")
    public DepartmentSummary move(
        @PathVariable UUID departmentId,
        @Valid @RequestBody MoveDepartmentRequest request,
        Authentication authentication
    ) {
        return organizationService.moveDepartment(currentUser(authentication), departmentId, request.parentId(), request.sortOrder());
    }

    @PostMapping("/{departmentId}/disable")
    public void disable(@PathVariable UUID departmentId, Authentication authentication) {
        organizationService.disableDepartment(currentUser(authentication), departmentId);
    }

    @PostMapping("/{departmentId}/enable")
    public void enable(@PathVariable UUID departmentId, Authentication authentication) {
        organizationService.enableDepartment(currentUser(authentication), departmentId);
    }

    @DeleteMapping("/{departmentId}")
    public void delete(@PathVariable UUID departmentId, Authentication authentication) {
        organizationService.deleteDepartment(currentUser(authentication), departmentId);
    }

    @GetMapping("/{departmentId}/members")
    public List<DepartmentMember> members(@PathVariable UUID departmentId, Authentication authentication) {
        return organizationService.listMembers(currentUser(authentication), departmentId);
    }

    @PostMapping("/{departmentId}/members")
    public void addMember(
        @PathVariable UUID departmentId,
        @Valid @RequestBody DepartmentMemberRequest request,
        Authentication authentication
    ) {
        organizationService.addMember(currentUser(authentication), departmentId, request.userId(), request.relationType());
    }

    @DeleteMapping("/{departmentId}/members/{userId}")
    public void removeMember(@PathVariable UUID departmentId, @PathVariable UUID userId, Authentication authentication) {
        organizationService.removeMember(currentUser(authentication), departmentId, userId);
    }

    @PostMapping("/{departmentId}/managers")
    public void addManager(
        @PathVariable UUID departmentId,
        @Valid @RequestBody DepartmentManagerRequest request,
        Authentication authentication
    ) {
        organizationService.addManager(currentUser(authentication), departmentId, request.userId(), request.managerType());
    }

    @DeleteMapping("/{departmentId}/managers/{userId}")
    public void removeManager(
        @PathVariable UUID departmentId,
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "primary") String managerType,
        Authentication authentication
    ) {
        organizationService.removeManager(currentUser(authentication), departmentId, userId, managerType);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record DepartmentRequest(UUID parentId, @NotBlank String code, @NotBlank String name, Integer sortOrder) {
    }

    public record MoveDepartmentRequest(UUID parentId, Integer sortOrder) {
    }

    public record DepartmentMemberRequest(@NotNull UUID userId, String relationType) {
    }

    public record DepartmentManagerRequest(@NotNull UUID userId, String managerType) {
    }
}
