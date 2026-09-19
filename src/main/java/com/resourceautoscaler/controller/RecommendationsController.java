package com.resourceautoscaler.controller;

import com.resourceautoscaler.dto.RecommendationRequest;
import com.resourceautoscaler.dto.RecommendationResponse;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ScalingRecommendation;
import com.resourceautoscaler.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

/** HTTP endpoints for recommendations and generated scaling manifests. */
@RestController
@Validated
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
    public ResponseEntity<List<ScalingRecommendation>> getRecommendations(String resourceId, double days) {
        return getRecommendations(resourceId, days, 0, 0, null, null);
    }

    @GetMapping("/{resourceId}")
    public ResponseEntity<List<ScalingRecommendation>> getRecommendations(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String resourceId,
            @RequestParam(defaultValue = "30") @DecimalMin("0.01") @DecimalMax("365") double days,
            @RequestParam(defaultValue = "0") @DecimalMin("0") @DecimalMax("1") double minConfidence,
            @RequestParam(defaultValue = "0") @DecimalMin("0") double minSavingsUsd,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @Pattern(regexp = "(?i)LOW|MEDIUM|HIGH") String risk
    ) {
        Stream<ScalingRecommendation> recommendations = pipeline.recommend(resourceId, days).recommendations().stream()
                .filter(rec -> rec.confidenceScore() >= minConfidence)
                .filter(rec -> rec.estimatedMonthlySavingsUsd() >= minSavingsUsd)
                .filter(rec -> resourceType == null || rec.resourceType().name().equalsIgnoreCase(resourceType))
                .filter(rec -> risk == null || rec.risk().equalsIgnoreCase(risk));
        return ResponseEntity.ok(recommendations.toList());
    }

    /**
     * Applies optional schedule and cost overrides, then renders KEDA or Terraform
     * code for the first applicable recommendation. Returns 204 when none applies.
     */
    @PostMapping("/generate")
    public ResponseEntity<RecommendationResponse> generateCode(
            @Valid @RequestBody RecommendationRequest request
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

        RecommendationPipelineService.RecommendationResult result = pipeline.recommendWithOverrides(request.resourceId(), 30, config, request.currentMonthlyCostUsd());
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
