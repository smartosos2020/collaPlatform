package com.colla.platform.modules.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.platform.contract.DashboardPersonalization.CardPreference;
import com.colla.platform.modules.platform.infrastructure.DashboardPersonalizationRepository;
import java.time.Instant;
import java.util.ArrayList;
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
        assertThat(response.cards()).hasSize(12);
        verify(repository).completeCommand(any(), eq(1L));
        assertThatThrownBy(() -> service.update(
            WORKSPACE, USER, "request-2", 1,
            List.of(new CardPreference("unknown", 0, false))
        )).hasMessageContaining("all dashboard cards");
    }

    @Test
    void appendsNewCardsToLegacyLayoutAndNormalizesAllPositions() {
        DashboardPersonalizationRepository repository = mock(DashboardPersonalizationRepository.class);
        when(repository.layout(WORKSPACE, USER)).thenReturn(legacyPreferences());
        when(repository.currentVersion(WORKSPACE, USER)).thenReturn(7L);
        when(repository.updatedAt(WORKSPACE, USER)).thenReturn(Instant.EPOCH);
        DashboardPersonalizationService service = new DashboardPersonalizationService(repository);

        var response = service.view(WORKSPACE, USER);

        assertThat(response.version()).isEqualTo(7);
        assertThat(response.cards()).extracting(card -> card.cardKey()).containsExactly(
            "objects.favorites",
            "personal.participating",
            "drafts.own",
            "objects.recent",
            "personal.responsible",
            "personal.todo",
            "personal.watching",
            "work.recent",
            "conversations.unread",
            "approvals.todo",
            "notifications.latest",
            "content.recent"
        );
        assertThat(response.cards()).extracting(card -> card.position()).containsExactly(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
        );
        assertThat(response.cards().get(6).hidden()).isTrue();
        assertThat(response.cards().subList(7, 12)).allMatch(card -> !card.hidden());
    }

    @Test
    void expandsCompleteLegacyRequestBeforePersisting() {
        DashboardPersonalizationRepository repository = mock(DashboardPersonalizationRepository.class);
        when(repository.completedCommand(eq(WORKSPACE), eq(USER), eq("replace_layout"), eq("legacy-write"), any()))
            .thenReturn(Optional.empty());
        when(repository.tryStartCommand(any(), eq(WORKSPACE), eq(USER), eq("replace_layout"), eq("legacy-write"), any()))
            .thenReturn(true);
        when(repository.replace(eq(WORKSPACE), eq(USER), eq(7L), eq(8L), any())).thenReturn(true);
        when(repository.updatedAt(WORKSPACE, USER)).thenReturn(Instant.EPOCH);
        DashboardPersonalizationService service = new DashboardPersonalizationService(repository);

        var response = service.update(WORKSPACE, USER, "legacy-write", 7, legacyPreferences());

        assertThat(response.version()).isEqualTo(8);
        verify(repository).replace(
            eq(WORKSPACE),
            eq(USER),
            eq(7L),
            eq(8L),
            argThat(cards -> cards.size() == 12
                && cards.subList(7, 12).stream().allMatch(card -> !card.hidden())
                && cards.get(7).cardKey().equals("work.recent")
                && cards.get(11).cardKey().equals("content.recent"))
        );
    }

    @Test
    void rejectsNewLegacyWriteAfterCurrentCatalogWasPersisted() {
        DashboardPersonalizationRepository repository = mock(DashboardPersonalizationRepository.class);
        when(repository.completedCommand(eq(WORKSPACE), eq(USER), eq("replace_layout"), eq("stale-client"), any()))
            .thenReturn(Optional.empty());
        when(repository.layout(WORKSPACE, USER)).thenReturn(preferences());
        DashboardPersonalizationService service = new DashboardPersonalizationService(repository);

        assertThatThrownBy(() -> service.update(
            WORKSPACE,
            USER,
            "stale-client",
            8,
            legacyPreferences()
        )).hasMessageContaining("dashboard card catalog changed");

        verify(repository, never()).tryStartCommand(any(), any(), any(), any(), any(), any());
        verify(repository, never()).replace(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void replaysCompletedLegacyRequestWithoutSecondWrite() {
        DashboardPersonalizationRepository repository = mock(DashboardPersonalizationRepository.class);
        String legacyHash = "d521f4a80e61d67c9fabcf30b0ecfe6fae967bd714bb6563104c8cd3398a2b28";
        when(repository.completedCommand(
            eq(WORKSPACE),
            eq(USER),
            eq("replace_layout"),
            eq("stable"),
            eq(legacyHash)
        ))
            .thenReturn(Optional.of(4L));
        when(repository.updatedAt(WORKSPACE, USER)).thenReturn(Instant.EPOCH);
        DashboardPersonalizationService service = new DashboardPersonalizationService(repository);

        assertThat(service.update(WORKSPACE, USER, "stable", 3, legacyPreferences()).version()).isEqualTo(4);
        verify(repository, never()).tryStartCommand(any(), any(), any(), any(), any(), any());
        verify(repository, never()).replace(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void rejectsUnknownDuplicatePartialInvalidAndNullCards() {
        DashboardPersonalizationService service = new DashboardPersonalizationService(
            mock(DashboardPersonalizationRepository.class)
        );

        List<CardPreference> legacyWithNewKey = new ArrayList<>(legacyPreferences());
        legacyWithNewKey.set(0, new CardPreference("work.recent", 0, false));
        assertThatThrownBy(() -> service.update(WORKSPACE, USER, "legacy-unknown", 0, legacyWithNewKey))
            .hasMessageContaining("unknown or duplicate dashboard card");

        List<CardPreference> currentWithUnknownKey = new ArrayList<>(preferences());
        currentWithUnknownKey.set(11, new CardPreference("unknown", 11, false));
        assertThatThrownBy(() -> service.update(WORKSPACE, USER, "current-unknown", 0, currentWithUnknownKey))
            .hasMessageContaining("unknown or duplicate dashboard card");

        assertThatThrownBy(() -> service.update(
            WORKSPACE,
            USER,
            "partial",
            0,
            preferences().subList(0, 11)
        )).hasMessageContaining("all dashboard cards are required");

        List<CardPreference> duplicateKey = new ArrayList<>(preferences());
        duplicateKey.set(11, new CardPreference("drafts.own", 11, false));
        assertThatThrownBy(() -> service.update(WORKSPACE, USER, "duplicate-key", 0, duplicateKey))
            .hasMessageContaining("unknown or duplicate dashboard card");

        List<CardPreference> invalidPosition = new ArrayList<>(preferences());
        invalidPosition.set(11, new CardPreference("content.recent", 12, false));
        assertThatThrownBy(() -> service.update(WORKSPACE, USER, "invalid-position", 0, invalidPosition))
            .hasMessageContaining("invalid dashboard card position");

        List<CardPreference> duplicatePosition = new ArrayList<>(preferences());
        duplicatePosition.set(11, new CardPreference("content.recent", 10, false));
        assertThatThrownBy(() -> service.update(WORKSPACE, USER, "duplicate-position", 0, duplicatePosition))
            .hasMessageContaining("dashboard card positions must be unique");

        List<CardPreference> nullCard = new ArrayList<>(preferences());
        nullCard.set(0, null);
        assertThatThrownBy(() -> service.update(WORKSPACE, USER, "null-card", 0, nullCard))
            .hasMessageContaining("unknown or duplicate dashboard card");
    }

    private List<CardPreference> preferences() {
        return List.of(
            new CardPreference("drafts.own", 0, false),
            new CardPreference("objects.favorites", 1, false),
            new CardPreference("objects.recent", 2, false),
            new CardPreference("personal.participating", 3, false),
            new CardPreference("personal.responsible", 4, false),
            new CardPreference("personal.todo", 5, false),
            new CardPreference("personal.watching", 6, true),
            new CardPreference("work.recent", 7, false),
            new CardPreference("conversations.unread", 8, false),
            new CardPreference("approvals.todo", 9, false),
            new CardPreference("notifications.latest", 10, false),
            new CardPreference("content.recent", 11, false)
        );
    }

    private List<CardPreference> legacyPreferences() {
        return List.of(
            new CardPreference("objects.favorites", 0, false),
            new CardPreference("personal.participating", 1, false),
            new CardPreference("drafts.own", 2, false),
            new CardPreference("objects.recent", 3, false),
            new CardPreference("personal.responsible", 4, false),
            new CardPreference("personal.todo", 5, false),
            new CardPreference("personal.watching", 6, true)
        );
    }
}
