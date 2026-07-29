package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioManifest;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallResult;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallStep;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioTemplate;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioTemplateVersion;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioUpgradeConflict;
import com.fasterxml.jackson.core.JsonProcessingException;
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
public class JdbcScenarioTemplateRepository implements ScenarioTemplateRepository {
    private static final String SELECT = """
        select t.id,t.scenario_key,t.name,t.description,t.status,t.updated_at,
               v.id as version_id,v.version_number,v.schema_version,v.manifest_hash,
               v.manifest::text,v.catalog_version,v.published_at
          from project_scenario_templates t
          join project_scenario_template_versions v on v.id=t.current_version_id
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcScenarioTemplateRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void importTemplate(
        UUID templateId,
        UUID versionId,
        String scenarioKey,
        String name,
        String description,
        String catalogVersion,
        String manifestHash,
        ScenarioManifest manifest
    ) {
        jdbc.update("""
            insert into project_scenario_templates(
              id,scenario_key,name,description,status,aggregate_version
            ) values (?,?,?,?,'active',1)
            on conflict (scenario_key) do update
              set name=excluded.name,description=excluded.description,
                  updated_at=case
                    when project_scenario_templates.name<>excluded.name
                      or project_scenario_templates.description<>excluded.description
                    then now() else project_scenario_templates.updated_at end
            """, templateId, scenarioKey, name, description);
        jdbc.update("""
            insert into project_scenario_template_versions(
              id,template_id,version_number,schema_version,manifest_hash,
              manifest,catalog_version,published_at
            ) values (
              ?,?,
              (select coalesce(max(version_number),0)+1
                 from project_scenario_template_versions where template_id=?),
              1,?,?::jsonb,?,now()
            )
            on conflict (template_id,manifest_hash) do nothing
            """, versionId, templateId, templateId, manifestHash, write(manifest), catalogVersion);
        for (int index = 0; index < manifest.components().size(); index++) {
            var component = manifest.components().get(index);
            jdbc.update("""
                insert into project_scenario_template_components(
                  template_version_id,component_key,component_kind,owner_contract,
                  configuration_template_key,dependency_keys,required,description,sort_order
                ) values (?,?,?,?,?,?::jsonb,?,?,?)
                on conflict (template_version_id,component_key) do nothing
                """, versionId, component.componentKey(), component.kind(),
                component.ownerContract(), component.configurationTemplateKey(),
                write(component.dependencies()), component.required(),
                component.description(), index);
        }
        jdbc.update("""
            update project_scenario_templates
               set current_version_id=?,
                   aggregate_version=case when current_version_id is distinct from ?
                     then aggregate_version+1 else aggregate_version end,
                   updated_at=case when current_version_id is distinct from ?
                     then now() else updated_at end
             where id=?
            """, versionId, versionId, versionId, templateId);
    }

    @Override
    public List<ScenarioTemplate> list(int limit) {
        return jdbc.query(
            SELECT + " where t.status='active' order by t.scenario_key limit ?",
            this::map, limit
        );
    }

    @Override
    public Optional<ScenarioTemplate> find(String scenarioKey) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                SELECT + " where t.scenario_key=? and t.status='active'",
                this::map, scenarioKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ScenarioInstallResult> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId,
        String requestHash
    ) {
        List<CommandRow> rows = jdbc.query("""
            select request_hash,response_payload::text
              from project_scenario_template_commands
             where workspace_id=? and space_id=? and actor_id=?
               and operation=? and request_id=?
            """, (rs, row) -> new CommandRow(
                rs.getString("request_hash"), rs.getString("response_payload")
            ), workspaceId, spaceId, actorId, operation, requestId);
        if (rows.isEmpty()) return Optional.empty();
        CommandRow row = rows.getFirst();
        if (!row.requestHash().equals(requestHash)) {
            throw failure(
                "SCENARIO_REQUEST_REUSE_CONFLICT",
                "Request ID was already used with different input"
            );
        }
        return Optional.of(replayed(read(row.payload(), ScenarioInstallResult.class)));
    }

    @Override
    public Optional<ScenarioInstallResult> findInstallation(
        UUID workspaceId,
        UUID spaceId,
        String scenarioKey
    ) {
        List<String> rows = jdbc.query("""
            select c.response_payload::text
              from project_scenario_template_commands c
             where c.workspace_id=? and c.space_id=?
               and c.operation in ('install','upgrade','retry','detach')
               and c.response_payload->>'scenarioKey'=?
             order by c.completed_at desc,c.id desc
             limit 1
            """, (rs, row) -> rs.getString(1), workspaceId, spaceId, scenarioKey);
        return rows.isEmpty()
            ? Optional.empty()
            : Optional.of(read(rows.getFirst(), ScenarioInstallResult.class));
    }

    @Override
    @Transactional
    public ScenarioInstallResult recordRun(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        ScenarioTemplate template,
        String operation,
        String requestId,
        String requestHash,
        String localManifestHash,
        List<ScenarioInstallStep> requestedSteps,
        List<ScenarioUpgradeConflict> conflicts
    ) {
        UUID runId = UUID.randomUUID();
        UUID installationId = installationId(workspaceId, spaceId, template.id()).orElse(null);
        String status = conflicts.stream().anyMatch(value -> !value.resolved())
            ? "attention" : "completed";
        if ("install".equals(operation) || "retry".equals(operation)) {
            if (installationId == null) {
                installationId = UUID.randomUUID();
                jdbc.update("""
                    insert into project_scenario_template_installations(
                      id,workspace_id,space_id,template_id,installed_version_id,
                      upstream_version_id,status,local_manifest_hash,aggregate_version,
                      installed_by
                    ) values (?,?,?,?,?,?,'installed',?,1,?)
                    """, installationId, workspaceId, spaceId, template.id(),
                    template.currentVersion().id(), template.currentVersion().id(),
                    localManifestHash, actorId);
            } else {
                jdbc.update("""
                    update project_scenario_template_installations
                       set installed_version_id=?,upstream_version_id=?,status='installed',
                           local_manifest_hash=?,aggregate_version=aggregate_version+1,
                           installed_by=?,updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                    """, template.currentVersion().id(), template.currentVersion().id(),
                    localManifestHash, actorId, workspaceId, spaceId, installationId);
            }
        } else if ("upgrade".equals(operation)) {
            if (installationId == null) {
                throw failure("SCENARIO_NOT_INSTALLED", "Scenario template is not installed");
            }
            if ("completed".equals(status)) {
                jdbc.update("""
                    update project_scenario_template_installations
                       set installed_version_id=?,upstream_version_id=?,status='installed',
                           local_manifest_hash=?,aggregate_version=aggregate_version+1,
                           installed_by=?,updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                    """, template.currentVersion().id(), template.currentVersion().id(),
                    localManifestHash, actorId, workspaceId, spaceId, installationId);
            } else {
                jdbc.update("""
                    update project_scenario_template_installations
                       set status='attention',updated_at=now()
                     where workspace_id=? and space_id=? and id=?
                    """, workspaceId, spaceId, installationId);
            }
        } else if ("detach".equals(operation)) {
            if (installationId == null) {
                throw failure("SCENARIO_NOT_INSTALLED", "Scenario template is not installed");
            }
            jdbc.update("""
                update project_scenario_template_installations
                   set status='detached',aggregate_version=aggregate_version+1,
                       installed_by=?,updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                """, actorId, workspaceId, spaceId, installationId);
        }
        jdbc.update("""
            insert into project_scenario_template_install_runs(
              id,workspace_id,space_id,installation_id,template_id,template_version_id,
              operation,status,manifest_hash,diagnostic_code,requested_by,
              started_at,completed_at
            ) values (?,?,?,?,?,?,?,? ,?,'',?,now(),now())
            """, runId, workspaceId, spaceId, installationId, template.id(),
            template.currentVersion().id(), operation, status,
            template.currentVersion().manifestHash(), actorId);
        List<ScenarioInstallStep> steps = requestedSteps.stream().map(step ->
            new ScenarioInstallStep(
                step.id() == null ? UUID.randomUUID() : step.id(),
                step.componentKey(), step.kind(), step.ownerContract(), step.operation(),
                step.status(), step.sourceVersion(), step.targetIdentity(),
                step.targetVersion(), step.diagnosticCode()
            )
        ).toList();
        for (int index = 0; index < steps.size(); index++) {
            ScenarioInstallStep step = steps.get(index);
            jdbc.update("""
                insert into project_scenario_template_install_steps(
                  id,run_id,component_key,step_order,owner_contract,operation,status,
                  source_version,target_identity,target_version,diagnostic_code,
                  started_at,completed_at
                ) values (?,?,?,?,?,?,?,?,?,?,?,now(),now())
                """, step.id(), runId, step.componentKey(), index,
                step.ownerContract(), step.operation(), step.status(),
                step.sourceVersion(), step.targetIdentity(), step.targetVersion(),
                step.diagnosticCode());
        }
        if ("upgrade".equals(operation) && installationId != null) {
            UUID diffId = UUID.randomUUID();
            String diffStatus = conflicts.isEmpty() ? "unchanged"
                : ("completed".equals(status) ? "applied" : "conflicted");
            jdbc.update("""
                insert into project_scenario_template_upgrade_diffs(
                  id,workspace_id,space_id,installation_id,run_id,
                  base_manifest_hash,upstream_manifest_hash,local_manifest_hash,status
                ) values (?,?,?,?,?,?,?,?,?)
                """, diffId, workspaceId, spaceId, installationId, runId,
                template.currentVersion().manifestHash(),
                template.currentVersion().manifestHash(), localManifestHash, diffStatus);
            for (ScenarioUpgradeConflict conflict : conflicts) {
                jdbc.update("""
                    insert into project_scenario_template_upgrade_conflicts(
                      id,diff_id,key_path,reason,base_hash,upstream_hash,local_hash,
                      resolution,resolved
                    ) values (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), diffId, conflict.keyPath(), conflict.reason(),
                    conflict.baseHash(), conflict.upstreamHash(), conflict.localHash(),
                    conflict.resolution(), conflict.resolved());
            }
        }
        long aggregateVersion = installationId == null ? 0L : jdbc.queryForObject("""
            select aggregate_version from project_scenario_template_installations
             where workspace_id=? and space_id=? and id=?
            """, Long.class, workspaceId, spaceId, installationId);
        Instant completedAt = Instant.now();
        ScenarioInstallResult result = new ScenarioInstallResult(
            runId, installationId, template.scenarioKey(), operation, status,
            template.currentVersion().manifestHash(),
            template.currentVersion().manifestHash(), localManifestHash,
            aggregateVersion, false, steps, conflicts, completedAt
        );
        UUID commandId = UUID.randomUUID();
        jdbc.update("""
            insert into project_scenario_template_commands(
              id,workspace_id,space_id,actor_id,operation,request_id,request_hash,
              object_id,response_payload,status,created_at,completed_at
            ) values (?,?,?,?,?,?,?,?,?::jsonb,'completed',now(),now())
            """, commandId, workspaceId, spaceId, actorId, operation, requestId,
            requestHash, installationId == null ? runId : installationId, write(result));
        return result;
    }

    private Optional<UUID> installationId(
        UUID workspaceId, UUID spaceId, UUID templateId
    ) {
        List<UUID> rows = jdbc.query("""
            select id from project_scenario_template_installations
             where workspace_id=? and space_id=? and template_id=?
            """, (rs, row) -> rs.getObject(1, UUID.class),
            workspaceId, spaceId, templateId);
        return rows.stream().findFirst();
    }

    private ScenarioInstallResult replayed(ScenarioInstallResult value) {
        return new ScenarioInstallResult(
            value.runId(), value.installationId(), value.scenarioKey(),
            value.operation(), value.status(), value.baseManifestHash(),
            value.upstreamManifestHash(), value.localManifestHash(),
            value.aggregateVersion(), true, value.steps(), value.conflicts(),
            value.completedAt()
        );
    }

    private ScenarioTemplate map(ResultSet rs, int row) throws SQLException {
        ScenarioManifest manifest = read(
            rs.getString("manifest"), ScenarioManifest.class
        );
        ScenarioTemplateVersion version = new ScenarioTemplateVersion(
            rs.getObject("version_id", UUID.class),
            rs.getInt("version_number"),
            rs.getInt("schema_version"),
            rs.getString("manifest_hash"),
            manifest,
            rs.getString("catalog_version"),
            rs.getTimestamp("published_at").toInstant()
        );
        return new ScenarioTemplate(
            rs.getObject("id", UUID.class),
            rs.getString("scenario_key"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("status"),
            version,
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("SCENARIO_TEMPLATE_INVALID", "Scenario manifest cannot be encoded");
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw failure("SCENARIO_TEMPLATE_INVALID", "Scenario manifest cannot be decoded");
        }
    }

    private record CommandRow(String requestHash, String payload) {
    }
}
