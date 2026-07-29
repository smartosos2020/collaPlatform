package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioComponent;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioManifest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class ScenarioTemplateCatalog {
    public static final String CATALOG_VERSION = "s20.1";

    public List<CatalogTemplate> templates() {
        return List.of(development(), marketing(), humanResources(), delivery());
    }

    private CatalogTemplate marketing() {
        List<ScenarioComponent> components = List.of(
            type("type.campaign", "scenario-marketing-campaign", "市场活动"),
            type("type.content", "scenario-marketing-content", "营销内容"),
            type("type.asset", "scenario-marketing-asset", "创意素材（只保存文件公共引用）"),
            type("type.channel", "scenario-marketing-channel", "发布渠道"),
            type("type.placement", "scenario-marketing-placement", "投放计划"),
            type("type.review", "scenario-marketing-review", "活动复盘"),
            component("workflow.content_review", "workflow",
                "WorkItemNodeFlowService", "",
                List.of("type.content", "type.asset"), "内容评审与素材审批流程"),
            component("workflow.channel_publish", "workflow",
                "WorkItemStateFlowService", "",
                List.of("type.channel", "type.placement"), "渠道检查、发布与活动关闭流程"),
            component("relation.campaign_content", "relation",
                "WorkItemRelationDefinitionService", "",
                List.of("type.campaign", "type.content", "type.review"), "活动、内容与复盘追溯"),
            component("relation.distribution", "relation",
                "WorkItemRelationService", "",
                List.of("type.asset", "type.channel", "type.placement"), "素材、渠道与投放关系"),
            component("view.campaign_calendar", "calendar",
                "WorkItemCalendarService", "",
                List.of("type.campaign", "type.content", "type.placement"), "市场日历"),
            component("view.campaign_board", "board",
                "WorkItemBoardService", "",
                List.of("type.content", "type.asset", "type.placement"), "内容与投放看板"),
            component("automation.review_notify", "notification",
                "AutomationRuleService", "",
                List.of("workflow.content_review", "type.review"), "受控评审通知与提醒"),
            component("metric.campaign_review", "metric",
                "MetricSemanticService", "",
                List.of("type.campaign", "type.placement", "type.review"), "活动复盘指标"),
            component("dashboard.campaign_retrospective", "dashboard",
                "MetricDashboardService", "",
                List.of("metric.campaign_review", "view.campaign_calendar"), "活动复盘面板")
        );
        return new CatalogTemplate(
            "marketing",
            "市场活动",
            "活动、内容、素材、渠道、投放与复盘的版本化市场协作闭环。",
            new ScenarioManifest(
                1,
                "marketing",
                components,
                List.of(
                    "type_configuration",
                    "state_and_node_flow",
                    "relations",
                    "calendar_and_board",
                    "controlled_notification",
                    "versioned_metric_dashboard"
                ),
                List.of(
                    "private_table_access",
                    "arbitrary_sql",
                    "script",
                    "implicit_membership",
                    "permission_snapshot",
                    "file_content_copy",
                    "external_channel_credentials"
                )
            )
        );
    }

    private CatalogTemplate humanResources() {
        List<ScenarioComponent> components = List.of(
            type("type.hiring_plan", "scenario-hr-hiring-plan", "招聘计划"),
            type("type.position", "scenario-hr-position", "招聘职位"),
            type("type.candidate", "scenario-hr-candidate", "候选人（敏感字段默认受限）"),
            type("type.interview", "scenario-hr-interview", "面试与受限评价"),
            type("type.offer", "scenario-hr-offer", "Offer"),
            type("type.onboarding", "scenario-hr-onboarding", "入职检查"),
            component("workflow.position_approval", "workflow",
                "WorkItemNodeFlowService", "",
                List.of("type.hiring_plan", "type.position"), "职位审批流程"),
            component("workflow.candidate_stage", "workflow",
                "WorkItemStateFlowService", "",
                List.of("type.candidate"), "候选人阶段流程（不扩大字段可见范围）"),
            component("workflow.interview_offer", "workflow",
                "WorkItemNodeFlowService", "",
                List.of("type.interview", "type.offer", "type.onboarding"), "面试会签、Offer 与入职流程"),
            component("relation.hiring_trace", "relation",
                "WorkItemRelationDefinitionService", "",
                List.of("type.hiring_plan", "type.position", "type.candidate"), "计划、职位与候选人最小追溯"),
            component("relation.candidate_interview", "relation",
                "WorkItemRelationService", "",
                List.of("type.candidate", "type.interview", "type.offer"), "候选人、面试与 Offer 最小引用"),
            component("view.hiring_board", "board",
                "WorkItemBoardService", "",
                List.of("type.position", "type.candidate"), "受限招聘看板"),
            component("view.interview_calendar", "calendar",
                "WorkItemCalendarService", "",
                List.of("type.interview"), "受限面试日历"),
            component("view.recruiter_tasks", "saved_view",
                "WorkItemSavedViewService", "",
                List.of("type.position", "type.interview", "type.offer"), "招聘待办视图"),
            component("automation.interview_notify", "notification",
                "AutomationRuleService", "",
                List.of("type.interview", "type.offer", "type.onboarding"), "执行时重校准接收者的受控提醒"),
            component("metric.hiring_pipeline", "metric",
                "MetricSemanticService", "",
                List.of("type.position", "type.candidate"), "受限招聘漏斗指标（禁止个人排名）")
        );
        return new CatalogTemplate(
            "human-resources",
            "HR 招聘",
            "招聘计划、职位、候选人、面试、Offer 与入职的隐私优先协作闭环。",
            new ScenarioManifest(
                1,
                "human-resources",
                components,
                List.of(
                    "restricted_field_configuration",
                    "state_and_node_flow",
                    "minimal_relations",
                    "permission_scoped_views",
                    "controlled_notification",
                    "suppressed_aggregate_metric"
                ),
                List.of(
                    "private_table_access",
                    "arbitrary_sql",
                    "script",
                    "candidate_pii_in_catalog",
                    "candidate_evaluation_in_diagnostic",
                    "hidden_candidate_count",
                    "personal_ranking",
                    "interviewer_performance"
                )
            )
        );
    }

    private CatalogTemplate delivery() {
        List<ScenarioComponent> components = List.of(
            type("type.delivery_project", "scenario-delivery-project", "客户交付项目"),
            type("type.delivery_task", "scenario-delivery-task", "交付任务"),
            type("type.delivery_risk", "scenario-delivery-risk", "交付风险"),
            type("type.deliverable", "scenario-delivery-deliverable", "交付物（只保存文件公共引用）"),
            type("type.delivery_review", "scenario-delivery-review", "交付评审"),
            type("type.acceptance", "scenario-delivery-acceptance", "客户验收（只保存验收公共引用）"),
            component("workflow.delivery_stage", "workflow",
                "WorkItemStateFlowService", "",
                List.of("type.delivery_project", "type.delivery_task"), "交付阶段与里程碑流程"),
            component("workflow.review_remediation", "workflow",
                "WorkItemNodeFlowService", "",
                List.of("type.deliverable", "type.delivery_review"), "交付物评审与整改流程"),
            component("workflow.acceptance_close", "workflow",
                "WorkItemNodeFlowService", "",
                List.of("type.acceptance", "type.delivery_project"), "验收、签署引用与关闭流程"),
            component("relation.delivery_traceability", "relation",
                "WorkItemRelationDefinitionService", "",
                List.of("type.delivery_project", "type.delivery_task", "type.deliverable", "type.acceptance"),
                "项目、任务、交付物与验收追溯"),
            component("relation.risk_impact", "relation",
                "WorkItemRelationService", "",
                List.of("type.delivery_risk", "type.delivery_task", "type.deliverable"), "风险影响关系"),
            component("plan.delivery_timeline", "project_plan",
                "ProjectPlanService", "",
                List.of("type.delivery_project", "type.delivery_task"), "交付计划与甘特时间线"),
            component("view.delivery_register", "saved_view",
                "WorkItemSavedViewService", "",
                List.of("type.deliverable", "type.delivery_review", "type.acceptance"), "交付台账与审计视图"),
            component("automation.delivery_notify", "notification",
                "AutomationRuleService", "",
                List.of("type.delivery_task", "type.delivery_review", "type.acceptance"), "到期、评审与验收受控通知"),
            component("risk.delivery_governance", "risk_policy",
                "MetricRiskService", "",
                List.of("type.delivery_risk", "type.delivery_task"), "交付风险升级策略"),
            component("dashboard.delivery_governance", "dashboard",
                "MetricGovernanceService", "",
                List.of("plan.delivery_timeline", "view.delivery_register", "risk.delivery_governance"),
                "交付治理与追溯面板")
        );
        return new CatalogTemplate(
            "delivery",
            "客户交付",
            "项目、任务、风险、交付物、评审与验收的可追溯交付闭环。",
            new ScenarioManifest(
                1,
                "delivery",
                components,
                List.of(
                    "type_configuration",
                    "state_and_node_flow",
                    "traceable_relations",
                    "project_plan_and_gantt",
                    "controlled_notification",
                    "risk_and_governance"
                ),
                List.of(
                    "private_table_access",
                    "arbitrary_sql",
                    "script",
                    "file_content_copy",
                    "acceptance_evidence_copy",
                    "signature_emulation",
                    "customer_content_in_diagnostic",
                    "implicit_delivery_success"
                )
            )
        );
    }

    private CatalogTemplate development() {
        List<ScenarioComponent> components = List.of(
            type("type.project", "platform-project", "研发项目"),
            type("type.requirement", "platform-requirement", "需求"),
            type("type.task", "platform-task", "任务"),
            type("type.bug", "platform-bug", "缺陷"),
            type("type.version", "platform-version", "版本"),
            type("type.iteration", "platform-iteration", "迭代"),
            component("relation.delivery_hierarchy", "relation",
                "WorkItemRelationDefinitionService", "",
                List.of("type.project", "type.requirement", "type.task"), "研发交付层级"),
            component("relation.defect_trace", "relation",
                "WorkItemRelationService", "",
                List.of("type.requirement", "type.bug", "type.version"), "缺陷追溯关系"),
            component("view.backlog", "saved_view",
                "WorkItemSavedViewService", "",
                List.of("type.requirement", "type.task"), "需求与任务待办视图"),
            component("view.delivery_board", "board",
                "WorkItemBoardService", "",
                List.of("type.task", "type.bug", "type.iteration"), "交付看板"),
            component("plan.roadmap", "project_plan",
                "ProjectPlanService", "",
                List.of("type.project", "type.version", "type.iteration"), "版本与迭代路线图"),
            component("automation.defect_triage", "automation",
                "AutomationRuleService", "",
                List.of("type.bug"), "缺陷分诊提醒"),
            component("metric.delivery_health", "metric",
                "MetricSemanticService", "",
                List.of("type.task", "type.bug", "type.version"), "交付健康指标")
        );
        return new CatalogTemplate(
            "development",
            "研发项目",
            "项目、需求、任务、缺陷、版本与迭代的可配置研发闭环。",
            new ScenarioManifest(
                1,
                "development",
                components,
                List.of(
                    "type_configuration",
                    "state_and_node_flow",
                    "relations",
                    "saved_views",
                    "board",
                    "project_plan",
                    "controlled_automation",
                    "versioned_metric"
                ),
                List.of(
                    "private_table_access",
                    "arbitrary_sql",
                    "script",
                    "implicit_membership",
                    "permission_snapshot",
                    "legacy_write_cutover"
                )
            )
        );
    }

    private ScenarioComponent type(String key, String templateKey, String description) {
        return component(key, "work_item_type", "WorkItemConfigurationTemplateService",
            templateKey, List.of(), description);
    }

    private ScenarioComponent component(
        String key,
        String kind,
        String owner,
        String templateKey,
        List<String> dependencies,
        String description
    ) {
        return new ScenarioComponent(
            key, kind, owner, templateKey, dependencies, true, description
        );
    }

    public record CatalogTemplate(
        String scenarioKey,
        String name,
        String description,
        ScenarioManifest manifest
    ) {
    }
}
