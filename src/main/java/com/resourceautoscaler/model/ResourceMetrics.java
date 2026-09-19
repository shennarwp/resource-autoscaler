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
        int offPeakSampleCount,
        double p50CpuUtilization,
        double p95CpuUtilization,
        double p99CpuUtilization
    ) {
        /** Backwards-compatible constructor for callers that do not provide percentiles. */
        public AggregatedStats(double avgCpuUtilization, double maxCpuUtilization,
                double minCpuUtilization, double avgMemoryUtilization,
                double maxMemoryUtilization, double avgActiveRequests,
                double peakHourUtilization, double offPeakHourUtilization,
                int peakSampleCount, int offPeakSampleCount) {
            this(avgCpuUtilization, maxCpuUtilization, minCpuUtilization,
                    avgMemoryUtilization, maxMemoryUtilization, avgActiveRequests,
                    peakHourUtilization, offPeakHourUtilization, peakSampleCount,
                    offPeakSampleCount, avgCpuUtilization, maxCpuUtilization, maxCpuUtilization);
        }
    }
}
