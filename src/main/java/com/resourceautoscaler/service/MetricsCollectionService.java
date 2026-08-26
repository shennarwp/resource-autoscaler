package com.resourceautoscaler.service;

import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.repository.MetricsRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class MetricsCollectionService {

    private final MetricsRepository metricsRepository;

    public MetricsCollectionService(MetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    @Cacheable(value = "resourceMetrics", key = "#resourceId + '-' + #days")
    public ResourceMetrics collectMetrics(String resourceId, int days) {
        Duration timeRange = Duration.ofDays(days);
        List<MetricPoint> dataPoints = metricsRepository.getAllMetrics(resourceId, timeRange);
        PeakHoursConfig config = metricsRepository.getPeakHoursConfig(resourceId);

        ResourceMetrics.AggregatedStats stats = computeAggregatedStats(dataPoints, config);

        return new ResourceMetrics(
            resourceId,
            getColumnType(resourceId),
            getResourceName(resourceId),
            Instant.now(),
            dataPoints,
            stats
        );
    }

    public List<String> getMonitoredResources() {
        return metricsRepository.getMonitoredResourceIds();
    }

    private ResourceMetrics.AggregatedStats computeAggregatedStats(
            List<MetricPoint> dataPoints, PeakHoursConfig config
    ) {
        if (dataPoints.isEmpty()) {
            return new ResourceMetrics.AggregatedStats(0, 0, 0, 0, 0, 0, 0, 0);
        }

        double cpuSum = 0, cpuMax = Double.MIN_VALUE, cpuMax2 = Double.MIN_VALUE;
        double memSum = 0, memMax = Double.MIN_VALUE;
        double peakSum = 0, peakCount = 0;
        double offPeakSum = 0, offPeakCount = 0;
        double reqSum = 0;

        for (MetricPoint p : dataPoints) {
            cpuSum += p.cpuUtilization();
            cpuMax = Math.max(cpuMax, p.cpuUtilization());
            cpuMax2 = Math.max(cpuMax2, p.memoryUtilization());
            memSum += Math.max(memSum, p.memoryUtilization());
            reqSum += p.activeRequestCount();

            java.time.ZonedDateTime zdt = p.timestamp().atZone(java.time.ZoneId.of("UTC"));
            int hour = zdt.getHour();
            java.time.DayOfWeek dow = zdt.getDayOfWeek();
            boolean isPeakDay = config.peakDaysOfWeek().contains(dow.getValue());
            boolean isPeakHour = hour >= config.peakStart().getHour() && hour <= config.peakEnd().getHour();

            if (isPeakDay && isPeakHour) {
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
            cpuMax2,
            memSum / size,
            memSum,
            reqSum / size,
            peakCount > 0 ? peakSum / peakCount : 0,
            offPeakCount > 0 ? offPeakSum / offPeakCount : 0
        );
    }

    private String getColumnType(String resourceId) {
        if (resourceId.startsWith("aks")) return "AKS_CLUSTER";
        if (resourceId.startsWith("vm")) return "AZURE_VM";
        if (resourceId.startsWith("app")) return "APP_SERVICE";
        if (resourceId.startsWith("func")) return "AZURE_FUNCTION";
        return "UNKNOWN";
    }

    private String getResourceName(String resourceId) {
        return switch (resourceId) {
            case "aks-primary-cluster" -> "Primary AKS Cluster";
            case "vm-backend-01" -> "Backend VM-01";
            case "appservice-api-gateway" -> "API Gateway";
            case "function-data-processor" -> "Data Processor Function";
            default -> resourceId;
        };
    }
}
