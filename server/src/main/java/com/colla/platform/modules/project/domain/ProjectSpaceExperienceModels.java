package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class ProjectSpaceExperienceModels {
    public static final int SCHEMA_VERSION = 1;

    private ProjectSpaceExperienceModels() {
    }

    public enum ExperienceMode {
        simple,
        advanced;

        public static ExperienceMode parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid project space experience mode");
            }
        }
    }

    public record ExperiencePreference(
        int schemaVersion,
        String mode,
        long version,
        Instant updatedAt
    ) {
    }

    public record ExperiencePreferenceView(
        int schemaVersion,
        String mode,
        long version,
        Instant updatedAt,
        List<String> availableModes
    ) {
    }

    public static final class ExperiencePreferenceConflictException extends RuntimeException {
        public ExperiencePreferenceConflictException() {
            super("Project space experience preference conflict");
        }
    }
}
