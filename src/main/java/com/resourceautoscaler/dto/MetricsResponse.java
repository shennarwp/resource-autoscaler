package com.resourceautoscaler.dto;

import com.resourceautoscaler.model.MetricPoint;
import java.util.List;

/** Public metrics payload returned to the frontend. */
public record MetricsResponse(
    String resourceId,
    String resourceType,
    String resourceName,
    List<MetricPoint> dataPoints,
    AggregatedStats stats
) {
    /** Reduced aggregate set needed by the resource detail view. */
    public record AggregatedStats(
        double avgCpuUtilization,
        double maxCpuUtilization,
        double avgMemoryUtilization,
        double peakHourUtilization,
        double offPeakHourUtilization
    ) {}
}
