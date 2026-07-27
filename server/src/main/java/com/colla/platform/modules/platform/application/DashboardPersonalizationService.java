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
    private static final Map<String, String> CATALOG = Map.ofEntries(
        Map.entry("personal.todo", "我的待办"),
        Map.entry("personal.responsible", "我负责的"),
        Map.entry("personal.participating", "我参与的"),
        Map.entry("personal.watching", "我关注的"),
        Map.entry("objects.recent", "最近访问"),
        Map.entry("objects.favorites", "收藏对象"),
        Map.entry("drafts.own", "我的草稿")
    );
    private final DashboardPersonalizationRepository repository;

    public DashboardPersonalizationService(DashboardPersonalizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DashboardCard> layout(UUID workspaceId, UUID userId) {
        Map<String, CardPreference> stored = new LinkedHashMap<>();
        repository.layout(workspaceId, userId).forEach(card -> stored.put(card.cardKey(), card));
        List<DashboardCard> result = new ArrayList<>();
        int defaultPosition = 0;
        for (var entry : CATALOG.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            CardPreference preference = stored.get(entry.getKey());
            result.add(new DashboardCard(
                entry.getKey(),
                entry.getValue(),
                preference == null ? defaultPosition : preference.position(),
                preference != null && preference.hidden(),
                true
            ));
            defaultPosition++;
        }
        return result.stream().sorted(Comparator.comparingInt(DashboardCard::position).thenComparing(DashboardCard::cardKey)).toList();
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
        List<CardPreference> normalized = normalize(cards);
        String requestHash = sha256(expectedVersion + ":" + normalized);
        var replay = repository.completedCommand(workspaceId, userId, "replace_layout", stableRequestId, requestHash);
        if (replay.isPresent()) {
            return new DashboardLayout(replay.get(), layout(workspaceId, userId), repository.updatedAt(workspaceId, userId));
        }
        UUID commandId = UUID.randomUUID();
        if (!repository.tryStartCommand(commandId, workspaceId, userId, "replace_layout", stableRequestId, requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId was reused with another payload");
        }
        long nextVersion = expectedVersion + 1;
        if (!repository.replace(workspaceId, userId, expectedVersion, nextVersion, normalized)) {
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

    private List<CardPreference> normalize(List<CardPreference> cards) {
        if (cards == null || cards.size() != CATALOG.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all dashboard cards are required");
        }
        Set<String> keys = cards.stream().map(CardPreference::cardKey).collect(java.util.stream.Collectors.toSet());
        if (!keys.equals(CATALOG.keySet()) || keys.size() != cards.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown or duplicate dashboard card");
        }
        if (cards.stream().anyMatch(card -> card.position() < 0 || card.position() >= CATALOG.size())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid dashboard card position");
        }
        if (cards.stream().map(CardPreference::position).distinct().count() != CATALOG.size()) {
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
}
