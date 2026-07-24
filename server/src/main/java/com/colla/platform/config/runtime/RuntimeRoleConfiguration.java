package com.colla.platform.config.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RuntimeRoleProperties.class)
public class RuntimeRoleConfiguration {
    @Bean
    InitializingBean runtimeRoleValidator(RuntimeRoleProperties properties, Environment environment) {
        return () -> {
            RuntimeRole role = properties.role();
            boolean localOrTest = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
            if (role == RuntimeRole.COMBINED && !localOrTest) {
                throw new IllegalStateException("combined runtime role is restricted to local/test profiles");
            }
            if (properties.getRole().contains(",")) {
                throw new IllegalStateException("exactly one runtime role must be configured");
            }
        };
    }

    @Bean
    InfoContributor runtimeRoleInfoContributor(RuntimeRoleProperties properties) {
        return (Info.Builder builder) -> builder.withDetail("runtime", java.util.Map.of(
            "role", properties.role().value(),
            "instanceId", properties.getInstanceId(),
            "version", properties.getVersion(),
            "commit", properties.getCommit()
        ));
    }

    @Bean
    InitializingBean runtimeRoleMetricTags(RuntimeRoleProperties properties, MeterRegistry registry) {
        return () -> registry.config().commonTags(
            "runtime_role", properties.role().value(),
            "instance_id", properties.getInstanceId(),
            "version", properties.getVersion()
        );
    }
}
