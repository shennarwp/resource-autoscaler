package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MetricsRepository {

    List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange);

    List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange);

    List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange);

    List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange);

    List<String> getMonitoredResourceIds();

    PeakHoursConfig getPeakHoursConfig(String resourceId);

    CurrentConfig getCurrentConfig(String resourceId);

    /**
     * Downloads raw metric samples for an explicit window, for persistence into a snapshot.
     * Repositories without a raw download source return {@link Optional#empty()}.
     */
    default Optional<List<MetricPoint>> downloadRawMetrics(String resourceId, Instant start, Instant end) {
        return Optional.empty();
    }
}
