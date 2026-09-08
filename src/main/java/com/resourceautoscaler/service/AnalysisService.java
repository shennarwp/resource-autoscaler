package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalysisService {

    public List<ScalingRecommendation> analyzeAndRecommend(
            ResourceMetrics metrics,
            PeakHoursConfig config,
            double currentMonthlyCostUsd,
            CurrentConfig currentConfig
    ) {
        List<ScalingRecommendation> recommendations = new ArrayList<>();

        ResourceMetrics.AggregatedStats stats = metrics.aggregated();

        if (stats.peakSampleCount() >= 1 && stats.offPeakSampleCount() >= 1
                && stats.offPeakHourUtilization() < config.offPeakTargetUtilization()
                && stats.peakHourUtilization() > config.peakTargetUtilization()) {

            double savingsPercent = calculateSavingsPercentage(stats, config);
            double estimatedSavings = currentMonthlyCostUsd * (savingsPercent / 100.0);

            ScalingRecommendation.RecommendationType recType = determineRecommendationType(metrics.resourceType());

            String currentConfigText = describeCurrentConfig(metrics.resourceType(), currentConfig);
            String recommendedConfig = describeRecommendedConfig(metrics.resourceType(), config, currentConfig);

            double confidence = calculateConfidenceScore(stats, config);

            recommendations.add(new ScalingRecommendation(
                metrics.resourceId(),
                metrics.resourceName(),
                mapResourceType(metrics.resourceType()),
                recType,
                currentConfigText,
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
        double offPeakHoursFraction = offPeakHoursFraction(config);

        double offPeakRatio = config.offPeakTargetUtilization() > 0
            ? Math.min(stats.offPeakHourUtilization() / config.offPeakTargetUtilization(), 1.0)
            : 1.0;
        double offPeakReduction = 1.0 - offPeakRatio;

        return offPeakHoursFraction * offPeakReduction * 100.0;
    }

    private double peakHoursPerDay(PeakHoursConfig config) {
        long seconds = Duration.between(config.peakStart(), config.peakEnd()).toSeconds();
        if (seconds <= 0) {
            seconds += 24L * 3600L;
        }
        return seconds / 3600.0;
    }

    private double offPeakHoursFraction(PeakHoursConfig config) {
        int peakDays = config.peakDaysOfWeek().size();
        double peakFraction = (peakDays * peakHoursPerDay(config)) / (7.0 * 24.0);
        return Math.max(0.0, Math.min(1.0, 1.0 - peakFraction));
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
            case "AKS_CLUSTER", "K8S_CLUSTER" -> ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT;
            case "AZURE_VM" -> ScalingRecommendation.RecommendationType.TERRAFORM_AUTOSCALE;
            case "APP_SERVICE" -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
            case "AZURE_FUNCTION" -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
            default -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
        };
    }

    private String describeCurrentConfig(String resourceType, CurrentConfig currentConfig) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" -> describeClusterConfig(currentConfig);
            case "AZURE_VM" -> "Standard_D4s_v3 (4 vCPU, 16 GiB), always on";
            case "APP_SERVICE" -> "Standard S3 tier, always running";
            case "AZURE_FUNCTION" -> "Consumption plan, always warm";
            default -> "Static provisioning, no scaling";
        };
    }

    private String describeClusterConfig(CurrentConfig currentConfig) {
        if (currentConfig == null || !currentConfig.available() || currentConfig.replicas() <= 0) {
            return "No cluster config discovered (deployment replica/limit metrics not found in Container Insights)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(currentConfig.replicas())
          .append(" replica").append(currentConfig.replicas() == 1 ? "" : "s")
          .append(" (").append(currentConfig.availableReplicas()).append(" available)");

        if (currentConfig.cpuRequestCores() > 0 || currentConfig.cpuLimitCores() > 0) {
            sb.append(", ").append(cpuFormat(currentConfig.cpuRequestCores()))
              .append(" CPU req / ").append(cpuFormat(currentConfig.cpuLimitCores()))
              .append(" limit per pod");
        }
        if (currentConfig.memoryRequestGiB() > 0 || currentConfig.memoryLimitGiB() > 0) {
            sb.append(", ").append(memoryFormat(currentConfig.memoryRequestGiB()))
              .append("/").append(memoryFormat(currentConfig.memoryLimitGiB()))
              .append(" mem per pod");
        }
        if (currentConfig.nodeCount() > 0) {
            sb.append(", ").append(currentConfig.nodeCount())
              .append(" node").append(currentConfig.nodeCount() == 1 ? "" : "s")
              .append(", ").append(cpuFormat(currentConfig.nodeCpuCores())).append(" total");
        }
        sb.append(", running 24/7");
        return sb.toString();
    }

    private String cpuFormat(double cores) {
        if (cores >= 1.0) {
            return String.format("%.1f", cores);
        }
        return String.format("%.0fm", cores * 1000);
    }

    private String memoryFormat(double gib) {
        if (gib >= 1.0) {
            return String.format("%.0fGi", gib);
        }
        return String.format("%.0fMi", gib * 1024);
    }

    private String describeRecommendedConfig(String resourceType, PeakHoursConfig config, CurrentConfig currentConfig) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" ->
                "KEDA ScaledObject: " + peakReplicas(currentConfig) + " replicas " + config.peakStart() + "-" + config.peakEnd() +
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

    private int peakReplicas(CurrentConfig currentConfig) {
        if (currentConfig != null && currentConfig.available() && currentConfig.replicas() > 0) {
            return currentConfig.replicas();
        }
        return 3;
    }

    private String generateRationale(
            ResourceMetrics.AggregatedStats stats,
            PeakHoursConfig config,
            double savingsPercent
    ) {
        return String.format(
            "Detected significant utilization gap: peak hours avg %.1f%% CPU vs off-peak avg %.1f%% CPU. " +
            "Off-peak resources are idle for ~%.0f%% of the week. " +
            "Applying schedule-based scaling to reduce off-peak provisioned capacity " +
            "yields an estimated %.1f%% cost reduction with minimal risk.",
            stats.peakHourUtilization(),
            stats.offPeakHourUtilization(),
            offPeakHoursFraction(config) * 100,
            savingsPercent
        );
    }

    private com.resourceautoscaler.model.ScalingRecommendation.ResourceType mapResourceType(String type) {
        return switch (type) {
            case "AKS_CLUSTER", "K8S_CLUSTER" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AKS_DEPLOYMENT;
            case "AZURE_VM" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_VM;
            case "APP_SERVICE" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_APP_SERVICE;
            case "AZURE_FUNCTION" -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_FUNCTION;
            default -> com.resourceautoscaler.model.ScalingRecommendation.ResourceType.AZURE_VM;
        };
    }
}
