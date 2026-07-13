package com.colla.platform.modules.admin.api;

import com.colla.platform.modules.admin.application.AdminBatchGovernanceService;
import com.colla.platform.modules.admin.application.AdminBatchGovernanceService.BatchCommand;
import com.colla.platform.modules.admin.application.AdminBatchGovernanceService.BatchReport;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/batch-governance")
public class AdminBatchGovernanceController {
    private final AdminBatchGovernanceService service;

    public AdminBatchGovernanceController(AdminBatchGovernanceService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public List<BatchCapability> capabilities() {
        return service.capabilities();
    }

    @PostMapping("/preview")
    public BatchReport preview(@Valid @RequestBody BatchCommand command, Authentication authentication) {
        return service.preview(currentUser(authentication), command);
    }

    @PostMapping("/execute")
    public BatchReport execute(
        @Valid @RequestBody BatchCommand command,
        @RequestParam(defaultValue = "false") boolean confirm,
        Authentication authentication
    ) {
        return service.execute(currentUser(authentication), command, confirm);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record BatchCapability(String resourceType, String action, String label) {}
}
