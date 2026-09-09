package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import org.springframework.stereotype.Service;

/**
 * Provides baseline monthly costs and discovered Kubernetes capacity estimates.
 *
 * <p>The Kubernetes estimate is an intentionally coarse compute-only estimate:
 * {@code nodeCpuCores} is the total capacity across all discovered nodes, so it
 * must not be multiplied by {@code nodeCount}. SKU, region, control-plane,
 * storage, and networking charges are outside this estimate.</p>
 */
@Service
public class CostEstimateService {

    private static final double K8S_MONTHLY_COST_PER_CORE_USD = 40.0;

    /** Returns the flat monthly estimate for a resource family, in USD. */
    public double estimateMonthlyCost(String resourceType) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" -> 2400.00;
            case "AZURE_VM" -> 560.00;
            case "APP_SERVICE" -> 380.00;
            case "AZURE_FUNCTION" -> 120.00;
            default -> 200.00;
        };
    }

    /** Uses total discovered node cores for Kubernetes, otherwise the family baseline. */
    public double estimateMonthlyCost(CurrentConfig currentConfig, String resourceType) {
        if (isKubernetes(resourceType)
                && currentConfig != null
                && currentConfig.available()
                && currentConfig.nodeCpuCores() > 0) {
            return currentConfig.nodeCpuCores() * K8S_MONTHLY_COST_PER_CORE_USD;
        }
        return estimateMonthlyCost(resourceType);
    }

    /** Identifies resource types whose cost can be derived from node capacity. */
    private static boolean isKubernetes(String resourceType) {
        return "AKS_CLUSTER".equals(resourceType) || "K8S_CLUSTER".equals(resourceType);
    }
}