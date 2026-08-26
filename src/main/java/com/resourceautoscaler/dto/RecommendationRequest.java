package com.resourceautoscaler.dto;

import java.time.LocalTime;
import java.util.List;

public record RecommendationRequest(
    String resourceId,
    String resourceType,
    LocalTime peakStart,
    LocalTime peakEnd,
    List<Integer> peakDaysOfWeek,
    double currentMonthlyCostUsd
) {}
