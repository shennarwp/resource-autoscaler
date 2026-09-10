package com.resourceautoscaler.controller;

import com.resourceautoscaler.service.MetricsSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests the snapshot controller behavior and regression cases. */
class SnapshotControllerTest {

    private final MetricsSnapshotService snapshotService = mock(MetricsSnapshotService.class);
    private final SnapshotController controller = new SnapshotController(snapshotService);

    /** Verifies valid resource ID delegates to the snapshot service. */
    @Test
    void validResourceIdDelegatesToSnapshotService() {
        Instant start = Instant.parse("2026-09-08T05:00:00Z");
        Instant end = Instant.parse("2026-09-08T09:00:00Z");
        when(snapshotService.export("nginx-busy", start, end))
            .thenReturn(new MetricsSnapshotService.SnapshotDownload("/tmp/nginx-busy.json", 1439, "2026-09-08T09:00:00Z"));

        SnapshotController.SnapshotResponse response =
            controller.downloadSnapshot("nginx-busy", start, end);

        assertEquals("nginx-busy", response.resourceId());
        assertEquals("/tmp/nginx-busy.json", response.file());
        assertEquals(1439, response.pointCount());
        verify(snapshotService).export("nginx-busy", start, end);
    }

    /** Verifies resource IDs with dots and hyphens are accepted. */
    @ParameterizedTest
    @ValueSource(strings = {"vm-backend-01", "appservice-api-gateway", "function.data_processor", "AKS1"})
    void validResourceIdsAreAccepted(String resourceId) {
        Instant start = Instant.parse("2026-09-08T05:00:00Z");
        Instant end = Instant.parse("2026-09-08T09:00:00Z");
        when(snapshotService.export(eq(resourceId), any(), any()))
            .thenReturn(new MetricsSnapshotService.SnapshotDownload("/tmp/test.json", 0, "2026-09-08T09:00:00Z"));

        controller.downloadSnapshot(resourceId, start, end);

        verify(snapshotService).export(eq(resourceId), any(), any());
    }

    /** Verifies path traversal attempts are rejected. */
    @ParameterizedTest
    @ValueSource(strings = {
        "../etc/passwd",
        "nginx-busy/../../../etc/shadow",
        "../../secret",
        "a/b/c",
        "../"
    })
    void pathTraversalIsRejected(String resourceId) {
        assertThrows(IllegalArgumentException.class,
            () -> controller.downloadSnapshot(resourceId, Instant.now(), Instant.now()));
    }

    /** Verifies unsafe characters in resource ID are rejected. */
    @ParameterizedTest
    @ValueSource(strings = {
        "nginx-busy'; drop table",
        "test; rm -rf /",
        "test`whoami`",
        "test|cat /etc/passwd",
        "hello world",
        ""
    })
    void unsafeResourceIdIsRejected(String resourceId) {
        assertThrows(IllegalArgumentException.class,
            () -> controller.downloadSnapshot(resourceId, Instant.now(), Instant.now()));
    }

    /** Verifies resource ID starting with invalid character is rejected. */
    @ParameterizedTest
    @ValueSource(strings = {"-leading", ".leading", "_leading", " leading"})
    void resourceIdStartingWithInvalidCharIsRejected(String resourceId) {
        assertThrows(IllegalArgumentException.class,
            () -> controller.downloadSnapshot(resourceId, Instant.now(), Instant.now()));
    }
}
