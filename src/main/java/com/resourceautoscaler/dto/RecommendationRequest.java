package com.resourceautoscaler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalTime;
import java.util.List;

/** Optional overrides accepted when requesting generated scaling code. */
public record RecommendationRequest(
    @NotBlank String resourceId,
    String resourceType,
    LocalTime peakStart,
    LocalTime peakEnd,
    @jakarta.validation.constraints.Size(min = 1, max = 7)
    List<@Min(0) @Max(6) Integer> peakDaysOfWeek,
    @PositiveOrZero double currentMonthlyCostUsd
) {}
