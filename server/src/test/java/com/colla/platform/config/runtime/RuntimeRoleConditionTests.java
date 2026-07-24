package com.colla.platform.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RuntimeRoleConditionTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
        .withUserConfiguration(RuntimeRoleConfiguration.class, MatrixConfiguration.class);

    @Test
    void productionRolesExposeOnlyTheirDeclaredBeanBoundaries() {
        Map<String, String> expected = Map.of(
            "api", "api",
            "worker", "worker",
            "event-gateway", "gateway",
            "maintenance", "maintenance"
        );
        expected.forEach((role, beanName) -> runner
            .withPropertyValues("colla.runtime.role=" + role)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean(beanName);
                assertThat(context).doesNotHaveBean(
                    switch (beanName) {
                        case "api" -> "worker";
                        case "worker" -> "gateway";
                        default -> "api";
                    }
                );
            }));
    }

    @Test
    void combinedIsExplicitlyAvailableForTestProfile() {
        runner.withPropertyValues("colla.runtime.role=combined").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("api");
            assertThat(context).hasBean("worker");
            assertThat(context).hasBean("gateway");
            assertThat(context).hasBean("maintenance");
        });
    }

    @Test
    void combinedIsRejectedOutsideLocalAndTest() {
        new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
            .withUserConfiguration(RuntimeRoleConfiguration.class, MatrixConfiguration.class)
            .withPropertyValues("colla.runtime.role=combined")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingUnknownAndCombinedValuesFailClosed() {
        new ApplicationContextRunner()
            .withUserConfiguration(RuntimeRoleConfiguration.class, MatrixConfiguration.class)
            .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("colla.runtime.role=api,worker")
            .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("colla.runtime.role=unknown")
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class MatrixConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean("api")
        @ConditionalOnRuntimeRole({RuntimeRole.API, RuntimeRole.COMBINED})
        Object api() {
            return new Object();
        }

        @Bean("worker")
        @ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.COMBINED})
        Object worker() {
            return new Object();
        }

        @Bean("gateway")
        @ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
        Object gateway() {
            return new Object();
        }

        @Bean("maintenance")
        @ConditionalOnRuntimeRole({RuntimeRole.MAINTENANCE, RuntimeRole.COMBINED})
        Object maintenance() {
            return new Object();
        }
    }
}
