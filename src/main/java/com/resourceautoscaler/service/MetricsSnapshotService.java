package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.MetricsSnapshot;
import com.resourceautoscaler.repository.MetricsRepository;
import com.resourceautoscaler.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

/**
 * Downloads raw metrics for an explicit window and persists them as a readable
 * JSON snapshot that other profiles can replay.
 */
@Service
public class MetricsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(MetricsSnapshotService.class);

    private static final int EXPORT_STEP_SECONDS = 60;

    private final MetricsRepository metricsRepository;
    private final SnapshotStore snapshotStore;

    /** Coordinates raw metric export with local snapshot persistence. */
    public MetricsSnapshotService(MetricsRepository metricsRepository, SnapshotStore snapshotStore) {
        this.metricsRepository = metricsRepository;
        this.snapshotStore = snapshotStore;
    }

    /** Downloads raw samples and writes a replayable JSON snapshot for the exact window. */
    public SnapshotDownload export(String resourceId, Instant start, Instant end) {
        List<MetricPoint> points = metricsRepository.downloadRawMetrics(resourceId, start, end)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No raw metrics source available for resource " + resourceId));

        CurrentConfig currentConfig = metricsRepository.getCurrentConfig(resourceId);
        String resourceType = points.isEmpty() ? "UNKNOWN" : points.getFirst().resourceType();

        List<MetricsSnapshot.Point> dataPoints = points.stream()
                .map(p -> new MetricsSnapshot.Point(
                        p.timestamp().toString(),
                        p.cpuUtilization(),
                        p.memoryUtilization(),
                        p.activeRequestCount()))
                .toList();

        MetricsSnapshot snapshot = new MetricsSnapshot(
                resourceId,
                resourceType,
                resourceName(resourceId),
                Instant.now().toString(),
                start.toString(),
                end.toString(),
                EXPORT_STEP_SECONDS,
                currentConfig,
                dataPoints
        );

        try {
            var file = snapshotStore.write(snapshot);
            log.info("Exported metrics snapshot for {} ({} points, {} -> {}) to {}",
                    resourceId, dataPoints.size(), start, end, file);
            return new SnapshotDownload(file.toString(), dataPoints.size(), snapshot.downloadedAt());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write metrics snapshot for " + resourceId, e);
        }
    }

    /** Result returned after a snapshot file has been persisted. */
    public record SnapshotDownload(String file, int pointCount, String downloadedAt) {}

    /** Supplies the display name embedded in exported snapshots. */
    private String resourceName(String resourceId) {
        return switch (resourceId) {
            case "nginx-busy" -> "Nginx Busy (K3s)";
            case "nginx-idle" -> "Nginx Idle (K3s)";
            default -> resourceId;
        };
    }
}