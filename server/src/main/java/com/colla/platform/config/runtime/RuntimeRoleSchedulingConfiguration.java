package com.colla.platform.config.runtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.COMBINED})
public class RuntimeRoleSchedulingConfiguration {
}
