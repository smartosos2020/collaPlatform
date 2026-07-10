package com.colla.platform.modules.admin.application;

import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminApplicationGovernanceService {
    private final JdbcTemplate jdbcTemplate;

    public AdminApplicationGovernanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminApplicationGovernanceView overview(CurrentUser currentUser) {
        return new AdminApplicationGovernanceView(List.of(
            baseGovernance(currentUser),
            projectGovernance(currentUser),
            messageGovernance(currentUser),
            approvalGovernance(currentUser)
        ));
    }

    private AdminApplicationModuleGovernance baseGovernance(CurrentUser currentUser) {
        long baseCount = count("select count(*) from bases where workspace_id = ? and archived_at is null", currentUser.workspaceId());
        long tableCount = count("""
            select count(*)
            from base_tables bt
            join bases b on b.id = bt.base_id
            where b.workspace_id = ? and b.archived_at is null and bt.archived_at is null
            """, currentUser.workspaceId());
        long recordCount = count("""
            select count(*)
            from base_records br
            join base_tables bt on bt.id = br.table_id
            join bases b on b.id = bt.base_id
            where b.workspace_id = ? and b.archived_at is null and bt.archived_at is null and br.deleted_at is null
            """, currentUser.workspaceId());
        return new AdminApplicationModuleGovernance(
            "base",
            "Base 治理",
            "多维表格",
            "数据协作保留在用户工作台；后台只管理空间权限、导入导出策略、字段风险和模板治理。",
            "/bases",
            "/admin/app-governance?module=base",
            new AdminApplicationMetrics(baseCount, tableCount, recordCount, "空间", "数据表", "记录"),
            List.of("base.import_export_policy", "base.template_governance", "base.field_type_risk", "base.permission_audit"),
            List.of(
                risk("medium", "导入导出策略未集中配置", "CSV 导入导出仍由用户侧动作触发，后台需要统一策略、大小限制和审计筛选。"),
                risk("low", "模板治理待产品化", "当前 Base 模板仍未形成后台发布、停用和审计视图。")
            ),
            List.of(
                link("权限排查", "/admin/permission-governance?resourceType=base"),
                link("审计日志", "/admin/audit-logs?targetType=base")
            ),
            List.of("用户侧入口只用于数据协作，不作为后台页面主体。", "后台深链只指向权限治理、审计和后续策略配置页。")
        );
    }

    private AdminApplicationModuleGovernance projectGovernance(CurrentUser currentUser) {
        long projectCount = count("select count(*) from projects where workspace_id = ? and archived_at is null", currentUser.workspaceId());
        long memberCount = count("select count(*) from project_members where workspace_id = ? and archived_at is null", currentUser.workspaceId());
        long openIssueCount = count("""
            select count(*)
            from issues
            where workspace_id = ? and deleted_at is null and status <> 'closed'
            """, currentUser.workspaceId());
        return new AdminApplicationModuleGovernance(
            "project",
            "项目治理",
            "项目与事项",
            "项目事项协作保留在用户工作台；后台只治理项目成员、权限策略、归档、模板和审计。",
            "/projects",
            "/admin/app-governance?module=project",
            new AdminApplicationMetrics(projectCount, memberCount, openIssueCount, "项目", "成员关系", "未关闭事项"),
            List.of("project.member_governance", "project.permission_policy", "project.archive_policy", "project.template_config"),
            List.of(
                risk("medium", "项目归档策略待后台化", "用户侧可继续处理事项，后台需要后续统一项目归档、保留和恢复策略。"),
                risk("low", "项目模板配置未接入", "项目类型、默认成员和事项模板仍缺少后台配置面。")
            ),
            List.of(
                link("权限排查", "/admin/permission-governance?resourceType=project"),
                link("审计日志", "/admin/audit-logs?targetType=project")
            ),
            List.of("用户对象卡只打开项目/事项协作页。", "后台治理深链只进入权限、审计和模板配置语义。")
        );
    }

    private AdminApplicationModuleGovernance messageGovernance(CurrentUser currentUser) {
        long conversationCount = count("select count(*) from conversations where workspace_id = ? and archived_at is null", currentUser.workspaceId());
        long memberCount = count("select count(*) from conversation_members where workspace_id = ? and archived_at is null", currentUser.workspaceId());
        long messageCount = count("select count(*) from messages where workspace_id = ? and deleted_at is null", currentUser.workspaceId());
        return new AdminApplicationModuleGovernance(
            "message",
            "消息治理",
            "消息与通知",
            "会话、消息和通知消费保留在用户工作台；后台治理留存策略、敏感词、转知识审计和通知策略。",
            "/im",
            "/admin/app-governance?module=message",
            new AdminApplicationMetrics(conversationCount, memberCount, messageCount, "会话", "会话成员", "消息"),
            List.of("message.retention_policy", "message.sensitive_word_policy", "message.convert_to_knowledge_audit", "notification_policy"),
            List.of(
                risk("medium", "留存策略未后台配置", "消息当前按协作记录保存，后续需要后台配置保留期和导出边界。"),
                risk("medium", "敏感词策略待接入", "消息发送路径尚无后台敏感词策略配置和命中审计面。")
            ),
            List.of(
                link("转知识审计", "/admin/audit-logs?action=message.converted_to_document"),
                link("通知审计", "/admin/audit-logs?targetType=notification")
            ),
            List.of("后台不展示用户 IM 会话列表作为主体。", "用户侧对象卡不暴露消息治理入口。")
        );
    }

    private AdminApplicationModuleGovernance approvalGovernance(CurrentUser currentUser) {
        long formCount = count("select count(*) from approval_forms where workspace_id = ? and archived_at is null", currentUser.workspaceId());
        long pendingCount = count("select count(*) from approval_instances where workspace_id = ? and status = 'pending'", currentUser.workspaceId());
        long taskCount = count("select count(*) from approval_tasks where workspace_id = ? and status = 'pending'", currentUser.workspaceId());
        return new AdminApplicationModuleGovernance(
            "approval",
            "审批治理",
            "审批",
            "审批发起和处理保留在用户工作台；后台治理流程模板、权限、审计和异常排查。",
            "/approvals",
            "/admin/app-governance?module=approval",
            new AdminApplicationMetrics(formCount, pendingCount, taskCount, "流程模板", "待完成实例", "待办任务"),
            List.of("approval.template_governance", "approval.permission_policy", "approval.exception_inspection", "approval.audit_review"),
            List.of(
                risk("medium", "流程模板缺少后台生命周期", "表单和节点目前可用，但发布、停用、版本和审批人策略需要后台治理页承载。"),
                risk("low", "异常排查视图待接入", "转交、撤回、超时和被拒原因后续应进入后台排查视角。")
            ),
            List.of(
                link("审批审计", "/admin/audit-logs?targetType=approval"),
                link("权限排查", "/admin/permission-governance?resourceType=approval")
            ),
            List.of("后台不复用审批发起/处理页面。", "用户侧审批卡只展示流程状态和处理动作。")
        );
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private AdminApplicationRisk risk(String severity, String title, String reason) {
        return new AdminApplicationRisk(severity, title, reason);
    }

    private AdminApplicationLink link(String label, String path) {
        return new AdminApplicationLink(label, path);
    }

    public record AdminApplicationGovernanceView(List<AdminApplicationModuleGovernance> modules) {
    }

    public record AdminApplicationModuleGovernance(
        String key,
        String title,
        String moduleName,
        String description,
        String userRoute,
        String adminRoute,
        AdminApplicationMetrics metrics,
        List<String> policies,
        List<AdminApplicationRisk> risks,
        List<AdminApplicationLink> adminLinks,
        List<String> boundaryRules
    ) {
    }

    public record AdminApplicationMetrics(
        long primary,
        long secondary,
        long tertiary,
        String primaryLabel,
        String secondaryLabel,
        String tertiaryLabel
    ) {
    }

    public record AdminApplicationRisk(String severity, String title, String reason) {
    }

    public record AdminApplicationLink(String label, String path) {
    }
}
