package com.resourceautoscaler.model;

import java.time.LocalTime;
import java.util.List;

public record PeakHoursConfig(
    LocalTime peakStart,
    LocalTime peakEnd,
    List<Integer> peakDaysOfWeek,
    double peakTargetUtilization,
    double offPeakTargetUtilization,
    int scalingCooldownMinutes
) {
    public static PeakHoursConfig defaults() {
        return new PeakHoursConfig(
            LocalTime.of(7, 0),
            LocalTime.of(18, 0),
            List.of(1, 2, 3, 4, 5),
            65.0,
            10.0,
            15
        );
    }
}
