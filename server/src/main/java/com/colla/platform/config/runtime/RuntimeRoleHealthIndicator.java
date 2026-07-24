package com.colla.platform.config.runtime;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("runtimeRoleReadiness")
public class RuntimeRoleHealthIndicator implements HealthIndicator {
    private final RuntimeRoleProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public RuntimeRoleHealthIndicator(RuntimeRoleProperties properties, DataSource dataSource) {
        this(properties, new JdbcTemplate(dataSource));
    }

    RuntimeRoleHealthIndicator(RuntimeRoleProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        RuntimeRole role = properties.role();
        if (role == RuntimeRole.MAINTENANCE) {
            return Health.outOfService()
                .withDetail("role", role.value())
                .withDetail("traffic", "disabled")
                .build();
        }
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return Health.up()
                .withDetail("role", role.value())
                .withDetail("instanceId", properties.getInstanceId())
                .withDetail("postgresql", "up")
                .build();
        } catch (RuntimeException exception) {
            return Health.down()
                .withDetail("role", role.value())
                .withDetail("instanceId", properties.getInstanceId())
                .withDetail("postgresql", "down")
                .build();
        }
    }
}
