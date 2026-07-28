package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;

public final class AutomationManagementModels {
    public static final int SCHEMA_VERSION=1;
    private AutomationManagementModels(){}
    public record QuotaState(
        String quotaType,String quotaKey,Instant windowStart,int usedCount,
        int limitCount,Instant pausedUntil,int version
    ){}
    public record ManagementPreference(boolean compactMode,String defaultFilter,int version){}
    public record ManagementFoundation(
        int schemaVersion,AutomationRuleModels.AutomationFoundation rules,
        AutomationExecutionModels.ExecutionFoundation executions,
        AutomationConnectorModels.ConnectorFoundation connectors,
        List<QuotaState> quotas,ManagementPreference preference,
        boolean healthy,List<String> diagnostics
    ){}
    public record QuotaGovernanceCommand(
        int schemaVersion,String requestId,String quotaType,String quotaKey,
        String action,Instant pausedUntil,String reason,int expectedVersion
    ){}
    public record SavePreferenceCommand(
        int schemaVersion,String requestId,boolean compactMode,String defaultFilter,int expectedVersion
    ){}
}
