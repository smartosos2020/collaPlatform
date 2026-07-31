package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.ConfigurationTemplate;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.ConfigurationTemplateVersion;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateCommandReceipt;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateInstallation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConfigurationTemplateRepository implements ConfigurationTemplateRepository {
    private static final String TEMPLATE_SELECT = """
        select id, owner_workspace_id, scope, template_key, name, description,
               visibility, status, current_version_id, aggregate_version, updated_at
          from project_work_item_configuration_templates
        """;
    private static final String VERSION_SELECT = """
        select id, template_id, owner_workspace_id, version_number, snapshot_schema_version,
               config_hash, snapshot, source_space_id, source_type_definition_id,
               source_configuration_version_id, source_catalog_version, published_by, published_at
          from project_work_item_configuration_template_versions
        """;
    private static final String INSTALLATION_SELECT = """
        select id, workspace_id, space_id, type_definition_id, template_id,
               installed_version_id, upstream_version_id, status, last_lineage_summary,
               aggregate_version, updated_at
          from project_work_item_configuration_template_installations
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcConfigurationTemplateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void importPlatformTemplate(PlatformTemplateImport value) {
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_templates (
                    id, owner_workspace_id, scope, template_key, name, description,
                    visibility, status, aggregate_version, created_at, updated_at
                )
                values (?, null, 'platform', ?, ?, ?, 'platform', 'active', 0, now(), now())
                on conflict (template_key) where scope = 'platform' do update
                    set name = excluded.name,
                        description = excluded.description,
                        updated_at = case
                            when project_work_item_configuration_templates.name <> excluded.name
                              or project_work_item_configuration_templates.description is distinct from excluded.description
                            then now() else project_work_item_configuration_templates.updated_at end,
                        aggregate_version = case
                            when project_work_item_configuration_templates.name <> excluded.name
                              or project_work_item_configuration_templates.description is distinct from excluded.description
                            then project_work_item_configuration_templates.aggregate_version + 1
                            else project_work_item_configuration_templates.aggregate_version end
                """,
            value.templateId(),
            value.templateKey(),
            value.name(),
            value.description()
        );
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_template_versions (
                    id, template_id, owner_workspace_id, version_number, snapshot_schema_version,
                    config_hash, snapshot, source_catalog_version, published_at
                )
                values (?, ?, null, 1, ?, ?, ?::jsonb, ?, now())
                on conflict (template_id, version_number) do nothing
                """,
            value.versionId(),
            value.templateId(),
            value.snapshotSchemaVersion(),
            value.configHash(),
            json(value.snapshot()),
            value.sourceCatalogVersion()
        );
        jdbcTemplate.update(
            """
                update project_work_item_configuration_templates
                   set current_version_id = ?
                 where id = ? and current_version_id is null
                """,
            value.versionId(),
            value.templateId()
        );
    }

    @Override
    public List<ConfigurationTemplate> listVisible(UUID workspaceId) {
        return jdbcTemplate.query(
            TEMPLATE_SELECT + """
                 where (scope = 'platform' or (scope = 'workspace' and owner_workspace_id = ?))
                 order by scope, name, template_key
                """,
            this::mapTemplate,
            workspaceId
        );
    }

    @Override
    public Optional<ConfigurationTemplate> findVisible(UUID workspaceId, UUID templateId) {
        return queryTemplate(
            TEMPLATE_SELECT + """
                 where id = ?
                   and (scope = 'platform' or (scope = 'workspace' and owner_workspace_id = ?))
                """,
            templateId,
            workspaceId
        );
    }

    @Override
    public Optional<ConfigurationTemplate> lockVisible(UUID workspaceId, UUID templateId) {
        return queryTemplate(
            TEMPLATE_SELECT + """
                 where id = ?
                   and (scope = 'platform' or (scope = 'workspace' and owner_workspace_id = ?))
                 for update
                """,
            templateId,
            workspaceId
        );
    }

    @Override
    public Optional<ConfigurationTemplateVersion> findVersion(
        UUID workspaceId,
        UUID templateId,
        UUID versionId
    ) {
        return queryVersion(
            VERSION_SELECT + """
                 where template_id = ? and id = ?
                   and (owner_workspace_id is null or owner_workspace_id = ?)
                """,
            templateId,
            versionId,
            workspaceId
        );
    }

    @Override
    public List<ConfigurationTemplateVersion> listVersions(UUID workspaceId, UUID templateId) {
        return jdbcTemplate.query(
            VERSION_SELECT + """
                 where template_id = ?
                   and (owner_workspace_id is null or owner_workspace_id = ?)
                 order by version_number desc
                """,
            this::mapVersion,
            templateId,
            workspaceId
        );
    }

    @Override
    public void insertWorkspaceTemplate(NewWorkspaceTemplate template, NewTemplateVersion version) {
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_templates (
                    id, owner_workspace_id, scope, template_key, name, description,
                    visibility, status, aggregate_version, created_by, created_at, updated_by, updated_at
                )
                values (?, ?, 'workspace', ?, ?, ?, 'workspace', 'active', 0, ?, now(), ?, now())
                """,
            template.id(),
            template.workspaceId(),
            template.templateKey(),
            template.name(),
            template.description(),
            template.actorId(),
            template.actorId()
        );
        insertVersion(version);
        if (switchCurrentVersion(template.id(), null, version.id(), template.actorId()) != 1) {
            throw new IllegalStateException("Configuration template current version was not initialized");
        }
    }

    @Override
    public void insertVersion(NewTemplateVersion version) {
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_template_versions (
                    id, template_id, owner_workspace_id, version_number, snapshot_schema_version,
                    config_hash, snapshot, source_space_id, source_type_definition_id,
                    source_configuration_version_id, source_catalog_version, published_by, published_at
                )
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now())
                """,
            version.id(),
            version.templateId(),
            version.ownerWorkspaceId(),
            version.versionNumber(),
            version.snapshotSchemaVersion(),
            version.configHash(),
            json(version.snapshot()),
            version.sourceSpaceId(),
            version.sourceTypeDefinitionId(),
            version.sourceConfigurationVersionId(),
            version.sourceCatalogVersion(),
            version.actorId()
        );
    }

    @Override
    public int switchCurrentVersion(UUID templateId, UUID expectedVersionId, UUID nextVersionId, UUID actorId) {
        if (expectedVersionId == null) {
            return jdbcTemplate.update(
                """
                    update project_work_item_configuration_templates
                       set current_version_id = ?, updated_by = coalesce(?, updated_by),
                           updated_at = now(), aggregate_version = aggregate_version + 1
                     where id = ? and current_version_id is null
                    """,
                nextVersionId,
                actorId,
                templateId
            );
        }
        return jdbcTemplate.update(
            """
                update project_work_item_configuration_templates
                   set current_version_id = ?, updated_by = coalesce(?, updated_by),
                       updated_at = now(), aggregate_version = aggregate_version + 1
                 where id = ? and current_version_id = ?
                """,
            nextVersionId,
            actorId,
            templateId,
            expectedVersionId
        );
    }

    @Override
    public int withdraw(UUID workspaceId, UUID templateId, UUID actorId) {
        return jdbcTemplate.update(
            """
                update project_work_item_configuration_templates
                   set status = 'withdrawn', updated_by = ?, updated_at = now(),
                       aggregate_version = aggregate_version + 1
                 where id = ? and owner_workspace_id = ? and scope = 'workspace' and status = 'active'
                """,
            actorId,
            templateId,
            workspaceId
        );
    }

    @Override
    public Optional<TemplateInstallation> findInstallation(UUID workspaceId, UUID spaceId, UUID typeId) {
        return queryInstallation(
            INSTALLATION_SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                """,
            workspaceId,
            spaceId,
            typeId
        );
    }

    @Override
    public Optional<TemplateInstallation> lockInstallation(UUID workspaceId, UUID spaceId, UUID typeId) {
        return queryInstallation(
            INSTALLATION_SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                 for update
                """,
            workspaceId,
            spaceId,
            typeId
        );
    }

    @Override
    public void install(NewInstallation value) {
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_template_installations (
                    id, workspace_id, space_id, type_definition_id, template_id,
                    installed_version_id, upstream_version_id, status, last_lineage_summary,
                    aggregate_version, installed_by, installed_at, updated_by, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 'attached', ?::jsonb, 0, ?, now(), ?, now())
                on conflict (workspace_id, space_id, type_definition_id) do update
                    set template_id = excluded.template_id,
                        installed_version_id = excluded.installed_version_id,
                        upstream_version_id = excluded.upstream_version_id,
                        status = 'attached',
                        last_lineage_summary = excluded.last_lineage_summary,
                        aggregate_version = project_work_item_configuration_template_installations.aggregate_version + 1,
                        updated_by = excluded.updated_by,
                        updated_at = now(),
                        detached_by = null,
                        detached_at = null
                """,
            value.id(),
            value.workspaceId(),
            value.spaceId(),
            value.typeDefinitionId(),
            value.templateId(),
            value.versionId(),
            value.versionId(),
            json(value.lineageSummary()),
            value.actorId(),
            value.actorId()
        );
    }

    @Override
    public int upgrade(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID installationId,
        UUID expectedUpstreamVersionId,
        long expectedAggregateVersion,
        UUID nextUpstreamVersionId,
        JsonNode lineageSummary,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_configuration_template_installations
                   set upstream_version_id = ?, last_lineage_summary = ?::jsonb,
                       aggregate_version = aggregate_version + 1,
                       updated_by = ?, updated_at = now()
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and id = ? and upstream_version_id = ? and aggregate_version = ?
                   and status = 'attached'
                """,
            nextUpstreamVersionId,
            json(lineageSummary),
            actorId,
            workspaceId,
            spaceId,
            typeId,
            installationId,
            expectedUpstreamVersionId,
            expectedAggregateVersion
        );
    }

    @Override
    public int detach(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID installationId,
        JsonNode lineageSummary,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_configuration_template_installations
                   set status = 'detached', last_lineage_summary = ?::jsonb,
                       aggregate_version = aggregate_version + 1,
                       updated_by = ?, updated_at = now(), detached_by = ?, detached_at = now()
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and id = ? and status = 'attached'
                """,
            json(lineageSummary),
            actorId,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            installationId
        );
    }

    @Override
    public void appendHistory(TemplateHistory value) {
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_template_upgrade_history (
                    id, workspace_id, space_id, type_definition_id, installation_id,
                    operation, from_version_id, to_version_id, result_hash,
                    result_summary, created_by, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
                """,
            value.id(),
            value.workspaceId(),
            value.spaceId(),
            value.typeDefinitionId(),
            value.installationId(),
            value.operation(),
            value.fromVersionId(),
            value.toVersionId(),
            value.resultHash(),
            json(value.resultSummary()),
            value.actorId()
        );
    }

    @Override
    public boolean tryStartCommand(TemplateCommandStart value) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_configuration_template_commands (
                    id, workspace_id, space_id, type_definition_id, request_id,
                    operation, request_hash, status, response_schema_version,
                    created_by, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 'pending', 1, ?, now())
                on conflict do nothing
                """,
            value.id(),
            value.workspaceId(),
            value.spaceId(),
            value.typeDefinitionId(),
            value.requestId(),
            value.operation(),
            value.requestHash(),
            value.actorId()
        ) == 1;
    }

    @Override
    public Optional<TemplateCommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, space_id, type_definition_id, request_id,
                           operation, request_hash, status, response_payload,
                           created_by, created_at, completed_at
                      from project_work_item_configuration_template_commands
                     where workspace_id = ? and space_id = ? and type_definition_id = ?
                       and operation = ? and request_id = ?
                    """,
                this::mapReceipt,
                workspaceId,
                spaceId,
                typeId,
                operation,
                requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void completeCommand(UUID commandId, JsonNode response) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_configuration_template_commands
                   set status = 'completed', response_payload = ?::jsonb, completed_at = now()
                 where id = ? and status = 'pending'
                """,
            json(response),
            commandId
        );
        if (updated != 1) {
            throw new IllegalStateException("Configuration template command could not be completed");
        }
    }

    private Optional<ConfigurationTemplate> queryTemplate(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapTemplate, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<ConfigurationTemplateVersion> queryVersion(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapVersion, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<TemplateInstallation> queryInstallation(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapInstallation, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ConfigurationTemplate mapTemplate(ResultSet rs, int row) throws SQLException {
        return new ConfigurationTemplate(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_workspace_id", UUID.class),
            rs.getString("scope"),
            rs.getString("template_key"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("visibility"),
            rs.getString("status"),
            rs.getObject("current_version_id", UUID.class),
            rs.getLong("aggregate_version"),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private ConfigurationTemplateVersion mapVersion(ResultSet rs, int row) throws SQLException {
        return new ConfigurationTemplateVersion(
            rs.getObject("id", UUID.class),
            rs.getObject("template_id", UUID.class),
            rs.getObject("owner_workspace_id", UUID.class),
            rs.getInt("version_number"),
            rs.getInt("snapshot_schema_version"),
            rs.getString("config_hash"),
            parse(rs.getString("snapshot")),
            rs.getObject("source_space_id", UUID.class),
            rs.getObject("source_type_definition_id", UUID.class),
            rs.getObject("source_configuration_version_id", UUID.class),
            rs.getString("source_catalog_version"),
            rs.getObject("published_by", UUID.class),
            rs.getTimestamp("published_at").toInstant()
        );
    }

    private TemplateInstallation mapInstallation(ResultSet rs, int row) throws SQLException {
        return new TemplateInstallation(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getObject("space_id", UUID.class),
            rs.getObject("type_definition_id", UUID.class),
            rs.getObject("template_id", UUID.class),
            rs.getObject("installed_version_id", UUID.class),
            rs.getObject("upstream_version_id", UUID.class),
            rs.getString("status"),
            parse(rs.getString("last_lineage_summary")),
            rs.getLong("aggregate_version"),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private TemplateCommandReceipt mapReceipt(ResultSet rs, int row) throws SQLException {
        Timestamp completedAt = rs.getTimestamp("completed_at");
        return new TemplateCommandReceipt(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getObject("space_id", UUID.class),
            rs.getObject("type_definition_id", UUID.class),
            rs.getString("request_id"),
            rs.getString("operation"),
            rs.getString("request_hash"),
            rs.getString("status"),
            parseNullable(rs.getString("response_payload")),
            rs.getObject("created_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            completedAt == null ? null : completedAt.toInstant()
        );
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid configuration template JSON", exception);
        }
    }

    private JsonNode parse(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid configuration template JSON", exception);
        }
    }

    private JsonNode parseNullable(String value) throws SQLException {
        return value == null ? null : parse(value);
    }
}
