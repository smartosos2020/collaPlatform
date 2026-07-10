package com.colla.platform.modules.identity.api;

import com.colla.platform.modules.identity.application.MemberService;
import com.colla.platform.modules.identity.api.AdminIdentityDtos.AdminMemberView;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final MemberService memberService;

    public AdminUserController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<AdminMemberView> list(@RequestParam(required = false) UUID departmentId, Authentication authentication) {
        return memberService.listMembers(currentUser(authentication), departmentId).stream()
            .map(AdminIdentityDtos::member)
            .toList();
    }

    @PostMapping
    public AdminMemberView create(@Valid @RequestBody CreateMemberRequest request, Authentication authentication) {
        return AdminIdentityDtos.member(memberService.createMember(
            currentUser(authentication),
            request.username(),
            request.password(),
            request.displayName(),
            request.email(),
            request.roleCode(),
            request.primaryDepartmentId()
        ));
    }

    @PostMapping("/{userId}/disable")
    public void disable(@PathVariable UUID userId, Authentication authentication) {
        memberService.disableMember(currentUser(authentication), userId);
    }

    @PostMapping("/{userId}/enable")
    public void enable(@PathVariable UUID userId, Authentication authentication) {
        memberService.enableMember(currentUser(authentication), userId);
    }

    @PatchMapping("/{userId}/password")
    public void resetPassword(
        @PathVariable UUID userId,
        @Valid @RequestBody ResetPasswordRequest request,
        Authentication authentication
    ) {
        memberService.resetPassword(currentUser(authentication), userId, request.newPassword());
    }

    @PatchMapping("/{userId}/avatar")
    public void updateAvatar(
        @PathVariable UUID userId,
        @RequestBody UpdateAvatarRequest request,
        Authentication authentication
    ) {
        memberService.updateAvatar(currentUser(authentication), userId, request.avatarFileId());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateMemberRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String displayName,
        @Email String email,
        String roleCode,
        UUID primaryDepartmentId
    ) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8) String newPassword) {
    }

    public record UpdateAvatarRequest(UUID avatarFileId) {
    }
}
