package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.MetricSemanticModels.Dimension;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricDefinition;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricExpression;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricSample;
import com.colla.platform.modules.project.domain.MetricSemanticModels.PreviewMetricCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.SaveMetricCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.Window;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.MetricSemanticRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MetricSemanticServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void resolvesFixedCalendarDayAcrossDstWithoutAssumingTwentyFourHours() {
        MetricSemanticService service = service(mock(MetricSemanticRepository.class));
        Window window = new Window(
            1, "fixed", 1, "day", "America/New_York", "iso8601", "previous_period"
        );

        var bounds = service.resolve(window, Instant.parse("2026-03-08T16:00:00Z"));

        assertThat(Duration.between(bounds.startInclusive(), bounds.endExclusive()))
            .isEqualTo(Duration.ofHours(23));
        assertThat(bounds.comparisonStartInclusive()).isNotNull();
        assertThat(bounds.diagnostic()).contains("DST");
    }

    @Test
    void rejectsPrivateTableOrExecutableExpressionBeforePersistence() {
        MetricSemanticRepository repository = mock(MetricSemanticRepository.class);
        when(repository.dimensions(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(new Dimension(
                "status", 1, "状态", "string",
                "WorkItemQueryService.authorizedFacet", 32
            )));
        MetricSemanticService service = service(repository);
        CurrentUser user = user();
        SaveMetricCommand command = new SaveMetricCommand(
            1, "metric-invalid-01", null, 0, "private.sql", "非法指标", "",
            "count", new MetricExpression(
                1, "sum", "select project_work_items", null, null, List.of("status")
            ), window()
        );

        assertThatThrownBy(() -> service.save(user, UUID.randomUUID(), command))
            .isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("registered measure");
        verify(repository, never()).save(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void excludesUnauthorizedSamplesAndKeepsNoSampleExplicit() {
        MetricSemanticRepository repository = mock(MetricSemanticRepository.class);
        UUID spaceId = UUID.randomUUID();
        UUID metricId = UUID.randomUUID();
        CurrentUser user = user();
        MetricDefinition metric = metric(metricId);
        when(repository.find(user.workspaceId(), spaceId, metricId))
            .thenReturn(Optional.of(metric));
        MetricSemanticService service = service(repository);

        var result = service.preview(user, spaceId, metricId, new PreviewMetricCommand(
            1, Instant.parse("2026-07-28T12:00:00Z"),
            List.of(new MetricSample(
                Instant.parse("2026-07-28T10:00:00Z"), BigDecimal.TEN, null, null,
                Map.of("status", "active"), "hidden-item", 1,
                false, false, false, false
            ))
        ));

        assertThat(result.status()).isEqualTo("no_sample");
        assertThat(result.value()).isNull();
        assertThat(result.sampleCount()).isZero();
        assertThat(result.sources().getFirst().diagnostic()).contains("excluded");
    }

    @Test
    void neverCoercesSuppressedStaleOrTruncatedToZero() {
        MetricSemanticRepository repository = mock(MetricSemanticRepository.class);
        UUID spaceId = UUID.randomUUID();
        UUID metricId = UUID.randomUUID();
        CurrentUser user = user();
        when(repository.find(user.workspaceId(), spaceId, metricId))
            .thenReturn(Optional.of(metric(metricId)));
        MetricSemanticService service = service(repository);
        Instant sampleAt = Instant.parse("2026-07-28T10:00:00Z");

        for (var expected : List.of("suppressed", "stale", "truncated")) {
            boolean suppressed = expected.equals("suppressed");
            boolean stale = expected.equals("stale");
            boolean truncated = expected.equals("truncated");
            var result = service.preview(user, spaceId, metricId, new PreviewMetricCommand(
                1, Instant.parse("2026-07-28T12:00:00Z"),
                List.of(new MetricSample(
                    sampleAt, BigDecimal.TEN, null, null, Map.of(), "source", 1,
                    true, suppressed, stale, truncated
                ))
            ));
            assertThat(result.status()).isEqualTo(expected);
            assertThat(result.value()).isNull();
        }
    }

    @Test
    void keepsPreviewAtTheDeclaredFiveHundredSamplePort() {
        MetricSemanticRepository repository = mock(MetricSemanticRepository.class);
        UUID spaceId = UUID.randomUUID();
        UUID metricId = UUID.randomUUID();
        CurrentUser user = user();
        when(repository.find(user.workspaceId(), spaceId, metricId))
            .thenReturn(Optional.of(metric(metricId)));
        MetricSemanticService service = service(repository);
        List<MetricSample> samples = IntStream.range(0, 500)
            .mapToObj(index -> new MetricSample(
                Instant.parse("2026-07-28T10:00:00Z"), BigDecimal.ONE, null, null,
                Map.of("status", index % 2 == 0 ? "active" : "done"),
                "item-" + index, index + 1L, true, false, false, false
            )).toList();

        var result = service.preview(user, spaceId, metricId, new PreviewMetricCommand(
            1, Instant.parse("2026-07-28T12:00:00Z"), samples
        ));
        assertThat(result.status()).isEqualTo("ready");
        assertThat(result.value()).isEqualByComparingTo("500");
        assertThat(result.sampleCount()).isEqualTo(500);

        assertThatThrownBy(() -> service.preview(
            user, spaceId, metricId,
            new PreviewMetricCommand(
                1, Instant.parse("2026-07-28T12:00:00Z"),
                java.util.stream.Stream.concat(samples.stream(), samples.stream().limit(1)).toList()
            )
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("exceeds");
    }

    private static MetricSemanticService service(MetricSemanticRepository repository) {
        return new MetricSemanticService(
            repository, mock(WorkItemRelationAccessDecisionService.class),
            mock(AuditLog.class), mock(TransactionalOutbox.class), JSON
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "owner", "Owner", Set.of(), Set.of()
        );
    }

    private static Window window() {
        return new Window(1, "rolling", 30, "day", "Asia/Shanghai", "iso8601", "none");
    }

    private static MetricDefinition metric(UUID metricId) {
        return new MetricDefinition(
            metricId, "work_item.total", "工作项总量", "当前受权总量", "count",
            "draft", 1,
            new MetricExpression(1, "count", "work_item.count", null, null, List.of("status")),
            window(), null, Instant.parse("2026-07-28T00:00:00Z")
        );
    }
}
