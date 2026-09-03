package com.resourceautoscaler.controller;

import com.resourceautoscaler.dto.RecommendationRequest;
import com.resourceautoscaler.dto.RecommendationResponse;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import com.resourceautoscaler.service.AnalysisService;
import com.resourceautoscaler.service.CodeGenerationService;
import com.resourceautoscaler.service.MetricsCollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationsController {

    private final MetricsCollectionService metricsService;
    private final AnalysisService analysisService;
    private final CodeGenerationService codeGenerationService;

    public RecommendationsController(
            MetricsCollectionService metricsService,
            AnalysisService analysisService,
            CodeGenerationService codeGenerationService
    ) {
        this.metricsService = metricsService;
        this.analysisService = analysisService;
        this.codeGenerationService = codeGenerationService;
    }

    @GetMapping("/{resourceId}")
    public ResponseEntity<List<ScalingRecommendation>> getRecommendations(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "560.00") double currentMonthlyCost
    ) {
        ResourceMetrics metrics = metricsService.collectMetrics(resourceId, days);
        PeakHoursConfig config = new PeakHoursConfig(
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            List.of(1, 2, 3, 4, 5), 65.0, 10.0, 15
        );
        List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(metrics, config, currentMonthlyCost);
        return ResponseEntity.ok(recs);
    }

    @PostMapping("/generate")
    public ResponseEntity<RecommendationResponse> generateCode(
            @RequestBody RecommendationRequest request
    ) {
        ResourceMetrics metrics = metricsService.collectMetrics(request.resourceId(), 30);
        PeakHoursConfig config = new PeakHoursConfig(
            request.peakStart() != null ? request.peakStart() : LocalTime.of(7, 0),
            request.peakEnd() != null ? request.peakEnd() : LocalTime.of(18, 0),
            request.peakDaysOfWeek() != null ? request.peakDaysOfWeek() : List.of(1, 2, 3, 4, 5),
            65.0, 10.0, 15
        );

        List<ScalingRecommendation> recs = analysisService.analyzeAndRecommend(
            metrics, config, request.currentMonthlyCostUsd()
        );

        if (recs.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        ScalingRecommendation rec = recs.getFirst();

        String kedaYaml = null;
        String terraformHcl = null;

        if (rec.recommendationType() == ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT) {
            kedaYaml = codeGenerationService.generateKedaScaledObject(rec);
        } else {
            terraformHcl = codeGenerationService.generateTerraformAutoscale(rec);
        }

        return ResponseEntity.ok(new RecommendationResponse(rec, kedaYaml, terraformHcl));
    }
}
