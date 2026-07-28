package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ChartDefinition;
import com.colla.platform.modules.project.domain.MetricDashboardModels.Dashboard;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardConfig;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardQueryResult;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardVersion;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DataSourceBinding;
import com.colla.platform.modules.project.domain.MetricDashboardModels.Layout;
import com.colla.platform.modules.project.domain.MetricDashboardModels.QueryDashboardCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ResolvedSource;
import com.colla.platform.modules.project.domain.MetricDashboardModels.SaveDashboardCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricResult;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricSample;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.MetricDashboardRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetricDashboardServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void rejectsChartAndBindingMetricVersionMismatchBeforePersistence() {
        Fixture fixture = fixture();
        DashboardConfig config = config(
            fixture.spaceId,
            fixture.metricId,
            1,
            2,
            "metric_card"
        );
        SaveDashboardCommand command = new SaveDashboardCommand(
            1,
            "dashboard-save-01",
            null,
            0,
            "delivery.dashboard",
            "交付驾驶舱",
            "当前受权事实",
            config
        );

        assertThatThrownBy(() -> fixture.service.save(
            fixture.user, fixture.spaceId, command
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("same immutable metric version");
        verify(fixture.repository, never()).save(
            any(), any(), any(), any(), any(), any(), any(), any(),
            any(Long.class), any(), any()
        );
    }

    @Test
    void hidesSeriesFacetsCountsAndVersionsWhenEvidenceIsTruncated() {
        Fixture fixture = fixture();
        Dashboard dashboard = dashboard(
            fixture.spaceId, fixture.metricId, "line"
        );
        when(fixture.repository.find(
            fixture.user.workspaceId(), fixture.spaceId, dashboard.id()
        )).thenReturn(Optional.of(dashboard));
        when(fixture.sources.resolve(any(), eq(fixture.spaceId), any()))
            .thenReturn(new ResolvedSource(
                samples(4),
                List.of("WorkItemQueryService.execute:source-v1"),
                false,
                true,
                "bounded"
            ));
        when(fixture.metrics.evaluatePublished(
            any(), eq(fixture.spaceId), eq(fixture.metricId), eq(1), any(), anyList()
        )).thenAnswer(invocation -> result(
            invocation.getArgument(5, List.class).size(),
            "ready",
            false
        ));

        DashboardQueryResult result = fixture.service.query(
            fixture.user,
            fixture.spaceId,
            dashboard.id(),
            new QueryDashboardCommand(1, Instant.parse("2026-07-28T12:00:00Z"), Map.of())
        );

        assertThat(result.status()).isEqualTo("truncated");
        assertThat(result.charts().getFirst().series()).isEmpty();
        assertThat(result.charts().getFirst().facets()).isEmpty();
        assertThat(result.charts().getFirst().visibleSampleCount()).isZero();
        assertThat(result.charts().getFirst().sourceVersions()).isEmpty();
    }

    @Test
    void buildsBoundedAuthorizedSeriesForAllRegisteredVisualizations() {
        for (String visualization : List.of(
            "table", "metric_card", "line", "bar", "stacked_bar", "distribution"
        )) {
            Fixture fixture = fixture();
            Dashboard dashboard = dashboard(
                fixture.spaceId, fixture.metricId, visualization
            );
            when(fixture.repository.find(
                fixture.user.workspaceId(), fixture.spaceId, dashboard.id()
            )).thenReturn(Optional.of(dashboard));
            when(fixture.sources.resolve(any(), eq(fixture.spaceId), any()))
                .thenReturn(new ResolvedSource(
                    samples(6), List.of("source@1"), false, false, "authorized"
                ));
            when(fixture.metrics.evaluatePublished(
                any(), eq(fixture.spaceId), eq(fixture.metricId), eq(1), any(), anyList()
            )).thenAnswer(invocation -> result(
                invocation.getArgument(5, List.class).size(),
                "ready",
                false
            ));

            var result = fixture.service.query(
                fixture.user,
                fixture.spaceId,
                dashboard.id(),
                new QueryDashboardCommand(
                    1, Instant.parse("2026-07-28T12:00:00Z"), Map.of()
                )
            );

            assertThat(result.charts().getFirst().visualization())
                .isEqualTo(visualization);
            assertThat(result.charts().getFirst().visibleSampleCount()).isEqualTo(6);
            assertThat(result.charts().getFirst().series()).hasSize(2);
        }
    }

    @Test
    void rejectsTwentyFifthChartAtTheDeclaredPort() {
        Fixture fixture = fixture();
        DashboardConfig base = config(
            fixture.spaceId, fixture.metricId, 1, 1, "bar"
        );
        List<ChartDefinition> charts = java.util.stream.IntStream.range(0, 25)
            .mapToObj(index -> new ChartDefinition(
                null,
                "chart." + index,
                "图表 " + index,
                "bar",
                "work.items",
                fixture.metricId,
                1,
                List.of("status"),
                Map.of(),
                10,
                50,
                true,
                0
            )).toList();
        SaveDashboardCommand command = new SaveDashboardCommand(
            1,
            "dashboard-bound-01",
            null,
            0,
            "bounded.dashboard",
            "有界驾驶舱",
            "",
            new DashboardConfig(1, base.dataSources(), charts, List.of(), List.of())
        );

        assertThatThrownBy(() -> fixture.service.save(
            fixture.user, fixture.spaceId, command
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");
    }

    private static DashboardConfig config(
        UUID spaceId,
        UUID metricId,
        int bindingVersion,
        int chartVersion,
        String visualization
    ) {
        DataSourceBinding binding = new DataSourceBinding(
            1,
            "work.items",
            "work_item_query",
            List.of(spaceId),
            null,
            metricId,
            bindingVersion
        );
        ChartDefinition chart = new ChartDefinition(
            UUID.randomUUID(),
            "status.chart",
            "状态分布",
            visualization,
            binding.bindingKey(),
            metricId,
            chartVersion,
            List.of("status"),
            Map.of(),
            10,
            50,
            true,
            1
        );
        return new DashboardConfig(
            1,
            List.of(binding),
            List.of(chart),
            List.of(new Layout(chart.chartKey(), 0, 0, 6, 4)),
            List.of()
        );
    }

    private static Dashboard dashboard(
        UUID spaceId,
        UUID metricId,
        String visualization
    ) {
        UUID id = UUID.randomUUID();
        DashboardConfig config = config(spaceId, metricId, 1, 1, visualization);
        DashboardVersion version = new DashboardVersion(
            UUID.randomUUID(),
            id,
            1,
            "a".repeat(64),
            config,
            Instant.parse("2026-07-28T00:00:00Z"),
            UUID.randomUUID()
        );
        return new Dashboard(
            id,
            "delivery.dashboard",
            "交付驾驶舱",
            "",
            "active",
            "space",
            2,
            config,
            version,
            Instant.parse("2026-07-28T00:00:00Z")
        );
    }

    private static List<MetricSample> samples(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(index ->
            new MetricSample(
                Instant.parse("2026-07-28T10:00:00Z"),
                BigDecimal.ONE,
                null,
                null,
                Map.of("status", index % 2 == 0 ? "active" : "done"),
                "item-" + index,
                index + 1L,
                true,
                false,
                false,
                false
            )
        ).toList();
    }

    private static MetricResult result(int samples, String status, boolean truncated) {
        return new MetricResult(
            1,
            UUID.randomUUID(),
            1,
            status,
            "ready".equals(status) ? BigDecimal.valueOf(samples) : null,
            null,
            null,
            "count",
            null,
            List.of(),
            samples,
            false,
            false,
            truncated,
            Instant.parse("2026-07-28T12:00:00Z"),
            "test"
        );
    }

    private static Fixture fixture() {
        MetricDashboardRepository repository = mock(MetricDashboardRepository.class);
        MetricDataSourceResolver sources = mock(MetricDataSourceResolver.class);
        MetricSemanticService metrics = mock(MetricSemanticService.class);
        WorkItemRelationAccessDecisionService access =
            mock(WorkItemRelationAccessDecisionService.class);
        MetricDashboardService service = new MetricDashboardService(
            repository,
            sources,
            metrics,
            access,
            mock(AuditLog.class),
            mock(TransactionalOutbox.class),
            JSON
        );
        return new Fixture(
            service,
            repository,
            sources,
            metrics,
            user(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "owner",
            "Owner",
            Set.of(),
            Set.of()
        );
    }

    private record Fixture(
        MetricDashboardService service,
        MetricDashboardRepository repository,
        MetricDataSourceResolver sources,
        MetricSemanticService metrics,
        CurrentUser user,
        UUID spaceId,
        UUID metricId
    ) {
    }
}
