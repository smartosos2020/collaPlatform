package com.colla.platform.modules.project.application;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemStateFlowSideEffectRegistry {
    private static final Set<String> KEYS = Set.of(
        "activity_append",
        "field_patch",
        "notification_request"
    );

    public boolean supports(String key) {
        return KEYS.contains(key);
    }

    public Set<String> keys() {
        return KEYS;
    }
}
