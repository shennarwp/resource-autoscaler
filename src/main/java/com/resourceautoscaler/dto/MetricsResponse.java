package com.resourceautoscaler.dto;

import com.resourceautoscaler.model.MetricPoint;
import java.util.List;

public record MetricsResponse(
    String resourceId,
    String resourceType,
    String resourceName,
    List<MetricPoint> dataPoints,
    AggregatedStats stats
) {
    public record AggregatedStats(
        double avgCpuUtilization,
        double maxCpuUtilization,
        double avgMemoryUtilization,
        double peakHourUtilization,
        double offPeakHourUtilization
    ) {}
}
