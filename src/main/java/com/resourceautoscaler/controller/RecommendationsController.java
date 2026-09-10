package com.resourceautoscaler.controller;

import com.resourceautoscaler.dto.RecommendationRequest;
import com.resourceautoscaler.dto.RecommendationResponse;
import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import com.resourceautoscaler.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for recommendations and generated scaling manifests. */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationsController {

    private final RecommendationPipelineService pipeline;
    private final CodeGenerationService codeGenerationService;

    public RecommendationsController(
        RecommendationPipelineService pipeline,
        CodeGenerationService codeGenerationService
    ) {
        this.pipeline = pipeline;
        this.codeGenerationService = codeGenerationService;
    }

    /** Analyzes the requested window and returns applicable scaling recommendations. */
    @GetMapping("/{resourceId}")
    public ResponseEntity<List<ScalingRecommendation>> getRecommendations(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "30") double days
    ) {
        return ResponseEntity.ok(pipeline.recommend(resourceId, days).recommendations());
    }

    /**
     * Applies optional schedule and cost overrides, then renders KEDA or Terraform
     * code for the first applicable recommendation. Returns 204 when none applies.
     */
    @PostMapping("/generate")
    public ResponseEntity<RecommendationResponse> generateCode(
            @RequestBody RecommendationRequest request
    ) {
        PeakHoursConfig baseConfig = pipeline.basePeakHoursConfig(request.resourceId());
        PeakHoursConfig config = new PeakHoursConfig(
            request.peakStart() != null ? request.peakStart() : baseConfig.peakStart(),
            request.peakEnd() != null ? request.peakEnd() : baseConfig.peakEnd(),
            request.peakDaysOfWeek() != null ? request.peakDaysOfWeek() : baseConfig.peakDaysOfWeek(),
            baseConfig.peakTargetUtilization(),
            baseConfig.offPeakTargetUtilization(),
            baseConfig.scalingCooldownMinutes()
        );

        var result = pipeline.recommendWithOverrides(request.resourceId(), 30, config, request.currentMonthlyCostUsd());
        if (result.recommendations().isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        ScalingRecommendation rec = result.recommendations().getFirst();

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
