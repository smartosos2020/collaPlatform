package com.colla.platform.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

class RuntimeRoleHealthIndicatorTests {
    @Test
    void productionConstructorIsExplicitlyMarkedForInjection() {
        Constructor<?> injectionConstructor = Arrays.stream(RuntimeRoleHealthIndicator.class.getConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .findFirst()
            .orElseThrow();

        assertThat(injectionConstructor.getParameterTypes())
            .containsExactly(RuntimeRoleProperties.class, javax.sql.DataSource.class);
    }

    @Test
    void apiReadinessTracksPostgresql() {
        RuntimeRoleProperties properties = properties("api");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

        var health = new RuntimeRoleHealthIndicator(properties, jdbcTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("role", "api").containsEntry("postgresql", "up");
    }

    @Test
    void databaseFailureMakesTrafficRoleNotReady() {
        RuntimeRoleProperties properties = properties("worker");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenThrow(new IllegalStateException("offline"));

        assertThat(new RuntimeRoleHealthIndicator(properties, jdbcTemplate).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void maintenanceNeverAdvertisesTrafficReadiness() {
        RuntimeRoleProperties properties = properties("maintenance");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        assertThat(new RuntimeRoleHealthIndicator(properties, jdbcTemplate).health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        verifyNoInteractions(jdbcTemplate);
    }

    private RuntimeRoleProperties properties(String role) {
        RuntimeRoleProperties properties = new RuntimeRoleProperties();
        properties.setRole(role);
        properties.setInstanceId("test-instance");
        return properties;
    }
}
