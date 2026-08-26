package com.resourceautoscaler.model;

import java.time.Instant;
import java.util.List;

public record ResourceMetrics(
    String resourceId,
    String resourceType,
    String resourceName,
    Instant collectedAt,
    List<MetricPoint> dataPoints,
    AggregatedStats aggregated
) {
    public record AggregatedStats(
        double avgCpuUtilization,
        double maxCpuUtilization,
        double minCpuUtilization,
        double avgMemoryUtilization,
        double maxMemoryUtilization,
        double avgActiveRequests,
        double peakHourUtilization,
        double offPeakHourUtilization
    ) {}
}
