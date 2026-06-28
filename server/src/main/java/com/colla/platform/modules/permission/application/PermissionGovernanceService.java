package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionInspectionResult;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskItem;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionMatch;
import com.colla.platform.modules.permission.infrastructure.ResourcePermissionRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionGovernanceService {
    private final PermissionService permissionService;
    private final PermissionDecisionService permissionDecisionService;
    private final ResourcePermissionRepository resourcePermissionRepository;
    private final JdbcTemplate jdbcTemplate;

    public PermissionGovernanceService(
        PermissionService permissionService,
        PermissionDecisionService permissionDecisionService,
        ResourcePermissionRepository resourcePermissionRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.permissionService = permissionService;
        this.permissionDecisionService = permissionDecisionService;
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PermissionInspectionResult inspect(
        CurrentUser currentUser,
        UUID userId,
        String resourceType,
        UUID resourceId,
        String action
    ) {
        permissionService.requireInspectPermissions(currentUser);
        String requiredLevel = permissionDecisionService.requiredLevel(action);
        return resourcePermissionRepository.findBestMatch(currentUser.workspaceId(), userId, resourceType, resourceId)
            .map(match -> toInspection(userId, resourceType, resourceId, action, requiredLevel, match))
            .orElseGet(() -> new PermissionInspectionResult(
                userId,
                resourceType,
                resourceId,
                action,
                false,
                "none",
                requiredLevel,
                "none",
                "该用户不在该资源授权范围内。",
                null
            ));
    }

    public PermissionRiskSummary risks(CurrentUser currentUser) {
        permissionService.requireInspectPermissions(currentUser);
        List<PermissionRiskItem> items = new ArrayList<>();
        items.addAll(disabledUserRisks(currentUser.workspaceId()));
        items.addAll(disabledDepartmentRisks(currentUser.workspaceId()));
        items.addAll(disabledUserGroupRisks(currentUser.workspaceId()));
        items.addAll(resourcesWithoutOwner(currentUser.workspaceId()));
        items.addAll(highRiskBroadGrants(currentUser.workspaceId()));
        excessiveAdmins(currentUser.workspaceId()).ifPresent(items::add);
        return new PermissionRiskSummary(items.size(), items);
    }

    public String exportRisksCsv(CurrentUser currentUser) {
        PermissionRiskSummary summary = risks(currentUser);
        StringBuilder csv = new StringBuilder("ruleCode,severity,resourceType,resourceId,subjectType,subjectId,subjectName,permissionLevel,reason\n");
        for (PermissionRiskItem item : summary.items()) {
            csv.append(csv(item.ruleCode())).append(',')
                .append(csv(item.severity())).append(',')
                .append(csv(item.resourceType())).append(',')
                .append(csv(item.resourceId() == null ? "" : item.resourceId().toString())).append(',')
                .append(csv(item.subjectType())).append(',')
                .append(csv(item.subjectId() == null ? "" : item.subjectId().toString())).append(',')
                .append(csv(item.subjectName())).append(',')
                .append(csv(item.permissionLevel())).append(',')
                .append(csv(item.reason())).append('\n');
        }
        return csv.toString();
    }

    private PermissionInspectionResult toInspection(
        UUID userId,
        String resourceType,
        UUID resourceId,
        String action,
        String requiredLevel,
        ResourcePermissionMatch match
    ) {
        boolean allowed = permissionDecisionService.levelRank(match.permissionLevel()) >= permissionDecisionService.levelRank(requiredLevel);
        return new PermissionInspectionResult(
            userId,
            resourceType,
            resourceId,
            action,
            allowed,
            match.permissionLevel(),
            requiredLevel,
            match.subjectType() + ":" + match.subjectName(),
            allowed ? "当前授权满足访问要求。" : "当前授权等级不足。",
            match.permissionId()
        );
    }

    private List<PermissionRiskItem> disabledUserRisks(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select rp.id, rp.resource_type, rp.resource_id, rp.subject_id, coalesce(u.display_name, u.username) subject_name,
                       rp.permission_level
                from resource_permissions rp
                join users u on u.id = rp.subject_id and u.workspace_id = rp.workspace_id
                where rp.workspace_id = ?
                  and rp.subject_type = 'user'
                  and rp.status = 'active'
                  and (u.status <> 'active' or u.deleted_at is not null)
                """,
            (rs, rowNum) -> new PermissionRiskItem(
                rs.getObject("id", UUID.class).toString(),
                "disabled_user_active_permission",
                "high",
                rs.getString("resource_type"),
                rs.getObject("resource_id", UUID.class),
                "user",
                rs.getObject("subject_id", UUID.class),
                rs.getString("subject_name"),
                rs.getString("permission_level"),
                "停用或删除成员仍保留资源授权。"
            ),
            workspaceId
        );
    }

    private List<PermissionRiskItem> disabledDepartmentRisks(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select rp.id, rp.resource_type, rp.resource_id, rp.subject_id, d.name subject_name, rp.permission_level
                from resource_permissions rp
                join departments d on d.id = rp.subject_id and d.workspace_id = rp.workspace_id
                where rp.workspace_id = ?
                  and rp.subject_type = 'department'
                  and rp.status = 'active'
                  and (d.status <> 'active' or d.deleted_at is not null)
                """,
            (rs, rowNum) -> risk(rs.getObject("id", UUID.class), "disabled_department_active_permission", "medium", rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), "department", rs.getObject("subject_id", UUID.class), rs.getString("subject_name"), rs.getString("permission_level"), "停用或删除部门仍保留资源授权。"),
            workspaceId
        );
    }

    private List<PermissionRiskItem> disabledUserGroupRisks(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select rp.id, rp.resource_type, rp.resource_id, rp.subject_id, ug.name subject_name, rp.permission_level
                from resource_permissions rp
                join user_groups ug on ug.id = rp.subject_id and ug.workspace_id = rp.workspace_id
                where rp.workspace_id = ?
                  and rp.subject_type = 'user_group'
                  and rp.status = 'active'
                  and (ug.status <> 'active' or ug.deleted_at is not null)
                """,
            (rs, rowNum) -> risk(rs.getObject("id", UUID.class), "disabled_user_group_active_permission", "medium", rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), "user_group", rs.getObject("subject_id", UUID.class), rs.getString("subject_name"), rs.getString("permission_level"), "停用或删除用户组仍保留资源授权。"),
            workspaceId
        );
    }

    private List<PermissionRiskItem> resourcesWithoutOwner(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select rp.resource_type, rp.resource_id
                from resource_permissions rp
                where rp.workspace_id = ? and rp.status = 'active'
                group by rp.resource_type, rp.resource_id
                having sum(case when rp.permission_level = 'owner' then 1 else 0 end) = 0
                """,
            (rs, rowNum) -> new PermissionRiskItem(
                "owner:" + rs.getString("resource_type") + ":" + rs.getObject("resource_id", UUID.class),
                "resource_without_owner",
                "high",
                rs.getString("resource_type"),
                rs.getObject("resource_id", UUID.class),
                null,
                null,
                null,
                null,
                "资源存在授权记录但没有 owner 权限主体。"
            ),
            workspaceId
        );
    }

    private List<PermissionRiskItem> highRiskBroadGrants(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select rp.id, rp.resource_type, rp.resource_id, rp.subject_type, rp.subject_id,
                       coalesce(d.name, ug.name) subject_name, rp.permission_level
                from resource_permissions rp
                left join departments d on rp.subject_type = 'department' and d.id = rp.subject_id
                left join user_groups ug on rp.subject_type = 'user_group' and ug.id = rp.subject_id
                where rp.workspace_id = ?
                  and rp.status = 'active'
                  and rp.subject_type in ('department', 'user_group')
                  and rp.permission_level in ('manage', 'owner')
                """,
            (rs, rowNum) -> risk(rs.getObject("id", UUID.class), "high_risk_broad_permission", "critical", rs.getString("resource_type"), rs.getObject("resource_id", UUID.class), rs.getString("subject_type"), rs.getObject("subject_id", UUID.class), rs.getString("subject_name"), rs.getString("permission_level"), "部门或用户组持有 manage/owner 高风险权限。"),
            workspaceId
        );
    }

    private java.util.Optional<PermissionRiskItem> excessiveAdmins(UUID workspaceId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(distinct subject_id)
                from (
                    select ur.user_id subject_id
                    from user_roles ur
                    join roles r on r.id = ur.role_id and r.code = 'admin'
                    where ur.workspace_id = ?
                    union
                    select ra.subject_id
                    from role_assignments ra
                    join roles r on r.id = ra.role_id and r.code = 'admin'
                    where ra.workspace_id = ? and ra.subject_type = 'user' and ra.status = 'active'
                ) admins
                """,
            Integer.class,
            workspaceId,
            workspaceId
        );
        if (count == null || count <= 3) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new PermissionRiskItem(
            "admin_count",
            "too_many_admins",
            "medium",
            null,
            null,
            "role",
            null,
            "admin",
            null,
            "管理员成员数量超过 3 人，建议复核最小授权。"
        ));
    }

    private PermissionRiskItem risk(UUID id, String ruleCode, String severity, String resourceType, UUID resourceId, String subjectType, UUID subjectId, String subjectName, String permissionLevel, String reason) {
        return new PermissionRiskItem(id.toString(), ruleCode, severity, resourceType, resourceId, subjectType, subjectId, subjectName, permissionLevel, reason);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
