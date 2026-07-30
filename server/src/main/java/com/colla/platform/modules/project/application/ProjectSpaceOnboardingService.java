package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.FLOW_VERSION;
import static com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.MAX_ACKNOWLEDGED_STEPS;
import static com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.MAX_TELEMETRY_BATCH;
import static com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.SCENARIO_KEYS;
import static com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.SCHEMA_VERSION;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.AcknowledgedStep;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.Acknowledgement;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.CommandAction;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.DurationBucket;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingMutation;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingState;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingVersionConflictException;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingView;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.StartingPoint;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.StartingPointView;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryErrorCode;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryEvent;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryOutcome;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceOnboardingRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectSpaceOnboardingService {
    private final ProjectSpaceService projectSpaces;
    private final ProjectSpaceOnboardingRepository repository;
    private final ProjectSpaceOnboardingCatalog catalog;

    public ProjectSpaceOnboardingService(
        ProjectSpaceService projectSpaces,
        ProjectSpaceOnboardingRepository repository,
        ProjectSpaceOnboardingCatalog catalog
    ) {
        this.projectSpaces = projectSpaces;
        this.repository = repository;
        this.catalog = catalog;
    }

    public OnboardingView get(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = memberSpace(user, spaceId);
        return view(space, current(user, spaceId));
    }

    @Transactional
    public OnboardingView command(
        CurrentUser user,
        UUID spaceId,
        OnboardingCommand command
    ) {
        validateEnvelope(command);
        ProjectSpaceSummary space = memberSpace(user, spaceId);
        OnboardingState current = current(user, spaceId);
        if (command.requestId().equals(current.lastRequestId())) {
            return view(space, current);
        }
        if (current.version() != command.expectedVersion()) {
            throw conflict(new OnboardingVersionConflictException());
        }

        CommandAction action = parse(() -> CommandAction.parse(command.action()));
        if (
            !FLOW_VERSION.equals(current.flowVersion())
            && action != CommandAction.upgrade_flow
            && action != CommandAction.set_telemetry_opt_out
            && action != CommandAction.reset
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Project space onboarding flow must be upgraded before it can change"
            );
        }

        OnboardingMutation mutation = mutation(space, current, command, action);
        try {
            OnboardingState saved = repository.save(
                user.workspaceId(),
                spaceId,
                user.id(),
                mutation,
                command.expectedVersion()
            );
            return view(space, saved);
        } catch (OnboardingVersionConflictException exception) {
            throw conflict(exception);
        }
    }

    @Transactional
    public int recordTelemetry(
        CurrentUser user,
        UUID spaceId,
        List<TelemetryEvent> events
    ) {
        ProjectSpaceSummary space = memberSpace(user, spaceId);
        if (events == null || events.isEmpty() || events.size() > MAX_TELEMETRY_BATCH) {
            throw badRequest("Onboarding telemetry batch must contain between 1 and 20 events");
        }
        OnboardingState state = current(user, spaceId);
        if (state.telemetryOptOut()) {
            return 0;
        }
        Set<String> stepKeys = catalog.stepKeys(space, state);
        List<TelemetryEvent> normalized = new ArrayList<>(events.size());
        for (TelemetryEvent event : events) {
            if (event == null || event.eventId() == null || !FLOW_VERSION.equals(event.flowVersion())) {
                throw badRequest("Onboarding telemetry contract is invalid");
            }
            String stepKey = normalizedStepKey(event.stepKey());
            if (!stepKeys.contains(stepKey)) {
                throw badRequest("Unknown onboarding checklist step");
            }
            String outcome = parse(() -> TelemetryOutcome.parse(event.outcome())).name();
            String duration = parse(() -> DurationBucket.parse(event.durationBucket())).value();
            String errorCode = parse(() -> TelemetryErrorCode.parse(event.errorCode())).name();
            normalized.add(new TelemetryEvent(
                event.eventId(), FLOW_VERSION, stepKey, outcome, duration, errorCode
            ));
        }
        int inserted = repository.appendTelemetry(user.workspaceId(), spaceId, normalized);
        repository.purgeExpiredTelemetry(100);
        return inserted;
    }

    private OnboardingMutation mutation(
        ProjectSpaceSummary space,
        OnboardingState current,
        OnboardingCommand command,
        CommandAction action
    ) {
        String flowVersion = current.flowVersion();
        String startingPoint = current.startingPoint();
        String scenarioKey = current.scenarioKey();
        List<AcknowledgedStep> acknowledgedSteps = current.acknowledgedSteps();
        String dismissedFlowVersion = current.dismissedFlowVersion();
        boolean telemetryOptOut = current.telemetryOptOut();

        switch (action) {
            case select_starting_point -> {
                StartingPoint selected = parse(() -> StartingPoint.parse(command.startingPoint()));
                if (selected == StartingPoint.unselected) {
                    throw badRequest("Select blank or scenario as the onboarding starting point");
                }
                String normalizedScenario = normalizeScenarioKey(command.scenarioKey());
                if (selected == StartingPoint.scenario && !SCENARIO_KEYS.contains(normalizedScenario)) {
                    throw badRequest("Unknown onboarding scenario");
                }
                if (selected == StartingPoint.blank && normalizedScenario != null) {
                    throw badRequest("Blank onboarding cannot carry a scenario key");
                }
                startingPoint = selected.name();
                scenarioKey = selected == StartingPoint.scenario ? normalizedScenario : null;
            }
            case acknowledge_step -> {
                String stepKey = normalizedStepKey(command.stepKey());
                if (!catalog.stepKeys(space, current).contains(stepKey)) {
                    throw badRequest("Unknown onboarding checklist step");
                }
                Acknowledgement acknowledgement =
                    parse(() -> Acknowledgement.parse(command.acknowledgement()));
                Map<String, AcknowledgedStep> byKey = new LinkedHashMap<>();
                for (AcknowledgedStep value : acknowledgedSteps) {
                    byKey.put(value.stepKey(), value);
                }
                byKey.put(stepKey, new AcknowledgedStep(stepKey, acknowledgement.name()));
                if (byKey.size() > MAX_ACKNOWLEDGED_STEPS) {
                    throw badRequest("Too many onboarding checklist acknowledgements");
                }
                acknowledgedSteps = List.copyOf(byKey.values());
            }
            case dismiss -> dismissedFlowVersion = FLOW_VERSION;
            case resume -> dismissedFlowVersion = null;
            case upgrade_flow -> {
                flowVersion = FLOW_VERSION;
                OnboardingState upgraded = new OnboardingState(
                    SCHEMA_VERSION,
                    FLOW_VERSION,
                    validStartingPoint(current.startingPoint(), current.scenarioKey()),
                    validScenarioKey(current.startingPoint(), current.scenarioKey()),
                    List.of(),
                    null,
                    telemetryOptOut,
                    current.lastRequestId(),
                    current.version(),
                    current.updatedAt()
                );
                Set<String> recognized = catalog.stepKeys(space, upgraded);
                acknowledgedSteps = current.acknowledgedSteps().stream()
                    .filter(value -> recognized.contains(value.stepKey()))
                    .limit(MAX_ACKNOWLEDGED_STEPS)
                    .toList();
                startingPoint = upgraded.startingPoint();
                scenarioKey = upgraded.scenarioKey();
                dismissedFlowVersion = null;
            }
            case set_telemetry_opt_out -> {
                if (command.telemetryOptOut() == null) {
                    throw badRequest("Telemetry opt-out value is required");
                }
                telemetryOptOut = command.telemetryOptOut();
            }
            case reset -> {
                flowVersion = FLOW_VERSION;
                startingPoint = StartingPoint.unselected.name();
                scenarioKey = null;
                acknowledgedSteps = List.of();
                dismissedFlowVersion = null;
            }
        }

        return new OnboardingMutation(
            SCHEMA_VERSION,
            flowVersion,
            startingPoint,
            scenarioKey,
            acknowledgedSteps,
            dismissedFlowVersion,
            telemetryOptOut,
            command.requestId()
        );
    }

    private void validateEnvelope(OnboardingCommand command) {
        if (
            command == null
            || command.requestId() == null
            || command.schemaVersion() != SCHEMA_VERSION
            || !FLOW_VERSION.equals(command.flowVersion())
            || command.expectedVersion() < 0
        ) {
            throw badRequest("Project space onboarding command contract is invalid");
        }
    }

    private ProjectSpaceSummary memberSpace(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = projectSpaces.getVisible(user, spaceId);
        if (!space.isMember()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project space not found");
        }
        return space;
    }

    private OnboardingState current(CurrentUser user, UUID spaceId) {
        return repository.find(user.workspaceId(), spaceId, user.id()).orElseGet(() ->
            new OnboardingState(
                SCHEMA_VERSION,
                FLOW_VERSION,
                StartingPoint.unselected.name(),
                null,
                List.of(),
                null,
                false,
                null,
                0,
                null
            )
        );
    }

    private OnboardingView view(ProjectSpaceSummary space, OnboardingState state) {
        return new OnboardingView(
            state.schemaVersion(),
            state.flowVersion(),
            FLOW_VERSION,
            state.version(),
            state.updatedAt(),
            !FLOW_VERSION.equals(state.flowVersion()),
            new StartingPointView(state.startingPoint(), state.scenarioKey()),
            state.acknowledgedSteps(),
            FLOW_VERSION.equals(state.dismissedFlowVersion()),
            state.telemetryOptOut(),
            "experience_only",
            false,
            false,
            catalog.track(space),
            !"active".equals(space.status()),
            catalog.checklist(space, state)
        );
    }

    private String validStartingPoint(String value, String scenarioKey) {
        try {
            StartingPoint startingPoint = StartingPoint.parse(value);
            if (
                startingPoint == StartingPoint.scenario
                && !SCENARIO_KEYS.contains(validScenarioKey(value, scenarioKey))
            ) {
                return StartingPoint.unselected.name();
            }
            return startingPoint.name();
        } catch (IllegalArgumentException exception) {
            return StartingPoint.unselected.name();
        }
    }

    private String validScenarioKey(String startingPoint, String scenarioKey) {
        if (!StartingPoint.scenario.name().equals(startingPoint)) {
            return null;
        }
        String normalized = normalizeScenarioKey(scenarioKey);
        return SCENARIO_KEYS.contains(normalized) ? normalized : null;
    }

    private String normalizeScenarioKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizedStepKey(String value) {
        String normalized = value == null
            ? ""
            : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) {
            throw badRequest("Invalid onboarding checklist step");
        }
        return normalized;
    }

    private <T> T parse(Parser<T> parser) {
        try {
            return parser.parse();
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(OnboardingVersionConflictException exception) {
        return new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Project space onboarding state changed",
            exception
        );
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse();
    }
}
