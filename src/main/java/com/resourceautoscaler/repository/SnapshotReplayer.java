package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.MetricsSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Replays a downloaded {@link MetricsSnapshot} for an arbitrary requested window:
 * aligns wall-clock "now" onto the snapshot's last day, tiles the snapshot's span
 * to cover longer ranges, downsamples to a range-appropriate cadence, renders
 * weekends as idle, and adds a small randomized baseline to near-zero CPU samples
 * so replayed charts are never a flat, misleading zero.
 * <p>
 * Extracted from {@link MockMetricsRepository} so the replay algorithm can be
 * read, tested, and changed independently of the mock repository's simpler
 * synthetic (sine-wave) generation path.
 */
final class SnapshotReplayer {

    private SnapshotReplayer() {}

    /**
     * Maps the wall-clock time of {@code now} onto the snapshot's last day so the
     * replay window tracks tick-by-tick clock alignment (e.g. "now, 3h back")
     * against the downloaded sample day, rather than always ending at the snapshot's
     * newest data point. If now's clock time is past the newest data point, the end
     * is clamped to it.
     */
    static Instant alignedWindowEnd(MetricsSnapshot snapshot, Instant now) {
        List<MetricsSnapshot.Point> points = snapshot.dataPoints();
        if (points == null || points.isEmpty()) {
            return now;
        }
        Instant last = Instant.parse(points.getLast().timestamp());
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
     * random 7-15% baseline so idle points are never a flat 0.
     */
    static List<MetricPoint> samplesForRange(MetricsSnapshot snapshot, Instant start, Instant end, String resourceId) {
        List<MetricsSnapshot.Point> points = snapshot.dataPoints();
        if (points == null || points.isEmpty()) {
            return List.of();
        }

        Instant first = Instant.parse(points.getFirst().timestamp());
        Instant last = Instant.parse(points.getLast().timestamp());
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
    static long stepSecondsForRange(Duration range) {
        long seconds = range.getSeconds();
        if (seconds < 3600) return 60;
        if (seconds < 3 * 86400) return 300;
        return 3600;
    }
}