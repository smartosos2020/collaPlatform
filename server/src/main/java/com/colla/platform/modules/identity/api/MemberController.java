package com.colla.platform.modules.identity.api;

import com.colla.platform.modules.identity.application.MemberService;
import com.colla.platform.modules.identity.domain.AuthModels.MemberSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberSummary> list(Authentication authentication) {
        return memberService.listWorkspaceMembers((CurrentUser) authentication.getPrincipal());
    }
}
