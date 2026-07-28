package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand;
import com.colla.platform.modules.project.contract.CrossSpaceRelationCommand.CanonicalRelationReference;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Cardinality;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationDefinitionBinding;
import com.colla.platform.modules.project.runtime.WorkItemRelationRuntimeAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcCrossSpaceRelationCommand implements CrossSpaceRelationCommand {
    private static final int MAX_GRAPH_DEPTH = 64;
    private final JdbcTemplate jdbc;
    private final WorkItemRepository workItems;
    private final WorkItemRelationRuntimeAdapter runtime;

    public JdbcCrossSpaceRelationCommand(
        JdbcTemplate jdbc,
        WorkItemRepository workItems,
        WorkItemRelationRuntimeAdapter runtime
    ) {
        this.jdbc = jdbc;
        this.workItems = workItems;
        this.runtime = runtime;
    }

    @Override
    public CanonicalRelationReference create(CreateCommand command) {
        acquireLock(command);
        WorkItem source = lock(
            command.workspaceId(), command.sourceSpaceId(), command.sourceWorkItemId()
        );
        WorkItem target = lock(
            command.workspaceId(), command.targetSpaceId(), command.targetWorkItemId()
        );
        requireVersion(source, command.expectedSourceVersion());
        requireVersion(target, command.expectedTargetVersion());
        if (!"active".equals(source.status()) || !"active".equals(target.status())) {
            throw failure(
                "CROSS_SPACE_RELATION_ENDPOINT_NOT_ACTIVE",
                "Both cross-space relation endpoints must be active"
            );
        }
        requireBinding(command, source, target);
        validateAvailability(command);
        UUID relationId = UUID.randomUUID();
        try {
            jdbc.update("""
                insert into project_work_item_cross_space_relations(
                    id,workspace_id,source_space_id,source_work_item_id,
                    target_space_id,target_work_item_id,relation_key,direction,
                    source_definition_type_id,source_definition_version_id,source_definition_hash,
                    target_definition_type_id,target_definition_version_id,target_definition_hash,
                    source_policy_id,source_policy_version,status,version,
                    created_by,updated_by
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'active',0,?,?)
                """,
                relationId, command.workspaceId(),
                command.sourceSpaceId(), command.sourceWorkItemId(),
                command.targetSpaceId(), command.targetWorkItemId(),
                command.relationKey(), command.direction(),
                command.sourceDefinitionTypeId(), command.sourceDefinitionVersionId(),
                command.sourceDefinitionHash(),
                command.targetDefinitionTypeId(), command.targetDefinitionVersionId(),
                command.targetDefinitionHash(),
                command.policyId(), command.policyVersion(),
                command.actorId(), command.actorId()
            );
        } catch (DataIntegrityViolationException exception) {
            throw failure(
                "CROSS_SPACE_RELATION_EDGE_CONFLICT",
                "An equivalent active cross-space relation already exists",
                exception
            );
        }
        appendHistory(command.workspaceId(), relationId, 0, "created", command.actorId(), null);
        return find(command.workspaceId(), relationId).orElseThrow();
    }

    @Override
    public Optional<CanonicalRelationReference> find(UUID workspaceId, UUID relationId) {
        return jdbc.query("""
            select id,source_space_id,source_work_item_id,target_space_id,target_work_item_id,
                   relation_key,direction,status,version,source_policy_id,source_policy_version,
                   updated_at
              from project_work_item_cross_space_relations
             where workspace_id=? and id=?
            """, this::map, workspaceId, relationId).stream().findFirst();
    }

    @Override
    public CanonicalRelationReference withdraw(WithdrawCommand command) {
        int updated = jdbc.update("""
            update project_work_item_cross_space_relations
               set status='withdrawn',version=version+1,updated_by=?,updated_at=now(),
                   withdrawn_by=?,withdrawn_at=now(),withdrawal_reason_hash=?
             where workspace_id=? and id=? and status='active' and version=?
            """,
            command.actorId(), command.actorId(), command.reasonHash(),
            command.workspaceId(), command.relationId(), command.expectedVersion()
        );
        if (updated != 1) {
            throw failure(
                "CROSS_SPACE_RELATION_VERSION_CONFLICT",
                "Cross-space relation changed or is not active"
            );
        }
        CanonicalRelationReference result = find(command.workspaceId(), command.relationId())
            .orElseThrow();
        appendHistory(
            command.workspaceId(), command.relationId(), result.version(),
            "withdrawn", command.actorId(), command.reasonHash()
        );
        return result;
    }

    private WorkItem lock(UUID workspaceId, UUID spaceId, UUID itemId) {
        return workItems.lock(workspaceId, spaceId, itemId)
            .orElseThrow(() -> failure(
                "CROSS_SPACE_REFERENCE_FORBIDDEN",
                "Cross-space endpoint reference is forbidden"
            ));
    }

    private void requireVersion(WorkItem item, long expectedVersion) {
        if (item.version() != expectedVersion) {
            throw failure(
                "CROSS_SPACE_RELATION_ENDPOINT_VERSION_CONFLICT",
                "A cross-space endpoint changed"
            );
        }
    }

    private void requireBinding(CreateCommand command, WorkItem source, WorkItem target) {
        if (!source.typeDefinitionId().equals(command.sourceDefinitionTypeId())
            || !source.typeVersionId().equals(command.sourceDefinitionVersionId())
            || !source.configHash().equals(command.sourceDefinitionHash())
            || !target.typeDefinitionId().equals(command.targetDefinitionTypeId())
            || !target.typeVersionId().equals(command.targetDefinitionVersionId())
            || !target.configHash().equals(command.targetDefinitionHash())) {
            throw failure(
                "CROSS_SPACE_RELATION_DEFINITION_CHANGED",
                "Endpoint definitions no longer match the confirmed policy"
            );
        }
        RelationDefinitionBinding sourceBinding = runtime.requireForCreate(
            source, target, command.relationKey()
        );
        RelationDefinitionBinding targetBinding = runtime.requireForSource(
            target, command.relationKey()
        );
        if (sourceBinding.direction() != targetBinding.direction()
            || sourceBinding.kind() != targetBinding.kind()) {
            throw failure(
                "CROSS_SPACE_RELATION_DEFINITION_MISMATCH",
                "Both endpoint definitions must agree on relation direction and kind"
            );
        }
        validateCardinality(command, sourceBinding);
        if (isAcyclic(sourceBinding.kind()) && pathExists(command)) {
            throw failure(
                "CROSS_SPACE_RELATION_CYCLE_DETECTED",
                "The cross-space relation would create a cycle"
            );
        }
    }

    private void validateAvailability(CreateCommand command) {
        Integer count = jdbc.queryForObject("""
            select count(*) from project_work_item_cross_space_relations
             where workspace_id=? and relation_key=? and status='active'
               and source_space_id=? and source_work_item_id=?
               and target_space_id=? and target_work_item_id=?
            """, Integer.class,
            command.workspaceId(), command.relationKey(),
            command.sourceSpaceId(), command.sourceWorkItemId(),
            command.targetSpaceId(), command.targetWorkItemId()
        );
        if (count != null && count > 0) {
            throw failure(
                "CROSS_SPACE_RELATION_EDGE_CONFLICT",
                "An equivalent active cross-space relation already exists"
            );
        }
    }

    private void validateCardinality(
        CreateCommand command, RelationDefinitionBinding binding
    ) {
        if (binding.sourceCardinality() == Cardinality.one
            && count(command, true) > 0) {
            throw failure(
                "CROSS_SPACE_RELATION_SOURCE_CARDINALITY_EXCEEDED",
                "The source endpoint reached its cross-space relation bound"
            );
        }
        if (binding.targetCardinality() == Cardinality.one
            && count(command, false) > 0) {
            throw failure(
                "CROSS_SPACE_RELATION_TARGET_CARDINALITY_EXCEEDED",
                "The target endpoint reached its cross-space relation bound"
            );
        }
    }

    private int count(CreateCommand command, boolean source) {
        String endpoint = source
            ? "source_space_id=? and source_work_item_id=?"
            : "target_space_id=? and target_work_item_id=?";
        Integer value = jdbc.queryForObject(
            "select count(*) from project_work_item_cross_space_relations"
                + " where workspace_id=? and relation_key=? and status='active' and " + endpoint,
            Integer.class,
            command.workspaceId(), command.relationKey(),
            source ? command.sourceSpaceId() : command.targetSpaceId(),
            source ? command.sourceWorkItemId() : command.targetWorkItemId()
        );
        return value == null ? 0 : value;
    }

    private boolean pathExists(CreateCommand command) {
        Integer count = jdbc.queryForObject("""
            with recursive walk(space_id,work_item_id,depth) as (
                select target_space_id,target_work_item_id,1
                  from project_work_item_cross_space_relations
                 where workspace_id=? and relation_key=? and status='active'
                   and source_space_id=? and source_work_item_id=?
                union
                select r.target_space_id,r.target_work_item_id,w.depth+1
                  from walk w
                  join project_work_item_cross_space_relations r
                    on r.workspace_id=? and r.relation_key=? and r.status='active'
                   and r.source_space_id=w.space_id
                   and r.source_work_item_id=w.work_item_id
                 where w.depth < ?
            )
            select count(*) from walk where space_id=? and work_item_id=?
            """, Integer.class,
            command.workspaceId(), command.relationKey(),
            command.targetSpaceId(), command.targetWorkItemId(),
            command.workspaceId(), command.relationKey(), MAX_GRAPH_DEPTH,
            command.sourceSpaceId(), command.sourceWorkItemId()
        );
        return count != null && count > 0;
    }

    private boolean isAcyclic(RelationKind kind) {
        return kind == RelationKind.parent_child
            || kind == RelationKind.dependency
            || kind == RelationKind.blocking;
    }

    private void acquireLock(CreateCommand command) {
        jdbc.query(
            "select pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            statement -> statement.setString(
                1, command.workspaceId() + ":cross:" + command.relationKey()
            ),
            result -> null
        );
    }

    private void appendHistory(
        UUID workspaceId,
        UUID relationId,
        long version,
        String event,
        UUID actorId,
        String reasonHash
    ) {
        jdbc.update("""
            insert into project_work_item_cross_space_relation_history(
                id,workspace_id,relation_id,relation_version,event_kind,actor_id,reason_hash
            ) values (?,?,?,?,?,?,?)
            """,
            UUID.randomUUID(), workspaceId, relationId, version, event, actorId, reasonHash
        );
    }

    private CanonicalRelationReference map(ResultSet rs, int row) throws SQLException {
        return new CanonicalRelationReference(
            rs.getObject("id", UUID.class),
            rs.getObject("source_space_id", UUID.class),
            rs.getObject("source_work_item_id", UUID.class),
            rs.getObject("target_space_id", UUID.class),
            rs.getObject("target_work_item_id", UUID.class),
            rs.getString("relation_key"),
            rs.getString("direction"),
            rs.getString("status"),
            rs.getLong("version"),
            rs.getObject("source_policy_id", UUID.class),
            rs.getLong("source_policy_version"),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
