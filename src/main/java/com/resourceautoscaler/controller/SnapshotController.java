package com.resourceautoscaler.controller;

import com.resourceautoscaler.service.MetricsSnapshotService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Endpoint for exporting an explicit raw-metrics window to a local snapshot. */
@RestController
@RequestMapping("/api/v1/metrics")
public class SnapshotController {

    private final MetricsSnapshotService snapshotService;

    /** Injects the service that downloads and persists raw metric snapshots. */
    public SnapshotController(MetricsSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /** Exports the inclusive {@code start}--{@code end} interval for replay. */
    @PostMapping("/{resourceId}/snapshot")
    public SnapshotResponse downloadSnapshot(
            @PathVariable String resourceId,
            @RequestParam Instant start,
            @RequestParam Instant end
    ) {
        MetricsSnapshotService.SnapshotDownload download = snapshotService.export(resourceId, start, end);
        return new SnapshotResponse(resourceId, download.file(), download.pointCount(), download.downloadedAt());
    }

    /** JSON result describing the persisted snapshot file and sample count. */
    public record SnapshotResponse(String resourceId, String file, int pointCount, String downloadedAt) {}
}