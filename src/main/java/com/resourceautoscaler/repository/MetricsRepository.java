package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Profile-specific source of utilization samples and resource configuration. */
public interface MetricsRepository {

    /** Returns CPU samples for the requested duration. */
    List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange);

    /** Returns memory samples for the requested duration. */
    List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange);

    /** Returns active-request samples for the requested duration. */
    List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange);

    /** Returns aligned CPU, memory, and request samples for analysis. */
    List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange);

    /** Lists resources visible to the active application profile. */
    List<String> getMonitoredResourceIds();

    /** Returns the schedule and thresholds used to split samples. */
    PeakHoursConfig getPeakHoursConfig(String resourceId);

    /** Returns current capacity information, if the source can discover it. */
    CurrentConfig getCurrentConfig(String resourceId);

    /**
     * Downloads raw metric samples for an explicit window, for persistence into a snapshot.
     * Repositories without a raw download source return {@link Optional#empty()}.
     */
    default Optional<List<MetricPoint>> downloadRawMetrics(String resourceId, Instant start, Instant end) {
        return Optional.empty();
    }
}
