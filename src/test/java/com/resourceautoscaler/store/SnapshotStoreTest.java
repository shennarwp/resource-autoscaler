package com.resourceautoscaler.store;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricsSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the snapshot store behavior and regression cases. */
class SnapshotStoreTest {

    @TempDir
    Path tempDir;

    /** Verifies writes and reads snapshot round trip. */
    @Test
    void writesAndReadsSnapshotRoundTrip() throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir.toString());

        List<MetricsSnapshot.Point> points = List.of(
            new MetricsSnapshot.Point("2026-09-08T05:00:00Z", 61.5, 20.0, 0),
            new MetricsSnapshot.Point("2026-09-08T05:01:00Z", 70.2, 21.0, 0)
        );
        CurrentConfig config = new CurrentConfig(
            "nginx-busy", 3, 3, 0.025, 0.150, 8.0 / 1024.0, 32.0 / 1024.0, 1, 4.0, true);
        MetricsSnapshot snapshot = new MetricsSnapshot(
            "nginx-busy", "K8S_CLUSTER", "Nginx Busy (K3s)",
            "2026-09-08T09:00:00Z", "2026-09-08T05:00:00Z", "2026-09-08T09:00:00Z",
            60, config, points);

        Path file = store.write(snapshot);

        assertTrue(Files.exists(file));
        assertTrue(new String(Files.readAllBytes(file)).contains("\"dataPoints\""));
        assertEquals(snapshot, store.read("nginx-busy"));
    }

    /** Verifies read missing file returns null. */
    @Test
    void readMissingFileReturnsNull() {
        SnapshotStore store = new SnapshotStore(tempDir.toString());
        assertNull(store.read("nginx-busy"));
    }
}