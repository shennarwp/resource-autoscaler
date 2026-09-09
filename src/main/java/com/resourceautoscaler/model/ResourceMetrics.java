package com.resourceautoscaler.model;

import java.time.Instant;
import java.util.List;

/** Collected samples and derived utilization statistics for one resource. */
public record ResourceMetrics(
    String resourceId,
    String resourceType,
    String resourceName,
    Instant collectedAt,
    List<MetricPoint> dataPoints,
    AggregatedStats aggregated
) {
    /** Aggregate values used by API responses and recommendation scoring. */
    public record AggregatedStats(
        double avgCpuUtilization,
        double maxCpuUtilization,
        double minCpuUtilization,
        double avgMemoryUtilization,
        double maxMemoryUtilization,
        double avgActiveRequests,
        double peakHourUtilization,
        double offPeakHourUtilization,
        int peakSampleCount,
        int offPeakSampleCount
    ) {}
}
