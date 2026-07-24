package com.colla.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.shared.realtime.RealtimeRedisHealthIndicator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class RealtimeRoleReadinessContextTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RoleReadinessConfiguration.class)
        .withPropertyValues("colla.realtime.redis-listener-enabled=false");

    @Test
    void apiAndWorkerAlwaysExposeLowCostNotApplicableContributor() {
        Stream.of(RuntimeRole.API, RuntimeRole.WORKER).forEach(role ->
            runner.withPropertyValues("colla.runtime.role=" + role.value()).run(context -> {
                assertThat(context).hasSingleBean(RealtimeRedisHealthIndicator.class);
                var health = context.getBean("realtimeRedisReadiness", RealtimeRedisHealthIndicator.class).health();
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails())
                    .containsEntry("realtimeRedis", "not_applicable")
                    .containsEntry("runtimeRole", role.value());
            })
        );
    }

    @Test
    void gatewayAndCombinedRequireConfiguredRedisSubscriber() {
        Stream.of(RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED).forEach(role ->
            runner.withPropertyValues("colla.runtime.role=" + role.value()).run(context -> {
                assertThat(context).hasSingleBean(RealtimeRedisHealthIndicator.class);
                var health = context.getBean("realtimeRedisReadiness", RealtimeRedisHealthIndicator.class).health();
                assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                assertThat(health.getDetails())
                    .containsEntry("redis", "subscriber_not_configured")
                    .containsEntry("runtimeRole", role.value());
            })
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RuntimeRoleProperties.class)
    @Import(RealtimeConfiguration.class)
    static class RoleReadinessConfiguration {
    }
}
