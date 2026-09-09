package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.MetricsSnapshot;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.store.SnapshotStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default profile repository. The mock UI shows only the Kubernetes cluster
 * (default {@code nginx-busy}); when a downloaded metrics snapshot exists for it,
 * that real data is replayed for any requested timeframe (tiled by the snapshot's
 * span), otherwise a deterministic sine-wave generator is used.
 */
@Repository
@Profile("mock")
public class MockMetricsRepository implements MetricsRepository {

    private static final Logger log = LoggerFactory.getLogger(MockMetricsRepository.class);

    private static final Map<String, PeakHoursConfig> PEAK_CONFIGS = Map.of(
        "nginx-busy", new PeakHoursConfig(
            LocalTime.of(5, 0), LocalTime.of(16, 0),
            List.of(1, 2, 3, 4, 5),
            50.0, 18.0, 15
        ),
        "aks-primary-cluster", PeakHoursConfig.defaults(),
        "vm-backend-01", PeakHoursConfig.defaults(),
        "appservice-api-gateway", PeakHoursConfig.defaults(),
        "function-data-processor", new PeakHoursConfig(
            LocalTime.of(6, 0), LocalTime.of(22, 0),
            List.of(1, 2, 3, 4, 5, 6, 0),
            55.0, 8.0, 10
        )
    );

    private final SnapshotStore snapshotStore;
    private final String kubernetesResourceId;
    private MetricsSnapshot kubeSnapshot;

    /** Creates the mock source and selects the resource whose snapshot is replayed. */
    public MockMetricsRepository(
            SnapshotStore snapshotStore,
            @Value("${app.mock.kubernetes-id:nginx-busy}") String kubernetesResourceId
    ) {
        this.snapshotStore = snapshotStore;
        this.kubernetesResourceId = kubernetesResourceId;
    }

    /** Loads an exported Kubernetes snapshot before serving mock requests. */
    @PostConstruct
    void loadKubeSnapshot() {
        kubeSnapshot = snapshotStore.read(kubernetesResourceId);
        if (kubeSnapshot != null) {
            log.info("Mock Kubernetes cluster '{}' replaying {} cached metric points from {} to {}",
                    kubernetesResourceId, kubeSnapshot.dataPoints().size(), kubeSnapshot.start(), kubeSnapshot.end());
        }
    }

