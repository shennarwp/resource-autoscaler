package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import org.springframework.stereotype.Service;

/** Provides baseline monthly costs and discovered Kubernetes capacity estimates. */
@Service
public class CostEstimateService {

    static final double HOURLY_RATE_PER_CORE_USD = 0.18;
    static final double HOURS_PER_MONTH = 730;
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

    /** Uses discovered node cores for Kubernetes, otherwise the family baseline. */
    public double estimateMonthlyCost(CurrentConfig currentConfig, String resourceType) {
        if (isKubernetes(resourceType)
                && currentConfig != null
                && currentConfig.available()
                && currentConfig.nodeCpuCores() > 0) {
            double coreCount = Math.max(1.0, currentConfig.nodeCpuCores());
            double nodeCount = Math.max(1.0, currentConfig.nodeCount());
            return coreCount * nodeCount * K8S_MONTHLY_COST_PER_CORE_USD;
        }
        return estimateMonthlyCost(resourceType);
    }

    /** Identifies resource types whose cost can be derived from node capacity. */
    private static boolean isKubernetes(String resourceType) {
        return "AKS_CLUSTER".equals(resourceType) || "K8S_CLUSTER".equals(resourceType);
    }
}