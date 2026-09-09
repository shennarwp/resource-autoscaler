package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the cost estimate service behavior and regression cases. */
class CostEstimateServiceTest {

    private final CostEstimateService service = new CostEstimateService();

    /** Verifies estimates cost by resource type. */
    @Test
    void estimatesCostByResourceType() {
        assertEquals(2400.00, service.estimateMonthlyCost("AKS_CLUSTER"), 0.001);
        assertEquals(2400.00, service.estimateMonthlyCost("K8S_CLUSTER"), 0.001);
        assertEquals(560.00, service.estimateMonthlyCost("AZURE_VM"), 0.001);
        assertEquals(380.00, service.estimateMonthlyCost("APP_SERVICE"), 0.001);
        assertEquals(120.00, service.estimateMonthlyCost("AZURE_FUNCTION"), 0.001);
        assertEquals(200.00, service.estimateMonthlyCost("UNKNOWN"), 0.001);
    }

    /** Verifies kubernetes cluster cost is derived from discovered node capacity. */
    @Test
    void kubernetesClusterCostIsDerivedFromDiscoveredNodeCapacity() {
        CurrentConfig k3s = new CurrentConfig("nginx-busy", 3, 3, 0.025, 0.150, 0.008, 0.032, 1, 4.0625, true);
        double expected = 4.0625 * 1.0 * 40.0;

        assertEquals(expected, service.estimateMonthlyCost(k3s, "K8S_CLUSTER"), 0.001);
    }

    /** Verifies total discovered node capacity is not multiplied by node count twice. */
    @Test
    void kubernetesClusterCostUsesTotalDiscoveredNodeCapacityOnce() {
        CurrentConfig cluster = new CurrentConfig(
            "aks-primary-cluster", 3, 3, 1.0, 2.0, 2.0, 4.0, 2, 8.0, true);

        assertEquals(320.0, service.estimateMonthlyCost(cluster, "AKS_CLUSTER"), 0.001);
    }

    /** Verifies kubernetes cluster without discovered nodes falls back to flat estimate. */
    @Test
    void kubernetesClusterWithoutDiscoveredNodesFallsBackToFlatEstimate() {
        assertEquals(2400.00,
            service.estimateMonthlyCost(CurrentConfig.unknown("nginx-busy"), "K8S_CLUSTER"), 0.001);
    }
}