package com.resourceautoscaler.model;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/** Cost comparison produced for all resources in the active repository. */
public record CostAnalysis(
    YearMonth analysisPeriod,
    Instant generatedAt,
    List<ResourceCostBreakdown> resources,
    CostSummary summary
) {
    /** Per-resource current cost, optimized cost, utilization, and savings detail. */
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

    /** Totals and counts displayed by the dashboard summary. */
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
