package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.AutomationManagementModels.QuotaGovernanceCommand;
import com.colla.platform.modules.project.domain.AutomationManagementModels.QuotaState;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationQuotaService {
    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final TransactionalOutbox outbox;
    private final ObjectMapper json;
    public AutomationQuotaService(JdbcTemplate jdbc,AuditLog audit,TransactionalOutbox outbox,ObjectMapper json){
        this.jdbc=jdbc;this.audit=audit;this.outbox=outbox;this.json=json;
    }

    @Transactional public void claim(
        UUID workspaceId,UUID spaceId,UUID ruleId,UUID actorId,String actionType,String requestKey
    ){
        int inserted=jdbc.update("""
            insert into project_automation_quota_receipts
             (workspace_id,space_id,request_key,rule_id,actor_id,action_type)
            values(?,?,?,?,?,?) on conflict do nothing
            """,workspaceId,spaceId,requestKey,ruleId,actorId,actionType);
        if(inserted==0)return;
        consume(workspaceId,spaceId,"space",spaceId.toString(),500);
        consume(workspaceId,spaceId,"rule",ruleId.toString(),100);
        consume(workspaceId,spaceId,"actor",actorId.toString(),200);
        consume(workspaceId,spaceId,"action",actionType,250);
    }
    private void consume(UUID workspaceId,UUID spaceId,String type,String key,int limit){
        Instant window=Instant.now().truncatedTo(ChronoUnit.DAYS);
        Timestamp windowTimestamp=Timestamp.from(window);
        jdbc.update("""
            insert into project_automation_quota_states
             (workspace_id,space_id,quota_type,quota_key,window_start,used_count,limit_count)
            values(?,?,?,?,?,1,?)
            on conflict(workspace_id,space_id,quota_type,quota_key,window_start)
            do update set used_count=project_automation_quota_states.used_count+1,
              version=project_automation_quota_states.version+1,updated_at=now()
            """,workspaceId,spaceId,type,key,windowTimestamp,limit);
        QuotaState state=jdbc.queryForObject("""
            select quota_type,quota_key,window_start,used_count,limit_count,paused_until,version
            from project_automation_quota_states
            where workspace_id=? and space_id=? and quota_type=? and quota_key=? and window_start=?
            """,this::map,workspaceId,spaceId,type,key,windowTimestamp);
        if(state!=null&&state.pausedUntil()!=null&&state.pausedUntil().isAfter(Instant.now()))
            throw failure("AUTOMATION_QUOTA_PAUSED","Automation quota is paused");
        if(state!=null&&state.usedCount()>state.limitCount())
            throw failure("AUTOMATION_QUOTA_EXCEEDED","Automation quota is exceeded");
    }
    public List<QuotaState> list(UUID workspaceId,UUID spaceId){
        return jdbc.query("""
            select quota_type,quota_key,window_start,used_count,limit_count,paused_until,version
            from project_automation_quota_states where workspace_id=? and space_id=?
            order by updated_at desc limit 100
            """,this::map,workspaceId,spaceId);
    }
    @Transactional public QuotaState govern(CurrentUser user,UUID spaceId,QuotaGovernanceCommand command){
        if(command==null||command.schemaVersion()!=1||command.requestId()==null
            ||!Set.of("pause","resume").contains(command.action())
            ||command.reason()==null||command.reason().trim().length()<10||command.reason().length()>512)
            throw failure("AUTOMATION_GOVERNANCE_INVALID","Quota governance input is invalid");
        String inputHash=hash(command);
        var replay=jdbc.query("""
            select input_hash,response_json from project_automation_governance_receipts
            where workspace_id=? and space_id=? and request_id=?
            """,(rs,row)->new String[]{rs.getString(1),rs.getString(2)},
            user.workspaceId(),spaceId,command.requestId()).stream().findFirst();
        if(replay.isPresent()){
            if(!inputHash.equals(replay.get()[0]))throw failure("AUTOMATION_GOVERNANCE_REQUEST_CONFLICT","Governance request changed");
            try{return json.readValue(replay.get()[1],QuotaState.class);}catch(Exception e){throw new IllegalStateException(e);}
        }
        Instant window=Instant.now().truncatedTo(ChronoUnit.DAYS);
        Timestamp windowTimestamp=Timestamp.from(window);
        Instant paused="pause".equals(command.action())
            ? (command.pausedUntil()==null?Instant.now().plus(1,ChronoUnit.HOURS):command.pausedUntil()) : null;
        Timestamp pausedTimestamp=paused==null?null:Timestamp.from(paused);
        int changed=jdbc.update("""
            update project_automation_quota_states set paused_until=?,version=version+1,updated_at=now()
            where workspace_id=? and space_id=? and quota_type=? and quota_key=? and window_start=? and version=?
            """,pausedTimestamp,user.workspaceId(),spaceId,command.quotaType(),command.quotaKey(),windowTimestamp,command.expectedVersion());
        if(changed!=1)throw failure("AUTOMATION_GOVERNANCE_VERSION_CONFLICT","Quota governance version conflicted");
        QuotaState result=jdbc.queryForObject("""
            select quota_type,quota_key,window_start,used_count,limit_count,paused_until,version
            from project_automation_quota_states
            where workspace_id=? and space_id=? and quota_type=? and quota_key=? and window_start=?
            """,this::map,user.workspaceId(),spaceId,command.quotaType(),command.quotaKey(),windowTimestamp);
        try {
            jdbc.update("""
                insert into project_automation_governance_receipts
                (workspace_id,space_id,request_id,input_hash,action,target_type,target_key,reason,actor_id,response_json)
                values(?,?,?,?,?,'quota',?,?,?,?::jsonb)
                """,user.workspaceId(),spaceId,command.requestId(),inputHash,command.action(),
                command.quotaType()+":"+command.quotaKey(),command.reason().trim(),user.id(),json.writeValueAsString(result));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
        audit.log(user,"project_automation.quota_"+command.action(),"project_automation_quota",spaceId,
            java.util.Map.of("quota_type",command.quotaType(),"quota_key",command.quotaKey()));
        outbox.append(user.workspaceId(),"project.automation.management.changed","project_space",spaceId,user.id(),
            java.util.Map.of("spaceId",spaceId.toString(),"change","quota_"+command.action()),
            "automation-governance:"+command.requestId());
        return result;
    }
    private QuotaState map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        var paused=rs.getTimestamp("paused_until");
        return new QuotaState(rs.getString("quota_type"),rs.getString("quota_key"),
            rs.getTimestamp("window_start").toInstant(),rs.getInt("used_count"),rs.getInt("limit_count"),
            paused==null?null:paused.toInstant(),rs.getInt("version"));
    }
    private String hash(Object value){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(json.writeValueAsBytes(value)));}catch(Exception e){throw new IllegalStateException(e);}
    }
}
