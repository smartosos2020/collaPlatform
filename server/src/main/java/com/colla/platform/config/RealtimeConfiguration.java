package com.colla.platform.config;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.shared.realtime.RealtimeProperties;
import com.colla.platform.shared.realtime.RealtimeRedisHealthIndicator;
import com.colla.platform.shared.realtime.RedisRealtimeSignalSubscriber;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeConfiguration {
    @Bean
    InitializingBean realtimePropertiesValidator(RealtimeProperties properties) {
        return properties::validate;
    }

    @Bean("realtimeMessageExecutor")
    @ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
    TaskExecutor realtimeMessageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("realtime-redis-message-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Bean("realtimeSubscriptionExecutor")
    @ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
    TaskExecutor realtimeSubscriptionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("realtime-redis-subscription-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Bean("realtimeRedisListenerContainer")
    @ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
    @ConditionalOnProperty(
        prefix = "colla.realtime",
        name = "redis-listener-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    RedisMessageListenerContainer realtimeRedisListenerContainer(
        RedisConnectionFactory connectionFactory,
        RedisRealtimeSignalSubscriber subscriber,
        RealtimeProperties properties,
        @Qualifier("realtimeMessageExecutor") TaskExecutor realtimeMessageExecutor,
        @Qualifier("realtimeSubscriptionExecutor") TaskExecutor realtimeSubscriptionExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(realtimeMessageExecutor);
        container.setSubscriptionExecutor(realtimeSubscriptionExecutor);
        container.setRecoveryInterval(2_000);
        container.setErrorHandler(subscriber::recordRedisFailure);
        container.addMessageListener(subscriber, new ChannelTopic(properties.getChannel()));
        return container;
    }

    @Bean("realtimeRedisReadiness")
    RealtimeRedisHealthIndicator realtimeRedisHealthIndicator(
        ObjectProvider<RedisConnectionFactory> connectionFactory,
        @Qualifier("realtimeRedisListenerContainer")
        ObjectProvider<RedisMessageListenerContainer> realtimeRedisListenerContainer,
        RealtimeProperties properties,
        RuntimeRoleProperties runtimeRoleProperties
    ) {
        return new RealtimeRedisHealthIndicator(
            connectionFactory.getIfAvailable(),
            realtimeRedisListenerContainer.getIfAvailable(),
            properties,
            runtimeRoleProperties.role()
        );
    }
}
