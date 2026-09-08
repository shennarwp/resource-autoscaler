package com.resourceautoscaler.service;

import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.repository.MetricsRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsCollectionServiceTest {

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

    private MetricPoint point(String iso, double cpu) {
        return new MetricPoint(
            Instant.parse(iso), cpu, 50.0, 100, "aks-primary-cluster", "AKS_CLUSTER"
        );
    }
}