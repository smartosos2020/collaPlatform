package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.contract.DashboardPersonalization;
import com.colla.platform.modules.platform.contract.DashboardPersonalization.CardPreference;
import com.colla.platform.modules.platform.contract.DashboardPersonalization.DashboardCard;
import com.colla.platform.modules.platform.contract.DashboardPersonalization.DashboardLayout;
import com.colla.platform.modules.platform.infrastructure.DashboardPersonalizationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardPersonalizationService implements DashboardPersonalization {
    private static final List<CardDefinition> CATALOG = List.of(
        new CardDefinition("drafts.own", "我的草稿"),
        new CardDefinition("objects.favorites", "收藏对象"),
        new CardDefinition("objects.recent", "最近访问"),
        new CardDefinition("personal.participating", "我参与的"),
        new CardDefinition("personal.responsible", "我负责的"),
        new CardDefinition("personal.todo", "我的待办"),
        new CardDefinition("personal.watching", "我关注的"),
        new CardDefinition("work.recent", "最近事项"),
        new CardDefinition("conversations.unread", "未读会话"),
        new CardDefinition("approvals.todo", "审批待办"),
        new CardDefinition("notifications.latest", "最新通知"),
        new CardDefinition("content.recent", "最近知识内容和表格")
    );
    private static final Map<String, CardDefinition> CATALOG_BY_KEY = CATALOG.stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(CardDefinition::key, definition -> definition));
    private static final Set<String> LEGACY_CATALOG_KEYS = Set.of(
        "drafts.own",
        "objects.favorites",
        "objects.recent",
        "personal.participating",
        "personal.responsible",
        "personal.todo",
        "personal.watching"
    );
    private static final int LEGACY_CARD_COUNT = LEGACY_CATALOG_KEYS.size();
    private final DashboardPersonalizationRepository repository;

    public DashboardPersonalizationService(DashboardPersonalizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DashboardCard> layout(UUID workspaceId, UUID userId) {
        Map<String, CardPreference> stored = new LinkedHashMap<>();
        repository.layout(workspaceId, userId).forEach(card -> stored.put(card.cardKey(), card));
        List<CardDefinition> ordered = new ArrayList<>();
        stored.values().stream()
            .filter(preference -> CATALOG_BY_KEY.containsKey(preference.cardKey()))
            .sorted(Comparator.comparingInt(CardPreference::position).thenComparing(CardPreference::cardKey))
            .map(preference -> CATALOG_BY_KEY.get(preference.cardKey()))
            .forEach(ordered::add);
        CATALOG.stream()
            .filter(definition -> !stored.containsKey(definition.key()))
            .forEach(ordered::add);

        List<DashboardCard> result = new ArrayList<>();
        for (int position = 0; position < ordered.size(); position++) {
            CardDefinition definition = ordered.get(position);
            CardPreference preference = stored.get(definition.key());
            result.add(new DashboardCard(
                definition.key(),
                definition.title(),
                position,
                preference != null && preference.hidden(),
                true
            ));
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional
    public DashboardLayout update(
        UUID workspaceId,
        UUID userId,
        String requestId,
        long expectedVersion,
        List<CardPreference> cards
    ) {
        String stableRequestId = requestId == null ? "" : requestId.trim();
        if (stableRequestId.isBlank() || stableRequestId.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }
        NormalizedRequest normalized = normalize(cards);
        String requestHash = sha256(expectedVersion + ":" + normalized.cardsForHash());
        var replay = repository.completedCommand(workspaceId, userId, "replace_layout", stableRequestId, requestHash);
        if (replay.isPresent()) {
            return new DashboardLayout(replay.get(), layout(workspaceId, userId), repository.updatedAt(workspaceId, userId));
        }
        if (normalized.legacyPayload() && repository.layout(workspaceId, userId).stream()
            .anyMatch(card -> !LEGACY_CATALOG_KEYS.contains(card.cardKey()))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "dashboard card catalog changed; refresh and retry"
            );
        }
        UUID commandId = UUID.randomUUID();
        if (!repository.tryStartCommand(commandId, workspaceId, userId, "replace_layout", stableRequestId, requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId was reused with another payload");
        }
        long nextVersion = expectedVersion + 1;
        if (!repository.replace(workspaceId, userId, expectedVersion, nextVersion, normalized.cardsToPersist())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "dashboard layout version changed; refresh and retry");
        }
        repository.completeCommand(commandId, nextVersion);
        return new DashboardLayout(nextVersion, layout(workspaceId, userId), repository.updatedAt(workspaceId, userId));
    }

    @Override
    public DashboardLayout view(UUID workspaceId, UUID userId) {
        return new DashboardLayout(
            repository.currentVersion(workspaceId, userId),
            layout(workspaceId, userId),
            repository.updatedAt(workspaceId, userId)
        );
    }

    private NormalizedRequest normalize(List<CardPreference> cards) {
        if (cards == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all dashboard cards are required");
        }
        if (cards.size() == CATALOG.size()) {
            List<CardPreference> normalized = normalizeExact(cards, CATALOG_BY_KEY.keySet());
            return new NormalizedRequest(normalized, normalized, false);
        }
        if (cards.size() == LEGACY_CARD_COUNT) {
            List<CardPreference> legacy = normalizeExact(cards, LEGACY_CATALOG_KEYS);
            List<CardPreference> expanded = new ArrayList<>(legacy);
            CATALOG.stream()
                .filter(definition -> !LEGACY_CATALOG_KEYS.contains(definition.key()))
                .forEach(definition -> expanded.add(
                    new CardPreference(definition.key(), expanded.size(), false)
                ));
            return new NormalizedRequest(List.copyOf(expanded), legacy, true);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all dashboard cards are required");
    }

    private List<CardPreference> normalizeExact(List<CardPreference> cards, Set<String> catalogKeys) {
        if (cards.stream().anyMatch(card -> card == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown or duplicate dashboard card");
        }
        Set<String> keys = cards.stream().map(CardPreference::cardKey).collect(java.util.stream.Collectors.toSet());
        if (!keys.equals(catalogKeys) || keys.size() != cards.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown or duplicate dashboard card");
        }
        if (cards.stream().anyMatch(card -> card.position() < 0 || card.position() >= catalogKeys.size())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid dashboard card position");
        }
        if (cards.stream().map(CardPreference::position).distinct().count() != catalogKeys.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dashboard card positions must be unique");
        }
        return cards.stream().sorted(Comparator.comparingInt(CardPreference::position)).toList();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record CardDefinition(String key, String title) {
    }

    private record NormalizedRequest(
        List<CardPreference> cardsToPersist,
        List<CardPreference> cardsForHash,
        boolean legacyPayload
    ) {
    }
}
