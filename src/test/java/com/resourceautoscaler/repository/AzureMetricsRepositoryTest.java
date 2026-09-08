package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.MetricPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AzureMetricsRepositoryTest {

    @Test
    void mergeByTimestampAlignsSeriesThatDriftApart() {
        Instant t0 = Instant.parse("2026-09-08T10:00:00Z");
        Instant t1 = Instant.parse("2026-09-08T11:00:00Z");
        Instant t2 = Instant.parse("2026-09-08T12:00:00Z");

        List<MetricPoint> cpu = List.of(
            point(t0, 80), point(t2, 90)
        );
        List<MetricPoint> mem = List.of(
            memoryPoint(t1, 40)
        );
        List<MetricPoint> req = List.of(
            requestPoint(t0, 100), requestPoint(t1, 200), requestPoint(t2, 300)
        );

        List<MetricPoint> merged =
            AzureMetricsRepository.mergeByTimestamp("autoscaler-busy", "APP_SERVICE", cpu, mem, req);

        assertEquals(3, merged.size());
        assertEquals(t0, merged.get(0).timestamp());
        assertEquals(80, merged.get(0).cpuUtilization(), 0.001);
        assertEquals(0, merged.get(0).memoryUtilization(), 0.001);
        assertEquals(100, merged.get(0).activeRequestCount());
        assertEquals(t1, merged.get(1).timestamp());
        assertEquals(0, merged.get(1).cpuUtilization(), 0.001);
        assertEquals(40, merged.get(1).memoryUtilization(), 0.001);
        assertEquals(200, merged.get(1).activeRequestCount());
        assertEquals(t2, merged.get(2).timestamp());
        assertEquals(90, merged.get(2).cpuUtilization(), 0.001);
        assertEquals(0, merged.get(2).memoryUtilization(), 0.001);
        assertEquals(300, merged.get(2).activeRequestCount());
    }

    private MetricPoint point(Instant timestamp, double cpu) {
        return new MetricPoint(timestamp, cpu, 0, 0, "autoscaler-busy", "APP_SERVICE");
    }

    private MetricPoint requestPoint(Instant timestamp, double requests) {
        return new MetricPoint(timestamp, requests, 0, (int) requests, "autoscaler-busy", "APP_SERVICE");
    }

    private MetricPoint memoryPoint(Instant timestamp, double memory) {
        return new MetricPoint(timestamp, 0, memory, 0, "autoscaler-busy", "APP_SERVICE");
    }
}