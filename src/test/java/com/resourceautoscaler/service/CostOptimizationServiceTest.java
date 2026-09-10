package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CostAnalysis;
import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests the cost optimization service behavior and regression cases. */
class CostOptimizationServiceTest {

    private final PeakHoursConfig functionConfig = new PeakHoursConfig(
        LocalTime.of(6, 0), LocalTime.of(22, 0),
        List.of(1, 2, 3, 4, 5, 6, 0), 55.0, 8.0, 10
    );

    /** Verifies positive savings are subtracted and resource config is used. */
    @Test
    void positiveSavingsAreSubtractedAndResourceConfigIsUsed() {
        MetricsCollectionService metricsService = mock(MetricsCollectionService.class);
        AnalysisService analysisService = mock(AnalysisService.class);
        CostEstimateService costEstimateService = mock(CostEstimateService.class);
        RecommendationPipelineService pipeline =
                new RecommendationPipelineService(metricsService, analysisService, costEstimateService);
        CostOptimizationService service =
            new CostOptimizationService(pipeline);

        ResourceMetrics metrics = new ResourceMetrics(
            "aks-primary-cluster", "AKS_CLUSTER", "Primary AKS Cluster",
            Instant.now(),
            List.of(),
            new ResourceMetrics.AggregatedStats(30, 60, 5, 55, 70, 100, 40, 5, 100, 100)
        );
        ScalingRecommendation rec = new ScalingRecommendation(
            "aks-primary-cluster", "Primary AKS Cluster",
            ScalingRecommendation.ResourceType.AKS_DEPLOYMENT,
            ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT,
            "3 replicas", "1 replica",
            "07:00 - 18:00", "18:00 - 07:00",
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            100.0, 25.0, 0.8,
            Instant.now(), "rationale"
        );

        when(metricsService.getMonitoredResources()).thenReturn(List.of("aks-primary-cluster"));
        when(metricsService.collectMetrics("aks-primary-cluster", 30.0)).thenReturn(metrics);
        when(metricsService.getPeakHoursConfig("aks-primary-cluster")).thenReturn(functionConfig);
        when(metricsService.getCurrentConfig("aks-primary-cluster"))
            .thenReturn(new CurrentConfig("aks-primary-cluster", 3, 3, 1.0, 2.0, 2.0, 4.0, 2, 8.0, true));
        when(costEstimateService.estimateMonthlyCost(any(CurrentConfig.class), org.mockito.ArgumentMatchers.eq("AKS_CLUSTER")))
            .thenReturn(2400.0);
        when(analysisService.analyzeAndRecommend(any(), any(), anyDouble(), any())).thenReturn(List.of(rec));

        CostAnalysis analysis = service.generateCostAnalysis();

        CostAnalysis.ResourceCostBreakdown breakdown = analysis.resources().getFirst();
        assertEquals(2400.0, breakdown.currentMonthlyCostUsd(), 0.001);
        assertEquals(2300.0, breakdown.optimizedMonthlyCostUsd(), 0.001);
        assertEquals(100.0, breakdown.potentialSavingsUsd(), 0.001);
        assertTrue(breakdown.potentialSavingsUsd() > 0);
        assertEquals(2300.0, analysis.summary().totalOptimizedCostUsd(), 0.001);

        ArgumentCaptor<PeakHoursConfig> configCaptor = ArgumentCaptor.forClass(PeakHoursConfig.class);
        verify(analysisService).analyzeAndRecommend(org.mockito.ArgumentMatchers.eq(metrics),
            configCaptor.capture(), org.mockito.ArgumentMatchers.eq(2400.0), any());
        assertEquals(functionConfig, configCaptor.getValue());
    }

    /** Verifies negative recommendation savings cannot create negative cost. */
    @Test
    void negativeRecommendationSavingsCannotCreateNegativeCost() {
        MetricsCollectionService metricsService = mock(MetricsCollectionService.class);
        AnalysisService analysisService = mock(AnalysisService.class);
        CostEstimateService costEstimateService = mock(CostEstimateService.class);
        RecommendationPipelineService pipeline =
            new RecommendationPipelineService(metricsService, analysisService, costEstimateService);
        CostOptimizationService service =
            new CostOptimizationService(pipeline);

        ResourceMetrics metrics = new ResourceMetrics(
            "aks-primary-cluster", "AKS_CLUSTER", "Primary AKS Cluster",
            Instant.now(), List.of(),
            new ResourceMetrics.AggregatedStats(30, 60, 5, 55, 70, 100, 40, 5, 100, 100)
        );
        ScalingRecommendation rec = new ScalingRecommendation(
            "aks-primary-cluster", "Primary AKS Cluster",
            ScalingRecommendation.ResourceType.AKS_DEPLOYMENT,
            ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT,
            "3 replicas", "1 replica",
            "07:00 - 18:00", "18:00 - 07:00",
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            -100.0, -10.0, 0.5,
            Instant.now(), "negative savings"
        );

        when(metricsService.getMonitoredResources()).thenReturn(List.of("aks-primary-cluster"));
        when(metricsService.collectMetrics("aks-primary-cluster", 30.0)).thenReturn(metrics);
        when(metricsService.getPeakHoursConfig("aks-primary-cluster")).thenReturn(functionConfig);
        when(metricsService.getCurrentConfig("aks-primary-cluster"))
            .thenReturn(CurrentConfig.unknown("aks-primary-cluster"));
        when(costEstimateService.estimateMonthlyCost(any(CurrentConfig.class), org.mockito.ArgumentMatchers.eq("AKS_CLUSTER")))
            .thenReturn(2400.0);
        when(analysisService.analyzeAndRecommend(any(), any(), anyDouble(), any())).thenReturn(List.of(rec));

        CostAnalysis analysis = service.generateCostAnalysis();

        CostAnalysis.ResourceCostBreakdown breakdown = analysis.resources().getFirst();
        assertEquals(2400.0, breakdown.optimizedMonthlyCostUsd(), 0.001);
        assertEquals(0.0, breakdown.potentialSavingsUsd(), 0.001);
        assertEquals(0.0, breakdown.savingsPercentage(), 0.001);
    }
}