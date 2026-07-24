package com.colla.platform.config.runtime;

import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class RuntimeRoleCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var attributes = metadata.getAnnotationAttributes(ConditionalOnRuntimeRole.class.getName());
        RuntimeRole configured;
        try {
            configured = RuntimeRole.parse(context.getEnvironment().getProperty("colla.runtime.role"));
        } catch (IllegalArgumentException exception) {
            return ConditionOutcome.noMatch(exception.getMessage());
        }
        RuntimeRole[] accepted = (RuntimeRole[]) attributes.get("value");
        boolean matches = Arrays.asList(accepted).contains(configured);
        return matches
            ? ConditionOutcome.match("runtime role is " + configured.value())
            : ConditionOutcome.noMatch("runtime role " + configured.value() + " is not accepted");
    }
}
