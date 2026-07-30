package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ProjectSpaceExperienceRolloutPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class
        ))
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsScopedRolloutAndTelemetrySettings() {
        UUID workspaceId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        runner.withPropertyValues(
            "colla.project-space-experience.enabled=true",
            "colla.project-space-experience.kill-switch=false",
            "colla.project-space-experience.policy-version=s21-m7-canary",
            "colla.project-space-experience.rollout-basis-points=2500",
            "colla.project-space-experience.evaluation-salt=stable-test-salt",
            "colla.project-space-experience.included-workspace-ids[0]=" + workspaceId,
            "colla.project-space-experience.excluded-space-ids[0]=" + spaceId,
            "colla.project-space-experience.included-user-ids[0]=" + userId,
            "colla.project-space-experience.telemetry.enabled=true",
            "colla.project-space-experience.telemetry.sample-basis-points=1000",
            "colla.project-space-experience.telemetry.max-batch-size=12"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(ProjectSpaceExperienceRolloutProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getRolloutBasisPoints()).isEqualTo(2500);
            assertThat(properties.getIncludedWorkspaceIds()).containsExactly(workspaceId);
            assertThat(properties.getExcludedSpaceIds()).containsExactly(spaceId);
            assertThat(properties.getIncludedUserIds()).containsExactly(userId);
            assertThat(properties.getTelemetry().getSampleBasisPoints()).isEqualTo(1000);
            assertThat(properties.getTelemetry().getMaxBatchSize()).isEqualTo(12);
        });
    }

    @Test
    void rejectsOutOfRangeRolloutAndTelemetryBudgets() {
        runner.withPropertyValues(
            "colla.project-space-experience.rollout-basis-points=10001"
        ).run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
            "colla.project-space-experience.telemetry.max-batch-size=21"
        ).run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
            "colla.project-space-experience.telemetry.sample-basis-points=-1"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void defaultsFailClosedWhenNoDeploymentSettingsAreProvided() {
        runner.run(context -> {
            var properties = context.getBean(ProjectSpaceExperienceRolloutProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.isKillSwitch()).isTrue();
            assertThat(properties.getRolloutBasisPoints()).isZero();
            assertThat(properties.getTelemetry().isEnabled()).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProjectSpaceExperienceRolloutProperties.class)
    static class PropertiesConfiguration {
    }
}
