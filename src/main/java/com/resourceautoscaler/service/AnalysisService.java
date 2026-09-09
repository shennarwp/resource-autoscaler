package com.resourceautoscaler.service;

import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.PeakHoursConfig;
import com.resourceautoscaler.model.ResourceMetrics;
import com.resourceautoscaler.model.ScalingRecommendation;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Converts peak/off-peak utilization differences into actionable recommendations. */
@Service
public class AnalysisService {

    /**
     * Emits a recommendation only when both schedule buckets have samples and the
     * observed utilization crosses both configured thresholds.
     */
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
                config.peakStart() + " - " + config.peakEnd() + " UTC",
                config.peakEnd() + " - " + config.peakStart() + " UTC",
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

    /** Estimates weekly off-peak savings from target utilization and schedule coverage. */
    private double calculateSavingsPercentage(
            ResourceMetrics.AggregatedStats stats,
            PeakHoursConfig config
    ) {
        double offPeakHoursFraction = offPeakHoursFraction(config);

        double offPeakRatio = config.offPeakTargetUtilization() > 0
            ? Math.min(stats.offPeakHourUtilization() / config.offPeakTargetUtilization(), 1.0)
            : 1.0;
        double offPeakReduction = Math.max(0.0, 1.0 - offPeakRatio);

        return Math.max(0.0, offPeakHoursFraction * offPeakReduction * 100.0);
    }

    /** Returns the configured peak duration, including schedules crossing midnight. */
    private double peakHoursPerDay(PeakHoursConfig config) {
        long seconds = Duration.between(config.peakStart(), config.peakEnd()).toSeconds();
        if (seconds <= 0) {
            seconds += 24L * 3600L;
        }
        return seconds / 3600.0;
    }

    /** Calculates the fraction of a seven-day week outside the peak schedule. */
    private double offPeakHoursFraction(PeakHoursConfig config) {
        int peakDays = config.peakDaysOfWeek().size();
        double peakFraction = (peakDays * peakHoursPerDay(config)) / (7.0 * 24.0);
        return Math.clamp(1.0 - peakFraction, 0.0, 1.0);
    }

    /** Scores sample coverage and separation without claiming certainty above 0.95. */
    private double calculateConfidenceScore(
            ResourceMetrics.AggregatedStats stats,
            PeakHoursConfig config
    ) {
        if (stats.peakSampleCount() == 0 || stats.offPeakSampleCount() == 0) {
            return 0.0;
        }

        double score = 0.35;
        double sampleCoverage = Math.min(stats.peakSampleCount() / 100.0, 1.0)
            + Math.min(stats.offPeakSampleCount() / 200.0, 1.0);
        score += 0.2 * (sampleCoverage / 2.0);

        if (stats.offPeakHourUtilization() < 20.0) score += 0.15;
        if (stats.offPeakHourUtilization() < 5.0) score += 0.1;
        if (stats.peakHourUtilization() > config.peakTargetUtilization() + 10.0) score += 0.15;

        double utilRatio = stats.offPeakHourUtilization() > 0.0
            ? stats.peakHourUtilization() / stats.offPeakHourUtilization()
            : 6.0;
        score += Math.min(Math.max(utilRatio - 1.0, 0.0) / 5.0, 1.0) * 0.1;

        return Math.min(score, 0.95);
    }

    /** Selects KEDA, Terraform, or schedule output for a repository resource type. */
    private ScalingRecommendation.RecommendationType determineRecommendationType(String resourceType) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" -> ScalingRecommendation.RecommendationType.KEDA_SCALED_OBJECT;
            case "AZURE_VM" -> ScalingRecommendation.RecommendationType.TERRAFORM_AUTOSCALE;
            case "APP_SERVICE" -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
            case "AZURE_FUNCTION" -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
            default -> ScalingRecommendation.RecommendationType.SCHEDULE_BASED_SCALING;
        };
    }

    /** Formats the discovered or representative current capacity for the UI. */
    private String describeCurrentConfig(String resourceType, CurrentConfig currentConfig) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" -> describeClusterConfig(currentConfig);
            case "AZURE_VM" -> "Standard_D4s_v3 (4 vCPU, 16 GiB), always on";
            case "APP_SERVICE" -> "Standard S3 tier, always running";
            case "AZURE_FUNCTION" -> "Consumption plan, always warm";
            default -> "Static provisioning, no scaling";
        };
    }

    /** Formats Kubernetes replicas, pod requests/limits, and node capacity. */
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

    /** Formats CPU cores using cores or millicores depending on magnitude. */
    private String cpuFormat(double cores) {
        if (cores >= 1.0) {
            return String.format("%.1f", cores);
        }
        return String.format("%.0fm", cores * 1000);
    }

    /** Formats memory using GiB or MiB depending on magnitude. */
    private String memoryFormat(double gib) {
        if (gib >= 1.0) {
            return String.format("%.0fGi", gib);
        }
        return String.format("%.0fMi", gib * 1024);
    }

    /** Formats the provider-specific schedule that would reduce off-peak capacity. */
    private String describeRecommendedConfig(String resourceType, PeakHoursConfig config, CurrentConfig currentConfig) {
        return switch (resourceType) {
            case "AKS_CLUSTER", "K8S_CLUSTER" ->
                "KEDA ScaledObject: " + peakReplicas(currentConfig) + " replicas " + config.peakStart() + "-" + config.peakEnd() +
                " UTC, 1 replica " + config.peakEnd() + "-" + config.peakStart() + " UTC";
            case "AZURE_VM" ->
                "Terraform azurerm_monitor_autoscale: D4s_v3 " + config.peakStart() + "-" + config.peakEnd() +
                " UTC, B2s " + config.peakEnd() + "-" + config.peakStart() + " UTC";
            case "APP_SERVICE" ->
                "Auto-scale: S3 " + config.peakStart() + "-" + config.peakEnd() +
                " UTC, B1 " + config.peakEnd() + "-" + config.peakStart() + " UTC";
            case "AZURE_FUNCTION" ->
                "Pre-warm " + config.peakStart() + " UTC, scale to 0 " + config.peakEnd() + " UTC";
            default -> "Apply schedule-based scaling (UTC)";
        };
    }

    /** Uses discovered replicas when available, otherwise a conservative default. */
    private int peakReplicas(CurrentConfig currentConfig) {
        if (currentConfig != null && currentConfig.available() && currentConfig.replicas() > 0) {
            return currentConfig.replicas();
        }
        return 3;
    }

    /** Explains the observed utilization pattern and estimated savings percentage. */
    private String generateRationale(
            ResourceMetrics.AggregatedStats stats,
            PeakHoursConfig config,
            double savingsPercent
    ) {
        double roundedSavings = Math.round(savingsPercent * 10.0) / 10.0;
        double offPeakIdleFraction = Math.clamp(offPeakHoursFraction(config), 0.0, 1.0) * 100.0;

        return String.format(
            "Observed utilization pattern: peak hours average %.1f%% CPU while off-peak hours average %.1f%% CPU. " +
            "Based on the configured %s-%s UTC schedule, about %.0f%% of the week is outside the peak window. " +
            "Using the current observed utilization gap and target thresholds, the estimated savings are %.1f%% of monthly spend.",
            stats.peakHourUtilization(),
            stats.offPeakHourUtilization(),
            config.peakStart(),
            config.peakEnd(),
            offPeakIdleFraction,
            roundedSavings
        );
    }

    /** Maps repository type names to the public recommendation enum. */
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
