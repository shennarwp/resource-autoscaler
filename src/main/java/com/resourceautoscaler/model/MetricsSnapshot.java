package com.resourceautoscaler.model;

import java.util.List;

/**
 * A point-in-time download of a resource's metrics (plus its current config),
 * persisted as a readable JSON file so other profiles can replay real data.
 */
public record MetricsSnapshot(
    String resourceId,
    String resourceType,
    String resourceName,
    String downloadedAt,
    String start,
    String end,
    Integer stepSeconds,
    CurrentConfig currentConfig,
    List<Point> dataPoints
) {
    public record Point(String timestamp, double cpuUtilization, double memoryUtilization, int activeRequestCount) {}
}