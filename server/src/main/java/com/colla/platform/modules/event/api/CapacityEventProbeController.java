package com.colla.platform.modules.event.api;

import com.colla.platform.modules.event.application.CapacityEventProbeService;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerPage;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityProbeAcknowledgement;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityRunSummary;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/event-deliveries/capacity-runs/{runId}")
public class CapacityEventProbeController {
    private static final String SECRET_HEADER = "X-Colla-Capacity-Secret";
    private final CapacityEventProbeService service;

    public CapacityEventProbeController(CapacityEventProbeService service) {
        this.service = service;
    }

    @PostMapping("/events")
    public ResponseEntity<CapacityProbeAcknowledgement> produce(
        Authentication authentication,
        @PathVariable UUID runId,
        @RequestHeader(SECRET_HEADER) String secret,
        @Valid @RequestBody CapacityProbeRequest request
    ) {
        return noStore(service.produce(
            currentUser(authentication), runId, secret, request.aggregateKey(), request.requestKey()
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<CapacityRunSummary> summary(
        Authentication authentication,
        @PathVariable UUID runId,
        @RequestHeader(SECRET_HEADER) String secret
    ) {
        return noStore(service.summary(currentUser(authentication), runId, secret));
    }

    @GetMapping("/ledger")
    public ResponseEntity<CapacityLedgerPage> ledger(
        Authentication authentication,
        @PathVariable UUID runId,
        @RequestHeader(SECRET_HEADER) String secret,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "1000") int limit
    ) {
        return noStore(service.ledger(currentUser(authentication), runId, secret, cursor, limit));
    }

    private static CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(body);
    }

    public record CapacityProbeRequest(@NotBlank String aggregateKey, @NotBlank String requestKey) {
    }
}
