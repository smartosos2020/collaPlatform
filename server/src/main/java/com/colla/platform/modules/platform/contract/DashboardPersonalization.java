package com.colla.platform.modules.platform.contract;

import java.time.Instant;
import java.util.List;

/**
 * Public, content-free personalization contract owned by the platform module.
 */
public interface DashboardPersonalization {
    List<DashboardCard> layout(java.util.UUID workspaceId, java.util.UUID userId);

    DashboardLayout view(java.util.UUID workspaceId, java.util.UUID userId);

    DashboardLayout update(
        java.util.UUID workspaceId,
        java.util.UUID userId,
        String requestId,
        long expectedVersion,
        List<CardPreference> cards
    );

    record DashboardCard(
        String cardKey,
        String title,
        int position,
        boolean hidden,
        boolean configurable
    ) {
    }

    record CardPreference(String cardKey, int position, boolean hidden) {
    }

    record DashboardLayout(long version, List<DashboardCard> cards, Instant updatedAt) {
    }
}
