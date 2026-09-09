package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.repository.MetricsRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the metrics collection service behavior and regression cases. */
class MetricsCollectionServiceTest {

    /** Verifies classifies boundary and weekend samples into peak and off-peak buckets. */
    @Test
    void classifiesBoundaryAndWeekendSamplesIntoPeakAndOffPeakBuckets() {
        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.getPeakHoursConfig(anyString())).thenReturn(PeakHoursConfig.defaults());
        when(repo.getAllMetrics(anyString(), any(Duration.class))).thenReturn(List.of(
            point("2026-09-08T17:59:00Z", 50),
            point("2026-09-08T18:00:00Z", 10),
            point("2026-09-08T07:00:00Z", 60),
            point("2026-09-08T06:59:00Z", 5),
            point("2026-09-12T12:00:00Z", 20)
        ));

        MetricsCollectionService service = new MetricsCollectionService(repo);
        ResourceMetrics metrics = service.collectMetrics("aks-primary-cluster", 1);

        assertEquals(2, metrics.aggregated().peakSampleCount());
        assertEquals(3, metrics.aggregated().offPeakSampleCount());
        assertEquals(55.0, metrics.aggregated().peakHourUtilization(), 0.001);
        assertEquals(35.0 / 3.0, metrics.aggregated().offPeakHourUtilization(), 0.001);
    }

    /** Verifies classification honors non-zero minutes in peak window. */
    @Test
    void classificationHonorsNonZeroMinutesInPeakWindow() {
        PeakHoursConfig config = new PeakHoursConfig(
            LocalTime.of(6, 30), LocalTime.of(22, 30),
            List.of(1, 2, 3, 4, 5), 65.0, 10.0, 15
        );
        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.getPeakHoursConfig(anyString())).thenReturn(config);
        when(repo.getAllMetrics(anyString(), any(Duration.class))).thenReturn(List.of(
            point("2026-09-08T06:29:00Z", 10),
            point("2026-09-08T06:30:00Z", 20),
            point("2026-09-08T22:29:00Z", 30),
            point("2026-09-08T22:30:00Z", 40)
        ));

        MetricsCollectionService service = new MetricsCollectionService(repo);
        ResourceMetrics metrics = service.collectMetrics("aks-primary-cluster", 1);

        assertEquals(2, metrics.aggregated().peakSampleCount());
        assertEquals(2, metrics.aggregated().offPeakSampleCount());
        assertEquals(25.0, metrics.aggregated().peakHourUtilization(), 0.001);
        assertEquals(25.0, metrics.aggregated().offPeakHourUtilization(), 0.001);
    }

    /** Verifies classification handles window crossing midnight. */
    @Test
    void classificationHandlesWindowCrossingMidnight() {
        PeakHoursConfig config = new PeakHoursConfig(
            LocalTime.of(22, 0), LocalTime.of(6, 0),
            List.of(1, 2, 3, 4, 5), 65.0, 10.0, 15
        );
        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.getPeakHoursConfig(anyString())).thenReturn(config);
        when(repo.getAllMetrics(anyString(), any(Duration.class))).thenReturn(List.of(
            point("2026-09-08T23:00:00Z", 10),
            point("2026-09-08T05:59:00Z", 20),
            point("2026-09-08T06:00:00Z", 30),
            point("2026-09-08T12:00:00Z", 40)
        ));

        MetricsCollectionService service = new MetricsCollectionService(repo);
        ResourceMetrics metrics = service.collectMetrics("aks-primary-cluster", 1);

        assertEquals(2, metrics.aggregated().peakSampleCount());
        assertEquals(2, metrics.aggregated().offPeakSampleCount());
        assertEquals(15.0, metrics.aggregated().peakHourUtilization(), 0.001);
        assertEquals(35.0, metrics.aggregated().offPeakHourUtilization(), 0.001);
    }

    /** Verifies stats use resource specific peak hours config from repository. */
    @Test
    void statsUseResourceSpecificPeakHoursConfigFromRepository() {
        PeakHoursConfig functionConfig = new PeakHoursConfig(
            LocalTime.of(6, 0), LocalTime.of(22, 0),
            List.of(1, 2, 3, 4, 5, 6, 0), 55.0, 8.0, 10
        );
        MetricPoint atEightPm = point("2026-09-08T20:00:00Z", 30);

        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.getPeakHoursConfig("function-data-processor")).thenReturn(functionConfig);
        when(repo.getPeakHoursConfig("aks-primary-cluster")).thenReturn(PeakHoursConfig.defaults());
        when(repo.getAllMetrics(eq("function-data-processor"), any(Duration.class)))
            .thenReturn(List.of(atEightPm));
        when(repo.getAllMetrics(eq("aks-primary-cluster"), any(Duration.class)))
            .thenReturn(List.of(atEightPm));

        MetricsCollectionService service = new MetricsCollectionService(repo);
        ResourceMetrics functionMetrics = service.collectMetrics("function-data-processor", 1);
        ResourceMetrics aksMetrics = service.collectMetrics("aks-primary-cluster", 1);

        assertEquals(1, functionMetrics.aggregated().peakSampleCount());
        assertEquals(0, functionMetrics.aggregated().offPeakSampleCount());
        assertEquals(0, aksMetrics.aggregated().peakSampleCount());
        assertEquals(1, aksMetrics.aggregated().offPeakSampleCount());
    }

    /** Verifies exposes repository peak hours config. */
    @Test
    void exposesRepositoryPeakHoursConfig() {
        PeakHoursConfig functionConfig = new PeakHoursConfig(
            LocalTime.of(6, 0), LocalTime.of(22, 0),
            List.of(1, 2, 3, 4, 5, 6, 0), 55.0, 8.0, 10
        );
        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.getPeakHoursConfig(anyString())).thenReturn(PeakHoursConfig.defaults());
        when(repo.getPeakHoursConfig("function-data-processor")).thenReturn(functionConfig);

        MetricsCollectionService service = new MetricsCollectionService(repo);

        assertEquals(functionConfig, service.getPeakHoursConfig("function-data-processor"));
        assertEquals(PeakHoursConfig.defaults(), service.getPeakHoursConfig("aks-primary-cluster"));
    }

    /** Verifies exactly idle window reports zero max cpu. */
    @Test
    void exactlyIdleWindowReportsZeroMaxCpu() {
        PeakHoursConfig config = new PeakHoursConfig(
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            List.of(1, 2, 3, 4, 5), 65.0, 10.0, 15
        );
        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.getPeakHoursConfig(anyString())).thenReturn(config);
        when(repo.getAllMetrics(anyString(), any(Duration.class))).thenReturn(List.of(
            idlePoint("2026-09-08T07:00:00Z"),
            idlePoint("2026-09-08T18:00:00Z")
        ));

        MetricsCollectionService service = new MetricsCollectionService(repo);
        ResourceMetrics metrics = service.collectMetrics("aks-primary-cluster", 1);

        assertEquals(0.0, metrics.aggregated().maxCpuUtilization(), 0.001);
        assertEquals(0.0, metrics.aggregated().maxMemoryUtilization(), 0.001);
    }

    private MetricPoint point(String iso, double cpu) {
        return new MetricPoint(
            Instant.parse(iso), cpu, 50.0, 100, "aks-primary-cluster", "AKS_CLUSTER"
        );
    }

    private MetricPoint idlePoint(String iso) {
        return new MetricPoint(
            Instant.parse(iso), 0, 0, 0, "aks-primary-cluster", "AKS_CLUSTER"
        );
    }

    /** Verifies exposes current config from repository. */
    @Test
    void exposesCurrentConfigFromRepository() {
        MetricsRepository repo = mock(MetricsRepository.class);
        CurrentConfig expected = new CurrentConfig("nginx-busy", 3, 3, 0.025, 0.150, 0.008, 0.032, 1, 4.0, true);
        when(repo.getCurrentConfig("nginx-busy")).thenReturn(expected);

        MetricsCollectionService service = new MetricsCollectionService(repo);

        assertEquals(expected, service.getCurrentConfig("nginx-busy"));
    }
}