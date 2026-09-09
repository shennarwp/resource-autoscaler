package com.resourceautoscaler.model;

import java.time.Instant;

/** One UTC metric sample; utilization values are percentages. */
public record MetricPoint(
    Instant timestamp,
    double cpuUtilization,
    double memoryUtilization,
    int activeRequestCount,
    String resourceId,
    String resourceType
) {}
