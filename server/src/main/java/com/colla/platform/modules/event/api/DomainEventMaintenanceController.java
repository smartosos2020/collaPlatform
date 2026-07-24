package com.colla.platform.modules.event.api;

import com.colla.platform.modules.event.application.DomainEventMaintenanceService;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeadLetter;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/event-deliveries")
public class DomainEventMaintenanceController {
    private final DomainEventMaintenanceService service;

    public DomainEventMaintenanceController(DomainEventMaintenanceService service) {
        this.service = service;
    }

    @GetMapping("/dead-letters")
    public List<DeadLetter> deadLetters(
        Authentication authentication,
        @RequestParam(required = false) String handlerKey,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return service.inspect(currentUser(authentication), handlerKey, limit);
    }

    @PostMapping("/{deliveryId}/replay")
    public DeadLetter replay(
        Authentication authentication,
        @PathVariable UUID deliveryId,
        @Valid @RequestBody MaintenanceReason request
    ) {
        return service.replay(currentUser(authentication), deliveryId, request.reason());
    }

    @PostMapping("/{deliveryId}/abandon")
    public DeadLetter abandon(
        Authentication authentication,
        @PathVariable UUID deliveryId,
        @Valid @RequestBody MaintenanceReason request
    ) {
        return service.abandon(currentUser(authentication), deliveryId, request.reason());
    }

    private static CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record MaintenanceReason(@NotBlank String reason) {
    }
}
