package com.resourceautoscaler.controller;

import com.resourceautoscaler.dto.MetricsResponse;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.service.MetricsCollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsCollectionService metricsService;

    public MetricsController(MetricsCollectionService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public ResponseEntity<List<String>> getMonitoredResources() {
        return ResponseEntity.ok(metricsService.getMonitoredResources());
    }

    @GetMapping("/{resourceId}")
    public ResponseEntity<MetricsResponse> getResourceMetrics(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "30") int days
    ) {
        ResourceMetrics metrics = metricsService.collectMetrics(resourceId, days);
        PeakHoursConfig config = new PeakHoursConfig(
            java.time.LocalTime.of(7, 0), java.time.LocalTime.of(18, 0),
            List.of(1, 2, 3, 4, 5), 65.0, 10.0, 15
        );

        ResourceMetrics.AggregatedStats stats = metrics.aggregated();
        return ResponseEntity.ok(new MetricsResponse(
            metrics.resourceId(),
            metrics.resourceType(),
            metrics.resourceName(),
            metrics.dataPoints(),
            new MetricsResponse.AggregatedStats(
                stats.avgCpuUtilization(),
                stats.maxCpuUtilization(),
                stats.avgMemoryUtilization(),
                stats.peakHourUtilization(),
                stats.offPeakHourUtilization()
            )
        ));
    }

    @GetMapping("/{resourceId}/peak-config")
    public ResponseEntity<PeakHoursConfig> getPeakHoursConfig(@PathVariable String resourceId) {
        return ResponseEntity.ok(PeakHoursConfig.defaults());
    }
}
