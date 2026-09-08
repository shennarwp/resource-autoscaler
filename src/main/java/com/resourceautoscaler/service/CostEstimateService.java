package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import org.springframework.stereotype.Service;

@Service
public class CostEstimateService {

    static final double HOURLY_RATE_PER_CORE_USD = 0.18;
    static final double HOURS_PER_MONTH = 730;

    public double estimateMonthlyCost(String resourceType) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" -> 2400.00;
            case "AZURE_VM" -> 560.00;
            case "APP_SERVICE" -> 380.00;
            case "AZURE_FUNCTION" -> 120.00;
            default -> 200.00;
        };
    }

    public double estimateMonthlyCost(CurrentConfig currentConfig, String resourceType) {
        if (isKubernetes(resourceType)
                && currentConfig != null
                && currentConfig.available()
                && currentConfig.nodeCpuCores() > 0) {
            return currentConfig.nodeCpuCores() * HOURLY_RATE_PER_CORE_USD * HOURS_PER_MONTH;
        }
        return estimateMonthlyCost(resourceType);
    }

    private static boolean isKubernetes(String resourceType) {
        return "AKS_CLUSTER".equals(resourceType) || "K8S_CLUSTER".equals(resourceType);
    }
}