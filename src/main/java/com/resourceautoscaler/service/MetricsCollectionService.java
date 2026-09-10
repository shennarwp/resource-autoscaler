package com.resourceautoscaler.service;

import com.resourceautoscaler.model.*;
import com.resourceautoscaler.repository.MetricsRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Collects repository samples, computes UTC peak aggregates, and caches results. */
@Service
public class MetricsCollectionService {

    private final MetricsRepository metricsRepository;

    /** Uses the active profile's repository as the source of metric data. */
    public MetricsCollectionService(MetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    /** Returns cached metrics when the same resource and duration are requested. */
    @Cacheable(value = "resourceMetrics", key = "#resourceId + '-' + #days")
    public ResourceMetrics collectMetrics(String resourceId, double days) {
        Duration timeRange = Duration.ofSeconds(Math.round(days * 86_400));
        List<MetricPoint> dataPoints = metricsRepository.getAllMetrics(resourceId, timeRange);
        PeakHoursConfig config = metricsRepository.getPeakHoursConfig(resourceId);

        ResourceMetrics.AggregatedStats stats = computeAggregatedStats(dataPoints, config);

        return new ResourceMetrics(
            resourceId,
            ResourceTypeResolver.resourceType(resourceId),
            ResourceTypeResolver.displayName(resourceId),
            Instant.now(),
            dataPoints,
            stats
        );
    }

    /** Lists resources supplied by the active metrics repository. */
    public List<String> getMonitoredResources() {
        return metricsRepository.getMonitoredResourceIds();
    }

    /** Computes overall, peak, off-peak, and request averages in UTC. */
    private ResourceMetrics.AggregatedStats computeAggregatedStats(
            List<MetricPoint> dataPoints, PeakHoursConfig config
    ) {
        if (dataPoints.isEmpty()) {
            return new ResourceMetrics.AggregatedStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        double cpuSum = 0, cpuMax = Double.NEGATIVE_INFINITY, cpuMin = Double.MAX_VALUE;
        double memSum = 0, memMax = Double.NEGATIVE_INFINITY;
        double peakSum = 0, peakCount = 0;
        double offPeakSum = 0, offPeakCount = 0;
        double reqSum = 0;

        for (MetricPoint p : dataPoints) {
            cpuSum += p.cpuUtilization();
            cpuMax = Math.max(cpuMax, p.cpuUtilization());
            cpuMin = Math.min(cpuMin, p.cpuUtilization());
            memSum += p.memoryUtilization();
            memMax = Math.max(memMax, p.memoryUtilization());
            reqSum += p.activeRequestCount();

            java.time.ZonedDateTime zdt = p.timestamp().atZone(java.time.ZoneId.of("UTC"));
            int daySeconds = zdt.toLocalTime().toSecondOfDay();
            int peakStartSeconds = config.peakStart().toSecondOfDay();
            int peakEndSeconds = config.peakEnd().toSecondOfDay();
            boolean isPeakDay = config.peakDaysOfWeek().contains(zdt.getDayOfWeek().getValue());
            boolean isPeakTime;
            if (peakStartSeconds <= peakEndSeconds) {
                isPeakTime = daySeconds >= peakStartSeconds && daySeconds < peakEndSeconds;
            } else {
                isPeakTime = daySeconds >= peakStartSeconds || daySeconds < peakEndSeconds;
            }

            if (isPeakDay && isPeakTime) {
                peakSum += p.cpuUtilization();
                peakCount++;
            } else {
                offPeakSum += p.cpuUtilization();
                offPeakCount++;
            }
        }

        int size = dataPoints.size();
        return new ResourceMetrics.AggregatedStats(
            cpuSum / size,
            cpuMax,
            cpuMin,
            memSum / size,
            memMax,
            reqSum / size,
            peakCount > 0 ? peakSum / peakCount : 0,
            offPeakCount > 0 ? offPeakSum / offPeakCount : 0,
            (int) peakCount,
            (int) offPeakCount
        );
    }

    /** Returns the schedule configured by the active repository. */
    public PeakHoursConfig getPeakHoursConfig(String resourceId) {
        return metricsRepository.getPeakHoursConfig(resourceId);
    }

    /** Returns discovered capacity, if supported by the active repository. */
    public CurrentConfig getCurrentConfig(String resourceId) {
        return metricsRepository.getCurrentConfig(resourceId);
    }
}
