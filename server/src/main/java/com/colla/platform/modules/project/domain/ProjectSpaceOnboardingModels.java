package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ProjectSpaceOnboardingModels {
    public static final int SCHEMA_VERSION = 1;
    public static final String FLOW_VERSION = "s21-m6-v1";
    public static final int MAX_ACKNOWLEDGED_STEPS = 32;
    public static final int MAX_TELEMETRY_BATCH = 20;
    public static final Set<String> SCENARIO_KEYS =
        Set.of("development", "marketing", "human-resources", "delivery");

    private ProjectSpaceOnboardingModels() {
    }

    public enum StartingPoint {
        unselected,
        blank,
        scenario;

        public static StartingPoint parse(String value) {
            return parseEnum(StartingPoint.class, value, "Invalid onboarding starting point");
        }
    }

    public enum Acknowledgement {
        seen,
        skipped;

        public static Acknowledgement parse(String value) {
            return parseEnum(Acknowledgement.class, value, "Invalid onboarding acknowledgement");
        }
    }

    public enum CommandAction {
        select_starting_point,
        acknowledge_step,
        dismiss,
        resume,
        upgrade_flow,
        set_telemetry_opt_out,
        reset;

        public static CommandAction parse(String value) {
            return parseEnum(CommandAction.class, value, "Invalid onboarding command action");
        }
    }

    public enum TelemetryOutcome {
        shown,
        started,
        succeeded,
        skipped,
        blocked,
        failed,
        dismissed,
        reset;

        public static TelemetryOutcome parse(String value) {
            return parseEnum(TelemetryOutcome.class, value, "Invalid onboarding telemetry outcome");
        }
    }

    public enum DurationBucket {
        under_5s("under_5s"),
        between_5s_and_30s("5_to_30s"),
        between_30s_and_120s("30_to_120s"),
        between_2m_and_10m("2_to_10m"),
        over_10m("over_10m"),
        unknown("unknown");

        private final String value;

        DurationBucket(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static DurationBucket parse(String value) {
            String normalized = normalize(value);
            for (DurationBucket candidate : values()) {
                if (candidate.value.equals(normalized)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("Invalid onboarding telemetry duration bucket");
        }
    }

    public enum TelemetryErrorCode {
        none,
        capability_denied,
        space_read_only,
        offline,
        version_conflict,
        owner_api_failed,
        unknown;

        public static TelemetryErrorCode parse(String value) {
            return parseEnum(TelemetryErrorCode.class, value, "Invalid onboarding telemetry error code");
        }
    }

    public record AcknowledgedStep(String stepKey, String acknowledgement) {
    }

    public record OnboardingState(
        int schemaVersion,
        String flowVersion,
        String startingPoint,
        String scenarioKey,
        List<AcknowledgedStep> acknowledgedSteps,
        String dismissedFlowVersion,
        boolean telemetryOptOut,
        UUID lastRequestId,
        long version,
        Instant updatedAt
    ) {
        public OnboardingState {
            acknowledgedSteps = acknowledgedSteps == null ? List.of() : List.copyOf(acknowledgedSteps);
        }
    }

    public record OnboardingMutation(
        int schemaVersion,
        String flowVersion,
        String startingPoint,
        String scenarioKey,
        List<AcknowledgedStep> acknowledgedSteps,
        String dismissedFlowVersion,
        boolean telemetryOptOut,
        UUID requestId
    ) {
        public OnboardingMutation {
            acknowledgedSteps = acknowledgedSteps == null ? List.of() : List.copyOf(acknowledgedSteps);
        }
    }

    public record OnboardingCommand(
        UUID requestId,
        int schemaVersion,
        String flowVersion,
        long expectedVersion,
        String action,
        String startingPoint,
        String scenarioKey,
        String stepKey,
        String acknowledgement,
        Boolean telemetryOptOut
    ) {
    }

    public record ChecklistStep(
        String stepKey,
        String labelKey,
        String helpKey,
        String path,
        List<String> dependencies,
        String ownerContract,
        String status
    ) {
        public ChecklistStep {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }
    }

    public record StartingPointView(String kind, String scenarioKey) {
    }

    public record OnboardingView(
        int schemaVersion,
        String flowVersion,
        String currentFlowVersion,
        long version,
        Instant updatedAt,
        boolean migrationRequired,
        StartingPointView startingPoint,
        List<AcknowledgedStep> acknowledgedSteps,
        boolean dismissed,
        boolean telemetryOptOut,
        String selectionEffect,
        boolean installationRequested,
        boolean publicationRequested,
        String track,
        boolean readOnly,
        List<ChecklistStep> checklist
    ) {
        public OnboardingView {
            acknowledgedSteps = acknowledgedSteps == null ? List.of() : List.copyOf(acknowledgedSteps);
            checklist = checklist == null ? List.of() : List.copyOf(checklist);
        }
    }

    public record TelemetryEvent(
        UUID eventId,
        String flowVersion,
        String stepKey,
        String outcome,
        String durationBucket,
        String errorCode
    ) {
    }

    public static final class OnboardingVersionConflictException extends RuntimeException {
        public OnboardingVersionConflictException() {
            super("Project space onboarding state changed");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static <T extends Enum<T>> T parseEnum(
        Class<T> type,
        String value,
        String message
    ) {
        try {
            return Enum.valueOf(type, normalize(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(message);
        }
    }
}
