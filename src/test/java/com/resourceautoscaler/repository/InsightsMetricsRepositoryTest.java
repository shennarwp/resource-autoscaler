package com.resourceautoscaler.repository;

import com.resourceautoscaler.model.CurrentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightsMetricsRepositoryTest {

    @Test
    void configQueryScopesDeploymentsPodsAndNodesToResource() {
        String query = InsightsMetricsRepository.buildConfigQuery("nginx-busy", "24h", "default");

        assertTrue(query.contains("Name == 'kube_deployment_status_replicas_ready'"));
        assertTrue(query.contains("tags.k8sNamespace) == 'default'"));
        assertTrue(query.contains("tags.deployment) == 'nginx-busy'"));
        assertTrue(query.contains("where Name startswith 'nginx-busy-'"));
        assertTrue(query.contains("CounterName in ('cpuRequestNanoCores','cpuLimitNanoCores','memoryRequestBytes','memoryLimitBytes')"));
        assertTrue(query.contains("CounterName == 'cpuCapacityNanoCores'"));
    }

    @Test
    void exportQueryBindsExplicitExactWindowWithSixtySecondStep() {
        String query = InsightsMetricsRepository.buildExportQuery(
                "nginx-busy", "2026-09-08T05:00:00Z", "2026-09-08T09:00:00Z", 60, "default");

        assertTrue(query.contains("TimeGenerated between (datetime(2026-09-08T05:00:00Z) .. datetime(2026-09-08T09:00:00Z))"));
        assertTrue(query.contains("where Name startswith 'nginx-busy-'"));
        assertTrue(query.contains("CounterName in ('cpuUsageNanoCores','cpuLimitNanoCores','memoryWorkingSetBytes','memoryLimitBytes')"));
        assertTrue(query.contains("by bin(TimeGenerated, 60s)"));
        assertFalse(query.contains("ago("));
    }

    @Test
    void configRowMapsNanocoresAndBytesToCoresAndGiB() {
        CurrentConfig config = InsightsMetricsRepository.currentConfigFromValues(
            "nginx-busy",
            3.0, 2.0,
            25_000_000.0, 150_000_000.0,
            8.0 * 1024 * 1024, 32.0 * 1024 * 1024,
            1.0, 4_062_500_000.0
        );

        assertTrue(config.available());
        assertEquals("nginx-busy", config.resourceId());
        assertEquals(3, config.replicas());
        assertEquals(2, config.availableReplicas());
        assertEquals(0.025, config.cpuRequestCores(), 1e-9);
        assertEquals(0.150, config.cpuLimitCores(), 1e-9);
        assertEquals(8.0 / 1024.0, config.memoryRequestGiB(), 1e-9);
        assertEquals(32.0 / 1024.0, config.memoryLimitGiB(), 1e-9);
        assertEquals(1, config.nodeCount());
        assertEquals(4.0625, config.nodeCpuCores(), 1e-9);
    }

    @Test
    void missingSpecReturnsUnknownConfig() {
        CurrentConfig config = InsightsMetricsRepository.currentConfigFromValues(
            "nginx-busy", null, null, null, null, null, null, null, null);

        assertFalse(config.available());
        assertEquals("nginx-busy", config.resourceId());
        assertEquals(0, config.replicas());
    }

    @Test
    void nullableSeriesColumnsDefaultToZero() {
        CurrentConfig config = InsightsMetricsRepository.currentConfigFromValues(
            "nginx-busy", 3.0, 3.0, null, null, null, null, null, null);

        assertTrue(config.available());
        assertEquals(3, config.replicas());
        assertEquals(0.0, config.cpuRequestCores(), 0.0);
        assertEquals(0.0, config.cpuLimitCores(), 0.0);
        assertEquals(0.0, config.memoryRequestGiB(), 0.0);
        assertEquals(0.0, config.memoryLimitGiB(), 0.0);
        assertEquals(0, config.nodeCount());
        assertEquals(0.0, config.nodeCpuCores(), 0.0);
    }
}