package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;

import java.time.Duration;
import java.util.List;

public interface MetricsRepository {

    List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange);

    List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange);

    List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange);

    List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange);

    List<String> getMonitoredResourceIds();

    PeakHoursConfig getPeakHoursConfig(String resourceId);
}
