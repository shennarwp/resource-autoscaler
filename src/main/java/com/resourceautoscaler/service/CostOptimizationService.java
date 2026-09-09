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

    private final MetricsCollectionService metricsService;
    private final AnalysisService analysisService;
    private final CostEstimateService costEstimateService;

    /** Wires the services used to calculate resource-level savings. */
    public CostOptimizationService(
            MetricsCollectionService metricsService,
            AnalysisService analysisService,
            CostEstimateService costEstimateService
    ) {
        this.metricsService = metricsService;
        this.analysisService = analysisService;
        this.costEstimateService = costEstimateService;
    }

    /** Analyzes every monitored resource over 30 days and totals projected savings. */
    public CostAnalysis generateCostAnalysis() {
        List<String> resources = metricsService.getMonitoredResources();
        List<CostAnalysis.ResourceCostBreakdown> breakdowns = new ArrayList<>();

        double totalCurrent = 0;
        double totalOptimized = 0;
        int optimizationsCount = 0;

        for (String resourceId : resources) {
            try {
                ResourceMetrics metrics = metricsService.collectMetrics(resourceId, 30);
                PeakHoursConfig config = metricsService.getPeakHoursConfig(resourceId);
                CurrentConfig currentConfig = metricsService.getCurrentConfig(resourceId);

                double monthlyCost = costEstimateService.estimateMonthlyCost(currentConfig, metrics.resourceType());
                List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(
                    metrics, config, monthlyCost, currentConfig);

                double optimizedCost = monthlyCost;
                List<String> optimizations = new ArrayList<>();
                for (ScalingRecommendation rec : recs) {
                    double savings = Math.max(0.0, rec.estimatedMonthlySavingsUsd());
                    optimizedCost = Math.max(0.0, optimizedCost - savings);
                    optimizations.add(rec.recommendationType().name() + ": " + rec.rationale().substring(0, Math.min(80, rec.rationale().length())) + "...");
                }

                if (!recs.isEmpty()) {
                    optimizationsCount++;
                }

                double potentialSavings = Math.max(0.0, monthlyCost - optimizedCost);
                breakdowns.add(new CostAnalysis.ResourceCostBreakdown(
                    resourceId,
                    metrics.resourceName(),
                    metrics.resourceType(),
                    monthlyCost,
                    optimizedCost,
                    potentialSavings,
                    monthlyCost > 0 ? (potentialSavings / monthlyCost) * 100 : 0,
                    metrics.aggregated().peakHourUtilization(),
                    metrics.aggregated().offPeakHourUtilization(),
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
