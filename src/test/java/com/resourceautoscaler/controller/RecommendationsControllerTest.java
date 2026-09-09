package com.resourceautoscaler.controller;

import com.resourceautoscaler.dto.RecommendationRequest;
import com.resourceautoscaler.dto.RecommendationResponse;
import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import com.resourceautoscaler.service.AnalysisService;
import com.resourceautoscaler.service.CodeGenerationService;
import com.resourceautoscaler.service.CostEstimateService;
import com.resourceautoscaler.service.MetricsCollectionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationsControllerTest {

    private final PeakHoursConfig functionConfig = new PeakHoursConfig(
        LocalTime.of(6, 0), LocalTime.of(22, 0),
        List.of(1, 2, 3, 4, 5, 6, 0), 55.0, 8.0, 10
    );

    @Test
    void recommendationsUseResourceSpecificPeakHoursConfig() {
        MetricsCollectionService metricsService = mock(MetricsCollectionService.class);
        AnalysisService analysisService = mock(AnalysisService.class);
        CodeGenerationService codeGenerationService = mock(CodeGenerationService.class);
        CostEstimateService costEstimateService = mock(CostEstimateService.class);
        RecommendationsController controller =
            new RecommendationsController(metricsService, analysisService, codeGenerationService, costEstimateService);

        ResourceMetrics metrics = sampleMetrics("function-data-processor", "AZURE_FUNCTION");
        when(metricsService.collectMetrics("function-data-processor", 30.0)).thenReturn(metrics);
        when(metricsService.getPeakHoursConfig("function-data-processor")).thenReturn(functionConfig);
        when(metricsService.getCurrentConfig("function-data-processor"))
            .thenReturn(CurrentConfig.unknown("function-data-processor"));
        when(costEstimateService.estimateMonthlyCost(any(CurrentConfig.class), org.mockito.ArgumentMatchers.eq("AZURE_FUNCTION")))
            .thenReturn(120.0);
        when(analysisService.analyzeAndRecommend(any(), any(), anyDouble(), any())).thenReturn(List.of());

        controller.getRecommendations("function-data-processor", 30.0);

        ArgumentCaptor<PeakHoursConfig> configCaptor = ArgumentCaptor.forClass(PeakHoursConfig.class);
        ArgumentCaptor<Double> costCaptor = ArgumentCaptor.forClass(Double.class);
        verify(analysisService).analyzeAndRecommend(org.mockito.ArgumentMatchers.eq(metrics),
            configCaptor.capture(), costCaptor.capture(), any());
        assertEquals(functionConfig, configCaptor.getValue());
        assertEquals(120.0, costCaptor.getValue(), 0.001);
    }

    @Test
    void kubernetesClusterRecommendationsEstimateCostFromResourceType() {
        MetricsCollectionService metricsService = mock(MetricsCollectionService.class);
        AnalysisService analysisService = mock(AnalysisService.class);
        CodeGenerationService codeGenerationService = mock(CodeGenerationService.class);
        CostEstimateService costEstimateService = mock(CostEstimateService.class);
        RecommendationsController controller =
            new RecommendationsController(metricsService, analysisService, codeGenerationService, costEstimateService);

        CurrentConfig k8sConfig = new CurrentConfig("nginx-busy", 3, 3, 0.025, 0.150, 0.008, 0.032, 1, 4.0, true);
        ResourceMetrics metrics = sampleMetrics("nginx-busy", "K8S_CLUSTER");
        when(metricsService.collectMetrics("nginx-busy", 30.0)).thenReturn(metrics);
        when(metricsService.getPeakHoursConfig("nginx-busy")).thenReturn(PeakHoursConfig.defaults());
        when(metricsService.getCurrentConfig("nginx-busy")).thenReturn(k8sConfig);
        when(costEstimateService.estimateMonthlyCost(any(CurrentConfig.class), org.mockito.ArgumentMatchers.eq("K8S_CLUSTER")))
            .thenReturn(525.6);
        when(analysisService.analyzeAndRecommend(any(), any(), anyDouble(), any())).thenReturn(List.of());

        controller.getRecommendations("nginx-busy", 30.0);

        ArgumentCaptor<Double> costCaptor = ArgumentCaptor.forClass(Double.class);
        verify(analysisService).analyzeAndRecommend(org.mockito.ArgumentMatchers.eq(metrics),
            any(), costCaptor.capture(), any());
        assertEquals(525.6, costCaptor.getValue(), 0.001);
    }

    @Test
    void appServiceCodeGenerationUsesAppServiceTemplate() {
        MetricsCollectionService metricsService = mock(MetricsCollectionService.class);
        AnalysisService analysisService = mock(AnalysisService.class);
        CodeGenerationService codeGenerationService = mock(CodeGenerationService.class);
        CostEstimateService costEstimateService = mock(CostEstimateService.class);
        RecommendationsController controller =
            new RecommendationsController(metricsService, analysisService, codeGenerationService, costEstimateService);

        ResourceMetrics metrics = sampleMetrics("appservice-api-gateway", "APP_SERVICE");
        CurrentConfig currentConfig = CurrentConfig.unknown("appservice-api-gateway");
        ScalingRecommendation recommendation = new ScalingRecommendation(
            "appservice-api-gateway", "API Gateway",
            ScalingRecommendation.ResourceType.AZURE_APP_SERVICE,
            ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING,
            "current", "recommended",
            "07:00 - 18:00 UTC", "18:00 - 07:00 UTC",
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            100.0, 20.0, 0.8,
            Instant.now(), "rationale"
        );
        RecommendationRequest request = new RecommendationRequest(
            "appservice-api-gateway", "APP_SERVICE",
            null, null, null, 0.0
        );

        when(metricsService.collectMetrics("appservice-api-gateway", 30.0)).thenReturn(metrics);
        when(metricsService.getCurrentConfig("appservice-api-gateway")).thenReturn(currentConfig);
        when(metricsService.getPeakHoursConfig("appservice-api-gateway")).thenReturn(PeakHoursConfig.defaults());
        when(costEstimateService.estimateMonthlyCost(any(CurrentConfig.class), org.mockito.ArgumentMatchers.eq("APP_SERVICE")))
            .thenReturn(380.0);
        when(analysisService.analyzeAndRecommend(any(), any(), anyDouble(), any()))
            .thenReturn(List.of(recommendation));
        when(codeGenerationService.generateAppServiceAutoscale(recommendation)).thenReturn("app-service-hcl");

        RecommendationResponse response = controller.generateCode(request).getBody();

        assertEquals("app-service-hcl", response.terraformHcl());
        org.mockito.Mockito.verify(codeGenerationService).generateAppServiceAutoscale(recommendation);
        org.mockito.Mockito.verify(codeGenerationService, org.mockito.Mockito.never())
            .generateTerraformAutoscale(recommendation);
    }

    private ResourceMetrics sampleMetrics(String resourceId, String resourceType) {
        ResourceMetrics.AggregatedStats stats = new ResourceMetrics.AggregatedStats(
            30, 60, 5,
            55, 70,
            100,
            40, 5,
            10, 10
        );
        return new ResourceMetrics(
            resourceId, resourceType, resourceId,
            Instant.now(),
            List.<MetricPoint>of(),
            stats
        );
    }
}