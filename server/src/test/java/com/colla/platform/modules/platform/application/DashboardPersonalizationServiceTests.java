package com.colla.platform.modules.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.platform.contract.DashboardPersonalization.CardPreference;
import com.colla.platform.modules.platform.infrastructure.DashboardPersonalizationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardPersonalizationServiceTests {
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void closesUnknownCardsAndPersistsOneOptimisticVersion() {
        DashboardPersonalizationRepository repository = mock(DashboardPersonalizationRepository.class);
        when(repository.completedCommand(eq(WORKSPACE), eq(USER), eq("replace_layout"), eq("request-1"), any()))
            .thenReturn(Optional.empty());
        when(repository.tryStartCommand(any(), eq(WORKSPACE), eq(USER), eq("replace_layout"), eq("request-1"), any()))
            .thenReturn(true);
        when(repository.replace(eq(WORKSPACE), eq(USER), eq(0L), eq(1L), any())).thenReturn(true);
        when(repository.currentVersion(WORKSPACE, USER)).thenReturn(1L);
        when(repository.updatedAt(WORKSPACE, USER)).thenReturn(Instant.EPOCH);
        DashboardPersonalizationService service = new DashboardPersonalizationService(repository);

        var response = service.update(WORKSPACE, USER, "request-1", 0, preferences());

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.cards()).hasSize(7);
        verify(repository).completeCommand(any(), eq(1L));
        assertThatThrownBy(() -> service.update(
            WORKSPACE, USER, "request-2", 1,
            List.of(new CardPreference("unknown", 0, false))
        )).hasMessageContaining("all dashboard cards");
    }

    @Test
    void replaysCompletedRequestWithoutSecondWrite() {
        DashboardPersonalizationRepository repository = mock(DashboardPersonalizationRepository.class);
        when(repository.completedCommand(eq(WORKSPACE), eq(USER), eq("replace_layout"), eq("stable"), any()))
            .thenReturn(Optional.of(4L));
        when(repository.updatedAt(WORKSPACE, USER)).thenReturn(Instant.EPOCH);
        DashboardPersonalizationService service = new DashboardPersonalizationService(repository);

        assertThat(service.update(WORKSPACE, USER, "stable", 3, preferences()).version()).isEqualTo(4);
    }

    private List<CardPreference> preferences() {
        return List.of(
            new CardPreference("drafts.own", 0, false),
            new CardPreference("objects.favorites", 1, false),
            new CardPreference("objects.recent", 2, false),
            new CardPreference("personal.participating", 3, false),
            new CardPreference("personal.responsible", 4, false),
            new CardPreference("personal.todo", 5, false),
            new CardPreference("personal.watching", 6, true)
        );
    }
}
