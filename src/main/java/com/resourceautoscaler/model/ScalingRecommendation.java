package com.resourceautoscaler.model;

import java.time.Instant;
import java.time.LocalTime;

/** Actionable scaling recommendation with schedule, confidence, and savings. */
public record ScalingRecommendation(
    String resourceId,
    String resourceName,
    ResourceType resourceType,
    RecommendationType recommendationType,
    String currentConfiguration,
    String recommendedConfiguration,
    String peakSchedule,
    String offPeakSchedule,
    LocalTime peakStart,
    LocalTime peakEnd,
    double estimatedMonthlySavingsUsd,
    double estimatedSavingsPercentage,
    double confidenceScore,
    Instant generatedAt,
    String rationale
) {
    /** Resource families supported by generated configuration. */
    public enum ResourceType {
        AZURE_VM,
        AKS_DEPLOYMENT,
        AZURE_APP_SERVICE,
        AZURE_FUNCTION
    }

    /** Rendering strategy selected for a recommendation. */
    public enum RecommendationType {
        SCHEDULE_BASED_SCALING,
        RIGHTSIZING,
        SHUTDOWN_OFF_HOURS,
        KEDA_SCALED_OBJECT,
        TERRAFORM_AUTOSCALE
    }
}
