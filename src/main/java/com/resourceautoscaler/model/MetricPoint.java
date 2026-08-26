package com.resourceautoscaler.model;

import java.time.Instant;

public record MetricPoint(
    Instant timestamp,
    double cpuUtilization,
    double memoryUtilization,
    int activeRequestCount,
    String resourceId,
    String resourceType
) {}
