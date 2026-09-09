package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.MetricsSnapshot;
import com.resourceautoscaler.repository.MetricsRepository;
import com.resourceautoscaler.store.SnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the metrics snapshot service behavior and regression cases. */
class MetricsSnapshotServiceTest {

    @TempDir
    Path tempDir;

    /** Verifies export writes readable snapshot file. */
    @Test
    void exportWritesReadableSnapshotFile() {
        MetricsRepository repo = mock(MetricsRepository.class);
        Instant start = Instant.parse("2026-09-08T05:00:00Z");
        Instant end = Instant.parse("2026-09-08T09:00:00Z");
        List<MetricPoint> points = List.of(
            new MetricPoint(start, 61.5, 20.0, 0, "nginx-busy", "K8S_CLUSTER"),
            new MetricPoint(start.plusSeconds(60), 62.1, 21.0, 0, "nginx-busy", "K8S_CLUSTER")
        );
        when(repo.downloadRawMetrics("nginx-busy", start, end)).thenReturn(Optional.of(points));
        when(repo.getCurrentConfig("nginx-busy"))
            .thenReturn(new CurrentConfig("nginx-busy", 3, 3, 0.025, 0.150, 0.0078125, 0.03125, 1, 4.0, true));

        MetricsSnapshotService service = new MetricsSnapshotService(repo, new SnapshotStore(tempDir.toString()));
        MetricsSnapshotService.SnapshotDownload download = service.export("nginx-busy", start, end);

        assertTrue(Files.exists(Path.of(download.file())));
        assertEquals(2, download.pointCount());

        MetricsSnapshot loaded = new SnapshotStore(tempDir.toString()).read("nginx-busy");
        assertNotNull(loaded);
        assertEquals("nginx-busy", loaded.resourceId());
        assertEquals("K8S_CLUSTER", loaded.resourceType());
        assertEquals("Nginx Busy (K3s)", loaded.resourceName());
        assertEquals(60, loaded.stepSeconds());
        assertEquals(2, loaded.dataPoints().size());
        assertEquals(61.5, loaded.dataPoints().get(0).cpuUtilization(), 0.001);
        assertEquals("2026-09-08T05:00:00Z", loaded.start());
        assertEquals("2026-09-08T09:00:00Z", loaded.end());
        assertNotNull(loaded.currentConfig());
        assertTrue(loaded.currentConfig().available());
        assertEquals(3, loaded.currentConfig().replicas());
    }

    /** Verifies export throws when no raw source supports resource. */
    @Test
    void exportThrowsWhenNoRawSourceSupportsResource() {
        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.downloadRawMetrics(anyString(), any(), any())).thenReturn(Optional.empty());

        MetricsSnapshotService service = new MetricsSnapshotService(repo, new SnapshotStore(tempDir.toString()));
        assertThrows(IllegalArgumentException.class, () ->
                service.export("app-xyz", Instant.parse("2026-09-08T05:00:00Z"), Instant.parse("2026-09-08T06:00:00Z")));
        assertFalse(Files.exists(Path.of(tempDir.toString(), "app-xyz.json")));
    }
}