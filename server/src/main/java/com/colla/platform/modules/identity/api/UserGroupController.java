package com.colla.platform.modules.identity.api;

import com.colla.platform.modules.identity.application.UserGroupService;
import com.colla.platform.modules.identity.api.AdminIdentityDtos.AdminExpandedUserGroupMemberView;
import com.colla.platform.modules.identity.api.AdminIdentityDtos.AdminUserGroupMemberView;
import com.colla.platform.modules.identity.api.AdminIdentityDtos.AdminUserGroupView;
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
@RequestMapping("/api/admin/user-groups")
public class UserGroupController {
    private final UserGroupService userGroupService;

    public UserGroupController(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @GetMapping
    public List<AdminUserGroupView> list(
        @RequestParam(defaultValue = "false") boolean activeOnly,
        Authentication authentication
    ) {
        return userGroupService.listGroups(currentUser(authentication), activeOnly).stream()
            .map(AdminIdentityDtos::userGroup)
            .toList();
    }

    @GetMapping("/{groupId}")
    public AdminUserGroupView get(@PathVariable UUID groupId, Authentication authentication) {
        return AdminIdentityDtos.userGroup(userGroupService.getGroup(currentUser(authentication), groupId));
    }

    @PostMapping
    public AdminUserGroupView create(@Valid @RequestBody UserGroupRequest request, Authentication authentication) {
        return AdminIdentityDtos.userGroup(userGroupService.createGroup(
            currentUser(authentication),
            request.code(),
            request.name(),
            request.description(),
            request.groupType()
        ));
    }

    @PatchMapping("/{groupId}")
    public AdminUserGroupView update(
        @PathVariable UUID groupId,
        @Valid @RequestBody UserGroupRequest request,
        Authentication authentication
    ) {
        return AdminIdentityDtos.userGroup(userGroupService.updateGroup(
            currentUser(authentication),
            groupId,
            request.code(),
            request.name(),
            request.description(),
            request.groupType()
        ));
    }

    @PostMapping("/{groupId}/disable")
    public void disable(@PathVariable UUID groupId, Authentication authentication) {
        userGroupService.disableGroup(currentUser(authentication), groupId);
    }

    @PostMapping("/{groupId}/enable")
    public void enable(@PathVariable UUID groupId, Authentication authentication) {
        userGroupService.enableGroup(currentUser(authentication), groupId);
    }

    @DeleteMapping("/{groupId}")
    public void delete(@PathVariable UUID groupId, Authentication authentication) {
        userGroupService.deleteGroup(currentUser(authentication), groupId);
    }

    @GetMapping("/{groupId}/members")
    public List<AdminUserGroupMemberView> members(@PathVariable UUID groupId, Authentication authentication) {
        return userGroupService.listMembers(currentUser(authentication), groupId).stream()
            .map(AdminIdentityDtos::userGroupMember)
            .toList();
    }

    @PostMapping("/{groupId}/members")
    public AdminUserGroupMemberView addMember(
        @PathVariable UUID groupId,
        @Valid @RequestBody UserGroupMemberRequest request,
        Authentication authentication
    ) {
        return AdminIdentityDtos.userGroupMember(
            userGroupService.addMember(currentUser(authentication), groupId, request.subjectType(), request.subjectId())
        );
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    public void removeMember(
        @PathVariable UUID groupId,
        @PathVariable UUID memberId,
        Authentication authentication
    ) {
        userGroupService.removeMember(currentUser(authentication), groupId, memberId);
    }

    @GetMapping("/{groupId}/expanded-members")
    public List<AdminExpandedUserGroupMemberView> expandedMembers(@PathVariable UUID groupId, Authentication authentication) {
        return userGroupService.listExpandedMembers(currentUser(authentication), groupId).stream()
            .map(AdminIdentityDtos::expandedUserGroupMember)
            .toList();
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record UserGroupRequest(@NotBlank String code, @NotBlank String name, String description, String groupType) {
    }

    public record UserGroupMemberRequest(@NotBlank String subjectType, @NotNull UUID subjectId) {
    }
}
