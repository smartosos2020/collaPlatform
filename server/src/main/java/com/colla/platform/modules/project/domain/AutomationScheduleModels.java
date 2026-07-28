package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class AutomationScheduleModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CATCH_UP = 20;
    public static final int MAX_CANDIDATES = 200;
    private AutomationScheduleModels() {}

    public record ScheduleTrigger(
        UUID id, UUID ruleId, int ruleVersion, String triggerKind,
        String timezone, String expression, String missedPolicy,
        int cooldownSeconds, String status, int version,
        Instant cursorAt, Instant nextFireAt, long fencingToken
    ) {
        public ScheduleTrigger {
            ZoneId.of(timezone);
        }
    }

    public record ScheduleDiagnostic(
        int schemaVersion, List<ScheduleTrigger> schedules, boolean truncated,
        String clockSource, int maxCatchUp, int maxCandidates
    ) {}

    public static Instant nextFixedTime(Instant after, String timezone, int hour, int minute) {
        var zone = ZoneId.of(timezone);
        var local = after.atZone(zone).toLocalDate().atTime(hour, minute);
        var candidate = local.atZone(zone).toInstant();
        if (!candidate.isAfter(after)) candidate = local.plusDays(1).atZone(zone).toInstant();
        return candidate;
    }
}
