package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectDetailModels.MAX_DETAIL_DELIVERABLES;
import static com.colla.platform.modules.project.domain.ProjectDetailModels.MAX_DETAIL_PLANS;
import static com.colla.platform.modules.project.domain.ProjectDetailModels.MAX_DETAIL_REGISTER;
import static com.colla.platform.modules.project.domain.ProjectDetailModels.MAX_SIGNALS;
import static com.colla.platform.modules.project.domain.ProjectDetailModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectDetailModels.BlockingSummary;
import com.colla.platform.modules.project.domain.ProjectDetailModels.DetailPreference;
import com.colla.platform.modules.project.domain.ProjectDetailModels.Deviation;
import com.colla.platform.modules.project.domain.ProjectDetailModels.HealthSignal;
import com.colla.platform.modules.project.domain.ProjectDetailModels.HealthStatus;
import com.colla.platform.modules.project.domain.ProjectDetailModels.PreferenceCommand;
import com.colla.platform.modules.project.domain.ProjectDetailModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.ProjectDetailRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectDetailService {
    private static final String POLICY_VERSION = "project-health-v1";
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Set<String> SECTIONS =
        Set.of("plan", "register", "delivery", "health");

    private final ProjectDetailRepository repository;
    private final ProjectSpaceRepository spaces;
    private final ProjectPlanService plans;
    private final ProjectRegisterService register;
    private final ProjectDeliveryService deliveries;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ProjectDetailService(
        ProjectDetailRepository repository,
        ProjectSpaceRepository spaces,
        ProjectPlanService plans,
        ProjectRegisterService register,
        ProjectDeliveryService deliveries,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this(
            repository, spaces, plans, register, deliveries, auditLog, outbox,
            objectMapper, Clock.systemUTC()
        );
    }

    ProjectDetailService(
        ProjectDetailRepository repository,
        ProjectSpaceRepository spaces,
        ProjectPlanService plans,
        ProjectRegisterService register,
        ProjectDeliveryService deliveries,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.spaces = spaces;
        this.plans = plans;
        this.register = register;
        this.deliveries = deliveries;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ProjectDetail get(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        List<PlanSummary> planSummaries = plans.list(user, spaceId);
        List<ProjectPlan> planDetails = planSummaries.stream()
            .map(value -> plans.get(user, spaceId, value.id())).toList();
        List<RegisterSummary> registerEntries = register.list(user, spaceId, null);
        List<DeliverableSummary> deliverableEntries = deliveries.list(user, spaceId);
        Instant now = clock.instant();
        List<Deviation> deviations = planDetails.stream()
            .map(value -> new Deviation(
                value.plan().id(),
                value.plan().version(),
                value.progress().completionPercent(),
                value.progress().overdueMilestones(),
                value.progress().visibleMilestones()
            )).toList();

        int openIssues = count(registerEntries, "issue", Set.of("verified"));
        int highRisks = (int) registerEntries.stream()
            .filter(value -> "risk".equals(value.entryType()))
            .filter(value -> !"closed".equals(value.status()))
            .filter(value -> value.score() >= 15).count();
        int pendingChanges = count(
            registerEntries, "change", Set.of("approved", "applied", "rejected")
        );
        int pendingAcceptances = (int) deliverableEntries.stream()
            .filter(value -> !Set.of("accepted", "archived").contains(value.status()))
            .count();
        int rejectedDeliverables = (int) deliverableEntries.stream()
            .filter(value -> "rejected".equals(value.status())).count();
        BlockingSummary blocking = new BlockingSummary(
            openIssues, highRisks, pendingChanges, pendingAcceptances,
            rejectedDeliverables
        );
        boolean truncated = planSummaries.size() >= MAX_DETAIL_PLANS
            || registerEntries.size() >= MAX_DETAIL_REGISTER
            || deliverableEntries.size() >= MAX_DETAIL_DELIVERABLES;
        List<HealthSignal> signals = signals(
            planDetails, registerEntries, deliverableEntries, now
        );
        if (signals.size() > MAX_SIGNALS) {
            signals = List.copyOf(signals.subList(0, MAX_SIGNALS));
            truncated = true;
        }
        String status = truncated ? "unknown"
            : (openIssues > 0 || highRisks > 0 || rejectedDeliverables > 0)
                ? "critical" : signals.isEmpty() ? "healthy" : "attention";
        HealthStatus health = new HealthStatus(
            status, signals, truncated, POLICY_VERSION, now
        );
        DetailPreference preference = preference(user, spaceId);
        String fingerprint = hash(List.of(
            sourceFacts(planSummaries), sourceFacts(registerEntries),
            sourceFacts(deliverableEntries), POLICY_VERSION
        ));
        try {
            repository.recordProjection(
                user.workspaceId(), spaceId, user.id(), health, fingerprint
            );
        } catch (DataAccessException ignored) {
            // The projection index is disposable and never blocks a canonical response.
        }
        return new ProjectDetail(
            planSummaries, registerEntries, deliverableEntries,
            deviations, blocking, health, preference
        );
    }

    public DetailPreference preference(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        return repository.findPreference(
            user.workspaceId(), spaceId, user.id()
        ).orElseGet(() -> new DetailPreference(
            SCHEMA_VERSION, List.of("plan", "register", "delivery", "health"),
            false, 0, Instant.EPOCH
        ));
    }

    @Transactional
    public DetailPreference savePreference(
        CurrentUser user, UUID spaceId, PreferenceCommand command
    ) {
        requireVisible(user, spaceId);
        validate(command);
        String requestHash = hash(command);
        Optional<ProjectDetailRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(), command.requestId()
            );
        if (replay.isPresent()) {
            if (!requestHash.equals(replay.get().requestHash())) {
                throw failure(
                    "PROJECT_DETAIL_REQUEST_CONFLICT",
                    "Request ID was reused with different input"
                );
            }
            return read(replay.get().responseJson());
        }
        DetailPreference result = repository.savePreference(
            user.workspaceId(), spaceId, user.id(), command.requestId(),
            requestHash, command.expectedVersion(), command.visibleSections(),
            command.compact()
        );
        auditLog.log(
            user, "project_detail.preference_saved", "project_space", spaceId,
            Map.of("space_id", spaceId.toString(), "version", result.version())
        );
        outbox.append(
            user.workspaceId(), "project.detail.preference.changed",
            "project_space", spaceId, user.id(),
            Map.of("version", result.version()),
            "project-detail:preference:" + command.requestId()
        );
        return result;
    }

    private List<HealthSignal> signals(
        List<ProjectPlan> planDetails,
        List<RegisterSummary> registerEntries,
        List<DeliverableSummary> deliverableEntries,
        Instant now
    ) {
        List<HealthSignal> result = new ArrayList<>();
        for (ProjectPlan plan : planDetails) {
            if (plan.progress().overdueMilestones() > 0) {
                result.add(signal(
                    "schedule_overdue", "attention", "project_plan",
                    plan.plan().id(), plan.plan().version(),
                    "overdueMilestones > 0",
                    plan.progress().overdueMilestones() + " visible milestone(s) overdue",
                    now
                ));
            }
        }
        for (RegisterSummary entry : registerEntries) {
            if ("risk".equals(entry.entryType()) && !"closed".equals(entry.status())) {
                result.add(signal(
                    entry.score() >= 15 ? "risk_high" : "risk_open",
                    entry.score() >= 15 ? "critical" : "attention",
                    "project_register_entry", entry.id(), entry.version(),
                    "risk status != closed; high when probability*impact >= 15",
                    "Open risk score " + entry.score(), now
                ));
            } else if ("issue".equals(entry.entryType())
                && !"verified".equals(entry.status())) {
                result.add(signal(
                    "issue_open", "critical", "project_register_entry",
                    entry.id(), entry.version(), "issue status != verified",
                    "Unverified issue blocks project health", now
                ));
            } else if ("change".equals(entry.entryType())
                && !Set.of("approved", "applied", "rejected").contains(entry.status())) {
                result.add(signal(
                    "change_pending", "attention", "project_register_entry",
                    entry.id(), entry.version(), "change has no terminal decision",
                    "Change request still needs a conclusion", now
                ));
            }
        }
        for (DeliverableSummary deliverable : deliverableEntries) {
            if ("rejected".equals(deliverable.status())) {
                result.add(signal(
                    "delivery_rejected", "critical", "project_deliverable",
                    deliverable.id(), deliverable.version(),
                    "deliverable status = rejected",
                    "Rejected deliverable blocks acceptance", now
                ));
            } else if (!Set.of("accepted", "archived").contains(deliverable.status())) {
                result.add(signal(
                    "acceptance_pending", "attention", "project_deliverable",
                    deliverable.id(), deliverable.version(),
                    "deliverable status not accepted/archived",
                    "Deliverable acceptance is pending", now
                ));
            }
        }
        result.sort(java.util.Comparator
            .comparing(HealthSignal::severity)
            .thenComparing(HealthSignal::sourceType)
            .thenComparing(value -> value.sourceId().toString()));
        return result;
    }

    private HealthSignal signal(
        String code, String severity, String sourceType, UUID sourceId,
        long sourceVersion, String rule, String explanation, Instant observedAt
    ) {
        return new HealthSignal(
            code, severity, sourceType, sourceId, sourceVersion,
            rule, explanation, observedAt
        );
    }

    private int count(
        List<RegisterSummary> entries, String type, Set<String> terminal
    ) {
        return (int) entries.stream()
            .filter(value -> type.equals(value.entryType()))
            .filter(value -> !terminal.contains(value.status())).count();
    }

    private List<String> sourceFacts(List<?> values) {
        return values.stream().map(value -> {
            if (value instanceof PlanSummary plan) {
                return plan.id() + ":" + plan.version() + ":" + plan.status();
            }
            if (value instanceof RegisterSummary entry) {
                return entry.id() + ":" + entry.version() + ":" + entry.status();
            }
            DeliverableSummary delivery = (DeliverableSummary) value;
            return delivery.id() + ":" + delivery.version() + ":" + delivery.status();
        }).sorted().toList();
    }

    private void validate(PreferenceCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || command.requestId() == null
            || !REQUEST_ID.matcher(command.requestId()).matches()
            || command.expectedVersion() < 0
            || command.visibleSections() == null
            || command.visibleSections().isEmpty()
            || command.visibleSections().size() > SECTIONS.size()
            || !SECTIONS.containsAll(command.visibleSections())
            || new HashSet<>(command.visibleSections()).size()
                != command.visibleSections().size()) {
            throw failure(
                "PROJECT_DETAIL_PREFERENCE_INVALID",
                "Project detail preference is invalid"
            );
        }
    }

    private ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaces.findById(
            user.workspaceId(), spaceId, user.id()
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Project space is not available"
        ));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    private String hash(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(json(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize project detail", exception);
        }
    }

    private DetailPreference read(String value) {
        try {
            return objectMapper.readValue(value, DetailPreference.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read detail receipt", exception);
        }
    }
}
