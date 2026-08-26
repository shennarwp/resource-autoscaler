package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CostAnalysis;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class CostOptimizationService {

    private final MetricsCollectionService metricsService;
    private final AnalysisService analysisService;

    public CostOptimizationService(
            MetricsCollectionService metricsService,
            AnalysisService analysisService
    ) {
        this.metricsService = metricsService;
        this.analysisService = analysisService;
    }

    public CostAnalysis generateCostAnalysis() {
        List<String> resources = metricsService.getMonitoredResources();
        List<CostAnalysis.ResourceCostBreakdown> breakdowns = new ArrayList<>();

        double totalCurrent = 0;
        double totalOptimized = 0;
        int optimizationsCount = 0;

        for (String resourceId : resources) {
            ResourceMetrics metrics = metricsService.collectMetrics(resourceId, 30);
            PeakHoursConfig config = new PeakHoursConfig(
                java.time.LocalTime.of(7, 0), java.time.LocalTime.of(18, 0),
                List.of(1, 2, 3, 4, 5), 65.0, 10.0, 15
            );

            double monthlyCost = estimateMonthlyCost(metrics.resourceType());
            List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(metrics, config, monthlyCost);

            double optimizedCost = monthlyCost;
            List<String> optimizations = new ArrayList<>();
            for (ScalingRecommendation rec : recs) {
                optimizedCost -= rec.estimatedMonthlySavingsUsd();
                optimizations.add(rec.recommendationType().name() + ": " + rec.rationale().substring(0, Math.min(80, rec.rationale().length())) + "...");
            }

            if (!recs.isEmpty()) {
                optimizationsCount++;
            }

            breakdowns.add(new CostAnalysis.ResourceCostBreakdown(
                resourceId,
                metrics.resourceName(),
                metrics.resourceType(),
                monthlyCost,
                optimizedCost,
                monthlyCost - optimizedCost,
                monthlyCost > 0 ? ((monthlyCost - optimizedCost) / monthlyCost) * 100 : 0,
                metrics.aggregated().peakHourUtilization(),
                metrics.aggregated().offPeakHourUtilization(),
                optimizations
            ));

            totalCurrent += monthlyCost;
            totalOptimized += optimizedCost;
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

    private double estimateMonthlyCost(String resourceType) {
        return switch (resourceType) {
            case "AKS_CLUSTER" -> 2400.00;
            case "AZURE_VM" -> 560.00;
            case "APP_SERVICE" -> 380.00;
            case "AZURE_FUNCTION" -> 120.00;
            default -> 200.00;
        };
    }
}
