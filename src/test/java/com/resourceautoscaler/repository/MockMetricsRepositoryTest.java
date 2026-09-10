package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.MetricsSnapshot;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.store.SnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the mock metrics repository behavior and regression cases. */
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

    /** Verifies range within snapshot returns only overlapping samples. */
    @Test
    void rangeWithinSnapshotReturnsOnlyOverlappingSamples() {
        Instant base = Instant.parse("2026-09-08T05:00:00Z");
        List<MetricPoint> points = SnapshotReplayer.samplesForRange(
                snapshot(base), base, base.plusSeconds(60), "nginx-busy");

        assertEquals(2, points.size());
        assertEquals(base, points.get(0).timestamp());
        assertEquals(10.0, points.get(0).cpuUtilization(), 0.001);
        assertEquals(base.plusSeconds(60), points.get(1).timestamp());
        assertEquals("K8S_CLUSTER", points.get(0).resourceType());
    }

    /** Verifies range longer than snapshot is tiled by snapshot span. */
    @Test
    void rangeLongerThanSnapshotIsTiledBySnapshotSpan() {
        Instant base = Instant.parse("2026-09-08T05:00:00Z");
        MetricsSnapshot snap = snapshot(base);

        Instant start = base.minusSeconds(2 * 180);
        Instant end = base.plusSeconds(120);
        List<MetricPoint> points = SnapshotReplayer.samplesForRange(snap, start, end, "nginx-busy");

        // 3 copies of the 3-sample snapshot fall inside the window
        assertEquals(9, points.size());
        assertTrue(points.get(0).timestamp().isBefore(points.get(1).timestamp()));
        assertEquals(9, points.stream().map(MetricPoint::timestamp).distinct().count());
        assertEquals(10.0, points.get(0).cpuUtilization(), 0.001);
        assertEquals(base, points.get(points.size() - 3).timestamp());
    }

    /** Verifies long ranges are downsampled to hourly cadence. */
    @Test
    void longRangesAreDownsampledToHourlyCadence() {
        Instant base = Instant.parse("2026-09-08T05:00:00Z");
        MetricsSnapshot snap = snapshot(base);

        Instant start = base.minus(Duration.ofDays(7));
        Instant end = base.plusSeconds(120);
        List<MetricPoint> points = SnapshotReplayer.samplesForRange(snap, start, end, "nginx-busy");

        assertFalse(points.isEmpty());
        long distinctHourBuckets = points.stream()
                .map(p -> p.timestamp().getEpochSecond() / 3600)
                .distinct().count();
        assertEquals(points.size(), distinctHourBuckets);
        long windowSeconds = Duration.between(start, end).getSeconds();
        assertTrue(points.size() <= windowSeconds / 180 + 2);
        assertTrue(points.size() >= windowSeconds / 3600);
    }

    private static List<MetricsSnapshot.Point> dayPoints() {
        return List.of(
            new MetricsSnapshot.Point("2026-09-08T00:00:00Z", 10.0, 5.0, 0),
            new MetricsSnapshot.Point("2026-09-08T05:00:00Z", 20.0, 6.0, 0),
            new MetricsSnapshot.Point("2026-09-08T06:00:00Z", 30.0, 7.0, 0),
            new MetricsSnapshot.Point("2026-09-08T07:00:00Z", 40.0, 8.0, 0),
            new MetricsSnapshot.Point("2026-09-08T08:00:00Z", 50.0, 9.0, 0),
            new MetricsSnapshot.Point("2026-09-08T10:00:00Z", 60.0, 10.0, 0),
            new MetricsSnapshot.Point("2026-09-08T12:00:00Z", 70.0, 11.0, 0),
            new MetricsSnapshot.Point("2026-09-08T14:00:00Z", 80.0, 12.0, 0),
            new MetricsSnapshot.Point("2026-09-08T16:00:00Z", 90.0, 13.0, 0),
            new MetricsSnapshot.Point("2026-09-08T17:59:00Z", 95.0, 14.0, 0)
        );
    }

    private static MetricsSnapshot daySnapshot() {
        return new MetricsSnapshot(
            "nginx-busy", "K8S_CLUSTER", "Nginx Busy (K3s)",
            "2026-09-08T17:59:00Z", "2026-09-08T00:00:00Z", "2026-09-08T17:59:00Z",
            3600, null, dayPoints());
    }

    /** Verifies aligned window end maps wall clock onto snapshot day. */
    @Test
    void alignedWindowEndMapsWallClockOntoSnapshotDay() {
        MetricsSnapshot snap = daySnapshot();

        assertEquals(Instant.parse("2026-09-08T10:40:00Z"),
            SnapshotReplayer.alignedWindowEnd(snap, Instant.parse("2026-09-09T10:40:37Z")));
        assertEquals(Instant.parse("2026-09-08T17:59:00Z"),
            SnapshotReplayer.alignedWindowEnd(snap, Instant.parse("2026-09-09T19:00:00Z")));
        assertEquals(Instant.parse("2026-09-08T06:30:00Z"),
            SnapshotReplayer.alignedWindowEnd(snap, Instant.parse("2026-09-08T06:30:00Z")));
    }

    /** Verifies replay window tracks now clock on the snapshot day. */
    @Test
    void replayWindowTracksNowClockOnTheSnapshotDay() throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir.toString());
        store.write(daySnapshot());

        MockMetricsRepository repo = new MockMetricsRepository(store, "nginx-busy");
        repo.loadKubeSnapshot();

        Instant expectedEnd = SnapshotReplayer.alignedWindowEnd(daySnapshot(), Instant.now());
        List<Instant> expected = daySnapshot().dataPoints().stream()
                .map(p -> Instant.parse(p.timestamp()))
                .filter(ts -> !ts.isBefore(expectedEnd.minus(Duration.ofHours(3)))
                        && !ts.isAfter(expectedEnd))
                .sorted()
                .toList();

        List<MetricPoint> served = repo.getAllMetrics("nginx-busy", Duration.ofHours(3));
        assertEquals(expected, served.stream().map(MetricPoint::timestamp).toList());
    }

    /** Verifies weekend tiles render idle CPU and no requests. */
    @Test
    void weekendTilesRenderIdleUsage() {
        MetricsSnapshot snap = daySnapshot();
        Instant end = Instant.parse("2026-09-08T17:59:00Z");
        Instant start = end.minus(Duration.ofDays(4)); // Fri..Tue, spans Sat/Sun

        List<MetricPoint> points = SnapshotReplayer.samplesForRange(snap, start, end, "nginx-busy");

        assertTrue(points.stream().anyMatch(p -> isWeekend(p.timestamp())));
        assertTrue(points.stream().filter(p -> isWeekend(p.timestamp()))
                .allMatch(p -> p.cpuUtilization() == 0.0 && p.activeRequestCount() == 0));
        assertTrue(points.stream().filter(p -> !isWeekend(p.timestamp()))
                .anyMatch(p -> p.cpuUtilization() > 0.0));
    }

    /** Verifies low cpu samples get randomized baseline usage. */
    @Test
    void lowCpuSamplesGetRandomizedBaselineUsage() {
        MetricsSnapshot snap = new MetricsSnapshot(
            "nginx-busy", "K8S_CLUSTER", "Nginx Busy (K3s)",
            "2026-09-08T09:00:00Z", "2026-09-08T05:00:00Z", "2026-09-08T07:00:00Z",
            3600, null, List.of(
                new MetricsSnapshot.Point("2026-09-08T05:00:00Z", 0.0, 5.0, 0),
                new MetricsSnapshot.Point("2026-09-08T06:00:00Z", 3.0, 5.0, 0),
                new MetricsSnapshot.Point("2026-09-08T07:00:00Z", 5.5, 5.0, 0)
            )
        );
        Instant start = Instant.parse("2026-09-08T04:30:00Z");
        Instant end = Instant.parse("2026-09-08T07:30:00Z");

        List<MetricPoint> points = SnapshotReplayer.samplesForRange(snap, start, end, "nginx-busy");

        assertFalse(points.isEmpty());
        assertTrue(points.stream().allMatch(p -> p.cpuUtilization() >= 7.0 && p.cpuUtilization() <= 15.0));
    }

    /** Verifies peak config for nginx busy matches deployed schedule. */
    @Test
    void peakConfigForNginxBusyMatchesDeployedSchedule() {
        MockMetricsRepository repo = new MockMetricsRepository(new SnapshotStore(tempDir.toString()), "nginx-busy");
        PeakHoursConfig cfg = repo.getPeakHoursConfig("nginx-busy");
        assertEquals(LocalTime.of(5, 0), cfg.peakStart());
        assertEquals(LocalTime.of(16, 0), cfg.peakEnd());
        assertEquals(List.of(1, 2, 3, 4, 5), cfg.peakDaysOfWeek());
        assertEquals(50.0, cfg.peakTargetUtilization(), 0.001);
        assertEquals(18.0, cfg.offPeakTargetUtilization(), 0.001);
    }

    private static boolean isWeekend(Instant ts) {
        int dow = ts.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue();
        return dow == 6 || dow == 7;
    }

    /** Verifies mock exposes only the kubernetes cluster. */
    @Test
    void mockExposesOnlyTheKubernetesCluster() {
        MetricsRepository repo = new MockMetricsRepository(new SnapshotStore(tempDir.toString()), "nginx-busy");
        assertEquals(List.of("nginx-busy"), repo.getMonitoredResourceIds());
    }

    /** Verifies mock replays downloaded snapshot and its current config. */
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
        assertFalse(points.isEmpty());
        assertTrue(points.stream().allMatch(p -> Math.abs(p.cpuUtilization() - 10.0) < 0.001
                || Math.abs(p.cpuUtilization() - 30.0) < 0.001));

        CurrentConfig discovered = repo.getCurrentConfig("nginx-busy");
        assertTrue(discovered.available());
        assertEquals(3, discovered.replicas());
        assertEquals(4.0, discovered.nodeCpuCores(), 0.001);
    }
}