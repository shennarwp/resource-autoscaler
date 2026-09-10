package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CostAnalysis;
import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Combines collection, analysis, and pricing into a cost comparison. */
@Service
public class CostOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(CostOptimizationService.class);

    private final RecommendationPipelineService pipeline;

    /** Wires the services used to calculate resource-level savings. */
    public CostOptimizationService(
        RecommendationPipelineService pipeline
    ) {
        this.pipeline = pipeline;
    }

    /** Analyzes every monitored resource over 30 days and totals projected savings. */
    public CostAnalysis generateCostAnalysis() {
        List<String> resources = pipeline.getMonitoredResources();
        List<CostAnalysis.ResourceCostBreakdown> breakdowns = new ArrayList<>();

        double totalCurrent = 0;
        double totalOptimized = 0;
        int optimizationsCount = 0;

        for (String resourceId : resources) {
            try {
                var result = pipeline.recommend(resourceId, 30);
                double monthlyCost = result.monthlyCostUsd();
                double optimizedCost = monthlyCost;
                List<String> optimizations = new ArrayList<>();
                for (ScalingRecommendation rec : result.recommendations()) {
                    double savings = Math.max(0.0, rec.estimatedMonthlySavingsUsd());
                    optimizedCost = Math.max(0.0, optimizedCost - savings);
                    optimizations.add(rec.recommendationType().name() + ": "
                            + rec.rationale().substring(0, Math.min(80, rec.rationale().length())) + "...");
                }
                if (!result.recommendations().isEmpty()) optimizationsCount++;

                double potentialSavings = Math.max(0.0, monthlyCost - optimizedCost);
                breakdowns.add(new CostAnalysis.ResourceCostBreakdown(
                        resourceId,
                        result.metrics().resourceName(),
                        result.metrics().resourceType(),
                        monthlyCost, optimizedCost, potentialSavings,
                        monthlyCost > 0 ? (potentialSavings / monthlyCost) * 100 : 0,
                        result.metrics().aggregated().peakHourUtilization(),
                        result.metrics().aggregated().offPeakHourUtilization(),
                        optimizations
                ));
                totalCurrent += monthlyCost;
                totalOptimized += optimizedCost;
            } catch (Exception e) {
                log.warn("Skipping resource {} due to error: {}", resourceId, e.getMessage());
            }
        }

        return new CostAnalysis(
            YearMonth.now(),
            Instant.now(),
            breakdowns,
            new CostAnalysis.CostSummary(
                totalCurrent,
                totalOptimized,
                totalCurrent - totalOptimized,
                totalCurrent > 0 ? ((totalCurrent - totalOptimized) / totalCurrent) * 100 : 0,
                resources.size(),
                optimizationsCount,
                String.format("%.2f", (totalCurrent - totalOptimized) * 12)
            )
        );
    }
}
