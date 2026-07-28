package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.AutomationConnectorModels.Connector;
import com.colla.platform.modules.project.domain.AutomationConnectorModels.Delivery;
import com.colla.platform.modules.project.domain.AutomationConnectorModels.DeliveryAttempt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAutomationConnectorRepository implements AutomationConnectorRepository {
    private final JdbcTemplate jdbc;
    public JdbcAutomationConnectorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<Connector> list(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query("""
            select id,name,target_uri,credential_reference,status,signing_version,version,updated_at
            from project_automation_connectors where workspace_id=? and space_id=? and status<>'archived'
            order by updated_at desc,id limit ?
            """, this::connector, workspaceId, spaceId, limit);
    }
    @Override public Optional<Connector> find(UUID workspaceId, UUID spaceId, UUID id) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
            select id,name,target_uri,credential_reference,status,signing_version,version,updated_at
            from project_automation_connectors where workspace_id=? and space_id=? and id=?
            """, this::connector, workspaceId, spaceId, id)); }
        catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
    }
    @Override @Transactional public Connector save(
        UUID workspaceId, UUID spaceId, UUID id, int expectedVersion,
        String name, String targetUri, String credentialReference
    ) {
        if (expectedVersion == 0) {
            try {
                jdbc.update("""
                    insert into project_automation_connectors
                    (id,workspace_id,space_id,name,target_uri,credential_reference,status,signing_version,version)
                    values(?,?,?,?,?,?,'active',1,1)
                    """, id, workspaceId, spaceId, name, targetUri, credentialReference);
            } catch (org.springframework.dao.DuplicateKeyException exception) {
                throw failure("AUTOMATION_CONNECTOR_VERSION_CONFLICT", "Connector create conflicted");
            }
        } else {
            int changed = jdbc.update("""
                update project_automation_connectors set name=?,target_uri=?,credential_reference=?,
                  version=version+1,updated_at=now()
                where workspace_id=? and space_id=? and id=? and version=? and status<>'archived'
                """, name, targetUri, credentialReference, workspaceId, spaceId, id, expectedVersion);
            if (changed != 1) throw failure("AUTOMATION_CONNECTOR_VERSION_CONFLICT", "Connector version conflicted");
        }
        return find(workspaceId, spaceId, id).orElseThrow();
    }
    @Override public List<Delivery> deliveries(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query("""
            select id,connector_id,run_id,payload_version,payload_hash,status,attempt_count,
              next_attempt_at,created_at,completed_at
            from project_automation_deliveries where workspace_id=? and space_id=?
            order by created_at desc,id limit ?
            """, (rs,row) -> delivery(workspaceId,spaceId,rs), workspaceId, spaceId, limit);
    }
    @Override @Transactional public Delivery beginDelivery(
        UUID workspaceId, UUID spaceId, UUID connectorId, UUID runId, String payloadHash, String nonce
    ) {
        UUID id=UUID.randomUUID();
        int inserted = jdbc.update("""
                insert into project_automation_deliveries
                (id,workspace_id,space_id,connector_id,run_id,payload_version,payload_hash,nonce,status)
                values(?,?,?,?,?,1,?,?,'pending')
                on conflict(workspace_id,space_id,connector_id,nonce) do nothing
                """,id,workspaceId,spaceId,connectorId,runId,payloadHash,nonce);
        if (inserted == 0) {
            id=jdbc.queryForObject("""
                select id from project_automation_deliveries
                where workspace_id=? and space_id=? and connector_id=? and nonce=?
                """,UUID.class,workspaceId,spaceId,connectorId,nonce);
        }
        return getDelivery(workspaceId,spaceId,id);
    }
    @Override @Transactional public Delivery recordAttempt(
        UUID workspaceId, UUID spaceId, UUID deliveryId, String outcome,
        Integer httpStatus, String errorCode, int durationMs, boolean retryable
    ) {
        Integer number=jdbc.queryForObject("""
            select attempt_count+1 from project_automation_deliveries
            where workspace_id=? and space_id=? and id=? for update
            """,Integer.class,workspaceId,spaceId,deliveryId);
        if(number==null) throw failure("NOT_FOUND_OR_HIDDEN","Delivery unavailable");
        jdbc.update("""
            insert into project_automation_delivery_attempts
            (id,workspace_id,space_id,delivery_id,attempt_number,outcome,http_status,error_code,duration_ms)
            values(?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),workspaceId,spaceId,deliveryId,number,outcome,httpStatus,errorCode,durationMs);
        String status="succeeded".equals(outcome)?"succeeded":retryable&&number<6?"retry":"dead_letter";
        jdbc.update("""
            update project_automation_deliveries set status=?,attempt_count=?,
              next_attempt_at=case when ?='retry' then now()+(interval '5 seconds'*power(2,least(?,9))) else null end,
              completed_at=case when ? in ('succeeded','dead_letter') then now() else null end
            where workspace_id=? and space_id=? and id=?
            """,status,number,status,number,status,workspaceId,spaceId,deliveryId);
        if("dead_letter".equals(status)) jdbc.update("""
            insert into project_automation_dead_letters(delivery_id,workspace_id,space_id,reason_code)
            values(?,?,?,?) on conflict(delivery_id) do nothing
            """,deliveryId,workspaceId,spaceId,errorCode==null?"DELIVERY_FAILED":errorCode);
        return getDelivery(workspaceId,spaceId,deliveryId);
    }
    @Override @Transactional public Delivery govern(
        UUID workspaceId, UUID spaceId, UUID deliveryId, String action, String reason
    ) {
        int changed;
        if("replay".equals(action)) {
            changed=jdbc.update("""
                update project_automation_deliveries set status='retry',next_attempt_at=now(),completed_at=null
                where workspace_id=? and space_id=? and id=? and status='dead_letter'
                """,workspaceId,spaceId,deliveryId);
            jdbc.update("update project_automation_dead_letters set replay_count=replay_count+1,last_reason=? where delivery_id=?",
                reason,deliveryId);
        } else {
            changed=jdbc.update("""
                update project_automation_deliveries set status='abandoned',completed_at=now()
                where workspace_id=? and space_id=? and id=? and status in ('dead_letter','retry')
                """,workspaceId,spaceId,deliveryId);
            jdbc.update("update project_automation_dead_letters set last_reason=? where delivery_id=?",reason,deliveryId);
        }
        if(changed!=1) throw failure("AUTOMATION_DELIVERY_STATE_CONFLICT","Delivery cannot be governed");
        return getDelivery(workspaceId,spaceId,deliveryId);
    }
    private Delivery getDelivery(UUID workspaceId,UUID spaceId,UUID id) {
        return jdbc.queryForObject("""
            select id,connector_id,run_id,payload_version,payload_hash,status,attempt_count,
              next_attempt_at,created_at,completed_at from project_automation_deliveries
            where workspace_id=? and space_id=? and id=?
            """,(rs,row)->delivery(workspaceId,spaceId,rs),workspaceId,spaceId,id);
    }
    private Connector connector(ResultSet rs,int row) throws SQLException {
        return new Connector(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("target_uri"),
            rs.getString("credential_reference"),rs.getString("status"),rs.getInt("signing_version"),
            rs.getInt("version"),rs.getTimestamp("updated_at").toInstant());
    }
    private Delivery delivery(UUID workspaceId,UUID spaceId,ResultSet rs) throws SQLException {
        UUID id=rs.getObject("id",UUID.class);
        List<DeliveryAttempt> attempts=jdbc.query("""
            select attempt_number,outcome,http_status,error_code,duration_ms,attempted_at
            from project_automation_delivery_attempts where workspace_id=? and space_id=? and delivery_id=?
            order by attempt_number
            """,(a,row)->new DeliveryAttempt(a.getInt(1),a.getString(2),(Integer)a.getObject(3),
                a.getString(4),a.getInt(5),a.getTimestamp(6).toInstant()),workspaceId,spaceId,id);
        String dead=jdbc.query("""
            select reason_code from project_automation_dead_letters where delivery_id=?
            """,(a,row)->a.getString(1),id).stream().findFirst().orElse(null);
        return new Delivery(id,rs.getObject("connector_id",UUID.class),rs.getObject("run_id",UUID.class),
            rs.getInt("payload_version"),rs.getString("payload_hash"),rs.getString("status"),
            rs.getInt("attempt_count"),instant(rs.getTimestamp("next_attempt_at")),attempts,dead,
            rs.getTimestamp("created_at").toInstant(),instant(rs.getTimestamp("completed_at")));
    }
    private Instant instant(Timestamp value){return value==null?null:value.toInstant();}
}
