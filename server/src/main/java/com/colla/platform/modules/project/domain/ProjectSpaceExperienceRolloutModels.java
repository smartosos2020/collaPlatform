package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public final class ProjectSpaceExperienceRolloutModels {
    public static final int SCHEMA_VERSION = 1;
    public static final String FALLBACK_CONTEXT = "canonical_project_space";

    private ProjectSpaceExperienceRolloutModels() {
    }

    public enum RolloutState {
        enabled,
        baseline,
        temporarily_disabled,
        unknown
    }

    public record TelemetryPolicy(
        int schemaVersion,
        boolean enabled,
        int sampleBasisPoints,
        int maxBatchSize
    ) {
    }

    public record RolloutView(
        int schemaVersion,
        String policyVersion,
        boolean enabled,
        RolloutState state,
        String fallbackContext,
        Instant evaluatedAt,
        int cacheMaxAgeSeconds,
        TelemetryPolicy telemetry
    ) {
    }

    public record TelemetryEventCommand(
        UUID eventId,
        String eventKind,
        String routeKey,
        String mode,
        String outcome,
        String durationBucket,
        String errorCode,
        String freshness
    ) {
    }

    public record TelemetryEvent(
        UUID eventId,
        EventKind eventKind,
        RouteKey routeKey,
        ExperienceMode mode,
        Outcome outcome,
        DurationBucket durationBucket,
        ErrorCode errorCode,
        Freshness freshness
    ) {
    }

    public enum EventKind implements WireValue {
        ENTRY("entry"),
        MODE("mode"),
        HELP("help"),
        TASK_RESULT("task_result"),
        ROUTE_ERROR("route_error"),
        RECOVERY("recovery");

        private final String value;

        EventKind(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static EventKind parse(String value) {
            return ProjectSpaceExperienceRolloutModels.parse(EventKind.class, value);
        }
    }

    public enum RouteKey implements WireValue {
        OVERVIEW("overview"),
        WORK_ITEMS("work_items"),
        MANAGEMENT("management"),
        MEMBERS("members"),
        SETTINGS("settings"),
        ADVANCED_CONFIGURATION("advanced_configuration"),
        NOTIFICATIONS("notifications"),
        UNKNOWN("unknown");

        private final String value;

        RouteKey(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static RouteKey parse(String value) {
            return ProjectSpaceExperienceRolloutModels.parse(RouteKey.class, value);
        }
    }

    public enum ExperienceMode implements WireValue {
        SIMPLE("simple"),
        ADVANCED("advanced"),
        BASELINE("baseline"),
        UNKNOWN("unknown");

        private final String value;

        ExperienceMode(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static ExperienceMode parse(String value) {
            return ProjectSpaceExperienceRolloutModels.parse(ExperienceMode.class, value);
        }
    }

    public enum Outcome implements WireValue {
        SHOWN("shown"),
        OPENED("opened"),
        CHANGED("changed"),
        SUCCEEDED("succeeded"),
        BLOCKED("blocked"),
        FAILED("failed"),
        RECOVERED("recovered"),
        UNKNOWN("unknown");

        private final String value;

        Outcome(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static Outcome parse(String value) {
            return ProjectSpaceExperienceRolloutModels.parse(Outcome.class, value);
        }
    }

    public enum DurationBucket implements WireValue {
        UNDER_5S("under_5s"),
        FIVE_TO_30S("5_to_30s"),
        THIRTY_TO_120S("30_to_120s"),
        TWO_TO_10M("2_to_10m"),
        OVER_10M("over_10m"),
        UNKNOWN("unknown");

        private final String value;

        DurationBucket(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static DurationBucket parse(String value) {
            return ProjectSpaceExperienceRolloutModels.parse(DurationBucket.class, value);
        }
    }

    public enum ErrorCode implements WireValue {
        NONE("none"),
        NOT_FOUND_OR_HIDDEN("not_found_or_hidden"),
        CAPABILITY_DENIED("capability_denied"),
        SPACE_READ_ONLY("space_read_only"),
        OFFLINE("offline"),
        TIMEOUT("timeout"),
        VERSION_CONFLICT("version_conflict"),
        SERVER_ERROR("server_error"),
        UNKNOWN("unknown");

        private final String value;

        ErrorCode(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static ErrorCode parse(String value) {
            return ProjectSpaceExperienceRolloutModels.parse(ErrorCode.class, value);
        }
    }

    public enum Freshness implements WireValue {
        FRESH("fresh"),
        STALE("stale"),
        UNKNOWN("unknown");

        private final String value;

        Freshness(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static Freshness parse(String value) {
            return ProjectSpaceExperienceRolloutModels.parse(Freshness.class, value);
        }
    }

    private interface WireValue {
        String value();
    }

    private static <T extends Enum<T> & WireValue> T parse(Class<T> type, String value) {
        return Arrays.stream(type.getEnumConstants())
            .filter(candidate -> candidate.value().equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid experience telemetry value"));
    }
}
