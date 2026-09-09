package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostEstimateServiceTest {

    private final CostEstimateService service = new CostEstimateService();

    @Test
    void estimatesCostByResourceType() {
        assertEquals(2400.00, service.estimateMonthlyCost("AKS_CLUSTER"), 0.001);
        assertEquals(2400.00, service.estimateMonthlyCost("K8S_CLUSTER"), 0.001);
        assertEquals(560.00, service.estimateMonthlyCost("AZURE_VM"), 0.001);
        assertEquals(380.00, service.estimateMonthlyCost("APP_SERVICE"), 0.001);
        assertEquals(120.00, service.estimateMonthlyCost("AZURE_FUNCTION"), 0.001);
        assertEquals(200.00, service.estimateMonthlyCost("UNKNOWN"), 0.001);
    }

    @Test
    void kubernetesClusterCostIsDerivedFromDiscoveredNodeCapacity() {
        CurrentConfig k3s = new CurrentConfig("nginx-busy", 3, 3, 0.025, 0.150, 0.008, 0.032, 1, 4.0625, true);
        double expected = 4.0625 * 1.0 * 40.0;

        assertEquals(expected, service.estimateMonthlyCost(k3s, "K8S_CLUSTER"), 0.001);
    }

    @Test
    void kubernetesClusterWithoutDiscoveredNodesFallsBackToFlatEstimate() {
        assertEquals(2400.00,
            service.estimateMonthlyCost(CurrentConfig.unknown("nginx-busy"), "K8S_CLUSTER"), 0.001);
    }
}