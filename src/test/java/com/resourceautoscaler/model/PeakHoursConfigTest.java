package com.resourceautoscaler.model;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the peak hours config behavior and regression cases. */
class PeakHoursConfigTest {

    /** Verifies normalizes Sunday deduplicates and drops invalid days. */
    @Test
    void normalizesSundayDeduplicatesAndDropsInvalidDays() {
        PeakHoursConfig config = new PeakHoursConfig(
            LocalTime.of(7, 0),
            LocalTime.of(18, 0),
            List.of(0, 1, 1, 8, -1, 7),
            65.0,
            10.0,
            15
        );

        assertEquals(List.of(1, 7), config.peakDaysOfWeek());
    }

    /** Verifies empty days use weekday defaults. */
    @Test
    void emptyDaysUseWeekdayDefaults() {
        PeakHoursConfig config = new PeakHoursConfig(
            LocalTime.of(7, 0),
            LocalTime.of(18, 0),
            List.of(),
            65.0,
            10.0,
            15
        );

        assertEquals(List.of(1, 2, 3, 4, 5), config.peakDaysOfWeek());
    }
}
