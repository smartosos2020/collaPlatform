package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionInspectionResult;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionMatrixEntry;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskRemediation;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskItem;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionMatch;
import com.colla.platform.modules.permission.infrastructure.ResourcePermissionRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionGovernanceService {
    private static final List<PermissionMatrixEntry> PERMISSION_MATRIX = List.of(
        matrix("im", "conversation", "view", "view", "成员可查看会话与历史消息。", "非成员不可读取会话标题和消息。", "读取不记业务审计；成员和权限变更必须审计。"),
        matrix("im", "conversation", "send", "comment", "成员可发送消息并获得回执。", "只读或非成员发送被拒绝。", "发送结果与来源会话可追溯。"),
        matrix("im", "conversation", "manage_members", "manage", "会话管理员可增删成员。", "普通成员不可改变成员范围。", "成员变更前后与操作者必须审计。"),
        matrix("project", "project", "view", "view", "项目成员可查看项目与事项。", "非成员不可读取项目敏感信息。", "读取不记业务审计；成员变更必须审计。"),
        matrix("project", "project", "edit_issue", "edit", "编辑成员可创建和更新事项。", "只读或非成员写入被拒绝。", "状态、负责人和关键字段变化必须审计。"),
        matrix("project", "project", "manage_members", "manage", "项目管理员可治理成员。", "普通成员不可改变项目成员。", "成员变更前后与操作者必须审计。"),
        matrix("knowledge", "knowledge_content", "view", "view", "获授权主体可读取标题和正文。", "无权限主体不泄露标题、摘要和正文。", "授权来源可解释；分享与权限变更必须审计。"),
        matrix("knowledge", "knowledge_content", "edit", "edit", "编辑者可保存结构化内容。", "查看者保存被拒绝。", "版本、冲突和恢复操作必须审计。"),
        matrix("knowledge", "knowledge_content", "share", "manage", "管理者可授予和撤销访问。", "编辑者不可扩大授权范围。", "授权主体、来源、期限和变更前后必须审计。"),
        matrix("base", "base", "view", "view", "获授权主体可查看表、视图和记录。", "无权限主体不可读取字段和记录。", "授权来源可解释；导出与权限变更必须审计。"),
        matrix("base", "base", "edit_record", "edit", "编辑者可新增和修改记录。", "查看者写入或导入被拒绝。", "批量导入结果和关键记录变更必须审计。"),
        matrix("base", "base", "manage_schema", "manage", "管理者可修改字段、视图和授权。", "编辑者不可改变结构或授权。", "结构和授权变更前后必须审计。"),
        matrix("approval", "approval_instance", "view", "view", "发起人、当前处理人和获授权主体可查看。", "无关成员不可读取表单和审批意见。", "查看范围由参与关系解释。"),
        matrix("approval", "approval_instance", "act", "edit", "当前合法处理人可审批、拒绝或转交。", "非当前处理人和过期会话动作被拒绝。", "每次动作、意见、前后状态和操作者必须审计。"),
        matrix("approval", "approval_instance", "withdraw", "edit", "满足状态规则的发起人可撤回。", "非发起人或终态实例撤回被拒绝。", "撤回原因和前后状态必须审计。")
    );
    private final PermissionService permissionService;
    private final PermissionDecisionService permissionDecisionService;
    private final ResourcePermissionRepository resourcePermissionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public PermissionGovernanceService(
        PermissionService permissionService,
        PermissionDecisionService permissionDecisionService,
        ResourcePermissionRepository resourcePermissionRepository,
        JdbcTemplate jdbcTemplate,
        AuditService auditService
    ) {
        this.permissionService = permissionService;
        this.permissionDecisionService = permissionDecisionService;
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
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
        return risks(currentUser, null);
    }

    public List<PermissionMatrixEntry> permissionMatrix(CurrentUser currentUser) {
        permissionService.requireInspectPermissions(currentUser);
        return PERMISSION_MATRIX.stream()
            .map(entry -> new PermissionMatrixEntry(
                entry.module(), entry.resourceType(), entry.action(), permissionDecisionService.requiredLevel(entry.action()),
                entry.allowedExpectation(), entry.deniedExpectation(), entry.auditExpectation()
            ))
            .toList();
    }

    public PermissionRiskRemediation remediate(CurrentUser currentUser, String riskId, boolean confirm) {
        permissionService.requireInspectPermissions(currentUser);
        PermissionRiskItem risk = risks(currentUser).items().stream()
            .filter(item -> item.id().equals(riskId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission risk not found"));
        boolean executable = List.of(
            "expired_active_permission", "orphaned_permission_subject", "disabled_user_active_permission",
            "disabled_department_active_permission", "disabled_user_group_active_permission"
        ).contains(risk.ruleCode());
        if (!executable) {
            return new PermissionRiskRemediation(riskId, risk.ruleCode(), false, false, "manual_review", "该风险需要人工判断授权范围。");
        }
        if (!confirm) {
            return new PermissionRiskRemediation(riskId, risk.ruleCode(), true, false, "revoke_permission", "确认后仅撤销这一条风险授权。");
        }
        UUID permissionId;
        try {
            permissionId = UUID.fromString(riskId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Risk does not reference a permission grant");
        }
        int changed = jdbcTemplate.update(
            """
                update resource_permissions set status = 'revoked', updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and status = 'active'
                """,
            currentUser.id(), currentUser.workspaceId(), permissionId
        );
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permission risk is no longer active");
        }
        auditService.log(currentUser, "permission.risk.remediated", risk.resourceType(), risk.resourceId(), Map.of(
            "riskId", riskId,
            "ruleCode", risk.ruleCode(),
            "action", "revoke_permission",
            "subjectType", risk.subjectType() == null ? "" : risk.subjectType(),
            "subjectId", risk.subjectId() == null ? "" : risk.subjectId().toString()
        ));
        return new PermissionRiskRemediation(riskId, risk.ruleCode(), true, true, "revoke_permission", "风险授权已撤销并写入审计。");
    }

    private static PermissionMatrixEntry matrix(
        String module,
        String resourceType,
        String action,
        String requiredLevel,
        String allowedExpectation,
        String deniedExpectation,
        String auditExpectation
    ) {
        return new PermissionMatrixEntry(module, resourceType, action, requiredLevel, allowedExpectation, deniedExpectation, auditExpectation);
    }

    public PermissionRiskSummary risks(CurrentUser currentUser, UUID knowledgeBaseId) {
        permissionService.requireInspectPermissions(currentUser);
        List<PermissionRiskItem> items = new ArrayList<>();
        items.addAll(disabledUserRisks(currentUser.workspaceId()));
        items.addAll(disabledDepartmentRisks(currentUser.workspaceId()));
        items.addAll(disabledUserGroupRisks(currentUser.workspaceId()));
        items.addAll(expiredGrantRisks(currentUser.workspaceId()));
        items.addAll(orphanedSubjectRisks(currentUser.workspaceId()));
        items.addAll(resourcesWithoutOwner(currentUser.workspaceId()));
        items.addAll(highRiskBroadGrants(currentUser.workspaceId()));
        if (knowledgeBaseId == null) {
            excessiveAdmins(currentUser.workspaceId()).ifPresent(items::add);
        }
        if (knowledgeBaseId != null) {
            items = items.stream()
                .filter(item -> inKnowledgeBase(currentUser.workspaceId(), knowledgeBaseId, item.resourceType(), item.resourceId()))
                .toList();
        }
        return new PermissionRiskSummary(items.size(), items);
    }

    public String exportRisksCsv(CurrentUser currentUser) {
        return exportRisksCsv(currentUser, null);
    }

    public String exportRisksCsv(CurrentUser currentUser, UUID knowledgeBaseId) {
        PermissionRiskSummary summary = risks(currentUser, knowledgeBaseId);
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

    private boolean inKnowledgeBase(UUID workspaceId, UUID knowledgeBaseId, String resourceType, UUID resourceId) {
        if (resourceType == null || resourceId == null) {
            return false;
        }
        if ("knowledge_base".equals(resourceType)) {
            return resourceId.equals(knowledgeBaseId);
        }
        if (!"knowledge_content".equals(resourceType)) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject(
            """
                with recursive subtree as (
                    select d.id
                    from knowledge_base_spaces k
                    join knowledge_base_items d on d.workspace_id = k.workspace_id and d.id = k.root_item_id and d.deleted_at is null
                    where k.workspace_id = ? and k.id = ? and k.deleted_at is null
                    union all
                    select child.id
                    from knowledge_base_items child
                    join subtree parent on parent.id = child.parent_id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                select exists(select 1 from subtree where id = ?)
                """,
            Boolean.class,
            workspaceId,
            knowledgeBaseId,
            workspaceId,
            resourceId
        );
        return Boolean.TRUE.equals(exists);
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

    private List<PermissionRiskItem> expiredGrantRisks(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select id, resource_type, resource_id, subject_type, subject_id, permission_level
                from resource_permissions
                where workspace_id = ? and status = 'active' and expires_at is not null and expires_at <= now()
                """,
            (rs, rowNum) -> risk(
                rs.getObject("id", UUID.class), "expired_active_permission", "medium",
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class),
                rs.getString("subject_type"), rs.getObject("subject_id", UUID.class), null,
                rs.getString("permission_level"), "授权已过期但记录仍处于 active 状态。"
            ),
            workspaceId
        );
    }

    private List<PermissionRiskItem> orphanedSubjectRisks(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select rp.id, rp.resource_type, rp.resource_id, rp.subject_type, rp.subject_id, rp.permission_level
                from resource_permissions rp
                where rp.workspace_id = ? and rp.status = 'active'
                  and not exists (
                    select 1 from users u where rp.subject_type = 'user' and u.workspace_id = rp.workspace_id and u.id = rp.subject_id
                    union all
                    select 1 from departments d where rp.subject_type = 'department' and d.workspace_id = rp.workspace_id and d.id = rp.subject_id
                    union all
                    select 1 from user_groups ug where rp.subject_type = 'user_group' and ug.workspace_id = rp.workspace_id and ug.id = rp.subject_id
                    union all
                    select 1 from roles r where rp.subject_type = 'role' and r.workspace_id = rp.workspace_id and r.id = rp.subject_id
                  )
                """,
            (rs, rowNum) -> risk(
                rs.getObject("id", UUID.class), "orphaned_permission_subject", "high",
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class),
                rs.getString("subject_type"), rs.getObject("subject_id", UUID.class), null,
                rs.getString("permission_level"), "授权主体已不存在，无法解释实际授权来源。"
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