    /** Replays CPU samples or generates mock CPU data for the requested window. */
    @Override
    public List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange) {
        List<MetricPoint> full = snapshotPoints(resourceId, timeRange);
        if (full != null) {
            return full.stream()
                    .map(p -> new MetricPoint(p.timestamp(), p.cpuUtilization(), 0, 0, resourceId, getResourceType(resourceId)))
                    .toList();
        }
        if ("appservice-api-gateway".equals(resourceId)) {
            return generateSineWaveMetrics(resourceId, timeRange, 7, 18, 40.0, 40.0, 5.0);
        }
        return generateSineWaveMetrics(resourceId, timeRange, 7, 18, 75.0, 5.0, 15.0);
    }

    /** Replays memory samples or generates mock memory data for the requested window. */
    @Override
    public List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange) {
        List<MetricPoint> full = snapshotPoints(resourceId, timeRange);
        if (full != null) {
            return full.stream()
                    .map(p -> new MetricPoint(p.timestamp(), 0, p.memoryUtilization(), 0, resourceId, getResourceType(resourceId)))
                    .toList();
        }
        return generateSineWaveMetrics(resourceId, timeRange, 7, 18, 55.0, 12.0, 10.0);
    }

    /** Replays request samples or generates mock request data for the requested window. */
    @Override
    public List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange) {
        List<MetricPoint> full = snapshotPoints(resourceId, timeRange);
        if (full != null) {
            return full.stream()
                    .map(p -> new MetricPoint(p.timestamp(), 0, 0, p.activeRequestCount(), resourceId, getResourceType(resourceId)))
                    .toList();
        }
        return generateSineWaveMetrics(resourceId, timeRange, 7, 18, 200.0, 5.0, 50.0);
    }

    /** Returns a snapshot unchanged or merges generated metric streams. */
    @Override
    public List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange) {
        List<MetricPoint> full = snapshotPoints(resourceId, timeRange);
        if (full != null) {
            return full;
        }

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

    /** Exposes the configured mock resource, normally one Kubernetes cluster. */
    @Override
    public List<String> getMonitoredResourceIds() {
        return List.of(kubernetesResourceId);
    }

    /** Returns resource-specific mock schedule data or shared defaults. */
    @Override
    public PeakHoursConfig getPeakHoursConfig(String resourceId) {
        return PEAK_CONFIGS.getOrDefault(resourceId, PeakHoursConfig.defaults());
    }

    /** Returns replayed discovered config or representative mock capacity values. */
    @Override
    public CurrentConfig getCurrentConfig(String resourceId) {
        if (kubeSnapshot != null
                && kubernetesResourceId.equals(resourceId)
                && kubeSnapshot.currentConfig() != null) {
            CurrentConfig cfg = kubeSnapshot.currentConfig();
            return new CurrentConfig(
                resourceId,
                cfg.replicas(), cfg.availableReplicas(),
                cfg.cpuRequestCores(), cfg.cpuLimitCores(),
                cfg.memoryRequestGiB(), cfg.memoryLimitGiB(),
                cfg.nodeCount(), cfg.nodeCpuCores(),
                true
            );
        }
        switch (resourceId) {
            case "aks-primary-cluster":
                return new CurrentConfig(resourceId, 3, 3, 1.0, 2.0, 2.0, 4.0, 2, 8.0, true);
            case "vm-backend-01":
                return new CurrentConfig(resourceId, 1, 1, 4.0, 4.0, 8.0, 16.0, 1, 4.0, true);
            case "appservice-api-gateway":
                return new CurrentConfig(resourceId, 1, 1, 0.75, 1.75, 1.0, 3.5, 0, 0, true);
            case "function-data-processor":
                return new CurrentConfig(resourceId, 1, 1, 0.25, 0.5, 0.5, 1.0, 0, 0, true);
            default:
                return CurrentConfig.unknown(resourceId);
        }
    }

    /** Returns replayed points when the requested resource has a loaded snapshot. */
    private List<MetricPoint> snapshotPoints(String resourceId, Duration timeRange) {
        if (kubeSnapshot == null || !kubernetesResourceId.equals(resourceId)
                || kubeSnapshot.dataPoints() == null || kubeSnapshot.dataPoints().isEmpty()) {
            return null;
        }
        Instant end = alignedWindowEnd(kubeSnapshot, Instant.now());
        return samplesForRange(kubeSnapshot, end.minus(timeRange), end, resourceId);
    }

    /**
     * Maps the wall-clock time of {@code now} onto the snapshot's last day so the
     * replay window tracks tick-by-tick clock alignment (e.g. "now, 3h back")
     * against the downloaded sample day, rather than always ending at the snapshot's
     * newest data point. If now's clock time is past the newest data point, the end
     * is clamped to it. Exposed as static so the mapping is unit-testable.
     */
    static Instant alignedWindowEnd(MetricsSnapshot snapshot, Instant now) {
        List<MetricsSnapshot.Point> points = snapshot.dataPoints();
        if (points == null || points.isEmpty()) {
            return now;
        }
        Instant last = Instant.parse(points.get(points.size() - 1).timestamp());
        java.time.ZonedDateTime lastDay = last.atZone(java.time.ZoneOffset.UTC);
        java.time.LocalTime clock = now.atZone(java.time.ZoneOffset.UTC).toLocalTime()
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant candidate = lastDay.with(clock).toInstant();
        return candidate.isAfter(last) ? last : candidate;
    }

    /**
     * Replays the snapshot's samples for an arbitrary window by tiling the snapshot's
     * span (most recent copy first) and returning points falling inside the window,
     * downsampled to the step cadence of the requested range. Weekend days render a
     * light baseline in place of the tiled workload, and any zero-cpu sample gets a
     * random 7-15% baseline so idle points are never a flat 0. Exposed as static so
     * the propagation logic is unit-testable.
     */
    static List<MetricPoint> samplesForRange(MetricsSnapshot snapshot, Instant start, Instant end, String resourceId) {
        List<MetricsSnapshot.Point> points = snapshot.dataPoints();
        if (points == null || points.isEmpty()) {
            return List.of();
        }

        Instant first = Instant.parse(points.get(0).timestamp());
        Instant last = Instant.parse(points.get(points.size() - 1).timestamp());
        long step = stepSecondsForRange(Duration.between(start, end));
        long spanSeconds = Math.max(Duration.between(first, last).getSeconds()
                + (snapshot.stepSeconds() != null && snapshot.stepSeconds() > 0 ? snapshot.stepSeconds() : 60), step);

        long windowSeconds = Duration.between(start, end).getSeconds();
        long copies = Math.max(windowSeconds / spanSeconds + 1, 1);
        String resourceType = snapshot.resourceType();

        List<MetricPoint> result = new ArrayList<>();
        for (long copy = copies - 1; copy >= 0; copy--) {
            long offsetSeconds = copy * spanSeconds;
            long lastBucket = -1;
            for (MetricsSnapshot.Point p : points) {
                Instant ts = Instant.parse(p.timestamp()).minusSeconds(offsetSeconds);
                if (ts.isBefore(start) || ts.isAfter(end)) {
                    continue;
                }
                long bucket = ts.getEpochSecond() / step;
                if (bucket == lastBucket) {
                    continue;
                }
                lastBucket = bucket;
                int weekday = ts.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue();
                boolean weekend = weekday == 6 || weekday == 7;
                double cpu = weekend ? 0.0 : p.cpuUtilization();
                if (!weekend && cpu < 7.0) {
                    cpu = ThreadLocalRandom.current().nextDouble(7.0, 15.0);
                }
                result.add(new MetricPoint(
                    ts,
                    cpu,
                    p.memoryUtilization(),
                    weekend ? 0 : p.activeRequestCount(),
                    resourceId, resourceType
                ));
            }
        }
        return result;
    }

    /** Chooses the display downsampling cadence for a requested range. */
    private static long stepSecondsForRange(Duration range) {
        long seconds = range.getSeconds();
        if (seconds < 3600) return 60;
        if (seconds < 3 * 86400) return 300;
        return 3600;
    }

    /** Generates a bounded weekday/weekend sine-wave utilization profile. */
    private List<MetricPoint> generateSineWaveMetrics(
            String resourceId, Duration timeRange,
            int peakStartHour, int peakEndHour,
            double peakBase, double offPeakBase, double amplitude
    ) {
        Instant end = Instant.now();
        Instant start = end.minus(timeRange);
        List<MetricPoint> points = new ArrayList<>();

        long totalSeconds = timeRange.getSeconds();
        long interval = totalSeconds < 3600 ? 60 : 3600;

        for (Instant t = start; !t.isAfter(end); t = t.plusSeconds(interval)) {
            java.time.ZonedDateTime zdt = t.atZone(java.time.ZoneId.of("UTC"));
            int hour = zdt.getHour();

            java.time.DayOfWeek dow = zdt.getDayOfWeek();
            boolean isWeekend = dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY;
            double base;
            if (!isWeekend && hour >= peakStartHour && hour <= peakEndHour) {
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

    /** Infers the public resource type from the mock ID prefix. */
    private String getResourceType(String resourceId) {
        if (resourceId.startsWith("nginx")) return "K8S_CLUSTER";
        if (resourceId.startsWith("aks")) return "AKS_CLUSTER";
        if (resourceId.startsWith("vm")) return "AZURE_VM";
        if (resourceId.startsWith("app")) return "APP_SERVICE";
        if (resourceId.startsWith("func")) return "AZURE_FUNCTION";
        return "UNKNOWN";
    }
}