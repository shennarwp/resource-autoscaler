package com.resourceautoscaler.service;

import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalysisService {

    public List<ScalingRecommendation> analyzeAndRecommend(
            ResourceMetrics metrics,
            PeakHoursConfig config,
            double currentMonthlyCostUsd
    ) {
        List<ScalingRecommendation> recommendations = new ArrayList<>();

        ResourceMetrics.AggregatedStats stats = metrics.aggregated();

        if (stats.offPeakHourUtilization() < config.offPeakTargetUtilization()
                && stats.peakHourUtilization() > config.peakTargetUtilization()) {

            double savingsPercent = calculateSavingsPercentage(stats, config);
            double estimatedSavings = currentMonthlyCostUsd * (savingsPercent / 100.0);

            ScalingRecommendation.RecommendationType recType = determineRecommendationType(metrics.resourceType());

            String currentConfig = describeCurrentConfig(metrics.resourceType());
            String recommendedConfig = describeRecommendedConfig(metrics.resourceType(), config);

            double confidence = calculateConfidenceScore(stats, config);

            recommendations.add(new ScalingRecommendation(
                metrics.resourceId(),
                metrics.resourceName(),
                mapResourceType(metrics.resourceType()),
                recType,
                currentConfig,
                recommendedConfig,
                config.peakStart() + " - " + config.peakEnd(),
                config.peakEnd() + " - " + config.peakStart(),
                config.peakStart(),
                config.peakEnd(),
                estimatedSavings,
                savingsPercent,
                confidence,
                Instant.now(),
                generateRationale(stats, config, savingsPercent)
            ));
        }

        return recommendations;
    }

    private double calculateSavingsPercentage(
            ResourceMetrics.AggregatedStats stats,
            PeakHoursConfig config
    ) {
        double peakHoursFraction = (double)(config.peakEnd().getHour() - config.peakStart().getHour()) / 24.0;
        double offPeakHoursFraction = 1.0 - peakHoursFraction;

        double offPeakReduction = 1.0 - (config.offPeakTargetUtilization() / Math.max(stats.offPeakHourUtilization(), 1.0));

        return offPeakHoursFraction * offPeakReduction * 100.0;
    }

    private double calculateConfidenceScore(
            ResourceMetrics.AggregatedStats stats,
            PeakHoursConfig config
    ) {
        double score = 0.5;

        if (stats.offPeakHourUtilization() < 15.0) score += 0.2;
        if (stats.offPeakHourUtilization() < 5.0) score += 0.1;
        if (stats.peakHourUtilization() > 50.0) score += 0.1;
        if (stats.maxCpuUtilization() - stats.offPeakHourUtilization() > 40.0) score += 0.1;

        return Math.min(score, 1.0);
    }

    private ScalingRecommendation.RecommendationType determineRecommendationType(String resourceType) {
        return switch (resourceType) {
            case "AKS_CLUSTER" -> ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT;
            case "AZURE_VM" -> ScalingRecommendation.RecommendationType.TERRAFORM_AUTOSCALE;
            case "APP_SERVICE" -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
            case "AZURE_FUNCTION" -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
            default -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
        };
    }

    private String describeCurrentConfig(String resourceType) {
        return switch (resourceType) {
            case "AKS_CLUSTER" -> "3 replicas, 2 CPU / 4Gi memory, running 24/7";
            case "AZURE_VM" -> "Standard_D4s_v3 (4 vCPU, 16 GiB), always on";
            case "APP_SERVICE" -> "Standard S3 tier, always running";
            case "AZURE_FUNCTION" -> "Consumption plan, always warm";
            default -> "Static provisioning, no scaling";
        };
    }

    private String describeRecommendedConfig(String resourceType, PeakHoursConfig config) {
        return switch (resourceType) {
            case "AKS_CLUSTER" ->
                "KEDA ScaledObject: 3 replicas " + config.peakStart() + "-" + config.peakEnd() +
                ", 1 replica " + config.peakEnd() + "-" + config.peakStart();
            case "AZURE_VM" ->
                "Terraform azurerm_monitor_autoscale: D4s_v3 " + config.peakStart() + "-" + config.peakEnd() +
                ", B2s " + config.peakEnd() + "-" + config.peakStart();
            case "APP_SERVICE" ->
                "Auto-scale: S3 " + config.peakStart() + "-" + config.peakEnd() +
                ", B1 " + config.peakEnd() + "-" + config.peakStart();
            case "AZURE_FUNCTION" ->
                "Pre-warm " + config.peakStart() + ", scale to 0 " + config.peakEnd();
            default -> "Apply schedule-based scaling";
        };
    }

    private String generateRationale(
            ResourceMetrics.AggregatedStats stats,
            PeakHoursConfig config,
            double savingsPercent
    ) {
        return String.format(
            "Detected significant utilization gap: peak hours avg %.1f%% CPU vs off-peak avg %.1f%% CPU. " +
            "Off-peak resources are idle for ~%.0f%% of the day. " +
            "Applying schedule-based scaling to reduce off-peak provisioned capacity " +
            "yields an estimated %.1f%% cost reduction with minimal risk.",
            stats.peakHourUtilization(),
            stats.offPeakHourUtilization(),
            (1.0 - (double)(config.peakEnd().getHour() - config.peakStart().getHour()) / 24.0) * 100,
            savingsPercent
        );
    }

    private com.resourceautoscaler.model.ScalingRecommendation.ResourceType mapResourceType(String type) {
        return switch (type) {
            case "AKS_CLUSTER" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AKS_DEPLOYMENT;
            case "AZURE_VM" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_VM;
            case "APP_SERVICE" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_APP_SERVICE;
            case "AZURE_FUNCTION" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_FUNCTION;
            default -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_VM;
        };
    }
}
