package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.MetricsSnapshot;
import com.resourceautoscaler.store.SnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockMetricsRepositoryTest {

    @TempDir
    Path tempDir;

    private MetricsSnapshot snapshot(Instant base) {
        List<MetricsSnapshot.Point> points = List.of(
            new MetricsSnapshot.Point(base.toString(), 10.0, 5.0, 0),
            new MetricsSnapshot.Point(base.plusSeconds(60).toString(), 20.0, 6.0, 0),
            new MetricsSnapshot.Point(base.plusSeconds(120).toString(), 30.0, 7.0, 0)
        );
        return new MetricsSnapshot(
            "nginx-busy", "K8S_CLUSTER", "Nginx Busy (K3s)",
            "2026-09-08T09:00:00Z", base.toString(), base.plusSeconds(120).toString(),
            60, null, points);
    }

    @Test
    void rangeWithinSnapshotReturnsOnlyOverlappingSamples() {
        Instant base = Instant.parse("2026-09-08T05:00:00Z");
        List<MetricPoint> points = MockMetricsRepository.samplesForRange(
                snapshot(base), base, base.plusSeconds(60), "nginx-busy");

        assertEquals(2, points.size());
        assertEquals(base, points.get(0).timestamp());
        assertEquals(10.0, points.get(0).cpuUtilization(), 0.001);
        assertEquals(base.plusSeconds(60), points.get(1).timestamp());
        assertEquals("K8S_CLUSTER", points.get(0).resourceType());
    }

    @Test
    void rangeLongerThanSnapshotIsTiledBySnapshotSpan() {
        Instant base = Instant.parse("2026-09-08T05:00:00Z");
        MetricsSnapshot snap = snapshot(base);

        Instant start = base.minusSeconds(2 * 180);
        Instant end = base.plusSeconds(120);
        List<MetricPoint> points = MockMetricsRepository.samplesForRange(snap, start, end, "nginx-busy");

        // 3 copies of the 3-sample snapshot fall inside the window
        assertEquals(9, points.size());
        assertTrue(points.get(0).timestamp().isBefore(points.get(1).timestamp()));
        assertEquals(9, points.stream().map(MetricPoint::timestamp).distinct().count());
        assertEquals(10.0, points.get(0).cpuUtilization(), 0.001);
        assertEquals(base, points.get(points.size() - 3).timestamp());
    }

    @Test
    void longRangesAreDownsampledToHourlyCadence() {
        Instant base = Instant.parse("2026-09-08T05:00:00Z");
        MetricsSnapshot snap = snapshot(base);

        Instant start = base.minus(Duration.ofDays(7));
        Instant end = base.plusSeconds(120);
        List<MetricPoint> points = MockMetricsRepository.samplesForRange(snap, start, end, "nginx-busy");

        assertTrue(!points.isEmpty());
        long distinctHourBuckets = points.stream()
                .map(p -> p.timestamp().getEpochSecond() / 3600)
                .distinct().count();
        assertEquals(points.size(), distinctHourBuckets);
        long windowSeconds = Duration.between(start, end).getSeconds();
        assertTrue(points.size() <= windowSeconds / 180 + 2);
        assertTrue(points.size() >= windowSeconds / 3600);
    }

    @Test
    void mockExposesOnlyTheKubernetesCluster() {
        MetricsRepository repo = new MockMetricsRepository(new SnapshotStore(tempDir.toString()), "nginx-busy");
        assertEquals(List.of("nginx-busy"), repo.getMonitoredResourceIds());
    }

    @Test
    void mockReplaysDownloadedSnapshotAndItsCurrentConfig() throws Exception {
        Instant base = Instant.now().minusSeconds(90);
        CurrentConfig config = new CurrentConfig("nginx-busy", 3, 3, 0.025, 0.150, 8.0 / 1024.0, 32.0 / 1024.0, 1, 4.0, true);
        SnapshotStore store = new SnapshotStore(tempDir.toString());
        store.write(new MetricsSnapshot(
            "nginx-busy", "K8S_CLUSTER", "Nginx Busy (K3s)",
            "2026-09-08T09:00:00Z", base.toString(), base.plusSeconds(120).toString(),
            60, config, List.of(
                new MetricsSnapshot.Point(base.toString(), 10.0, 5.0, 0),
                new MetricsSnapshot.Point(base.plusSeconds(120).toString(), 30.0, 7.0, 0)
            )
        ));

        MockMetricsRepository repo = new MockMetricsRepository(store, "nginx-busy");
        repo.loadKubeSnapshot();

        List<MetricPoint> points = repo.getAllMetrics("nginx-busy", Duration.ofMinutes(30));
        assertTrue(!points.isEmpty());
        assertTrue(points.stream().allMatch(p -> Math.abs(p.cpuUtilization() - 10.0) < 0.001
                || Math.abs(p.cpuUtilization() - 30.0) < 0.001));

        CurrentConfig discovered = repo.getCurrentConfig("nginx-busy");
        assertTrue(discovered.available());
        assertEquals(3, discovered.replicas());
        assertEquals(4.0, discovered.nodeCpuCores(), 0.001);
    }
}