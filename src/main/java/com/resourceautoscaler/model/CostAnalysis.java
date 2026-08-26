package com.resourceautoscaler.model;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

public record CostAnalysis(
    YearMonth analysisPeriod,
    Instant generatedAt,
    List<ResourceCostBreakdown> resources,
    CostSummary summary
) {
    public record ResourceCostBreakdown(
        String resourceId,
        String resourceName,
        String resourceType,
        double currentMonthlyCostUsd,
        double optimizedMonthlyCostUsd,
        double potentialSavingsUsd,
        double savingsPercentage,
        double currentPeakCpuPercent,
        double currentOffPeakCpuPercent,
        List<String> appliedOptimizations
    ) {}

    public record CostSummary(
        double totalCurrentCostUsd,
        double totalOptimizedCostUsd,
        double totalPotentialSavingsUsd,
        double overallSavingsPercentage,
        int totalResourcesAnalyzed,
        int resourcesWithOptimizations,
        String estimatedAnnualSavingsUsd
    ) {}
}
