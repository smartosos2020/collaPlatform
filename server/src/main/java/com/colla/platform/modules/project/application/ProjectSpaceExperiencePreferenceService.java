package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.SCHEMA_VERSION;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperienceMode;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreference;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreferenceConflictException;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreferenceView;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceExperiencePreferenceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectSpaceExperiencePreferenceService {
    private final ProjectSpaceService projectSpaces;
    private final ProjectSpaceExperiencePreferenceRepository preferences;

    public ProjectSpaceExperiencePreferenceService(
        ProjectSpaceService projectSpaces,
        ProjectSpaceExperiencePreferenceRepository preferences
    ) {
        this.projectSpaces = projectSpaces;
        this.preferences = preferences;
    }

    public ExperiencePreferenceView get(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = projectSpaces.getVisible(user, spaceId);
        return calibrated(
            space,
            preferences.find(user.workspaceId(), spaceId, user.id())
        );
    }

    @Transactional
    public ExperiencePreferenceView save(
        CurrentUser user,
        UUID spaceId,
        int schemaVersion,
        String mode,
        long expectedVersion
    ) {
        ProjectSpaceSummary space = projectSpaces.getVisible(user, spaceId);
        ExperienceMode normalized = validate(schemaVersion, mode, expectedVersion);
        if (normalized == ExperienceMode.advanced && !space.canManage()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Advanced project space mode requires current management capability"
            );
        }
        try {
            ExperiencePreference saved = preferences.save(
                user.workspaceId(),
                spaceId,
                user.id(),
                SCHEMA_VERSION,
                normalized.name(),
                expectedVersion
            );
            return calibrated(space, Optional.of(saved));
        } catch (ExperiencePreferenceConflictException exception) {
            throw conflict(exception);
        }
    }

    @Transactional
    public ExperiencePreferenceView reset(CurrentUser user, UUID spaceId, long expectedVersion) {
        ProjectSpaceSummary space = projectSpaces.getVisible(user, spaceId);
        if (expectedVersion < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected version must not be negative");
        }
        Optional<ExperiencePreference> current =
            preferences.find(user.workspaceId(), spaceId, user.id());
        if (current.isEmpty() && expectedVersion == 0) {
            return calibrated(space, Optional.empty());
        }
        try {
            preferences.reset(user.workspaceId(), spaceId, user.id(), expectedVersion);
            return calibrated(space, Optional.empty());
        } catch (ExperiencePreferenceConflictException exception) {
            throw conflict(exception);
        }
    }

    private ExperienceMode validate(int schemaVersion, String mode, long expectedVersion) {
        if (schemaVersion != SCHEMA_VERSION || expectedVersion < 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Project space experience preference contract is invalid"
            );
        }
        try {
            return ExperienceMode.parse(mode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private ExperiencePreferenceView calibrated(
        ProjectSpaceSummary space,
        Optional<ExperiencePreference> stored
    ) {
        List<String> availableModes =
            space.canManage() ? List.of("simple", "advanced") : List.of("simple");
        String effectiveMode = stored
            .map(ExperiencePreference::mode)
            .filter(mode -> !"advanced".equals(mode) || space.canManage())
            .orElse("simple");
        return stored
            .map(value -> new ExperiencePreferenceView(
                value.schemaVersion(),
                effectiveMode,
                value.version(),
                value.updatedAt(),
                availableModes
            ))
            .orElseGet(() -> new ExperiencePreferenceView(
                SCHEMA_VERSION,
                "simple",
                0,
                null,
                availableModes
            ));
    }

    private ResponseStatusException conflict(ExperiencePreferenceConflictException exception) {
        return new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Project space experience preference changed",
            exception
        );
    }
}
