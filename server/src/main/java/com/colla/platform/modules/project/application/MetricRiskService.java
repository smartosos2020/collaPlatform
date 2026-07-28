package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.MetricRiskModels.MAX_EVIDENCE;
import static com.colla.platform.modules.project.domain.MetricRiskModels.MAX_POLICIES;
import static com.colla.platform.modules.project.domain.MetricRiskModels.MAX_SIGNALS;
import static com.colla.platform.modules.project.domain.MetricRiskModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.MetricRiskModels.EvaluateRisksCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.EvidenceReference;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskFoundation;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicy;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicyLifecycleCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicyVersion;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignal;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignalActionCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.SaveRiskPolicyCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.SignalCandidate;
import com.colla.platform.modules.project.infrastructure.MetricRiskRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricRiskService {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{1,63}");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");
    private static final Set<String> TYPES = Set.of(
        "overdue", "due_soon", "stagnation", "blocked", "quality", "resource"
    );
    private static final Set<String> SEVERITIES = Set.of("info", "warning", "critical");
    private static final Set<String> ACTIONS = Set.of(
        "acknowledge", "close", "suppress", "reopen", "invalidate"
    );

    private final MetricRiskRepository repository;
    private final MetricRiskEvidenceResolver evidence;
    private final WorkItemRelationAccessDecisionService access;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper json;

    public MetricRiskService(
        MetricRiskRepository repository,
        MetricRiskEvidenceResolver evidence,
        WorkItemRelationAccessDecisionService access,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper json
    ) {
        this.repository = repository;
        this.evidence = evidence;
        this.access = access;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.json = json;
    }

    public RiskFoundation foundation(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        List<RiskPolicy> policies = repository.listPolicies(
            user.workspaceId(), spaceId, MAX_POLICIES + 1
        );
        List<RiskSignal> signals = repository.listSignals(
            user.workspaceId(), spaceId, MAX_SIGNALS + 1
        );
        boolean truncated = policies.size() > MAX_POLICIES || signals.size() > MAX_SIGNALS;
        return new RiskFoundation(
            SCHEMA_VERSION,
            policies.stream().limit(MAX_POLICIES).toList(),
            signals.stream().limit(MAX_SIGNALS).toList(),
            TYPES.stream().sorted().toList(),
            SEVERITIES.stream().sorted().toList(),
            List.of("open", "acknowledged", "suppressed", "closed", "invalidated"),
            truncated,
            Map.of(
                "policies", MAX_POLICIES,
                "signals", MAX_SIGNALS,
                "evidence", MAX_EVIDENCE,
                "chainDepth", 8,
                "fanOut", 50
            ),
            truncated
                ? "Directory is explicitly truncated; counts are not complete"
                : "Only currently authorized public source facts are included"
        );
    }

    @Transactional
    public RiskPolicy save(
        CurrentUser user,
        UUID spaceId,
        SaveRiskPolicyCommand command
    ) {
        access.requireManager(user, spaceId);
        validateSave(command);
        UUID policyId = command.policyId() == null
            ? stableId(user, spaceId, "policy", command.requestId())
            : command.policyId();
        List<String> types = command.signalTypes().stream().distinct().sorted().toList();
        String requestHash = hash(Map.of(
            "policyId", policyId,
            "expectedVersion", command.expectedVersion(),
            "policyKey", command.policyKey(),
            "name", command.name(),
            "description", command.description(),
            "signalTypes", types,
            "severity", command.severity(),
            "cooldownHours", command.cooldownHours()
        ));
        Optional<MetricRiskRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "save_policy", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), RiskPolicy.class);
        }
        RiskPolicy result = repository.savePolicy(
            user.workspaceId(), spaceId, user.id(), policyId,
            command.policyKey(), command.name().trim(),
            command.description() == null ? "" : command.description().trim(),
            types, command.severity(), command.cooldownHours(),
            command.expectedVersion(), command.requestId(), requestHash
        );
        emit(user, spaceId, policyId, result.version(), "policy_saved", command.requestId());
        return result;
    }

    @Transactional
    public RiskPolicyVersion publish(
        CurrentUser user,
        UUID spaceId,
        UUID policyId,
        RiskPolicyLifecycleCommand command
    ) {
        access.requireManager(user, spaceId);
        validateLifecycle(command, "publish");
        RiskPolicy policy = requirePolicy(user, spaceId, policyId);
        if (policy.version() != command.expectedVersion()) throw versionConflict();
        String requestHash = hash(command);
        Optional<MetricRiskRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "publish_policy", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), RiskPolicyVersion.class);
        }
        String definitionHash = hash(Map.of(
            "policyKey", policy.policyKey(),
            "signalTypes", policy.draftSignalTypes(),
            "severity", policy.draftSeverity(),
            "cooldownHours", policy.draftCooldownHours()
        ));
        RiskPolicyVersion result = repository.publishPolicy(
            user.workspaceId(), spaceId, user.id(), policyId,
            command.expectedVersion(), definitionHash,
            command.requestId(), requestHash
        );
        emit(user, spaceId, policyId, result.versionNumber(), "policy_published", command.requestId());
        return result;
    }

    @Transactional
    public List<RiskSignal> evaluate(
        CurrentUser user,
        UUID spaceId,
        EvaluateRisksCommand command
    ) {
        access.requireManager(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.anchor() == null) {
            throw failure("RISK_EVALUATION_INVALID", "Risk evaluation input is invalid");
        }
        String requestHash = hash(command);
        Optional<MetricRiskRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "evaluate_risks", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            try {
                return json.readerForListOf(RiskSignal.class)
                    .readValue(replay.get().responseJson());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(exception);
            }
        }
        Map<String, List<EvidenceReference>> resolved = evidence.resolve(
            user, spaceId, command.anchor()
        );
        List<SignalCandidate> candidates = new ArrayList<>();
        for (RiskPolicy policy : repository.listPolicies(
            user.workspaceId(), spaceId, MAX_POLICIES
        )) {
            RiskPolicyVersion version = policy.publishedVersion();
            if (version == null || !"active".equals(policy.status())) continue;
            for (String type : version.signalTypes()) {
                List<EvidenceReference> references = resolved
                    .getOrDefault(type, List.of())
                    .stream().filter(EvidenceReference::available)
                    .limit(MAX_EVIDENCE).toList();
                if (references.isEmpty()) continue;
                String fingerprint = hash(references);
                candidates.add(new SignalCandidate(
                    policy.id(),
                    version.versionNumber(),
                    type,
                    version.severity(),
                    hash(policy.id() + ":" + version.versionNumber() + ":" + type),
                    fingerprint,
                    references,
                    version.cooldownHours(),
                    command.anchor()
                ));
            }
        }
        List<RiskSignal> result = repository.upsertSignals(
            user.workspaceId(), spaceId, user.id(),
            candidates.stream().limit(MAX_SIGNALS).toList(),
            command.requestId(), requestHash
        );
        emit(user, spaceId, spaceId, result.size(), "evaluated", command.requestId());
        return result;
    }

    @Transactional
    public RiskSignal act(
        CurrentUser user,
        UUID spaceId,
        UUID signalId,
        RiskSignalActionCommand command
    ) {
        access.requireManager(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || !ACTIONS.contains(command.action())
            || command.reason() == null || command.reason().isBlank()
            || command.reason().length() > 1000) {
            throw failure("RISK_SIGNAL_ACTION_INVALID", "Risk signal action is invalid");
        }
        RiskSignal current = repository.findSignal(
            user.workspaceId(), spaceId, signalId
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Risk signal is not available"));
        if (current.version() != command.expectedVersion()) throw versionConflict();
        String operation = command.action() + "_signal";
        String requestHash = hash(command);
        Optional<MetricRiskRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), operation, command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), RiskSignal.class);
        }
        RiskSignal result = repository.act(
            user.workspaceId(), spaceId, user.id(), signalId,
            command.action(), command.reason().trim(), command.expectedVersion(),
            command.requestId(), requestHash
        );
        emit(user, spaceId, signalId, result.version(), command.action(), command.requestId());
        return result;
    }

    private void validateSave(SaveRiskPolicyCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || !key(command.policyKey())
            || command.name() == null || command.name().isBlank()
            || command.name().length() > 160
            || command.description() == null || command.description().length() > 2000
            || command.signalTypes() == null || command.signalTypes().isEmpty()
            || command.signalTypes().size() > TYPES.size()
            || !TYPES.containsAll(command.signalTypes())
            || new HashSet<>(command.signalTypes()).size() != command.signalTypes().size()
            || !SEVERITIES.contains(command.severity())
            || command.cooldownHours() < 1 || command.cooldownHours() > 720
            || command.expectedVersion() < 0) {
            throw failure("RISK_POLICY_INVALID", "Risk policy input is invalid");
        }
    }

    private void validateLifecycle(
        RiskPolicyLifecycleCommand command,
        String expectedAction
    ) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId())
            || command.expectedVersion() < 1
            || !expectedAction.equals(command.action())) {
            throw failure("RISK_POLICY_COMMAND_INVALID", "Risk policy command is invalid");
        }
    }

    private RiskPolicy requirePolicy(CurrentUser user, UUID spaceId, UUID policyId) {
        return repository.findPolicy(user.workspaceId(), spaceId, policyId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Risk policy is not available"));
    }

    private void emit(
        CurrentUser user,
        UUID spaceId,
        UUID objectId,
        long version,
        String change,
        String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "spaceId", spaceId,
            "objectId", objectId,
            "version", version,
            "change", change
        );
        auditLog.log(user, "project_risk." + change, "project_risk", objectId, metadata);
        String eventId = stableId(user, spaceId, "event", requestId).toString();
        outbox.append(
            user.workspaceId(), "project.risk.changed", "project_risk",
            objectId, user.id(), metadata, "project-risk:" + eventId
        );
        outbox.append(
            user.workspaceId(), "project_space.changed", "project_space",
            spaceId, user.id(), Map.of("spaceId", spaceId, "reason", "risk_" + change),
            "project-risk-space:" + eventId
        );
    }

    private RuntimeException versionConflict() {
        return failure("RISK_VERSION_CONFLICT", "Risk object changed; refresh before retrying");
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private boolean key(String value) {
        return value != null && KEY.matcher(value).matches()
            && !value.contains("sql") && !value.contains("script")
            && !value.contains("project_");
    }

    private UUID stableId(
        CurrentUser user,
        UUID spaceId,
        String kind,
        String requestId
    ) {
        return UUID.nameUUIDFromBytes(
            (user.workspaceId() + ":" + spaceId + ":" + user.id()
                + ":" + kind + ":" + requestId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hash(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void requireHash(
        MetricRiskRepository.CommandRecord record,
        String requestHash
    ) {
        if (!record.requestHash().equals(requestHash)) {
            throw failure(
                "IDEMPOTENCY_KEY_REUSED",
                "Request ID was reused with different risk input"
            );
        }
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
