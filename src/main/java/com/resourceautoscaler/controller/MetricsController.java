package com.resourceautoscaler.controller;

import com.resourceautoscaler.dto.MetricsResponse;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.service.MetricsCollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints exposing monitored resources, samples, and peak schedules. */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsCollectionService metricsService;

    /** Injects the service responsible for collection and aggregation. */
    public MetricsController(MetricsCollectionService metricsService) {
        this.metricsService = metricsService;
    }

    /** Lists resource identifiers provided by the active metrics repository. */
    @GetMapping
    public ResponseEntity<List<String>> getMonitoredResources() {
        return ResponseEntity.ok(metricsService.getMonitoredResources());
    }

    /**
     * Collects a resource window and maps internal aggregates to the public response
     * shape. The {@code days} value may be fractional for short chart windows.
     */
    @GetMapping("/{resourceId}")
    public ResponseEntity<MetricsResponse> getResourceMetrics(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "30") double days
    ) {
        ResourceMetrics metrics = metricsService.collectMetrics(resourceId, days);

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

    /** Returns the UTC schedule and utilization thresholds for a resource. */
    @GetMapping("/{resourceId}/peak-config")
    public ResponseEntity<PeakHoursConfig> getPeakHoursConfig(@PathVariable String resourceId) {
        return ResponseEntity.ok(metricsService.getPeakHoursConfig(resourceId));
    }
}
