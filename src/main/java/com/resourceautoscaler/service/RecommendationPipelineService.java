package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs the shared metrics-collect -> peak-config -> current-config -> cost-estimate ->
 * analyze pipeline used by both the recommendations endpoint and the aggregate cost
 * analysis. Extracted so the five-step recipe is defined once instead of being
 * re-assembled independently by each caller.
 */
@Service
public class RecommendationPipelineService {

    private final MetricsCollectionService metricsService;
    private final AnalysisService analysisService;
    private final CostEstimateService costEstimateService;

    public RecommendationPipelineService(
            MetricsCollectionService metricsService,
            AnalysisService analysisService,
            CostEstimateService costEstimateService
    ) {
        this.metricsService = metricsService;
        this.analysisService = analysisService;
        this.costEstimateService = costEstimateService;
    }

    /** Runs the pipeline using the resource's own configured peak schedule and estimated cost. */
    public RecommendationResult recommend(String resourceId, double days) {
        ResourceMetrics metrics = metricsService.collectMetrics(resourceId, days);
        PeakHoursConfig config = metricsService.getPeakHoursConfig(resourceId);
        CurrentConfig currentConfig = metricsService.getCurrentConfig(resourceId);
        double monthlyCost = costEstimateService.estimateMonthlyCost(currentConfig, metrics.resourceType());
        List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(metrics, config, monthlyCost, currentConfig);
        return new RecommendationResult(metrics, config, currentConfig, monthlyCost, recs);
    }

    /**
     * Runs the pipeline with an explicit schedule and/or cost override, used by
     * code generation when the caller supplies partial overrides on top of the
     * resource's defaults.
     */
    public RecommendationResult recommendWithOverrides(
        String resourceId, double days, PeakHoursConfig configOverride, double costOverrideUsd
    ) {
        ResourceMetrics metrics = metricsService.collectMetrics(resourceId, days);
        CurrentConfig currentConfig = metricsService.getCurrentConfig(resourceId);
        double monthlyCost = costOverrideUsd > 0
                ? costOverrideUsd
                : costEstimateService.estimateMonthlyCost(currentConfig, metrics.resourceType());
        List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(
                metrics, configOverride, monthlyCost, currentConfig);
        return new RecommendationResult(metrics, configOverride, currentConfig, monthlyCost, recs);
    }

    public PeakHoursConfig basePeakHoursConfig(String resourceId) {
        return metricsService.getPeakHoursConfig(resourceId);
    }

    /** Lists resources exposed by the active metrics repository. */
    public List<String> getMonitoredResources() {
        return metricsService.getMonitoredResources();
    }

    /** Bundled result of one pipeline run, reused by both callers. */
    public record RecommendationResult(
        ResourceMetrics metrics,
        PeakHoursConfig peakHoursConfig,
        CurrentConfig currentConfig,
        double monthlyCostUsd,
        List<ScalingRecommendation> recommendations
    ) {}
}