package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.MetricRiskModels.EvidenceReference;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicy;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicyVersion;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignal;
import com.colla.platform.modules.project.domain.MetricRiskModels.SignalCandidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMetricRiskRepository implements MetricRiskRepository {
    private static final String POLICY_SELECT = """
        select p.id,p.policy_key,p.name,p.description,p.status,
               p.draft_signal_types::text,p.draft_severity,p.draft_cooldown_hours,
               p.row_version,p.updated_at,
               v.id as version_id,v.version_number,v.definition_hash,
               v.signal_types::text as version_signal_types,v.severity as version_severity,
               v.cooldown_hours as version_cooldown_hours,v.published_at,v.published_by
          from project_risk_policies p
          left join project_risk_policy_versions v
            on v.workspace_id=p.workspace_id and v.space_id=p.space_id
           and v.id=p.current_version_id
        """;
    private static final String SIGNAL_SELECT = """
        select id,policy_id,policy_version,signal_type,severity,state,dedupe_key,
               evidence_fingerprint,evidence_refs::text,row_version,
               acknowledged_by,acknowledged_at,closed_by,closed_at,
               resolution_reason,observed_at,updated_at
          from project_risk_signals
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcMetricRiskRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<RiskPolicy> listPolicies(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(POLICY_SELECT + """
             where p.workspace_id=? and p.space_id=? and p.status <> 'archived'
             order by p.updated_at desc,p.id
             limit ?
            """, this::mapPolicy, workspaceId, spaceId, limit);
    }

    @Override
    public Optional<RiskPolicy> findPolicy(
        UUID workspaceId,
        UUID spaceId,
        UUID policyId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(POLICY_SELECT + """
                 where p.workspace_id=? and p.space_id=? and p.id=?
                """, this::mapPolicy, workspaceId, spaceId, policyId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<RiskSignal> listSignals(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(SIGNAL_SELECT + """
             where workspace_id=? and space_id=?
               and state <> 'invalidated'
             order by
               case severity when 'critical' then 0 when 'warning' then 1 else 2 end,
               observed_at desc,id
             limit ?
            """, this::mapSignal, workspaceId, spaceId, limit);
    }

    @Override
    public Optional<RiskSignal> findSignal(
        UUID workspaceId,
        UUID spaceId,
        UUID signalId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(SIGNAL_SELECT + """
                 where workspace_id=? and space_id=? and id=?
                """, this::mapSignal, workspaceId, spaceId, signalId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                select request_hash,response_payload::text
                  from project_risk_commands
                 where workspace_id=? and space_id=? and actor_id=?
                   and operation=? and request_id=?
                """, (result, row) -> new CommandRecord(
                    result.getString("request_hash"),
                    result.getString("response_payload")
                ), workspaceId, spaceId, actorId, operation, requestId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public RiskPolicy savePolicy(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID policyId,
        String policyKey,
        String name,
        String description,
        List<String> signalTypes,
        String severity,
        int cooldownHours,
        long expectedVersion,
        String requestId,
        String requestHash
    ) {
        int changed;
        if (expectedVersion == 0) {
            changed = jdbc.update("""
                insert into project_risk_policies(
                  id,workspace_id,space_id,policy_key,name,description,status,
                  draft_signal_types,draft_severity,draft_cooldown_hours,row_version,
                  created_by,updated_by
                ) values (?,?,?,?,?,?,'draft',?::jsonb,?,?,1,?,?)
                on conflict do nothing
                """, policyId, workspaceId, spaceId, policyKey, name, description,
                write(signalTypes), severity, cooldownHours, actorId, actorId);
        } else {
            changed = jdbc.update("""
                update project_risk_policies
                   set name=?,description=?,draft_signal_types=?::jsonb,
                       draft_severity=?,draft_cooldown_hours=?,
                       status=case when status='active' then 'draft' else status end,
                       row_version=row_version+1,updated_by=?,updated_at=now()
                 where workspace_id=? and space_id=? and id=? and row_version=?
                   and status <> 'archived'
                """, name, description, write(signalTypes), severity, cooldownHours,
                actorId, workspaceId, spaceId, policyId, expectedVersion);
        }
        if (changed != 1) throw versionConflict();
        RiskPolicy result = findPolicy(workspaceId, spaceId, policyId).orElseThrow();
        storeCommand(
            workspaceId, spaceId, actorId, "save_policy", requestId,
            requestHash, "policy", policyId, result
        );
        return result;
    }

    @Override
    @Transactional
    public RiskPolicyVersion publishPolicy(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID policyId,
        long expectedVersion,
        String definitionHash,
        String requestId,
        String requestHash
    ) {
        RiskPolicy current = lockPolicy(workspaceId, spaceId, policyId);
        if (current.version() != expectedVersion) throw versionConflict();
        int number = jdbc.queryForObject("""
            select coalesce(max(version_number),0)+1
              from project_risk_policy_versions
             where workspace_id=? and space_id=? and policy_id=?
            """, Integer.class, workspaceId, spaceId, policyId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
            insert into project_risk_policy_versions(
              id,workspace_id,space_id,policy_id,version_number,schema_version,
              definition_hash,signal_types,severity,cooldown_hours,published_by
            ) values (?,?,?,?,?,1,?,?::jsonb,?,?,?)
            """, versionId, workspaceId, spaceId, policyId, number,
            definitionHash, write(current.draftSignalTypes()),
            current.draftSeverity(), current.draftCooldownHours(), actorId);
        int changed = jdbc.update("""
            update project_risk_policies
               set status='active',current_version_id=?,row_version=row_version+1,
                   updated_by=?,updated_at=now()
             where workspace_id=? and space_id=? and id=? and row_version=?
            """, versionId, actorId, workspaceId, spaceId, policyId, expectedVersion);
        if (changed != 1) throw versionConflict();
        RiskPolicyVersion result = findPolicy(workspaceId, spaceId, policyId)
            .orElseThrow().publishedVersion();
        storeCommand(
            workspaceId, spaceId, actorId, "publish_policy", requestId,
            requestHash, "policy", policyId, result
        );
        return result;
    }

    @Override
    @Transactional
    public List<RiskSignal> upsertSignals(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        List<SignalCandidate> candidates,
        String requestId,
        String requestHash
    ) {
        for (SignalCandidate candidate : candidates) {
            jdbc.update("""
                insert into project_risk_signals(
                  id,workspace_id,space_id,policy_id,policy_version,signal_type,
                  severity,state,dedupe_key,evidence_fingerprint,evidence_refs,
                  cooldown_until,row_version,observed_at,updated_at
                ) values (?,?,?,?,?,?,?,'open',?,?,?::jsonb,
                          ?::timestamptz + make_interval(hours => ?),1,?,?)
                on conflict (workspace_id,space_id,dedupe_key) do update
                  set evidence_fingerprint=excluded.evidence_fingerprint,
                      evidence_refs=excluded.evidence_refs,
                      severity=excluded.severity,
                      observed_at=excluded.observed_at,
                      updated_at=excluded.updated_at,
                      row_version=project_risk_signals.row_version+1,
                      state=case
                        when project_risk_signals.evidence_fingerprint <> excluded.evidence_fingerprint
                         and project_risk_signals.state in ('closed','invalidated')
                        then 'open'
                        else project_risk_signals.state
                      end,
                      acknowledged_by=case
                        when project_risk_signals.evidence_fingerprint <> excluded.evidence_fingerprint
                        then null else project_risk_signals.acknowledged_by end,
                      acknowledged_at=case
                        when project_risk_signals.evidence_fingerprint <> excluded.evidence_fingerprint
                        then null else project_risk_signals.acknowledged_at end,
                      closed_by=case
                        when project_risk_signals.evidence_fingerprint <> excluded.evidence_fingerprint
                        then null else project_risk_signals.closed_by end,
                      closed_at=case
                        when project_risk_signals.evidence_fingerprint <> excluded.evidence_fingerprint
                        then null else project_risk_signals.closed_at end,
                      resolution_reason=case
                        when project_risk_signals.evidence_fingerprint <> excluded.evidence_fingerprint
                        then '' else project_risk_signals.resolution_reason end
                """, UUID.randomUUID(), workspaceId, spaceId, candidate.policyId(),
                candidate.policyVersion(), candidate.signalType(), candidate.severity(),
                candidate.dedupeKey(), candidate.evidenceFingerprint(),
                write(candidate.evidence()), java.sql.Timestamp.from(candidate.observedAt()),
                candidate.cooldownHours(),
                java.sql.Timestamp.from(candidate.observedAt()),
                java.sql.Timestamp.from(candidate.observedAt()));
        }
        List<RiskSignal> result = listSignals(workspaceId, spaceId, 200);
        storeCommand(
            workspaceId, spaceId, actorId, "evaluate_risks", requestId,
            requestHash, "space", spaceId, result
        );
        return result;
    }

    @Override
    @Transactional
    public RiskSignal act(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        UUID signalId,
        String action,
        String reason,
        long expectedVersion,
        String requestId,
        String requestHash
    ) {
        String state = switch (action) {
            case "acknowledge" -> "acknowledged";
            case "close" -> "closed";
            case "suppress" -> "suppressed";
            case "reopen" -> "open";
            case "invalidate" -> "invalidated";
            default -> throw failure("RISK_SIGNAL_ACTION_INVALID", "Risk signal action is invalid");
        };
        int changed = jdbc.update("""
            update project_risk_signals
               set state=?,row_version=row_version+1,resolution_reason=?,updated_at=now(),
                   acknowledged_by=case when ?='acknowledged' then ? when ?='open' then null else acknowledged_by end,
                   acknowledged_at=case when ?='acknowledged' then now() when ?='open' then null else acknowledged_at end,
                   closed_by=case when ? in ('closed','invalidated') then ? when ?='open' then null else closed_by end,
                   closed_at=case when ? in ('closed','invalidated') then now() when ?='open' then null else closed_at end
             where workspace_id=? and space_id=? and id=? and row_version=?
            """, state, reason,
            state, actorId, state,
            state, state,
            state, actorId, state,
            state, state,
            workspaceId, spaceId, signalId, expectedVersion);
        if (changed != 1) throw versionConflict();
        RiskSignal result = findSignal(workspaceId, spaceId, signalId).orElseThrow();
        jdbc.update("""
            insert into project_risk_signal_actions(
              id,workspace_id,space_id,signal_id,signal_version,action,reason,
              actor_id,request_id,request_hash,evidence_fingerprint
            ) values (?,?,?,?,?,?,?,?,?,?,?)
            """, UUID.randomUUID(), workspaceId, spaceId, signalId, result.version(),
            action, reason, actorId, requestId, requestHash, result.evidenceFingerprint());
        storeCommand(
            workspaceId, spaceId, actorId, action + "_signal", requestId,
            requestHash, "signal", signalId, result
        );
        return result;
    }

    private RiskPolicy lockPolicy(UUID workspaceId, UUID spaceId, UUID policyId) {
        try {
            return jdbc.queryForObject(POLICY_SELECT + """
                 where p.workspace_id=? and p.space_id=? and p.id=?
                 for update of p
                """, this::mapPolicy, workspaceId, spaceId, policyId);
        } catch (EmptyResultDataAccessException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Risk policy is not available");
        }
    }

    private void storeCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId,
        String requestHash,
        String objectType,
        UUID objectId,
        Object result
    ) {
        jdbc.update("""
            insert into project_risk_commands(
              id,workspace_id,space_id,actor_id,operation,request_id,
              request_hash,object_type,object_id,response_payload,status
            ) values (?,?,?,?,?,?,?,?,?,?::jsonb,'completed')
            """, UUID.randomUUID(), workspaceId, spaceId, actorId, operation,
            requestId, requestHash, objectType, objectId, write(result));
    }

    private RiskPolicy mapPolicy(ResultSet result, int row) throws SQLException {
        RiskPolicyVersion version = result.getObject("version_id") == null ? null
            : new RiskPolicyVersion(
                result.getObject("version_id", UUID.class),
                result.getObject("id", UUID.class),
                result.getInt("version_number"),
                result.getString("definition_hash"),
                readStrings(result.getString("version_signal_types")),
                result.getString("version_severity"),
                result.getInt("version_cooldown_hours"),
                result.getTimestamp("published_at").toInstant(),
                result.getObject("published_by", UUID.class)
            );
        return new RiskPolicy(
            result.getObject("id", UUID.class),
            result.getString("policy_key"),
            result.getString("name"),
            result.getString("description"),
            result.getString("status"),
            readStrings(result.getString("draft_signal_types")),
            result.getString("draft_severity"),
            result.getInt("draft_cooldown_hours"),
            result.getLong("row_version"),
            version,
            result.getTimestamp("updated_at").toInstant()
        );
    }

    private RiskSignal mapSignal(ResultSet result, int row) throws SQLException {
        return new RiskSignal(
            result.getObject("id", UUID.class),
            result.getObject("policy_id", UUID.class),
            result.getInt("policy_version"),
            result.getString("signal_type"),
            result.getString("severity"),
            result.getString("state"),
            result.getString("dedupe_key"),
            result.getString("evidence_fingerprint"),
            readEvidence(result.getString("evidence_refs")),
            result.getLong("row_version"),
            result.getObject("acknowledged_by", UUID.class),
            instant(result, "acknowledged_at"),
            result.getObject("closed_by", UUID.class),
            instant(result, "closed_at"),
            result.getString("resolution_reason"),
            result.getTimestamp("observed_at").toInstant(),
            result.getTimestamp("updated_at").toInstant()
        );
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column) == null
            ? null : result.getTimestamp(column).toInstant();
    }

    private RuntimeException versionConflict() {
        return failure("RISK_VERSION_CONFLICT", "Risk object changed; refresh before retrying");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> readStrings(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<EvidenceReference> readEvidence(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
