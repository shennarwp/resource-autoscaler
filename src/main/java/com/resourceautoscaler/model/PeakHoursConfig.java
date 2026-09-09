package com.resourceautoscaler.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** UTC schedule and utilization thresholds used for peak/off-peak analysis. */
public record PeakHoursConfig(
    LocalTime peakStart,
    LocalTime peakEnd,
    List<Integer> peakDaysOfWeek,
    double peakTargetUtilization,
    double offPeakTargetUtilization,
    int scalingCooldownMinutes
) {
    /**
     * Normalizes missing times, weekday numbering, duplicates, and invalid days.
     * Java's Monday-based values are retained; input {@code 0} is accepted as Sunday.
     */
    public PeakHoursConfig {
        peakStart = peakStart != null ? peakStart : LocalTime.of(7, 0);
        peakEnd = peakEnd != null ? peakEnd : LocalTime.of(18, 0);

        List<Integer> normalized = new ArrayList<>();
        if (peakDaysOfWeek == null || peakDaysOfWeek.isEmpty()) {
            normalized.addAll(List.of(1, 2, 3, 4, 5));
        } else {
            for (Integer day : peakDaysOfWeek) {
                if (day == null) {
                    continue;
                }
                int normalizedDay = day;
                if (normalizedDay == 0) {
                    normalizedDay = 7;
                }
                if (normalizedDay >= 1 && normalizedDay <= 7 && !normalized.contains(normalizedDay)) {
                    normalized.add(normalizedDay);
                }
            }
            if (normalized.isEmpty()) {
                normalized.addAll(List.of(1, 2, 3, 4, 5));
            }
            normalized.sort(Comparator.naturalOrder());
        }

        peakDaysOfWeek = List.copyOf(normalized);
    }

    /** Returns the default weekday schedule used by profiles without overrides. */
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
