package com.colla.platform.config.runtime;

import java.util.ArrayList;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@Configuration(proxyBeanMethods = false)
public class RuntimeRoleWebBoundaryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, PriorityOrdered {
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        RuntimeRole role = RuntimeRole.parse(environment.getProperty("colla.runtime.role"));
        if (role == RuntimeRole.API || role == RuntimeRole.COMBINED) {
            return;
        }
        var metadataReaderFactory = new CachingMetadataReaderFactory();
        for (String beanName : new ArrayList<>(java.util.List.of(registry.getBeanDefinitionNames()))) {
            BeanDefinition definition = registry.getBeanDefinition(beanName);
            String className = definition.getBeanClassName();
            if (className == null || !className.startsWith("com.colla.platform.modules.")) {
                continue;
            }
            try {
                var metadata = metadataReaderFactory.getMetadataReader(className).getAnnotationMetadata();
                if (metadata.hasAnnotation(RestController.class.getName()) || metadata.hasAnnotation(Controller.class.getName())) {
                    registry.removeBeanDefinition(beanName);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot inspect runtime web boundary for " + className, exception);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
