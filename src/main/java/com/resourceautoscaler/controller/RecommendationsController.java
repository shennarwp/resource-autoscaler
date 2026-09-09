package com.resourceautoscaler.controller;

import com.resourceautoscaler.dto.RecommendationRequest;
import com.resourceautoscaler.dto.RecommendationResponse;
import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import com.resourceautoscaler.service.AnalysisService;
import com.resourceautoscaler.service.CodeGenerationService;
import com.resourceautoscaler.service.CostEstimateService;
import com.resourceautoscaler.service.MetricsCollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for recommendations and generated scaling manifests. */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationsController {

    private final MetricsCollectionService metricsService;
    private final AnalysisService analysisService;
    private final CodeGenerationService codeGenerationService;
    private final CostEstimateService costEstimateService;

    /** Injects analysis, pricing, collection, and code-rendering collaborators. */
    public RecommendationsController(
            MetricsCollectionService metricsService,
            AnalysisService analysisService,
            CodeGenerationService codeGenerationService,
            CostEstimateService costEstimateService
    ) {
        this.metricsService = metricsService;
        this.analysisService = analysisService;
        this.codeGenerationService = codeGenerationService;
        this.costEstimateService = costEstimateService;
    }

    /** Analyzes the requested window and returns applicable scaling recommendations. */
    @GetMapping("/{resourceId}")
    public ResponseEntity<List<ScalingRecommendation>> getRecommendations(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "30") double days
    ) {
        ResourceMetrics metrics = metricsService.collectMetrics(resourceId, days);
        PeakHoursConfig config = metricsService.getPeakHoursConfig(resourceId);
        CurrentConfig currentConfig = metricsService.getCurrentConfig(resourceId);
        double currentMonthlyCost = costEstimateService.estimateMonthlyCost(currentConfig, metrics.resourceType());
        List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(
            metrics, config, currentMonthlyCost, currentConfig);
        return ResponseEntity.ok(recs);
    }

    /**
     * Applies optional schedule and cost overrides, then renders KEDA or Terraform
     * code for the first applicable recommendation. Returns 204 when none applies.
     */
    @PostMapping("/generate")
    public ResponseEntity<RecommendationResponse> generateCode(
            @RequestBody RecommendationRequest request
    ) {
        ResourceMetrics metrics = metricsService.collectMetrics(request.resourceId(), 30);
        CurrentConfig currentConfig = metricsService.getCurrentConfig(request.resourceId());
        PeakHoursConfig baseConfig = metricsService.getPeakHoursConfig(request.resourceId());
        PeakHoursConfig config = new PeakHoursConfig(
            request.peakStart() != null ? request.peakStart() : baseConfig.peakStart(),
            request.peakEnd() != null ? request.peakEnd() : baseConfig.peakEnd(),
            request.peakDaysOfWeek() != null ? request.peakDaysOfWeek() : baseConfig.peakDaysOfWeek(),
            baseConfig.peakTargetUtilization(),
            baseConfig.offPeakTargetUtilization(),
            baseConfig.scalingCooldownMinutes()
        );

        List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(
            metrics,
            config,
            request.currentMonthlyCostUsd() > 0
                ? request.currentMonthlyCostUsd()
                : costEstimateService.estimateMonthlyCost(currentConfig, metrics.resourceType()),
            currentConfig
        );

        if (recs.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        ScalingRecommendation rec = recs.getFirst();

        String kedaYaml = null;
        String terraformHcl = null;

        if (rec.recommendationType() == ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT) {
            kedaYaml = codeGenerationService.generateKedaScaledObject(rec);
        } else if (rec.resourceType() == ScalingRecommendation.ResourceType.AZURE_APP_SERVICE) {
            terraformHcl = codeGenerationService.generateAppServiceAutoscale(rec);
        } else {
            terraformHcl = codeGenerationService.generateTerraformAutoscale(rec);
        }

        return ResponseEntity.ok(new RecommendationResponse(rec, kedaYaml, terraformHcl));
    }
}
