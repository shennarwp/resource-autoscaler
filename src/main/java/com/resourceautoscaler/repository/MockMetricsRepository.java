package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("mock")
public class MockMetricsRepository implements MetricsRepository {

    private static final Map<String, PeakHoursConfig> PEAK_CONFIGS = Map.of(
        "aks-primary-cluster", PeakHoursConfig.defaults(),
        "vm-backend-01", PeakHoursConfig.defaults(),
        "appservice-api-gateway", PeakHoursConfig.defaults(),
        "function-data-processor", new PeakHoursConfig(
            LocalTime.of(6, 0), LocalTime.of(22, 0),
            List.of(1, 2, 3, 4, 5, 6, 0),
            55.0, 8.0, 10
        )
    );

    @Override
    public List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange) {
        return generateSineWaveMetrics(resourceId, timeRange, 7, 18, 65.0, 5.0, 15.0);
    }

    @Override
    public List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange) {
        return generateSineWaveMetrics(resourceId, timeRange, 7, 18, 55.0, 12.0, 10.0);
    }

    @Override
    public List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange) {
        return generateSineWaveMetrics(resourceId, timeRange, 7, 18, 200.0, 5.0, 50.0);
    }

    @Override
    public List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange) {
        List<MetricPoint> cpu = getCpuUtilization(resourceId, timeRange);
        List<MetricPoint> mem = getMemoryUtilization(resourceId, timeRange);
        List<MetricPoint> req = getActiveRequestCount(resourceId, timeRange);

        List<MetricPoint> merged = new ArrayList<>();
        for (int i = 0; i < cpu.size(); i++) {
            merged.add(new MetricPoint(
                cpu.get(i).timestamp(),
                cpu.get(i).cpuUtilization(),
                mem.get(i).memoryUtilization(),
                (int) req.get(i).cpuUtilization(),
                resourceId,
                getResourceType(resourceId)
            ));
        }
        return merged;
    }

    @Override
    public List<String> getMonitoredResourceIds() {
        return List.of(
            "aks-primary-cluster",
            "vm-backend-01",
            "appservice-api-gateway",
            "function-data-processor"
        );
    }

    @Override
    public PeakHoursConfig getPeakHoursConfig(String resourceId) {
        return PEAK_CONFIGS.getOrDefault(resourceId, PeakHoursConfig.defaults());
    }

    private List<MetricPoint> generateSineWaveMetrics(
            String resourceId, Duration timeRange,
            int peakStartHour, int peakEndHour,
            double peakBase, double offPeakBase, double amplitude
    ) {
        Instant end = Instant.now();
        Instant start = end.minus(timeRange);
        List<MetricPoint> points = new ArrayList<>();

        long totalSeconds = timeRange.getSeconds();
        long interval = Math.max(totalSeconds / 48, 3600);

        for (Instant t = start; !t.isAfter(end); t = t.plusSeconds(interval)) {
            java.time.ZonedDateTime zdt = t.atZone(java.time.ZoneId.of("UTC"));
            int hour = zdt.getHour();

            double base;
            if (hour >= peakStartHour && hour <= peakEndHour) {
                base = peakBase;
            } else {
                base = offPeakBase;
            }

            double noise = (Math.random() - 0.5) * 4.0;
            double value = Math.max(0, Math.min(100, base + noise));

            points.add(new MetricPoint(
                t, value, value * 0.85, (int)(value * 3),
                resourceId, getResourceType(resourceId)
            ));
        }
        return points;
    }

    private String getResourceType(String resourceId) {
        if (resourceId.startsWith("aks")) return "AKS_CLUSTER";
        if (resourceId.startsWith("vm")) return "AZURE_VM";
        if (resourceId.startsWith("app")) return "APP_SERVICE";
        if (resourceId.startsWith("func")) return "AZURE_FUNCTION";
        return "UNKNOWN";
    }
}
