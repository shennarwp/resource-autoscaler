package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisServiceTest {

    private final AnalysisService service = new AnalysisService();
    private final PeakHoursConfig config = PeakHoursConfig.defaults();
    private final CurrentConfig unknownConfig = CurrentConfig.unknown("aks-primary-cluster");

    private CurrentConfig k8sCurrentConfig() {
        return new CurrentConfig("nginx-busy", 3, 3, 0.025, 0.150, 8.0 / 1024.0, 32.0 / 1024.0, 1, 4.0, true);
    }

    private ResourceMetrics metricsWith(double peakUtil, double offPeakUtil, int peakSamples, int offPeakSamples) {
        return metricsWith("AKS_CLUSTER", peakUtil, offPeakUtil, peakSamples, offPeakSamples);
    }

    private ResourceMetrics metricsWith(String resourceType, double peakUtil, double offPeakUtil, int peakSamples, int offPeakSamples) {
        ResourceMetrics.AggregatedStats stats = new ResourceMetrics.AggregatedStats(
            30, 60, 5,
            55, 70,
            100,
            peakUtil, offPeakUtil,
            peakSamples, offPeakSamples
        );
        return new ResourceMetrics(
            "aks-primary-cluster", resourceType, "Primary AKS Cluster",
            Instant.now(), List.<MetricPoint>of(), stats
        );
    }

    @Test
    void savingsEstimateIsPositiveWhenOffPeakIsBelowTarget() {
        List<ScalingRecommendation> recs =
            service.analyzeAndRecommend(metricsWith(80, 5, 100, 100), config, 100.0, unknownConfig);

        assertEquals(1, recs.size());
        ScalingRecommendation rec = recs.getFirst();
        double offPeakFraction = 1.0 - (5.0 * 11.0) / (7.0 * 24.0);
        assertEquals(offPeakFraction * 0.5 * 100.0, rec.estimatedSavingsPercentage(), 0.01);
        assertTrue(rec.estimatedMonthlySavingsUsd() > 0);
    }

    @Test
    void recommendationIsSkippedWhenOffPeakBucketHasNoSamples() {
        List<ScalingRecommendation> recs =
            service.analyzeAndRecommend(metricsWith(80, 0, 100, 0), config, 100.0, unknownConfig);
        assertTrue(recs.isEmpty());
    }

    @Test
    void recommendationIsSkippedWhenPeakBucketHasNoSamples() {
        List<ScalingRecommendation> recs =
            service.analyzeAndRecommend(metricsWith(0, 5, 0, 100), config, 100.0, unknownConfig);
        assertTrue(recs.isEmpty());
    }

    @Test
    void savingsUsesWeeklyOffPeakFractionForSevenDayPeakWindow() {
        PeakHoursConfig sevenDayConfig = new PeakHoursConfig(
            LocalTime.of(6, 0), LocalTime.of(22, 0),
            List.of(1, 2, 3, 4, 5, 6, 0), 55.0, 8.0, 10);

        List<ScalingRecommendation> recs =
            service.analyzeAndRecommend(metricsWith(70, 4, 100, 100), sevenDayConfig, 100.0, unknownConfig);

        assertEquals(1, recs.size());
        assertEquals(100.0 / 3.0 * 0.5, recs.getFirst().estimatedSavingsPercentage(), 0.01);
    }

    @Test
    void weekendDaysIncreaseOffPeakWeightingForSameUtilization() {
        PeakHoursConfig sevenDayConfig = new PeakHoursConfig(
            LocalTime.of(7, 0), LocalTime.of(18, 0),
            List.of(1, 2, 3, 4, 5, 6, 0), 65.0, 10.0, 15);

        double weekdaySavings = service.analyzeAndRecommend(metricsWith(80, 5, 100, 100), config, 100.0, unknownConfig)
            .getFirst().estimatedSavingsPercentage();
        double weekendIncludedSavings = service.analyzeAndRecommend(
            metricsWith(80, 5, 100, 100), sevenDayConfig, 100.0, unknownConfig)
            .getFirst().estimatedSavingsPercentage();

        assertEquals((1.0 - (5.0 * 11.0) / (7.0 * 24.0)) * 50.0, weekdaySavings, 0.01);
        assertEquals((1.0 - (7.0 * 11.0) / (7.0 * 24.0)) * 50.0, weekendIncludedSavings, 0.01);
        assertTrue(weekdaySavings > weekendIncludedSavings);
    }

    @Test
    void kubernetesClusterGetsKedaRecommendation() {
        List<ScalingRecommendation> recs = service.analyzeAndRecommend(
            metricsWith("K8S_CLUSTER", 80, 5, 100, 100), config, 2400.0, k8sCurrentConfig());

        assertEquals(1, recs.size());
        ScalingRecommendation rec = recs.getFirst();
        assertEquals(ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT, rec.recommendationType());
        assertEquals(ScalingRecommendation.ResourceType.AKS_DEPLOYMENT, rec.resourceType());
        assertTrue(rec.currentConfiguration().contains("3 replicas"));
        assertTrue(rec.recommendedConfiguration().contains("KEDA ScaledObject"));
        assertTrue(rec.recommendedConfiguration().contains(" UTC"));
        assertTrue(rec.peakSchedule().endsWith(" UTC"));
    }

    @Test
    void kubernetesClusterUsesDiscoveredCurrentConfig() {
        List<ScalingRecommendation> recs = service.analyzeAndRecommend(
            metricsWith("K8S_CLUSTER", 80, 5, 100, 100), config, 100.0,
            new CurrentConfig("nginx-busy", 5, 4, 0.025, 0.150, 8.0 / 1024.0, 32.0 / 1024.0, 1, 4.0, true));

        assertEquals(1, recs.size());
        ScalingRecommendation rec = recs.getFirst();
        assertTrue(rec.currentConfiguration().contains("5 replicas (4 available)"));
        assertTrue(rec.currentConfiguration().contains("25m CPU req / 150m limit per pod"));
        assertTrue(rec.currentConfiguration().contains("8Mi/32Mi mem per pod"));
        assertTrue(rec.recommendedConfiguration().contains("5 replicas"));
    }

    @Test
    void kubernetesClusterWithoutDiscoverableConfigIsHonestAboutIt() {
        List<ScalingRecommendation> recs = service.analyzeAndRecommend(
            metricsWith("K8S_CLUSTER", 80, 5, 100, 100), config, 100.0,
            CurrentConfig.unknown("nginx-busy"));

        assertEquals(1, recs.size());
        assertTrue(recs.getFirst().currentConfiguration().contains("No cluster config discovered"));
    }
}