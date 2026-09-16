package com.resourceautoscaler.controller;

import com.resourceautoscaler.service.MetricsCollectionService;
import com.resourceautoscaler.service.MetricsSnapshotService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Endpoint for exporting an explicit raw-metrics window to a local snapshot. */
@RestController
@Validated
@RequestMapping("/api/v1/metrics")
public class SnapshotController {

    private static final String RESOURCE_ID_PATTERN_STRING = "[A-Za-z0-9][A-Za-z0-9._-]*";
    private static final java.util.regex.Pattern RESOURCE_ID_PATTERN =
            java.util.regex.Pattern.compile(RESOURCE_ID_PATTERN_STRING);

    private final MetricsSnapshotService snapshotService;
    private final MetricsCollectionService metricsService;

    /** Injects the services for snapshot export and cache eviction. */
    public SnapshotController(MetricsSnapshotService snapshotService, MetricsCollectionService metricsService) {
        this.snapshotService = snapshotService;
        this.metricsService = metricsService;
    }

    /** Exports the inclusive {@code start}--{@code end} interval for replay. */
    @PostMapping("/{resourceId}/snapshot")
    public SnapshotResponse downloadSnapshot(
            @PathVariable @Pattern(regexp = RESOURCE_ID_PATTERN_STRING) String resourceId,
            @RequestParam Instant start,
            @RequestParam Instant end
    ) {
        if (resourceId == null || !RESOURCE_ID_PATTERN.matcher(resourceId).matches()) {
            throw new IllegalArgumentException("Invalid resource ID");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Snapshot start must be before end");
        }
        MetricsSnapshotService.SnapshotDownload download = snapshotService.export(resourceId, start, end);
        metricsService.evictMetricsCache(resourceId, 30);
        return new SnapshotResponse(resourceId, download.file(), download.pointCount(), download.downloadedAt());
    }

    /** JSON result describing the persisted snapshot file and sample count. */
    public record SnapshotResponse(String resourceId, String file, int pointCount, String downloadedAt) {}
}