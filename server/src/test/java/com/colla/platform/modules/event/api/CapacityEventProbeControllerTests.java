package com.colla.platform.modules.event.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.event.application.CapacityEventProbeService;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityRunSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class CapacityEventProbeControllerTests {
    @Test
    void preventsCachingOfSecretProtectedResponses() {
        CapacityEventProbeService service = mock(CapacityEventProbeService.class);
        CurrentUser admin = new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "admin",
            "Administrator",
            Set.of("admin"),
            Set.of("admin.access")
        );
        Authentication authentication = mock(Authentication.class);
        UUID runId = UUID.randomUUID();
        CapacityRunSummary summary = new CapacityRunSummary(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        when(authentication.getPrincipal()).thenReturn(admin);
        when(service.summary(admin, runId, "secret")).thenReturn(summary);

        var response = new CapacityEventProbeController(service)
            .summary(authentication, runId, "secret");

        assertThat(response.getBody()).isEqualTo(summary);
        assertThat(response.getHeaders().getCacheControl())
            .isEqualTo("no-store");
    }
}
