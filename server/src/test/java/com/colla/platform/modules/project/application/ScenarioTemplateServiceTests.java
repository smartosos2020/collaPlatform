package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioComponent;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioTemplateServiceTests {
    private final ScenarioTemplateService service = new ScenarioTemplateService(
        null, null, null, null, new ObjectMapper()
    );

    @Test
    void producesDeterministicTopologicalOrder() {
        var result = service.validate(new ScenarioManifest(
            1,
            "test_scenario",
            List.of(
                component("view.board", "board", List.of("type.task")),
                component("type.task", "work_item_type", List.of()),
                component("automation.notify", "automation", List.of("view.board"))
            ),
            List.of("board"),
            List.of("script")
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.installationOrder())
            .containsExactly("type.task", "view.board", "automation.notify");
        assertThat(result.manifestHash()).hasSize(64);
    }

    @Test
    void rejectsCyclesAndMissingDependencies() {
        var cycle = service.validate(new ScenarioManifest(
            1,
            "cycle_scenario",
            List.of(
                component("type.a", "work_item_type", List.of("type.b")),
                component("type.b", "work_item_type", List.of("type.a"))
            ),
            List.of(),
            List.of()
        ));
        var missing = service.validate(new ScenarioManifest(
            1,
            "missing_scenario",
            List.of(component("view.board", "board", List.of("type.hidden"))),
            List.of(),
            List.of()
        ));

        assertThat(cycle.valid()).isFalse();
        assertThat(cycle.diagnostics()).extracting("code")
            .containsOnly("SCENARIO_DEPENDENCY_CYCLE");
        assertThat(missing.valid()).isFalse();
        assertThat(missing.diagnostics()).extracting("code")
            .containsExactly("SCENARIO_DEPENDENCY_MISSING");
    }

    @Test
    void publishesBoundedDevelopmentAndMarketingCatalogs() {
        var catalog = new ScenarioTemplateCatalog();

        assertThat(catalog.templates()).extracting("scenarioKey")
            .startsWith("development", "marketing");
        var marketing = catalog.templates().get(1).manifest();
        assertThat(marketing.components()).hasSize(15);
        assertThat(marketing.components()).extracting("componentKey")
            .contains(
                "type.campaign", "type.content", "type.asset", "type.channel",
                "type.placement", "type.review", "workflow.content_review",
                "view.campaign_calendar", "automation.review_notify",
                "metric.campaign_review", "dashboard.campaign_retrospective"
            );
        assertThat(service.validate(marketing).valid()).isTrue();
        assertThat(marketing.prohibitedCapabilities())
            .contains("file_content_copy", "external_channel_credentials");
    }

    @Test
    void publishesPrivacyBoundedHumanResourcesCatalog() {
        var catalog = new ScenarioTemplateCatalog();

        assertThat(catalog.templates()).extracting("scenarioKey")
            .startsWith("development", "marketing", "human-resources");
        var hr = catalog.templates().get(2).manifest();
        assertThat(hr.components()).hasSize(16);
        assertThat(hr.components()).extracting("componentKey")
            .contains(
                "type.candidate", "type.interview", "type.offer",
                "workflow.candidate_stage", "relation.candidate_interview",
                "view.interview_calendar", "automation.interview_notify",
                "metric.hiring_pipeline"
            );
        assertThat(service.validate(hr).valid()).isTrue();
        assertThat(hr.prohibitedCapabilities())
            .contains(
                "candidate_pii_in_catalog", "candidate_evaluation_in_diagnostic",
                "hidden_candidate_count", "personal_ranking",
                "interviewer_performance"
            );
    }

    @Test
    void publishesTraceableDeliveryCatalog() {
        var catalog = new ScenarioTemplateCatalog();

        assertThat(catalog.templates()).extracting("scenarioKey")
            .containsExactly("development", "marketing", "human-resources", "delivery");
        var delivery = catalog.templates().get(3).manifest();
        assertThat(delivery.components()).hasSize(16);
        assertThat(delivery.components()).extracting("componentKey")
            .contains(
                "type.delivery_project", "type.deliverable", "type.acceptance",
                "workflow.review_remediation", "relation.delivery_traceability",
                "plan.delivery_timeline", "risk.delivery_governance",
                "dashboard.delivery_governance"
            );
        assertThat(service.validate(delivery).valid()).isTrue();
        assertThat(delivery.prohibitedCapabilities())
            .contains(
                "file_content_copy", "acceptance_evidence_copy",
                "signature_emulation", "implicit_delivery_success"
            );
    }

    private ScenarioComponent component(
        String key, String kind, List<String> dependencies
    ) {
        return new ScenarioComponent(
            key, kind, "OwnerService", "", dependencies, true, key
        );
    }
}
