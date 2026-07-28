package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.AutomationManagementModels.*;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationManagementService {
    private final AutomationRuleService rules;
    private final AutomationExecutionService executions;
    private final AutomationConnectorService connectors;
    private final AutomationQuotaService quotas;
    private final ProjectSpaceRepository spaces;
    private final JdbcTemplate jdbc;
    public AutomationManagementService(AutomationRuleService rules,AutomationExecutionService executions,
        AutomationConnectorService connectors,AutomationQuotaService quotas,
        ProjectSpaceRepository spaces,JdbcTemplate jdbc){
        this.rules=rules;this.executions=executions;this.connectors=connectors;
        this.quotas=quotas;this.spaces=spaces;this.jdbc=jdbc;
    }
    public ManagementFoundation get(CurrentUser user,UUID spaceId){
        visible(user,spaceId);
        var rule=rules.get(user,spaceId);var run=executions.list(user,spaceId);
        var connector=connectors.get(user,spaceId);var quota=quotas.list(user.workspaceId(),spaceId);
        List<String> diagnostics=new java.util.ArrayList<>();
        if(rule.truncated())diagnostics.add("RULES_TRUNCATED");
        if(run.truncated())diagnostics.add("RUNS_TRUNCATED");
        if(connector.connectorsTruncated()||connector.deliveriesTruncated())diagnostics.add("CONNECTORS_TRUNCATED");
        if(quota.stream().anyMatch(q->q.pausedUntil()!=null&&q.pausedUntil().isAfter(java.time.Instant.now())))
            diagnostics.add("QUOTA_PAUSED");
        return new ManagementFoundation(1,rule,run,connector,quota,preference(user,spaceId),
            diagnostics.isEmpty(),List.copyOf(diagnostics));
    }
    @Transactional public ManagementPreference savePreference(CurrentUser user,UUID spaceId,SavePreferenceCommand c){
        visible(user,spaceId);
        if(c==null||c.schemaVersion()!=1||c.requestId()==null
            ||!Set.of("all","failed","paused","dead_letter").contains(c.defaultFilter())
            ||c.expectedVersion()<0)throw failure("AUTOMATION_MANAGEMENT_INVALID","Preference input is invalid");
        int changed=c.expectedVersion()==0?jdbc.update("""
            insert into project_automation_management_preferences
             (workspace_id,space_id,user_id,compact_mode,default_filter,version)
            values(?,?,?,?,?,1) on conflict do nothing
            """,user.workspaceId(),spaceId,user.id(),c.compactMode(),c.defaultFilter())
            :jdbc.update("""
            update project_automation_management_preferences
            set compact_mode=?,default_filter=?,version=version+1,updated_at=now()
            where workspace_id=? and space_id=? and user_id=? and version=?
            """,c.compactMode(),c.defaultFilter(),user.workspaceId(),spaceId,user.id(),c.expectedVersion());
        if(changed!=1)throw failure("AUTOMATION_MANAGEMENT_VERSION_CONFLICT","Preference version conflicted");
        return preference(user,spaceId);
    }
    public QuotaState govern(CurrentUser user,UUID spaceId,QuotaGovernanceCommand command){
        configurable(user,spaceId);return quotas.govern(user,spaceId,command);
    }
    private ManagementPreference preference(CurrentUser user,UUID spaceId){
        return jdbc.query("""
            select compact_mode,default_filter,version from project_automation_management_preferences
            where workspace_id=? and space_id=? and user_id=?
            """,(rs,row)->new ManagementPreference(rs.getBoolean(1),rs.getString(2),rs.getInt(3)),
            user.workspaceId(),spaceId,user.id()).stream().findFirst()
            .orElse(new ManagementPreference(false,"all",0));
    }
    private ProjectSpaceSummary visible(CurrentUser u,UUID s){
        var space=spaces.findById(u.workspaceId(),s,u.id()).orElseThrow(()->failure("NOT_FOUND_OR_HIDDEN","Space unavailable"));
        if(!space.isMember()||"archived".equals(space.status()))throw failure("NOT_FOUND_OR_HIDDEN","Space unavailable");
        return space;
    }
    private void configurable(CurrentUser u,UUID s){
        var space=visible(u,s);
        if(!"active".equals(space.status())||!Set.of("owner","admin").contains(space.currentUserRole()))
            throw failure("FORBIDDEN","Only owners and administrators can govern automation");
    }
}
