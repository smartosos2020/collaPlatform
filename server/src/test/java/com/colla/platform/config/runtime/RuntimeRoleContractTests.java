package com.colla.platform.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimeRoleContractTests {
    @Test
    void parsesTheFourProductionRolesAndCombined() {
        assertThat(RuntimeRole.parse("api")).isEqualTo(RuntimeRole.API);
        assertThat(RuntimeRole.parse("worker")).isEqualTo(RuntimeRole.WORKER);
        assertThat(RuntimeRole.parse("event-gateway")).isEqualTo(RuntimeRole.EVENT_GATEWAY);
        assertThat(RuntimeRole.parse("event_gateway")).isEqualTo(RuntimeRole.EVENT_GATEWAY);
        assertThat(RuntimeRole.parse("maintenance")).isEqualTo(RuntimeRole.MAINTENANCE);
        assertThat(RuntimeRole.parse("combined")).isEqualTo(RuntimeRole.COMBINED);
    }

    @Test
    void rejectsMissingUnknownAndMultipleRoles() {
        assertThatThrownBy(() -> RuntimeRole.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuntimeRole.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuntimeRole.parse("api,worker")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuntimeRole.parse("scheduler")).isInstanceOf(IllegalArgumentException.class);
    }
}
