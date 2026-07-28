package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.AutomationScheduleModels;
import java.time.Instant;
import java.time.zone.ZoneRulesException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationScheduleModelsTests {
    @Test
    void fixedTimeUsesIanaTimezoneAndMovesAcrossDstDeterministically() {
        Instant beforeDst = Instant.parse("2026-03-28T23:30:00Z");
        Instant next = AutomationScheduleModels.nextFixedTime(
            beforeDst, "Europe/Berlin", 9, 0
        );
        assertThat(next).isEqualTo(Instant.parse("2026-03-29T07:00:00Z"));
    }

    @Test
    void rejectsUnknownTimezone() {
        assertThatThrownBy(() -> new AutomationScheduleModels.ScheduleTrigger(
            UUID.randomUUID(), UUID.randomUUID(), 1, "fixed_time",
            "Mars/Olympus", "09:00", "latest", 300, "active", 1,
            Instant.now(), Instant.now(), 1
        )).isInstanceOf(ZoneRulesException.class);
    }
}
