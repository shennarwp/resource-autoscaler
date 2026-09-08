package com.resourceautoscaler.service;

import org.springframework.stereotype.Service;

@Service
public class CostEstimateService {

    public double estimateMonthlyCost(String resourceType) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" -> 2400.00;
            case "AZURE_VM" -> 560.00;
            case "APP_SERVICE" -> 380.00;
            case "AZURE_FUNCTION" -> 120.00;
            default -> 200.00;
        };
    }
}