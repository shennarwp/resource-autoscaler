package com.resourceautoscaler.controller;

import com.resourceautoscaler.service.MetricsSnapshotService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/metrics")
public class SnapshotController {

    private final MetricsSnapshotService snapshotService;

    public SnapshotController(MetricsSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @PostMapping("/{resourceId}/snapshot")
    public SnapshotResponse downloadSnapshot(
            @PathVariable String resourceId,
            @RequestParam Instant start,
            @RequestParam Instant end
    ) {
        MetricsSnapshotService.SnapshotDownload download = snapshotService.export(resourceId, start, end);
        return new SnapshotResponse(resourceId, download.file(), download.pointCount(), download.downloadedAt());
    }

    public record SnapshotResponse(String resourceId, String file, int pointCount, String downloadedAt) {}
}